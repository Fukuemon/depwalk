package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.BeforeEach;
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
 * java-analyzer feature doc「solver 層の bytecode member 合成」の cross-module
 * 救済: 依存 project の classes output にしか存在しない生成 member への
 * cross-module 呼び出しが、bytecode-only member の出力契約で edge になる。
 * BytecodeOnlyMemberTest と同じく generator 非依存 (完全 source を compile した
 * classes + member を削った解析対象 source) で、Lombok 等の生成 member を模擬する。
 */
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

    @SuppressWarnings("unchecked")
    @Test
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

        AnalysisTestSupport.Ran ran = MultiContextAnalysisTestSupport.run(workspace, projects(), null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        Map<String, Object> edge = edgeTo(ran, "java:com.example.lib.LibModel#getName()");
        Map<String, Object> metadata = (Map<String, Object>) edge.get("metadata");
        assertEquals("project-bytecode-member", metadata.get("calleeOrigin"));
    }

    @SuppressWarnings("unchecked")
    @Test
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

        AnalysisTestSupport.Ran ran = MultiContextAnalysisTestSupport.run(workspace, projects(), null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        Map<String, Object> edge = edgeTo(ran, "java:com.example.lib.LibModel#<init>(java.lang.String)");
        Map<String, Object> metadata = (Map<String, Object>) edge.get("metadata");
        assertEquals("project-bytecode-member", metadata.get("calleeOrigin"));
    }

    /** :app が :lib に依存する 2 context 構成 (E2E の multi-module fixture の最小形)。 */
    private List<MultiContextAnalysisTestSupport.Project> projects() {
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
                List.of(libClasses),
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
        List<String> args = new ArrayList<>(List.of("--release", "17", "-d", classesDir.toString()));
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
