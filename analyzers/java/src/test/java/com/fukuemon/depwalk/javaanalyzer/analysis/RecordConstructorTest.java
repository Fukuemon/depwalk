package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * record の compact constructor ({@code record User(String name) { User { validate(); } }}) を
 * canonical constructor として扱う。signature は record component の erasure 型列とし、内部の
 * 呼び出しはその {@code <init>} を caller とする。record の通常 ctor (compact constructor なし) /
 * component accessor との整合も確認する。
 */
@DisplayName("record の compact constructor の canonical constructor としての扱い")
class RecordConstructorTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/recordctor");

    private AnalysisTestSupport.Ran run() throws Exception {
        return AnalysisTestSupport.run(FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
    }

    @Test
    @DisplayName("compact constructor は、record component の型列を signature に持つ <init> node になる")
    void compactConstructorProducesInitNode() throws Exception {
        List<Map<String, Object>> nodes = run().byType("methodSymbol");
        String initId = "java:com.example.UserRecord$User#<init>(java.lang.String,int)";
        assertTrue(nodes.stream().anyMatch(n -> initId.equals(n.get("methodId")) && "constructor".equals(n.get("symbolKind"))),
                "compact constructor must produce an <init> methodSymbol node: " + nodes);
    }

    @Test
    @DisplayName("compact constructor 本体の中の呼び出しは、その <init> を caller とする呼び出し関係 (edge) になる")
    void callWithinCompactConstructorBodyHasInitAsCaller() throws Exception {
        List<Map<String, Object>> edges = run().byType("callEdge");
        String callerId = "java:com.example.UserRecord$User#<init>(java.lang.String,int)";
        String calleeId = "java:com.example.UserRecord$User#validate(java.lang.String)";
        assertTrue(edges.stream().anyMatch(e -> callerId.equals(e.get("callerMethodId")) && calleeId.equals(e.get("calleeMethodId"))),
                "call inside a compact constructor body must not lose its caller: " + edges);
    }

    @Test
    @DisplayName("compact constructor 付き record を new するとき、宣言由来と同じ <init> node へ解決され、node は 1 件に重複排除される")
    void newExpressionOnRecordWithCompactConstructorResolvesToTheSameInitNode() throws Exception {
        AnalysisTestSupport.Ran ran = run();
        List<Map<String, Object>> edges = ran.byType("callEdge");
        String calleeId = "java:com.example.UserRecord$User#<init>(java.lang.String,int)";
        assertTrue(edges.stream().anyMatch(e ->
                        "java:com.example.UserRecord#constructUser()".equals(e.get("callerMethodId"))
                                && calleeId.equals(e.get("calleeMethodId"))),
                "new User(...) must resolve to the same <init> node produced by the compact constructor declaration: " + edges);

        long matchingNodes = ran.byType("methodSymbol").stream()
                .filter(n -> calleeId.equals(n.get("methodId")))
                .count();
        assertEquals(1, matchingNodes, "the <init> node must be de-duplicated to a single node regardless of visit order");
    }

    @Test
    @DisplayName("compact constructor の無い record を new する場合でも、合成された canonical <init> へ解決される")
    void newExpressionOnRecordWithoutCompactConstructorStillResolvesToSyntheticInit() throws Exception {
        List<Map<String, Object>> edges = run().byType("callEdge");
        assertTrue(edges.stream().anyMatch(e ->
                        "java:com.example.UserRecord#constructPoint()".equals(e.get("callerMethodId"))
                                && "java:com.example.UserRecord$Point#<init>(int,int)".equals(e.get("calleeMethodId"))),
                "record without a compact constructor must still resolve new Point(...) to the synthetic canonical <init>: " + edges);
    }

    @Test
    @DisplayName("record component の accessor 呼び出しは、通常のメソッドとして解決される")
    void componentAccessorCallResolvesNormally() throws Exception {
        List<Map<String, Object>> edges = run().byType("callEdge");
        assertTrue(edges.stream().anyMatch(e ->
                        "java:com.example.UserRecord#invokeAccessor(com.example.UserRecord$User)".equals(e.get("callerMethodId"))
                                && "java:com.example.UserRecord$User#name()".equals(e.get("calleeMethodId"))),
                "record component accessor call must resolve normally: " + edges);
    }

    @Test
    @DisplayName("record 内に宣言した通常のインスタンスメソッドの呼び出しは、通常どおり解決される")
    void instanceMethodInsideRecordResolvesNormally() throws Exception {
        List<Map<String, Object>> edges = run().byType("callEdge");
        assertTrue(edges.stream().anyMatch(e ->
                        "java:com.example.UserRecord#invokeGreet(com.example.UserRecord$User)".equals(e.get("callerMethodId"))
                                && "java:com.example.UserRecord$User#greet()".equals(e.get("calleeMethodId"))),
                "regular instance method declared inside a record must resolve normally: " + edges);
    }
}
