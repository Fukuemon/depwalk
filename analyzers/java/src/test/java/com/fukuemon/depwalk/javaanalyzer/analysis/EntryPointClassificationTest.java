package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Framework entry point classification: annotated methods carry
 * {@code methodSymbol.metadata.entryPoint} (sorted FQNs), no edges are added,
 * and composed annotations are detected one meta level deep only.
 */
class EntryPointClassificationTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/entrypoint");

    @Test
    void annotatedMethodsCarrySortedEntryPointMetadata() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
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
    void composedAnnotationsDetectOneMetaLevelOnly() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertEquals(0, ran.exitCode(), ran.stderr());

        // One meta level: @Audited carries @PostMapping, so the marker names PostMapping.
        assertEquals(
                List.of("org.springframework.web.bind.annotation.PostMapping"),
                entryPointOf(ran, "java:com.example.Uses#composedEntry()"));
        // Two meta levels are undetectable by design: no marker, no diagnostic.
        assertNull(entryPointOf(ran, "java:com.example.Uses#twoLevels()"));
    }

    @Test
    void listenerKeepsMarkerEvenWithCallerEdges() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertEquals(0, ran.exitCode(), ran.stderr());

        // The marker means "the framework may invoke this directly" and is independent
        // of caller edges: a directly-called listener carries both.
        assertEquals(
                List.of("org.springframework.context.event.EventListener"),
                entryPointOf(ran, "java:com.example.Listener#onEvent()"));
        assertTrue(ran.byType("callEdge").stream().anyMatch(edge ->
                        "java:com.example.Listener#invokeDirectly()".equals(edge.get("callerMethodId"))
                                && "java:com.example.Listener#onEvent()".equals(edge.get("calleeMethodId"))),
                "direct call to the listener must stay an edge");
    }

    @Test
    void classificationAddsNoEdgesAndKeepsPlainMethodsUnmarked() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertEquals(0, ran.exitCode(), ran.stderr());

        assertNull(entryPointOf(ran, "java:com.example.Api#plain()"));
        assertNull(entryPointOf(ran, "java:com.example.Jobs#helper()"));

        // Only real source calls become edges. The fixture has exactly three call
        // expressions (Jobs.init -> helper, Jobs.nightly -> helper,
        // Listener.invokeDirectly -> onEvent); annotations add none.
        List<Map<String, Object>> edges = ran.byType("callEdge");
        assertEquals(3, edges.size(), "entry point classification must not add edges: " + edges);
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
