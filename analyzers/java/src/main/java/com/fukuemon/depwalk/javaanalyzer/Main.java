package com.fukuemon.depwalk.javaanalyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fukuemon.depwalk.javaanalyzer.analysis.AnalysisRunner;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.IncompleteAnalysisException;
import com.fukuemon.depwalk.javaanalyzer.analysis.context.AnalysisContextFactory;
import com.fukuemon.depwalk.javaanalyzer.discovery.DiscoveryFailure;
import com.fukuemon.depwalk.javaanalyzer.discovery.GradleModelDiscovery;
import com.fukuemon.depwalk.javaanalyzer.discovery.GradleToolingClient;
import com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkGradleModel;
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

    /**
     * process の標準入出力を使って Analyzer を実行し、終了コードを process へ反映する。
     *
     * @param args command-line 引数。現在は使用しない
     */
    public static void main(String[] args) {
        int exitCode = run(System.in, System.out, System.err);
        System.exit(exitCode);
    }

    /**
     * テスト容易性のため exit code を返す形にし、{@link System#exit(int)} を呼ばない実行本体。
     *
     * @param in analysis request JSON を1件受け取る入力
     * @param out protocol JSONL record の出力先
     * @param err metrics と process-level error の出力先
     * @return 成功なら {@code 0}、fatal error なら非ゼロ
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
            AnalysisContextFactory.Result contexts;
            try {
                validated = PreflightValidator.validate(request);
                contexts = buildContexts(request, validated, errStream);
            } catch (AnalyzerFatalException e) {
                writer.write(ErrorRecord.of(e.errorCode().code(), e.getMessage()));
                return 1;
            } catch (DiscoveryFailure e) {
                writer.write(ErrorRecord.of(
                        JavaErrorCode.JAVA_GRADLE_MODEL_ERROR.code(),
                        e.userMessage(),
                        null,
                        java.util.Map.of("reason", e.category().reason(), "phase", e.phase().label())));
                return 1;
            }

            try {
                AnalysisRunner.RunStats stats = AnalysisRunner.run(request, contexts, writer);
                errStream.println(stats.callSiteSummary());
                Duration elapsed = Duration.between(start, Instant.now());
                MetricsSummary summary = new MetricsSummary(
                        stats.analyzedFileCount(),
                        elapsed.toMillis(),
                        stats.parsePreflightMillis(),
                        stats.contextBuildMillis(),
                        stats.unresolvedCount());
                MetricsReporter.report(errStream, summary);
                return 0;
            } catch (IncompleteAnalysisException e) {
                writer.write(new ErrorRecord(
                        com.fukuemon.depwalk.javaanalyzer.protocol.ProtocolSchema.VERSION,
                        ErrorRecord.RECORD_TYPE,
                        JavaErrorCode.JAVA_INCOMPLETE_ANALYSIS.code(),
                        e.getMessage(),
                        null,
                        e.metadata(),
                        e.details()));
                return 1;
            } catch (AnalyzerFatalException e) {
                writer.write(ErrorRecord.of(e.errorCode().code(), e.getMessage()));
                return 1;
            } catch (RuntimeException | LinkageError e) {
                // 解析中の未捕捉 RuntimeException と LinkageError (binary 非互換等) を
                // 継続不能な内部エラーとして扱う (spec #24 step 4.2)。他の Error は catch しない。
                return reportInternalError(writer, errStream, e);
            } catch (IOException e) {
                // 解析 setup / 実行中の IOException (壊れた classpath jar を JarTypeSolver が開けない等、
                // pre-flight をすり抜けた読み取り失敗) も RuntimeException と同様に継続不能な内部エラーと
                // して扱い、best-effort で JAVA_INTERNAL_ERROR record を書く。writer.write 自体が失敗する
                // ケース (stdout が壊れている等) は reportInternalError 内で個別に catch し、その場合は
                // stderr のメッセージのみが残る。
                return reportInternalError(writer, errStream, e);
            }
        } catch (IOException e) {
            // record の書き出し自体が失敗した場合 (stdout が壊れている等)。無言で 1 を返さず、
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

    /**
     * 明示 {@code sourceRoots} なら synthetic context、省略なら Gradle build model
     * discovery (P2_02) から解析 context を構築する。明示 root は Tooling API
     * runtime を完全 bypass する。
     */
    private static AnalysisContextFactory.Result buildContexts(
            AnalysisRequest request, PreflightValidator.Validated validated, PrintStream errStream)
            throws AnalyzerFatalException, DiscoveryFailure {
        java.nio.file.Path workspaceRoot;
        try {
            // root 検証・相対化と同じ基準にするため real path を使う。
            workspaceRoot = java.nio.file.Path.of(request.workspaceRoot()).toRealPath();
        } catch (java.io.IOException e) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "failed to resolve the real path of analysisRequest.workspaceRoot");
        }
        if (GradleModelDiscovery.isExplicitOverride(request.sourceRoots())) {
            return AnalysisContextFactory.explicitContext(
                    workspaceRoot, request.sourceRoots(), validated.classpath(), request.metadata());
        }
        AnalysisContextFactory.rejectLanguageMetadataOnDiscovery(request.metadata());
        DepwalkGradleModel model =
                new GradleModelDiscovery(new GradleToolingClient(), errStream).discover(workspaceRoot);
        return AnalysisContextFactory.discoveredContexts(workspaceRoot, model, validated.classpath());
    }

    private static int reportInternalError(RecordWriter writer, PrintStream errStream, Throwable e) {
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
