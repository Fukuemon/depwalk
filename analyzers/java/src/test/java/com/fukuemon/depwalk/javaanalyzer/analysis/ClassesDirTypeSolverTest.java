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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M6: metadata 契約は classpath entry として「依存 jar」だけでなく「classes dir (コンパイル済み
 * .class ファイルの directory)」も許容する。{@link javax.tools.JavaCompiler} で小さなクラスを
 * classes dir へコンパイルし、{@link com.fukuemon.depwalk.javaanalyzer.analysis.TypeSolverFactory}
 * がそれを解決できることを確認する。
 */
class ClassesDirTypeSolverTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/classesdirtypesolver");

    @Test
    void declarationInClassesDirectoryIsLiftedToScopeInternalSubtype(@TempDir Path classesDir) throws Exception {
        compileExternalLib(classesDir);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(classesDir.toString()), null, null, null, null);

        assertEquals(0, ran.exitCode());
        List<Map<String, Object>> edges = ran.byType("callEdge");
        String calleeId = "java:com.example.UsesExternalLib#ping()";
        assertTrue(edges.stream().anyMatch(e ->
                        "java:com.example.UsesExternalLib#invoke()".equals(e.get("callerMethodId"))
                                && calleeId.equals(e.get("calleeMethodId"))),
                "classes-dir-declared inherited method should be lifted to the scope-internal subtype: " + edges);

        Map<String, Object> node = ran.byType("methodSymbol").stream()
                .filter(n -> calleeId.equals(n.get("methodId")))
                .findFirst()
                .orElseThrow();
        Map<?, ?> metadata = (Map<?, ?>) node.get("metadata");
        assertEquals("com.example.lib.ExternalLib", metadata.get("declaringType"));
        assertEquals(Boolean.TRUE, metadata.get("inherited"));
    }

    private static void compileExternalLib(Path classesDir) throws IOException {
        Path srcDir = Files.createTempDirectory("classesdirtypesolver-src");
        Path pkgDir = srcDir.resolve("com/example/lib");
        Files.createDirectories(pkgDir);
        Path javaFile = pkgDir.resolve("ExternalLib.java");
        Files.writeString(javaFile, "package com.example.lib;\n\npublic class ExternalLib {\n    public void ping() {}\n}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classesDir.toFile()));
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(List.of(javaFile));
            boolean ok = compiler.getTask(null, fileManager, null, null, null, units).call();
            if (!ok) {
                throw new IllegalStateException("failed to compile fixture ExternalLib.java for classes dir test");
            }
        }
    }
}
