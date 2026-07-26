package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * diagnostic の code / severity の契約。parse 不能 file は java-analyzer feature doc
 * 「Parse・resolution・call 完全性」により request 全体の fatal
 * ({@code JAVA_PARSE_ERROR})。未解決 symbol は解析を継続する。
 */
class DiagnosticsTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/diagnostics");

    @Test
    void parseErrorFailsWholeRequestBeforeAnyGraphRecord() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(1, ran.exitCode(), "a parse failure must fail the whole request");

        List<Map<String, Object>> errors = ran.byType("error");
        assertTrue(errors.stream().anyMatch(e ->
                        "JAVA_PARSE_ERROR".equals(e.get("code"))
                                && String.valueOf(e.get("message")).contains("com/example/Broken.java")),
                "expected a JAVA_PARSE_ERROR fatal error with the workspace-relative path: " + errors);

        // graph record は 1 件も出力しない (partial mode なし)。
        assertEquals(0, ran.byType("methodSymbol").size());
        assertEquals(0, ran.byType("callEdge").size());
        assertEquals(0, ran.byType("diagnostic").size());
    }

    @Test
    void unresolvedSymbolIsReportedAsWarningAndAnalysisContinues() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null,
                List.of("com/example/Broken.java"), null, null);

        List<Map<String, Object>> diagnostics = ran.byType("diagnostic");
        assertTrue(diagnostics.stream().anyMatch(d ->
                        "JAVA_UNRESOLVED_SYMBOL".equals(d.get("code")) && "warning".equals(d.get("severity"))),
                "expected JAVA_UNRESOLVED_SYMBOL/warning diagnostic: " + diagnostics);

        // No callEdge should be produced for the unresolved call.
        List<Map<String, Object>> edges = ran.byType("callEdge");
        assertTrue(edges.stream().noneMatch(e -> "java:com.example.Ok#run()".equals(e.get("callerMethodId"))),
                "unresolved call must not produce a callEdge: " + edges);

        // Ok#run() itself is still enumerated as a declared method (declaration-site enumeration is unaffected).
        List<Map<String, Object>> nodes = ran.byType("methodSymbol");
        assertTrue(nodes.stream().anyMatch(n -> "java:com.example.Ok#run()".equals(n.get("methodId"))));
    }

    @Test
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
