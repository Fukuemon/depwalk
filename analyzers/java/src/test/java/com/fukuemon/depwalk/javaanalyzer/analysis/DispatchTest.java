package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
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
 * {@code callEdge.metadata.dispatch} の値 (static / virtual / interface / abstract) を検証する。
 */
@DisplayName("呼び出し種別 (callEdge.metadata.dispatch) の記録")
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
    @DisplayName("static メソッドを呼ぶとき、dispatch は \"static\" として記録される")
    void staticMethodCallIsTaggedStatic() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertEquals("static", dispatchOf(ran.byType("callEdge"), "java:com.example.Shapes#callStatic()"));
    }

    @Test
    @DisplayName("interface で宣言されたメソッドを呼ぶとき、dispatch は \"interface\" として記録される")
    void interfaceMethodCallIsTaggedInterface() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertEquals("interface", dispatchOf(ran.byType("callEdge"), "java:com.example.Shapes#callInterface(com.example.Shapes$Shape)"));
    }

    @Test
    @DisplayName("abstract class の抽象メソッドを呼ぶとき、dispatch は \"abstract\" として記録される")
    void abstractClassMethodCallIsTaggedAbstract() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertEquals("abstract", dispatchOf(ran.byType("callEdge"), "java:com.example.Shapes#callAbstract(com.example.Shapes$AbstractAnimal)"));
    }

    @Test
    @DisplayName("具象 class のインスタンスメソッドを呼ぶとき、dispatch は \"virtual\" として記録される")
    void concreteInstanceMethodCallIsTaggedVirtual() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertEquals("virtual", dispatchOf(ran.byType("callEdge"), "java:com.example.Shapes#callVirtual(com.example.Shapes$Circle)"));
    }

    @Test
    @DisplayName("project の bytecode が無いとき、診断 JAVA_SOOTUP_UNAVAILABLE を出しつつ宣言への呼び出し関係 (edge) は保たれたままになる")
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
    @DisplayName("project の bytecode を classpath に渡したとき、SootUp の索引から実装候補への呼び出し関係 (edge) を追加し、利用不可の診断は出さない")
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
    @DisplayName("解析できない classfile があるとき、診断 JAVA_SOOTUP_UNAVAILABLE を出して JavaParser の解析だけで続行する")
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
