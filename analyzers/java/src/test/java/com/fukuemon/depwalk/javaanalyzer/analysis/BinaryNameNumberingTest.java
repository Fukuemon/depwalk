package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M3 / D5: 匿名クラス / ローカルクラスの binary name 採番を JVM (javac) 互換にする。
 * <ul>
 *   <li>匿名クラス: 直近の enclosing class ごとに 1 始まりのソース出現順
 *       ({@code Outer$Nested} 内の最初の匿名クラスは、{@code Outer} 直下に先行する匿名クラスが
 *       あっても {@code Outer$Nested$1})。</li>
 *   <li>ローカルクラス: {@code Outer$<n><Name>} 形式。n は同名ローカルクラスの enclosing class 内
 *       出現順。</li>
 * </ul>
 * javac が実際に生成する class ファイル名と突合して互換性を確認する。
 */
class BinaryNameNumberingTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/binarynames");

    private AnalysisTestSupport.Ran run() throws Exception {
        return AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
    }

    private static Set<String> methodIds(AnalysisTestSupport.Ran ran) {
        return ran.byType("methodSymbol").stream()
                .map(n -> (String) n.get("methodId"))
                .collect(Collectors.toSet());
    }

    @Test
    void anonymousClassInNestedTypeIsNumberedPerImmediateEnclosingClass() throws Exception {
        Set<String> ids = methodIds(run());
        // Nested 内の匿名クラスは、Outer 直下に先行する匿名クラス (first() 内) があっても $Nested$1。
        assertTrue(ids.contains("java:com.example.Outer$Nested$1#run()"), ids.toString());
    }

    @Test
    void anonymousClassesInOuterAreNumberedIndependentlyOfNestedOnes() throws Exception {
        Set<String> ids = methodIds(run());
        assertTrue(ids.contains("java:com.example.Outer$1#run()"), ids.toString());
        // second() 内の匿名クラスは Outer 直下の 2 個目 (Nested 内のものは数えない) なので $2。
        assertTrue(ids.contains("java:com.example.Outer$2#run()"), ids.toString());
    }

    @Test
    void localClassesUseJvmCompatibleNumberedNameForm() throws Exception {
        Set<String> ids = methodIds(run());
        assertTrue(ids.contains("java:com.example.Outer$1Local#go()"), ids.toString());
        assertTrue(ids.contains("java:com.example.Outer$2Local#go()"), ids.toString());
    }

    @Test
    void callInsideLocalClassIsAttributedToTheLocalClassBinaryName() throws Exception {
        AnalysisTestSupport.Ran ran = run();
        List<Map<String, Object>> edges = ran.byType("callEdge");
        assertTrue(edges.stream().anyMatch(e ->
                        "java:com.example.Outer$1Local#go()".equals(e.get("callerMethodId"))
                                && "java:com.example.Outer#helper()".equals(e.get("calleeMethodId"))),
                "call from inside a local class must be attributed to the local class binary name: " + edges);
    }

    @Test
    void everyEmittedBinaryNameMatchesAClassFileGeneratedByJavac(@TempDir Path classesDir) throws Exception {
        Set<String> javacBinaryNames = compileFixtureWithJavac(classesDir);

        AnalysisTestSupport.Ran ran = run();
        assertEquals(0, ran.exitCode());
        Set<String> emittedBinaryNames = methodIds(ran).stream()
                .map(BinaryNameNumberingTest::declaringBinaryNameOf)
                .collect(Collectors.toSet());

        for (String binaryName : emittedBinaryNames) {
            assertTrue(javacBinaryNames.contains(binaryName),
                    "analyzer-emitted binary name \"" + binaryName + "\" must match a javac-generated class file; "
                            + "javac produced: " + javacBinaryNames);
        }
    }

    /** methodId ({@code java:<binaryName>#<name>(<params>)}) から宣言型 binary name を取り出す。 */
    private static String declaringBinaryNameOf(String methodId) {
        String withoutPrefix = methodId.substring("java:".length());
        return withoutPrefix.substring(0, withoutPrefix.indexOf('#'));
    }

    private static Set<String> compileFixtureWithJavac(Path classesDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classesDir.toFile()));
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(
                    List.of(FIXTURE.resolve("com/example/Outer.java")));
            boolean ok = compiler.getTask(null, fileManager, null, null, null, units).call();
            if (!ok) {
                throw new IllegalStateException("failed to compile fixture Outer.java with javac");
            }
        }
        try (Stream<Path> walk = Files.walk(classesDir)) {
            return walk.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> {
                        String relative = classesDir.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                        return relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
                    })
                    .collect(Collectors.toSet());
        }
    }
}
