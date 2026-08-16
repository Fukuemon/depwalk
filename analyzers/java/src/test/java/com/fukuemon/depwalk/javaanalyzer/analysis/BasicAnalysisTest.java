package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ステップ 1: 3 TypeSolver 構成での型解決 + AST 逐次走査の疎通確認。
 */
@DisplayName("型解決と AST 走査の基本的な疎通")
class BasicAnalysisTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/basic");

    @Test
    @DisplayName("scope 内の 2 つの class 間の単純な呼び出しを解決し、宣言メソッド・暗黙の constructor・呼び出し関係 (edge) をすべて出力する")
    void resolvesSimpleCallBetweenTwoScopeInternalClasses() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(0, ran.exitCode());

        List<Map<String, Object>> nodes = ran.byType("methodSymbol");
        List<Map<String, Object>> edges = ran.byType("callEdge");

        assertTrue(nodes.stream().anyMatch(n -> "java:com.example.App#run()".equals(n.get("methodId"))),
                "App#run() should be enumerated as a declared method: " + nodes);
        assertTrue(nodes.stream().anyMatch(n -> "java:com.example.Greeter#greet(java.lang.String)".equals(n.get("methodId"))),
                "Greeter#greet(String) should be enumerated as a declared method: " + nodes);
        assertTrue(nodes.stream().anyMatch(n -> "java:com.example.Greeter#<init>()".equals(n.get("methodId"))),
                "Greeter's implicit constructor should be emitted via the call site: " + nodes);

        assertTrue(edges.stream().anyMatch(e ->
                        "java:com.example.App#run()".equals(e.get("callerMethodId"))
                                && "java:com.example.Greeter#<init>()".equals(e.get("calleeMethodId"))),
                "App#run() -> Greeter#<init>() edge expected: " + edges);
        assertTrue(edges.stream().anyMatch(e ->
                        "java:com.example.App#run()".equals(e.get("callerMethodId"))
                                && "java:com.example.Greeter#greet(java.lang.String)".equals(e.get("calleeMethodId"))),
                "App#run() -> Greeter#greet(String) edge expected: " + edges);
    }
}
