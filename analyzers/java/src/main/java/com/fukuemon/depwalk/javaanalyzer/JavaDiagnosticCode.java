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
    JAVA_SOOTUP_UNAVAILABLE("warning"),

    /** Spring Data / MyBatis が実行時に実装を提供するため意図的に解決しない。 */
    JAVA_RUNTIME_PROVIDED("info"),

    /** Qualifier / Primary 適用後も複数の Bean 候補が残った。 */
    JAVA_AMBIGUOUS_CANDIDATE("warning"),

    /** 条件付き Bean の実行時条件を評価せず候補として保持した。 */
    JAVA_CONDITIONAL_BEAN("info");

    private final String severity;

    JavaDiagnosticCode(String severity) {
        this.severity = severity;
    }

    /**
     * Analyzer Protocol に出力する diagnostic code を返す。
     *
     * @return enum 定数名と同じ安定した code
     */
    public String code() {
        return name();
    }

    /**
     * diagnostic の既定 severity を返す。
     *
     * @return {@code info}、{@code warning}、{@code partialFailure} のいずれか
     */
    public String severity() {
        return severity;
    }
}
