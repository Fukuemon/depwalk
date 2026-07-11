package com.fukuemon.depwalk.javaanalyzer.preflight;

import com.fukuemon.depwalk.javaanalyzer.JavaErrorCode;

/**
 * pre-flight 検査で検出された、解析を継続できない致命的な問題。
 * 呼び出し側は {@code error} record を出力し、非ゼロ exit code で終了する。
 */
public final class AnalyzerFatalException extends Exception {

    private final JavaErrorCode errorCode;

    public AnalyzerFatalException(JavaErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public JavaErrorCode errorCode() {
        return errorCode;
    }
}
