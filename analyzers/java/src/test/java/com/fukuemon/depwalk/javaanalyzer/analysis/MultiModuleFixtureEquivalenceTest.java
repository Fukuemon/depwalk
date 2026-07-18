package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3 module primary fixture (spec #24 P5 step 1-2) の自動 discovery と明示
 * override の同値検証。実 jar (shadowJar) を子 process として両経路で実行し、
 * 固定期待集合 (testdata の expected/graph.json) と graph を照合する。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiModuleFixtureEquivalenceTest {

    private static final List<String> EXPLICIT_ROOTS = List.of(
            "app/src/main/java", "modules/service/src/main/java", "repository/src/domain/java");

    private Path fixture;
    private List<String> classpathManifest;

    @BeforeAll
    void buildFixture() throws Exception {
        fixture = Path.of("..", "..", "testdata", "fixtures", "java", "multi-module-spring-project")
                .toAbsolutePath().normalize();
        Path manifest = fixture.resolve("build/depwalk-classpath.txt");
        if (!Files.exists(manifest)) {
            // fixture build (classes output + 明示 override 用 classpath manifest) は
            // test harness が起動する。Analyzer 自身は task を起動しない。
            try (ProjectConnection connection = GradleConnector.newConnector()
                    .forProjectDirectory(fixture.toFile())
                    .useGradleVersion("9.6.1")
                    .connect()) {
                connection.newBuild()
                        .forTasks("writeDepwalkClasspath")
                        .setStandardOutput(OutputStream.nullOutputStream())
                        .setStandardError(OutputStream.nullOutputStream())
                        .run();
            }
        }
        classpathManifest = Files.readAllLines(manifest).stream().filter(l -> !l.isBlank()).toList();
    }

    private record Run(int exitCode, List<Map<String, Object>> records, String stderr) {
        List<Map<String, Object>> byType(String type) {
            return records.stream().filter(r -> type.equals(r.get("recordType"))).toList();
        }
    }

    private Run analyze(String requestJson) throws Exception {
        Path jar = Path.of("build", "libs", "java-analyzer.jar").toAbsolutePath().normalize();
        assertTrue(Files.exists(jar), "run ./gradlew shadowJar first: " + jar);
        Path javaBin = Path.of(System.getProperty("java.home"), "bin", "java");
        Process process = new ProcessBuilder(javaBin.toString(), "-jar", jar.toString()).start();
        process.getOutputStream().write(requestJson.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> records = new ArrayList<>();
        for (String line : stdout.split("\n")) {
            if (!line.isBlank()) {
                records.add(mapper.readValue(line, Map.class));
            }
        }
        return new Run(exit, records, stderr);
    }

    private Run runAuto() throws Exception {
        // 自動 discovery: workspace root だけを渡す (sourceRoots / classpath / language metadata なし)。
        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\",\"requestId\":\"mm-auto\","
                + "\"workspaceRoot\":" + json(fixture.toString()) + ",\"language\":\"java\"}";
        return analyze(request);
    }

    private Run runExplicit() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String roots = mapper.writeValueAsString(EXPLICIT_ROOTS);
        String classpath = mapper.writeValueAsString(classpathManifest);
        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\",\"requestId\":\"mm-explicit\","
                + "\"workspaceRoot\":" + json(fixture.toString()) + ",\"sourceRoots\":" + roots
                + ",\"language\":\"java\",\"metadata\":{\"classpath\":" + classpath
                + ",\"javaLanguageLevel\":[\"17\"]}}";
        return analyze(request);
    }

    @Test
    void autoDiscoveryAndExplicitOverrideProduceTheSameGraph() throws Exception {
        Run auto = runAuto();
        Run explicit = runExplicit();

        assertEquals(0, auto.exitCode(), auto.stderr());
        assertEquals(0, explicit.exitCode(), explicit.stderr());

        // 自動経路だけが discovery を実行し、安全通知と discovery metrics を出す。
        assertTrue(auto.stderr().contains("depwalk: notice build-model discovery"), auto.stderr());
        assertTrue(auto.stderr().contains("discovery phase=end"), auto.stderr());
        assertTrue(auto.stderr().contains("projects=3"), auto.stderr());
        assertTrue(auto.stderr().contains("sourceRoots=3"), auto.stderr());
        assertFalse(explicit.stderr().contains("build-model discovery"),
                "explicit override must bypass the Tooling API completely: " + explicit.stderr());

        // discovery 固有 metrics を除き、method / edge / diagnostic / outcome 集計は同値。
        assertEquals(methodSet(auto), methodSet(explicit));
        assertEquals(edgeSet(auto), edgeSet(explicit));
        assertEquals(diagnosticSet(auto), diagnosticSet(explicit));
        assertEquals(ledgerSummary(auto.stderr()), ledgerSummary(explicit.stderr()));

        assertExpectedGraph(auto);
        assertExpectedGraph(explicit);
    }

    @Test
    void moduleScopedIncludeExcludeAppliesToWorkspaceRelativePaths() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String roots = mapper.writeValueAsString(EXPLICIT_ROOTS);
        String classpath = mapper.writeValueAsString(classpathManifest);
        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\",\"requestId\":\"mm-include\","
                + "\"workspaceRoot\":" + json(fixture.toString()) + ",\"sourceRoots\":" + roots
                + ",\"include\":[\"repository/**\"],"
                + "\"language\":\"java\",\"metadata\":{\"classpath\":" + classpath
                + ",\"javaLanguageLevel\":[\"17\"]}}";
        Run run = analyze(request);

        assertEquals(0, run.exitCode(), run.stderr());
        // include glob は workspace 相対 path (module directory を含む) で判定される。
        for (Map<String, Object> method : methodSet(run)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> location = (Map<String, Object>) method.get("sourceLocation");
            if (location != null) {
                assertTrue(((String) location.get("path")).startsWith("repository/"),
                        "include repository/** must restrict the scope by path: " + method);
            }
        }
        Set<String> methods = methodSet(run).stream()
                .map(m -> (String) m.get("methodId")).collect(Collectors.toSet());
        assertTrue(methods.contains("java:com.example.mm.repository.JpaOrderRepository#save(java.lang.String)"),
                methods.toString());
    }

    @SuppressWarnings("unchecked")
    private void assertExpectedGraph(Run run) throws Exception {
        // 固定期待集合の正本は testdata の expected/graph.json (P6 の実 CLI E2E も参照可能)。
        Map<String, Object> expected = new ObjectMapper()
                .readValue(fixture.resolve("expected/graph.json").toFile(), Map.class);
        Map<String, Map<String, Object>> methodsById = methodSet(run).stream()
                .collect(Collectors.toMap(m -> (String) m.get("methodId"), m -> m));
        for (Map<String, Object> expectedMethod : (List<Map<String, Object>>) expected.get("methods")) {
            String methodId = (String) expectedMethod.get("methodId");
            Map<String, Object> actual = methodsById.get(methodId);
            assertTrue(actual != null, () -> "missing " + methodId + " in " + methodsById.keySet());
            Map<String, Object> location = (Map<String, Object>) actual.get("sourceLocation");
            assertEquals(expectedMethod.get("sourceLocation"), location.get("path"), methodId);
        }
        for (Map<String, Object> expectedEdge : (List<Map<String, Object>>) expected.get("edges")) {
            assertEdge(run, (String) expectedEdge.get("caller"), (String) expectedEdge.get("callee"),
                    (String) expectedEdge.get("resolution"));
        }
        assertTrue(run.stderr().contains("silentOmission=0"), run.stderr());
    }

    private void assertEdge(Run run, String caller, String callee, String resolution) {
        Map<String, Object> edge = edgeSet(run).stream()
                .filter(e -> caller.equals(e.get("caller")) && callee.equals(e.get("callee")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing edge " + caller + " -> " + callee));
        if (resolution != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) edge.get("metadata");
            assertEquals(resolution, metadata.get("resolution"), edge.toString());
            assertTrue(((List<?>) metadata.get("provenance")).contains("spring-di"), edge.toString());
        }
    }

    private static Set<Map<String, Object>> methodSet(Run run) {
        return new LinkedHashSet<>(run.byType("methodSymbol"));
    }

    private static Set<Map<String, Object>> edgeSet(Run run) {
        // edgeId は採番順で経路間に依存しないため比較から除外する。
        return run.byType("callEdge").stream().map(e -> Map.<String, Object>of(
                "caller", e.get("callerMethodId"),
                "callee", e.get("calleeMethodId"),
                "callSite", e.getOrDefault("callSite", Map.of()),
                "metadata", e.getOrDefault("metadata", Map.of()))).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<Map<String, Object>> diagnosticSet(Run run) {
        return new LinkedHashSet<>(run.byType("diagnostic"));
    }

    private static String ledgerSummary(String stderr) {
        return stderr.lines().filter(line -> line.startsWith("callSites=")).findFirst().orElse("");
    }

    private static String json(String value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
