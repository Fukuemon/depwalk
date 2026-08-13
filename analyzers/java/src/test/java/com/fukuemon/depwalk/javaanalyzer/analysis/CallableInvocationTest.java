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

        // Two call sites pass different callables to retry; each yields its own edge
        // from the invoking method: lambda -> its enclosing method, reference -> target.
        assertTrue(callableEdge(ran, RETRY, FROM_LAMBDA).isPresent(),
                "retry must link back to the lambda's enclosing method: " + ran.byType("callEdge"));
        assertTrue(callableEdge(ran, RETRY, STATIC_WORK).isPresent(),
                "retry must link to the referenced method: " + ran.byType("callEdge"));
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
    void reassignedLocalIsNotTracked() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertTrue(ran.byType("callEdge").stream().noneMatch(edge ->
                        USE_REASSIGNED.equals(edge.get("callerMethodId"))
                                && Boolean.TRUE.equals(metadataOf(edge).get("viaCallableInvocation"))),
                "reassigned locals must not be tracked (no guessed callee)");
    }

    @Test
    void fieldStoredCallableSurfacesAdvisoryInfoDiagnostic() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertEquals(0, ran.exitCode(), ran.stderr());
        assertTrue(ran.byType("diagnostic").stream().anyMatch(diagnostic ->
                        "JAVA_CALLABLE_UNRESOLVED".equals(diagnostic.get("code"))
                                && "info".equals(diagnostic.get("severity"))),
                "field-stored callable invocation must surface the advisory info: " + ran.byType("diagnostic"));
        assertTrue(ran.stderr().contains("silentOmission=0"), ran.stderr());
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
