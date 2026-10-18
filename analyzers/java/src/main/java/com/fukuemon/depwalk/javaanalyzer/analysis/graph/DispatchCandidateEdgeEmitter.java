package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.TypeSite;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.MethodIds;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.spring.SpringDiIndex;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;
import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.types.ResolvedIntersectionType;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;

/**
 * 宣言型への edge を出した call site に、実行時に呼ばれうる実装の候補への edge を足す。
 *
 * <p>候補は、receiver の静的型の制約から引いた型階層の実装と、Spring DI で注入される bean の実装を
 * call site ごとに統合して決める。宣言型への edge の出力と、call site の終端の ledger への記録は
 * しない。それは {@link CallGraphBuilder} が先に済ませている。
 */
final class DispatchCandidateEdgeEmitter {

    private final GraphAccumulator accumulator;
    private final SootUpTypeHierarchyIndex sootUpIndex;
    private final SpringInjectionMatcher springInjections;
    private final MethodSymbolFactory methodSymbols;
    private final UnresolvedDiagnostics diagnostics;
    /** edge 出力に使える caller の解決。caller 不在時に {@code <clinit>} node を保証する副作用を含む。 */
    private final BiFunction<Node, CallGraphBuilder.WalkContext, List<String>> edgeCallers;

    DispatchCandidateEdgeEmitter(
            GraphAccumulator accumulator,
            SootUpTypeHierarchyIndex sootUpIndex,
            SpringInjectionMatcher springInjections,
            MethodSymbolFactory methodSymbols,
            UnresolvedDiagnostics diagnostics,
            BiFunction<Node, CallGraphBuilder.WalkContext, List<String>> edgeCallers) {
        this.accumulator = accumulator;
        this.sootUpIndex = sootUpIndex;
        this.springInjections = springInjections;
        this.methodSymbols = methodSymbols;
        this.diagnostics = diagnostics;
        this.edgeCallers = edgeCallers;
    }

    private static final class CandidateEdgeInfo {
        private final SootUpTypeHierarchyIndex.MethodCandidate method;
        private final Set<String> provenance = new TreeSet<>();
        private final Set<String> conditionTypes = new TreeSet<>();
        private boolean ambiguous;

        private CandidateEdgeInfo(SootUpTypeHierarchyIndex.MethodCandidate method) {
            this.method = method;
        }
    }

    /**
     * 型階層と Spring DI の候補を call site 単位で統合し、宣言型 edge とは別に実装候補 edge を追加する。
     *
     * @param callSite 宣言型への edge に付けた call site の位置。候補の edge にも同じ位置を使う
     * @param declarationMethodId 宣言型への edge の callee。同じ methodId の候補は重ねて出さない
     */
    void emit(
            ResolvedMethodDeclaration resolved,
            String dispatch,
            TypeSite receiverSite,
            Node callNode,
            CallGraphBuilder.WalkContext ctx,
            SourceLocation callSite,
            String declarationMethodId) {
        if ("static".equals(dispatch) || isExplicitSuperDispatch(callNode)) {
            return;
        }
        String declaringType = BinaryNames.forResolvedDeclaration(resolved.declaringType());
        List<String> parameterTypes = AttributionSites.paramBinaryNames(resolved);
        String receiverType = receiverSite == null ? declaringType : receiverSite.binaryName();
        List<String> receiverTypes = receiverTypeConstraints(callNode, receiverType);
        SootUpTypeHierarchyIndex.Resolution sootResolution = sootUpIndex.resolveMethod(
                declaringType,
                receiverTypes,
                resolved.getName(),
                parameterTypes);
        if (!sootResolution.isAvailable()) {
            diagnostics.reportSootUnavailable(sootResolution, declaringType, callNode, ctx.callerMethodIds());
            return;
        }

        Map<String, CandidateEdgeInfo> merged = new LinkedHashMap<>();
        Set<String> sootCandidateKeys = new LinkedHashSet<>();
        for (SootUpTypeHierarchyIndex.MethodCandidate candidate : sootResolution.candidates()) {
            sootCandidateKeys.add(candidateKey(candidate));
        }

        SpringDiIndex.InjectionResolution springResolution =
                springInjections.resolutionFor(callNode, ctx.enclosingTypeNode());
        if (springResolution == null
                || springResolution.status() == SpringDiIndex.ResolutionStatus.UNRESOLVED) {
            addSootCandidates(merged, sootResolution.candidates());
        } else if (springResolution.status() == SpringDiIndex.ResolutionStatus.UNIQUE
                || springResolution.status() == SpringDiIndex.ResolutionStatus.AMBIGUOUS) {
            boolean ambiguous = springResolution.status() == SpringDiIndex.ResolutionStatus.AMBIGUOUS;
            for (SpringDiIndex.BeanCandidate beanCandidate : springResolution.candidates()) {
                SootUpTypeHierarchyIndex.Resolution implementation = sootUpIndex.resolveImplementationMethod(
                        beanCandidate.bean().implementationType(),
                        resolved.getName(),
                        parameterTypes);
                if (!implementation.isAvailable()) {
                    diagnostics.reportSootUnavailable(
                            implementation, beanCandidate.bean().implementationType(), callNode,
                            ctx.callerMethodIds());
                    continue;
                }
                for (SootUpTypeHierarchyIndex.MethodCandidate candidate : implementation.candidates()) {
                    CandidateEdgeInfo info = merged.computeIfAbsent(
                            candidateKey(candidate), key -> new CandidateEdgeInfo(candidate));
                    info.provenance.addAll(beanCandidate.provenance());
                    if (sootCandidateKeys.contains(candidateKey(candidate))) {
                        info.provenance.add("sootup");
                    }
                    info.conditionTypes.addAll(beanCandidate.bean().conditionTypes());
                    info.ambiguous |= ambiguous;
                }
            }
        }

        for (CandidateEdgeInfo info : merged.values()) {
            MethodSymbol candidateSymbol = methodSymbols.buildCandidateMethodSymbol(info.method);
            if (declarationMethodId.equals(candidateSymbol.methodId())) {
                continue;
            }
            accumulator.addNode(candidateSymbol);
            Map<String, Object> metadata = candidateEdgeMetadata(info);
            for (String callerId : edgeCallers.apply(callNode, ctx)) {
                accumulator.addEdge(callerId, candidateSymbol.methodId(), callSite, metadata);
            }
        }
    }

    private static void addSootCandidates(
            Map<String, CandidateEdgeInfo> merged,
            List<SootUpTypeHierarchyIndex.MethodCandidate> candidates) {
        boolean ambiguous = candidates.size() != 1;
        for (SootUpTypeHierarchyIndex.MethodCandidate candidate : candidates) {
            CandidateEdgeInfo info = merged.computeIfAbsent(
                    candidateKey(candidate),
                    key -> new CandidateEdgeInfo(candidate));
            info.provenance.add("sootup");
            info.ambiguous = ambiguous;
        }
    }

    /**
     * {@code super.method()} と {@code super::method} は JVM の {@code invokespecial} に相当し、
     * 実行時のレシーバー型によるオーバーライド選択を行わない。そのため宣言先への通常 edge は保持しつつ、
     * 型階層由来の実装候補 edge だけを生成対象外とする。
     */
    private static boolean isExplicitSuperDispatch(Node callNode) {
        if (callNode instanceof MethodCallExpr methodCall) {
            return methodCall.getScope().filter(SuperExpr.class::isInstance).isPresent();
        }
        return callNode instanceof MethodReferenceExpr methodReference
                && methodReference.getScope() instanceof SuperExpr;
    }

    /**
     * call site の receiver が同時に満たす静的型を返す。
     *
     * <p>通常の reference type は fallback の 1 型だけを返す。型変数または intersection type では
     * 全 extends 境界を返し、SootUp 側で候補の積集合を取れるようにする。型解決に失敗した場合も
     * erasure 済み fallback を保持する。返す列は重複なし。
     */
    private static List<String> receiverTypeConstraints(Node callNode, String fallback) {
        Expression scope = CallScopes.scopeOf(callNode);
        if (scope == null) {
            return List.of(fallback);
        }
        try {
            ResolvedType receiverType = scope.calculateResolvedType();
            LinkedHashSet<String> constraints = new LinkedHashSet<>();
            collectReceiverTypeConstraints(receiverType, constraints, new LinkedHashSet<>());
            return constraints.isEmpty() ? List.of(fallback) : List.copyOf(constraints);
        } catch (RuntimeException | LinkageError e) {
            return List.of(fallback);
        }
    }

    /**
     * 型変数・intersection・上限 wildcard を再帰展開し、最終的な reference 型境界を収集する。
     * 間接境界 ({@code T extends U}, {@code U extends A & B}) でも A と B の両方を保持する。
     *
     * @param visiting 展開中の型変数名。循環参照を停止するために呼び出し間で引き回す
     */
    private static void collectReceiverTypeConstraints(
            ResolvedType type,
            Set<String> constraints,
            Set<String> visiting) {
        if (type.isTypeVariable()) {
            String variableName = type.asTypeVariable().qualifiedName();
            if (!visiting.add(variableName)) {
                return;
            }
            type.asTypeVariable().asTypeParameter().getBounds().stream()
                    .filter(bound -> bound.isExtends())
                    .forEach(bound -> collectReceiverTypeConstraints(bound.getType(), constraints, visiting));
            visiting.remove(variableName);
            return;
        }
        if (type instanceof ResolvedIntersectionType intersectionType) {
            intersectionType.getElements()
                    .forEach(element -> collectReceiverTypeConstraints(element, constraints, visiting));
            return;
        }
        if (type.isWildcard() && type.asWildcard().isExtends()) {
            collectReceiverTypeConstraints(type.asWildcard().getBoundedType(), constraints, visiting);
            return;
        }
        ResolvedType erased = type.erasure();
        if (erased.isReferenceType()) {
            constraints.add(BinaryNames.erasureOf(erased));
        }
    }

    private static Map<String, Object> candidateEdgeMetadata(CandidateEdgeInfo info) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("resolution", info.ambiguous ? "ambiguous" : "unique");
        metadata.put("provenance", List.copyOf(info.provenance));
        if (!info.conditionTypes.isEmpty()) {
            metadata.put("conditional", true);
            metadata.put("conditionTypes", List.copyOf(info.conditionTypes));
        }
        return metadata;
    }

    private static String candidateKey(SootUpTypeHierarchyIndex.MethodCandidate candidate) {
        return MethodIds.signature(candidate.declaringType(), candidate.methodName(), candidate.parameterTypes());
    }
}
