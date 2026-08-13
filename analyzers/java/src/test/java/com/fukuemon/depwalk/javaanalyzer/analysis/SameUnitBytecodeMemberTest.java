package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Same-compilation-unit references to bytecode-only members (Lombok-style
 * generated getters) must resolve without diagnostics. These references never
 * consult the type solver, so the solver-side augmentation cannot reach them;
 * the AST injector covers them instead, and calls to injected members are
 * emitted under the bytecode-only member output contract
 * (adr/0005-adopt-sootup-and-spring-di-resolution.md). Covered positions:
 * argument inside a builder-style chain, implicit-this call, and a switch
 * selector whose case body must not be poisoned by the selector's resolution.
 */
class SameUnitBytecodeMemberTest {

    private static final String RELEASE = "17";

    @TempDir
    Path temp;

    @SuppressWarnings("unchecked")
    @Test
    void sameUnitCallsToBytecodeOnlyGettersResolveEverywhere() throws Exception {
        Path workspace = Files.createDirectories(temp.resolve("workspace"));
        write(workspace, "com/example/AssignType.java", """
                package com.example;
                public enum AssignType {
                    SPRAYING,
                    OTHER
                }
                """);
        // The walked source has no getters; the compiled classes do.
        write(workspace, "com/example/Outer.java", """
                package com.example;
                public class Outer {
                    public static class Entry {
                        private java.time.LocalDate assignDate;
                        private AssignType assignType;

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
        compile(classes, Map.of(
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

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classpath", List.of(classes.toString()));
        metadata.put("javaLanguageLevel", List.of(RELEASE));
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(workspace, metadata, null, null, null, null);

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
        assertTrue(ran.byType("callEdge").stream().anyMatch(edge ->
                        "java:com.example.Outer$Entry#getAssignType()".equals(edge.get("calleeMethodId"))),
                () -> "switch selector call must be emitted: " + ran.byType("callEdge"));

        // The injected declaration must not masquerade as a source declaration.
        Map<String, Object> getterNode = ran.byType("methodSymbol").stream()
                .filter(node -> getterId.equals(node.get("methodId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("getter node missing: " + ran.byType("methodSymbol")));
        assertNull(getterNode.get("sourceLocation"), getterNode.toString());
        Map<String, Object> nodeMetadata = (Map<String, Object>) getterNode.get("metadata");
        assertEquals("project-bytecode", nodeMetadata.get("declarationOrigin"), nodeMetadata.toString());
    }

    private void compile(Path classesDir, Map<String, String> sources) throws Exception {
        Path build = temp.resolve("compile-src");
        List<String> args = new ArrayList<>(List.of("--release", RELEASE, "-d", classesDir.toString()));
        for (Map.Entry<String, String> source : sources.entrySet()) {
            write(build, source.getKey(), source.getValue());
            args.add(build.resolve(source.getKey()).toString());
        }
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new));
        assertEquals(0, rc, "fixture compile failed");
    }

    private void write(Path root, String relative, String source) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }
}
