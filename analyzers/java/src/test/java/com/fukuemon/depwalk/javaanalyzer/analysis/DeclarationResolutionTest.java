package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.fukuemon.depwalk.javaanalyzer.analysis.AnalysisTestSupport.metadataOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("宣言列挙側の解決失敗の扱い")
class DeclarationResolutionTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/declrecovery");

    @Test
    @DisplayName("解決できないメソッド / constructor 宣言があるとき、field の初期化子の caller としても失敗した constructor を含め、宣言ごとに 1 件の details を載せて解析を fatal にする")
    void unresolvableMethodAndConstructorDeclarationsFailTheRequest() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(1, ran.exitCode(), ran.stderr());
        List<Map<String, Object>> errors = ran.byType("error");
        assertEquals(1, errors.size());
        assertEquals("JAVA_INCOMPLETE_ANALYSIS", errors.get(0).get("code"));

        List<Map<String, Object>> details = AnalysisTestSupport.field(errors.get(0), "details");
        List<Map<String, Object>> declarationDetails = details.stream()
                .filter(d -> "unresolved-declaration".equals(metadataOf(d).get("reason")))
                .toList();
        assertEquals(List.of("broken", "BadDecl"), declarationDetails.stream()
                        .map(d -> metadataOf(d).get("target"))
                        .toList(),
                () -> "each unresolvable declaration must be reported once, in source order: " + details);
        for (Map<String, Object> detail : declarationDetails) {
            assertEquals("JAVA_UNRESOLVED_SYMBOL", detail.get("code"));
            assertEquals("com/example/BadDecl.java",
                    ((Map<?, ?>) detail.get("sourceLocation")).get("path"));
            Object exceptionClass = metadataOf(detail).get("exceptionClass");
            assertTrue(((String) exceptionClass).matches("[\\w.$]+"),
                    () -> "exceptionClass must be a bare class name: " + exceptionClass);
        }

        Map<String, Object> metadata = AnalysisTestSupport.field(errors.get(0), "metadata");
        Map<String, Object> reasonCounts = AnalysisTestSupport.field(metadata, "reasonCounts");
        // field の初期化子の呼び出しは、caller の constructor を resolve できないため unresolved-caller で残る。
        assertEquals(Map.of(
                        "JAVA_UNRESOLVED_SYMBOL:unresolved-caller", 1L,
                        "JAVA_UNRESOLVED_SYMBOL:unresolved-declaration", 2L),
                reasonCounts.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey, e -> ((Number) e.getValue()).longValue())));
        assertEquals(3, ((Number) metadata.get("total")).intValue());
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
        // constructor の resolve の失敗は、field の初期化子の走査と constructor 自身の走査の 2 回起きる。
        assertEquals(2, diagnostics.stream()
                        .filter(d -> "failed to resolve constructor declaration: BadDecl".equals(d.get("message")))
                        .count(),
                () -> "the constructor failure must be detected on both paths: " + diagnostics);
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
