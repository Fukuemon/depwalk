package com.fukuemon.depwalk.javaanalyzer.protocol;

import java.util.Map;

/**
 * graph node として扱う method / constructor / function。
 * AST 解析・型解決による生成は P2_01 の責務であり、本 record は出力 schema のみを定義する。
 *
 * @param schemaVersion  Protocol version
 * @param recordType     {@code methodSymbol}
 * @param methodId       Analyzer が決定的に生成する stable ID
 * @param language       対象言語 (Phase1 は {@code java})
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

    public static final String RECORD_TYPE = "methodSymbol";

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
