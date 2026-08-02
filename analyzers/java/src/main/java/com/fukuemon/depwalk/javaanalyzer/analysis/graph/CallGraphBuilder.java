package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.JavaDiagnosticCode;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResolver;
import com.fukuemon.depwalk.javaanalyzer.analysis.augment.SynthesizedBytecodeMethodDeclaration;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteId;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteInventory;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteOutcomeLedger;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.ProjectBytecodeMemberIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.WorkspaceSourceDeclarationIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResult;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.TypeSite;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.MethodIds;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.spring.SpringDiIndex;
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
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
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
import java.util.function.Supplier;

/**
 * AST を 1 ファイルずつ走査し、declared method / constructor / static initializer の
 * {@code methodSymbol} と、呼び出し式ごとの帰属型決定 + {@code callEdge} を {@link GraphAccumulator}
 * へ積み上げる。呼び出し先の型解決に AST ノードの identity が必要な場面
 * ({@link BinaryNames#forTypeLikeNode(Node)}) は、ファイルの visit 順序に依存しない純粋な構造計算で
 * 実装しているため、ファイル単位で 1 パス (declare + call-edge を同時に処理) で完結できる。
 *
 * <p>symbol 生成は {@link MethodSymbolFactory}、解決失敗時の bytecode 救済と external 分類は
 * {@link BytecodeRescue}、Spring DI 照合は {@link SpringInjectionMatcher}、診断は
 * {@link UnresolvedDiagnostics} が担い、本クラスは走査と各経路の調停に専念する。
 *
 * <p>本クラスの契約の正本は java-analyzer feature doc「Parse・resolution・call 完全性」
 * (call site の終端記録と完全性 gate)。以下の各経路はこれに従う。
 */
public final class CallGraphBuilder {

    private final SourceLocations sourceLocations;
    private final AttributionResolver attributionResolver;
    private final GraphAccumulator accumulator;
    private final SootUpTypeHierarchyIndex sootUpIndex;
    private final MethodSymbolFactory methodSymbols;
    private final BytecodeRescue bytecodeRescue;
    private final SpringInjectionMatcher springInjections;
    private final UnresolvedDiagnostics diagnostics;
    private final CallSiteOutcomeLedger ledger;
    private final WorkspaceSourceDeclarationIndex declIndex;
    /** 現在処理中 CU の workspace 相対 path ({@link #process} が設定する)。 */
    private String currentPath;

    /**
     * @param workspaceRoot source location を相対化する workspace root (絶対・正規化済み)
     * @param ledger call site ごとの終端 (emitted / excluded / diagnostic) を記録する完全性 gate 用 ledger。
     *     どの経路でも call site 1 件につき必ず 1 つの終端を commit する
     * @param declIndex workspace の source 宣言から型の所有 context と source location を引く索引。
     *     bytecode 救済の対象を workspace source を持つ型に限定する判定に使う
     * @param bytecodeIndex 呼び出し元 context の classpath 視点で bytecode member
     *     (method / constructor / field 型 / generic 戻り型) を引く索引。source だけでは解決できない
     *     候補の救済に使う
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
            Set<String> reachableContextIds) {
        this.sourceLocations = new SourceLocations(workspaceRoot);
        this.attributionResolver = attributionResolver;
        this.accumulator = accumulator;
        this.sootUpIndex = sootUpIndex;
        ReachableOwners reachableOwners = new ReachableOwners(declIndex, reachableContextIds);
        this.methodSymbols =
                new MethodSymbolFactory(accumulator, sourceLocations, sourceMethodIndex, reachableOwners);
        this.bytecodeRescue = new BytecodeRescue(sootUpIndex, declIndex, bytecodeIndex, reachableOwners);
        this.ledger = ledger;
        this.declIndex = declIndex;
        this.springInjections = new SpringInjectionMatcher(springResult);
        this.diagnostics = new UnresolvedDiagnostics(accumulator, sourceLocations);
    }

    /**
     * 1つの compilation unit を走査し、発見した node、edge、diagnostic を accumulator へ追加する。
     *
     * @param cu 解析対象の compilation unit。呼び出し後に参照は保持しない
     */
    public void process(CompilationUnit cu) {
        currentPath = cu.getStorage()
                .map(storage -> sourceLocations.recordPathOf(storage.getPath().toAbsolutePath().normalize()))
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
            walkCallableDeclaration(
                    node,
                    ctx,
                    () -> methodSymbols.buildMethodSymbol(
                            AttributionResult.scopeInternal(BinaryNames.forTypeLikeNode(ctx.enclosingTypeNode())),
                            md.resolve()),
                    "failed to resolve method declaration: " + md.getNameAsString(),
                    () -> CallSiteInventory.CallerIdentities.methodCallerId(ctx.enclosingTypeNode(), md, currentPath));
            return;
        }
        if (node instanceof ConstructorDeclaration cd) {
            walkCallableDeclaration(
                    node,
                    ctx,
                    () -> methodSymbols.buildConstructorSymbol(
                            AttributionResult.scopeInternal(BinaryNames.forTypeLikeNode(ctx.enclosingTypeNode())),
                            cd.resolve()),
                    "failed to resolve constructor declaration: " + cd.getNameAsString(),
                    () -> CallSiteInventory.CallerIdentities.constructorCallerId(
                            ctx.enclosingTypeNode(), cd, currentPath));
            return;
        }
        if (node instanceof CompactConstructorDeclaration ccd) {
            // record の compact constructor (`record User(String name) { User { validate(); } }`)。
            // canonical constructor 扱いとし、signature は record component の erasure 型列にする。
            // JavaParser の CompactConstructorDeclaration#resolve() は未実装
            // (UnsupportedOperationException) なため、record の component 列から自前で計算する。
            walkCallableDeclaration(
                    node,
                    ctx,
                    () -> methodSymbols.buildCompactConstructorSymbol(ctx.enclosingTypeNode(), ccd),
                    "failed to resolve compact constructor declaration: " + ccd.getNameAsString(),
                    () -> CallSiteInventory.CallerIdentities.compactConstructorCallerId(
                            ctx.enclosingTypeNode(), ccd, currentPath));
            return;
        }
        if (node instanceof InitializerDeclaration id) {
            walkInitializingMember(node, ctx, id.isStatic());
            return;
        }
        if (node instanceof FieldDeclaration fd) {
            walkInitializingMember(node, ctx, fd.isStatic());
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

    /**
     * 宣言 (method / constructor / compact constructor) を 1 件処理する。{@code symbolFactory} が
     * symbol を作れたら node として積み、その methodId を caller に子を辿る。例外で失敗した場合は
     * {@code failureMessage} の診断を出し、{@code fallbackCallerId} (失敗時のみ評価する。宣言を解決
     * できない site も完全性 gate から漏らさないための識別子) を caller に子を辿る。
     */
    private void walkCallableDeclaration(
            Node node,
            WalkContext ctx,
            Supplier<MethodSymbol> symbolFactory,
            String failureMessage,
            Supplier<String> fallbackCallerId) {
        MethodSymbol symbol;
        try {
            symbol = symbolFactory.get();
        } catch (RuntimeException | LinkageError e) {
            diagnostics.reportUnresolvedDeclaration(node, failureMessage);
            recurseChildren(node, ctx.withCaller(List.of(fallbackCallerId.get())));
            return;
        }
        accumulator.addNode(symbol);
        recurseChildren(node, ctx.withCaller(List.of(symbol.methodId())));
    }

    /**
     * initializer block / field 初期化子を走査する。呼び出しを含まない member は caller を変えずに
     * 素通しし、含む場合だけ static は {@code <clinit>}、instance は囲み型の全 constructor を
     * caller として子を辿る。
     */
    private void walkInitializingMember(Node node, WalkContext ctx, boolean isStatic) {
        if (!containsAnyCall(node)) {
            recurseChildren(node, ctx);
            return;
        }
        if (isStatic) {
            String clinitId = methodSymbols.ensureStaticInitializerNode(ctx.enclosingTypeNode());
            recurseChildren(node, ctx.withCaller(List.of(clinitId)));
        } else {
            recurseChildren(node, ctx.withCaller(constructorCallerIdsFor(ctx.enclosingTypeNode())));
        }
    }

    private void recurseChildren(Node node, WalkContext ctx) {
        for (Node child : node.getChildNodes()) {
            walk(child, ctx);
        }
    }

    /** subtree に呼び出し式が 1 つでもあるか (最初の 1 件で打ち切る)。 */
    private boolean containsAnyCall(Node node) {
        return node.findFirst(MethodCallExpr.class).isPresent()
                || node.findFirst(ObjectCreationExpr.class).isPresent()
                || node.findFirst(ExplicitConstructorInvocationStmt.class).isPresent()
                || node.findFirst(MethodReferenceExpr.class).isPresent();
    }

    // ------------------------------------------------------------------
    // メソッド呼び出しの処理
    // ------------------------------------------------------------------

    private void processMethodCall(MethodCallExpr mce, WalkContext ctx) {
        ResolvedMethodDeclaration resolved;
        try {
            resolved = mce.resolve();
        } catch (RuntimeException e) {
            rethrowUnlessIsolableResolutionFailure(e);
            BytecodeRescue.Rescue rescue = bytecodeRescue.methodRescue(mce, ctx.enclosingTypeNode());
            if (rescue != null) {
                emitBytecodeOnlyCall(mce, ctx, rescue, false);
                commitEmitted(mce, CallSiteId.CallKind.METHOD_CALL, ctx);
                return;
            }
            // receiver 型を (bytecode field 補完込みで) 特定できて、その型が
            // scope 内 source に存在しない場合、callee は scope 外であり
            // 理由付き external-target として分類する (ADR-0005)。
            // 例: Lombok @Slf4j の log field 経由の Logger#info 呼び出し。
            String receiverOwner = bytecodeRescue.bytecodeRescueOwner(mce, ctx.enclosingTypeNode());
            if (receiverOwner != null && declIndex.find(receiverOwner).isEmpty()) {
                commitExcludedExternal(mce, CallSiteId.CallKind.METHOD_CALL, ctx);
                return;
            }
            // 解決失敗した receiver chain を bytecode candidate の戻り値型
            // (classfile 由来) で前進解決し、復元した owner で救済 / external
            // 分類を試みる。根拠のない型推測は行わない。
            if (receiverOwner == null && mce.getScope().isPresent()) {
                String forwardOwner =
                        bytecodeRescue.chainForwardOwner(mce.getScope().get(), ctx.enclosingTypeNode());
                if (forwardOwner != null) {
                    if (declIndex.find(forwardOwner).isEmpty()) {
                        commitExcludedExternal(mce, CallSiteId.CallKind.METHOD_CALL, ctx);
                        return;
                    }
                    BytecodeRescue.Rescue forwardRescue = bytecodeRescue.methodRescueWithOwner(mce, forwardOwner);
                    if (forwardRescue != null) {
                        emitBytecodeOnlyCall(mce, ctx, forwardRescue, false);
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
                    && (bytecodeRescue.chainRootIsExternal(mce, ctx.enclosingTypeNode())
                            || bytecodeRescue.lambdaParamReceiverIsExternal(mce))) {
                commitExcludedExternal(mce, CallSiteId.CallKind.METHOD_CALL, ctx);
                return;
            }
            Map<String, Object> metadata = diagnostics.metadataOf(
                    UnresolvedDiagnostics.PHASE_BYTECODE_RESCUE, e, mce.getScope().orElse(null), "implicit-this");
            diagnostics.reportUnresolved(mce, ctx.callerMethodIds(), metadata);
            commitDiagnostic(mce, CallSiteId.CallKind.METHOD_CALL, ctx,
                    "unresolved-method-call", mce.getNameAsString(), metadata);
            return;
        }

        // solver が合成した bytecode-only member は、既存の bytecode-only member と同じ
        // 出力契約 (sourceLocation 省略 + owner metadata + calleeOrigin edge、ADR-0005)
        // で emit する。
        if (resolved instanceof SynthesizedBytecodeMethodDeclaration synthesized) {
            // 型名 scope の static call を instance 合成 member で解決しない
            // (usage 経路は staticOnly を持たないため、emit 前にここで検査する)。
            if (!synthesized.isStatic() && mce.getScope().isPresent()
                    && BytecodeRescue.isTypeNameScope(mce.getScope().get())) {
                Map<String, Object> guardMetadata = diagnostics.metadataOf(
                        UnresolvedDiagnostics.PHASE_SYNTHESIS_STATIC_GUARD, null, mce.getScope().get(), null);
                diagnostics.reportUnresolved(mce, ctx.callerMethodIds(), guardMetadata);
                commitDiagnostic(mce, CallSiteId.CallKind.METHOD_CALL, ctx,
                        "unresolved-method-call", mce.getNameAsString(), guardMetadata);
                return;
            }
            emitBytecodeOnlyCall(mce, ctx, synthesizedRescue(synthesized), false);
            commitEmitted(mce, CallSiteId.CallKind.METHOD_CALL, ctx);
            return;
        }

        TypeSite declaringSite = AttributionSites.typeSiteOf(resolved.declaringType());
        TypeSite receiverSite =
                AttributionSites.receiverSiteOf(mce, ctx.enclosingTypeNode(), resolved, declaringSite);
        AttributionResult attribution = attributionResolver.resolveMethod(declaringSite, receiverSite);
        if (attribution.isOmitted()) {
            commitExcluded(mce, CallSiteId.CallKind.METHOD_CALL, ctx, attribution);
            return;
        }

        MethodSymbol calleeSymbol = methodSymbols.buildMethodSymbol(attribution, resolved);
        accumulator.addNode(calleeSymbol);

        String dispatch = AttributionSites.dispatchOf(resolved);
        Map<String, Object> metadata = CallEdgeMetadata.forCall(dispatch, ctx.viaLambda());
        SourceLocation callSite = sourceLocations.sourceLocationOf(mce);
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
            BytecodeRescue.Rescue rescue = bytecodeRescue.constructorRescue(oce);
            if (rescue != null) {
                emitBytecodeOnlyCall(oce, ctx, rescue, false);
                commitEmitted(oce, CallSiteId.CallKind.OBJECT_CREATION, ctx);
                return;
            }
            Map<String, Object> metadata = diagnostics.metadataOf(
                    UnresolvedDiagnostics.PHASE_BYTECODE_RESCUE, e, oce.getScope().orElse(null), "none");
            diagnostics.reportUnresolved(oce, ctx.callerMethodIds(), metadata);
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
            BytecodeRescue.Rescue rescue = bytecodeRescue.explicitCtorRescue(ecis, ctx.enclosingTypeNode());
            if (rescue != null) {
                emitBytecodeOnlyCall(ecis, ctx, rescue, false);
                commitEmitted(ecis, CallSiteId.CallKind.EXPLICIT_CONSTRUCTOR_INVOCATION, ctx);
                return;
            }
            String ctorOwner = bytecodeRescue.explicitCtorOwner(ecis, ctx.enclosingTypeNode());
            if (ctorOwner != null && declIndex.find(ctorOwner).isEmpty()) {
                commitExcludedExternal(ecis, CallSiteId.CallKind.EXPLICIT_CONSTRUCTOR_INVOCATION, ctx);
                return;
            }
            Map<String, Object> metadata = diagnostics.metadataOf(
                    UnresolvedDiagnostics.PHASE_BYTECODE_RESCUE, e, null, ecis.isThis() ? "this" : "super");
            diagnostics.reportUnresolved(ecis, ctx.callerMethodIds(), metadata);
            commitDiagnostic(ecis, CallSiteId.CallKind.EXPLICIT_CONSTRUCTOR_INVOCATION, ctx,
                    "unresolved-constructor-call", ecis.isThis() ? "this" : "super", metadata);
            return;
        }
        emitConstructorCall(resolved, ecis, ctx, CallSiteId.CallKind.EXPLICIT_CONSTRUCTOR_INVOCATION);
    }

    private void emitConstructorCall(
            ResolvedConstructorDeclaration resolved, Node callNode, WalkContext ctx, CallSiteId.CallKind kind) {
        TypeSite declaringSite = AttributionSites.typeSiteOf(resolved.declaringType());
        AttributionResult attribution = attributionResolver.resolveConstructor(declaringSite);
        if (attribution.isOmitted()) {
            commitExcluded(callNode, kind, ctx, attribution);
            return;
        }
        MethodSymbol calleeSymbol = methodSymbols.buildConstructorSymbol(attribution, resolved);
        accumulator.addNode(calleeSymbol);

        Map<String, Object> metadata = CallEdgeMetadata.forCall(null, ctx.viaLambda());
        SourceLocation callSite = sourceLocations.sourceLocationOf(callNode);
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
            BytecodeRescue.Rescue rescue =
                    bytecodeRescue.methodReferenceRescue(mre, () -> inferFunctionalInterfaceArity(mre));
            if (rescue != null) {
                emitBytecodeOnlyCall(mre, ctx, rescue, true);
                commitEmitted(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx);
                return;
            }
            String referenceOwner = bytecodeRescue.methodReferenceOwner(mre);
            if (referenceOwner != null && declIndex.find(referenceOwner).isEmpty()) {
                commitExcludedExternal(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx);
                return;
            }
            Map<String, Object> metadata = diagnostics.metadataOf(
                    UnresolvedDiagnostics.PHASE_BYTECODE_RESCUE, e, mre.getScope(), null);
            diagnostics.reportUnresolved(mre, ctx.callerMethodIds(), metadata);
            commitDiagnostic(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                    "unresolved-method-reference", mre.getIdentifier(), metadata);
            return;
        }

        // solver が合成した bytecode-only member への reference は、method call の synthesized 経路と
        // 同じ出力契約 (sourceLocation 省略 + owner metadata + calleeOrigin edge、
        // ADR-0005) で emit する (従来この経路は通常 symbol として emit され、
        // この出力契約から漏れていた)。
        if (resolved instanceof SynthesizedBytecodeMethodDeclaration synthesized) {
            emitBytecodeOnlyCall(mre, ctx, synthesizedRescue(synthesized), true);
            commitEmitted(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx);
            return;
        }

        TypeSite declaringSite = AttributionSites.typeSiteOf(resolved.declaringType());
        TypeSite receiverSite = AttributionSites.typeSiteOfExpression(mre.getScope());
        AttributionResult attribution = attributionResolver.resolveMethod(declaringSite, receiverSite);
        if (attribution.isOmitted()) {
            commitExcluded(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx, attribution);
            return;
        }

        MethodSymbol calleeSymbol = methodSymbols.buildMethodSymbol(attribution, resolved);
        accumulator.addNode(calleeSymbol);

        String dispatch = AttributionSites.dispatchOf(resolved);
        Map<String, Object> metadata = CallEdgeMetadata.forMethodReference(dispatch, ctx.viaLambda());
        SourceLocation callSite = sourceLocations.sourceLocationOf(mre);
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
                Map<String, Object> metadata = diagnostics.metadataOf(
                        UnresolvedDiagnostics.PHASE_SOLVER_RESOLVE, null, mre.getScope(), null);
                diagnostics.reportUnresolved(mre, ctx.callerMethodIds(), metadata);
                commitDiagnostic(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                        "unresolved-constructor-reference", mre.getScope().toString(), metadata);
                return;
            }
            scopeDecl = scopeType.asReferenceType().getTypeDeclaration().orElse(null);
            if (scopeDecl == null) {
                Map<String, Object> metadata = diagnostics.metadataOf(
                        UnresolvedDiagnostics.PHASE_SOLVER_RESOLVE, null, mre.getScope(), null);
                diagnostics.reportUnresolved(mre, ctx.callerMethodIds(), metadata);
                commitDiagnostic(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                        "unresolved-constructor-reference", mre.getScope().toString(), metadata);
                return;
            }
        } catch (RuntimeException | LinkageError e) {
            Map<String, Object> metadata = diagnostics.metadataOf(
                    UnresolvedDiagnostics.PHASE_SOLVER_RESOLVE, e, mre.getScope(), null);
            diagnostics.reportUnresolved(mre, ctx.callerMethodIds(), metadata);
            commitDiagnostic(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                    "unresolved-constructor-reference", mre.getScope().toString(), metadata);
            return;
        }

        TypeSite declaringSite = AttributionSites.typeSiteOf(scopeDecl);
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
            BytecodeRescue.Rescue rescue =
                    bytecodeRescue.constructorReferenceRescue(scopeDecl, () -> inferFunctionalInterfaceArity(mre));
            if (rescue != null) {
                emitBytecodeOnlyCall(mre, ctx, rescue, true);
                commitEmitted(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx);
                return;
            }
            Map<String, Object> metadata = diagnostics.metadataOf(
                    UnresolvedDiagnostics.PHASE_CONSTRUCTOR_REFERENCE_SELECTION, null, mre.getScope(), null);
            diagnostics.reportUnresolved(mre, ctx.callerMethodIds(), metadata);
            commitDiagnostic(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                    "ambiguous-constructor-reference", mre.getScope().toString(), metadata);
            return;
        }

        MethodSymbol calleeSymbol = methodSymbols.buildConstructorSymbol(attribution, resolvedCtor);
        accumulator.addNode(calleeSymbol);

        Map<String, Object> metadata = CallEdgeMetadata.forMethodReference(null, ctx.viaLambda());
        SourceLocation callSite = sourceLocations.sourceLocationOf(mre);
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

    // ------------------------------------------------------------------
    // 呼び出し箇所ごとの結果台帳 (ledger)
    // ------------------------------------------------------------------

    /** ledger 用の実効 caller (caller 不在の site は <clinit> / placeholder へ帰着)。 */
    private List<String> ledgerCallers(Node callNode, WalkContext ctx) {
        return CallSiteInventory.CallerIdentities.effectiveCallers(
                ctx.callerMethodIds(), ctx.enclosingTypeNode(), callNode, currentPath);
    }

    /** edge 出力に使える caller (placeholder を除外し、caller 不在時は <clinit> node を保証する)。 */
    private List<String> edgeCallers(Node callNode, WalkContext ctx) {
        List<String> callers = ledgerCallers(callNode, ctx);
        // enum constant 引数など member 外の call は <clinit> caller で edge 化する
        // (caller ごとに変わらない条件なのでループ外で判定する)。
        boolean needsStaticInitializer = ctx.callerMethodIds().isEmpty() && ctx.enclosingTypeNode() != null;
        List<String> result = new ArrayList<>();
        for (String caller : callers) {
            if (CallSiteInventory.CallerIdentities.isPlaceholder(caller)) {
                continue;
            }
            if (needsStaticInitializer) {
                methodSymbols.ensureStaticInitializerNode(ctx.enclosingTypeNode());
            }
            result.add(caller);
        }
        return result;
    }

    private void commitEmitted(Node callNode, CallSiteId.CallKind kind, WalkContext ctx) {
        for (String caller : ledgerCallers(callNode, ctx)) {
            if (CallSiteInventory.CallerIdentities.isPlaceholder(caller)) {
                // caller 宣言が resolve できない site は edge を出力できないため、
                // emitted でなく primary diagnostic として完全性 gate に残す。
                // 診断 metadata は「call 解決の失敗段階」を表すため、
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
    // bytecode-only member の出力
    // ------------------------------------------------------------------

    /** solver が合成した bytecode-only member を、救済経路と同じ出力契約へ載せる。 */
    private BytecodeRescue.Rescue synthesizedRescue(SynthesizedBytecodeMethodDeclaration synthesized) {
        return new BytecodeRescue.Rescue(
                bytecodeRescue.requireReachableOwner(synthesized),
                synthesized.candidate().declaringType(),
                synthesized.getName(),
                synthesized.candidate().parameterTypes(),
                "method");
    }

    /** 救済で採用した bytecode-only member を node + edge として出力する (ADR-0005 の出力契約)。 */
    private void emitBytecodeOnlyCall(
            Node callNode, WalkContext ctx, BytecodeRescue.Rescue rescue, boolean viaMethodReference) {
        String signature = MethodIds.signature(
                rescue.declaringType(), rescue.methodNameToken(), rescue.parameterTypes());
        String methodId = MethodIds.methodId(signature);
        WorkspaceSourceDeclarationIndex.TypeLocation owner = rescue.owner();
        if (owner.path() == null) {
            throw new IllegalStateException(
                    "adopted a bytecode-only member without a constructible owner location: " + methodId);
        }
        String qualifiedName = rescue.declaringType().replace('$', '.') + "." + rescue.methodNameToken();
        Map<String, Object> symbolMetadata = new LinkedHashMap<>();
        symbolMetadata.put("declarationOrigin", "project-bytecode");
        symbolMetadata.put("sourceAnchor", "owner-type");
        Map<String, Object> ownerLocation = new LinkedHashMap<>();
        ownerLocation.put("path", owner.path());
        ownerLocation.put("startLine", owner.beginLine());
        symbolMetadata.put("ownerSourceLocation", ownerLocation);
        // 定義位置を偽装しない: sourceLocation は省略し、owner 位置は metadata へ分離する (ADR-0005)。
        accumulator.addNode(MethodSymbol.of(
                methodId, "java", rescue.symbolKind(), qualifiedName, signature, null, symbolMetadata));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("calleeOrigin", "project-bytecode-member");
        if (ctx.viaLambda()) {
            metadata.put("viaLambda", true);
        }
        if (viaMethodReference) {
            metadata.put("viaMethodReference", true);
        }
        SourceLocation callSite = sourceLocations.sourceLocationOf(callNode);
        for (String callerId : edgeCallers(callNode, ctx)) {
            accumulator.addEdge(callerId, methodId, callSite, metadata);
        }
    }

    /**
     * 要素単位に隔離可能と確認済みの resolution failure だけを diagnostic 経路へ
     * 通し、それ以外の RuntimeException は request fatal (JAVA_INTERNAL_ERROR)
     * として伝播させる。
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

    private List<String> constructorCallerIdsFor(Node enclosingType) {
        List<ConstructorDeclaration> constructors = new ArrayList<>();
        for (Node member : membersOf(enclosingType)) {
            if (member instanceof ConstructorDeclaration cd) {
                constructors.add(cd);
            }
        }
        if (constructors.isEmpty()) {
            return List.of(methodSymbols.ensureDefaultConstructorNode(enclosingType));
        }
        List<String> ids = new ArrayList<>();
        String declaringBinaryName = BinaryNames.forTypeLikeNode(enclosingType);
        for (ConstructorDeclaration cd : constructors) {
            try {
                List<String> paramTypes = AttributionSites.paramBinaryNames(cd.resolve());
                String signature = MethodIds.signature(declaringBinaryName, MethodIds.CONSTRUCTOR_TOKEN, paramTypes);
                ids.add(MethodIds.methodId(signature));
            } catch (RuntimeException | LinkageError e) {
                diagnostics.reportUnresolvedDeclaration(cd, "failed to resolve constructor declaration: " + cd.getNameAsString());
                ids.add(CallSiteInventory.CallerIdentities.constructorCallerId(enclosingType, cd, currentPath));
            }
        }
        return ids;
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
     */
    private static boolean isExplicitSuperDispatch(Node callNode) {
        if (callNode instanceof MethodCallExpr methodCall) {
            return methodCall.getScope().filter(SuperExpr.class::isInstance).isPresent();
        }
        return callNode instanceof MethodReferenceExpr methodReference
                && methodReference.getScope() instanceof SuperExpr;
    }

    /**
     * call siteのreceiverが同時に満たす静的型を返す。
     *
     * <p>通常のreference typeはfallbackの1型だけを返す。型変数またはintersection typeでは
     * 全extends境界を返し、SootUp側で候補の積集合を取れるようにする。型解決に失敗した場合も
     * erasure済みfallbackを保持する。返す列は重複なし。
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
     * 型変数・intersection・上限wildcardを再帰展開し、最終的なreference型境界を収集する。
     * 間接境界 ({@code T extends U}, {@code U extends A & B}) でもAとBの両方を保持する。
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
