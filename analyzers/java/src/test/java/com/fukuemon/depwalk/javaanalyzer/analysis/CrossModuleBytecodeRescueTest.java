package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * fixture は、完全な source を compile した classes と、member を削った解析対象の source の組で、
 * Lombok のような generator が生成した member を模す。
 */
@DisplayName("module を跨いだ bytecode-only member の救済")
class CrossModuleBytecodeRescueTest {

    @TempDir
    Path temp;

    private Path workspace;
    private Path libSrc;
    private Path libClasses;
    private Path appSrc;
    private Path appClasses;

    @BeforeEach
    void layoutWorkspace() throws Exception {
        workspace = Files.createDirectories(temp.resolve("workspace"));
        libSrc = Files.createDirectories(workspace.resolve("lib/src"));
        libClasses = Files.createDirectories(workspace.resolve("lib/classes"));
        appSrc = Files.createDirectories(workspace.resolve("app/src"));
        appClasses = Files.createDirectories(workspace.resolve("app/classes"));
    }

    @Test
    @DisplayName("依存 module の bytecode にしか存在しない getter への module 跨ぎ呼び出しが、救済されて呼び出し関係 (edge) になる")
    void rescuesCrossModuleCallToBytecodeOnlyGetter() throws Exception {
        compile(libClasses, List.of(), "lib-full", "com/example/lib/LibModel.java", """
                package com.example.lib;
                public class LibModel {
                    private String name;
                    public String getName() { return name; }
                }
                """);
        write(libSrc, "com/example/lib/LibModel.java", """
                package com.example.lib;
                public class LibModel {
                    private String name;
                }
                """);
        write(appSrc, "com/example/app/AppService.java", """
                package com.example.app;
                import com.example.lib.LibModel;
                public class AppService {
                    public String use(LibModel model) { return model.getName(); }
                }
                """);
        compile(appClasses, List.of(libClasses), "app-src-copy", "com/example/app/AppService.java",
                Files.readString(appSrc.resolve("com/example/app/AppService.java")));

        AnalysisTestSupport.Ran ran = MultiContextAnalysisTestSupport.run(workspace, projects(libClasses), null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        Map<String, Object> edge = edgeTo(ran, "java:com.example.lib.LibModel#getName()");
        Map<String, Object> metadata = AnalysisTestSupport.field(edge, "metadata");
        assertEquals("project-bytecode-member", metadata.get("calleeOrigin"));
    }

    @Test
    @DisplayName("依存 module の bytecode にしか存在しない constructor への module 跨ぎ呼び出しも、同じ出力契約で救済される")
    void rescuesCrossModuleCallToBytecodeOnlyConstructor() throws Exception {
        compile(libClasses, List.of(), "lib-full", "com/example/lib/LibModel.java", """
                package com.example.lib;
                public class LibModel {
                    private final String name;
                    public LibModel(String name) { this.name = name; }
                }
                """);
        write(libSrc, "com/example/lib/LibModel.java", """
                package com.example.lib;
                public class LibModel {
                }
                """);
        write(appSrc, "com/example/app/AppService.java", """
                package com.example.app;
                import com.example.lib.LibModel;
                public class AppService {
                    public LibModel create() { return new LibModel("x"); }
                }
                """);
        compile(appClasses, List.of(libClasses), "app-src-copy", "com/example/app/AppService.java",
                Files.readString(appSrc.resolve("com/example/app/AppService.java")));

        AnalysisTestSupport.Ran ran = MultiContextAnalysisTestSupport.run(workspace, projects(libClasses), null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        Map<String, Object> edge = edgeTo(ran, "java:com.example.lib.LibModel#<init>(java.lang.String)");
        Map<String, Object> metadata = AnalysisTestSupport.field(edge, "metadata");
        assertEquals("project-bytecode-member", metadata.get("calleeOrigin"));
    }

    @Test
    @DisplayName("依存 module が jar として classpath に載る場合でも、model の project 依存関係を根拠にして救済される")
    void rescuesCrossModuleCallWhenDependencyAppearsAsJarOnClasspath() throws Exception {
        // Gradle の model は、依存 project を jar として classpath へ返すことがある。
        // そのとき classpath を照合しても :lib の output と分からず、model の project の依存関係だけが
        // 救済の根拠になる。classes directory を直に載せる場合では、この経路の退行を検出できない。
        compile(libClasses, List.of(), "lib-full", "com/example/lib/LibModel.java", """
                package com.example.lib;
                public class LibModel {
                    private String name;
                    public String getName() { return name; }
                }
                """);
        Path libJar = temp.resolve("lib.jar");
        try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(libJar))) {
            out.putNextEntry(new java.util.jar.JarEntry("com/example/lib/LibModel.class"));
            out.write(Files.readAllBytes(libClasses.resolve("com/example/lib/LibModel.class")));
            out.closeEntry();
        }
        write(libSrc, "com/example/lib/LibModel.java", """
                package com.example.lib;
                public class LibModel {
                    private String name;
                }
                """);
        write(appSrc, "com/example/app/AppService.java", """
                package com.example.app;
                import com.example.lib.LibModel;
                public class AppService {
                    public String use(LibModel model) { return model.getName(); }
                }
                """);
        compile(appClasses, List.of(libJar), "app-src-copy", "com/example/app/AppService.java",
                Files.readString(appSrc.resolve("com/example/app/AppService.java")));

        AnalysisTestSupport.Ran ran = MultiContextAnalysisTestSupport.run(workspace, projects(libJar), null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        Map<String, Object> edge = edgeTo(ran, "java:com.example.lib.LibModel#getName()");
        Map<String, Object> metadata = AnalysisTestSupport.field(edge, "metadata");
        assertEquals("project-bytecode-member", metadata.get("calleeOrigin"));
    }

    /**
     * :app が :lib に依存する 2 つの context の構成を返す。
     *
     * @param appClasspathEntry :app の compile classpath に載せる :lib の成果物。
     *     classes directory を渡すと classpath 照合でも project output と分かる形、
     *     jar を渡すと model の依存関係からしか :lib の output を解決できない形になる
     */
    private List<MultiContextAnalysisTestSupport.Project> projects(Path appClasspathEntry) {
        MultiContextAnalysisTestSupport.Project lib = new MultiContextAnalysisTestSupport.Project(
                ":lib",
                workspace.resolve("lib"),
                List.of(libSrc),
                List.of(),
                List.of(libClasses),
                List.of());
        MultiContextAnalysisTestSupport.Project app = new MultiContextAnalysisTestSupport.Project(
                ":app",
                workspace.resolve("app"),
                List.of(appSrc),
                List.of(appClasspathEntry),
                List.of(appClasses),
                List.of(":lib"));
        return List.of(lib, app);
    }

    private Map<String, Object> edgeTo(AnalysisTestSupport.Ran ran, String calleeMethodId) {
        return ran.byType("callEdge").stream()
                .filter(e -> calleeMethodId.equals(e.get("calleeMethodId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "cross-module call was not rescued to " + calleeMethodId + ": " + ran.byType("callEdge")));
    }

    private void compile(Path classesDir, List<Path> classpath, String srcDirName, String relative, String source)
            throws Exception {
        Path build = temp.resolve(srcDirName);
        write(build, relative, source);
        List<String> args = new ArrayList<>(
                List.of("--release", AnalysisTestSupport.FIXTURE_RELEASE, "-d", classesDir.toString()));
        if (!classpath.isEmpty()) {
            args.add("-cp");
            args.add(String.join(java.io.File.pathSeparator, classpath.stream().map(Path::toString).toList()));
        }
        args.add(build.resolve(relative).toString());
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new));
        assertEquals(0, rc, "fixture compile failed");
    }

    private void write(Path root, String relative, String source) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }
}
