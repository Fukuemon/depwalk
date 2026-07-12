package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * putIfAbsent (first-wins) 検証: 同一 {@code methodId} の node がどの経路 (宣言列挙 / call site 由来 /
 * synthetic default constructor) から生成されても同一内容になること。
 *
 * <p>fixture は「A.java (path 昇順で先) が {@code new B()} を呼び、B.java は constructor 宣言を持たず
 * field initializer で default constructor node が必要になる」構成。修正前は call site 由来の
 * synthetic default constructor ({@code toAst()} 空) が sourceLocation 無しで先に登録され、
 * B.java 由来の sourceLocation 付き node が first-wins で捨てられていた。
 */
class NodeContentConsistencyTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/nodemerge");

    @Test
    void callSiteEmittedSyntheticDefaultConstructorCarriesDeclaringTypeSourceLocation() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(0, ran.exitCode());
        List<Map<String, Object>> initNodes = ran.byType("methodSymbol").stream()
                .filter(n -> "java:com.example.B#<init>()".equals(n.get("methodId")))
                .toList();
        assertEquals(1, initNodes.size(), "same methodId must be emitted exactly once: " + initNodes);

        Map<?, ?> sourceLocation = (Map<?, ?>) initNodes.get(0).get("sourceLocation");
        assertNotNull(sourceLocation,
                "synthetic default constructor emitted from a call site must carry the declaring type's "
                        + "sourceLocation so first-wins dedup loses no information: " + initNodes.get(0));
        assertEquals("com/example/B.java", sourceLocation.get("path"));
        assertTrue(((Number) sourceLocation.get("startLine")).intValue() >= 1);
    }
}
