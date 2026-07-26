package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.JavaDiagnosticCode;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.fukuemon.depwalk.javaanalyzer.protocol.Diagnostic;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解決に失敗した call site / 宣言と、SootUp が使えない解決要求を {@code diagnostic} として積む。
 * 添える診断 metadata は sanitize 済みの安定値だけで構成する。
 *
 * <p>診断 metadata の内容と sanitize 制約の正本は java-analyzer feature doc
 * 「diagnostic / error code 体系」。
 */
final class UnresolvedDiagnostics {

    /** 診断 metadata の resolutionPhase 安定値。 */
    static final String PHASE_SOLVER_RESOLVE = "solver-resolve";
    static final String PHASE_BYTECODE_RESCUE = "bytecode-rescue";
    static final String PHASE_SYNTHESIS_STATIC_GUARD = "member-synthesis-static-guard";
    static final String PHASE_CONSTRUCTOR_REFERENCE_SELECTION = "constructor-reference-selection";

    private final GraphAccumulator accumulator;
    private final SourceLocations sourceLocations;

    UnresolvedDiagnostics(GraphAccumulator accumulator, SourceLocations sourceLocations) {
        this.accumulator = accumulator;
        this.sourceLocations = sourceLocations;
    }

    /**
     * primary diagnostic へ添える sanitize 済み診断 4 項目を構築する。
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
    Map<String, Object> metadataOf(
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
     * 診断 4 項目を streaming される {@code diagnostic} record へも付与するオーバーロード (multi-agent review 指摘反映: 2026-07-22)。従来は
     * ledger 経由の {@code error.details} (fatal 経路) にしか乗らず、
     * {@code metadata.allowIncompleteAnalysis=true} で成功時に残る diagnostic
     * には 4 項目が欠落していた。
     */
    void reportUnresolved(Node callNode, List<String> callerMethodIds, Map<String, Object> metadata) {
        accumulator.incrementUnresolved();
        String relatedMethodId = callerMethodIds.isEmpty() ? null : callerMethodIds.get(0);
        accumulator.addDiagnostic(Diagnostic.of(
                JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.severity(),
                JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.code(),
                // PR review 指摘反映 (2026-07-22): callNode.toString() は JavaParser が
                // 再構築した source 断片 (literal を含む) であり、sanitize 制約
                // (error.details と同様に diagnostic record にも source 本文を含めない)
                // に違反しうる。安定な AST ノード種別名だけを使い、位置は既存の
                // sourceLocation フィールドに委ねる。
                "failed to resolve " + callNode.getClass().getSimpleName(),
                sourceLocations.sourceLocationOf(callNode),
                relatedMethodId,
                metadata));
    }

    /**
     * 宣言列挙側 ({@code md.resolve()} / {@code cd.resolve()}) の解決失敗。呼び出し式側の
     * {@link #reportUnresolved} と異なり、宣言そのものが対象のため
     * {@code relatedMethodId} は付けない。その宣言だけ skip し、解析全体は継続する。
     */
    void reportUnresolvedDeclaration(Node declNode, String message) {
        accumulator.incrementUnresolved();
        accumulator.addDiagnostic(Diagnostic.of(
                JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.severity(),
                JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.code(),
                message,
                sourceLocations.sourceLocationOf(declNode),
                null,
                null));
    }

    void reportSootUnavailable(
            SootUpTypeHierarchyIndex.Resolution resolution,
            String targetType,
            Node callNode,
            List<String> callerMethodIds) {
        String relatedMethodId = callerMethodIds.isEmpty() ? null : callerMethodIds.get(0);
        accumulator.addDiagnostic(Diagnostic.of(
                JavaDiagnosticCode.JAVA_SOOTUP_UNAVAILABLE.severity(),
                JavaDiagnosticCode.JAVA_SOOTUP_UNAVAILABLE.code(),
                resolution.unavailableReason(),
                sourceLocations.sourceLocationOf(callNode),
                relatedMethodId,
                Map.of("targetType", targetType)));
    }
}
