package com.fukuemon.depwalk.javaanalyzer;

/**
 * Java Analyzer 固有の {@code error} code (fatal / 非ゼロ exit)。
 * 正本: design/features/java-analyzer/DesignDoc_java-analyzer.md 「diagnostic / error code 体系」。
 * {@code JAVA_UNRESOLVED_SYMBOL} / {@code JAVA_PARSE_ERROR} / {@code JAVA_ENTRYPOINT_NOT_FOUND} は
 * diagnostic (継続可能) であり P2_01 の責務のため、本 enum には含めない。
 */
public enum JavaErrorCode {

    /** analysisRequest.metadata に classpath の key が無い。 */
    JAVA_MISSING_CLASSPATH,

    /** classpath に指定された jar / classes dir が存在しない・読めない。 */
    JAVA_MISSING_JAR,

    /** analysisRequest が Java Analyzer として処理できない (未対応 language 等)。 */
    JAVA_INVALID_REQUEST,

    /** 上記以外の継続不能な内部エラー。 */
    JAVA_INTERNAL_ERROR;

    public String code() {
        return name();
    }
}
