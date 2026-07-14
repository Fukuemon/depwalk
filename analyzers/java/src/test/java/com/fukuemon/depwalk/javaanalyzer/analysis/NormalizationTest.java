package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * signature / methodId 正規化 (overload / erasure / varargs / nested class ($) /
 * constructor (&lt;init&gt;) / static initializer (&lt;clinit&gt;) / 匿名クラス採番の決定性)。
 */
class NormalizationTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/normalization");

    private Set<String> nodeMethodIds() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        return ran.byType("methodSymbol").stream()
                .map(n -> (String) n.get("methodId"))
                .collect(Collectors.toSet());
    }

    @Test
    void overloadsAreDistinguishedByErasedParameterTypes() throws Exception {
        Set<String> ids = nodeMethodIds();
        assertTrue(ids.contains("java:com.example.Widgets#overload(java.lang.String)"), ids.toString());
        assertTrue(ids.contains("java:com.example.Widgets#overload(int)"), ids.toString());
    }

    @Test
    void genericsAreErasedToTheirRawType() throws Exception {
        Set<String> ids = nodeMethodIds();
        assertTrue(ids.contains("java:com.example.Widgets#generics(java.util.List)"), ids.toString());
    }

    @Test
    void varargsAreNormalizedToArrayNotation() throws Exception {
        Set<String> ids = nodeMethodIds();
        assertTrue(ids.contains("java:com.example.Widgets#varargs(java.lang.String[])"), ids.toString());
    }

    @Test
    void nestedClassUsesDollarSeparatedBinaryName() throws Exception {
        Set<String> ids = nodeMethodIds();
        assertTrue(ids.contains("java:com.example.Widgets$Nested#inner()"), ids.toString());
    }

    @Test
    void constructorsUseInitToken() throws Exception {
        Set<String> ids = nodeMethodIds();
        assertTrue(ids.contains("java:com.example.Widgets#<init>()"), ids.toString());
        assertTrue(ids.contains("java:com.example.Widgets#<init>(java.lang.String)"), ids.toString());
    }

    @Test
    void staticInitializerUsesClinitTokenAndFoldsIntoOneNode() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);
        List<Map<String, Object>> clinitNodes = ran.byType("methodSymbol").stream()
                .filter(n -> "java:com.example.Widgets#<clinit>()".equals(n.get("methodId")))
                .toList();
        assertTrue(clinitNodes.size() == 1, "exactly one <clinit> node expected: " + clinitNodes);
        assertTrue("initializer".equals(clinitNodes.get(0).get("symbolKind")));
    }

    @Test
    void anonymousClassMethodsGetDeterministicSourceOrderNumbering() throws Exception {
        Set<String> ids = nodeMethodIds();
        assertTrue(ids.contains("java:com.example.Widgets$1#run()"), ids.toString());
        assertTrue(ids.contains("java:com.example.Widgets$2#run()"), ids.toString());
    }

    @Test
    void anonymousClassNumberingIsDeterministicAcrossRuns() throws Exception {
        Set<String> first = nodeMethodIds();
        Set<String> second = nodeMethodIds();
        assertTrue(first.equals(second), "anonymous numbering must be stable across separate runs");
    }
}
