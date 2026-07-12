package com.fukuemon.depwalk.javaanalyzer.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ステップ 1: {@code JarTypeSolver}（classpath の依存 jar）を経由した型解決の疎通確認。
 * jackson-databind は本プロジェクトの既存依存であり、test classpath 上の実 jar path を
 * リフレクション経由で取得して {@code analysisRequest.metadata.classpath} に渡す。
 */
class JarTypeSolverTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/jartypesolver");

    @Test
    void declarationInDependencyJarIsLiftedToScopeInternalSubtype() throws Exception {
        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                FIXTURE,
                AnalysisTestSupport.classpathMetadata(
                        jarPathOf(ObjectMapper.class), jarPathOf(JsonParser.class), jarPathOf(JsonIgnoreProperties.class)),
                null, null, null, null);

        assertEquals(0, ran.exitCode());
        List<Map<String, Object>> edges = ran.byType("callEdge");
        String calleeId = "java:com.example.MyMapper#writeValueAsString(java.lang.Object)";
        assertTrue(edges.stream().anyMatch(e ->
                        "java:com.example.MyMapper#invoke()".equals(e.get("callerMethodId"))
                                && calleeId.equals(e.get("calleeMethodId"))),
                "jar-declared inherited method should be lifted to the scope-internal subtype: " + edges);

        Map<String, Object> node = ran.byType("methodSymbol").stream()
                .filter(n -> calleeId.equals(n.get("methodId")))
                .findFirst()
                .orElseThrow();
        Map<?, ?> metadata = (Map<?, ?>) node.get("metadata");
        assertEquals("com.fasterxml.jackson.databind.ObjectMapper", metadata.get("declaringType"));
    }

    private static String jarPathOf(Class<?> marker) throws URISyntaxException {
        return Path.of(marker.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toAbsolutePath().toString();
    }
}
