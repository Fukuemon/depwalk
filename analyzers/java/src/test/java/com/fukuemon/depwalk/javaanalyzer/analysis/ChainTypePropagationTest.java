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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * stream chain の generic 推論が JavaParser 側で壊れる形状 (`Collectors.toMap` の
 * 結果 Map への bound method reference 適用と、その下流の lambda parameter 経由の
 * 生成 getter 呼び出し) が、型伝播救済層の generic 前進導出 (ADR-0012 の手段②③)
 * で edge になることを検証する。導出の根拠は AST の宣言型・classes output の
 * Signature・JDK コレクション API の宣言済み generic 意味論に限る。
 */
class ChainTypePropagationTest {

    private static final String RELEASE = "17";

    @TempDir
    Path temp;

    @Test
    void lambdaParamGetterInsideBrokenInferenceChainBecomesEdge() throws Exception {
        Path workspace = Files.createDirectories(temp.resolve("workspace"));
        // walk する source の Item は getter を持たない (bytecode のみ = Lombok 相当)。
        write(workspace, "com/example/Item.java", """
                package com.example;
                public class Item {
                    private String ulid;
                    private String code;
                }
                """);
        String useCase = """
                package com.example;
                import java.util.List;
                import java.util.Objects;
                import java.util.stream.Collectors;
                public class UseCase {
                    public List<String> run(List<Item> itemList, List<String> idList) {
                        final var idToItemMap = itemList.stream()
                            .collect(Collectors.toMap(Item::getUlid, item -> item));
                        final var codeList = idList.stream()
                            .map(idToItemMap::get)
                            .filter(Objects::nonNull)
                            .map(item -> item.getCode())
                            .toList();
                        return codeList;
                    }
                }
                """;
        write(workspace, "com/example/UseCase.java", useCase);

        Path classes = Files.createDirectories(temp.resolve("classes"));
        compile(classes, Map.of(
                "com/example/Item.java", """
                        package com.example;
                        public class Item {
                            private String ulid;
                            private String code;
                            public String getUlid() { return ulid; }
                            public String getCode() { return code; }
                        }
                        """,
                "com/example/UseCase.java", useCase));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classpath", List.of(classes.toString()));
        metadata.put("javaLanguageLevel", List.of(RELEASE));
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(workspace, metadata, null, null, null, null);

        assertEquals(0, ran.exitCode(), () -> "diagnostics: " + ran.byType("diagnostic")
                + "\nerrors: " + ran.byType("error") + "\nstderr: " + ran.stderr());
        assertTrue(ran.byType("diagnostic").isEmpty(),
                () -> "chain shapes must not leave diagnostics: " + ran.byType("diagnostic"));
        for (String callee : List.of(
                "java:com.example.Item#getUlid()", "java:com.example.Item#getCode()")) {
            assertTrue(ran.byType("callEdge").stream().anyMatch(edge ->
                            callee.equals(edge.get("calleeMethodId"))
                                    && "java:com.example.UseCase#run(java.util.List,java.util.List)"
                                            .equals(edge.get("callerMethodId"))),
                    () -> "missing rescued edge to " + callee + ": " + ran.byType("callEdge"));
        }
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
