package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.JavaDiagnosticCode;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResolver;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteId;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteInventory;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteOutcomeLedger;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.ProjectBytecodeMemberIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.WorkspaceSourceDeclarationIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.context.SolverOriginIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResult;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.TypeSite;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.MethodIds;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.RelativePaths;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.spring.SpringDiIndex;
import com.fukuemon.depwalk.javaanalyzer.protocol.Diagnostic;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;
import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedIntersectionType;
import com.github.javaparser.resolution.types.ResolvedType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * AST を 1 ファイルずつ走査し、declared method / constructor / static initializer の
 * {@code methodSymbol} と、呼び出し式ごとの帰属型決定 + {@code callEdge} を {@link GraphAccumulator}
 * へ積み上げる。呼び出し先の型解決に AST ノードの identity が必要な場面
 * ({@link BinaryNames#forTypeLikeNode(Node)}) は、ファイルの visit 順序に依存しない純粋な構造計算で
 * 実装しているため、ファイル単位で 1 パス (declare + call-edge を同時に処理) で完結できる。
 */
public final class CallGraphBuilder {

    private final Path workspaceRoot;
    private final AttributionResolver attributionResolver;
    private final GraphAccumulator accumulator;
    private final SootUpTypeHierarchyIndex sootUpIndex;
    private final SourceMethodIndex sourceMethodIndex;
    private final Map<String, List<SpringDiIndex.InjectionResolution>> springResolutionsByReceiver;
    // source 再対応付けが参照する solver origin 境界 (spec #24 D6 / D16)。
    private final SolverOriginIndex solverOrigins;
    private final CallSiteOutcomeLedger ledger;
    private final WorkspaceSourceDeclarationIndex declIndex;
    private final ProjectBytecodeMemberIndex bytecodeIndex;
    private final String contextId;
    private final java.util.Set<String> reachableContextIds;
    /** 現在処理中 CU の workspace 相対 path ({@link #process} が設定する)。 */
    private String currentPath;

    /**
     * 解析実行中に共有する索引と出力 accumulator を使う graph builder を生成する。
     *
     * @param workspaceRoot source location を相対化する workspace root
     * @param attributionResolver scope 内外と帰属型を決定する resolver
     * @param accumulator method symbol、call edge、diagnostic の出力先
     * @param sootUpIndex bytecode 型階層から実装候補を得る索引
     * @param sourceMethodIndex 候補メソッドの source location を補完する索引
     * @param springResult Spring Bean と注入点の解決結果
     * @param solverOrigins 所有 context の solver entry と origin の対応
     */
    public CallGraphBuilder(
            Path workspaceRoot,
            AttributionResolver attributionResolver,
            GraphAccumulator accumulator,
            SootUpTypeHierarchyIndex sootUpIndex,
            SourceMethodIndex sourceMethodIndex,
            SpringDiIndex.Result springResult,
            SolverOriginIndex solverOrigins,
            CallSiteOutcomeLedger ledger,
            WorkspaceSourceDeclarationIndex declIndex,
            ProjectBytecodeMemberIndex bytecodeIndex,
            String contextId,
            java.util.Set<String> reachableContextIds) {
        this.workspaceRoot = workspaceRoot;
        this.attributionResolver = attributionResolver;
        this.accumulator = accumulator;
        this.sootUpIndex = sootUpIndex;
        this.sourceMethodIndex = sourceMethodIndex;
        this.solverOrigins = solverOrigins;
        this.ledger = ledger;
        this.declIndex = declIndex;
        this.bytecodeIndex = bytecodeIndex;
        this.contextId = contextId;
        this.reachableContextIds = reachableContextIds;
        this.springResolutionsByReceiver = new LinkedHashMap<>();
        for (SpringDiIndex.InjectionResolution resolution : springResult.resolutions()) {
            SpringDiIndex.InjectionPoint injection = resolution.injectionPoint();
            for (String receiverName : injection.receiverNames()) {
                springResolutionsByReceiver
                        .computeIfAbsent(
                                springReceiverKey(injection.ownerType(), receiverName),
                                ignored -> new ArrayList<>())
                        .add(resolution);
            }
        }
    }

    /**
     * 1つの compilation unit を走査し、発見した node、edge、diagnostic を accumulator へ追加する。
     *
     * @param cu 解析対象の compilation unit。呼び出し後に参照は保持しない
     */
    public void process(CompilationUnit cu) {
        currentPath = cu.getStorage()
                .map(storage -> RelativePaths.toRecordPath(
                        workspaceRoot.relativize(storage.getPath().toAbsolutePath().normalize()).toString()))
                .orElseThrow(() -> new IllegalStateException("compilation unit without storage path"));
        walk(cu, new WalkContext(null, List.of(), false));
    }

    private record WalkContext(Node enclosingTypeNode, List<String> callerMethodIds, boolean viaLambda) {
        WalkContext withCaller(List<String> callerIds) {
            return new WalkContext(enclosingTypeNode, callerIds, viaLambda);
        }

        WalkContext withEnclosingType(Node typeNode) {
            return new WalkContext(typeNode, List.of(), viaLambda);
        }

        WalkContext withViaLambda() {
            return new WalkContext(enclosingTypeNode, callerMethodIds, true);
        }
    }

    private void walk(Node node, WalkContext ctx) {
        if (node instanceof TypeDeclaration<?> td) {
            recurseChildren(node, ctx.withEnclosingType(td));
            return;
        }
        if (node instanceof MethodDeclaration md) {
            MethodSymbol symbol;
            try {
                ResolvedMethodDeclaration resolved = md.resolve();
                symbol = buildMethodSymbol(AttributionResult.scopeInternal(BinaryNames.forTypeLikeNode(ctx.enclosingTypeNode())), resolved);
            } catch (RuntimeException | LinkageError e) {
                reportUnresolvedDeclaration(md, "failed to resolve method declaration: " + md.getNameAsString());
                recurseChildren(node, ctx.withCaller(List.of(
                        CallSiteInventory.CallerIdentities.methodCallerId(ctx.enclosingTypeNode(), md, currentPath))));
                return;
            }
            accumulator.addNode(symbol);
            recurseChildren(node, ctx.withCaller(List.of(symbol.methodId())));
            return;
        }
        if (node instanceof ConstructorDeclaration cd) {
            MethodSymbol symbol;
            try {
                ResolvedConstructorDeclaration resolved = cd.resolve();
                symbol = buildConstructorSymbol(AttributionResult.scopeInternal(BinaryNames.forTypeLikeNode(ctx.enclosingTypeNode())), resolved);
            } catch (RuntimeException | LinkageError e) {
                reportUnresolvedDeclaration(cd, "failed to resolve constructor declaration: " + cd.getNameAsString());
                recurseChildren(node, ctx.withCaller(List.of(
                        CallSiteInventory.CallerIdentities.constructorCallerId(ctx.enclosingTypeNode(), cd, currentPath))));
                return;
            }
            accumulator.addNode(symbol);
            recurseChildren(node, ctx.withCaller(List.of(symbol.methodId())));
            return;
        }
        if (node instanceof CompactConstructorDeclaration ccd) {
            // record の compact constructor (`record User(String name) { User { validate(); } }`)。
            // canonical constructor 扱いとし、signature は record component の erasure 型列にする。
            // JavaParser の CompactConstructorDeclaration#resolve() は未実装
            // (UnsupportedOperationException) なため、record の component 列から自前で計算する。
            MethodSymbol symbol;
            try {
                symbol = buildCompactConstructorSymbol(ctx.enclosingTypeNode(), ccd);
            } catch (RuntimeException | LinkageError e) {
                reportUnresolvedDeclaration(ccd, "failed to resolve compact constructor declaration: " + ccd.getNameAsString());
                recurseChildren(node, ctx.withCaller(List.of(
                        CallSiteInventory.CallerIdentities.compactConstructorCallerId(ctx.enclosingTypeNode(), ccd, currentPath))));
                return;
            }
            accumulator.addNode(symbol);
            recurseChildren(node, ctx.withCaller(List.of(symbol.methodId())));
            return;
        }
        if (node instanceof InitializerDeclaration id) {
            if (!containsAnyCall(id)) {
                recurseChildren(node, ctx);
                return;
            }
            if (id.isStatic()) {
                String clinitId = ensureStaticInitializerNode(ctx.enclosingTypeNode());
                recurseChildren(node, ctx.withCaller(List.of(clinitId)));
            } else {
                recurseChildren(node, ctx.withCaller(constructorCallerIdsFor(ctx.enclosingTypeNode())));
            }
            return;
        }
        if (node instanceof FieldDeclaration fd) {
            if (!containsAnyCall(fd)) {
                recurseChildren(node, ctx);
                return;
            }
            if (fd.isStatic()) {
                String clinitId = ensureStaticInitializerNode(ctx.enclosingTypeNode());
                recurseChildren(node, ctx.withCaller(List.of(clinitId)));
            } else {
                recurseChildren(node, ctx.withCaller(constructorCallerIdsFor(ctx.enclosingTypeNode())));
            }
            return;
        }
        if (node instanceof LambdaExpr) {
            recurseChildren(node, ctx.withViaLambda());
            return;
        }
        if (node instanceof MethodCallExpr mce) {
            processMethodCall(mce, ctx);
            recurseChildren(node, ctx);
            return;
        }
        if (node instanceof MethodReferenceExpr mre) {
            processMethodReference(mre, ctx);
            recurseChildren(node, ctx);
            return;
        }
        if (node instanceof ObjectCreationExpr oce) {
            processObjectCreation(oce, ctx);
            for (Node argument : oce.getArguments()) {
                walk(argument, ctx);
            }
            oce.getScope().ifPresent(scope -> walk(scope, ctx));
            if (oce.getAnonymousClassBody().isPresent()) {
                WalkContext bodyCtx = ctx.withEnclosingType(oce);
                for (BodyDeclaration<?> member : oce.getAnonymousClassBody().get()) {
                    walk(member, bodyCtx);
                }
            }
            return;
        }
        if (node instanceof ExplicitConstructorInvocationStmt ecis) {
            processExplicitConstructorInvocation(ecis, ctx);
            for (Node argument : ecis.getArguments()) {
                walk(argument, ctx);
            }
            return;
        }
        recurseChildren(node, ctx);
    }

    private void recurseChildren(Node node, WalkContext ctx) {
        for (Node child : node.getChildNodes()) {
            walk(child, ctx);
        }
    }

    private boolean containsAnyCall(Node node) {
        return !node.findAll(MethodCallExpr.class).isEmpty()
                || !node.findAll(ObjectCreationExpr.class).isEmpty()
                || !node.findAll(ExplicitConstructorInvocationStmt.class).isEmpty()
                || !node.findAll(MethodReferenceExpr.class).isEmpty();
    }

    // ------------------------------------------------------------------
    // method call processing
    // ------------------------------------------------------------------

    private void processMethodCall(MethodCallExpr mce, WalkContext ctx) {
        ResolvedMethodDeclaration resolved;
        try {
            resolved = mce.resolve();
        } catch (RuntimeException | LinkageError e) {
            if (tryBytecodeMethodRescue(mce, ctx)) {
                commitEmitted(mce, CallSiteId.CallKind.METHOD_CALL, ctx);
                return;
            }
            reportUnresolved(mce, ctx);
            commitDiagnostic(mce, CallSiteId.CallKind.METHOD_CALL, ctx,
                    "unresolved-method-call", mce.getNameAsString());
            return;
        }

        TypeSite declaringSite = typeSiteOf(resolved.declaringType());
        TypeSite receiverSite = receiverSiteOf(mce, ctx, resolved, declaringSite);
        AttributionResult attribution = attributionResolver.resolveMethod(declaringSite, receiverSite);
        if (attribution.isOmitted()) {
            commitExcluded(mce, CallSiteId.CallKind.METHOD_CALL, ctx, attribution);
            return;
        }

        MethodSymbol calleeSymbol = buildMethodSymbol(attribution, resolved);
        accumulator.addNode(calleeSymbol);

        String dispatch = dispatchOf(resolved);
        Map<String, Object> metadata = edgeMetadata(dispatch, ctx.viaLambda());
        SourceLocation callSite = sourceLocationOf(mce);
        for (String callerId : edgeCallers(mce, ctx)) {
            accumulator.addEdge(callerId, calleeSymbol.methodId(), callSite, metadata);
        }
        commitEmitted(mce, CallSiteId.CallKind.METHOD_CALL, ctx);
        emitDispatchCandidateEdges(
                resolved,
                dispatch,
                receiverSite,
                mce,
                ctx,
                callSite,
                calleeSymbol.methodId());
    }

    private void processObjectCreation(ObjectCreationExpr oce, WalkContext ctx) {
        ResolvedConstructorDeclaration resolved;
        try {
            resolved = oce.resolve();
        } catch (RuntimeException | LinkageError e) {
            if (tryBytecodeConstructorRescue(oce, ctx)) {
                commitEmitted(oce, CallSiteId.CallKind.OBJECT_CREATION, ctx);
                return;
            }
            reportUnresolved(oce, ctx);
            commitDiagnostic(oce, CallSiteId.CallKind.OBJECT_CREATION, ctx,
                    "unresolved-constructor-call", oce.getTypeAsString());
            return;
        }
        emitConstructorCall(resolved, oce, ctx, CallSiteId.CallKind.OBJECT_CREATION);
    }

    private void processExplicitConstructorInvocation(ExplicitConstructorInvocationStmt ecis, WalkContext ctx) {
        ResolvedConstructorDeclaration resolved;
        try {
            resolved = ecis.resolve();
        } catch (RuntimeException | LinkageError e) {
            reportUnresolved(ecis, ctx);
            commitDiagnostic(ecis, CallSiteId.CallKind.EXPLICIT_CONSTRUCTOR_INVOCATION, ctx,
                    "unresolved-constructor-call", ecis.isThis() ? "this" : "super");
            return;
        }
        emitConstructorCall(resolved, ecis, ctx, CallSiteId.CallKind.EXPLICIT_CONSTRUCTOR_INVOCATION);
    }

    private void emitConstructorCall(
            ResolvedConstructorDeclaration resolved, Node callNode, WalkContext ctx, CallSiteId.CallKind kind) {
        TypeSite declaringSite = typeSiteOf(resolved.declaringType());
        AttributionResult attribution = attributionResolver.resolveConstructor(declaringSite);
        if (attribution.isOmitted()) {
            commitExcluded(callNode, kind, ctx, attribution);
            return;
        }
        MethodSymbol calleeSymbol = buildConstructorSymbol(attribution, resolved);
        accumulator.addNode(calleeSymbol);

        Map<String, Object> metadata = edgeMetadata(null, ctx.viaLambda());
        SourceLocation callSite = sourceLocationOf(callNode);
        for (String callerId : edgeCallers(callNode, ctx)) {
            accumulator.addEdge(callerId, calleeSymbol.methodId(), callSite, metadata);
        }
        commitEmitted(callNode, kind, ctx);
    }

    /** {@code Foo::new} の source 上の識別子 ({@code getIdentifier()} が返す値)。 */
    private static final String METHOD_REFERENCE_CONSTRUCTOR_IDENTIFIER = "new";

    /**
     * lambda と同じ囲みメソッドへの帰属規則を method reference
     * ({@code this::toDto} / {@code Foo::bar} / {@code Foo::new}) に適用する。囲みメソッドを caller、
     * 通常の帰属規則を適用した参照先メソッドを callee とする {@code callEdge} を出力し、
     * {@code metadata.viaMethodReference: true} で標識する (Core は metadata を解釈しないため契約変更なし)。
     */
    private void processMethodReference(MethodReferenceExpr mre, WalkContext ctx) {
        if (METHOD_REFERENCE_CONSTRUCTOR_IDENTIFIER.equals(mre.getIdentifier())) {
            processConstructorReference(mre, ctx);
            return;
        }

        ResolvedMethodDeclaration resolved;
        try {
            resolved = mre.resolve();
        } catch (RuntimeException | LinkageError e) {
            reportUnresolved(mre, ctx);
            commitDiagnostic(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                    "unresolved-method-reference", mre.getIdentifier());
            return;
        }

        TypeSite declaringSite = typeSiteOf(resolved.declaringType());
        TypeSite receiverSite = typeSiteOfExpression(mre.getScope());
        AttributionResult attribution = attributionResolver.resolveMethod(declaringSite, receiverSite);
        if (attribution.isOmitted()) {
            commitExcluded(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx, attribution);
            return;
        }

        MethodSymbol calleeSymbol = buildMethodSymbol(attribution, resolved);
        accumulator.addNode(calleeSymbol);

        String dispatch = dispatchOf(resolved);
        Map<String, Object> metadata = methodReferenceEdgeMetadata(dispatch, ctx.viaLambda());
        SourceLocation callSite = sourceLocationOf(mre);
        for (String callerId : edgeCallers(mre, ctx)) {
            accumulator.addEdge(callerId, calleeSymbol.methodId(), callSite, metadata);
        }
        commitEmitted(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx);
        emitDispatchCandidateEdges(
                resolved,
                dispatch,
                receiverSite,
                mre,
                ctx,
                callSite,
                calleeSymbol.methodId());
    }

    /**
     * constructor reference ({@code Foo::new}) の扱い。constructor は継承されないため帰属型の
     * 引き上げを行わず、scope 外なら出力しない。{@code JavaParser} は
     * constructor reference の {@code resolve()} を未実装 ({@code UnsupportedOperationException}) と
     * しているため、参照先型の constructor 一覧から候補を自前で選ぶ (単一候補ならそれを使い、複数候補
     * のときは呼び出し先の関数型インタフェースの SAM 引数数で絞り込む)。
     */
    private void processConstructorReference(MethodReferenceExpr mre, WalkContext ctx) {
        ResolvedReferenceTypeDeclaration scopeDecl;
        try {
            ResolvedType scopeType = mre.getScope().calculateResolvedType();
            if (!scopeType.isReferenceType()) {
                reportUnresolved(mre, ctx);
                commitDiagnostic(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                        "unresolved-constructor-reference", mre.getScope().toString());
                return;
            }
            scopeDecl = scopeType.asReferenceType().getTypeDeclaration().orElse(null);
            if (scopeDecl == null) {
                reportUnresolved(mre, ctx);
                commitDiagnostic(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                        "unresolved-constructor-reference", mre.getScope().toString());
                return;
            }
        } catch (RuntimeException | LinkageError e) {
            reportUnresolved(mre, ctx);
            commitDiagnostic(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                    "unresolved-constructor-reference", mre.getScope().toString());
            return;
        }

        TypeSite declaringSite = typeSiteOf(scopeDecl);
        AttributionResult attribution = attributionResolver.resolveConstructor(declaringSite);
        if (attribution.isOmitted()) {
            commitExcluded(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx, attribution);
            return;
        }

        ResolvedConstructorDeclaration resolvedCtor = selectConstructor(scopeDecl.getConstructors(), mre);
        if (resolvedCtor == null) {
            reportUnresolved(mre, ctx);
            commitDiagnostic(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                    "ambiguous-constructor-reference", mre.getScope().toString());
            return;
        }

        MethodSymbol calleeSymbol = buildConstructorSymbol(attribution, resolvedCtor);
        accumulator.addNode(calleeSymbol);

        Map<String, Object> metadata = methodReferenceEdgeMetadata(null, ctx.viaLambda());
        SourceLocation callSite = sourceLocationOf(mre);
        for (String callerId : edgeCallers(mre, ctx)) {
            accumulator.addEdge(callerId, calleeSymbol.methodId(), callSite, metadata);
        }
        commitEmitted(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx);
    }

    /**
     * constructor reference の候補選択。候補が 1 つならそれを使う。複数の overload があるときは、
     * {@code mre} が代入される関数型インタフェースの SAM (single abstract method) の引数数に一致する
     * ものを選ぶ。一致がゼロ / 複数 (曖昧) / SAM 引数数が推論できない場合は {@code null} (呼び出し側で
     * {@code JAVA_UNRESOLVED_SYMBOL} diagnostic にする)。
     */
    private ResolvedConstructorDeclaration selectConstructor(List<ResolvedConstructorDeclaration> ctors, MethodReferenceExpr mre) {
        if (ctors.isEmpty()) {
            return null;
        }
        if (ctors.size() == 1) {
            return ctors.get(0);
        }
        int arity = inferFunctionalInterfaceArity(mre);
        if (arity < 0) {
            return null;
        }
        ResolvedConstructorDeclaration match = null;
        for (ResolvedConstructorDeclaration ctor : ctors) {
            if (ctor.getNumberOfParams() == arity) {
                if (match != null) {
                    return null;
                }
                match = ctor;
            }
        }
        return match;
    }

    /** {@code mre} が代入される関数型インタフェースの SAM の引数数。推論できなければ {@code -1}。 */
    private int inferFunctionalInterfaceArity(MethodReferenceExpr mre) {
        try {
            ResolvedType functionalType = mre.calculateResolvedType();
            if (!functionalType.isReferenceType()) {
                return -1;
            }
            ResolvedReferenceTypeDeclaration decl = functionalType.asReferenceType().getTypeDeclaration().orElse(null);
            if (decl == null) {
                return -1;
            }
            for (MethodUsage methodUsage : decl.getAllMethods()) {
                if (methodUsage.getDeclaration().isAbstract()) {
                    return methodUsage.getNoParams();
                }
            }
        } catch (RuntimeException | LinkageError e) {
            return -1;
        }
        return -1;
    }

    private Map<String, Object> methodReferenceEdgeMetadata(String dispatch, boolean viaLambda) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (dispatch != null) {
            metadata.put("dispatch", dispatch);
        }
        if (viaLambda) {
            metadata.put("viaLambda", true);
        }
        metadata.put("viaMethodReference", true);
        return metadata;
    }


    // ------------------------------------------------------------------
    // call-site outcome ledger (spec #24 D14 / D17 / D20)
    // ------------------------------------------------------------------

    /** ledger 用の実効 caller (caller 不在の site は <clinit> / placeholder へ帰着)。 */
    private List<String> ledgerCallers(Node callNode, WalkContext ctx) {
        return CallSiteInventory.CallerIdentities.effectiveCallers(
                ctx.callerMethodIds(), ctx.enclosingTypeNode(), callNode, currentPath);
    }

    /** edge 出力に使える caller (placeholder を除外し、caller 不在時は <clinit> node を保証する)。 */
    private List<String> edgeCallers(Node callNode, WalkContext ctx) {
        List<String> callers = ledgerCallers(callNode, ctx);
        List<String> result = new ArrayList<>();
        for (String caller : callers) {
            if (CallSiteInventory.CallerIdentities.isPlaceholder(caller)) {
                continue;
            }
            if (ctx.callerMethodIds().isEmpty() && ctx.enclosingTypeNode() != null) {
                // enum constant 引数など member 外の call は <clinit> caller で edge 化する。
                ensureStaticInitializerNode(ctx.enclosingTypeNode());
            }
            result.add(caller);
        }
        return result;
    }

    private void commitEmitted(Node callNode, CallSiteId.CallKind kind, WalkContext ctx) {
        for (String caller : ledgerCallers(callNode, ctx)) {
            ledger.commitEmitted(CallSiteInventory.of(callNode, currentPath, kind, caller));
        }
    }

    private void commitExcluded(Node callNode, CallSiteId.CallKind kind, WalkContext ctx, AttributionResult attribution) {
        String reason = attribution.outcome() == AttributionResult.Outcome.OMIT_EXCLUDED
                ? CallSiteOutcomeLedger.REASON_LIFT_EXCLUDED_PACKAGE
                : CallSiteOutcomeLedger.REASON_EXTERNAL_TARGET;
        for (String caller : ledgerCallers(callNode, ctx)) {
            ledger.commitExcluded(CallSiteInventory.of(callNode, currentPath, kind, caller), reason);
        }
    }

    private void commitDiagnostic(Node callNode, CallSiteId.CallKind kind, WalkContext ctx, String reason, String target) {
        for (String caller : ledgerCallers(callNode, ctx)) {
            ledger.commitDiagnostic(
                    CallSiteInventory.of(callNode, currentPath, kind, caller),
                    JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.code(),
                    reason,
                    target,
                    null);
        }
    }

    /**
     * 解決失敗した method call を、scope 内 source type の到達可能な project
     * bytecode の一意 member へ generator 非依存で救済する (spec #24 D18 / D21)。
     */
    private boolean tryBytecodeMethodRescue(MethodCallExpr mce, WalkContext ctx) {
        String ownerBinaryName = bytecodeRescueOwner(mce, ctx);
        if (ownerBinaryName == null) {
            return false;
        }
        WorkspaceSourceDeclarationIndex.TypeLocation owner = declIndex.find(ownerBinaryName).orElse(null);
        if (owner == null || !reachableContextIds.contains(owner.contextId())) {
            return false;
        }
        var candidate = bytecodeIndex.uniqueMethod(ownerBinaryName, mce.getNameAsString(), mce.getArguments().size())
                .orElse(null);
        if (candidate == null) {
            return false;
        }
        emitBytecodeOnlyCall(mce, ctx, owner,
                candidate.declaringType(), candidate.methodName(), candidate.parameterTypes(), "method");
        return true;
    }

    /** 解決失敗した object creation の bytecode-only constructor 救済。 */
    private boolean tryBytecodeConstructorRescue(ObjectCreationExpr oce, WalkContext ctx) {
        String ownerBinaryName;
        try {
            ownerBinaryName = BinaryNames.erasureOf(oce.getType().resolve());
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
        WorkspaceSourceDeclarationIndex.TypeLocation owner = declIndex.find(ownerBinaryName).orElse(null);
        if (owner == null || !reachableContextIds.contains(owner.contextId())) {
            return false;
        }
        var candidate = bytecodeIndex.uniqueConstructor(ownerBinaryName, oce.getArguments().size()).orElse(null);
        if (candidate == null) {
            return false;
        }
        emitBytecodeOnlyCall(oce, ctx, owner,
                candidate.declaringType(), MethodIds.CONSTRUCTOR_TOKEN, candidate.parameterTypes(), "constructor");
        return true;
    }

    /** 救済対象 method call の owner (receiver の static type、暗黙 this は囲み型)。 */
    private String bytecodeRescueOwner(MethodCallExpr mce, WalkContext ctx) {
        try {
            if (mce.getScope().isPresent()) {
                ResolvedType scopeType = mce.getScope().get().calculateResolvedType();
                if (!scopeType.isReferenceType()) {
                    return null;
                }
                return BinaryNames.erasureOf(scopeType);
            }
            return ctx.enclosingTypeNode() != null ? BinaryNames.forTypeLikeNode(ctx.enclosingTypeNode()) : null;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private void emitBytecodeOnlyCall(
            Node callNode,
            WalkContext ctx,
            WorkspaceSourceDeclarationIndex.TypeLocation owner,
            String declaringType,
            String methodNameToken,
            List<String> parameterTypes,
            String symbolKind) {
        String signature = MethodIds.signature(declaringType, methodNameToken, parameterTypes);
        String methodId = MethodIds.methodId(signature);
        if (owner.path() == null) {
            throw new IllegalStateException(
                    "adopted a bytecode-only member without a constructible owner location: " + methodId);
        }
        String qualifiedName = declaringType.replace('$', '.') + "." + methodNameToken;
        Map<String, Object> symbolMetadata = new LinkedHashMap<>();
        symbolMetadata.put("declarationOrigin", "project-bytecode");
        symbolMetadata.put("sourceAnchor", "owner-type");
        Map<String, Object> ownerLocation = new LinkedHashMap<>();
        ownerLocation.put("path", owner.path());
        ownerLocation.put("startLine", owner.beginLine());
        symbolMetadata.put("ownerSourceLocation", ownerLocation);
        // 定義位置を偽装しない: sourceLocation は省略し、owner 位置は metadata へ分離する (D21)。
        accumulator.addNode(MethodSymbol.of(
                methodId, "java", symbolKind, qualifiedName, signature, null, symbolMetadata));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("calleeOrigin", "project-bytecode-member");
        if (ctx.viaLambda()) {
            metadata.put("viaLambda", true);
        }
        SourceLocation callSite = sourceLocationOf(callNode);
        for (String callerId : edgeCallers(callNode, ctx)) {
            accumulator.addEdge(callerId, methodId, callSite, metadata);
        }
    }

    private void reportUnresolved(Node callNode, WalkContext ctx) {
        accumulator.incrementUnresolved();
        String relatedMethodId = ctx.callerMethodIds().isEmpty() ? null : ctx.callerMethodIds().get(0);
        accumulator.addDiagnostic(Diagnostic.of(
                JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.severity(),
                JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.code(),
                "failed to resolve call: " + callNode,
                sourceLocationOf(callNode),
                relatedMethodId,
                null));
    }

    /**
     * 宣言列挙側 ({@code md.resolve()} / {@code cd.resolve()}) の解決失敗。呼び出し式側の
     * {@link #reportUnresolved(Node, WalkContext)} と異なり、宣言そのものが対象のため
     * {@code relatedMethodId} は付けない。その宣言だけ skip し、解析全体は継続する。
     */
    private void reportUnresolvedDeclaration(Node declNode, String message) {
        accumulator.incrementUnresolved();
        accumulator.addDiagnostic(Diagnostic.of(
                JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.severity(),
                JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.code(),
                message,
                sourceLocationOf(declNode),
                null,
                null));
    }

    // ------------------------------------------------------------------
    // node construction
    // ------------------------------------------------------------------

    private MethodSymbol buildMethodSymbol(AttributionResult attribution, ResolvedMethodDeclaration resolved) {
        String declaringBinaryName = attribution.attributedBinaryName();
        List<String> paramTypes = paramBinaryNames(resolved);
        String signature = MethodIds.signature(declaringBinaryName, resolved.getName(), paramTypes);
        String methodId = MethodIds.methodId(signature);
        String qualifiedName = declaringBinaryName.replace('$', '.') + "." + resolved.getName();

        SourceLocation sourceLocation = null;
        Map<String, Object> metadata = null;
        if (attribution.outcome() == AttributionResult.Outcome.SCOPE_INTERNAL) {
            Node ast = resolved.toAst().orElse(null);
            sourceLocation = ast != null ? sourceLocationOf(ast) : null;
        } else if (attribution.outcome() == AttributionResult.Outcome.LIFTED) {
            metadata = Map.of(
                    "declaringType", attribution.declaringTypeBinaryName(),
                    "inherited", true);
        }
        return MethodSymbol.of(methodId, "java", "method", qualifiedName, signature, sourceLocation, metadata);
    }

    private MethodSymbol buildConstructorSymbol(AttributionResult attribution, ResolvedConstructorDeclaration resolved) {
        String declaringBinaryName = attribution.attributedBinaryName();
        List<String> paramTypes = paramBinaryNames(resolved);
        String signature = MethodIds.signature(declaringBinaryName, MethodIds.CONSTRUCTOR_TOKEN, paramTypes);
        String methodId = MethodIds.methodId(signature);
        String qualifiedName = declaringBinaryName.replace('$', '.') + "." + MethodIds.CONSTRUCTOR_TOKEN;

        SourceLocation sourceLocation = null;
        Map<String, Object> metadata = null;
        if (attribution.outcome() == AttributionResult.Outcome.SCOPE_INTERNAL) {
            // synthetic default constructor / record canonical constructor は自身の AST を持たない
            // ({@code toAst()} が空、record の場合は compact constructor の有無によらず常に空) ため、
            // 宣言型の AST 位置へフォールバックする。これにより ensureDefaultConstructorNode /
            // buildCompactConstructorSymbol と同一内容になり、同一 methodId の node がどの経路から
            // 生成されても内容が一致する (GraphAccumulator の first-wins 重複排除で情報が失われない)。
            // record に compact constructor があれば、宣言型そのものより精度の高い compact constructor
            // の位置を使う (buildCompactConstructorSymbol と揃える)。
            Node ast = resolved.toAst().orElse(null);
            if (ast == null) {
                ast = preferCompactConstructorLocation(resolved.declaringType().toAst().orElse(null));
            }
            sourceLocation = ast != null ? sourceLocationOf(ast) : null;
        } else if (attribution.outcome() == AttributionResult.Outcome.LIFTED) {
            metadata = Map.of(
                    "declaringType", attribution.declaringTypeBinaryName(),
                    "inherited", true);
        }
        return MethodSymbol.of(methodId, "java", "constructor", qualifiedName, signature, sourceLocation, metadata);
    }

    /**
     * {@code typeAst} が record で compact constructor を持つ場合、その位置を優先して返す
     * (宣言型全体より精度の高い sourceLocation にする)。該当しなければ {@code typeAst} をそのまま返す。
     */
    private Node preferCompactConstructorLocation(Node typeAst) {
        if (typeAst instanceof RecordDeclaration rd) {
            for (BodyDeclaration<?> member : rd.getMembers()) {
                if (member instanceof CompactConstructorDeclaration ccd) {
                    return ccd;
                }
            }
        }
        return typeAst;
    }

    /**
     * record の compact constructor を canonical constructor として扱い、その {@link MethodSymbol}
     * を作る。signature は record component の erasure 型列 (宣言順)。JavaParser の
     * {@code CompactConstructorDeclaration#resolve()} は未実装のため、record の component 一覧
     * ({@link RecordDeclaration#getParameters()}) から自前で param 型を求める。
     */
    private MethodSymbol buildCompactConstructorSymbol(Node enclosingTypeNode, CompactConstructorDeclaration ccd) {
        if (!(enclosingTypeNode instanceof RecordDeclaration rd)) {
            throw new IllegalStateException("compact constructor outside of a record declaration: " + ccd);
        }
        String declaringBinaryName = BinaryNames.forTypeLikeNode(enclosingTypeNode);
        List<String> paramTypes = new ArrayList<>();
        for (Parameter component : rd.getParameters()) {
            paramTypes.add(BinaryNames.erasureOf(component.resolve().getType()));
        }
        String signature = MethodIds.signature(declaringBinaryName, MethodIds.CONSTRUCTOR_TOKEN, paramTypes);
        String methodId = MethodIds.methodId(signature);
        String qualifiedName = declaringBinaryName.replace('$', '.') + "." + MethodIds.CONSTRUCTOR_TOKEN;
        SourceLocation sourceLocation = sourceLocationOf(ccd);
        return MethodSymbol.of(methodId, "java", "constructor", qualifiedName, signature, sourceLocation, null);
    }

    private String ensureStaticInitializerNode(Node enclosingType) {
        String declaringBinaryName = BinaryNames.forTypeLikeNode(enclosingType);
        String signature = MethodIds.signature(declaringBinaryName, MethodIds.STATIC_INITIALIZER_TOKEN, List.of());
        String methodId = MethodIds.methodId(signature);
        if (!accumulator.hasNode(methodId)) {
            String qualifiedName = declaringBinaryName.replace('$', '.') + "." + MethodIds.STATIC_INITIALIZER_TOKEN;
            accumulator.addNode(MethodSymbol.of(
                    methodId, "java", "initializer", qualifiedName, signature, sourceLocationOf(enclosingType), null));
        }
        return methodId;
    }

    private List<String> constructorCallerIdsFor(Node enclosingType) {
        List<ConstructorDeclaration> constructors = new ArrayList<>();
        for (Node member : membersOf(enclosingType)) {
            if (member instanceof ConstructorDeclaration cd) {
                constructors.add(cd);
            }
        }
        if (constructors.isEmpty()) {
            return List.of(ensureDefaultConstructorNode(enclosingType));
        }
        List<String> ids = new ArrayList<>();
        String declaringBinaryName = BinaryNames.forTypeLikeNode(enclosingType);
        for (ConstructorDeclaration cd : constructors) {
            try {
                List<String> paramTypes = paramBinaryNames(cd.resolve());
                String signature = MethodIds.signature(declaringBinaryName, MethodIds.CONSTRUCTOR_TOKEN, paramTypes);
                ids.add(MethodIds.methodId(signature));
            } catch (RuntimeException | LinkageError e) {
                reportUnresolvedDeclaration(cd, "failed to resolve constructor declaration: " + cd.getNameAsString());
                ids.add(CallSiteInventory.CallerIdentities.constructorCallerId(enclosingType, cd, currentPath));
            }
        }
        return ids;
    }

    private String ensureDefaultConstructorNode(Node enclosingType) {
        String declaringBinaryName = BinaryNames.forTypeLikeNode(enclosingType);
        String signature = MethodIds.signature(declaringBinaryName, MethodIds.CONSTRUCTOR_TOKEN, List.of());
        String methodId = MethodIds.methodId(signature);
        if (!accumulator.hasNode(methodId)) {
            String qualifiedName = declaringBinaryName.replace('$', '.') + "." + MethodIds.CONSTRUCTOR_TOKEN;
            accumulator.addNode(MethodSymbol.of(
                    methodId, "java", "constructor", qualifiedName, signature, sourceLocationOf(enclosingType), null));
        }
        return methodId;
    }

    private List<Node> membersOf(Node typeLikeNode) {
        List<Node> members = new ArrayList<>();
        if (typeLikeNode instanceof TypeDeclaration<?> td) {
            for (BodyDeclaration<?> member : td.getMembers()) {
                members.add(member);
            }
        } else if (typeLikeNode instanceof ObjectCreationExpr oce) {
            NodeList<BodyDeclaration<?>> body = oce.getAnonymousClassBody().orElseGet(NodeList::new);
            for (BodyDeclaration<?> member : body) {
                members.add(member);
            }
        }
        return members;
    }

    // ------------------------------------------------------------------
    // attribution helpers
    // ------------------------------------------------------------------

    private TypeSite typeSiteOf(ResolvedReferenceTypeDeclaration decl) {
        Node ast = decl.toAst().orElse(null);
        String binaryName = BinaryNames.forResolvedDeclaration(decl);
        Path filePath = ast != null ? filePathOf(ast) : null;
        return new TypeSite(binaryName, filePath);
    }

    /**
     * mce の scope (レシーバ式) から帰属型決定に使う {@link TypeSite} を決める。
     *
     * <p>scope が空 (無修飾呼び出し) のとき、通常は enclosing class を「参照した型」とみなす
     * (自クラス呼び出し / 継承 static・instance メソッドの暗黙 this 呼び出し)。ただし static かつ
     * 宣言型が enclosing class の継承階層に含まれない場合は、無修飾 static import
     * ({@code import static pkg.Type.member;}) 由来の呼び出しであり、「参照した型」は enclosing class
     * ではなく宣言型そのものである。そのため receiverSite = declaringSite として扱い、宣言型が
     * scope 外なら enclosing への誤った引き上げを起こさず出力を省略する。
     */
    private TypeSite receiverSiteOf(MethodCallExpr mce, WalkContext ctx, ResolvedMethodDeclaration resolved, TypeSite declaringSite) {
        if (mce.getScope().isEmpty()) {
            Node enclosing = ctx.enclosingTypeNode();
            if (enclosing == null) {
                return null;
            }
            if (resolved.isStatic() && !declaringTypeInEnclosingHierarchy(enclosing, resolved.declaringType())) {
                return declaringSite;
            }
            return new TypeSite(BinaryNames.forTypeLikeNode(enclosing), filePathOf(enclosing));
        }
        return typeSiteOfExpression(mce.getScope().get());
    }

    /**
     * 式を評価した静的型の {@link TypeSite} を返す。
     *
     * <p>型変数や wildcard は、そのままでは reference type 宣言を取得できないため erasure を使う。
     * たとえば {@code T extends ChildService} の receiver は {@code ChildService} として扱い、
     * 上限境界に含まれない実装を dispatch 候補へ混入させない。解決できなければ {@code null} を返す。
     *
     * @param expr 静的 receiver 型を取得する式
     * @return 静的型の所在情報。型解決または erasure に失敗した場合は {@code null}
     */
    private TypeSite typeSiteOfExpression(Expression expr) {
        try {
            ResolvedType erasedReceiverType = expr.calculateResolvedType().erasure();
            if (!erasedReceiverType.isReferenceType()) {
                return null;
            }
            ResolvedReferenceTypeDeclaration decl =
                    erasedReceiverType.asReferenceType().getTypeDeclaration().orElse(null);
            if (decl == null) {
                return null;
            }
            return typeSiteOf(decl);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /**
     * declaringType が enclosingTypeNode 自身または、その継承階層 (supertype / interface) に含まれるか
     * を判定する。判定不能な場合 (enclosing の型解決失敗 / ancestor 列挙が unresolved symbol で失敗) は、
     * 従来の「enclosing への引き上げ」挙動を壊さないよう保守的に {@code true} を返す。
     */
    private boolean declaringTypeInEnclosingHierarchy(Node enclosingTypeNode, ResolvedReferenceTypeDeclaration declaringType) {
        String declaringBinaryName = BinaryNames.forResolvedDeclaration(declaringType);
        String enclosingBinaryName = BinaryNames.forTypeLikeNode(enclosingTypeNode);
        if (declaringBinaryName.equals(enclosingBinaryName)) {
            return true;
        }
        ResolvedReferenceTypeDeclaration enclosingDecl = resolveTypeLikeNode(enclosingTypeNode);
        if (enclosingDecl == null) {
            return true;
        }
        try {
            for (ResolvedReferenceType ancestor : enclosingDecl.getAllAncestors()) {
                ResolvedReferenceTypeDeclaration ancestorDecl = ancestor.getTypeDeclaration().orElse(null);
                if (ancestorDecl != null && declaringBinaryName.equals(BinaryNames.forResolvedDeclaration(ancestorDecl))) {
                    return true;
                }
            }
        } catch (RuntimeException | LinkageError e) {
            return true;
        }
        return false;
    }

    private ResolvedReferenceTypeDeclaration resolveTypeLikeNode(Node typeLikeNode) {
        try {
            if (typeLikeNode instanceof TypeDeclaration<?> td) {
                return td.resolve();
            }
            if (typeLikeNode instanceof ObjectCreationExpr oce) {
                ResolvedType type = oce.calculateResolvedType();
                return type.isReferenceType() ? type.asReferenceType().getTypeDeclaration().orElse(null) : null;
            }
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
        return null;
    }

    private String dispatchOf(ResolvedMethodDeclaration resolved) {
        if (resolved.isStatic()) {
            return "static";
        }
        if (resolved.declaringType().isInterface()) {
            return "interface";
        }
        if (resolved.isAbstract()) {
            return "abstract";
        }
        return "virtual";
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

    /** 型階層と Spring DI の候補を call site 単位で統合し、宣言型 edge とは別に実装候補 edge を追加する。 */
    private void emitDispatchCandidateEdges(
            ResolvedMethodDeclaration resolved,
            String dispatch,
            TypeSite receiverSite,
            Node callNode,
            WalkContext ctx,
            SourceLocation callSite,
            String declarationMethodId) {
        if ("static".equals(dispatch) || isExplicitSuperDispatch(callNode)) {
            return;
        }
        String declaringType = BinaryNames.forResolvedDeclaration(resolved.declaringType());
        List<String> parameterTypes = paramBinaryNames(resolved);
        String receiverType = receiverSite == null ? declaringType : receiverSite.binaryName();
        List<String> receiverTypes = receiverTypeConstraints(callNode, receiverType);
        SootUpTypeHierarchyIndex.Resolution sootResolution = sootUpIndex.resolveMethod(
                declaringType,
                receiverTypes,
                resolved.getName(),
                parameterTypes);
        if (!sootResolution.isAvailable()) {
            reportSootUnavailable(sootResolution, declaringType, callNode, ctx);
            return;
        }

        Map<String, CandidateEdgeInfo> merged = new LinkedHashMap<>();
        Set<String> sootCandidateKeys = new LinkedHashSet<>();
        for (SootUpTypeHierarchyIndex.MethodCandidate candidate : sootResolution.candidates()) {
            sootCandidateKeys.add(candidateKey(candidate));
        }

        SpringDiIndex.InjectionResolution springResolution = springResolutionFor(callNode, ctx);
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
                    reportSootUnavailable(implementation, beanCandidate.bean().implementationType(), callNode, ctx);
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
            MethodSymbol candidateSymbol = buildCandidateMethodSymbol(info.method);
            if (declarationMethodId.equals(candidateSymbol.methodId())) {
                continue;
            }
            accumulator.addNode(candidateSymbol);
            Map<String, Object> metadata = candidateEdgeMetadata(info);
            for (String callerId : ctx.callerMethodIds()) {
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
     *
     * @param callNode 候補 edge を検討しているメソッド呼び出しまたはメソッド参照
     * @return 明示的な {@code super} 呼び出し・参照なら {@code true}
     */
    private static boolean isExplicitSuperDispatch(Node callNode) {
        if (callNode instanceof MethodCallExpr methodCall) {
            return methodCall.getScope().filter(SuperExpr.class::isInstance).isPresent();
        }
        return callNode instanceof MethodReferenceExpr methodReference
                && methodReference.getScope() instanceof SuperExpr;
    }

    private SpringDiIndex.InjectionResolution springResolutionFor(Node callNode, WalkContext ctx) {
        if (ctx.enclosingTypeNode() == null) {
            return null;
        }
        String receiverName = receiverNameOf(callNode);
        if (receiverName == null) {
            return null;
        }
        String ownerType = BinaryNames.forTypeLikeNode(ctx.enclosingTypeNode());
        List<SpringDiIndex.InjectionResolution> resolutions =
                springResolutionsByReceiver.get(springReceiverKey(ownerType, receiverName));
        return selectSpringResolution(callNode, receiverName, resolutions);
    }

    private static String receiverNameOf(Node callNode) {
        Expression scope = callScopeOf(callNode);
        if (scope == null) {
            return null;
        }
        while (scope.isEnclosedExpr()) {
            scope = scope.asEnclosedExpr().getInner();
        }
        if (scope instanceof NameExpr name) {
            try {
                var declaration = name.resolve();
                return declaration.isField() || declaration.isParameter()
                        ? name.getNameAsString()
                        : null;
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }
        if (scope instanceof FieldAccessExpr field && field.getScope() instanceof ThisExpr) {
            return field.getNameAsString();
        }
        return null;
    }

    /**
     * 同じownerとreceiver名を共有する注入点から、call siteが参照する宣言に対応する1件を選ぶ。
     * parameterはsource上の宣言行で区別し、fieldは直接field injectionをconstructor／setter経由の
     * aliasより優先する。1件に決められない場合は誤ったDI候補を使わず、型階層解決へ委ねる。
     */
    private static SpringDiIndex.InjectionResolution selectSpringResolution(
            Node callNode,
            String receiverName,
            List<SpringDiIndex.InjectionResolution> resolutions) {
        if (resolutions == null || resolutions.isEmpty()) {
            return null;
        }
        Expression scope = callScopeOf(callNode);
        if (scope == null) {
            return null;
        }
        while (scope.isEnclosedExpr()) {
            scope = scope.asEnclosedExpr().getInner();
        }
        ResolvedValueDeclaration declaration;
        try {
            if (scope instanceof NameExpr name) {
                declaration = name.resolve();
            } else if (scope instanceof FieldAccessExpr field && field.getScope() instanceof ThisExpr) {
                declaration = field.resolve();
            } else {
                return null;
            }
        } catch (RuntimeException | LinkageError e) {
            return null;
        }

        if (declaration.isParameter()) {
            Node ast = declaration.toAst().orElse(null);
            int declarationLine = ast != null && ast.getBegin().isPresent() ? ast.getBegin().get().line : -1;
            return uniqueResolution(resolutions.stream()
                    .filter(resolution -> resolution.injectionPoint().sourceLine() == declarationLine)
                    .toList());
        }
        if (!declaration.isField()) {
            return null;
        }
        List<SpringDiIndex.InjectionResolution> directFields = resolutions.stream()
                .filter(resolution -> resolution.injectionPoint().kind() == SpringDiIndex.InjectionKind.FIELD)
                .filter(resolution -> receiverName.equals(resolution.injectionPoint().targetName()))
                .toList();
        if (!directFields.isEmpty()) {
            return uniqueResolution(directFields);
        }
        return uniqueResolution(resolutions);
    }

    private static SpringDiIndex.InjectionResolution uniqueResolution(
            List<SpringDiIndex.InjectionResolution> resolutions) {
        return resolutions.size() == 1 ? resolutions.get(0) : null;
    }

    private static Expression callScopeOf(Node callNode) {
        if (callNode instanceof MethodCallExpr methodCall && methodCall.getScope().isPresent()) {
            return methodCall.getScope().get();
        }
        if (callNode instanceof MethodReferenceExpr methodReference) {
            return methodReference.getScope();
        }
        return null;
    }

    /**
     * call siteのreceiverが同時に満たす静的型を返す。
     *
     * <p>通常のreference typeはfallbackの1型だけを返す。型変数またはintersection typeでは
     * 全extends境界を返し、SootUp側で候補の積集合を取れるようにする。型解決に失敗した場合も
     * erasure済みfallbackを保持する。
     *
     * @param callNode method callまたはmethod reference
     * @param fallback erasureから得たreceiver型
     * @return receiverが同時に満たす型の重複なし配列
     */
    private static List<String> receiverTypeConstraints(Node callNode, String fallback) {
        Expression scope = callScopeOf(callNode);
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
     * 型変数・intersection・上限wildcardを再帰展開し、最終的なreference型境界を収集する。
     * 間接境界 ({@code T extends U}, {@code U extends A & B}) でもAとBの両方を保持する。
     *
     * @param type 展開するreceiver型または境界型
     * @param constraints 収集先のbinary name集合
     * @param visiting 展開中の型変数名。循環参照を停止する
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

    private MethodSymbol buildCandidateMethodSymbol(SootUpTypeHierarchyIndex.MethodCandidate candidate) {
        return sourceMethodIndex.find(candidate).orElseGet(() -> {
            String signature = MethodIds.signature(
                    candidate.declaringType(),
                    candidate.methodName(),
                    candidate.parameterTypes());
            return MethodSymbol.of(
                    MethodIds.methodId(signature),
                    "java",
                    "method",
                    candidate.declaringType().replace('$', '.') + "." + candidate.methodName(),
                    signature,
                    null,
                    null);
        });
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

    private void reportSootUnavailable(
            SootUpTypeHierarchyIndex.Resolution resolution,
            String targetType,
            Node callNode,
            WalkContext ctx) {
        String relatedMethodId = ctx.callerMethodIds().isEmpty() ? null : ctx.callerMethodIds().get(0);
        accumulator.addDiagnostic(Diagnostic.of(
                JavaDiagnosticCode.JAVA_SOOTUP_UNAVAILABLE.severity(),
                JavaDiagnosticCode.JAVA_SOOTUP_UNAVAILABLE.code(),
                resolution.unavailableReason(),
                sourceLocationOf(callNode),
                relatedMethodId,
                Map.of("targetType", targetType)));
    }

    private static String candidateKey(SootUpTypeHierarchyIndex.MethodCandidate candidate) {
        return MethodIds.signature(candidate.declaringType(), candidate.methodName(), candidate.parameterTypes());
    }

    private static String springReceiverKey(String ownerType, String targetName) {
        return ownerType + "\u0000" + targetName;
    }

    private Map<String, Object> edgeMetadata(String dispatch, boolean viaLambda) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (dispatch != null) {
            metadata.put("dispatch", dispatch);
        }
        if (viaLambda) {
            metadata.put("viaLambda", true);
        }
        return metadata.isEmpty() ? null : metadata;
    }

    private List<String> paramBinaryNames(ResolvedMethodLikeDeclaration resolved) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < resolved.getNumberOfParams(); i++) {
            names.add(BinaryNames.erasureOf(resolved.getParam(i).getType()));
        }
        return names;
    }

    private Path filePathOf(Node node) {
        return node.findCompilationUnit()
                .flatMap(CompilationUnit::getStorage)
                .map(storage -> storage.getPath().toAbsolutePath().normalize())
                .orElse(null);
    }

    /**
     * node の source 位置。schema は {@code startLine} を 1-based で要求するため、位置が不明な場合
     * ({@code node.getBegin()} が空) は {@code startLine: 0} を出さず sourceLocation 自体を省略する
     * (null を返す)。
     */
    private SourceLocation sourceLocationOf(Node node) {
        return node.getBegin().map(p -> {
            Path filePath = filePathOf(node);
            String relativePath = filePath != null
                    ? RelativePaths.toRecordPath(workspaceRoot.relativize(filePath).toString())
                    : "";
            return SourceLocation.of(relativePath, p.line);
        }).orElse(null);
    }
}
