package com.fukuemon.depwalk.javaanalyzer.protocol;

import java.util.Map;

/**
 * 継続可能な問題や部分解析情報。
 * diagnostic code の生成 (JAVA_UNRESOLVED_SYMBOL 等) は P2_01 の責務であり、本 record は出力 schema
 * のみを定義する。
 *
 * @param schemaVersion   Protocol version
 * @param recordType      {@code diagnostic}
 * @param severity        {@code info} / {@code warning} / {@code partialFailure}
 * @param code            {@code JAVA_} prefix の diagnostic code
 * @param message         人間向けメッセージ
 * @param sourceLocation  発生箇所 (任意)
 * @param relatedMethodId 関連する methodId (任意)
 * @param metadata        追加情報 (任意)
 */
public record Diagnostic(
        String schemaVersion,
        String recordType,
        String severity,
        String code,
        String message,
        SourceLocation sourceLocation,
        String relatedMethodId,
        Map<String, Object> metadata) implements ProtocolRecord {

    public static final String RECORD_TYPE = "diagnostic";

    public static Diagnostic of(
            String severity,
            String code,
            String message,
            SourceLocation sourceLocation,
            String relatedMethodId,
            Map<String, Object> metadata) {
        return new Diagnostic(
                ProtocolSchema.VERSION,
                RECORD_TYPE,
                severity,
                code,
                message,
                sourceLocation,
                relatedMethodId,
                metadata);
    }
}
