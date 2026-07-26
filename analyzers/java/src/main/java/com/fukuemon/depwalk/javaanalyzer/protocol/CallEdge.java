package com.fukuemon.depwalk.javaanalyzer.protocol;

import java.util.Map;

/**
 * 解決済み caller / callee の呼び出し関係。
 *
 * <p>AST 解析、型解決、bytecode 型階層、dependency injection 解決で得た呼び出しを Core へ渡す
 * Analyzer Protocol record であり、本型は出力 schema のみを定義する。
 *
 * @param schemaVersion   Protocol version
 * @param recordType      {@code callEdge}
 * @param edgeId          Analyzer が決定的に生成する stable ID
 * @param callerMethodId  呼び出し元の {@code methodSymbol.methodId}
 * @param calleeMethodId  呼び出し先の {@code methodSymbol.methodId}
 * @param callSite        呼び出し式の source 位置 (任意)
 * @param metadata        dispatch 種別など (任意)
 */
public record CallEdge(
        String schemaVersion,
        String recordType,
        String edgeId,
        String callerMethodId,
        String calleeMethodId,
        SourceLocation callSite,
        Map<String, Object> metadata) implements ProtocolRecord {

    /** 本 record が JSONL の {@code recordType} に設定する値。 */
    public static final String RECORD_TYPE = "callEdge";

    /**
     * 現在の schema version と record type を設定した call edge を生成する。
     *
     * @param edgeId Analyzer 内で一意な edge ID
     * @param callSite 呼び出し位置。取得できなければ {@code null}
     * @param metadata dispatch や候補解決根拠。追加情報がなければ {@code null}
     */
    public static CallEdge of(
            String edgeId,
            String callerMethodId,
            String calleeMethodId,
            SourceLocation callSite,
            Map<String, Object> metadata) {
        return new CallEdge(
                ProtocolSchema.VERSION,
                RECORD_TYPE,
                edgeId,
                callerMethodId,
                calleeMethodId,
                callSite,
                metadata);
    }
}
