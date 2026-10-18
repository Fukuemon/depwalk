package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("diagnostic の code / severity の契約")
class DiagnosticsTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/diagnostics");

    @Test
    @DisplayName("parse できない file があるとき、graph record を 1 件も出力せずに request 全体が失敗 (JAVA_PARSE_ERROR) になる")
    void parseErrorFailsWholeRequestBeforeAnyGraphRecord() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(1, ran.exitCode(), "a parse failure must fail the whole request");

        List<Map<String, Object>> errors = ran.byType("error");
        assertTrue(errors.stream().anyMatch(e ->
                        "JAVA_PARSE_ERROR".equals(e.get("code"))
                                && String.valueOf(e.get("message")).contains("com/example/Broken.java")),
                "expected a JAVA_PARSE_ERROR fatal error with the workspace-relative path: " + errors);

        assertEquals(0, ran.byType("methodSymbol").size());
        assertEquals(0, ran.byType("callEdge").size());
        assertEquals(0, ran.byType("diagnostic").size());
    }

    @Test
    @DisplayName("未解決の symbol は warning (JAVA_UNRESOLVED_SYMBOL) として報告され、呼び出し関係 (edge) を作らずに解析は継続する")
    void unresolvedSymbolIsReportedAsWarningAndAnalysisContinues() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null,
                List.of("com/example/Broken.java"), null, null);

        List<Map<String, Object>> diagnostics = ran.byType("diagnostic");
        assertTrue(diagnostics.stream().anyMatch(d ->
                        "JAVA_UNRESOLVED_SYMBOL".equals(d.get("code")) && "warning".equals(d.get("severity"))),
                "expected JAVA_UNRESOLVED_SYMBOL/warning diagnostic: " + diagnostics);

        List<Map<String, Object>> edges = ran.byType("callEdge");
        assertTrue(edges.stream().noneMatch(e -> "java:com.example.Ok#run()".equals(e.get("callerMethodId"))),
                "unresolved call must not produce a callEdge: " + edges);

        // 宣言の列挙は、呼び出しの解決の失敗に影響されない。
        List<Map<String, Object>> nodes = ran.byType("methodSymbol");
        assertTrue(nodes.stream().anyMatch(n -> "java:com.example.Ok#run()".equals(n.get("methodId"))));
    }

    @Test
    @DisplayName("指定した入口 (entry point) が見つからないとき、warning (JAVA_ENTRYPOINT_NOT_FOUND) として報告される")
    void entrypointNotFoundIsReportedAsWarning() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null,
                List.of("com/example/Broken.java"),
                List.of(Map.of("qualifiedName", "com.example.Ok.doesNotExist")), "reachableFromEntrypoints");

        List<Map<String, Object>> diagnostics = ran.byType("diagnostic");
        assertTrue(diagnostics.stream().anyMatch(d ->
                        "JAVA_ENTRYPOINT_NOT_FOUND".equals(d.get("code")) && "warning".equals(d.get("severity"))),
                "expected JAVA_ENTRYPOINT_NOT_FOUND/warning diagnostic: " + diagnostics);
    }
}
