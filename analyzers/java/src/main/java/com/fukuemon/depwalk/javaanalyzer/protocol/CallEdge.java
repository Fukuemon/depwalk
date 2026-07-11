package com.fukuemon.depwalk.javaanalyzer.protocol;

import java.util.Map;

/**
 * 解決済み caller / callee の呼び出し関係。
 * AST 解析・型解決による生成は P2_01 の責務であり、本 record は出力 schema のみを定義する。
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

    public static final String RECORD_TYPE = "callEdge";

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
