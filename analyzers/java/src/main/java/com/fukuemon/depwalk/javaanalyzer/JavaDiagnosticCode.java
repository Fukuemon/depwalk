package com.fukuemon.depwalk.javaanalyzer;

/**
 * Java Analyzer 固有の {@code diagnostic} code (解析継続)。
 * 正本: design/features/java-analyzer/DesignDoc_java-analyzer.md 「diagnostic / error code 体系」。
 * fatal な {@code error} code は {@link JavaErrorCode} を使う。
 */
public enum JavaDiagnosticCode {

    /** 呼び出し先の型が解決できず {@code callEdge} を張れない。 */
    JAVA_UNRESOLVED_SYMBOL("warning"),

    /** ファイル単位で構文解析に失敗し、そのファイルを飛ばした。 */
    JAVA_PARSE_ERROR("partialFailure"),

    /** {@code entrypoints} の method selector に一致する method が見つからない。 */
    JAVA_ENTRYPOINT_NOT_FOUND("warning"),

    /** SootUp が project bytecode を取得・索引化できず、JavaParser の宣言 edge のみで継続した。 */
    JAVA_SOOTUP_UNAVAILABLE("warning");

    private final String severity;

    JavaDiagnosticCode(String severity) {
        this.severity = severity;
    }

    public String code() {
        return name();
    }

    public String severity() {
        return severity;
    }
}
