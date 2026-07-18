package com.fukuemon.depwalk.javaanalyzer.protocol;

import java.util.Map;

/**
 * fatal error の言語共通 failure detail。Core は共通 field だけを検証・表示し、
 * {@code code} の値と {@code metadata} の key を意味解釈しない。
 *
 * @param code           必須の安定 detail code
 * @param message        必須の人間向けメッセージ
 * @param sourceLocation 発生箇所 (任意)
 * @param metadata       opaque な追加情報 (任意)。nested JSON 値を構造のまま保持する
 */
public record FailureDetail(
        String code,
        String message,
        SourceLocation sourceLocation,
        Map<String, Object> metadata) {
}
