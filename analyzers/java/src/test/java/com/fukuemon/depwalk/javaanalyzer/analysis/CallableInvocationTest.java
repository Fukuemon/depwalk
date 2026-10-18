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
 * 追跡する範囲は、同じメソッドの中の local と、workspace のメソッドの parameter へ 1 hop で渡した
 * callable に限る。範囲の外の functional interface の呼び出しは、呼び出し先を推測せず、info の
 * {@code JAVA_CALLABLE_UNRESOLVED} で表に出す。
 */
@DisplayName("渡された callable への呼び出し関係 (edge) の生成")
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
    @DisplayName("parameter へ 1 hop で渡された callable を呼び出すとき、渡された全ての callable への呼び出し関係 (edge) が、受け取り側メソッド内の呼び出し位置を位置情報 (anchor) として生成される")
    void oneHopParameterPassLinksEveryPassedCallable() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        assertEquals(0, ran.exitCode(), ran.stderr());
        assertTrue(ran.stderr().contains("silentOmission=0"), ran.stderr());

        // retry へは 2 つの call site が別々の callable を渡す。
        List<Map<String, Object>> retryEdges = ran.byType("callEdge").stream()
                .filter(edge -> RETRY.equals(edge.get("callerMethodId"))
                        && Boolean.TRUE.equals(metadataOf(edge).get("viaCallableInvocation")))
                .toList();
        assertEquals(
                List.of(FROM_LAMBDA, STATIC_WORK),
                retryEdges.stream().map(edge -> (String) edge.get("calleeMethodId")).sorted().toList(),
                "retry must enumerate exactly the passed callables: " + retryEdges);
        for (Map<String, Object> edge : retryEdges) {
            Map<String, Object> callSite = AnalysisTestSupport.field(edge, "callSite");
            assertEquals("com/example/Retry.java", callSite.get("path"),
                    "the edge anchor is the invocation site inside retry: " + edge);
        }
    }

    @Test
    @DisplayName("同一メソッド内の local に入れた callable を呼び出すとき、lambda は自分自身への、method reference は参照先メソッドへの呼び出し関係 (edge) になる")
    void sameMethodLocalsLinkLambdaToSelfAndReferenceToTarget() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());

        // local の lambda の本体は囲んでいるメソッドの node に含まれるので、呼び出しは自分自身への edge になる。
        assertTrue(callableEdge(ran, USE_LOCAL_LAMBDA, USE_LOCAL_LAMBDA).isPresent(),
                "local lambda invocation must self-link: " + ran.byType("callEdge"));
        assertTrue(callableEdge(ran, USE_LOCAL_REFERENCE, STATIC_HELPER).isPresent(),
                "local reference invocation must link to the referenced method: " + ran.byType("callEdge"));
    }

    @Test
    @DisplayName("再代入された local の呼び出しは追跡せず、呼び出し先を推測しないまま補助的な (advisory) 診断で表面化する")
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
    @DisplayName("field に保持された callable の呼び出しは呼び出し関係 (edge) を作らず、補助的な info 診断 (JAVA_CALLABLE_UNRESOLVED) だけを出す")
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
    @DisplayName("this. で修飾した field の callable の呼び出しも、無修飾のときと同じく補助的な info 診断 (JAVA_CALLABLE_UNRESOLVED) を出す")
    void thisQualifiedFieldStoredCallableSurfacesTheSameAdvisory() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        assertEquals(0, ran.exitCode(), ran.stderr());
        String invokeStoredViaThis = "java:com.example.FieldUse#invokeStoredViaThis()";
        assertTrue(ran.byType("callEdge").stream().noneMatch(edge ->
                        invokeStoredViaThis.equals(edge.get("callerMethodId"))
                                && Boolean.TRUE.equals(metadataOf(edge).get("viaCallableInvocation"))),
                "this-qualified field callables must not produce guessed edges");
        assertTrue(ran.byType("diagnostic").stream().anyMatch(diagnostic ->
                        "JAVA_CALLABLE_UNRESOLVED".equals(diagnostic.get("code"))
                                && "info".equals(diagnostic.get("severity"))
                                && invokeStoredViaThis.equals(diagnostic.get("relatedMethodId"))),
                "this-qualified field callable invocation must surface the advisory info: "
                        + ran.byType("diagnostic"));
    }

    @Test
    @DisplayName("functional でない interface の呼び出しは、callable 追跡の対象にも補助診断の対象にもならず、通常の呼び出し関係 (edge) のままになる")
    void plainInterfaceCallsAreNeitherTrackedNorDiagnosed() throws Exception {
        // 注入した service のような普通の interface の呼び出しを診断に入れると、診断が雑音で埋まる。
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata());
        assertEquals(0, ran.exitCode(), ran.stderr());
        // 解析が動かずに通ってしまう誤りを防ぐため、通常の edge が出ていることも確かめる。
        assertTrue(ran.byType("callEdge").stream().anyMatch(edge ->
                        "java:com.example.PlainInterfaceUse#call()".equals(edge.get("callerMethodId"))
                                && "java:com.example.NonFunctional#first()".equals(edge.get("calleeMethodId"))
                                && !Boolean.TRUE.equals(metadataOf(edge).get("viaCallableInvocation"))),
                "the plain interface call must stay a normal edge: " + ran.byType("callEdge"));
        assertTrue(ran.byType("callEdge").stream().noneMatch(edge ->
                        "java:com.example.PlainInterfaceUse#call()".equals(edge.get("callerMethodId"))
                                && Boolean.TRUE.equals(metadataOf(edge).get("viaCallableInvocation"))),
                "plain interface calls must not produce callable invocation edges: " + ran.byType("callEdge"));
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
