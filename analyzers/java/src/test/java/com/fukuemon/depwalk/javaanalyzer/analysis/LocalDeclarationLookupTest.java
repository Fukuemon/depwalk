package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * generic の推論が壊れた式の型は、local 変数の名前から AST 上の宣言を探して導く。
 * 推論を壊すために、走査する source の getter を除き、bytecode にだけ getter を置く。
 */
@DisplayName("generic の推論が壊れた式で、local 変数の宣言を探す")
class LocalDeclarationLookupTest {

    private static final String ITEM_GET_CODE = "java:com.example.Item#getCode()";

    @TempDir
    static Path temp;

    private static AnalysisTestSupport.Ran ran;

    @BeforeAll
    static void analyze() throws Exception {
        Path workspace = Files.createDirectories(temp.resolve("workspace"));
        String item = """
                package com.example;
                public class Item {
                    private String ulid;
                    private String code;
                }
                """;
        String other = """
                package com.example;
                public class Other {
                    private String code;
                }
                """;
        AnalysisTestSupport.writeSource(workspace, "com/example/Item.java", item);
        AnalysisTestSupport.writeSource(workspace, "com/example/Other.java", other);
        AnalysisTestSupport.writeSource(workspace, "com/example/SiblingBlockUse.java", """
                package com.example;
                import java.util.List;
                import java.util.Map;
                import java.util.Objects;
                import java.util.stream.Collectors;
                public class SiblingBlockUse {
                    public List<String> run(List<Item> itemList, List<String> idList) {
                        {
                            final var idToItemMap = Map.of("key", 1);
                            idToItemMap.size();
                        }
                        final var idToItemMap = itemList.stream()
                            .collect(Collectors.toMap(Item::getUlid, item -> item));
                        return idList.stream()
                            .map(idToItemMap::get)
                            .filter(Objects::nonNull)
                            .map(item -> item.getCode())
                            .toList();
                    }
                }
                """);
        AnalysisTestSupport.writeSource(workspace, "com/example/LambdaCaptureUse.java", """
                package com.example;
                import java.util.List;
                import java.util.Objects;
                import java.util.stream.Collectors;
                public class LambdaCaptureUse {
                    public List<String> run(List<Item> itemList, List<String> idList) {
                        final var idToItemMap = itemList.stream()
                            .collect(Collectors.toMap(Item::getUlid, item -> item));
                        return idList.stream()
                            .map(id -> idToItemMap.get(id))
                            .filter(Objects::nonNull)
                            .map(item -> item.getCode())
                            .toList();
                    }
                }
                """);
        AnalysisTestSupport.writeSource(workspace, "com/example/FieldBeforeLocalUse.java", """
                package com.example;
                import java.util.List;
                import java.util.Map;
                import java.util.Objects;
                import java.util.stream.Collectors;
                public class FieldBeforeLocalUse {
                    private Map<String, Other> idToItemMap;
                    public List<String> run(List<Item> itemList, List<String> idList) {
                        List<String> codes = idList.stream()
                            .map(idToItemMap::get)
                            .filter(Objects::nonNull)
                            .map(item -> item.getCode())
                            .toList();
                        final var idToItemMap = itemList.stream()
                            .collect(Collectors.toMap(Item::getUlid, item -> item));
                        idToItemMap.size();
                        return codes;
                    }
                }
                """);
        AnalysisTestSupport.writeSource(workspace, "com/example/InitializerUse.java", """
                package com.example;
                import java.util.List;
                import java.util.Map;
                import java.util.Objects;
                import java.util.stream.Collectors;
                public class InitializerUse {
                    private List<Item> itemList = List.of();
                    private List<String> idList = List.of();
                    private List<String> codes;
                    {
                        final var idToItemMap = itemList.stream()
                            .collect(Collectors.toMap(Item::getUlid, item -> item));
                        codes = idList.stream()
                            .map(idToItemMap::get)
                            .filter(Objects::nonNull)
                            .map(item -> item.getCode())
                            .toList();
                    }
                    public int other() {
                        final var idToItemMap = Map.of("key", 1);
                        return idToItemMap.size();
                    }
                }
                """);

        Path classes = Files.createDirectories(temp.resolve("classes"));
        AnalysisTestSupport.compileFixture(
                temp.resolve("compile-src"), classes, AnalysisTestSupport.FIXTURE_RELEASE, List.of(), Map.of(
                        "com/example/Item.java", """
                                package com.example;
                                public class Item {
                                    private String ulid;
                                    private String code;
                                    public String getUlid() { return ulid; }
                                    public String getCode() { return code; }
                                }
                                """,
                        "com/example/Other.java", """
                                package com.example;
                                public class Other {
                                    private String code;
                                    public String getCode() { return code; }
                                }
                                """));

        Map<String, Object> metadata = AnalysisTestSupport.classesDirMetadata(classes);
        metadata.put("allowIncompleteAnalysis", List.of("true"));
        ran = AnalysisTestSupport.run(workspace, metadata);
        assertEquals(0, ran.exitCode(), ran.stderr());
    }

    @Test
    @DisplayName("兄弟の block に同名の local があると、宣言を採らず、型を導けない呼び出しを diagnostic に残す")
    void siblingBlockWithTheSameNameLeavesDiagnostic() {
        String caller = "java:com.example.SiblingBlockUse#run(java.util.List,java.util.List)";
        assertTrue(edge(caller, ITEM_GET_CODE).isEmpty(), () -> "edges: " + ran.byType("callEdge"));
        assertTrue(hasUnresolved(caller), () -> "diagnostics: " + ran.byType("diagnostic"));
    }

    @Test
    @DisplayName("lambda の中から外側の local を使うと、宣言を採らず、型を導けない呼び出しを diagnostic に残す")
    void outerLocalUsedInsideLambdaLeavesDiagnostic() {
        String caller = "java:com.example.LambdaCaptureUse#run(java.util.List,java.util.List)";
        assertTrue(edge(caller, ITEM_GET_CODE).isEmpty(), () -> "edges: " + ran.byType("callEdge"));
        assertTrue(hasUnresolved(caller), () -> "diagnostics: " + ran.byType("diagnostic"));
    }

    @Test
    @DisplayName("宣言より前で同名の field を使うと、後ろの local の型から呼び出し関係 (edge) を作る")
    void fieldUsedBeforeTheLocalAdoptsTheLaterLocal() {
        String caller = "java:com.example.FieldBeforeLocalUse#run(java.util.List,java.util.List)";
        assertTrue(edge(caller, ITEM_GET_CODE).isPresent(), () -> "edges: " + ran.byType("callEdge"));
    }

    @Test
    @DisplayName("instance initializer の中で使うと、ファイル全体の同名の宣言が数に入り、宣言を採らずに diagnostic に残す")
    void initializerUseCountsDeclarationsInTheWholeFile() {
        String caller = "java:com.example.InitializerUse#<init>()";
        assertTrue(edge(caller, ITEM_GET_CODE).isEmpty(), () -> "edges: " + ran.byType("callEdge"));
        assertTrue(hasUnresolved(caller), () -> "diagnostics: " + ran.byType("diagnostic"));
    }

    private static Optional<Map<String, Object>> edge(String callerMethodId, String calleeMethodId) {
        return ran.byType("callEdge").stream()
                .filter(edge -> callerMethodId.equals(edge.get("callerMethodId"))
                        && calleeMethodId.equals(edge.get("calleeMethodId")))
                .findFirst();
    }

    private static boolean hasUnresolved(String relatedMethodId) {
        return ran.byType("diagnostic").stream().anyMatch(diagnostic ->
                "JAVA_UNRESOLVED_SYMBOL".equals(diagnostic.get("code"))
                        && relatedMethodId.equals(diagnostic.get("relatedMethodId")));
    }
}
