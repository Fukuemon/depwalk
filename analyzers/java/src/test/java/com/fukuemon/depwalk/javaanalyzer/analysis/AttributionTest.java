package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D7 / D11: 帰属型の決定規則 (宣言サイト scope 内 (override あり / なし)、scope 外宣言の引き上げ、
 * 除外 package (既定値 / liftExcludePackages による置き換え、segment 単位 prefix 一致)、
 * this / super / static / new の各形。
 *
 * <p>「出力しない」条 (IF) は「出力する」条 (WHEN/WHERE) に優先する例外であるため、
 * テスト名で明示する ({@code ...IsOmitted...} / {@code doesNotEmit...} 系)。
 */
class AttributionTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/attribution");
    private static final List<String> EXCLUDE_EXTERNAL = List.of("com/example/lib/**", "com/example/libx/**");

    private AnalysisTestSupport.Ran runDefault() throws Exception {
        return AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, EXCLUDE_EXTERNAL, null, null);
    }

    private boolean hasEdge(List<Map<String, Object>> edges, String caller, String callee) {
        return edges.stream().anyMatch(e -> caller.equals(e.get("callerMethodId")) && callee.equals(e.get("calleeMethodId")));
    }

    @Test
    void declarationSiteInScopeWithOverrideAttributesToTheOverridingType() throws Exception {
        List<Map<String, Object>> edges = runDefault().byType("callEdge");
        assertTrue(hasEdge(edges,
                "java:com.example.UserService#invokeSave()",
                "java:com.example.UserService#save()"));
    }

    @Test
    void declarationSiteInScopeWithoutOverrideAttributesToTheDeclaringSupertype() throws Exception {
        List<Map<String, Object>> edges = runDefault().byType("callEdge");
        assertTrue(hasEdge(edges,
                "java:com.example.UserService#invokeNotOverridden()",
                "java:com.example.BaseService#notOverridden()"));
    }

    @Test
    void superCallResolvesToTheDeclaringSupertypeEvenWhenOverridden() throws Exception {
        List<Map<String, Object>> edges = runDefault().byType("callEdge");
        assertTrue(hasEdge(edges,
                "java:com.example.UserService#invokeSuperSave()",
                "java:com.example.BaseService#save()"));
    }

    @Test
    void implicitThisCallFollowsOverrideResolutionLikeExplicitThis() throws Exception {
        List<Map<String, Object>> edges = runDefault().byType("callEdge");
        assertTrue(hasEdge(edges,
                "java:com.example.UserService#invokeImplicitThis()",
                "java:com.example.UserService#save()"));
    }

    @Test
    void declarationSiteOutOfScopeWithScopeInternalReceiverLiftsToReceiverType() throws Exception {
        AnalysisTestSupport.Ran ran = runDefault();
        List<Map<String, Object>> edges = ran.byType("callEdge");
        String calleeId = "java:com.example.UserRepository#find(java.lang.Long)";
        assertTrue(hasEdge(edges, "java:com.example.UserRepository#invokeFind()", calleeId));

        Map<String, Object> liftedNode = ran.byType("methodSymbol").stream()
                .filter(n -> calleeId.equals(n.get("methodId")))
                .findFirst()
                .orElseThrow();
        Map<?, ?> metadata = (Map<?, ?>) liftedNode.get("metadata");
        assertEquals("com.example.lib.ExternalRepo", metadata.get("declaringType"));
        assertEquals(Boolean.TRUE, metadata.get("inherited"));
    }

    @Test
    void declarationSiteOutOfScopeInDefaultExcludedPackageIsOmittedWithoutDiagnostic() throws Exception {
        AnalysisTestSupport.Ran ran = runDefault();
        List<Map<String, Object>> edges = ran.byType("callEdge");
        assertFalse(edges.stream().anyMatch(e -> "java:com.example.UserService#invokeToString()".equals(e.get("callerMethodId"))),
                "java.lang.Object#toString() is excluded by default and must not produce an edge: " + edges);
        assertNoUnresolvedDiagnostic(ran, "excluded-package omission must not raise an unresolved diagnostic");
    }

    @Test
    void bothDeclarationSiteAndReceiverOutOfScopeIsOmittedWithoutDiagnostic() throws Exception {
        AnalysisTestSupport.Ran ran = runDefault();
        List<Map<String, Object>> edges = ran.byType("callEdge");
        assertFalse(edges.stream().anyMatch(e -> "java:com.example.UserRepository#invokeExternalDirect(com.example.lib.ExternalRepo)".equals(e.get("callerMethodId"))),
                "receiver type out of scope must not produce an edge: " + edges);
        assertNoUnresolvedDiagnostic(ran, "scope-external omission must not raise an unresolved diagnostic");
    }

    @Test
    void liftExcludePackagesReplacesDefaultsAndOmitsMatchingDeclaration() throws Exception {
        Map<String, Object> metadata = AnalysisTestSupport.classpathMetadata();
        metadata.put("liftExcludePackages", List.of("com.example.lib"));
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, metadata, null, EXCLUDE_EXTERNAL, null, null);
        List<Map<String, Object>> edges = ran.byType("callEdge");

        assertFalse(edges.stream().anyMatch(e -> "java:com.example.UserRepository#invokeFind()".equals(e.get("callerMethodId"))),
                "com.example.lib is excluded by the overridden liftExcludePackages, so it must not be lifted: " + edges);

        // 既定値 (java/javax/jakarta) は liftExcludePackages 指定で「置き換え」られるため、
        // 既定除外が効かなくなる副作用が無いことは他テストの java.lang.Object 呼び出しで別途確認する。
    }

    @Test
    void liftExcludePackagesMatchesOnDotSeparatedSegmentsNotRawStringPrefix() throws Exception {
        Map<String, Object> metadata = AnalysisTestSupport.classpathMetadata();
        metadata.put("liftExcludePackages", List.of("com.example.lib"));
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, metadata, null, EXCLUDE_EXTERNAL, null, null);
        List<Map<String, Object>> edges = ran.byType("callEdge");

        // "com.example.lib" は "com.example.libx.OtherExternal" の segment 単位 prefix にはならない
        // (次の文字が "." ではなく "x") ため、引き上げは除外されず lift が発生する。
        assertTrue(hasEdge(edges,
                        "java:com.example.OtherRepository#invokePing()",
                        "java:com.example.OtherRepository#ping()"),
                "segment boundary must distinguish com.example.lib from com.example.libx: " + edges);
    }

    @Test
    void newExpressionOnScopeInternalTypeIsEmitted() throws Exception {
        List<Map<String, Object>> edges = runDefault().byType("callEdge");
        assertTrue(hasEdge(edges,
                "java:com.example.UserService#constructScopeInternal()",
                "java:com.example.UserService#<init>()"));
    }

    @Test
    void newExpressionOnScopeExternalTypeIsOmittedWithoutLiftOrDiagnostic() throws Exception {
        AnalysisTestSupport.Ran ran = runDefault();
        List<Map<String, Object>> edges = ran.byType("callEdge");
        assertFalse(edges.stream().anyMatch(e -> "java:com.example.UserService#constructScopeExternal()".equals(e.get("callerMethodId"))),
                "constructor calls are never lifted, out-of-scope new must be omitted: " + edges);
        assertNoUnresolvedDiagnostic(ran, "scope-external constructor omission must not raise an unresolved diagnostic");
    }

    /**
     * D11 (無修飾 static import): {@code import static com.example.lib.StaticUtils.util; util();}
     * は「参照した型」= 宣言型 (StaticUtils) そのものであり、enclosing class (UserService) へは
     * 引き上げない。宣言型・参照型ともに scope 外のため出力しない (diagnostic も出さない)。
     */
    @Test
    void unqualifiedStaticImportOutOfScopeIsOmittedWithoutLiftToEnclosingOrDiagnostic() throws Exception {
        AnalysisTestSupport.Ran ran = runDefault();
        List<Map<String, Object>> edges = ran.byType("callEdge");
        assertFalse(edges.stream().anyMatch(e ->
                        "java:com.example.UserService#invokeScopeExternalStaticImport()".equals(e.get("callerMethodId"))),
                "out-of-scope static import call must not be lifted to the enclosing class: " + edges);
        assertFalse(ran.byType("methodSymbol").stream().anyMatch(n ->
                        "java:com.example.lib.StaticUtils#util()".equals(n.get("methodId"))),
                "out-of-scope static import callee must not be emitted as a node either: " + ran.byType("methodSymbol"));
        assertNoUnresolvedDiagnostic(ran, "scope-external static import must not raise an unresolved diagnostic");
    }

    /**
     * D11 (無修飾 static import, scope 内): 宣言型 (MathUtils) が scope 内であれば、通常の
     * 「宣言サイトが scope 内」規則により、そのまま宣言型へ帰属する (enclosing への引き上げは
     * 発生しない、そもそも発生させる必要がない)。
     */
    @Test
    void unqualifiedStaticImportInScopeAttributesToTheDeclaringType() throws Exception {
        List<Map<String, Object>> edges = runDefault().byType("callEdge");
        assertTrue(hasEdge(edges,
                "java:com.example.UserService#invokeScopeInternalStaticImport()",
                "java:com.example.MathUtils#square()"));
    }

    private static void assertNoUnresolvedDiagnostic(AnalysisTestSupport.Ran ran, String message) {
        assertFalse(ran.byType("diagnostic").stream()
                .anyMatch(diagnostic -> "JAVA_UNRESOLVED_SYMBOL".equals(diagnostic.get("code"))), message);
    }

    /**
     * D11: 無修飾呼び出しでも、宣言型が enclosing class の継承階層内 (基底 library class から継承した
     * static メンバ) の場合は、従来どおり enclosing class への引き上げを維持する (static import 由来
     * ではなく、通常の継承メンバ参照であるため)。
     */
    @Test
    void unqualifiedInheritedStaticCallStillLiftsToEnclosingSubtype() throws Exception {
        AnalysisTestSupport.Ran ran = runDefault();
        List<Map<String, Object>> edges = ran.byType("callEdge");
        String calleeId = "java:com.example.UserRepository#staticFind()";
        assertTrue(hasEdge(edges, "java:com.example.UserRepository#invokeInheritedStaticUnqualified()", calleeId));

        Map<String, Object> liftedNode = ran.byType("methodSymbol").stream()
                .filter(n -> calleeId.equals(n.get("methodId")))
                .findFirst()
                .orElseThrow();
        Map<?, ?> metadata = (Map<?, ?>) liftedNode.get("metadata");
        assertEquals("com.example.lib.ExternalRepo", metadata.get("declaringType"));
        assertEquals(Boolean.TRUE, metadata.get("inherited"));
    }
}
