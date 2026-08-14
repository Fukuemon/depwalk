package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * framework entry point 分類の検証。対象アノテーション付きメソッドは
 * {@code methodSymbol.metadata.entryPoint} (sort 済み FQN 配列) の標識を持ち、
 * edge は増えない。合成アノテーションは meta 1 段までだけ検出する。
 */
@DisplayName("framework entry point の分類標識")
class EntryPointClassificationTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/entrypoint");

    @Test
    @DisplayName("対象アノテーション付きメソッドは、検出 FQN を sort した entryPoint 標識を持つ")
    void annotatedMethodsCarrySortedEntryPointMetadata() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        assertEquals(0, ran.exitCode(), ran.stderr());

        assertEquals(
                List.of("jakarta.annotation.PostConstruct"),
                entryPointOf(ran, "java:com.example.Jobs#init()"));
        assertEquals(
                List.of("javax.annotation.PreDestroy"),
                entryPointOf(ran, "java:com.example.Jobs#shutdown()"));
        assertEquals(
                List.of("org.springframework.scheduling.annotation.Scheduled"),
                entryPointOf(ran, "java:com.example.Jobs#nightly()"));
        assertEquals(
                List.of("org.springframework.web.bind.annotation.GetMapping"),
                entryPointOf(ran, "java:com.example.Api#list()"));
        assertEquals(
                List.of("org.springframework.web.bind.annotation.ExceptionHandler"),
                entryPointOf(ran, "java:com.example.Api#onError()"));
        assertEquals(
                List.of("org.springframework.web.bind.annotation.ModelAttribute"),
                entryPointOf(ran, "java:com.example.Api#common()"));
    }

    @Test
    @DisplayName("合成アノテーションは meta 1 段までだけ検出し、2 段は標識も診断も出さない")
    void composedAnnotationsDetectOneMetaLevelOnly() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        assertEquals(0, ran.exitCode(), ran.stderr());

        // meta 1 段: @Audited は @PostMapping を持つので、標識は PostMapping を指す。
        assertEquals(
                List.of("org.springframework.web.bind.annotation.PostMapping"),
                entryPointOf(ran, "java:com.example.Uses#composedEntry()"));
        // meta 2 段は設計上検出不能: 標識も診断も出ない。
        assertNull(entryPointOf(ran, "java:com.example.Uses#twoLevels()"));
    }

    @Test
    @DisplayName("caller edge を持つ listener でも、entry point 標識は保たれたままになる")
    void listenerKeepsMarkerEvenWithCallerEdges() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        assertEquals(0, ran.exitCode(), ran.stderr());

        // 標識の意味は「framework が直接起動し得る」であり、caller edge の有無とは
        // 独立している: 直接呼ばれる listener は両方を併せ持つ。
        assertEquals(
                List.of("org.springframework.context.event.EventListener"),
                entryPointOf(ran, "java:com.example.Listener#onEvent()"));
        assertTrue(ran.byType("callEdge").stream().anyMatch(edge ->
                        "java:com.example.Listener#invokeDirectly()".equals(edge.get("callerMethodId"))
                                && "java:com.example.Listener#onEvent()".equals(edge.get("calleeMethodId"))),
                "direct call to the listener must stay an edge");
    }

    @Test
    @DisplayName("分類は edge を追加せず、アノテーションのないメソッドは標識なしのままになる")
    void classificationAddsNoEdgesAndKeepsPlainMethodsUnmarked() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        assertEquals(0, ran.exitCode(), ran.stderr());

        assertNull(entryPointOf(ran, "java:com.example.Api#plain()"));
        assertNull(entryPointOf(ran, "java:com.example.Jobs#helper()"));

        // source 上の実呼び出しだけが edge になる。fixture の呼び出し式と edge の
        // (caller, callee) 集合が完全一致することで、アノテーション由来の edge が
        // 1 本も増えていないことを固定する。
        Set<String> edgePairs = ran.byType("callEdge").stream()
                .map(edge -> edge.get("callerMethodId") + " -> " + edge.get("calleeMethodId"))
                .collect(Collectors.toSet());
        assertEquals(
                Set.of(
                        "java:com.example.Jobs#init() -> java:com.example.Jobs#helper()",
                        "java:com.example.Jobs#nightly() -> java:com.example.Jobs#helper()",
                        "java:com.example.Listener#invokeDirectly() -> java:com.example.Listener#onEvent()"),
                edgePairs,
                "entry point classification must not add edges: " + edgePairs);
    }

    @SuppressWarnings("unchecked")
    private static List<String> entryPointOf(AnalysisTestSupport.Ran ran, String methodId) {
        Map<String, Object> node = ran.byType("methodSymbol").stream()
                .filter(record -> methodId.equals(record.get("methodId")))
                .findFirst()
                .orElse(null);
        assertNotNull(node, "methodSymbol not found: " + methodId);
        Map<String, Object> metadata = (Map<String, Object>) node.get("metadata");
        if (metadata == null) {
            return null;
        }
        Object entryPoint = metadata.get("entryPoint");
        if (entryPoint == null) {
            return null;
        }
        assertTrue(entryPoint instanceof List, "entryPoint must be a list: " + entryPoint);
        return (List<String>) entryPoint;
    }
}
