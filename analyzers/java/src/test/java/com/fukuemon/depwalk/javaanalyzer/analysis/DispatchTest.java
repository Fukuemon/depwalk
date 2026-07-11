package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * D7: {@code callEdge.metadata.dispatch} の値 (static / virtual / interface / abstract)。
 */
class DispatchTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/dispatch");

    private String dispatchOf(List<Map<String, Object>> edges, String caller) {
        return edges.stream()
                .filter(e -> caller.equals(e.get("callerMethodId")))
                .map(e -> (Map<?, ?>) e.get("metadata"))
                .filter(m -> m != null)
                .map(m -> (String) m.get("dispatch"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no dispatch metadata found for caller " + caller + " in " + edges));
    }

    @Test
    void staticMethodCallIsTaggedStatic() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertEquals("static", dispatchOf(ran.byType("callEdge"), "java:com.example.Shapes#callStatic()"));
    }

    @Test
    void interfaceMethodCallIsTaggedInterface() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertEquals("interface", dispatchOf(ran.byType("callEdge"), "java:com.example.Shapes#callInterface(com.example.Shapes$Shape)"));
    }

    @Test
    void abstractClassMethodCallIsTaggedAbstract() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertEquals("abstract", dispatchOf(ran.byType("callEdge"), "java:com.example.Shapes#callAbstract(com.example.Shapes$AbstractAnimal)"));
    }

    @Test
    void concreteInstanceMethodCallIsTaggedVirtual() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertEquals("virtual", dispatchOf(ran.byType("callEdge"), "java:com.example.Shapes#callVirtual(com.example.Shapes$Circle)"));
    }
}
