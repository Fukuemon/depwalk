package com.fukuemon.depwalk.javaanalyzer.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fukuemon.depwalk.javaanalyzer.Main;

import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * unit test 用の {@link Main#run} 実行ヘルパー。{@code analysisRequest} を組み立てて実行し、
 * stdout の JSONL を {@code Map} のリストとして返す (record 種別の判定は {@code recordType} フィールド)。
 */
final class AnalysisTestSupport {

    private AnalysisTestSupport() {
    }

    record Ran(int exitCode, List<Map<String, Object>> records, String stderr) {
        List<Map<String, Object>> byType(String recordType) {
            return records.stream().filter(r -> recordType.equals(r.get("recordType"))).toList();
        }
    }

    /** compile 済み fixture を生成する javac の {@code --release} 既定値。 */
    static final String FIXTURE_RELEASE = "17";

    /** include / exclude / entrypoints / analysisMode を使わない基本形。 */
    static Ran run(Path workspaceRoot, Map<String, Object> metadata) throws Exception {
        return run(workspaceRoot, metadata, null, null, null, null);
    }

    static Ran run(
            Path workspaceRoot,
            Map<String, Object> metadata,
            List<String> include,
            List<String> exclude,
            List<Map<String, String>> entrypoints,
            String analysisMode) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("schemaVersion", "1");
        request.put("recordType", "analysisRequest");
        request.put("requestId", "test-request");
        request.put("workspaceRoot", workspaceRoot.toAbsolutePath().toString());
        // 確定 schema (java-analyzer feature doc「metadata 契約」) への移行:
        // unit test は明示 root 経路を使う。
        request.put("sourceRoots", List.of("."));
        request.put("language", "java");
        if (include != null) {
            request.put("include", include);
        }
        if (exclude != null) {
            request.put("exclude", exclude);
        }
        if (entrypoints != null) {
            request.put("entrypoints", entrypoints);
        }
        if (analysisMode != null) {
            request.put("analysisMode", analysisMode);
        }
        request.put("metadata", metadata);

        String json = mapper.writeValueAsString(request);
        ByteArrayInputStream stdin = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = Main.run(stdin, stdout, stderr);

        List<Map<String, Object>> records = new ArrayList<>();
        String stdoutText = stdout.toString(StandardCharsets.UTF_8);
        for (String line : stdoutText.split("\n")) {
            if (!line.isBlank()) {
                records.add(mapper.readValue(line, Map.class));
            }
        }
        return new Ran(exitCode, records, stderr.toString(StandardCharsets.UTF_8));
    }

    static Map<String, Object> classpathMetadata(String... jars) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classpath", List.of(jars));
        metadata.put("javaLanguageLevel", List.of("25"));
        return metadata;
    }

    /** compile 済み classes dir を classpath に載せた metadata ({@link #FIXTURE_RELEASE} 前提)。 */
    static Map<String, Object> classesDirMetadata(Path classesDir) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classpath", List.of(classesDir.toString()));
        metadata.put("javaLanguageLevel", List.of(FIXTURE_RELEASE));
        return metadata;
    }

    /**
     * fixture source を javac で compile して {@code classesDir} へ出力する。
     * {@code buildDir} は compile 用 source の置き場で、呼び出し側が意図の分かる
     * 名前を明示する (walk 対象の workspace と混ざらない場所にすること)。
     */
    static void compileFixture(
            Path buildDir, Path classesDir, String release, List<Path> classpath, Map<String, String> sources)
            throws Exception {
        List<String> args = new ArrayList<>(List.of("--release", release, "-d", classesDir.toString()));
        if (!classpath.isEmpty()) {
            args.add("-cp");
            args.add(String.join(
                    java.io.File.pathSeparator, classpath.stream().map(Path::toString).toList()));
        }
        for (Map.Entry<String, String> source : sources.entrySet()) {
            writeSource(buildDir, source.getKey(), source.getValue());
            args.add(buildDir.resolve(source.getKey()).toString());
        }
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new));
        assertEquals(0, rc, "fixture compile failed");
    }

    /** source を {@code root} 配下の相対 path へ書き込む (親 directory は作成する)。 */
    static void writeSource(Path root, String relative, String source) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    /** edge / node record の {@code metadata} (無ければ空 Map)。 */
    @SuppressWarnings("unchecked")
    static Map<String, Object> metadataOf(Map<String, Object> record) {
        Map<String, Object> metadata = (Map<String, Object>) record.get("metadata");
        return metadata == null ? Map.of() : metadata;
    }
}
