package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResolver;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResult;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.TypeSite;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteId;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.MethodIds;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;
import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.List;
import java.util.Map;

/**
 * method reference と constructor reference ({@code Foo::new}) の参照先を決め、call site の結果を記録させる。
 *
 * <p>参照先には lambda と同じ囲みメソッドへの帰属規則を当て、囲みメソッドを caller とする edge に
 * {@code viaMethodReference: true} を付ける。constructor reference は solver が resolve できないため、
 * 参照先の型の constructor から候補を選ぶ。AST の走査と、ledger と accumulator への記録の実体は
 * 持たない。走査は {@link CallGraphBuilder} が行い、記録は {@link CallSiteRecorder} に任せる。
 */
final class MethodReferenceResolver {

    /**
     * call site 1 件の終端を ledger に記録する操作。どの操作も、呼ばれた call site の終端を 1 つだけ
     * 記録する。edge を出す操作は、emitted を記録するときに callee の node と edge も積み、除外を
     * 記録するときは何も積まない。
     */
    interface CallSiteRecorder {

        /**
         * 解決できた callee の edge を足し、emitted を記録する。
         *
         * @return edge に付けた call site の位置
         */
        SourceLocation emitResolvedEdge(
                Node callNode, CallSiteId.CallKind kind, CallGraphBuilder.WalkContext ctx, MethodSymbol calleeSymbol,
                Map<String, Object> edgeMetadata);

        /** 帰属の結果が scope の外だった call site を、除外として記録する。 */
        void commitExcluded(
                Node callNode, CallSiteId.CallKind kind, CallGraphBuilder.WalkContext ctx,
                AttributionResult attribution);

        /** resolve できなかった call site を、分類の結果に応じた終端で記録する。 */
        void recordResolutionFailure(
                Node callNode, CallSiteId.CallKind kind, CallGraphBuilder.WalkContext ctx,
                UnresolvedCallClassifier.Classification classification, PendingDiagnostic pending);

        /** 解決できなかった call site を、diagnostic と ledger の両方に記録する。 */
        void reportAndCommitUnresolved(
                Node callNode, CallSiteId.CallKind kind, CallGraphBuilder.WalkContext ctx, String reason,
                String target, Map<String, Object> diagnosticMetadata);

        /**
         * solver が合成した、または AST へ注入した bytecode-only member への edge を足し、emitted を記録する。
         * owner が scope の外なら edge を出さず、external の除外を記録する。
         */
        void emitBytecodeOnlyMember(
                Node callNode, CallSiteId.CallKind kind, CallGraphBuilder.WalkContext ctx,
                SootUpTypeHierarchyIndex.MethodCandidate candidate, String nameToken, String symbolKind);

        /** 救済で採用した bytecode-only member への edge を足す。 */
        void emitBytecodeOnlyCall(
                Node callNode, CallSiteId.CallKind kind, CallGraphBuilder.WalkContext ctx,
                BytecodeRescue.Rescue rescue);
    }

    private final AttributionResolver attributionResolver;
    private final MethodSymbolFactory methodSymbols;
    private final BytecodeRescue bytecodeRescue;
    private final UnresolvedCallClassifier unresolvedCallClassifier;
    private final UnresolvedDiagnostics diagnostics;
    private final DispatchCandidateEdgeEmitter dispatchCandidates;
    private final CallSiteRecorder recorder;

    MethodReferenceResolver(
            AttributionResolver attributionResolver,
            MethodSymbolFactory methodSymbols,
            BytecodeRescue bytecodeRescue,
            UnresolvedCallClassifier unresolvedCallClassifier,
            UnresolvedDiagnostics diagnostics,
            DispatchCandidateEdgeEmitter dispatchCandidates,
            CallSiteRecorder recorder) {
        this.attributionResolver = attributionResolver;
        this.methodSymbols = methodSymbols;
        this.bytecodeRescue = bytecodeRescue;
        this.unresolvedCallClassifier = unresolvedCallClassifier;
        this.diagnostics = diagnostics;
        this.dispatchCandidates = dispatchCandidates;
        this.recorder = recorder;
    }

    /** {@code Foo::new} の source 上の識別子 ({@code getIdentifier()} が返す値)。 */
    private static final String METHOD_REFERENCE_CONSTRUCTOR_IDENTIFIER = "new";

    /**
     * method reference 1 件 ({@code this::toDto} / {@code Foo::bar} / {@code Foo::new}) を解決し、
     * 結果を edge、external の除外、diagnostic のどれかで記録する。
     */
    void process(MethodReferenceExpr mre, CallGraphBuilder.WalkContext ctx) {
        if (METHOD_REFERENCE_CONSTRUCTOR_IDENTIFIER.equals(mre.getIdentifier())) {
            processConstructorReference(mre, ctx);
            return;
        }

        ResolvedMethodDeclaration resolved;
        try {
            resolved = mre.resolve();
        } catch (RuntimeException e) {
            ResolutionFailures.rethrowUnlessIsolable(e);
            recorder.recordResolutionFailure(
                    mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                    unresolvedCallClassifier.classifyMethodReference(mre, () -> inferFunctionalInterfaceArity(mre)),
                    new PendingDiagnostic(
                            "unresolved-method-reference", mre.getIdentifier(),
                            () -> diagnostics.metadataOf(
                                    UnresolvedDiagnostics.PHASE_BYTECODE_RESCUE, e, mre.getScope(), null)));
            return;
        }

        SootUpTypeHierarchyIndex.MethodCandidate bytecodeOnly = BytecodeOnlyDeclarations.methodCandidate(resolved);
        if (bytecodeOnly != null) {
            recorder.emitBytecodeOnlyMember(
                    mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                    bytecodeOnly, bytecodeOnly.methodName(), "method");
            return;
        }

        TypeSite declaringSite = AttributionSites.typeSiteOf(resolved.declaringType());
        TypeSite receiverSite = AttributionSites.typeSiteOfExpression(mre.getScope());
        AttributionResult attribution = attributionResolver.resolveMethod(declaringSite, receiverSite);
        if (attribution.isOmitted()) {
            recorder.commitExcluded(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx, attribution);
            return;
        }

        MethodSymbol calleeSymbol = methodSymbols.buildMethodSymbol(attribution, resolved);
        String dispatch = AttributionSites.dispatchOf(resolved);
        SourceLocation callSite = recorder.emitResolvedEdge(
                mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx, calleeSymbol,
                CallEdgeMetadata.forMethodReference(dispatch, ctx.viaLambda()));
        dispatchCandidates.emit(
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
    private void processConstructorReference(MethodReferenceExpr mre, CallGraphBuilder.WalkContext ctx) {
        ResolvedReferenceTypeDeclaration scopeDecl;
        try {
            ResolvedType scopeType = mre.getScope().calculateResolvedType();
            if (!scopeType.isReferenceType()) {
                reportUnresolvedConstructorReferenceScope(mre, ctx, null);
                return;
            }
            scopeDecl = scopeType.asReferenceType().getTypeDeclaration().orElse(null);
            if (scopeDecl == null) {
                reportUnresolvedConstructorReferenceScope(mre, ctx, null);
                return;
            }
        } catch (RuntimeException | LinkageError e) {
            reportUnresolvedConstructorReferenceScope(mre, ctx, e);
            return;
        }

        TypeSite declaringSite = AttributionSites.typeSiteOf(scopeDecl);
        AttributionResult attribution = attributionResolver.resolveConstructor(declaringSite);
        if (attribution.isOmitted()) {
            recorder.commitExcluded(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx, attribution);
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
                recorder.emitBytecodeOnlyCall(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx, rescue);
                return;
            }
            recorder.reportAndCommitUnresolved(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                    "ambiguous-constructor-reference", mre.getScope().toString(),
                    diagnostics.metadataOf(
                            UnresolvedDiagnostics.PHASE_CONSTRUCTOR_REFERENCE_SELECTION, null, mre.getScope(), null));
            return;
        }

        SootUpTypeHierarchyIndex.MethodCandidate injectedCtor =
                BytecodeOnlyDeclarations.constructorCandidate(resolvedCtor);
        if (injectedCtor != null) {
            recorder.emitBytecodeOnlyMember(
                    mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                    injectedCtor, MethodIds.CONSTRUCTOR_TOKEN, "constructor");
            return;
        }

        MethodSymbol calleeSymbol = methodSymbols.buildConstructorSymbol(attribution, resolvedCtor);
        recorder.emitResolvedEdge(
                mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx, calleeSymbol,
                CallEdgeMetadata.forMethodReference(null, ctx.viaLambda()));
    }

    /** constructor reference の参照先の型を solver が決められなかったことを診断と ledger に残す。 */
    private void reportUnresolvedConstructorReferenceScope(MethodReferenceExpr mre, CallGraphBuilder.WalkContext ctx, Throwable failure) {
        recorder.reportAndCommitUnresolved(mre, CallSiteId.CallKind.METHOD_REFERENCE, ctx,
                "unresolved-constructor-reference", mre.getScope().toString(),
                diagnostics.metadataOf(UnresolvedDiagnostics.PHASE_SOLVER_RESOLVE, failure, mre.getScope(), null));
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
}
