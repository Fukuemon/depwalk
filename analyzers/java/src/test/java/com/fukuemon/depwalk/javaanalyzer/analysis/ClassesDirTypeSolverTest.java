package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
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
 * metadata 契約は classpath entry として「依存 jar」だけでなく「classes dir (コンパイル済み
 * .class ファイルの directory)」も許容する。{@link javax.tools.JavaCompiler} で小さなクラスを
 * classes dir へコンパイルし、{@link com.fukuemon.depwalk.javaanalyzer.analysis.context.TypeSolverFactory}
 * がそれを解決できることを確認する。
 */
@DisplayName("classpath entry としての classes directory の型解決")
class ClassesDirTypeSolverTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/classesdirtypesolver");

    @Test
    @DisplayName("classes directory 内の宣言が別の classpath entry の依存型を必要とする場合でも、型を解決して継承メソッドを scope 内の派生型へ引き上げられる")
    void declarationInClassesDirectoryCanLoadDependencyFromAnotherClasspathEntry(@TempDir Path tempDir) throws Exception {
        Path dependencyClasses = tempDir.resolve("dependency-classes");
        Path libraryClasses = tempDir.resolve("library-classes");
        Files.createDirectories(dependencyClasses);
        Files.createDirectories(libraryClasses);
        compileBaseLib(dependencyClasses);
        compileExternalLib(libraryClasses, dependencyClasses);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE,
                AnalysisTestSupport.classpathMetadata(
                        libraryClasses.toString(),
                        dependencyClasses.toString()),
                null,
                null,
                null,
                null);

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

    @Test
    @DisplayName("推移的に必要な依存 classes が欠けているとき、request 全体が失敗 (JAVA_INCOMPLETE_ANALYSIS) になる")
    void missingTransitiveDependencyFailsTheWholeRequest(@TempDir Path tempDir) throws Exception {
        Path dependencyClasses = tempDir.resolve("dependency-classes");
        Path libraryClasses = tempDir.resolve("library-classes");
        Files.createDirectories(dependencyClasses);
        Files.createDirectories(libraryClasses);
        compileBaseLib(dependencyClasses);
        compileExternalLib(libraryClasses, dependencyClasses);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE,
                AnalysisTestSupport.classpathMetadata(libraryClasses.toString()),
                null,
                null,
                null,
                null);

        // scope 内 call が未解決のまま残る request は成功にしない。
        assertEquals(1, ran.exitCode(), ran.stderr());
        assertTrue(ran.byType("error").stream()
                .anyMatch(record -> "JAVA_INCOMPLETE_ANALYSIS".equals(record.get("code"))),
                "missing transitive classes must fail the request with JAVA_INCOMPLETE_ANALYSIS");
    }

    private static void compileBaseLib(Path classesDir) throws IOException {
        compile(
                classesDir,
                List.of(),
                "com/example/base/BaseLib.java",
                "package com.example.base;\n\npublic class BaseLib {}\n");
    }

    private static void compileExternalLib(Path classesDir, Path dependencyClasses) throws IOException {
        compile(
                classesDir,
                List.of(dependencyClasses),
                "com/example/lib/ExternalLib.java",
                "package com.example.lib;\n\n"
                        + "import com.example.base.BaseLib;\n\n"
                        + "public class ExternalLib extends BaseLib {\n"
                        + "    public void ping() {}\n"
                        + "}\n");
    }

    private static void compile(
            Path classesDir,
            List<Path> classpath,
            String relativeSourcePath,
            String source) throws IOException {
        Path srcDir = Files.createTempDirectory("classesdirtypesolver-src");
        Path javaFile = srcDir.resolve(relativeSourcePath);
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, source);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classesDir.toFile()));
            if (!classpath.isEmpty()) {
                fileManager.setLocation(StandardLocation.CLASS_PATH, classpath.stream().map(Path::toFile).toList());
            }
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(List.of(javaFile));
            boolean ok = compiler.getTask(null, fileManager, null, null, null, units).call();
            if (!ok) {
                throw new IllegalStateException("failed to compile classes dir fixture: " + relativeSourcePath);
            }
        }
    }
}
