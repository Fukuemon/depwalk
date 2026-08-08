package com.fukuemon.depwalk.javaanalyzer.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fukuemon.depwalk.javaanalyzer.JavaErrorCode;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.IncompleteAnalysisException;
import com.fukuemon.depwalk.javaanalyzer.analysis.context.AnalysisContextFactory;
import com.fukuemon.depwalk.javaanalyzer.analysis.pipeline.AnalysisRunner;
import com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkGradleModel;
import com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkProjectModel;
import com.fukuemon.depwalk.javaanalyzer.io.RecordWriter;
import com.fukuemon.depwalk.javaanalyzer.preflight.PreflightValidator;
import com.fukuemon.depwalk.javaanalyzer.protocol.AnalysisRequest;
import com.fukuemon.depwalk.javaanalyzer.protocol.ErrorRecord;
import com.fukuemon.depwalk.javaanalyzer.protocol.ProtocolSchema;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 複数 context (依存 project 関係) を要する unit test 用の実行ヘルパー。
 * 実 Gradle / Tooling API を起動せず、in-memory の fake build model から
 * production の {@link AnalysisContextFactory#discoveredContexts} で context を
 * 構築し、{@link AnalysisRunner#run} を駆動する。model 取得 (Tooling API /
 * daemon / provider 配布) だけが検証範囲から外れ、その部分は required E2E
 * ({@code core/e2e}) が実 Gradle で担う。
 */
final class MultiContextAnalysisTestSupport {

    private MultiContextAnalysisTestSupport() {
    }

    /**
     * fake model 上の 1 project。path は絶対・実在を要求する
     * ({@code discoveredContexts} の root 検証をそのまま通すため)。
     *
     * @param projectPath Gradle project path (例: {@code :lib})
     * @param projectDirectory workspace 配下の project directory
     * @param sourceDirectories main source root (実在する directory)
     * @param compileClasspath 解決済み compile classpath (依存 project の classes output を含める)
     * @param classesOutputDirectories 自 project の classes output
     * @param projectDependencyPaths compile classpath 上の project 依存 (例: {@code :lib})
     */
    record Project(
            String projectPath,
            Path projectDirectory,
            List<Path> sourceDirectories,
            List<Path> compileClasspath,
            List<Path> classesOutputDirectories,
            List<String> projectDependencyPaths) implements DepwalkProjectModel {

        @Override
        public String getProjectPath() {
            return projectPath;
        }

        @Override
        public File getProjectDirectory() {
            return projectDirectory.toFile();
        }

        @Override
        public List<File> getMainJavaSourceDirectories() {
            return toFiles(sourceDirectories);
        }

        @Override
        public List<File> getMainCompileClasspath() {
            return toFiles(compileClasspath);
        }

        @Override
        public List<File> getMainClassesOutputDirectories() {
            return toFiles(classesOutputDirectories);
        }

        @Override
        public List<String> getProjectDependencyPaths() {
            return projectDependencyPaths;
        }

        @Override
        public String getSourceLanguageLevel() {
            return "25";
        }

        @Override
        public boolean isPreviewEnabled() {
            return false;
        }

        private static List<File> toFiles(List<Path> paths) {
            return paths.stream().map(Path::toFile).toList();
        }
    }

    private record FakeGradleModel(Path buildRoot, List<Project> projects) implements DepwalkGradleModel {

        @Override
        public File getBuildRootDirectory() {
            return buildRoot.toFile();
        }

        @Override
        public List<? extends DepwalkProjectModel> getProjects() {
            return projects;
        }

        @Override
        public List<String> getExcludedSourceSetNames() {
            return List.of();
        }

        @Override
        public int getExcludedSourceSetCount() {
            return 0;
        }
    }

    /**
     * fake model の discovery 経路で解析を実行する。exit code / record 出力の
     * 契約は {@code Main} の discovery 成功・完全性 gate 失敗経路と同じ形に揃える
     * (成功 0、{@code JAVA_INCOMPLETE_ANALYSIS} は error record + 1)。
     *
     * @param workspaceRoot fake build の root (= workspace)
     * @param projects fake model に載せる project (1 件以上)
     * @param metadata request metadata (null 可。discovery 経路のため
     *     {@code javaLanguageLevel} / {@code javaPreview} は指定不可)
     */
    static AnalysisTestSupport.Ran run(
            Path workspaceRoot, List<Project> projects, Map<String, Object> metadata) throws Exception {
        AnalysisRequest request = new AnalysisRequest(
                "1",
                "analysisRequest",
                "multi-context-test-request",
                workspaceRoot.toAbsolutePath().toString(),
                null,
                "java",
                null,
                null,
                null,
                null,
                metadata == null ? new LinkedHashMap<>() : metadata);

        PreflightValidator.Validated validated = PreflightValidator.validate(request);
        AnalysisContextFactory.rejectLanguageMetadataOnDiscovery(request.metadata());
        DepwalkGradleModel model = new FakeGradleModel(workspaceRoot, projects);
        AnalysisContextFactory.Result contexts = AnalysisContextFactory.discoveredContexts(
                workspaceRoot.toRealPath(), model, validated.classpath());

        ObjectMapper mapper = new ObjectMapper();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        String summary;
        int exitCode;
        try (RecordWriter writer = new RecordWriter(stdout, mapper)) {
            try {
                AnalysisRunner.RunStats stats =
                        AnalysisRunner.run(request, contexts, writer, validated.allowIncompleteAnalysis());
                summary = stats.callSiteSummary();
                exitCode = 0;
            } catch (IncompleteAnalysisException e) {
                writer.write(new ErrorRecord(
                        ProtocolSchema.VERSION,
                        ErrorRecord.RECORD_TYPE,
                        JavaErrorCode.JAVA_INCOMPLETE_ANALYSIS.code(),
                        e.getMessage(),
                        null,
                        e.metadata(),
                        e.details()));
                summary = "";
                exitCode = 1;
            }
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (String line : stdout.toString(StandardCharsets.UTF_8).split("\n")) {
            if (!line.isBlank()) {
                records.add(mapper.readValue(line, Map.class));
            }
        }
        return new AnalysisTestSupport.Ran(exitCode, records, summary);
    }
}
