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

    /**
     * Gradle build model の自動 discovery に失敗した (対応範囲外 version、
     * provider 非互換、daemon JVM 非互換、model 取得失敗)。安定 reason は
     * {@code DiscoveryFailure.Category} が持つ。
     */
    JAVA_GRADLE_MODEL_ERROR,

    /** source root の構成が不正 (workspace 外、非 directory、包含関係、context 重複所有等)。 */
    JAVA_INVALID_SOURCE_ROOTS,

    /** 有効な source root が 1 件も残らなかった。 */
    JAVA_NO_SOURCE_ROOTS,

    /**
     * 解析 scope 内の source file を設定済み language level で parse できなかった。
     * graph record 出力前の pre-flight で request 全体を fatal にする
     * (継続 diagnostic ではない)。
     */
    JAVA_PARSE_ERROR,

    /**
     * 全 resolver と bytecode 救済の完了後も scope 内 call が edge / 明示除外へ
     * 確定せず primary diagnostic に残った (spec #24 D20)。全未解決 call は
     * {@code error.details} で観測可能にする。
     */
    JAVA_INCOMPLETE_ANALYSIS,

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
