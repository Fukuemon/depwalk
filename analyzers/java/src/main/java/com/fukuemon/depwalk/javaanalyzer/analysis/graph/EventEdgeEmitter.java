package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.spring.EventListenerIndex;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;
import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * publish → listener の candidate edge を生成する (broadcast 意味論)。
 *
 * <p>Spring イベントの突合規則 (publisher 判定・event 型階層の収集・listener 突合・
 * ambiguous 判定・イベント metadata の組み立て) をここへ集約する。graph package に
 * 置くのは、{@link CallGraphBuilder} の内部型 (WalkContext) と edge emit の基盤
 * (edgeCallers / {@link MethodSymbolFactory}) を使うため。
 */
final class EventEdgeEmitter {

    private static final String EVENT_PUBLISHER_TYPE = "org.springframework.context.ApplicationEventPublisher";

    private final GraphAccumulator accumulator;
    private final MethodSymbolFactory methodSymbols;
    private final SourceLocations sourceLocations;
    private final EventListenerIndex eventListenerIndex;
    private final ReachableOwners reachableOwners;
    private final UnresolvedDiagnostics diagnostics;
    /**
     * edge 出力に使える caller の解決 ({@link CallGraphBuilder} の edgeCallers)。
     * caller 不在時に {@code <clinit>} node を保証する副作用を含む。評価は切り出し前の
     * 実装と同じく突合結果の判定より先に行う (順序を変えると出力の node 集合が変わる)。
     */
    private final BiFunction<MethodCallExpr, CallGraphBuilder.WalkContext, List<String>> edgeCallers;

    EventEdgeEmitter(
            GraphAccumulator accumulator,
            MethodSymbolFactory methodSymbols,
            SourceLocations sourceLocations,
            EventListenerIndex eventListenerIndex,
            ReachableOwners reachableOwners,
            UnresolvedDiagnostics diagnostics,
            BiFunction<MethodCallExpr, CallGraphBuilder.WalkContext, List<String>> edgeCallers) {
        this.accumulator = accumulator;
        this.methodSymbols = methodSymbols;
        this.sourceLocations = sourceLocations;
        this.eventListenerIndex = eventListenerIndex;
        this.reachableOwners = reachableOwners;
        this.diagnostics = diagnostics;
        this.edgeCallers = edgeCallers;
    }

    /**
     * caller は call site の囲みメソッド、callee は引数の静的型とその型階層 (raw 近似)
     * に合致する listener。無条件 listener は複数でも各々 unique とし、実行時条件
     * (条件アノテーション / condition 属性 / transactional phase) を持つ listener と
     * raw 近似が過剰一致しうる listener (型変数・型引数付き param) は ambiguous とする。
     * 突合は candidate 再対応付けと同じく publisher context から到達可能な listener に
     * 限る。receiver が publisher と確認できない場合は対象外 (edge も診断も出さない保守側)。
     */
    void emit(MethodCallExpr mce, CallGraphBuilder.WalkContext ctx) {
        if (!"publishEvent".equals(mce.getNameAsString()) || mce.getArguments().size() != 1) {
            return;
        }
        Expression scope = mce.getScope().orElse(null);
        boolean publisherReceiver = scope != null
                ? isEventPublisher(scope)
                : enclosingTypeIsEventPublisher(ctx.enclosingTypeNode());
        if (!publisherReceiver) {
            return;
        }
        boolean argumentUnresolved = false;
        List<String> eventTypes = List.of();
        try {
            ResolvedType argType = mce.getArgument(0).calculateResolvedType();
            if (argType.isReferenceType()) {
                eventTypes = typeAndAncestors(argType.asReferenceType());
            }
            // 解決できた非 reference 引数 (null literal / primitive) はイベントではない。
            // 解決自体は成功しているため、診断は出さず黙って対象外にする。
        } catch (RuntimeException | LinkageError e) {
            argumentUnresolved = true;
        }
        if (argumentUnresolved) {
            // outcome ledger の外の advisory 診断。call site 自体の終端は通常経路が分類する。
            diagnostics.reportEventUnresolved(mce, ctx.callerMethodIds());
            return;
        }
        if (eventTypes.isEmpty() || eventListenerIndex.isEmpty()) {
            return;
        }

        Map<String, EventListenerIndex.Listener> matched = new LinkedHashMap<>();
        for (String eventType : eventTypes) {
            for (EventListenerIndex.Listener listener : eventListenerIndex.listenersFor(eventType)) {
                if (reachableOwners.find(listener.declaringType()).isEmpty()) {
                    // candidate 再対応付けと同じ制約: 到達可能 context の外の listener は
                    // 突合しない (合成の fallback node を作らない)。
                    continue;
                }
                matched.putIfAbsent(
                        listener.declaringType() + "#" + listener.methodName()
                                + "(" + String.join(",", listener.parameterTypes()) + ")",
                        listener);
            }
        }
        List<String> callers = edgeCallers.apply(mce, ctx);
        if (matched.isEmpty() || callers.isEmpty()) {
            return;
        }
        SourceLocation callSite = sourceLocations.sourceLocationOf(mce);
        for (EventListenerIndex.Listener listener : matched.values()) {
            MethodSymbol listenerSymbol = methodSymbols.buildCandidateMethodSymbol(
                    new SootUpTypeHierarchyIndex.MethodCandidate(
                            listener.declaringType(), listener.methodName(), listener.parameterTypes()));
            accumulator.addNode(listenerSymbol);
            boolean conditional = !listener.conditionTypes().isEmpty();
            boolean ambiguous = conditional || listener.rawApproximation();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("resolution", ambiguous ? "ambiguous" : "unique");
            metadata.put("provenance", List.of("spring-event"));
            if (conditional) {
                metadata.put("conditional", true);
                metadata.put("conditionTypes", listener.conditionTypes());
            }
            for (String callerId : callers) {
                accumulator.addEdge(callerId, listenerSymbol.methodId(), callSite, metadata);
            }
        }
    }

    /** receiver の静的型が {@code ApplicationEventPublisher} またはその subtype かを判定する。 */
    private static boolean isEventPublisher(Expression scope) {
        try {
            return isEventPublisherType(scope.calculateResolvedType());
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** 暗黙 this receiver: 囲み型が publisher subtype なら対象とする。 */
    private static boolean enclosingTypeIsEventPublisher(Node enclosingTypeNode) {
        if (!(enclosingTypeNode instanceof TypeDeclaration<?> td)) {
            return false;
        }
        try {
            ResolvedReferenceTypeDeclaration resolved = td.resolve();
            for (ResolvedReferenceType ancestor : resolved.getAllAncestors()) {
                if (EVENT_PUBLISHER_TYPE.equals(ancestor.getQualifiedName())) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static boolean isEventPublisherType(ResolvedType type) {
        if (!type.isReferenceType()) {
            return false;
        }
        ResolvedReferenceType reference = type.asReferenceType();
        if (EVENT_PUBLISHER_TYPE.equals(reference.getQualifiedName())) {
            return true;
        }
        try {
            for (ResolvedReferenceType ancestor : reference.getAllAncestors()) {
                if (EVENT_PUBLISHER_TYPE.equals(ancestor.getQualifiedName())) {
                    return true;
                }
            }
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
        return false;
    }

    /** 引数の静的型とその型階層の raw binary name 列 (解決できた ancestor のみの best effort)。 */
    private static List<String> typeAndAncestors(ResolvedReferenceType reference) {
        Set<String> types = new LinkedHashSet<>();
        types.add(BinaryNames.erasureOf(reference));
        try {
            for (ResolvedReferenceType ancestor : reference.getAllAncestors()) {
                types.add(BinaryNames.erasureOf(ancestor));
            }
        } catch (RuntimeException | LinkageError ignored) {
            // 部分的にしか解決できない階層は、解決できた prefix だけを保持する (raw 近似)。
        }
        return List.copyOf(types);
    }
}
