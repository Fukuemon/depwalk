package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 宣言列挙側 ({@code md.resolve()} / {@code cd.resolve()}、{@code constructorCallerIdsFor} 含む) の
 * 解決失敗は、その宣言だけを skip して {@code JAVA_UNRESOLVED_SYMBOL} (warning) を出し、完全性 gate で
 * {@code JAVA_INCOMPLETE_ANALYSIS} にする。{@code allowIncompleteAnalysis} のときは解析全体を継続する
 * (exit 0、他の宣言 / edge は出力される)。
 */
@DisplayName("宣言列挙側の解決失敗の扱い")
class DeclarationResolutionTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/declrecovery");

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("解決できないメソッド / constructor 宣言があるとき、宣言ごとの details を載せて解析を fatal にする")
    void unresolvableMethodAndConstructorDeclarationsFailTheRequest() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(1, ran.exitCode(), ran.stderr());
        List<Map<String, Object>> errors = ran.byType("error");
        assertEquals(1, errors.size());
        assertEquals("JAVA_INCOMPLETE_ANALYSIS", errors.get(0).get("code"));

        List<Map<String, Object>> details = (List<Map<String, Object>>) errors.get(0).get("details");
        List<Map<String, Object>> declarationDetails = details.stream()
                .filter(d -> "unresolved-declaration".equals(((Map<String, Object>) d.get("metadata")).get("reason")))
                .toList();
        assertEquals(List.of("broken", "BadDecl"), declarationDetails.stream()
                        .map(d -> ((Map<String, Object>) d.get("metadata")).get("target"))
                        .toList(),
                () -> "each unresolvable declaration must be reported once, in source order: " + details);
        for (Map<String, Object> detail : declarationDetails) {
            assertEquals("JAVA_UNRESOLVED_SYMBOL", detail.get("code"));
            assertEquals("com/example/BadDecl.java",
                    ((Map<String, Object>) detail.get("sourceLocation")).get("path"));
            Object exceptionClass = ((Map<String, Object>) detail.get("metadata")).get("exceptionClass");
            assertTrue(((String) exceptionClass).matches("[\\w.$]+"),
                    () -> "exceptionClass must be a bare class name: " + exceptionClass);
        }

        Map<String, Object> metadata = (Map<String, Object>) errors.get(0).get("metadata");
        Map<String, Object> reasonCounts = (Map<String, Object>) metadata.get("reasonCounts");
        assertEquals(2, ((Number) reasonCounts.get("JAVA_UNRESOLVED_SYMBOL:unresolved-declaration")).intValue());
        assertEquals(details.size(), ((Number) metadata.get("total")).intValue());
    }

    @Test
    @DisplayName("allowIncompleteAnalysis を有効にしたとき、解決できない宣言だけを飛ばして warning (JAVA_UNRESOLVED_SYMBOL) を出し、解析全体は継続する")
    void allowIncompleteAnalysisSkipsUnresolvableDeclarationsAndContinues() throws Exception {
        Map<String, Object> metadata = new LinkedHashMap<>(AnalysisTestSupport.classpathMetadata());
        metadata.put("allowIncompleteAnalysis", List.of("true"));
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, metadata, null, null, null, null);

        assertEquals(0, ran.exitCode(), ran.stderr());

        List<Map<String, Object>> diagnostics = ran.byType("diagnostic");
        long unresolvedDeclCount = diagnostics.stream()
                .filter(d -> "JAVA_UNRESOLVED_SYMBOL".equals(d.get("code")) && "warning".equals(d.get("severity")))
                .count();
        assertTrue(unresolvedDeclCount >= 2,
                "expected JAVA_UNRESOLVED_SYMBOL/warning diagnostics for the broken method and constructor: " + diagnostics);
        assertTrue(ran.stderr().contains("gap[JAVA_UNRESOLVED_SYMBOL:unresolved-declaration]=2"),
                () -> "callSiteSummary must report the unresolvable declarations: " + ran.stderr());

        List<Map<String, Object>> nodes = ran.byType("methodSymbol");
        assertFalse(nodes.stream().anyMatch(n -> "java:com.example.BadDecl#broken(UnknownParam)".equals(n.get("methodId"))),
                "the method whose declaration could not be resolved must not be enumerated: " + nodes);
        assertTrue(nodes.stream().anyMatch(n -> "java:com.example.BadDecl#safe()".equals(n.get("methodId"))),
                "other declarations in the same file must still be enumerated: " + nodes);

        List<Map<String, Object>> edges = ran.byType("callEdge");
        assertTrue(edges.stream().anyMatch(e ->
                        "java:com.example.Caller#run()".equals(e.get("callerMethodId"))
                                && "java:com.example.Caller#helper()".equals(e.get("calleeMethodId"))),
                "other files' declarations and call edges must still be produced: " + edges);
    }
}
