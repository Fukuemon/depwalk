package com.fukuemon.depwalk.javaanalyzer.protocol;

import java.util.Map;

/**
 * 継続可能な問題や部分解析情報。
 *
 * <p>解析処理が生成した warning や部分失敗を Core へ渡す Analyzer Protocol record であり、
 * 本型は出力 schema のみを定義する。個々の diagnostic code と severity は検出側が決定する。
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

    /** 本 record が JSONL の {@code recordType} に設定する値。 */
    public static final String RECORD_TYPE = "diagnostic";

    /**
     * 現在の schema version と record type を設定した diagnostic を生成する。
     *
     * @param severity 問題の重大度
     * @param code 安定した diagnostic code
     * @param message 人間向けの説明
     * @param sourceLocation 発生位置。特定できなければ {@code null}
     * @param relatedMethodId 関連 method ID。該当しなければ {@code null}
     * @param metadata 候補型や条件種別などの追加情報
     * @return protocol 出力可能な diagnostic
     */
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
