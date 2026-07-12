package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D6: symbolKind の割り当て (インスタンス初期化子 / フィールド初期化子の constructor への畳み込み、
 * lambda 内呼び出しの囲みメソッド帰属 + viaLambda: true)。
 */
class SymbolKindTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/symbolkind");

    @Test
    void instanceInitializerAndFieldInitializerFoldIntoEveryConstructor() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        List<Map<String, Object>> edges = ran.byType("callEdge");

        String noArgCtor = "java:com.example.Widget#<init>()";
        String stringCtor = "java:com.example.Widget#<init>(java.lang.String)";
        String computeA = "java:com.example.Widget#computeA()";
        String computeB = "java:com.example.Widget#computeB()";

        for (String ctor : List.of(noArgCtor, stringCtor)) {
            assertTrue(edges.stream().anyMatch(e -> ctor.equals(e.get("callerMethodId")) && computeA.equals(e.get("calleeMethodId"))),
                    "field initializer call should fold into " + ctor + ": " + edges);
            assertTrue(edges.stream().anyMatch(e -> ctor.equals(e.get("callerMethodId")) && computeB.equals(e.get("calleeMethodId"))),
                    "instance initializer call should fold into " + ctor + ": " + edges);
        }

        // インスタンス初期化ブロック / フィールド初期化子は独立 node 化しない
        List<Map<String, Object>> nodes = ran.byType("methodSymbol");
        assertFalse(nodes.stream().anyMatch(n -> "initializer".equals(n.get("symbolKind"))
                        && ((String) n.get("qualifiedName")).contains("<clinit>") == false
                        && !"java:com.example.Widget#<clinit>()".equals(n.get("methodId"))),
                "no standalone node for instance initializer blocks: " + nodes);
    }

    @Test
    void lambdaBodyCallsAttributeToEnclosingMethodWithViaLambdaFlag() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        List<Map<String, Object>> edges = ran.byType("callEdge");

        String runWithLambda = "java:com.example.Widget#runWithLambda(java.util.List)";
        String helper = "java:com.example.Widget#helper()";

        Map<String, Object> edge = edges.stream()
                .filter(e -> runWithLambda.equals(e.get("callerMethodId")) && helper.equals(e.get("calleeMethodId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected runWithLambda -> helper edge: " + edges));

        Map<?, ?> metadata = (Map<?, ?>) edge.get("metadata");
        assertTrue(metadata != null && Boolean.TRUE.equals(metadata.get("viaLambda")),
                "call inside lambda body should carry viaLambda: true: " + edge);

        // lambda 本体は独立 node 化しない
        List<Map<String, Object>> nodes = ran.byType("methodSymbol");
        assertFalse(nodes.stream().anyMatch(n -> ((String) n.get("qualifiedName")).contains("lambda")),
                "lambda body must not become its own node: " + nodes);
    }
}
