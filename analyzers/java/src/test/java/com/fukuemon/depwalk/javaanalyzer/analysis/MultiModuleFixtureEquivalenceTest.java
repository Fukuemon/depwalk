package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import com.fukuemon.depwalk.javaanalyzer.Main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3 module primary fixture (spec #24 P5 step 1-2) の自動 discovery と明示
 * override の同値検証。process 起動 wiring は P6 の実 CLI E2E が担い、本 test は
 * Analyzer entry point ({@link Main#run}) を両経路で実行して graph を照合する。
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
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit = Main.run(
                new ByteArrayInputStream(requestJson.getBytes(StandardCharsets.UTF_8)), stdout, stderr);
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> records = new ArrayList<>();
        for (String line : stdout.toString(StandardCharsets.UTF_8).split("\n")) {
            if (!line.isBlank()) {
                records.add(mapper.readValue(line, Map.class));
            }
        }
        return new Run(exit, records, stderr.toString(StandardCharsets.UTF_8));
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
        Set<String> methods = methodSet(run).stream()
                .map(m -> (String) m.get("methodId")).collect(Collectors.toSet());
        assertTrue(methods.stream().allMatch(id -> id.contains("repository")), methods.toString());
        assertTrue(methods.contains("java:com.example.mm.repository.JpaOrderRepository#save(java.lang.String)"),
                methods.toString());
    }

    private void assertExpectedGraph(Run run) {
        // 設計から固定した期待集合 (module 境界をまたぐ call / DI / dispatch)。
        Set<String> methods = methodSet(run).stream()
                .map(m -> (String) m.get("methodId")).collect(Collectors.toSet());
        for (String expected : List.of(
                "java:com.example.mm.app.OrderController#placeOrder(java.lang.String)",
                "java:com.example.mm.app.OrderController#<init>(com.example.mm.service.OrderService)",
                "java:com.example.mm.service.OrderService#process(java.lang.String)",
                "java:com.example.mm.service.DefaultOrderService#process(java.lang.String)",
                "java:com.example.mm.repository.OrderRepository#save(java.lang.String)",
                "java:com.example.mm.repository.JpaOrderRepository#save(java.lang.String)")) {
            assertTrue(methods.contains(expected), () -> "missing " + expected + " in " + methods);
        }

        assertEdge(run, "java:com.example.mm.app.OrderController#placeOrder(java.lang.String)",
                "java:com.example.mm.service.OrderService#process(java.lang.String)", null);
        assertEdge(run, "java:com.example.mm.app.OrderController#placeOrder(java.lang.String)",
                "java:com.example.mm.service.DefaultOrderService#process(java.lang.String)", "unique");
        assertEdge(run, "java:com.example.mm.service.DefaultOrderService#process(java.lang.String)",
                "java:com.example.mm.repository.OrderRepository#save(java.lang.String)", null);
        assertEdge(run, "java:com.example.mm.service.DefaultOrderService#process(java.lang.String)",
                "java:com.example.mm.repository.JpaOrderRepository#save(java.lang.String)", "unique");

        // workspace 相対 location (module directory を含む)。
        Map<String, Object> controller = methodSet(run).stream()
                .filter(m -> "java:com.example.mm.app.OrderController#placeOrder(java.lang.String)".equals(m.get("methodId")))
                .findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> location = (Map<String, Object>) controller.get("sourceLocation");
        assertEquals("app/src/main/java/com/example/mm/app/OrderController.java", location.get("path"));

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
