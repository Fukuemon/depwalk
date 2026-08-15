package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fukuemon.depwalk.javaanalyzer.analysis.AnalysisTestSupport.metadataOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring の event publish から listener への edge (broadcast 意味論) の検証。
 * 合致する listener は各々確定 callee ({@code resolution: unique}) で、条件付き
 * listener だけが ambiguous になる。event 引数型が解決できない場合は完全性 gate の
 * 外側で advisory の {@code JAVA_EVENT_UNRESOLVED} 診断を出す。
 */
@DisplayName("event publish から listener への呼び出し関係 (edge) の生成")
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
    private static final String ON_ORDER_EXPR =
            "java:com.example.Listeners#onOrderWhenExpression(com.example.OrderEvent)";
    private static final String ON_ORDER_CONST_EXPR =
            "java:com.example.Listeners#onOrderWhenConstantCondition(com.example.OrderEvent)";
    private static final String ON_SPECIAL ="java:com.example.Listeners#onSpecial(com.example.SpecialOrderEvent)";

    @Test
    @DisplayName("event を publish するとき、無条件 listener への呼び出し関係 (edge) は各々確定 (resolution=unique) になる")
    void broadcastEmitsOneCertainEdgePerUnconditionalListener() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        assertEquals(0, ran.exitCode(), ran.stderr());
        assertTrue(ran.stderr().contains("silentOmission=0"), ran.stderr());

        Map<String, Object> metadata = metadataOf(eventEdge(ran, PUBLISH_ORDER, ON_ORDER).orElseThrow());
        assertEquals("unique", metadata.get("resolution"), "broadcast listeners are each certain: " + metadata);
        assertEquals(List.of("spring-event"), metadata.get("provenance"));
        assertNull(metadata.get("conditional"));
        // OrderEvent は SpecialOrderEvent listener に合致しない (downcast を推測しない)。
        assertTrue(eventEdge(ran, PUBLISH_ORDER, ON_SPECIAL).isEmpty());
    }

    @Test
    @DisplayName("@TransactionalEventListener への呼び出し関係 (edge) は、transaction phase に依存するため曖昧 (ambiguous) になる")
    void transactionalListenerIsConditionalOnTheTransactionPhase() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        // @TransactionalEventListener は transaction が設定 phase へ達したときだけ
        // 実行されるので、broadcast の確定性が成立しない。
        Map<String, Object> metadata = metadataOf(eventEdge(ran, PUBLISH_ORDER, AFTER_COMMIT).orElseThrow());
        assertEquals("ambiguous", metadata.get("resolution"));
        assertEquals(true, metadata.get("conditional"));
        assertEquals(
                List.of("org.springframework.transaction.event.TransactionalEventListener"),
                metadata.get("conditionTypes"));
    }

    @Test
    @DisplayName("条件アノテーション付き listener への呼び出し関係 (edge) は、条件の出所を conditionTypes に載せて曖昧 (ambiguous) になる")
    void conditionalListenerIsAmbiguousWithConditionTypes() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        Map<String, Object> metadata = metadataOf(eventEdge(ran, PUBLISH_ORDER, ON_ORDER_COND).orElseThrow());
        assertEquals("ambiguous", metadata.get("resolution"));
        assertEquals(true, metadata.get("conditional"));
        assertEquals(List.of("org.springframework.context.annotation.Profile"), metadata.get("conditionTypes"));
    }

    @Test
    @DisplayName("@EventListener の condition (SpEL) 属性付き listener への呼び出し関係 (edge) は、実行時にしか評価できない条件のため曖昧 (ambiguous) になる")
    void conditionAttributeListenerIsAmbiguous() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        // condition 属性は実行時に評価される SpEL であり、静的解析は真偽を決められない。
        // 条件の出所として listener annotation 自身の FQN を conditionTypes に積む。
        Map<String, Object> metadata = metadataOf(eventEdge(ran, PUBLISH_ORDER, ON_ORDER_EXPR).orElseThrow());
        assertEquals("ambiguous", metadata.get("resolution"));
        assertEquals(true, metadata.get("conditional"));
        assertEquals(
                List.of("org.springframework.context.event.EventListener"),
                metadata.get("conditionTypes"));
    }

    @Test
    @DisplayName("condition 属性が定数参照の listener への呼び出し関係 (edge) も、条件の値を確定できないため曖昧 (ambiguous) になる")
    void constantReferenceConditionListenerIsAmbiguous() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        // 定数参照の condition は値を読まないので空に見えるが、条件なしと断定はできない。
        Map<String, Object> metadata = metadataOf(eventEdge(ran, PUBLISH_ORDER, ON_ORDER_CONST_EXPR).orElseThrow());
        assertEquals("ambiguous", metadata.get("resolution"));
        assertEquals(true, metadata.get("conditional"));
        assertEquals(
                List.of("org.springframework.context.event.EventListener"),
                metadata.get("conditionTypes"));
    }

    @Test
    @DisplayName("subtype の event を publish するとき、supertype を受ける listener にも呼び出し関係 (edge) が届く")
    void subtypeEventReachesSupertypeListeners() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        // SpecialOrderEvent は自身の listener と OrderEvent の listener の両方に合致する。
        assertTrue(eventEdge(ran, PUBLISH_SPECIAL, ON_SPECIAL).isPresent());
        assertTrue(eventEdge(ran, PUBLISH_SPECIAL, ON_ORDER).isPresent());
    }

    @Test
    @DisplayName("publisher の subtype receiver は呼び出し関係 (edge) を生み、publisher でない型の publishEvent は edge を生まない")
    void publisherSubtypeReceiverEmitsEdgesAndForeignPublishEventDoesNot() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        assertTrue(eventEdge(ran, PUBLISH_VIA_CONTEXT, ON_ORDER).isPresent(),
                "ApplicationContext receiver is an ApplicationEventPublisher subtype");
        assertFalse(ran.byType("callEdge").stream().anyMatch(edge ->
                        PUBLISH_VIA_BUS.equals(edge.get("callerMethodId"))
                                && List.of("spring-event").equals(metadataOf(edge).get("provenance"))),
                "publishEvent on a non-publisher receiver must not produce event edges");
    }

    @Test
    @DisplayName("event 引数型が解決できないとき、台帳 (ledger) の外側で advisory 診断 JAVA_EVENT_UNRESOLVED を出す")
    void unresolvableEventArgumentEmitsAdvisoryDiagnosticOutsideTheLedger() throws Exception {
        // 解決できない event 引数は publishEvent の call site 自体も未解決にするため、
        // publish には allowIncompleteAnalysis が要る。advisory であることは構造で示す:
        // JAVA_EVENT_UNRESOLVED は call site の終端種別ではないので、stderr の ledger
        // summary 行 (callSites=... の diagnostic[code:reason]=n 集計) に現れず、
        // それでも ledger は全 call site を終端する (silentOmission=0)。
        Map<String, Object> metadata = AnalysisTestSupport.classpathMetadata();
        metadata.put("allowIncompleteAnalysis", List.of("true"));
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(UNRESOLVED_FIXTURE, metadata);
        assertEquals(0, ran.exitCode(), ran.stderr());

        String summary = ran.stderr().lines()
                .filter(line -> line.contains("callSites="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ledger summary line missing: " + ran.stderr()));
        assertTrue(summary.contains("silentOmission=0"), summary);
        assertFalse(summary.contains("diagnostic[JAVA_EVENT_UNRESOLVED"),
                "the advisory code must not appear as a ledger outcome: " + summary);
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
}
