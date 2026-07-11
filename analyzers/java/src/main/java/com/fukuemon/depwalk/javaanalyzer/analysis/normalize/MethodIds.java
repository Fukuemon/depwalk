package com.fukuemon.depwalk.javaanalyzer.analysis.normalize;

import java.util.List;

/**
 * D5: {@code signature} / {@code methodId} の生成規則。
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

    public static String signature(String declaringTypeBinaryName, String methodNameToken, List<String> paramBinaryNames) {
        return declaringTypeBinaryName + "#" + methodNameToken + "(" + String.join(",", paramBinaryNames) + ")";
    }

    public static String methodId(String signature) {
        return METHOD_ID_PREFIX + signature;
    }
}
