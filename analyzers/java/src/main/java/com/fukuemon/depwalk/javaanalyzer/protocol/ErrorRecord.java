package com.fukuemon.depwalk.javaanalyzer.protocol;

import java.util.List;
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
 * @param details        言語共通の構造化 failure detail 配列 (任意)。存在する場合は 1 件以上で、
 *                       配列順は決定順を保持する
 */
public record ErrorRecord(
        String schemaVersion,
        String recordType,
        String code,
        String message,
        SourceLocation sourceLocation,
        Map<String, Object> metadata,
        List<FailureDetail> details) implements ProtocolRecord {

    /** 本 record が JSONL の {@code recordType} に設定する値。 */
    public static final String RECORD_TYPE = "error";

    /**
     * source location と metadata を持たない fatal error を生成する。
     *
     * @param code 安定した fatal error code
     * @param message 人間向けの説明
     * @return protocol 出力可能な error record
     */
    public static ErrorRecord of(String code, String message) {
        return new ErrorRecord(ProtocolSchema.VERSION, RECORD_TYPE, code, message, null, null, null);
    }

    /**
     * 任意の source location と metadata を含む fatal error を生成する。
     *
     * @param code 安定した fatal error code
     * @param message 人間向けの説明
     * @param sourceLocation 発生位置。特定できなければ {@code null}
     * @param metadata 追加情報。なければ {@code null}
     * @return protocol 出力可能な error record
     */
    public static ErrorRecord of(String code, String message, SourceLocation sourceLocation, Map<String, Object> metadata) {
        return new ErrorRecord(ProtocolSchema.VERSION, RECORD_TYPE, code, message, sourceLocation, metadata, null);
    }
}
