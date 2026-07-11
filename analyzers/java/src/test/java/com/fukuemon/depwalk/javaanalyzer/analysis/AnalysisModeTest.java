package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D4: {@code fullGraph} / {@code reachableFromEntrypoints} の node 母集合 (宣言列挙 ∪ call site 由来、
 * entrypoints 空は analysisMode によらず全体扱い)。
 */
class AnalysisModeTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/analysismode");

    private Set<String> methodIds(AnalysisTestSupport.Ran ran) {
        return ran.byType("methodSymbol").stream().map(n -> (String) n.get("methodId")).collect(Collectors.toSet());
    }

    @Test
    void fullGraphIncludesDeclaredMethodsWithNoCallers() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, "fullGraph");
        Set<String> ids = methodIds(ran);
        assertTrue(ids.contains("java:com.example.Chain#a()"));
        assertTrue(ids.contains("java:com.example.Chain#b()"));
        assertTrue(ids.contains("java:com.example.Chain#c()"));
        assertTrue(ids.contains("java:com.example.Chain#d()"), "unreachable but declared method must still be enumerated: " + ids);
    }

    @Test
    void reachableFromEntrypointsLimitsToTransitiveCallees() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null,
                List.of(Map.of("qualifiedName", "com.example.Chain.a")), "reachableFromEntrypoints");
        Set<String> ids = methodIds(ran);
        assertTrue(ids.contains("java:com.example.Chain#a()"));
        assertTrue(ids.contains("java:com.example.Chain#b()"));
        assertTrue(ids.contains("java:com.example.Chain#c()"));
        assertFalse(ids.contains("java:com.example.Chain#d()"), "d() is not reachable from a() and must be excluded: " + ids);

        List<Map<String, Object>> edges = ran.byType("callEdge");
        assertTrue(edges.stream().anyMatch(e ->
                "java:com.example.Chain#a()".equals(e.get("callerMethodId"))
                        && "java:com.example.Chain#b()".equals(e.get("calleeMethodId"))));
        assertTrue(edges.stream().anyMatch(e ->
                "java:com.example.Chain#b()".equals(e.get("callerMethodId"))
                        && "java:com.example.Chain#c()".equals(e.get("calleeMethodId"))));
    }

    @Test
    void emptyEntrypointsIsTreatedAsWholeScopeRegardlessOfAnalysisMode() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, List.of(), "reachableFromEntrypoints");
        Set<String> ids = methodIds(ran);
        assertTrue(ids.contains("java:com.example.Chain#d()"),
                "empty entrypoints must be treated as a whole-scope request even in reachableFromEntrypoints mode: " + ids);
    }
}
