package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * diagnostic の code / severity と、解析が継続すること (パース不能ファイルを飛ばして
 * 他ファイルの解析を続ける / 未解決 symbol があっても他の callEdge は出力する)。
 */
class DiagnosticsTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/diagnostics");

    @Test
    void parseErrorIsReportedAndOtherFilesStillAnalyzed() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(0, ran.exitCode(), "parse error is partialFailure, not fatal");

        List<Map<String, Object>> diagnostics = ran.byType("diagnostic");
        assertTrue(diagnostics.stream().anyMatch(d ->
                        "JAVA_PARSE_ERROR".equals(d.get("code")) && "partialFailure".equals(d.get("severity"))),
                "expected JAVA_PARSE_ERROR/partialFailure diagnostic: " + diagnostics);

        // Broken.java is skipped, but Ok.java (a sibling file) is still analyzed.
        List<Map<String, Object>> nodes = ran.byType("methodSymbol");
        assertTrue(nodes.stream().anyMatch(n -> "java:com.example.Ok#safe()".equals(n.get("methodId"))),
                "sibling file must still be analyzed after a parse error: " + nodes);
    }

    @Test
    void unresolvedSymbolIsReportedAsWarningAndAnalysisContinues() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

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
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null,
                List.of(Map.of("qualifiedName", "com.example.Ok.doesNotExist")), "reachableFromEntrypoints");

        List<Map<String, Object>> diagnostics = ran.byType("diagnostic");
        assertTrue(diagnostics.stream().anyMatch(d ->
                        "JAVA_ENTRYPOINT_NOT_FOUND".equals(d.get("code")) && "warning".equals(d.get("severity"))),
                "expected JAVA_ENTRYPOINT_NOT_FOUND/warning diagnostic: " + diagnostics);
    }
}
