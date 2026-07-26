package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.JavaDiagnosticCode;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResolver;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteId;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteInventory;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteOutcomeLedger;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.ProjectBytecodeMemberIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.WorkspaceSourceDeclarationIndex;
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
    private final CallSiteOutcomeLedger ledger;
    private final WorkspaceSourceDeclarationIndex declIndex;
    private final ProjectBytecodeMemberIndex bytecodeIndex;
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
     * @param ledger call site ごとの終端 (emitted / excluded / diagnostic) を記録する
     *     完全性 gate 用 ledger (java-analyzer feature doc「Parse・resolution・call 完全性」)
     * @param declIndex workspace の source 宣言から型の所有 context と source location を引く索引。
     *     bytecode 救済の対象を workspace source を持つ型に限定する判定に使う
     * @param bytecodeIndex 呼び出し元 context の classpath 視点で bytecode member
     *     (method / constructor / field 型 / generic 戻り型) を引く索引。source だけでは解決できない
     *     候補の救済に使う (feature doc「solver 層の bytecode member 合成」)
     * @param reachableContextIds 自 context と Gradle project 依存で推移的に到達可能な context id の集合。
     *     救済候補の所有 context がこの集合に含まれない場合は救済を行わない
     */
    public CallGraphBuilder(
            Path workspaceRoot,
            AttributionResolver attributionResolver,
            GraphAccumulator accumulator,
            SootUpTypeHierarchyIndex sootUpIndex,
            SourceMethodIndex sourceMethodIndex,
            SpringDiIndex.Result springResult,
            CallSiteOutcomeLedger ledger,
            WorkspaceSourceDeclarationIndex declIndex,
            ProjectBytecodeMemberIndex bytecodeIndex,
            java.util.Set<String> reachableContextIds) {
        this.workspaceRoot = workspaceRoot;
        this.attributionResolver = attributionResolver;
        this.accumulator = accumulator;
        this.sootUpIndex = sootUpIndex;
        this.sourceMethodIndex = sourceMethodIndex;
        this.ledger = ledger;
        this.declIndex = declIndex;
        this.bytecodeIndex = bytecodeIndex;
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
            // qualified super (`expr.super(...)`) の outer 式内の call も辿る
            // (inventory の走査と対)。
            ecis.getExpression().ifPresent(expression -> walk(expression, ctx));
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
        } catch (RuntimeException e) {
            rethrowUnlessIsolableResolutionFailure(e);
            if (tryBytecodeMethodRescue(mce, ctx)) {
                commitEmitted(mce, CallSiteId.CallKind.METHOD_CALL, ctx);
                return;
            }
            // receiver 型を (bytecode field 補完込みで) 特定できて、その型が
            // scope 内 source に存在しない場合、callee は scope 外であり
            // 理由付き external-target として分類する (ADR-0005)。
            // 例: Lombok @Slf4j の log field 経由の Logger#info 呼び出し。
            String receiverOwner = bytecodeRescueOwner(mce, ctx);
            if (receiverOwner != null && declIndex.find(receiverOwner).isEmpty()) {
                commitExcludedExternal(mce, CallSiteId.CallKind.METHOD_CALL, ctx);
                return;
            }
            // 解決失敗した receiver chain を bytecode candidate の戻り値型
            // (classfile 由来) で前進解決し、復元した owner で救済 / external
            // 分類を試みる。根拠のない型推測は行わない。
            if (receiverOwner == null && mce.getScope().isPresent()) {
                String forwardOwner = chainForwardOwner(mce.getScope().get(), ctx, 0);
                if (forwardOwner != null) {
                    if (declIndex.find(forwardOwner).isEmpty()) {
                        commitExcludedExternal(mce, CallSiteId.CallKind.METHOD_CALL, ctx);
                        return;
                    }
                    if (tryBytecodeMethodRescueWithOwner(mce, ctx, forwardOwner)) {
                        commitEmitted(mce, CallSiteId.CallKind.METHOD_CALL, ctx);
                        return;
                    }
                }
            }
            // receiver 型が取れない call でも、
            // (i) chain 起点の静的型が scope 外、または (ii) lambda parameter の
            // 引数先 functional interface が scope 外なら external-target へ分類
            // する。scope 内型が根拠に現れる場合は保守的に diagnostic に残す。
            if (receiverOwner == null
                    && (chainRootIsExternal(mce, ctx) || lambdaParamReceiverIsExternal(mce, ctx))) {
                commitExcludedExternal(mce, CallSiteId.CallKind.METHOD_CALL, ctx);
                return;
            }
            Map<String, Object> metadata = diagnosticMetadata(
                    PHASE_BYTECODE_RESCUE, e, mce.getScope().orElse(null), "implicit-this");
            reportUnresolved(mce, ctx, metadata);
            commitDiagnostic(mce, CallSiteId.CallKind.METHOD_CALL, ctx,
                    "unresolved-method-call", mce.getNameAsString(), metadata);
            return;
        }

        // solver が合成した bytecode-only member (java-analyzer feature doc
        // 「solver 層の bytecode member 合成」) は、既存の bytecode-only member と同じ
        // 出力契約 (sourceLocation 省略 + owner metadata + calleeOrigin edge、ADR-0005)
        // で emit する。
        if (resolved instanceof com.fukuemon.depwalk.javaanalyzer.analysis.augment.SynthesizedBytecodeMethodDeclaration synthesized) {
            // 型名 scope の static call を instance 合成 member で解決しない
            // (usage 経路は staticOnly を持たないため、emit 前にここで検査する)。
            if (!synthesized.isStatic() && mce.getScope().isPresent() && isTypeNameScope(mce.getScope().get())) {
                Map<String, Object> guardMetadata =
                        diagnosticMetadata(PHASE_SYNTHESIS_STATIC_GUARD, null, mce.getScope().get(), null);
                reportUnresolved(mce, ctx, guardMetadata);
                commitDiagnostic(mce, CallSiteId.CallKind.METHOD_CALL, ctx,
                        "unresolved-method-call", mce.getNameAsString(), guardMetadata);
                return;
            }
            WorkspaceSourceDeclarationIndex.TypeLocation owner =
                    declIndex.find(synthesized.candidate().declaringType()).orElse(null);
            if (owner == null || !reachableContextIds.contains(owner.contextId())) {
                throw new IllegalStateException(
                        "synthesized bytecode member without a reachable in-scope owner: "
                                + synthesized.candidate().declaringType() + "#" + synthesized.getName());
            }
            emitBytecodeOnlyCall(mce, ctx, owner,
                    synthesized.candidate().declaringType(), synthesized.getName(),
                    synthesized.candidate().parameterTypes(), "method");
            commitEmitted(mce, CallSiteId.CallKind.METHOD_CALL, ctx);
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
        } catch (RuntimeException e) {
            rethrowUnlessIsolableResolutionFailure(e);
            if (tryBytecodeConstructorRescue(oce, ctx)) {
                commitEmitted(oce, CallSiteId.CallKind.OBJECT_CREATION, ctx);
                return;
            }
            Map<String, Object> metadata =
                    diagnosticMetadata(PHASE_BYTECODE_RESCUE, e, oce.getScope().orElse(null), "none");
            reportUnresolved(oce, ctx, metadata);
            commitDiagnostic(oce, CallSiteId.CallKind.OBJECT_CREATION, ctx,
                    "unresolved-constructor-call", oce.getTypeAsString(), metadata);
            return;
        }
        emitConstructorCall(resolved, oce, ctx, CallSiteId.CallKind.OBJECT_CREATION);
    }

    private void processExplicitConstructorInvocation(ExplicitConstructorInvocationStmt ecis, WalkContext ctx) {
        ResolvedConstructorDeclaration resolved;
        try {
            resolved = ecis.resolve();
        } catch (RuntimeException e) {
            rethrowUnlessIsolableResolutionFailure(e);
            // 明示 super(...) / this(...) の解決先 (親 / 自クラスの
            // 生成 constructor) を bytecode 救済してから diagnostic 化する。
            if (tryBytecodeExplicitCtorRescue(ecis, ctx)) {
                commitEmitted(ecis, CallSiteId.CallKind.EXPLICIT_CONSTRUCTOR_INVOCATION, ctx);
                return;
            }
            String ctorOwner = explicitCtorOwner(ecis, ctx);
            if (ctorOwner != null && declIndex.find(ctorOwner).isEmpty()) {
                commitExcludedExternal(ecis, CallSiteId.CallKind.EXPLICIT_CONSTRUCTOR_INVOCATION, ctx);
                return;
            }
            Map<String, Object> metadata =
                    diagnosticMetadata(PHASE_BYTECODE_RESCUE, e, null, ecis.isThis() ? "this" : "super");
            reportUnresolved(ecis, ctx, metadata);
            commitDiagnostic(ecis, CallSiteId.CallKind.EXPLICIT_CONSTRUCTOR_INVOCATION, ctx,
                    "unresolved-constructor-call", ecis.isThis() ? "this" : "super", metadata);
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
        } catch (RuntimeException e) {
            rethrowUnlessIsolableResolutionFailure(e);
            // method call と同等に bytecode 救済 → external-target
            // 分類を試みてから diagnostic 化する。
            if (tryBytecodeMethodReferenceRescue(mre, ctx)) {
                commitEmitted(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx);
                return;
            }
            String referenceOwner = methodReferenceOwner(mre);
            if (referenceOwner != null && declIndex.find(referenceOwner).isEmpty()) {
                commitExcludedExternal(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx);
                return;
            }
            Map<String, Object> metadata = diagnosticMetadata(PHASE_BYTECODE_RESCUE, e, mre.getScope(), null);
            reportUnresolved(mre, ctx, metadata);
            commitDiagnostic(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                    "unresolved-method-reference", mre.getIdentifier(), metadata);
            return;
        }

        // solver が合成した bytecode-only member (java-analyzer feature doc「solver 層の
        // bytecode member 合成」) への reference は、method call の synthesized 経路と
        // 同じ出力契約 (sourceLocation 省略 + owner metadata + calleeOrigin edge、
        // ADR-0005) で emit する (従来この経路は通常 symbol として emit され、
        // この出力契約から漏れていた)。
        if (resolved instanceof com.fukuemon.depwalk.javaanalyzer.analysis.augment.SynthesizedBytecodeMethodDeclaration synthesized) {
            WorkspaceSourceDeclarationIndex.TypeLocation owner =
                    declIndex.find(synthesized.candidate().declaringType()).orElse(null);
            if (owner == null || !reachableContextIds.contains(owner.contextId())) {
                throw new IllegalStateException(
                        "synthesized bytecode member without a reachable in-scope owner: "
                                + synthesized.candidate().declaringType() + "#" + synthesized.getName());
            }
            emitBytecodeOnlyCall(mre, ctx, owner,
                    synthesized.candidate().declaringType(), synthesized.getName(),
                    synthesized.candidate().parameterTypes(), "method", true);
            commitEmitted(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx);
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
                Map<String, Object> metadata = diagnosticMetadata(PHASE_SOLVER_RESOLVE, null, mre.getScope(), null);
                reportUnresolved(mre, ctx, metadata);
                commitDiagnostic(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                        "unresolved-constructor-reference", mre.getScope().toString(), metadata);
                return;
            }
            scopeDecl = scopeType.asReferenceType().getTypeDeclaration().orElse(null);
            if (scopeDecl == null) {
                Map<String, Object> metadata = diagnosticMetadata(PHASE_SOLVER_RESOLVE, null, mre.getScope(), null);
                reportUnresolved(mre, ctx, metadata);
                commitDiagnostic(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                        "unresolved-constructor-reference", mre.getScope().toString(), metadata);
                return;
            }
        } catch (RuntimeException | LinkageError e) {
            Map<String, Object> metadata = diagnosticMetadata(PHASE_SOLVER_RESOLVE, e, mre.getScope(), null);
            reportUnresolved(mre, ctx, metadata);
            commitDiagnostic(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                    "unresolved-constructor-reference", mre.getScope().toString(), metadata);
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
            // source 側の候補選択で決まらない場合、SAM arity の一意
            // bytecode constructor (生成 constructor 含む) を救済してから
            // diagnostic 化する。
            if (tryBytecodeConstructorReferenceRescue(mre, ctx, scopeDecl)) {
                commitEmitted(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx);
                return;
            }
            Map<String, Object> metadata =
                    diagnosticMetadata(PHASE_CONSTRUCTOR_REFERENCE_SELECTION, null, mre.getScope(), null);
            reportUnresolved(mre, ctx, metadata);
            commitDiagnostic(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                    "ambiguous-constructor-reference", mre.getScope().toString(), metadata);
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
    // call-site outcome ledger (java-analyzer feature doc「Parse・resolution・call 完全性」)
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
            if (CallSiteInventory.CallerIdentities.isPlaceholder(caller)) {
                // caller 宣言が resolve できない site は edge を出力できないため、
                // emitted でなく primary diagnostic として完全性 gate に残す
                // (java-analyzer feature doc「Parse・resolution・call 完全性」)。
                // 診断 metadata (java-analyzer feature doc「diagnostic / error code 体系」)
                // は「call 解決の失敗段階」を表すため、
                // caller 宣言側の失敗であるこの経路には意図的に付けない
                // (details の 4 項目は解決失敗系 reason にのみ載る)。
                ledger.commitDiagnostic(
                        CallSiteInventory.of(callNode, currentPath, kind, caller),
                        JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.code(),
                        "unresolved-caller",
                        null,
                        null);
                continue;
            }
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

    /** attribution を経ない external-target の明示除外 commit (ADR-0005 の field 補完経路)。 */
    private void commitExcludedExternal(Node callNode, CallSiteId.CallKind kind, WalkContext ctx) {
        for (String caller : ledgerCallers(callNode, ctx)) {
            ledger.commitExcluded(
                    CallSiteInventory.of(callNode, currentPath, kind, caller),
                    CallSiteOutcomeLedger.REASON_EXTERNAL_TARGET);
        }
    }

    private void commitDiagnostic(
            Node callNode, CallSiteId.CallKind kind, WalkContext ctx, String reason, String target,
            Map<String, Object> diagnosticMetadata) {
        for (String caller : ledgerCallers(callNode, ctx)) {
            ledger.commitDiagnostic(
                    CallSiteInventory.of(callNode, currentPath, kind, caller),
                    JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.code(),
                    reason,
                    target,
                    null,
                    diagnosticMetadata);
        }
    }

    // ------------------------------------------------------------------
    // 解決失敗の診断 metadata (java-analyzer feature doc「diagnostic / error code 体系」)
    // ------------------------------------------------------------------

    /** 診断 metadata の resolutionPhase 安定値。 */
    static final String PHASE_SOLVER_RESOLVE = "solver-resolve";
    static final String PHASE_BYTECODE_RESCUE = "bytecode-rescue";
    static final String PHASE_SYNTHESIS_STATIC_GUARD = "member-synthesis-static-guard";
    static final String PHASE_CONSTRUCTOR_REFERENCE_SELECTION = "constructor-reference-selection";

    /**
     * primary diagnostic へ添える sanitize 済み診断 4 項目
     * (java-analyzer feature doc「diagnostic / error code 体系」) を構築する。
     * 含めるのは安定値だけ: 失敗した解決段階、resolver 例外のクラス名 (message は
     * 含めない)、receiver 式種別 (AST 型名)、receiver 静的型の取得成否。
     *
     * @param phase 失敗した解決段階 ({@code PHASE_*})
     * @param failure resolve 例外。例外を伴わない失敗 (候補選択の曖昧さ等) は null
     * @param scope receiver 式。暗黙 this / receiver を持たない call は null
     * @param implicitReceiverKind scope が null のときの receiver 種別表記。この場合の
     *     receiverTypeResolved は「receiver 式の型取得に失敗していない」ことを表す
     *     固定 true (取得対象の receiver 式が存在しないため、失敗ではない)
     */
    private Map<String, Object> diagnosticMetadata(
            String phase, Throwable failure, Expression scope, String implicitReceiverKind) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("resolutionPhase", phase);
        if (failure != null) {
            metadata.put("exceptionClass", failure.getClass().getName());
        }
        metadata.put("receiverKind", scope != null ? scope.getClass().getSimpleName() : implicitReceiverKind);
        metadata.put("receiverTypeResolved", scope != null ? receiverTypeResolves(scope) : true);
        return metadata;
    }

    /** receiver 式の静的型を計算できるか (診断 metadata の receiverTypeResolved)。 */
    private static boolean receiverTypeResolves(Expression scope) {
        try {
            return scope.calculateResolvedType().isReferenceType();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * 解決失敗した method call を、scope 内 source type の到達可能な project
     * bytecode の一意 member へ generator 非依存で救済する (ADR-0005)。
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
        // 型名 scope の static call を instance member で救済しない (偽 edge 防止)。
        if (!candidate.isStatic() && mce.getScope().isPresent() && isTypeNameScope(mce.getScope().get())) {
            return false;
        }
        emitBytecodeOnlyCall(mce, ctx, owner,
                candidate.declaringType(), candidate.methodName(), candidate.parameterTypes(), "method");
        return true;
    }

    /**
     * scope が値でなく型名 (static call の receiver) かを判定する。型として
     * 解決できる scope のうち、値 (field / 変数) として解決できない単純名 /
     * qualified name だけを型名とみなす。型が取れない scope は bytecode-only
     * field 補完経路の instance receiver であり型名扱いしない。
     */
    private static boolean isTypeNameScope(com.github.javaparser.ast.expr.Expression scope) {
        try {
            scope.calculateResolvedType();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
        try {
            if (scope instanceof com.github.javaparser.ast.expr.NameExpr nameExpr) {
                nameExpr.resolve();
                return false;
            }
            if (scope instanceof com.github.javaparser.ast.expr.FieldAccessExpr fieldAccess) {
                fieldAccess.resolve();
                return false;
            }
        } catch (RuntimeException | LinkageError e) {
            return true;
        }
        return false;
    }

    /**
     * 解決失敗した method reference の bytecode-only member 救済。
     * 参照先型が scope 内で到達可能な場合に、JLS 15.13.1 に沿った候補選択
     * ({@link #selectMethodReferenceCandidate}) で救済する。
     */
    private boolean tryBytecodeMethodReferenceRescue(MethodReferenceExpr mre, WalkContext ctx) {
        String ownerBinaryName = methodReferenceOwner(mre);
        if (ownerBinaryName == null) {
            return false;
        }
        WorkspaceSourceDeclarationIndex.TypeLocation owner = declIndex.find(ownerBinaryName).orElse(null);
        if (owner == null || !reachableContextIds.contains(owner.contextId())) {
            return false;
        }
        boolean typeNameScope = mre.getScope() instanceof com.github.javaparser.ast.expr.TypeExpr;
        int samArity = inferFunctionalInterfaceArity(mre);
        var candidate = selectMethodReferenceCandidate(ownerBinaryName, mre.getIdentifier(), typeNameScope, samArity);
        if (candidate == null) {
            return false;
        }
        emitBytecodeOnlyCall(mre, ctx, owner,
                candidate.declaringType(), candidate.methodName(), candidate.parameterTypes(), "method", true);
        return true;
    }

    /**
     * method reference の候補選択 (multi-agent review 指摘反映:
     * 2026-07-22)。JLS 15.13.1 の 2 つの解釈だけを候補にする:
     * <ul>
     * <li>{@code Type::m} ({@code typeNameScope=true}): static なら arity=samArity、
     *     instance (unbound reference) なら arity=samArity-1 のみが有効。両方に
     *     候補があれば曖昧として不採用。</li>
     * <li>{@code expr::m} ({@code typeNameScope=false}, bound reference): instance
     *     の arity=samArity のみが有効 (static、または samArity-1 は無効)。</li>
     * </ul>
     * SAM arity を推論できない場合は、上記の (arity, static/instance) 検証ができ
     * ないため、より保守的に「参照形式に矛盾しない (typeNameScope なら static も
     * instance も許容、bound なら instance のみ) 名前一意の member」だけを採用する。
     */
    private com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex.MethodCandidate
            selectMethodReferenceCandidate(
                    String ownerBinaryName, String methodName, boolean typeNameScope, int samArity) {
        if (samArity < 0) {
            var byName = bytecodeIndex.declaredCallableMethods(ownerBinaryName).stream()
                    .filter(method -> method.methodName().equals(methodName))
                    .filter(method -> typeNameScope || !method.isStatic())
                    .toList();
            return byName.size() == 1 ? byName.get(0) : null;
        }
        if (typeNameScope) {
            var staticCandidate = uniqueMethodByArityAndStatic(ownerBinaryName, methodName, samArity, true);
            var instanceCandidate = samArity >= 1
                    ? uniqueMethodByArityAndStatic(ownerBinaryName, methodName, samArity - 1, false)
                    : null;
            if (staticCandidate != null && instanceCandidate != null) {
                return null;
            }
            return staticCandidate != null ? staticCandidate : instanceCandidate;
        }
        return uniqueMethodByArityAndStatic(ownerBinaryName, methodName, samArity, false);
    }

    /** 名前・arity・static 性が一致する owner classfile 上の一意 member。 */
    private com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex.MethodCandidate
            uniqueMethodByArityAndStatic(String ownerBinaryName, String methodName, int arity, boolean wantStatic) {
        var matches = bytecodeIndex.declaredCallableMethods(ownerBinaryName).stream()
                .filter(method -> method.methodName().equals(methodName))
                .filter(method -> method.parameterTypes().size() == arity)
                .filter(method -> method.isStatic() == wantStatic)
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    /**
     * constructor reference (`Foo::new`) の候補選択が決まらない場合の
     * bytecode-only constructor 救済。SAM arity の一意 constructor
     * だけを採用する。
     */
    private boolean tryBytecodeConstructorReferenceRescue(
            MethodReferenceExpr mre, WalkContext ctx, ResolvedReferenceTypeDeclaration scopeDecl) {
        String ownerBinaryName = BinaryNames.forResolvedDeclaration(scopeDecl);
        WorkspaceSourceDeclarationIndex.TypeLocation owner = declIndex.find(ownerBinaryName).orElse(null);
        if (owner == null || !reachableContextIds.contains(owner.contextId())) {
            return false;
        }
        int samArity = inferFunctionalInterfaceArity(mre);
        if (samArity < 0) {
            return false;
        }
        var candidate = bytecodeIndex.uniqueConstructor(ownerBinaryName, samArity).orElse(null);
        if (candidate == null) {
            return false;
        }
        emitBytecodeOnlyCall(mre, ctx, owner,
                candidate.declaringType(), MethodIds.CONSTRUCTOR_TOKEN, candidate.parameterTypes(), "constructor", true);
        return true;
    }

    /** method reference の参照先 owner (scope 式の静的型 erasure)。 */
    private String methodReferenceOwner(MethodReferenceExpr mre) {
        try {
            ResolvedType scopeType = mre.getScope().calculateResolvedType().erasure();
            if (!scopeType.isReferenceType()) {
                return null;
            }
            return BinaryNames.erasureOf(scopeType);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /**
     * 解決失敗した明示 constructor invocation の bytecode-only constructor 救済。
     * this(...) は囲み型、super(...) は extends 節を resolve した
     * 親型を owner とする。
     */
    private boolean tryBytecodeExplicitCtorRescue(ExplicitConstructorInvocationStmt ecis, WalkContext ctx) {
        String ownerBinaryName = explicitCtorOwner(ecis, ctx);
        if (ownerBinaryName == null) {
            return false;
        }
        WorkspaceSourceDeclarationIndex.TypeLocation owner = declIndex.find(ownerBinaryName).orElse(null);
        if (owner == null || !reachableContextIds.contains(owner.contextId())) {
            return false;
        }
        var candidate = bytecodeIndex.uniqueConstructor(ownerBinaryName, ecis.getArguments().size()).orElse(null);
        if (candidate == null) {
            return false;
        }
        emitBytecodeOnlyCall(ecis, ctx, owner,
                candidate.declaringType(), MethodIds.CONSTRUCTOR_TOKEN, candidate.parameterTypes(), "constructor", false);
        return true;
    }

    /** 明示 constructor invocation の解決先 owner 型 (this は囲み型、super は親型)。 */
    private String explicitCtorOwner(ExplicitConstructorInvocationStmt ecis, WalkContext ctx) {
        if (ctx.enclosingTypeNode() == null) {
            return null;
        }
        if (ecis.isThis()) {
            try {
                return BinaryNames.forTypeLikeNode(ctx.enclosingTypeNode());
            } catch (RuntimeException | LinkageError e) {
                return null;
            }
        }
        if (ctx.enclosingTypeNode() instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration cid
                && !cid.getExtendedTypes().isEmpty()) {
            try {
                return BinaryNames.erasureOf(cid.getExtendedTypes().get(0).resolve());
            } catch (RuntimeException | LinkageError e) {
                return null;
            }
        }
        return null;
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

    /**
     * chain の前進解決。receiver 式の静的型が取れない場合、
     * chain を再帰的に遡り、各 link を bytecode candidate の戻り値型
     * (classfile の descriptor / generic Signature 由来) で前進解決して現在の
     * call の owner 型を復元する。候補が一意でない・classfile に根拠が無い
     * link があれば null (推測しない)。
     */
    private String chainForwardOwner(Expression expr, WalkContext ctx, int depth) {
        if (expr == null || depth > 16) {
            return null;
        }
        String direct = tryTypeErasureOf(expr);
        if (direct != null) {
            return direct;
        }
        if (expr instanceof com.github.javaparser.ast.expr.EnclosedExpr enclosed) {
            return chainForwardOwner(enclosed.getInner(), ctx, depth + 1);
        }
        if (expr instanceof NameExpr nameExpr) {
            // 型が取れない local 変数は、宣言の initializer 式を同じ規則で前進
            // 解決する (var / 失敗 chain 由来の変数への波及を classfile 根拠で辿る)。
            try {
                ResolvedValueDeclaration value = nameExpr.resolve();
                Node ast = value.toAst().orElse(null);
                if (ast instanceof com.github.javaparser.ast.body.VariableDeclarator declarator) {
                    Expression initializer = declarator.getInitializer().orElse(null);
                    if (initializer != null) {
                        return chainForwardOwner(initializer, ctx, depth + 1);
                    }
                }
                return null;
            } catch (RuntimeException | LinkageError e) {
                // `var` の型推論が壊れていると resolve() 自体が失敗する。囲み
                // callable 内で同名宣言が一意なら、その initializer を確定 AST
                // として前進解決する (一意でなければ shadowing の誤追跡を避けて
                // 不採用)。local に該当が無ければ囲み型の bytecode field 型で
                // 補完する (既存の receiver 補完経路と同じ classfile 根拠)。
                Expression initializer = uniqueLocalInitializer(nameExpr);
                if (initializer != null) {
                    return chainForwardOwner(initializer, ctx, depth + 1);
                }
                return enclosingBytecodeFieldType(nameExpr.getNameAsString(), ctx);
            }
        }
        if (expr instanceof FieldAccessExpr fieldAccess
                && fieldAccess.getScope() instanceof ThisExpr) {
            return enclosingBytecodeFieldType(fieldAccess.getNameAsString(), ctx);
        }
        if (!(expr instanceof MethodCallExpr link)) {
            return null;
        }
        Expression scope = link.getScope().orElse(null);
        String receiverOwner;
        if (scope == null) {
            // 暗黙 this の link は囲み型の classfile candidate で前進する
            // (継承 member は declared methods に現れないため、その場合は null)。
            receiverOwner = ctx.enclosingTypeNode() != null
                    ? tryBinaryNameOfEnclosing(ctx.enclosingTypeNode())
                    : null;
        } else {
            receiverOwner = chainForwardOwner(scope, ctx, depth + 1);
        }
        if (receiverOwner == null) {
            return null;
        }
        var candidate = bytecodeIndex
                .uniqueMethod(receiverOwner, link.getNameAsString(), link.getArguments().size())
                .orElse(null);
        if (candidate == null) {
            return null;
        }
        var generic = bytecodeIndex.genericReturnType(candidate).orElse(null);
        String returnType = generic != null && !generic.typeVariable() && generic.arrayDims() == 0
                ? generic.binaryName()
                : candidate.returnType();
        if (returnType == null || returnType.endsWith("[]") || isPrimitiveOrVoid(returnType)) {
            return null;
        }
        return returnType;
    }

    /**
     * 囲み callable (method / constructor / initializer / lambda body を含む
     * 最内の宣言) の中で同名の local 宣言が一意なら、その initializer を返す。
     */
    private static Expression uniqueLocalInitializer(NameExpr nameExpr) {
        String name = nameExpr.getNameAsString();
        Node child = nameExpr;
        Node parent = child.getParentNode().orElse(null);
        while (parent != null) {
            if (parent instanceof com.github.javaparser.ast.stmt.BlockStmt block) {
                Expression initializer = declaratorBeforeInBlock(block, child, name);
                if (initializer != null) {
                    return initializer;
                }
            }
            if (parent instanceof MethodDeclaration
                    || parent instanceof ConstructorDeclaration
                    || parent instanceof InitializerDeclaration
                    || parent instanceof LambdaExpr) {
                // callable / lambda 境界。ここまでで見つからなければ不採用
                // (字句スコープ外の同名宣言を誤って結びつけない、保守側)。
                return null;
            }
            child = parent;
            parent = parent.getParentNode().orElse(null);
        }
        return null;
    }

    /**
     * {@code block} 直下の文のうち、{@code child} (を祖先に持つ文) より前にある
     * 同名 {@code VariableDeclarator} の initializer (multi-agent review 指摘
     * 反映: 2026-07-22。前方参照は無効、かつ use を含まない兄弟文の宣言は
     * 対象にしない)。
     */
    private static Expression declaratorBeforeInBlock(
            com.github.javaparser.ast.stmt.BlockStmt block, Node child, String name) {
        for (com.github.javaparser.ast.stmt.Statement statement : block.getStatements()) {
            if (statement == child || statement.isAncestorOf(child)) {
                break;
            }
            if (statement instanceof com.github.javaparser.ast.stmt.ExpressionStmt exprStmt
                    && exprStmt.getExpression()
                            instanceof com.github.javaparser.ast.expr.VariableDeclarationExpr varDecl) {
                for (com.github.javaparser.ast.body.VariableDeclarator declarator : varDecl.getVariables()) {
                    if (declarator.getNameAsString().equals(name)) {
                        return declarator.getInitializer().orElse(null);
                    }
                }
            }
        }
        return null;
    }

    /** 囲み型の bytecode field 型 (classfile 根拠の receiver 補完)。 */
    private String enclosingBytecodeFieldType(String fieldName, WalkContext ctx) {
        if (ctx.enclosingTypeNode() == null) {
            return null;
        }
        String ownerType = tryBinaryNameOfEnclosing(ctx.enclosingTypeNode());
        if (ownerType == null) {
            return null;
        }
        WorkspaceSourceDeclarationIndex.TypeLocation owner = declIndex.find(ownerType).orElse(null);
        if (owner == null || !reachableContextIds.contains(owner.contextId())) {
            return null;
        }
        return bytecodeIndex.fieldType(ownerType, fieldName).orElse(null);
    }

    private static String tryBinaryNameOfEnclosing(Node enclosingTypeNode) {
        try {
            return BinaryNames.forTypeLikeNode(enclosingTypeNode);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static boolean isPrimitiveOrVoid(String binaryName) {
        return switch (binaryName) {
            case "void", "boolean", "byte", "short", "char", "int", "long", "float", "double" -> true;
            default -> false;
        };
    }

    /** 復元済み owner を使う method call の bytecode 救済 (chain 前進解決用)。 */
    private boolean tryBytecodeMethodRescueWithOwner(MethodCallExpr mce, WalkContext ctx, String ownerBinaryName) {
        WorkspaceSourceDeclarationIndex.TypeLocation owner = declIndex.find(ownerBinaryName).orElse(null);
        if (owner == null || !reachableContextIds.contains(owner.contextId())) {
            return false;
        }
        var candidate = bytecodeIndex.uniqueMethod(ownerBinaryName, mce.getNameAsString(), mce.getArguments().size())
                .orElse(null);
        if (candidate == null) {
            return false;
        }
        // 型名 scope の static call を instance member で救済しない境界 (PR #26)
        // を forward 経路でも対称に維持する (forward の owner は式評価由来で
        // 型名 scope になり得ないが、guard の非対称を残さない)。
        if (!candidate.isStatic() && mce.getScope().isPresent() && isTypeNameScope(mce.getScope().get())) {
            return false;
        }
        emitBytecodeOnlyCall(mce, ctx, owner,
                candidate.declaringType(), candidate.methodName(), candidate.parameterTypes(), "method");
        return true;
    }

    /**
     * chain 起点遡及 (external 分類規則 (i))。receiver が method call chain の
     * とき、chain を遡って最初に静的型が取れる式を探し、その型が scope 外
     * (source 宣言索引に無い) なら、root から現在の call までの中間 link を
     * {@link #forwardVerifyExternalChain} で classfile 根拠 (project 限定でない
     * full classpath) により前進検証できた場合だけ true。scope 内型が現れた場合、
     * 起点の型も取れない場合、または中間 link を検証できない場合は false
     * (diagnostic に残す、保守側)。
     */
    private boolean chainRootIsExternal(MethodCallExpr mce, WalkContext ctx) {
        return rootIsExternal(mce.getScope().orElse(null), ctx);
    }

    /**
     * 式の起点型を遡及し、根拠を伴って scope 外と判定できる場合だけ true
     * (規則 (i) の実体、multi-agent review 指摘反映: 2026-07-22)。
     */
    private boolean rootIsExternal(Expression start, WalkContext ctx) {
        List<MethodCallExpr> links = new ArrayList<>();
        Expression cursor = start;
        int guard = 0;
        while (cursor != null && guard++ < 64) {
            String erasure = tryTypeErasureOf(cursor);
            if (erasure != null) {
                if (declIndex.find(erasure).isPresent()) {
                    return false;
                }
                return forwardVerifyExternalChain(erasure, links);
            }
            if (cursor instanceof com.github.javaparser.ast.expr.EnclosedExpr enclosed) {
                cursor = enclosed.getInner();
                continue;
            }
            if (cursor instanceof MethodCallExpr link) {
                Expression inner = link.getScope().orElse(null);
                if (inner == null) {
                    // 暗黙 this 起点 = 囲み型 (scope 内) → 保守的に diagnostic。
                    return false;
                }
                // root 側 (先頭) が先に来るよう、遡る順とは逆に挿入する。
                links.add(0, link);
                cursor = inner;
                continue;
            }
            if (cursor instanceof NameExpr nameExpr) {
                // var 等の型が取れない変数は、確定 AST の initializer を起点として
                // 遡及を続ける (規則 (i) の「chain 起点」を代入 chain へ拡張)。
                Expression initializer = declaredInitializerOf(nameExpr);
                if (initializer != null) {
                    cursor = initializer;
                    continue;
                }
                // local に該当が無ければ囲み型の bytecode field 型で判定する。
                String fieldType = enclosingBytecodeFieldType(nameExpr.getNameAsString(), ctx);
                if (fieldType == null || declIndex.find(fieldType).isPresent()) {
                    return false;
                }
                return forwardVerifyExternalChain(fieldType, links);
            }
            if (cursor instanceof FieldAccessExpr fieldAccess
                    && fieldAccess.getScope() instanceof ThisExpr) {
                String fieldType = enclosingBytecodeFieldType(fieldAccess.getNameAsString(), ctx);
                if (fieldType == null || declIndex.find(fieldType).isPresent()) {
                    return false;
                }
                return forwardVerifyExternalChain(fieldType, links);
            }
            return false;
        }
        return false;
    }

    /**
     * root 型 (scope 外と確定済み) から、root→現在の call の順に並んだ中間
     * {@code links} を full classpath (project 限定でない) の classfile 情報で
     * 前進検証する。各 link について owner 上の名前・arity が一意な宣言 method を
     * 求め、その戻り値型を次の owner とする。いずれかの link で候補が一意に
     * 求まらない、戻り値型が判明しない、または戻り値型が in-scope と判明した
     * 場合は前進を打ち切り false (診断維持) を返す。全 link を通過できた場合の
     * み true (root が external で、かつ中間区間もすべて external と確認できた)。
     */
    /** SootUp の descriptor erasure で type variable が落ちる先 (JLS 4.6 の既定境界)。 */
    private static final String ERASED_TYPE_VARIABLE_BOUND = "java.lang.Object";

    private boolean forwardVerifyExternalChain(String rootType, List<MethodCallExpr> links) {
        String currentOwner = rootType;
        for (MethodCallExpr link : links) {
            var candidate = uniqueDeclaredMethodOnClasspath(
                    currentOwner, link.getNameAsString(), link.getArguments().size());
            if (candidate == null) {
                return false;
            }
            String returnType = candidate.returnType();
            if (returnType == null || isPrimitiveOrVoid(returnType) || returnType.endsWith("[]")) {
                return false;
            }
            // PR review 指摘反映 (2026-07-22): full classpath 経路 (uniqueDeclaredMethodOnClasspath)
            // には project 限定の bytecodeIndex.genericReturnType 相当の generic Signature
            // 読み取りが無く、境界なし type variable の戻り値は descriptor erasure で
            // Object になる。in-scope な実際の型引数を見失ったまま前進すると false
            // exclusion を再発するため、Object を「型変数の疑いあり、根拠不足」として
            // 前進を打ち切る (保守側)。
            if (ERASED_TYPE_VARIABLE_BOUND.equals(returnType)) {
                return false;
            }
            if (declIndex.find(returnType).isPresent()) {
                return false;
            }
            currentOwner = returnType;
        }
        return true;
    }

    /**
     * owner の classfile 上で名前・arity が一意な宣言 method (project 限定でない
     * full classpath、{@link #sootUpIndex} 直接参照。継承 member はここでは
     * 対象外 — 見つからなければ前進を打ち切る保守側)。
     */
    private com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex.MethodCandidate
            uniqueDeclaredMethodOnClasspath(String ownerBinaryName, String methodName, int arity) {
        var resolution = sootUpIndex.resolveDeclaredCallableMethods(ownerBinaryName, methodName);
        if (!resolution.isAvailable()) {
            return null;
        }
        var matches = resolution.candidates().stream()
                .filter(candidate -> candidate.parameterTypes().size() == arity)
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    /** NameExpr の宣言 (resolve または囲み callable 内の一意宣言) の initializer。 */
    private static Expression declaredInitializerOf(NameExpr nameExpr) {
        try {
            ResolvedValueDeclaration value = nameExpr.resolve();
            Node ast = value.toAst().orElse(null);
            if (ast instanceof com.github.javaparser.ast.body.VariableDeclarator declarator) {
                return declarator.getInitializer().orElse(null);
            }
            return null;
        } catch (RuntimeException | LinkageError e) {
            return uniqueLocalInitializer(nameExpr);
        }
    }

    /**
     * lambda parameter 起点の external 判定 (external 分類規則 (ii)、PR review
     * 指摘反映で範囲を縮小: 2026-07-22)。receiver が lambda parameter で、
     * lambda 自体が代入される変数の宣言型 (= functional interface 型そのもの)
     * が scope 外なら true。scope 内 functional interface / 判定不能は false。
     *
     * <p>lambda を直接 method の引数として渡す形 (受け手 method call の
     * receiver 型を根拠にする案) は撤回した: 受け手 method の receiver 型
     * (例 {@code externalApi.each(...)} の {@code externalApi}) と、その
     * method の functional interface parameter が実際に instantiate される型
     * (lambda parameter の型) は独立した情報であり、前者を後者の根拠にできない
     * (external な receiver を持つ method が in-scope 型を引数に取り得るため、
     * false exclusion の原因になる)。
     */
    private boolean lambdaParamReceiverIsExternal(MethodCallExpr mce, WalkContext ctx) {
        if (!(mce.getScope().orElse(null) instanceof NameExpr name)) {
            return false;
        }
        Node node = mce;
        while ((node = node.getParentNode().orElse(null)) != null) {
            if (!(node instanceof LambdaExpr lambda)) {
                continue;
            }
            boolean declaresReceiver = lambda.getParameters().stream()
                    .anyMatch(parameter -> parameter.getNameAsString().equals(name.getNameAsString()));
            if (!declaresReceiver) {
                continue; // 外側の lambda が宣言している可能性があるため遡上を続ける
            }
            Node parent = lambda.getParentNode().orElse(null);
            if (parent instanceof com.github.javaparser.ast.body.VariableDeclarator declarator) {
                try {
                    String owner = BinaryNames.erasureOf(declarator.getType().resolve());
                    return declIndex.find(owner).isEmpty();
                } catch (RuntimeException | LinkageError e) {
                    return false;
                }
            }
            return false;
        }
        return false;
    }

    /** 式の静的型 erasure。reference type として解決できなければ null。 */
    private static String tryTypeErasureOf(Expression expression) {
        try {
            ResolvedType type = expression.calculateResolvedType().erasure();
            if (!type.isReferenceType()) {
                return null;
            }
            return BinaryNames.erasureOf(type);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
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
            // receiver が source に無い bytecode-only field (Lombok logging field 等)
            // の場合、囲み型の bytecode field 型で receiver を補完する。
            return bytecodeFieldReceiverType(mce, ctx);
        }
    }

    /** scope が単純名 / this.field で、囲み型の bytecode-only field なら field 型を返す。 */
    private String bytecodeFieldReceiverType(MethodCallExpr mce, WalkContext ctx) {
        if (mce.getScope().isEmpty() || ctx.enclosingTypeNode() == null) {
            return null;
        }
        String fieldName = null;
        var scope = mce.getScope().get();
        if (scope instanceof com.github.javaparser.ast.expr.NameExpr nameExpr) {
            fieldName = nameExpr.getNameAsString();
        } else if (scope instanceof com.github.javaparser.ast.expr.FieldAccessExpr fieldAccess
                && fieldAccess.getScope() instanceof com.github.javaparser.ast.expr.ThisExpr) {
            fieldName = fieldAccess.getNameAsString();
        }
        if (fieldName == null) {
            return null;
        }
        try {
            String ownerType = BinaryNames.forTypeLikeNode(ctx.enclosingTypeNode());
            WorkspaceSourceDeclarationIndex.TypeLocation owner = declIndex.find(ownerType).orElse(null);
            if (owner == null || !reachableContextIds.contains(owner.contextId())) {
                return null;
            }
            return bytecodeIndex.fieldType(ownerType, fieldName).orElse(null);
        } catch (RuntimeException e) {
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
        emitBytecodeOnlyCall(callNode, ctx, owner, declaringType, methodNameToken, parameterTypes, symbolKind, false);
    }

    private void emitBytecodeOnlyCall(
            Node callNode,
            WalkContext ctx,
            WorkspaceSourceDeclarationIndex.TypeLocation owner,
            String declaringType,
            String methodNameToken,
            List<String> parameterTypes,
            String symbolKind,
            boolean viaMethodReference) {
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
        // 定義位置を偽装しない: sourceLocation は省略し、owner 位置は metadata へ分離する (ADR-0005)。
        accumulator.addNode(MethodSymbol.of(
                methodId, "java", symbolKind, qualifiedName, signature, null, symbolMetadata));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("calleeOrigin", "project-bytecode-member");
        if (ctx.viaLambda()) {
            metadata.put("viaLambda", true);
        }
        if (viaMethodReference) {
            metadata.put("viaMethodReference", true);
        }
        SourceLocation callSite = sourceLocationOf(callNode);
        for (String callerId : edgeCallers(callNode, ctx)) {
            accumulator.addEdge(callerId, methodId, callSite, metadata);
        }
    }

    /**
     * 要素単位に隔離可能と確認済みの resolution failure だけを diagnostic 経路へ
     * 通し、それ以外の RuntimeException は request fatal (JAVA_INTERNAL_ERROR)
     * として伝播させる (java-analyzer feature doc「Parse・resolution・call 完全性」)。
     * LinkageError はここへ来ず
     * Main の internal error 境界で処理される。
     */
    private static void rethrowUnlessIsolableResolutionFailure(RuntimeException e) {
        String packageName = e.getClass().getPackageName();
        if (packageName.startsWith("com.github.javaparser.resolution")) {
            return;
        }
        // JavaParser 内部 frame を起点とする汎用 RuntimeException
        // (UnsupportedOperationException / IllegalStateException /
        // ConcurrentModificationException 等) も、resolve 呼び出し境界で発生する
        // 限り要素単位に隔離できる library 側 resolution failure として扱う。
        // JDK collection 内で顕在化するケース (HashMap iterator 等) があるため、
        // JDK frame を除いた最初の frame で発生元 library を判定する。depwalk
        // 自身 (合成宣言等) を起点とする例外は analyzer 側バグとして伝播させ、
        // unresolved diagnostic へ化けさせない。
        for (StackTraceElement frame : e.getStackTrace()) {
            String className = frame.getClassName();
            if (className.startsWith("java.") || className.startsWith("jdk.") || className.startsWith("sun.")) {
                continue;
            }
            if (className.startsWith("com.github.javaparser.")) {
                return;
            }
            break;
        }
        throw e;
    }

    /**
     * java-analyzer feature doc「diagnostic / error code 体系」の診断 4 項目を
     * streaming される {@code diagnostic} record へも
     * 付与するオーバーロード (multi-agent review 指摘反映: 2026-07-22)。従来は
     * ledger 経由の {@code error.details} (fatal 経路) にしか乗らず、
     * {@code metadata.allowIncompleteAnalysis=true} で成功時に残る diagnostic
     * には 4 項目が欠落していた。
     */
    private void reportUnresolved(Node callNode, WalkContext ctx, Map<String, Object> metadata) {
        accumulator.incrementUnresolved();
        String relatedMethodId = ctx.callerMethodIds().isEmpty() ? null : ctx.callerMethodIds().get(0);
        accumulator.addDiagnostic(Diagnostic.of(
                JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.severity(),
                JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.code(),
                // PR review 指摘反映 (2026-07-22): callNode.toString() は JavaParser が
                // 再構築した source 断片 (literal を含む) であり、sanitize 制約
                // (error.details と同様に diagnostic record にも source 本文を含めない。
                // java-analyzer feature doc「diagnostic / error code 体系」)
                // に違反しうる。安定な AST ノード種別名だけを使い、位置は既存の
                // sourceLocation フィールドに委ねる。
                "failed to resolve " + callNode.getClass().getSimpleName(),
                sourceLocationOf(callNode),
                relatedMethodId,
                metadata));
    }

    /**
     * 宣言列挙側 ({@code md.resolve()} / {@code cd.resolve()}) の解決失敗。呼び出し式側の
     * {@link #reportUnresolved(Node, WalkContext, Map)} と異なり、宣言そのものが対象のため
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
            for (String callerId : edgeCallers(callNode, ctx)) {
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
        // bytecode 候補を source 宣言へ再対応付けするのは、宣言型が scope 内 source に
        // 存在し、呼出元 context から依存到達可能な場合だけ
        // (java-analyzer feature doc「solver 層の bytecode member 合成」)。external /
        // JDK / 非依存 context を workspace 全体の名前一致で source へ戻さない。
        boolean remappable = declIndex.find(candidate.declaringType())
                .map(owner -> reachableContextIds.contains(owner.contextId()))
                .orElse(false);
        return (remappable ? sourceMethodIndex.find(candidate) : java.util.Optional.<MethodSymbol>empty()).orElseGet(() -> {
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
