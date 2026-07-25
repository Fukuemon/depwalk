package com.fukuemon.depwalk.javaanalyzer.protocol;

/**
 * {@code analysisRequest.entrypoints} の要素。
 *
 * @param qualifiedName 起点 method の完全修飾名 (必須)
 * @param signature     overload を区別する正規化済み signature (任意)
 */
public record MethodSelector(String qualifiedName, String signature) {
}
