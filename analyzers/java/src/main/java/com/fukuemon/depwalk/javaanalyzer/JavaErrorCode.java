package com.fukuemon.depwalk.javaanalyzer;

/**
 * Java Analyzer 固有の {@code error} code (fatal / 非ゼロ exit)。
 * 正本: design/features/java-analyzer/DesignDoc_java-analyzer.md 「diagnostic / error code 体系」。
 * {@code JAVA_UNRESOLVED_SYMBOL} / {@code JAVA_PARSE_ERROR} / {@code JAVA_ENTRYPOINT_NOT_FOUND} は
 * 解析を継続できる diagnostic であるため、fatal error を表す本 enum には含めない。
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

    /**
     * Analyzer Protocol に出力する fatal error code を返す。
     *
     * @return enum 定数名と同じ安定した code
     */
    public String code() {
        return name();
    }
}
