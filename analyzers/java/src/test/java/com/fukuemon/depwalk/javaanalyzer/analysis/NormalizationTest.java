package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.DisplayName;
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
@DisplayName("signature / methodId の正規化")
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
    @DisplayName("overload された同名メソッドは、erasure 後の引数型列で別々の methodId として区別される")
    void overloadsAreDistinguishedByErasedParameterTypes() throws Exception {
        Set<String> ids = nodeMethodIds();
        assertTrue(ids.contains("java:com.example.Widgets#overload(java.lang.String)"), ids.toString());
        assertTrue(ids.contains("java:com.example.Widgets#overload(int)"), ids.toString());
    }

    @Test
    @DisplayName("generics の引数型は、型引数を消して raw 型で表される")
    void genericsAreErasedToTheirRawType() throws Exception {
        Set<String> ids = nodeMethodIds();
        assertTrue(ids.contains("java:com.example.Widgets#generics(java.util.List)"), ids.toString());
    }

    @Test
    @DisplayName("可変長引数 (varargs) は、配列表記へ正規化される")
    void varargsAreNormalizedToArrayNotation() throws Exception {
        Set<String> ids = nodeMethodIds();
        assertTrue(ids.contains("java:com.example.Widgets#varargs(java.lang.String[])"), ids.toString());
    }

    @Test
    @DisplayName("nested class は、$ 区切りの binary name で表される")
    void nestedClassUsesDollarSeparatedBinaryName() throws Exception {
        Set<String> ids = nodeMethodIds();
        assertTrue(ids.contains("java:com.example.Widgets$Nested#inner()"), ids.toString());
    }

    @Test
    @DisplayName("constructor は、メソッド名の代わりに <init> の token で表される")
    void constructorsUseInitToken() throws Exception {
        Set<String> ids = nodeMethodIds();
        assertTrue(ids.contains("java:com.example.Widgets#<init>()"), ids.toString());
        assertTrue(ids.contains("java:com.example.Widgets#<init>(java.lang.String)"), ids.toString());
    }

    @Test
    @DisplayName("static initializer は <clinit> の token で表され、複数ブロックあっても 1 つの node に畳み込まれる")
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
    @DisplayName("匿名 class は、source 上の出現順で $1, $2 と採番される")
    void anonymousClassMethodsGetDeterministicSourceOrderNumbering() throws Exception {
        Set<String> ids = nodeMethodIds();
        assertTrue(ids.contains("java:com.example.Widgets$1#run()"), ids.toString());
        assertTrue(ids.contains("java:com.example.Widgets$2#run()"), ids.toString());
    }

    @Test
    @DisplayName("解析を別々に実行した場合でも、匿名 class の採番は同じ結果のままになる")
    void anonymousClassNumberingIsDeterministicAcrossRuns() throws Exception {
        Set<String> first = nodeMethodIds();
        Set<String> second = nodeMethodIds();
        assertTrue(first.equals(second), "anonymous numbering must be stable across separate runs");
    }
}
