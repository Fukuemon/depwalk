package com.fukuemon.depwalk.javaanalyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fukuemon.depwalk.javaanalyzer.analysis.AnalysisRunner;
import com.fukuemon.depwalk.javaanalyzer.io.MetricsReporter;
import com.fukuemon.depwalk.javaanalyzer.io.MetricsSummary;
import com.fukuemon.depwalk.javaanalyzer.io.ProtocolObjectMapper;
import com.fukuemon.depwalk.javaanalyzer.io.RecordWriter;
import com.fukuemon.depwalk.javaanalyzer.io.RequestReader;
import com.fukuemon.depwalk.javaanalyzer.preflight.AnalyzerFatalException;
import com.fukuemon.depwalk.javaanalyzer.preflight.PreflightValidator;
import com.fukuemon.depwalk.javaanalyzer.protocol.AnalysisRequest;
import com.fukuemon.depwalk.javaanalyzer.protocol.ErrorRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Java Analyzer process の entry point。
 * process contract (analyzer-protocol 正本): stdin から {@code analysisRequest} を 1 件受け取り、
 * stdout へ JSONL record を逐次出力し、stderr へ計測ログを出す。exit code 0 = 成功、非ゼロ = fatal。
 *
 * <p>AST 解析 / 型解決 / {@code methodSymbol} ・ {@code callEdge} の生成は {@link AnalysisRunner} が担う。
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = run(System.in, System.out, System.err);
        System.exit(exitCode);
    }

    /**
     * テスト容易性のため exit code を返す形にし、{@link System#exit(int)} を呼ばない実行本体。
     */
    public static int run(InputStream in, OutputStream out, OutputStream err) {
        Instant start = Instant.now();
        ObjectMapper mapper = ProtocolObjectMapper.create();
        PrintStream errStream = new PrintStream(err, true, StandardCharsets.UTF_8);
        RecordWriter writer = new RecordWriter(out, mapper);

        try {
            AnalysisRequest request;
            try {
                request = new RequestReader(mapper).read(in);
            } catch (IOException e) {
                writer.write(ErrorRecord.of(
                        JavaErrorCode.JAVA_INVALID_REQUEST.code(),
                        "failed to read analysisRequest: " + e.getMessage()));
                return 1;
            }

            PreflightValidator.Validated validated;
            try {
                validated = PreflightValidator.validate(request);
            } catch (AnalyzerFatalException e) {
                writer.write(ErrorRecord.of(e.errorCode().code(), e.getMessage()));
                return 1;
            }

            try {
                AnalysisRunner.RunStats stats = AnalysisRunner.run(request, validated.classpath(), writer);
                Duration elapsed = Duration.between(start, Instant.now());
                MetricsSummary summary = new MetricsSummary(stats.analyzedFileCount(), elapsed.toMillis(), stats.unresolvedCount());
                MetricsReporter.report(errStream, summary);
                return 0;
            } catch (RuntimeException e) {
                // H1: 解析中の未捕捉 RuntimeException (SymbolSolver 例外 / UncheckedIOException 等) を
                // 継続不能な内部エラーとして扱う。Error は意図的に catch しない。
                return reportInternalError(writer, errStream, e);
            }
        } catch (IOException e) {
            // M2: record の書き出し自体が失敗した場合 (stdout が壊れている等)。無言で 1 を返さず、
            // stderr へ理由を残す。
            errStream.println("failed to write analyzer output: " + e.getClass().getName() + ": " + e.getMessage());
            return 1;
        } finally {
            try {
                writer.close();
            } catch (IOException ignored) {
                // best-effort close; exit code is already decided above.
            }
        }
    }

    private static int reportInternalError(RecordWriter writer, PrintStream errStream, RuntimeException e) {
        String detail = e.getClass().getName() + ": " + e.getMessage();
        errStream.println("internal error during analysis: " + detail);
        try {
            writer.write(ErrorRecord.of(JavaErrorCode.JAVA_INTERNAL_ERROR.code(), "internal error during analysis: " + detail));
        } catch (IOException ignored) {
            // stdout may already be unusable; stderr already has the exception detail.
        }
        return 1;
    }
}
