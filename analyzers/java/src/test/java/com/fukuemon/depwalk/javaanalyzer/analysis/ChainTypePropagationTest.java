package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JavaParser の generic の推論が壊れるのは、{@code Collectors.toMap} の結果の Map に bound method
 * reference を当て、その下流の lambda parameter から生成された getter を呼ぶ形である。
 * 導出の根拠は、AST の宣言型、classes の Signature 属性、JDK のコレクションの API が宣言する
 * generic の意味に限る。
 */
@DisplayName("型伝播救済層の generic 前進導出")
class ChainTypePropagationTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("generic 推論が壊れる stream chain の中でも、lambda parameter 経由の生成 getter 呼び出しが呼び出し関係 (edge) になる")
    void lambdaParamGetterInsideBrokenInferenceChainBecomesEdge() throws Exception {
        Path workspace = Files.createDirectories(temp.resolve("workspace"));
        // Lombok と同じく、走査する source の Item は getter を持たず、bytecode にだけ getter がある。
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
        compile("chain-compile-src", classes, Map.of(
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

        AnalysisTestSupport.Ran ran =
                AnalysisTestSupport.run(workspace, AnalysisTestSupport.classesDirMetadata(classes));

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

    @Test
    @DisplayName("downstream collector 付き groupingBy の値型は導出せず、誤った要素型への呼び出し関係 (edge) を作らない")
    void groupingByWithDownstreamCollectorIsNotDerived() throws Exception {
        // downstream collector 付きの groupingBy の値型は downstream で決まる (counting なら Long)。
        // 固定の表で List<E> と導出すると、偽の edge になる。
        Path workspace = Files.createDirectories(temp.resolve("grouping-workspace"));
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
                import java.util.stream.Collectors;
                public class GroupingUseCase {
                    public String run(List<Item> itemList) {
                        final var countMap = itemList.stream()
                            .collect(Collectors.groupingBy(Item::getUlid, Collectors.counting()));
                        return countMap.values().stream()
                            .map(count -> count.getCode())
                            .findFirst()
                            .orElse(null);
                    }
                }
                """;
        write(workspace, "com/example/GroupingUseCase.java", useCase);

        Path classes = Files.createDirectories(temp.resolve("grouping-classes"));
        compile("grouping-compile-src", classes, Map.of("com/example/Item.java", """
                package com.example;
                public class Item {
                    private String ulid;
                    private String code;
                    public String getUlid() { return ulid; }
                    public String getCode() { return code; }
                }
                """));

        Map<String, Object> metadata = AnalysisTestSupport.classesDirMetadata(classes);
        metadata.put("allowIncompleteAnalysis", List.of("true"));
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(workspace, metadata);

        assertEquals(0, ran.exitCode(), ran.stderr());
        // 解析が動かずに通ってしまう誤りを防ぐため、解析と救済が動いたことを先に確かめる。
        assertTrue(ran.byType("methodSymbol").stream().anyMatch(node ->
                        "java:com.example.GroupingUseCase#run(java.util.List)".equals(node.get("methodId"))),
                () -> "GroupingUseCase#run must be analyzed: " + ran.byType("methodSymbol"));
        assertTrue(ran.byType("callEdge").stream().anyMatch(edge ->
                        "java:com.example.Item#getUlid()".equals(edge.get("calleeMethodId"))
                                && "java:com.example.GroupingUseCase#run(java.util.List)"
                                        .equals(edge.get("callerMethodId"))),
                () -> "key extractor must still be rescued: " + ran.byType("callEdge"));
        assertTrue(ran.byType("diagnostic").stream().anyMatch(diagnostic ->
                        "JAVA_UNRESOLVED_SYMBOL".equals(diagnostic.get("code"))
                                && "java:com.example.GroupingUseCase#run(java.util.List)"
                                        .equals(diagnostic.get("relatedMethodId"))),
                () -> "count.getCode() must surface as an unresolved diagnostic: " + ran.byType("diagnostic"));
        assertTrue(ran.byType("callEdge").stream().noneMatch(edge ->
                        String.valueOf(edge.get("calleeMethodId")).startsWith("java:com.example.Item#getCode")),
                () -> "downstream-dependent value type must not be derived: " + ran.byType("callEdge"));
    }

    private void compile(String buildDirName, Path classesDir, Map<String, String> sources) throws Exception {
        AnalysisTestSupport.compileFixture(
                temp.resolve(buildDirName), classesDir, AnalysisTestSupport.FIXTURE_RELEASE, List.of(), sources);
    }

    private void write(Path root, String relative, String source) throws Exception {
        AnalysisTestSupport.writeSource(root, relative, source);
    }
}
