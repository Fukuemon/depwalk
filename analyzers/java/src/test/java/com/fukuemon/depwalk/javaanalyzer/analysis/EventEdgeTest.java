package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring event publish-to-listener edges (broadcast semantics): every matching
 * listener is a certain callee ({@code resolution: unique}); only conditional
 * listeners are ambiguous. Unresolvable event argument types produce the advisory
 * {@code JAVA_EVENT_UNRESOLVED} diagnostic outside the completeness gate.
 */
class EventEdgeTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/event");
    private static final Path UNRESOLVED_FIXTURE = Path.of("src/test/resources/fixtures/event-unresolved");

    private static final String PUBLISH_ORDER = "java:com.example.Publishers#publishOrder()";
    private static final String PUBLISH_SPECIAL = "java:com.example.Publishers#publishSpecial()";
    private static final String PUBLISH_VIA_CONTEXT = "java:com.example.Publishers#publishViaContext()";
    private static final String PUBLISH_VIA_BUS = "java:com.example.Publishers#publishViaBus()";
    private static final String ON_ORDER = "java:com.example.Listeners#onOrder(com.example.OrderEvent)";
    private static final String AFTER_COMMIT = "java:com.example.Listeners#afterCommit(com.example.OrderEvent)";
    private static final String ON_ORDER_COND =
            "java:com.example.Listeners#onOrderConditionally(com.example.OrderEvent)";
    private static final String ON_SPECIAL = "java:com.example.Listeners#onSpecial(com.example.SpecialOrderEvent)";

    @Test
    void broadcastEmitsOneCertainEdgePerUnconditionalListener() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertEquals(0, ran.exitCode(), ran.stderr());
        assertTrue(ran.stderr().contains("silentOmission=0"), ran.stderr());

        Map<String, Object> metadata = metadataOf(eventEdge(ran, PUBLISH_ORDER, ON_ORDER).orElseThrow());
        assertEquals("unique", metadata.get("resolution"), "broadcast listeners are each certain: " + metadata);
        assertEquals(List.of("spring-event"), metadata.get("provenance"));
        assertNull(metadata.get("conditional"));
        // OrderEvent does not match the SpecialOrderEvent listener (no downcast guessing).
        assertTrue(eventEdge(ran, PUBLISH_ORDER, ON_SPECIAL).isEmpty());
    }

    @Test
    void transactionalListenerIsConditionalOnTheTransactionPhase() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        // @TransactionalEventListener only fires when the transaction reaches the
        // configured phase, so broadcast certainty does not hold.
        Map<String, Object> metadata = metadataOf(eventEdge(ran, PUBLISH_ORDER, AFTER_COMMIT).orElseThrow());
        assertEquals("ambiguous", metadata.get("resolution"));
        assertEquals(true, metadata.get("conditional"));
        assertEquals(
                List.of("org.springframework.transaction.event.TransactionalEventListener"),
                metadata.get("conditionTypes"));
    }

    @Test
    void conditionalListenerIsAmbiguousWithConditionTypes() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        Map<String, Object> metadata = metadataOf(eventEdge(ran, PUBLISH_ORDER, ON_ORDER_COND).orElseThrow());
        assertEquals("ambiguous", metadata.get("resolution"));
        assertEquals(true, metadata.get("conditional"));
        assertEquals(List.of("org.springframework.context.annotation.Profile"), metadata.get("conditionTypes"));
    }

    @Test
    void subtypeEventReachesSupertypeListeners() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        // SpecialOrderEvent matches both its own listener and the OrderEvent listeners.
        assertTrue(eventEdge(ran, PUBLISH_SPECIAL, ON_SPECIAL).isPresent());
        assertTrue(eventEdge(ran, PUBLISH_SPECIAL, ON_ORDER).isPresent());
    }

    @Test
    void publisherSubtypeReceiverEmitsEdgesAndForeignPublishEventDoesNot() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        assertTrue(eventEdge(ran, PUBLISH_VIA_CONTEXT, ON_ORDER).isPresent(),
                "ApplicationContext receiver is an ApplicationEventPublisher subtype");
        assertFalse(ran.byType("callEdge").stream().anyMatch(edge ->
                        PUBLISH_VIA_BUS.equals(edge.get("callerMethodId"))
                                && List.of("spring-event").equals(metadataOf(edge).get("provenance"))),
                "publishEvent on a non-publisher receiver must not produce event edges");
    }

    @Test
    void unresolvableEventArgumentEmitsAdvisoryDiagnosticOutsideTheLedger() throws Exception {
        // An unresolvable event argument makes the publishEvent call site itself
        // unresolvable too, so the run needs allowIncompleteAnalysis to publish.
        // The advisory property is proven structurally: JAVA_EVENT_UNRESOLVED never
        // appears in the ledger summary (it is not a call site outcome), and the
        // ledger still terminates every call site (silentOmission=0).
        Map<String, Object> metadata = AnalysisTestSupport.classpathMetadata();
        metadata.put("allowIncompleteAnalysis", List.of("true"));
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                UNRESOLVED_FIXTURE, metadata, null, null, null, null);
        assertEquals(0, ran.exitCode(), ran.stderr());
        assertTrue(ran.stderr().contains("silentOmission=0"), ran.stderr());
        assertFalse(ran.stderr().contains("JAVA_EVENT_UNRESOLVED"),
                "the advisory code must not appear as a ledger outcome: " + ran.stderr());
        assertTrue(ran.byType("diagnostic").stream().anyMatch(diagnostic ->
                        "JAVA_EVENT_UNRESOLVED".equals(diagnostic.get("code"))
                                && "warning".equals(diagnostic.get("severity"))),
                "unresolvable event argument must surface JAVA_EVENT_UNRESOLVED: " + ran.byType("diagnostic"));
    }

    private static Optional<Map<String, Object>> eventEdge(
            AnalysisTestSupport.Ran ran, String callerMethodId, String calleeMethodId) {
        return ran.byType("callEdge").stream()
                .filter(edge -> callerMethodId.equals(edge.get("callerMethodId"))
                        && calleeMethodId.equals(edge.get("calleeMethodId"))
                        && List.of("spring-event").equals(metadataOf(edge).get("provenance")))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadataOf(Map<String, Object> edge) {
        Map<String, Object> metadata = (Map<String, Object>) edge.get("metadata");
        return metadata == null ? Map.of() : metadata;
    }
}
