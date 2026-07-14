package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * method reference ({@code this::toDto} / {@code Foo::bar} / {@code Foo::new}) の call graph 化。
 * lambda と同じく囲みメソッドへ帰属させ、{@code callEdge.metadata.viaMethodReference: true} で
 * 標識する。constructor reference は通常の object creation と同じ帰属規則を適用し、scope 外参照を
 * 出力しないことも確認する。
 */
class MethodReferenceTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/methodreference");

    private AnalysisTestSupport.Ran run() throws Exception {
        return AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
    }

    private Map<String, Object> edgeFrom(List<Map<String, Object>> edges, String caller, String callee) {
        return edges.stream()
                .filter(e -> caller.equals(e.get("callerMethodId")) && callee.equals(e.get("calleeMethodId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no edge " + caller + " -> " + callee + " in " + edges));
    }

    @Test
    void instanceMethodReferenceProducesEdgeTaggedViaMethodReference() throws Exception {
        List<Map<String, Object>> edges = run().byType("callEdge");
        Map<String, Object> edge = edgeFrom(edges,
                "java:com.example.Widgets#invokeInstanceReference(com.example.Widgets$Widget)",
                "java:com.example.Widgets$Widget#label()");
        Map<?, ?> metadata = (Map<?, ?>) edge.get("metadata");
        assertEquals(Boolean.TRUE, metadata.get("viaMethodReference"));
        assertEquals("virtual", metadata.get("dispatch"));
    }

    @Test
    void staticMethodReferenceProducesEdgeTaggedStaticAndViaMethodReference() throws Exception {
        List<Map<String, Object>> edges = run().byType("callEdge");
        Map<String, Object> edge = edgeFrom(edges,
                "java:com.example.Widgets#invokeStaticReference()",
                "java:com.example.Widgets$Widget#describe()");
        Map<?, ?> metadata = (Map<?, ?>) edge.get("metadata");
        assertEquals(Boolean.TRUE, metadata.get("viaMethodReference"));
        assertEquals("static", metadata.get("dispatch"));
    }

    @Test
    void thisMethodReferenceProducesEdge() throws Exception {
        List<Map<String, Object>> edges = run().byType("callEdge");
        edgeFrom(edges, "java:com.example.Widgets#invokeThisReference()", "java:com.example.Widgets#toDto()");
    }

    @Test
    void noArgConstructorReferenceProducesInitEdge() throws Exception {
        List<Map<String, Object>> edges = run().byType("callEdge");
        Map<String, Object> edge = edgeFrom(edges,
                "java:com.example.Widgets#invokeConstructorReference()",
                "java:com.example.Widgets$Widget#<init>()");
        Map<?, ?> metadata = (Map<?, ?>) edge.get("metadata");
        assertEquals(Boolean.TRUE, metadata.get("viaMethodReference"));
    }

    @Test
    void oneArgConstructorReferenceSelectsMatchingOverload() throws Exception {
        List<Map<String, Object>> edges = run().byType("callEdge");
        edgeFrom(edges,
                "java:com.example.Widgets#invokeConstructorReferenceWithArg()",
                "java:com.example.Widgets$Widget#<init>(int)");
    }

    @Test
    void scopeExternalMethodReferenceIsOmittedWithoutDiagnostic() throws Exception {
        AnalysisTestSupport.Ran ran = run();
        List<Map<String, Object>> edges = ran.byType("callEdge");
        assertFalse(edges.stream().anyMatch(e ->
                        "java:com.example.Widgets#invokeScopeExternalReference()".equals(e.get("callerMethodId"))),
                "scope-external method reference (java.util.UUID#toString, excluded package) must not be emitted: " + edges);
        assertFalse(ran.byType("diagnostic").stream()
                .anyMatch(diagnostic -> "JAVA_UNRESOLVED_SYMBOL".equals(diagnostic.get("code"))),
                "scope-external omission must not raise an unresolved diagnostic");
    }
}
