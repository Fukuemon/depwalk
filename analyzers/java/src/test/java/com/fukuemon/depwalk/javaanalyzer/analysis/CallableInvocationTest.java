package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fukuemon.depwalk.javaanalyzer.analysis.AnalysisTestSupport.metadataOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * callable invocation edge (adr/0012-implicit-call-resolution-and-type-propagation-rescue.md) の検証。
 * functional interface の呼び出しは、静的追跡スコープ (同一メソッド内の local と、
 * workspace メソッドの parameter への 1 hop 引数渡し) の範囲で、渡された callable
 * へ edge を張る。スコープ外の SAM invocation は全般に、edge を推測せず advisory の
 * {@code JAVA_CALLABLE_UNRESOLVED} (info) で表面化する。
 */
@DisplayName("callable invocation edge の生成")
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
    @DisplayName("parameter へ 1 hop で渡された callable は、渡した全ての callable が invocation site を anchor に edge になる")
    void oneHopParameterPassLinksEveryPassedCallable() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        assertEquals(0, ran.exitCode(), ran.stderr());
        assertTrue(ran.stderr().contains("silentOmission=0"), ran.stderr());

        // retry へは 2 つの call site が別々の callable を渡す。invocation はその
        // 渡された callee だけを列挙し、anchor は retry 内の invocation site になる。
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
    @DisplayName("同一メソッド内 local の callable を invoke するとき、lambda は自己 edge、method reference は参照先への edge になる")
    void sameMethodLocalsLinkLambdaToSelfAndReferenceToTarget() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());

        // local lambda の本体は囲みメソッド自身に属するので、invocation は
        // 標識付きの自己 edge になる (本体 ⊂ この node)。
        assertTrue(callableEdge(ran, USE_LOCAL_LAMBDA, USE_LOCAL_LAMBDA).isPresent(),
                "local lambda invocation must self-link: " + ran.byType("callEdge"));
        assertTrue(callableEdge(ran, USE_LOCAL_REFERENCE, STATIC_HELPER).isPresent(),
                "local reference invocation must link to the referenced method: " + ran.byType("callEdge"));
    }

    @Test
    @DisplayName("再代入された local の invoke は追跡せず、advisory 診断で表面化する")
    void reassignedLocalIsNotTrackedAndSurfacesTheAdvisory() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
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
    @DisplayName("field に保持された callable の invoke は edge を作らず、advisory info 診断だけを出す")
    void fieldStoredCallableSurfacesAdvisoryInfoDiagnosticWithoutEdges() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
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
    @DisplayName("functional でない interface の呼び出しは、callable 追跡の対象にも advisory 診断の対象にもならず、通常 edge のままになる")
    void plainInterfaceCallsAreNeitherTrackedNorDiagnosed() throws Exception {
        // functional でない interface (注入された service 等) の呼び出しは、追跡と
        // advisory 診断のどちらにも入れない (ノイズ回帰の防止)。
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        assertEquals(0, ran.exitCode(), ran.stderr());
        // 解析自体は動いており、呼び出しは通常 edge として残る (空振り防止の正の検証)。
        assertTrue(ran.byType("callEdge").stream().anyMatch(edge ->
                        "java:com.example.PlainInterfaceUse#call()".equals(edge.get("callerMethodId"))
                                && "java:com.example.NonFunctional#first()".equals(edge.get("calleeMethodId"))
                                && !Boolean.TRUE.equals(metadataOf(edge).get("viaCallableInvocation"))),
                "the plain interface call must stay a normal edge: " + ran.byType("callEdge"));
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
}
