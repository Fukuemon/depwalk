package com.fukuemon.depwalk.javaanalyzer.preflight;

import com.fukuemon.depwalk.javaanalyzer.JavaErrorCode;

/**
 * pre-flight 検査で検出された、解析を継続できない致命的な問題。
 * 呼び出し側は {@code error} record を出力し、非ゼロ exit code で終了する。
 */
public final class AnalyzerFatalException extends Exception {

    /** protocol の error record に設定する code。 */
    private final JavaErrorCode errorCode;

    /**
     * fatal error code と説明を持つ例外を生成する。
     *
     * @param errorCode protocol に出力する fatal error code
     * @param message 人間向けのエラー説明
     */
    public AnalyzerFatalException(JavaErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * protocol に出力する fatal error code を返す。
     *
     * @return この例外の error code
     */
    public JavaErrorCode errorCode() {
        return errorCode;
    }
}
