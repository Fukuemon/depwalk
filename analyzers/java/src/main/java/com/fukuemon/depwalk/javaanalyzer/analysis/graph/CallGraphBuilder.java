package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.JavaDiagnosticCode;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResolver;
import com.fukuemon.depwalk.javaanalyzer.analysis.augment.SynthesizedBytecodeMethodDeclaration;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.AnalysisGapLedger;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteId;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteInventory;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.InjectedDeclarations;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteOutcomeLedger;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.ProjectBytecodeMemberIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.WorkspaceSourceDeclarationIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResult;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.TypeSite;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.MethodIds;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.spring.EventListenerIndex;
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
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserConstructorDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * AST を 1 ファイルずつ走査し、declared method / constructor / static initializer の
 * {@code methodSymbol} と、呼び出し式ごとの帰属型決定 + {@code callEdge} を {@link GraphAccumulator}
 * へ積み上げる。呼び出し先の型解決に AST ノードの identity が必要な場面
 * ({@link BinaryNames#forTypeLikeNode(Node)}) は、ファイルの visit 順序に依存しない純粋な構造計算で
 * 実装しているため、ファイル単位で 1 パス (declare + call-edge を同時に処理) で完結できる。
 *
 * <p>本クラスが持つのは、AST の走査、呼び出しの種類ごとの振り分け、method call と object creation と
 * 明示の constructor invocation の解決、call site の終端の ledger への記録である。call site 1 件に
 * つき必ず 1 つの終端 (emitted / excluded / diagnostic) を記録し、完全性 gate から漏らさない。
 *
 * <p>次の処理は持たず、それぞれの class に任せる。
 * <ul>
 *   <li>symbol の生成: {@link MethodSymbolFactory}
 *   <li>解決の失敗の分類: {@link UnresolvedCallClassifier} と {@link BytecodeRescue}
 *   <li>method reference と constructor reference の解決: {@link MethodReferenceResolver}
 *   <li>dispatch の候補の edge: {@link DispatchCandidateEdgeEmitter}
 *   <li>callable の実体への edge: {@link CallableInvocationEdgeEmitter}
 *   <li>Spring イベントの listener への edge: {@link EventEdgeEmitter}
 *   <li>診断の出力: {@link UnresolvedDiagnostics}
 * </ul>
 */
public final class CallGraphBuilder {

    private final SourceLocations sourceLocations;
    private final AttributionResolver attributionResolver;
    private final GraphAccumulator accumulator;
    private final MethodSymbolFactory methodSymbols;
    private final BytecodeRescue bytecodeRescue;
    private final UnresolvedCallClassifier unresolvedCallClassifier;
    private final EventEdgeEmitter eventEdges;
    private final DispatchCandidateEdgeEmitter dispatchCandidates;
    private final CallableInvocationEdgeEmitter callableInvocations;
    private final MethodReferenceResolver methodReferences;
    private final UnresolvedDiagnostics diagnostics;
    private final CallSiteOutcomeLedger ledger;
    private final AnalysisGapLedger gapLedger;
    /** 現在処理中 CU の workspace 相対 path ({@link #process} が設定する)。 */
    private String currentPath;

    /**
     * @param workspaceRoot source location を相対化する workspace root (絶対・正規化済み)
     * @param ledger call site ごとの終端 (emitted / excluded / diagnostic) を記録する完全性 gate 用 ledger。
     *     どの経路でも call site 1 件につき必ず 1 つの終端を commit する
     * @param gapLedger resolve できない宣言を、call site に属さない欠けとして記録する完全性 gate 用 ledger
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
            AnalysisGapLedger gapLedger,
            WorkspaceSourceDeclarationIndex declIndex,
            ProjectBytecodeMemberIndex bytecodeIndex,
            Set<String> reachableContextIds,
            EventListenerIndex eventListenerIndex,
            CallablePassIndex callablePassIndex) {
        this.sourceLocations = new SourceLocations(workspaceRoot);
        this.attributionResolver = attributionResolver;
        this.accumulator = accumulator;
        ReachableOwners reachableOwners = new ReachableOwners(declIndex, reachableContextIds);
        this.methodSymbols =
                new MethodSymbolFactory(accumulator, sourceLocations, sourceMethodIndex, reachableOwners);
        this.bytecodeRescue = new BytecodeRescue(sootUpIndex, declIndex, bytecodeIndex, reachableOwners);
        this.ledger = ledger;
        this.gapLedger = gapLedger;
        this.unresolvedCallClassifier = new UnresolvedCallClassifier(bytecodeRescue);
        this.diagnostics = new UnresolvedDiagnostics(accumulator, sourceLocations);
        this.eventEdges = new EventEdgeEmitter(
                accumulator,
                methodSymbols,
                sourceLocations,
                eventListenerIndex,
                reachableOwners,
                diagnostics,
                this::edgeCallers);
        this.dispatchCandidates = new DispatchCandidateEdgeEmitter(
                accumulator,
                sootUpIndex,
                new SpringInjectionMatcher(springResult),
                methodSymbols,
                diagnostics,
                this::edgeCallers);
        this.callableInvocations = new CallableInvocationEdgeEmitter(
                accumulator,
                methodSymbols,
                sourceLocations,
                callablePassIndex,
                reachableOwners,
                diagnostics,
                this::edgeCallers);
        this.methodReferences = new MethodReferenceResolver(
                attributionResolver,
                methodSymbols,
                bytecodeRescue,
                unresolvedCallClassifier,
                diagnostics,
                dispatchCandidates,
                new Recorder());
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

    // package-private: EventEdgeEmitter が walk の文脈 (囲み型 / caller) をそのまま受け取る。
    record WalkContext(Node enclosingTypeNode, List<String> callerMethodIds, boolean viaLambda) {
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
            if (InjectedDeclarations.isInjected(md)) {
                // 注入宣言を source 宣言として emit すると、生成 member が source 由来の
                // methodSymbol (位置付き) を偽装する。caller として扱わず、呼び出し側の
                // bytecode-only member 出力契約 (下の injected 分岐) だけに載せる。
                return;
            }
            walkCallableDeclaration(
                    node,
                    ctx,
                    () -> methodSymbols.buildMethodSymbol(
                            AttributionResult.scopeInternal(BinaryNames.forTypeLikeNode(ctx.enclosingTypeNode())),
                            md.resolve()),
                    md.getNameAsString(),
                    "failed to resolve method declaration: " + md.getNameAsString(),
                    () -> CallSiteInventory.CallerIdentities.methodCallerId(ctx.enclosingTypeNode(), md, currentPath));
            return;
        }
        if (node instanceof ConstructorDeclaration cd) {
            if (InjectedDeclarations.isInjected(cd)) {
                // 注入 constructor は解決専用の標識 (method 側の walk skip と同じ理由)。
                return;
            }
            walkCallableDeclaration(
                    node,
                    ctx,
                    () -> methodSymbols.buildConstructorSymbol(
                            AttributionResult.scopeInternal(BinaryNames.forTypeLikeNode(ctx.enclosingTypeNode())),
                            cd.resolve()),
                    cd.getNameAsString(),
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
                    ccd.getNameAsString(),
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
            methodReferences.process(mre, ctx);
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
     * {@code failureMessage} の診断を出して宣言の欠けを ledger へ記録し、{@code fallbackCallerId}
     * (失敗時のみ評価する。宣言を解決できない site も完全性 gate から漏らさないための識別子) を
     * caller に子を辿る。
     */
    private void walkCallableDeclaration(
            Node node,
            WalkContext ctx,
            Supplier<MethodSymbol> symbolFactory,
            String declarationName,
            String failureMessage,
            Supplier<String> fallbackCallerId) {
        MethodSymbol symbol;
        try {
            symbol = symbolFactory.get();
        } catch (RuntimeException | LinkageError e) {
            reportUnresolvedDeclaration(node, declarationName, failureMessage, e);
            recurseChildren(node, ctx.withCaller(List.of(fallbackCallerId.get())));
            return;
        }
        accumulator.addNode(symbol);
        recurseChildren(node, ctx.withCaller(List.of(symbol.methodId())));
    }

    /** 宣言の resolve の失敗を、warning の診断と、完全性 gate の対象になる ledger の欠けの両方に記録する。 */
    private void reportUnresolvedDeclaration(Node declNode, String declarationName, String message, Throwable failure) {
        diagnostics.reportUnresolvedDeclaration(declNode, message);
        gapLedger.recordUnresolvedDeclaration(
                JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.code(), currentPath, declNode, declarationName, failure);
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
        // event edge はこの call site の通常の終端への追加分: publishEvent 呼び出し自体は
        // 通常 external target として excluded 終端し、listener への edge は outcome
        // ledger の外で emit される。
        eventEdges.emit(mce, ctx);
        ResolvedMethodDeclaration resolved;
        try {
            resolved = mce.resolve();
        } catch (RuntimeException e) {
            // resolve に失敗した call は callable invocation 追跡の対象外
            // (CallableInvocationEdgeEmitter は解決済み宣言を前提とする)。
            rethrowUnlessIsolableResolutionFailure(e);
            recordResolutionFailure(
                    mce, CallSiteId.CallKind.METHOD_CALL, ctx,
                    unresolvedCallClassifier.classifyMethodCall(mce, ctx.enclosingTypeNode()),
                    new PendingDiagnostic(
                            "unresolved-method-call", mce.getNameAsString(),
                            () -> diagnostics.metadataOf(
                                    UnresolvedDiagnostics.PHASE_BYTECODE_RESCUE, e, mce.getScope().orElse(null),
                                    "implicit-this")));
            return;
        }
        callableInvocations.emit(mce, ctx, resolved);

        SootUpTypeHierarchyIndex.MethodCandidate bytecodeOnly = bytecodeOnlyCandidate(resolved);
        if (bytecodeOnly != null) {
            // 型名 scope の static call を instance の合成 / 注入 member で解決しない
            // (usage 経路は staticOnly を持たず、JavaParser は AST member の static 性を
            // 型名 scope で検査しないため、emit 前にここで検査する)。
            if (!bytecodeOnly.isStatic() && mce.getScope().isPresent()
                    && BytecodeRescue.isTypeNameScope(mce.getScope().get())) {
                reportAndCommitUnresolved(mce, CallSiteId.CallKind.METHOD_CALL, ctx,
                        "unresolved-method-call", mce.getNameAsString(),
                        diagnostics.metadataOf(
                                UnresolvedDiagnostics.PHASE_SYNTHESIS_STATIC_GUARD, null, mce.getScope().get(), null));
                return;
            }
            emitBytecodeOnlyMember(
                    mce, CallSiteId.CallKind.METHOD_CALL, ctx,
                    bytecodeOnly, bytecodeOnly.methodName(), "method");
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
        String dispatch = AttributionSites.dispatchOf(resolved);
        SourceLocation callSite = emitResolvedEdge(
                mce, CallSiteId.CallKind.METHOD_CALL, ctx, calleeSymbol,
                CallEdgeMetadata.forCall(dispatch, ctx.viaLambda()));
        dispatchCandidates.emit(
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
            recordResolutionFailure(
                    oce, CallSiteId.CallKind.OBJECT_CREATION, ctx,
                    unresolvedCallClassifier.classifyObjectCreation(oce),
                    new PendingDiagnostic(
                            "unresolved-constructor-call", oce.getTypeAsString(),
                            () -> diagnostics.metadataOf(
                                    UnresolvedDiagnostics.PHASE_BYTECODE_RESCUE, e, oce.getScope().orElse(null), "none")));
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
            recordResolutionFailure(
                    ecis, CallSiteId.CallKind.EXPLICIT_CONSTRUCTOR_INVOCATION, ctx,
                    unresolvedCallClassifier.classifyExplicitConstructorInvocation(ecis, ctx.enclosingTypeNode()),
                    new PendingDiagnostic(
                            "unresolved-constructor-call", ecis.isThis() ? "this" : "super",
                            () -> diagnostics.metadataOf(
                                    UnresolvedDiagnostics.PHASE_BYTECODE_RESCUE, e, null, ecis.isThis() ? "this" : "super")));
            return;
        }
        emitConstructorCall(resolved, ecis, ctx, CallSiteId.CallKind.EXPLICIT_CONSTRUCTOR_INVOCATION);
    }

    private void emitConstructorCall(
            ResolvedConstructorDeclaration resolved, Node callNode, WalkContext ctx, CallSiteId.CallKind kind) {
        // 注入 constructor でも attribution (scope 除外の理由分類) は通常経路と同じに
        // 保ち、除外理由が注入の有無で変わらないようにする。
        TypeSite declaringSite = AttributionSites.typeSiteOf(resolved.declaringType());
        AttributionResult attribution = attributionResolver.resolveConstructor(declaringSite);
        if (attribution.isOmitted()) {
            commitExcluded(callNode, kind, ctx, attribution);
            return;
        }
        SootUpTypeHierarchyIndex.MethodCandidate injectedCtor = injectedConstructorCandidate(resolved);
        if (injectedCtor != null) {
            emitBytecodeOnlyMember(
                    callNode, kind, ctx, injectedCtor, MethodIds.CONSTRUCTOR_TOKEN, "constructor");
            return;
        }
        MethodSymbol calleeSymbol = methodSymbols.buildConstructorSymbol(attribution, resolved);
        emitResolvedEdge(callNode, kind, ctx, calleeSymbol, CallEdgeMetadata.forCall(null, ctx.viaLambda()));
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

    /** attribution を経ない external-target の明示除外 commit (field 補完経路)。 */
    private void commitExcludedExternal(Node callNode, CallSiteId.CallKind kind, WalkContext ctx) {
        for (String caller : ledgerCallers(callNode, ctx)) {
            ledger.commitExcluded(
                    CallSiteInventory.of(callNode, currentPath, kind, caller),
                    CallSiteOutcomeLedger.REASON_EXTERNAL_TARGET);
        }
    }

    /**
     * 解決できた callee を node として積み、caller ごとの edge を足して emitted を記録する。
     *
     * @return edge に付けた call site の位置。dispatch の候補の edge にも同じ位置を使う
     */
    private SourceLocation emitResolvedEdge(
            Node callNode, CallSiteId.CallKind kind, WalkContext ctx, MethodSymbol calleeSymbol,
            Map<String, Object> edgeMetadata) {
        accumulator.addNode(calleeSymbol);
        SourceLocation callSite = sourceLocations.sourceLocationOf(callNode);
        for (String callerId : edgeCallers(callNode, ctx)) {
            accumulator.addEdge(callerId, calleeSymbol.methodId(), callSite, edgeMetadata);
        }
        commitEmitted(callNode, kind, ctx);
        return callSite;
    }

    /** 解決できなかった call site を、streaming する diagnostic と ledger の終端の両方に記録する。 */
    private void reportAndCommitUnresolved(
            Node callNode, CallSiteId.CallKind kind, WalkContext ctx, String reason, String target,
            Map<String, Object> diagnosticMetadata) {
        diagnostics.reportUnresolved(callNode, ctx.callerMethodIds(), diagnosticMetadata);
        commitDiagnostic(callNode, kind, ctx, reason, target, diagnosticMetadata);
    }

    /**
     * 分類が救済にも external にも至らなかったときに、diagnostic と ledger に残す内容。
     *
     * @param reason ledger の reason
     * @param target ledger の target
     * @param metadata 診断の metadata。diagnostic に残すときだけ評価する
     */
    record PendingDiagnostic(String reason, String target, Supplier<Map<String, Object>> metadata) {
    }

    /**
     * resolve できなかった call site を、分類の結果に応じて救済した member の edge、external の除外、
     * diagnostic のどれかで記録する。
     */
    private void recordResolutionFailure(
            Node callNode, CallSiteId.CallKind kind, WalkContext ctx,
            UnresolvedCallClassifier.Classification classification, PendingDiagnostic pending) {
        switch (classification) {
            case UnresolvedCallClassifier.Rescued rescued ->
                    emitBytecodeOnlyCall(callNode, kind, ctx, rescued.rescue());
            case UnresolvedCallClassifier.External _ -> commitExcludedExternal(callNode, kind, ctx);
            case UnresolvedCallClassifier.Unresolved _ -> reportAndCommitUnresolved(
                    callNode, kind, ctx, pending.reason(), pending.target(), pending.metadata().get());
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

    /** {@link MethodReferenceResolver} に、この builder の ledger への記録を使わせる。 */
    private final class Recorder implements MethodReferenceResolver.CallSiteRecorder {

        @Override
        public SourceLocation emitResolvedEdge(
                Node callNode, CallSiteId.CallKind kind, WalkContext ctx, MethodSymbol calleeSymbol,
                Map<String, Object> edgeMetadata) {
            return CallGraphBuilder.this.emitResolvedEdge(callNode, kind, ctx, calleeSymbol, edgeMetadata);
        }

        @Override
        public void commitExcluded(
                Node callNode, CallSiteId.CallKind kind, WalkContext ctx, AttributionResult attribution) {
            CallGraphBuilder.this.commitExcluded(callNode, kind, ctx, attribution);
        }

        @Override
        public void recordResolutionFailure(
                Node callNode, CallSiteId.CallKind kind, WalkContext ctx,
                UnresolvedCallClassifier.Classification classification, PendingDiagnostic pending) {
            CallGraphBuilder.this.recordResolutionFailure(callNode, kind, ctx, classification, pending);
        }

        @Override
        public void reportAndCommitUnresolved(
                Node callNode, CallSiteId.CallKind kind, WalkContext ctx, String reason, String target,
                Map<String, Object> diagnosticMetadata) {
            CallGraphBuilder.this.reportAndCommitUnresolved(callNode, kind, ctx, reason, target, diagnosticMetadata);
        }

        @Override
        public void emitBytecodeOnlyMember(
                Node callNode, CallSiteId.CallKind kind, WalkContext ctx,
                SootUpTypeHierarchyIndex.MethodCandidate candidate, String nameToken, String symbolKind) {
            CallGraphBuilder.this.emitBytecodeOnlyMember(callNode, kind, ctx, candidate, nameToken, symbolKind);
        }

        @Override
        public void emitBytecodeOnlyCall(
                Node callNode, CallSiteId.CallKind kind, WalkContext ctx, BytecodeRescue.Rescue rescue) {
            CallGraphBuilder.this.emitBytecodeOnlyCall(callNode, kind, ctx, rescue);
        }
    }

    // ------------------------------------------------------------------
    // bytecode-only member の出力
    // ------------------------------------------------------------------

    /**
     * solver が合成した、または AST へ注入した bytecode-only member に解決した call site を、
     * 救済した member と同じ出力契約 (sourceLocation を省き、owner の位置を metadata に置き、
     * edge に calleeOrigin を付ける) で emit する。owner が scope の外なら external として除外する。
     *
     * @param nameToken method は candidate の method 名、constructor は
     *     {@link MethodIds#CONSTRUCTOR_TOKEN}
     * @param symbolKind {@code "method"} または {@code "constructor"}
     */
    private void emitBytecodeOnlyMember(
            Node callNode, CallSiteId.CallKind kind, WalkContext ctx,
            SootUpTypeHierarchyIndex.MethodCandidate candidate, String nameToken, String symbolKind) {
        BytecodeRescue.Rescue rescue = bytecodeOnlyRescue(candidate, nameToken, symbolKind);
        if (rescue == null) {
            // solver からは見えても宣言索引に載らない owner は、include と exclude で外した file の型であり、
            // 解析の失敗ではないので fatal にしない。
            commitExcludedExternal(callNode, kind, ctx);
            return;
        }
        emitBytecodeOnlyCall(callNode, kind, ctx, rescue);
    }

    /**
     * 合成 / 注入の bytecode-only member を、救済した member と同じ形の {@link BytecodeRescue.Rescue} にする。
     * owner が scope 外 (include/exclude で除外された file の型など、solver からは
     * 見えるが宣言索引に載らない型) の場合は null を返す。
     */
    private BytecodeRescue.Rescue bytecodeOnlyRescue(
            SootUpTypeHierarchyIndex.MethodCandidate candidate, String nameToken, String symbolKind) {
        WorkspaceSourceDeclarationIndex.TypeLocation owner =
                bytecodeRescue.reachableOwner(candidate).orElse(null);
        if (owner == null) {
            return null;
        }
        return new BytecodeRescue.Rescue(
                owner,
                candidate.declaringType(),
                nameToken,
                candidate.parameterTypes(),
                symbolKind);
    }

    /** 解決結果が AST 注入の bytecode-only constructor ならその candidate。 */
    static SootUpTypeHierarchyIndex.MethodCandidate injectedConstructorCandidate(
            ResolvedConstructorDeclaration resolved) {
        if (resolved instanceof JavaParserConstructorDeclaration<?> declaration
                && InjectedDeclarations.isInjected(declaration.getWrappedNode())) {
            return declaration.getWrappedNode().getData(InjectedDeclarations.KEY);
        }
        return null;
    }

    /** 解決結果が solver 合成または AST 注入の bytecode-only member ならその candidate。 */
    static SootUpTypeHierarchyIndex.MethodCandidate bytecodeOnlyCandidate(
            ResolvedMethodDeclaration resolved) {
        if (resolved instanceof SynthesizedBytecodeMethodDeclaration synthesized) {
            return synthesized.candidate();
        }
        if (resolved instanceof JavaParserMethodDeclaration declaration
                && InjectedDeclarations.isInjected(declaration.getWrappedNode())) {
            return declaration.getWrappedNode().getData(InjectedDeclarations.KEY);
        }
        return null;
    }

    /** 救済で採用した bytecode-only member を node と edge として出力し、emitted を記録する。 */
    private void emitBytecodeOnlyCall(
            Node callNode, CallSiteId.CallKind kind, WalkContext ctx, BytecodeRescue.Rescue rescue) {
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
        // 定義位置を偽装しない: sourceLocation は省略し、owner 位置は metadata へ分離する。
        accumulator.addNode(MethodSymbol.of(
                methodId, "java", rescue.symbolKind(), qualifiedName, signature, null, symbolMetadata));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("calleeOrigin", "project-bytecode-member");
        if (ctx.viaLambda()) {
            metadata.put("viaLambda", true);
        }
        if (kind == CallSiteId.CallKind.METHOD_REFERENCE) {
            metadata.put("viaMethodReference", true);
        }
        SourceLocation callSite = sourceLocations.sourceLocationOf(callNode);
        for (String callerId : edgeCallers(callNode, ctx)) {
            accumulator.addEdge(callerId, methodId, callSite, metadata);
        }
        commitEmitted(callNode, kind, ctx);
    }

    /**
     * 要素単位に隔離可能と確認済みの resolution failure だけを diagnostic 経路へ
     * 通し、それ以外の RuntimeException は request fatal (JAVA_INTERNAL_ERROR)
     * として伝播させる。
     * LinkageError はここへ来ず
     * Main の internal error 境界で処理される。
     */
    static void rethrowUnlessIsolableResolutionFailure(RuntimeException e) {
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
            // 注入 constructor は caller 帰属に数えない (source 宣言の帰属集合を
            // 注入で変えない。inventory は注入前 AST を数えるため対称になる)。
            if (member instanceof ConstructorDeclaration cd
                    && !InjectedDeclarations.isInjected(cd)) {
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
                reportUnresolvedDeclaration(
                        cd, cd.getNameAsString(), "failed to resolve constructor declaration: " + cd.getNameAsString(), e);
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

}
