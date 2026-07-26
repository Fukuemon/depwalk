package com.fukuemon.depwalk.javaanalyzer.protocol;

import java.util.Map;

/**
 * graph node として扱う method / constructor / function。
 *
 * <p>本 record は Analyzer Protocol の出力 schema を表す。AST 解析・型解決・bytecode 補完で
 * 得た情報を、言語や解析方式に依存しない graph node として Core へ渡す。
 *
 * @param schemaVersion  Protocol version
 * @param recordType     {@code methodSymbol}
 * @param methodId       Analyzer が決定的に生成する stable ID
 * @param language       対象言語。Java Analyzer では {@code java}
 * @param symbolKind     {@code method} / {@code constructor} / {@code function} / {@code initializer}
 * @param qualifiedName  表示・debug 用の完全修飾名
 * @param signature      overload を区別できる正規化済み signature
 * @param sourceLocation 定義位置 (任意)
 * @param metadata       言語固有情報 (任意)
 */
public record MethodSymbol(
        String schemaVersion,
        String recordType,
        String methodId,
        String language,
        String symbolKind,
        String qualifiedName,
        String signature,
        SourceLocation sourceLocation,
        Map<String, Object> metadata) implements ProtocolRecord {

    /** 本 record が JSONL の {@code recordType} に設定する値。 */
    public static final String RECORD_TYPE = "methodSymbol";

    /**
     * 現在の schema version と record type を設定した method symbol を生成する。
     *
     * @param methodId signature から生成した安定 ID
     * @param language 対象言語
     * @param symbolKind method、constructor、initializer などの種別
     * @param qualifiedName 表示用の完全修飾名
     * @param signature overload を区別する正規化 signature
     * @param sourceLocation 宣言位置。scope 外なら {@code null}
     * @param metadata 継承元などの追加情報
     * @return protocol 出力可能な method symbol
     */
    public static MethodSymbol of(
            String methodId,
            String language,
            String symbolKind,
            String qualifiedName,
            String signature,
            SourceLocation sourceLocation,
            Map<String, Object> metadata) {
        return new MethodSymbol(
                ProtocolSchema.VERSION,
                RECORD_TYPE,
                methodId,
                language,
                symbolKind,
                qualifiedName,
                signature,
                sourceLocation,
                metadata);
    }
}
