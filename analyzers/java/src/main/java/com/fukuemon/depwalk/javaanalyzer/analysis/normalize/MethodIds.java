package com.fukuemon.depwalk.javaanalyzer.analysis.normalize;

import java.util.List;

/**
 * {@code signature} / {@code methodId} の決定的な生成規則。
 * {@code signature} = {@code <帰属型の binary name>#<メソッド名>(<引数型の binary name をカンマ区切り>)}、
 * {@code methodId} = {@code java:} prefix + {@code signature} (hash しない)。
 */
public final class MethodIds {

    /** constructor のメソッド名 token (JVM 表記)。 */
    public static final String CONSTRUCTOR_TOKEN = "<init>";

    /** static initializer のメソッド名 token (JVM 表記)。 */
    public static final String STATIC_INITIALIZER_TOKEN = "<clinit>";

    private static final String METHOD_ID_PREFIX = "java:";

    private MethodIds() {
    }

    /**
     * 宣言型、メソッド名、引数型から正規化 signature を生成する。
     *
     * @param declaringTypeBinaryName メソッドの帰属型を表す JVM binary name
     * @param methodNameToken 通常のメソッド名または constructor・initializer token
     * @param paramBinaryNames 宣言順の引数型 JVM binary name
     * @return overload を区別できる決定的な signature
     */
    public static String signature(String declaringTypeBinaryName, String methodNameToken, List<String> paramBinaryNames) {
        return declaringTypeBinaryName + "#" + methodNameToken + "(" + String.join(",", paramBinaryNames) + ")";
    }

    /**
     * 正規化 signature を Java method ID へ変換する。
     *
     * @param signature {@link #signature(String, String, List)} が生成した signature
     * @return {@code java:} prefix を付けた安定 ID
     */
    public static String methodId(String signature) {
        return METHOD_ID_PREFIX + signature;
    }
}
