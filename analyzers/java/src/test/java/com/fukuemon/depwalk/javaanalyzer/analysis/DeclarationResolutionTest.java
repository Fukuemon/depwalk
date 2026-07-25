package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 宣言列挙側 ({@code md.resolve()} / {@code cd.resolve()}、{@code constructorCallerIdsFor} 含む) の
 * 解決失敗は、その宣言だけを skip して {@code JAVA_UNRESOLVED_SYMBOL} (warning) を出し、解析全体は
 * 継続する (exit 0、他の宣言 / edge は出力される)。
 */
class DeclarationResolutionTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/declrecovery");

    @Test
    void unresolvableMethodAndConstructorDeclarationsAreSkippedWithWarningAndAnalysisContinues() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(0, ran.exitCode(), "declaration resolution failure must not be fatal");

        List<Map<String, Object>> diagnostics = ran.byType("diagnostic");
        long unresolvedDeclCount = diagnostics.stream()
                .filter(d -> "JAVA_UNRESOLVED_SYMBOL".equals(d.get("code")) && "warning".equals(d.get("severity")))
                .count();
        assertTrue(unresolvedDeclCount >= 2,
                "expected JAVA_UNRESOLVED_SYMBOL/warning diagnostics for the broken method and constructor: " + diagnostics);

        List<Map<String, Object>> nodes = ran.byType("methodSymbol");
        assertFalse(nodes.stream().anyMatch(n -> "java:com.example.BadDecl#broken(UnknownParam)".equals(n.get("methodId"))),
                "the method whose declaration could not be resolved must not be enumerated: " + nodes);
        assertTrue(nodes.stream().anyMatch(n -> "java:com.example.BadDecl#safe()".equals(n.get("methodId"))),
                "other declarations in the same file must still be enumerated: " + nodes);

        List<Map<String, Object>> edges = ran.byType("callEdge");
        assertTrue(edges.stream().anyMatch(e ->
                        "java:com.example.Caller#run()".equals(e.get("callerMethodId"))
                                && "java:com.example.Caller#helper()".equals(e.get("calleeMethodId"))),
                "other files' declarations and call edges must still be produced: " + edges);
    }
}
