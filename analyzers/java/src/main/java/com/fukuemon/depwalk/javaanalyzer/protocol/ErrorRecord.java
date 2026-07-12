package com.fukuemon.depwalk.javaanalyzer.protocol;

import java.util.Map;

/**
 * 致命的エラー。出力後、Analyzer process は非ゼロ exit code で終了する。
 *
 * @param schemaVersion  Protocol version
 * @param recordType     {@code error}
 * @param code           {@code JAVA_} prefix の error code
 * @param message        人間向けメッセージ
 * @param sourceLocation 発生箇所 (任意)
 * @param metadata       追加情報 (任意)
 */
public record ErrorRecord(
        String schemaVersion,
        String recordType,
        String code,
        String message,
        SourceLocation sourceLocation,
        Map<String, Object> metadata) implements ProtocolRecord {

    public static final String RECORD_TYPE = "error";

    public static ErrorRecord of(String code, String message) {
        return new ErrorRecord(ProtocolSchema.VERSION, RECORD_TYPE, code, message, null, null);
    }

    public static ErrorRecord of(String code, String message, SourceLocation sourceLocation, Map<String, Object> metadata) {
        return new ErrorRecord(ProtocolSchema.VERSION, RECORD_TYPE, code, message, sourceLocation, metadata);
    }
}
