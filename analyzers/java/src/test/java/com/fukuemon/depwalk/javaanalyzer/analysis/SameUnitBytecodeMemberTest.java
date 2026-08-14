package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同一 compilation unit 内から bytecode-only member (Lombok 生成 getter 相当) を
 * 参照する形状が診断なしで解決されることを検証する。同一 unit 内参照は TypeSolver
 * を経由しないため solver 側合成では救済できず、AST 注入が担う。注入 member への
 * 呼び出しは bytecode-only member の出力契約
 * (adr/0005-adopt-sootup-and-spring-di-resolution.md) で emit される。
 * 検証する位置: builder 風 chain の引数、暗黙 this 呼び出し、switch selector と
 * その case 本体 (selector の解決失敗が本体を巻き込まないこと)。
 */
@DisplayName("同一 compilation unit 内の bytecode-only member 参照の解決")
class SameUnitBytecodeMemberTest {

    @TempDir
    Path temp;

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("同一 unit 内から bytecode-only getter を呼ぶとき、chain 引数・暗黙 this・switch selector のどこでも呼び出し関係 (edge) になる")
    void sameUnitCallsToBytecodeOnlyGettersResolveEverywhere() throws Exception {
        Path workspace = Files.createDirectories(temp.resolve("workspace"));
        write(workspace, "com/example/AssignType.java", """
                package com.example;
                public enum AssignType {
                    SPRAYING,
                    OTHER
                }
                """);
        // walk する source は getAssignDate を持たない (bytecode のみ)。
        // getAssignType は source にも宣言し、同名同 arity の source 宣言が
        // 注入より優先されること (dedup) をあわせて検証する。
        write(workspace, "com/example/Outer.java", """
                package com.example;
                public class Outer {
                    public static class Entry {
                        private java.time.LocalDate assignDate;
                        private AssignType assignType;

                        public AssignType getAssignType() { return assignType; }

                        public void viaChainArgument(StringBuilder message) {
                            message.append("x").append(this.getAssignDate()).append("y");
                        }

                        public void viaImplicitThis(StringBuilder message) {
                            message.append(getAssignDate());
                        }

                        public void viaSwitchSelector(StringBuilder message) {
                            switch (this.getAssignType()) {
                                case SPRAYING:
                                    message.append("z").append(this.getAssignDate());
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                }
                """);

        Path classes = Files.createDirectories(temp.resolve("classes"));
        compile("getter-compile-src", classes, Map.of(
                "com/example/AssignType.java", """
                        package com.example;
                        public enum AssignType {
                            SPRAYING,
                            OTHER
                        }
                        """,
                "com/example/Outer.java", """
                        package com.example;
                        public class Outer {
                            public static class Entry {
                                private java.time.LocalDate assignDate;
                                private AssignType assignType;

                                public java.time.LocalDate getAssignDate() { return assignDate; }
                                public AssignType getAssignType() { return assignType; }

                                public void viaChainArgument(StringBuilder message) { }
                                public void viaImplicitThis(StringBuilder message) { }
                                public void viaSwitchSelector(StringBuilder message) { }
                            }
                        }
                        """));

        AnalysisTestSupport.Ran ran = run(workspace, classes);

        assertEquals(0, ran.exitCode(), () -> "diagnostics: " + ran.byType("diagnostic")
                + "\nerrors: " + ran.byType("error") + "\nstderr: " + ran.stderr());
        assertTrue(ran.byType("diagnostic").isEmpty(),
                () -> "same-unit bytecode member calls must not leave diagnostics: " + ran.byType("diagnostic"));

        String getterId = "java:com.example.Outer$Entry#getAssignDate()";
        List<Map<String, Object>> getterEdges = ran.byType("callEdge").stream()
                .filter(edge -> getterId.equals(edge.get("calleeMethodId")))
                .toList();
        List<String> expectedCallers = List.of(
                "java:com.example.Outer$Entry#viaChainArgument(java.lang.StringBuilder)",
                "java:com.example.Outer$Entry#viaImplicitThis(java.lang.StringBuilder)",
                "java:com.example.Outer$Entry#viaSwitchSelector(java.lang.StringBuilder)");
        for (String caller : expectedCallers) {
            assertTrue(getterEdges.stream().anyMatch(edge -> caller.equals(edge.get("callerMethodId"))),
                    () -> "missing getter edge from " + caller + ": " + ran.byType("callEdge"));
        }
        for (Map<String, Object> edge : getterEdges) {
            Map<String, Object> edgeMetadata = (Map<String, Object>) edge.get("metadata");
            assertEquals("project-bytecode-member", edgeMetadata.get("calleeOrigin"), edgeMetadata.toString());
        }

        // 注入 member は caller として walk されない。
        assertTrue(ran.byType("callEdge").stream().noneMatch(edge -> getterId.equals(edge.get("callerMethodId"))),
                () -> "injected member must not appear as a caller: " + ran.byType("callEdge"));

        // switch selector の呼び出しは source 宣言側 (dedup 勝ち) の通常 edge になる。
        Map<String, Object> selectorEdge = ran.byType("callEdge").stream()
                .filter(edge -> "java:com.example.Outer$Entry#getAssignType()".equals(edge.get("calleeMethodId"))
                        && "java:com.example.Outer$Entry#viaSwitchSelector(java.lang.StringBuilder)"
                                .equals(edge.get("callerMethodId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "switch selector edge missing: " + ran.byType("callEdge")));
        Map<String, Object> selectorMetadata = (Map<String, Object>) selectorEdge.get("metadata");
        assertNull(selectorMetadata == null ? null : selectorMetadata.get("calleeOrigin"),
                "source-declared getter must win over injection: " + selectorEdge);
        Map<String, Object> selectorNode = nodeOf(ran, "java:com.example.Outer$Entry#getAssignType()");
        assertNotNull(selectorNode.get("sourceLocation"),
                "source-declared getter must keep its source location: " + selectorNode);

        // 注入 member の node は bytecode-only member 契約 (定義位置省略 + owner metadata)。
        Map<String, Object> getterNode = nodeOf(ran, getterId);
        assertNull(getterNode.get("sourceLocation"), getterNode.toString());
        Map<String, Object> nodeMetadata = (Map<String, Object>) getterNode.get("metadata");
        assertEquals("project-bytecode", nodeMetadata.get("declarationOrigin"), nodeMetadata.toString());
        Map<String, Object> ownerLocation = (Map<String, Object>) nodeMetadata.get("ownerSourceLocation");
        assertNotNull(ownerLocation, nodeMetadata.toString());
        assertEquals("com/example/Outer.java", ownerLocation.get("path"), ownerLocation.toString());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("enum の bytecode-only getter と生成 constructor を呼ぶとき、AST 注入で呼び出し関係 (edge) になる")
    void enumGetterAndGeneratedConstructorResolve() throws Exception {
        // enum の @Getter 相当 (bytecode のみの getter) と、@AllArgsConstructor 相当
        // (bytecode のみの constructor) が、AST 注入で edge になることを検証する。
        Path workspace = Files.createDirectories(temp.resolve("enum-workspace"));
        write(workspace, "com/example/SortColumn.java", """
                package com.example;
                public enum SortColumn {
                    NAME,
                    CODE;
                }
                """);
        write(workspace, "com/example/Holder.java", """
                package com.example;
                public class Holder {
                    private String name;
                    private String code;
                }
                """);
        write(workspace, "com/example/Caller.java", """
                package com.example;
                public class Caller {
                    public Holder use(StringBuilder message) {
                        message.append(SortColumn.NAME.getKey());
                        return new Holder("a", "b");
                    }
                }
                """);

        Path classes = Files.createDirectories(temp.resolve("enum-classes"));
        compile("enum-compile-src", classes, Map.of(
                "com/example/SortColumn.java", """
                        package com.example;
                        public enum SortColumn {
                            NAME,
                            CODE;
                            public String getKey() { return name(); }
                        }
                        """,
                "com/example/Holder.java", """
                        package com.example;
                        public class Holder {
                            private String name;
                            private String code;
                            public Holder(String name, String code) {
                                this.name = name;
                                this.code = code;
                            }
                        }
                        """,
                "com/example/Caller.java", """
                        package com.example;
                        public class Caller {
                            public Holder use(StringBuilder message) { return null; }
                        }
                        """));

        AnalysisTestSupport.Ran ran = run(workspace, classes);

        assertEquals(0, ran.exitCode(), () -> "diagnostics: " + ran.byType("diagnostic")
                + "\nerrors: " + ran.byType("error") + "\nstderr: " + ran.stderr());
        Map<String, Object> getterEdge = ran.byType("callEdge").stream()
                .filter(edge -> "java:com.example.SortColumn#getKey()".equals(edge.get("calleeMethodId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("enum getter edge missing: " + ran.byType("callEdge")));
        Map<String, Object> getterMetadata = (Map<String, Object>) getterEdge.get("metadata");
        assertEquals("project-bytecode-member", getterMetadata.get("calleeOrigin"), getterMetadata.toString());
        Map<String, Object> ctorEdge = ran.byType("callEdge").stream()
                .filter(edge -> "java:com.example.Holder#<init>(java.lang.String,java.lang.String)"
                        .equals(edge.get("calleeMethodId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "generated constructor edge missing: " + ran.byType("callEdge")));
        Map<String, Object> ctorMetadata = (Map<String, Object>) ctorEdge.get("metadata");
        assertEquals("project-bytecode-member", ctorMetadata.get("calleeOrigin"), ctorMetadata.toString());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("暗黙 default constructor と内部 class constructor の呼び出しは、bytecode-only member として注入されず通常の呼び出し関係 (edge) のままになる")
    void implicitConstructorsAreNotInjectedAsBytecodeMembers() throws Exception {
        // javac の暗黙 default constructor と非 static 内部 class の constructor
        // (enclosing instance 引数で source と arity がずれる) は注入しない。
        // 呼び出しは source 宣言由来の通常 edge のまま残る。
        Path workspace = Files.createDirectories(temp.resolve("implicit-workspace"));
        String source = """
                package com.example;
                public class Plain {
                    private String name;

                    public class Inner {
                    }

                    public Plain makePlain() {
                        return new Plain();
                    }

                    public Inner makeInner() {
                        return new Inner();
                    }
                }
                """;
        write(workspace, "com/example/Plain.java", source);
        Path classes = Files.createDirectories(temp.resolve("implicit-classes"));
        compile("implicit-compile-src", classes, Map.of("com/example/Plain.java", source));

        AnalysisTestSupport.Ran ran = run(workspace, classes);

        assertEquals(0, ran.exitCode(), () -> "diagnostics: " + ran.byType("diagnostic")
                + "\nerrors: " + ran.byType("error") + "\nstderr: " + ran.stderr());
        assertTrue(ran.byType("diagnostic").isEmpty(),
                () -> "implicit constructors must stay resolvable: " + ran.byType("diagnostic"));
        // 先に constructor edge の存在を固定する。存在検証なしで calleeOrigin 不在
        // だけを見ると、edge が 1 本も出ない退行でも成立してしまう。
        List<Map<String, Object>> ctorEdges = ran.byType("callEdge").stream()
                .filter(edge -> String.valueOf(edge.get("calleeMethodId")).contains("#<init>"))
                .toList();
        for (String callee : List.of(
                "java:com.example.Plain#<init>()", "java:com.example.Plain$Inner#<init>()")) {
            assertTrue(ctorEdges.stream().anyMatch(edge -> callee.equals(edge.get("calleeMethodId"))),
                    () -> "missing constructor edge to " + callee + ": " + ran.byType("callEdge"));
        }
        for (Map<String, Object> edge : ctorEdges) {
            Map<String, Object> metadata = (Map<String, Object>) edge.get("metadata");
            assertTrue(metadata == null || metadata.get("calleeOrigin") == null,
                    "implicit constructor must not be marked bytecode-only: " + edge);
        }
    }

    @Test
    @DisplayName("同名・同 arity が bytecode 上に複数ある member は注入せず、呼び出しは完全性 gate に残る")
    void ambiguousOverloadIsNotInjectedAndStaysOnCompletenessGate() throws Exception {
        // 同名・同 arity が bytecode 上に複数ある member は注入しない (一意性規則)。
        // 呼び出しは未解決のまま完全性 gate に残る (偽 edge を作らない)。
        Path workspace = Files.createDirectories(temp.resolve("overload-workspace"));
        write(workspace, "com/example/Holder.java", """
                package com.example;
                public class Holder {
                    public void use(StringBuilder message) {
                        message.append(pick("x"));
                    }
                }
                """);
        Path classes = Files.createDirectories(temp.resolve("overload-classes"));
        compile("overload-compile-src", classes, Map.of("com/example/Holder.java", """
                package com.example;
                public class Holder {
                    public String pick(String value) { return value; }
                    public String pick(Object value) { return String.valueOf(value); }

                    public void use(StringBuilder message) { }
                }
                """));

        AnalysisTestSupport.Ran ran = run(workspace, classes);

        assertTrue(ran.byType("callEdge").stream()
                        .noneMatch(edge -> String.valueOf(edge.get("calleeMethodId")).contains("#pick(")),
                () -> "ambiguous overload must not become an edge: " + ran.byType("callEdge"));
        assertEquals(1, ran.exitCode(), ran.stderr());
        assertTrue(ran.byType("error").stream()
                        .anyMatch(error -> "JAVA_INCOMPLETE_ANALYSIS".equals(error.get("code"))),
                () -> "unresolved ambiguous member must reach the gate: " + ran.byType("error"));
    }

    private AnalysisTestSupport.Ran run(Path workspace, Path classes) throws Exception {
        return AnalysisTestSupport.run(workspace, AnalysisTestSupport.classesDirMetadata(classes));
    }

    private static Map<String, Object> nodeOf(AnalysisTestSupport.Ran ran, String methodId) {
        return ran.byType("methodSymbol").stream()
                .filter(node -> methodId.equals(node.get("methodId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        methodId + " node missing: " + ran.byType("methodSymbol")));
    }

    /** compile 用 source の置き場は呼び出し側が意図の分かる名前で指定する。 */
    private void compile(String buildDirName, Path classesDir, Map<String, String> sources) throws Exception {
        AnalysisTestSupport.compileFixture(
                temp.resolve(buildDirName), classesDir, AnalysisTestSupport.FIXTURE_RELEASE, List.of(), sources);
    }

    private void write(Path root, String relative, String source) throws Exception {
        AnalysisTestSupport.writeSource(root, relative, source);
    }
}
