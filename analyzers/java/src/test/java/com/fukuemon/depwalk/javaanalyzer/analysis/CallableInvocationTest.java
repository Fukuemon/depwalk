package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Callable invocation edges (ADR-0012): invoking a functional interface links back
 * to the passed callable within the static tracking scope (same-method locals and
 * the one-hop argument pass to a workspace method parameter). Field-stored
 * callables surface the advisory {@code JAVA_CALLABLE_UNRESOLVED} (info) instead.
 */
class CallableInvocationTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/callable");

    private static final String RETRY = "java:com.example.Retry#retry(java.util.function.Supplier)";
    private static final String FROM_LAMBDA = "java:com.example.Caller#fromLambda()";
    private static final String STATIC_WORK = "java:com.example.Caller#staticWork()";
    private static final String USE_LOCAL_LAMBDA = "java:com.example.LocalUse#useLocalLambda()";
    private static final String USE_LOCAL_REFERENCE = "java:com.example.LocalUse#useLocalReference()";
    private static final String USE_REASSIGNED = "java:com.example.LocalUse#useReassigned()";
    private static final String STATIC_HELPER = "java:com.example.LocalUse#staticHelper()";

    @Test
    void oneHopParameterPassLinksEveryPassedCallable() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertEquals(0, ran.exitCode(), ran.stderr());
        assertTrue(ran.stderr().contains("silentOmission=0"), ran.stderr());

        // Two call sites pass different callables to retry; the invocation enumerates
        // exactly those callees, each anchored at the invocation site inside retry.
        List<Map<String, Object>> retryEdges = ran.byType("callEdge").stream()
                .filter(edge -> RETRY.equals(edge.get("callerMethodId"))
                        && Boolean.TRUE.equals(metadataOf(edge).get("viaCallableInvocation")))
                .toList();
        assertEquals(
                List.of(FROM_LAMBDA, STATIC_WORK),
                retryEdges.stream().map(edge -> (String) edge.get("calleeMethodId")).sorted().toList(),
                "retry must enumerate exactly the passed callables: " + retryEdges);
        for (Map<String, Object> edge : retryEdges) {
            @SuppressWarnings("unchecked")
            Map<String, Object> callSite = (Map<String, Object>) edge.get("callSite");
            assertEquals("com/example/Retry.java", callSite.get("path"),
                    "the edge anchor is the invocation site inside retry: " + edge);
        }
    }

    @Test
    void sameMethodLocalsLinkLambdaToSelfAndReferenceToTarget() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        // A local lambda's body belongs to the enclosing method itself, so the
        // invocation is a marked self edge (the body ⊂ this node).
        assertTrue(callableEdge(ran, USE_LOCAL_LAMBDA, USE_LOCAL_LAMBDA).isPresent(),
                "local lambda invocation must self-link: " + ran.byType("callEdge"));
        assertTrue(callableEdge(ran, USE_LOCAL_REFERENCE, STATIC_HELPER).isPresent(),
                "local reference invocation must link to the referenced method: " + ran.byType("callEdge"));
    }

    @Test
    void reassignedLocalIsNotTrackedAndSurfacesTheAdvisory() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertTrue(ran.byType("callEdge").stream().noneMatch(edge ->
                        USE_REASSIGNED.equals(edge.get("callerMethodId"))
                                && Boolean.TRUE.equals(metadataOf(edge).get("viaCallableInvocation"))),
                "reassigned locals must not be tracked (no guessed callee)");
        assertTrue(ran.byType("diagnostic").stream().anyMatch(diagnostic ->
                        "JAVA_CALLABLE_UNRESOLVED".equals(diagnostic.get("code"))
                                && "java:com.example.LocalUse#useReassigned()".equals(
                                        diagnostic.get("relatedMethodId"))),
                "untracked SAM invocations are surfaced symmetrically: " + ran.byType("diagnostic"));
    }

    @Test
    void fieldStoredCallableSurfacesAdvisoryInfoDiagnosticWithoutEdges() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertEquals(0, ran.exitCode(), ran.stderr());
        String invokeStored = "java:com.example.FieldUse#invokeStored()";
        assertTrue(ran.byType("callEdge").stream().noneMatch(edge ->
                        invokeStored.equals(edge.get("callerMethodId"))
                                && Boolean.TRUE.equals(metadataOf(edge).get("viaCallableInvocation"))),
                "field-stored callables must not produce guessed edges");
        assertTrue(ran.byType("diagnostic").stream().anyMatch(diagnostic ->
                        "JAVA_CALLABLE_UNRESOLVED".equals(diagnostic.get("code"))
                                && "info".equals(diagnostic.get("severity"))
                                && invokeStored.equals(diagnostic.get("relatedMethodId"))),
                "field-stored callable invocation must surface the advisory info: " + ran.byType("diagnostic"));
        assertTrue(ran.stderr().contains("silentOmission=0"), ran.stderr());
    }

    @Test
    void plainInterfaceCallsAreNeitherTrackedNorDiagnosed() throws Exception {
        // Non-functional interfaces (e.g. injected services) must stay out of both
        // the tracking and the advisory diagnostic (noise regression).
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertTrue(ran.byType("diagnostic").stream().noneMatch(diagnostic ->
                        "JAVA_CALLABLE_UNRESOLVED".equals(diagnostic.get("code"))
                                && "java:com.example.PlainInterfaceUse#call()".equals(
                                        diagnostic.get("relatedMethodId"))),
                "plain interface calls must not surface the callable advisory: " + ran.byType("diagnostic"));
    }

    private static Optional<Map<String, Object>> callableEdge(
            AnalysisTestSupport.Ran ran, String callerMethodId, String calleeMethodId) {
        return ran.byType("callEdge").stream()
                .filter(edge -> callerMethodId.equals(edge.get("callerMethodId"))
                        && calleeMethodId.equals(edge.get("calleeMethodId"))
                        && Boolean.TRUE.equals(metadataOf(edge).get("viaCallableInvocation")))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadataOf(Map<String, Object> edge) {
        Map<String, Object> metadata = (Map<String, Object>) edge.get("metadata");
        return metadata == null ? Map.of() : metadata;
    }
}
