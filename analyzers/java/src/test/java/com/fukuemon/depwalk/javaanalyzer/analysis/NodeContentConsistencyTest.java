package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * fixture は、path の昇順で先の A.java が {@code new B()} を呼び、B.java は constructor を宣言せず
 * field initializer を持つ構成である。call site から作る default constructor の node は
 * ({@code toAst()} が空なので) sourceLocation を持たず、先に登録されると、B.java から作る
 * sourceLocation 付きの node が first-wins で捨てられる。
 */
@DisplayName("同一 methodId の node 内容の一貫性 (first-wins 登録)")
class NodeContentConsistencyTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/nodemerge");

    @Test
    @DisplayName("call site 由来で先に生成された default constructor の node でも、宣言 class の sourceLocation を持って 1 件だけ出力される")
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
