package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * spec #27 ④⑤: method reference / explicit constructor invocation の resolve
 * 失敗時に、method call と同等の bytecode 救済と external-target 分類を
 * 試みてから diagnostic 化する。BytecodeOnlyMemberTest と同じく generator
 * 非依存 (完全 source の classes + member を削った解析対象 source) で検証する。
 */
class ReferenceSuperRescueTest {

    @TempDir
    Path temp;

    @SuppressWarnings("unchecked")
    @Test
    void rescuesMethodReferenceToBytecodeOnlyMember() throws Exception {
        Path classes = compileFixture("owner-src", "owner-classes", "com/example/Owner.java", """
                package com.example;
                public class Owner {
                    public String getName() { return "x"; }
                }
                """);
        Path workspace = temp.resolve("mre-workspace");
        write(workspace, "com/example/Owner.java", """
                package com.example;
                public class Owner {
                }
                """);
        write(workspace, "com/example/Caller.java", """
                package com.example;
                import java.util.function.Function;
                public class Caller {
                    Function<Owner, String> use() { return Owner::getName; }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(classes.toString()), null, null, null, null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        Map<String, Object> edge = ran.byType("callEdge").stream()
                .filter(e -> "java:com.example.Owner#getName()".equals(e.get("calleeMethodId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("method reference was not rescued: " + ran.byType("callEdge")));
        Map<String, Object> metadata = (Map<String, Object>) edge.get("metadata");
        assertEquals("project-bytecode-member", metadata.get("calleeOrigin"));
        assertEquals(Boolean.TRUE, metadata.get("viaMethodReference"));
    }

    @Test
    void classifiesMethodReferenceToOutOfScopeOwnerAsExternal() throws Exception {
        // 参照先型が scope 外 (source 宣言索引に無い classes-only 型) で member も
        // 見つからない場合、diagnostic ではなく external-target 除外へ分類する。
        Path classes = compileFixture("ext-src", "ext-classes", "com/example/ext/Ext.java", """
                package com.example.ext;
                public class Ext {
                    public static void go() {}
                }
                """);
        Path workspace = temp.resolve("ext-workspace");
        write(workspace, "com/example/Caller.java", """
                package com.example;
                import com.example.ext.Ext;
                public class Caller {
                    Runnable use() { return Ext::missing; }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(classes.toString()), null, null, null, null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        assertTrue(ran.byType("diagnostic").isEmpty(), () -> "expected external exclusion, got: " + ran.byType("diagnostic"));
        assertTrue(ran.stderr().contains("excluded[external-target]="),
                () -> "ledger summary must count the external exclusion: " + ran.stderr());
    }

    @SuppressWarnings("unchecked")
    @Test
    void rescuesExplicitSuperToBytecodeOnlyConstructor() throws Exception {
        Path classes = compileFixture("base-src", "base-classes", "com/example/Base.java", """
                package com.example;
                public class Base {
                    public Base(String id) {}
                }
                """);
        Path workspace = temp.resolve("super-workspace");
        write(workspace, "com/example/Base.java", """
                package com.example;
                public class Base {
                }
                """);
        write(workspace, "com/example/Child.java", """
                package com.example;
                public class Child extends Base {
                    Child(String id) { super(id); }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(classes.toString()), null, null, null, null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        Map<String, Object> edge = ran.byType("callEdge").stream()
                .filter(e -> "java:com.example.Base#<init>(java.lang.String)".equals(e.get("calleeMethodId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("explicit super was not rescued: " + ran.byType("callEdge")));
        Map<String, Object> metadata = (Map<String, Object>) edge.get("metadata");
        assertEquals("project-bytecode-member", metadata.get("calleeOrigin"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void rescuesExplicitThisToBytecodeOnlyConstructor() throws Exception {
        Path classes = compileFixture("self-src", "self-classes", "com/example/Self.java", """
                package com.example;
                public class Self {
                    public Self() {}
                    public Self(String id) {}
                }
                """);
        Path workspace = temp.resolve("this-workspace");
        write(workspace, "com/example/Self.java", """
                package com.example;
                public class Self {
                    public Self() { this("x"); }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(classes.toString()), null, null, null, null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        List<Map<String, Object>> edges = ran.byType("callEdge");
        assertTrue(edges.stream().anyMatch(e ->
                        "java:com.example.Self#<init>(java.lang.String)".equals(e.get("calleeMethodId"))),
                () -> "explicit this was not rescued: " + edges);
    }

    private Path compileFixture(String srcDir, String classesDir, String relative, String source) throws Exception {
        Path build = temp.resolve(srcDir);
        write(build, relative, source);
        Path classes = temp.resolve(classesDir);
        Files.createDirectories(classes);
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17",
                "-d", classes.toString(), build.resolve(relative).toString());
        assertEquals(0, rc, "fixture compile failed");
        return classes;
    }

    private void write(Path root, String relative, String source) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }
}
