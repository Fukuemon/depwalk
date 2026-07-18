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
