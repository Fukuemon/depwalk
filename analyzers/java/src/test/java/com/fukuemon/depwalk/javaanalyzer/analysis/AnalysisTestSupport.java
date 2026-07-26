package com.fukuemon.depwalk.javaanalyzer.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fukuemon.depwalk.javaanalyzer.Main;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
}
