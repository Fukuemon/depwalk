package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D7: {@code callEdge.metadata.dispatch} の値 (static / virtual / interface / abstract)。
 */
class DispatchTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/dispatch");

    @TempDir
    Path tempDir;

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

    @Test
    void reportsSootUpUnavailableButKeepsDeclarationEdgeWhenProjectBytecodeIsAbsent() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertTrue(ran.byType("diagnostic").stream()
                .anyMatch(record -> "JAVA_SOOTUP_UNAVAILABLE".equals(record.get("code"))));
        assertEquals("interface", dispatchOf(
                ran.byType("callEdge"),
                "java:com.example.Shapes#callInterface(com.example.Shapes$Shape)"));
    }

    @Test
    void emitsIndexedProjectCandidateEdgesAfterP3Integration() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        int javacExit = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "--release",
                "21",
                "-d",
                classesDir.toString(),
                FIXTURE.resolve("com/example/Shapes.java").toString());
        assertEquals(0, javacExit);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE,
                AnalysisTestSupport.classpathMetadata(classesDir.toString()),
                null,
                null,
                null,
                null);

        assertFalse(ran.byType("diagnostic").stream()
                .anyMatch(record -> "JAVA_SOOTUP_UNAVAILABLE".equals(record.get("code"))));
        assertTrue(ran.byType("callEdge").stream().anyMatch(edge ->
                "java:com.example.Shapes#callInterface(com.example.Shapes$Shape)".equals(edge.get("callerMethodId"))
                        && "java:com.example.Shapes$Circle#area()".equals(edge.get("calleeMethodId"))));
    }

    @Test
    void reportsSootUpUnavailableForUnparseableClassAndContinuesWithJavaParser() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/sootup-broken-source");
        Path classesDir = tempDir.resolve("broken-classes");
        Path brokenClass = classesDir.resolve("com/example/Broken.class");
        Files.createDirectories(brokenClass.getParent());
        Files.writeString(brokenClass, "not bytecode");

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                fixture,
                AnalysisTestSupport.classpathMetadata(classesDir.toString()),
                null,
                null,
                null,
                null);

        assertTrue(ran.byType("diagnostic").stream()
                .anyMatch(record -> "JAVA_SOOTUP_UNAVAILABLE".equals(record.get("code"))));
        assertTrue(ran.byType("callEdge").stream().anyMatch(edge ->
                "java:com.example.Calls#call(com.example.Broken)".equals(edge.get("callerMethodId"))
                        && "java:com.example.Broken#run()".equals(edge.get("calleeMethodId"))));
    }
}
