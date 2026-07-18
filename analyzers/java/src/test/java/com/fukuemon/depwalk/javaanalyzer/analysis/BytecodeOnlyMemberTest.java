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
 * spec #24 D18 / D21: scope 内 source type の bytecode-only member を generator
 * 名に依存せず救済し、定義位置を偽装しない。fixture は annotation を使わず、
 * 「完全な source を compile した classes output」と「member を削った解析対象
 * source」の組で generator 非依存の経路を検証する。
 */
class BytecodeOnlyMemberTest {

    @TempDir
    Path temp;

    @SuppressWarnings("unchecked")
    @Test
    void rescuesUniqueBytecodeOnlyMethodWithOwnerMetadata() throws Exception {
        // 1) 完全な source (generator が生成した member に相当する getName あり) を compile。
        Path build = temp.resolve("build-src");
        write(build, "com/example/Owner.java", """
                package com.example;
                public class Owner {
                    public String getName() { return "x"; }
                }
                """);
        Path classes = temp.resolve("classes");
        Files.createDirectories(classes);
        // SootUp 2.0 が読める classfile 範囲に合わせて --release 17 で compile する
        // (Analyzer runtime JDK と対象 project の compile toolchain は別軸)。
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17",
                "-d", classes.toString(), build.resolve("com/example/Owner.java").toString());
        assertEquals(0, rc, "fixture compile failed");

        // 2) 解析対象 workspace の source からは getName を削る (bytecode-only member 化)。
        Path workspace = temp.resolve("workspace");
        write(workspace, "com/example/Owner.java", """
                package com.example;
                public class Owner {
                }
                """);
        write(workspace, "com/example/Caller.java", """
                package com.example;
                public class Caller {
                    String use(Owner owner) { return owner.getName(); }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(classes.toString()), null, null, null, null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        List<Map<String, Object>> nodes = ran.byType("methodSymbol");
        Map<String, Object> rescued = nodes.stream()
                .filter(n -> "java:com.example.Owner#getName()".equals(n.get("methodId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("bytecode-only member was not rescued: " + nodes));

        // 定義位置は偽装せず省略し、owner 位置は opaque metadata へ分離する。
        assertFalse(rescued.containsKey("sourceLocation"), rescued.toString());
        Map<String, Object> metadata = (Map<String, Object>) rescued.get("metadata");
        assertEquals("project-bytecode", metadata.get("declarationOrigin"));
        assertEquals("owner-type", metadata.get("sourceAnchor"));
        Map<String, Object> ownerLocation = (Map<String, Object>) metadata.get("ownerSourceLocation");
        assertEquals("com/example/Owner.java", ownerLocation.get("path"));

        Map<String, Object> edge = ran.byType("callEdge").stream()
                .filter(e -> "java:com.example.Owner#getName()".equals(e.get("calleeMethodId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("bytecode-only edge missing"));
        Map<String, Object> edgeMetadata = (Map<String, Object>) edge.get("metadata");
        assertEquals("project-bytecode-member", edgeMetadata.get("calleeOrigin"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void resolvesChainedCallsThroughSynthesizedMembers() throws Exception {
        // D31: bytecode-only member の戻り値型が solver へ伝播し、連鎖呼び出し
        // (owner.getName().isEmpty()) の外側も解決できる。
        Path build = temp.resolve("chain-src");
        write(build, "com/example/Owner.java", """
                package com.example;
                public class Owner {
                    public String getName() { return "x"; }
                }
                """);
        Path classes = temp.resolve("chain-classes");
        Files.createDirectories(classes);
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17",
                "-d", classes.toString(), build.resolve("com/example/Owner.java").toString());
        assertEquals(0, rc, "fixture compile failed");

        Path workspace = temp.resolve("chain-workspace");
        write(workspace, "com/example/Owner.java", """
                package com.example;
                public class Owner {
                }
                """);
        write(workspace, "com/example/Caller.java", """
                package com.example;
                public class Caller {
                    boolean use(Owner owner) { return owner.getName().isEmpty(); }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(classes.toString()), null, null, null, null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        // 内側の getName は bytecode-only member として emit される。
        assertTrue(ran.byType("methodSymbol").stream()
                .anyMatch(n -> "java:com.example.Owner#getName()".equals(n.get("methodId"))));
        // 外側の isEmpty は String 上の外部 callee → external-target excluded で
        // edge も diagnostic も残らず、全 call site が分類済みで成功する。
        assertTrue(ran.stderr().contains("silentOmission=0"), ran.stderr());
    }

    @Test
    void resolvesInheritedSynthesizedMembersThroughHierarchyWalk() throws Exception {
        // D31: 親 class の bytecode-only member (継承した生成 getter) は
        // getDeclaredMethods への合成経由で階層走査から解決できる。
        Path build = temp.resolve("inherit-src");
        write(build, "com/example/Base.java", """
                package com.example;
                public class Base {
                    public String getName() { return "x"; }
                }
                """);
        write(build, "com/example/Child.java", """
                package com.example;
                public class Child extends Base {
                }
                """);
        Path classes = temp.resolve("inherit-classes");
        Files.createDirectories(classes);
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-d", classes.toString(),
                build.resolve("com/example/Base.java").toString(),
                build.resolve("com/example/Child.java").toString());
        assertEquals(0, rc, "fixture compile failed");

        Path workspace = temp.resolve("inherit-workspace");
        write(workspace, "com/example/Base.java", """
                package com.example;
                public class Base {
                }
                """);
        write(workspace, "com/example/Child.java", """
                package com.example;
                public class Child extends Base {
                }
                """);
        write(workspace, "com/example/Caller.java", """
                package com.example;
                public class Caller {
                    boolean use(Child child) { return child.getName().isEmpty(); }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(classes.toString()), null, null, null, null);

        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        assertTrue(ran.byType("methodSymbol").stream()
                .anyMatch(n -> "java:com.example.Base#getName()".equals(n.get("methodId"))),
                () -> ran.records().toString());
        assertTrue(ran.stderr().contains("silentOmission=0"), ran.stderr());
    }

    @Test
    void genericElementTypeIsErasedToObjectAndStaysUnresolved() throws Exception {
        // D31 の既知の限界 (spec D31 記録): 合成 member の generic 型引数は
        // erasure で Object 埋めされるため、要素型に依存する後続 call は
        // 解決できず完全性 gate に残る。この限界を回帰として固定する。
        Path build = temp.resolve("generic-src");
        write(build, "com/example/Owner.java", """
                package com.example;
                import java.util.List;
                public class Owner {
                    public List<String> getTags() { return List.of(); }
                }
                """);
        Path classes = temp.resolve("generic-classes");
        Files.createDirectories(classes);
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-d", classes.toString(),
                build.resolve("com/example/Owner.java").toString());
        assertEquals(0, rc, "fixture compile failed");

        Path workspace = temp.resolve("generic-workspace");
        write(workspace, "com/example/Owner.java", """
                package com.example;
                public class Owner {
                }
                """);
        write(workspace, "com/example/Caller.java", """
                package com.example;
                public class Caller {
                    int sizeOnly(Owner owner) { return owner.getTags().size(); }
                    int elementDependent(Owner owner) { return owner.getTags().get(0).length(); }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(classes.toString()), null, null, null, null);

        // 要素型に依存しない size() までは解決する。要素型に依存する
        // get(0).length() は receiver が Object (external) となり、edge を
        // 生成せず external-target の明示除外へ分類される (要素型の復元は
        // generic Signature 読み取りの後続課題)。
        assertEquals(0, ran.exitCode(), () -> ran.records().toString() + ran.stderr());
        assertTrue(ran.byType("methodSymbol").stream()
                .anyMatch(n -> "java:com.example.Owner#getTags()".equals(n.get("methodId"))),
                () -> ran.records().toString());
        assertTrue(ran.stderr().contains("excluded[external-target]"), ran.stderr());
        assertTrue(ran.stderr().contains("silentOmission=0"), ran.stderr());
    }

    @Test
    void doesNotRescueAmbiguousOrForeignMembers() throws Exception {
        // owner type が scope 内 source に存在しない場合は救済せず fatal に残す。
        Path workspace = temp.resolve("workspace2");
        write(workspace, "com/example/Caller.java", """
                package com.example;
                public class Caller {
                    Object use() { return MissingOwner.make(); }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(1, ran.exitCode(), ran.stderr());
        assertTrue(ran.byType("error").stream()
                .anyMatch(e -> "JAVA_INCOMPLETE_ANALYSIS".equals(e.get("code"))));
    }

    private void write(Path root, String relative, String source) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }
}
