package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.MethodIds;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;
import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedParameterDeclaration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * functional interface の SAM を呼ぶ call site から、receiver に入っている callable の実体への edge を足す。
 *
 * <p>receiver の実体は、同じメソッドの local 変数の initializer と、workspace のメソッドの parameter
 * への引数渡し 1 段からだけ決める。実体を決められない SAM の呼び出しは advisory 診断に残す。
 * SAM の呼び出しそのものの解決、宣言型への edge の出力、call site の終端の ledger への記録はしない。
 * それは {@link CallGraphBuilder} が受け持つ。
 */
final class CallableInvocationEdgeEmitter {

    private final GraphAccumulator accumulator;
    private final MethodSymbolFactory methodSymbols;
    private final SourceLocations sourceLocations;
    private final CallablePassIndex callablePassIndex;
    private final ReachableOwners reachableOwners;
    private final UnresolvedDiagnostics diagnostics;
    /** edge 出力に使える caller の解決。caller 不在時に {@code <clinit>} node を保証する副作用を含む。 */
    private final BiFunction<Node, CallGraphBuilder.WalkContext, List<String>> edgeCallers;

    CallableInvocationEdgeEmitter(
            GraphAccumulator accumulator,
            MethodSymbolFactory methodSymbols,
            SourceLocations sourceLocations,
            CallablePassIndex callablePassIndex,
            ReachableOwners reachableOwners,
            UnresolvedDiagnostics diagnostics,
            BiFunction<Node, CallGraphBuilder.WalkContext, List<String>> edgeCallers) {
        this.accumulator = accumulator;
        this.methodSymbols = methodSymbols;
        this.sourceLocations = sourceLocations;
        this.callablePassIndex = callablePassIndex;
        this.reachableOwners = reachableOwners;
        this.diagnostics = diagnostics;
        this.edgeCallers = edgeCallers;
    }

    /**
     * functional interface の invocation site から、渡された callable 実体への edge を
     * 生成する。追跡範囲は (1) 同一メソッド内の local 変数 (再代入なし・
     * lambda / method reference の直接 initializer) と (2) workspace メソッドの
     * parameter への引数渡し 1 段。field 経由は JAVA_CALLABLE_UNRESOLVED (info) の
     * advisory 診断に残す。callee は method reference → 参照先、lambda → 定義側の
     * 囲みメソッドで、`viaCallableInvocation: true` を標識する。到達可能 context の
     * callee のみ対象 (external / 非依存 context へは張らない)。
     *
     * @param invoked {@code mce} の解決結果。resolve は呼び出し側が 1 回だけ行い、ここでは
     *     再び resolve しない
     */
    void emit(MethodCallExpr mce, CallGraphBuilder.WalkContext ctx, ResolvedMethodDeclaration invoked) {
        Expression scope = mce.getScope().orElse(null);
        // 無修飾の `stored.run()` と `this.stored.run()` は同じ呼び出しなので、
        // 片方だけ advisory 診断から漏らさない。`this` 以外を挟む多段 scope
        // (`other.field.run()` 等) は receiver の実体を name だけで決められないため
        // 対象にしない。
        NameExpr receiver = null;
        boolean thisQualifiedField = false;
        if (scope instanceof NameExpr nameScope) {
            receiver = nameScope;
        } else if (scope instanceof FieldAccessExpr fieldScope
                && fieldScope.getScope() instanceof ThisExpr) {
            thisQualifiedField = true;
        } else {
            return;
        }
        // SAM invocation として扱うのは functional interface の単一 abstract method の
        // 呼び出しだけ。普通の interface 呼び出し (注入された Spring bean 等) を追跡や
        // 下の advisory 診断へ流さない。合成宣言 (enum の values() 等) は isAbstract()
        // が throw することがあり、その場合は「SAM でない」とみなす。
        try {
            // equals などの Object 再宣言は SAM ではないため、その呼び出し自体も
            // callable invocation として扱わない。
            if (!invoked.isAbstract() || redeclaresObjectMethod(invoked) || !isFunctionalInterfaceSam(invoked)) {
                return;
            }
        } catch (RuntimeException | LinkageError e) {
            return;
        }
        List<CallablePassIndex.CallableTarget> callables;
        if (thisQualifiedField) {
            // `this.` 修飾は必ず field を指すので、追跡範囲外として空にする。
            callables = List.of();
        } else {
            Object receiverDecl;
            try {
                receiverDecl = receiver.resolve();
            } catch (RuntimeException | LinkageError e) {
                return;
            }
            if (receiverDecl instanceof ResolvedParameterDeclaration) {
                callables = callablesForParameter(receiver.getNameAsString(), mce, ctx);
            } else if (receiverDecl
                    instanceof ResolvedFieldDeclaration) {
                callables = List.of();
            } else {
                callables = callablesForLocal(receiver.getNameAsString(), mce);
            }
        }
        // constructor body の callable は対象外 (symbol の形が method と異なる)。
        // ここで落とすことで first-wins の node 内容不変条件を保つ。
        callables = callables.stream()
                .filter(callable -> !MethodIds.CONSTRUCTOR_TOKEN.equals(callable.methodName()))
                .filter(callable -> reachableOwners.find(callable.declaringType()).isPresent())
                .toList();
        if (callables.isEmpty()) {
            // 追跡できなかった SAM invocation (field 格納・対応付かない parameter・追跡
            // 不能な local) は対称に advisory 診断として可視化し、追跡の穴を観測可能に
            // 保つ。advisory のみで ledger には触れない。
            diagnostics.reportCallableUnresolved(mce, ctx.callerMethodIds());
            return;
        }
        List<String> callers = edgeCallers.apply(mce, ctx);
        if (callers.isEmpty()) {
            return;
        }

        SourceLocation callSite = sourceLocations.sourceLocationOf(mce);
        Map<String, Object> metadata = ctx.viaLambda()
                ? Map.of("viaCallableInvocation", true, "viaLambda", true)
                : Map.of("viaCallableInvocation", true);
        LinkedHashSet<String> emitted = new LinkedHashSet<>();
        for (CallablePassIndex.CallableTarget callable : callables) {
            MethodSymbol calleeSymbol = methodSymbols.buildCandidateMethodSymbol(
                    new SootUpTypeHierarchyIndex.MethodCandidate(
                            callable.declaringType(), callable.methodName(), callable.parameterTypes()));
            if (!emitted.add(calleeSymbol.methodId())) {
                continue;
            }
            accumulator.addNode(calleeSymbol);
            for (String callerId : callers) {
                accumulator.addEdge(callerId, calleeSymbol.methodId(), callSite, metadata);
            }
        }
    }

    /**
     * declaring interface が functional interface (抽象メソッドがちょうど 1 個) かを判定する。
     * Object の public method と同 signature の abstract 再宣言 (Comparator の equals 等) は
     * JLS の functional interface 判定と同じく数から除外する。
     */
    private static boolean isFunctionalInterfaceSam(ResolvedMethodDeclaration invoked) {
        try {
            var declaringType = invoked.declaringType();
            if (!declaringType.isInterface()) {
                return false;
            }
            long abstractCount = declaringType.getDeclaredMethods().stream()
                    .filter(method -> method.isAbstract())
                    .filter(method -> !redeclaresObjectMethod(method))
                    .count();
            // 自身の宣言だけで判定する制約: SAM を親から継承した interface (宣言 0 件)
            // は対象外になり、逆に親にも abstract がある非 functional interface を
            // 1 件と数えて対象にしうる。継承まで辿る判定は解決コストが大きいため
            // 採らない。
            return abstractCount == 1;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Object の public method (equals(Object) / hashCode() / toString()) と同 signature か。 */
    private static boolean redeclaresObjectMethod(ResolvedMethodDeclaration method) {
        return switch (method.getName()) {
            case "hashCode", "toString" -> method.getNumberOfParams() == 0;
            case "equals" -> method.getNumberOfParams() == 1
                    && "java.lang.Object".equals(BinaryNames.erasureOf(method.getParam(0).getType()));
            default -> false;
        };
    }

    /** 引数渡し 1 段: 囲みメソッドの parameter へ渡された callable を索引から引く。 */
    private List<CallablePassIndex.CallableTarget> callablesForParameter(
            String parameterName, MethodCallExpr mce, CallGraphBuilder.WalkContext ctx) {
        if (callablePassIndex.isEmpty() || ctx.callerMethodIds().size() != 1) {
            return List.of();
        }
        MethodDeclaration enclosing = mce.findAncestor(MethodDeclaration.class).orElse(null);
        if (enclosing == null) {
            return List.of();
        }
        NodeList<Parameter> parameters = enclosing.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i).getNameAsString().equals(parameterName)) {
                return callablePassIndex.callablesFor(ctx.callerMethodIds().get(0), i);
            }
        }
        return List.of();
    }

    /**
     * 同一メソッド内: lambda / method reference を直接 initializer に持ち、再代入の
     * ない local 変数の invocation。再代入がある場合は追跡しない (根拠のない実体を
     * 推測しない)。
     */
    private List<CallablePassIndex.CallableTarget> callablesForLocal(String variableName, MethodCallExpr mce) {
        Node enclosing = mce.findAncestor(MethodDeclaration.class).map(Node.class::cast)
                .or(() -> mce.findAncestor(ConstructorDeclaration.class).map(Node.class::cast))
                .orElse(null);
        if (enclosing == null) {
            return List.of();
        }
        boolean reassigned = enclosing
                .findAll(AssignExpr.class).stream()
                .anyMatch(assign -> assign.getTarget() instanceof NameExpr name
                        && name.getNameAsString().equals(variableName));
        if (reassigned) {
            return List.of();
        }
        // invocation より字句的に前に宣言され、囲み block が invocation を含む declarator
        // だけを候補にする。複数一致は推測なしに 1 つへ絞れないため追跡を諦める。
        List<VariableDeclarator> candidates = new ArrayList<>();
        for (VariableDeclarator declarator
                : enclosing.findAll(VariableDeclarator.class)) {
            if (!declarator.getNameAsString().equals(variableName)
                    || !beginsBefore(declarator, mce)
                    || !enclosingBlockContains(declarator, mce)) {
                continue;
            }
            candidates.add(declarator);
        }
        if (candidates.size() != 1) {
            return List.of();
        }
        Expression initializer = candidates.get(0).getInitializer().orElse(null);
        if (initializer == null) {
            return List.of();
        }
        try {
            CallablePassIndex.CallableTarget target = CallablePassIndex.targetOf(initializer);
            return target != null ? List.of(target) : List.<CallablePassIndex.CallableTarget>of();
        } catch (RuntimeException | LinkageError e) {
            return List.of();
        }
    }

    private static boolean beginsBefore(Node first, Node second) {
        return first.getBegin().isPresent() && second.getBegin().isPresent()
                && first.getBegin().get().isBefore(second.getBegin().get());
    }

    /** declarator の直近ブロックが invocation を字句的に包含するかを判定する。 */
    private static boolean enclosingBlockContains(Node declarator, Node invocation) {
        Node block = declarator.findAncestor(BlockStmt.class).orElse(null);
        if (block == null) {
            return false;
        }
        for (Node ancestor = invocation; ancestor != null; ancestor = ancestor.getParentNode().orElse(null)) {
            if (ancestor == block) {
                return true;
            }
        }
        return false;
    }
}
