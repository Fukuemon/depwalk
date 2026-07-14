package com.fukuemon.depwalk.javaanalyzer.analysis;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchCandidateIntegrationTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/dispatch-candidates");

    @TempDir
    Path tempDir;

    private AnalysisTestSupport.Ran ran;

    @BeforeEach
    void analyzeFixture() throws Exception {
        Path classesDir = compileFixture();
        ran = AnalysisTestSupport.run(
                FIXTURE,
                AnalysisTestSupport.classpathMetadata(classesDir.toString()),
                null,
                null,
                null,
                null);
        assertEquals(0, ran.exitCode(), ran.stderr());
    }

    @Test
    void keepsDeclarationEdgeAndAddsPrimaryAndQualifierImplementationEdges() {
        assertEdge(
                "java:com.example.PrimaryConsumer#checkout()",
                "java:com.example.PaymentService#pay()",
                "interface");
        Map<String, Object> primary = assertEdge(
                "java:com.example.PrimaryConsumer#checkout()",
                "java:com.example.PaypalPayment#pay()",
                null);
        assertCandidateMetadata(primary, "unique", List.of("sootup", "spring-di"));
        Map<String, Object> primaryNode = ran.byType("methodSymbol").stream()
                .filter(node -> "java:com.example.PaypalPayment#pay()".equals(node.get("methodId")))
                .findFirst()
                .orElseThrow();
        assertTrue(primaryNode.get("sourceLocation") instanceof Map<?, ?>,
                "candidate methodSymbol must keep its source declaration location");
        assertFalse(hasEdge(
                "java:com.example.PrimaryConsumer#checkout()",
                "java:com.example.StripePayment#pay()"));

        Map<String, Object> qualifier = assertEdge(
                "java:com.example.QualifierConsumer#checkout()",
                "java:com.example.StripePayment#pay()",
                null);
        assertCandidateMetadata(qualifier, "unique", List.of("sootup", "spring-di"));
    }

    @Test
    void emitsAllAmbiguousCandidatesAndDeduplicatesEachCallerCalleeEdge() {
        String caller = "java:com.example.AmbiguousConsumer#runAudit()";
        for (String callee : List.of(
                "java:com.example.AuditOne#audit()",
                "java:com.example.AuditTwo#audit()")) {
            Map<String, Object> edge = assertEdge(caller, callee, null);
            assertCandidateMetadata(edge, "ambiguous", List.of("sootup", "spring-di"));
            assertEquals(1, countEdges(caller, callee));
        }
        assertDiagnostic("JAVA_AMBIGUOUS_CANDIDATE");
    }

    @Test
    void usesSootUpCandidatesWhenCallReceiverIsNotAnInjectionPoint() {
        String caller = "java:com.example.DispatchOnlyConsumer#run(com.example.AuditService)";
        for (String callee : List.of(
                "java:com.example.AuditOne#audit()",
                "java:com.example.AuditTwo#audit()")) {
            Map<String, Object> edge = assertEdge(caller, callee, null);
            assertCandidateMetadata(edge, "ambiguous", List.of("sootup"));
        }

        String shadowedCaller = "java:com.example.PrimaryConsumer#checkoutWithShadowedParameter(com.example.PaymentService)";
        for (String callee : List.of(
                "java:com.example.PaypalPayment#pay()",
                "java:com.example.StripePayment#pay()")) {
            Map<String, Object> edge = assertEdge(shadowedCaller, callee, null);
            assertCandidateMetadata(edge, "ambiguous", List.of("sootup"));
        }
    }

    @Test
    void marksConditionalCandidateAndEmitsConditionalDiagnostic() {
        Map<String, Object> edge = assertEdge(
                "java:com.example.ConditionalConsumer#notifyCustomer()",
                "java:com.example.ConditionalNotifier#notifyUser()",
                null);
        Map<?, ?> metadata = (Map<?, ?>) edge.get("metadata");
        assertEquals("ambiguous", metadata.get("resolution"));
        assertEquals(Boolean.TRUE, metadata.get("conditional"));
        assertEquals(
                List.of("org.springframework.context.annotation.Profile"),
                metadata.get("conditionTypes"));
        assertDiagnostic("JAVA_CONDITIONAL_BEAN");
    }

    @Test
    void resolvesLombokConstructorInjectionAndDistinguishesRuntimeDiagnostics() {
        Map<String, Object> lombok = assertEdge(
                "java:com.example.LombokConsumer#checkout()",
                "java:com.example.PaypalPayment#pay()",
                null);
        assertCandidateMetadata(lombok, "unique", List.of("sootup", "spring-di"));

        assertDiagnostic("JAVA_RUNTIME_PROVIDED");
        assertDiagnostic("JAVA_UNRESOLVED_SYMBOL");
        assertEdge(
                "java:com.example.RuntimeConsumer#invokeRuntimeTypes()",
                "java:com.example.UserRepository#find()",
                "interface");
        assertEdge(
                "java:com.example.RuntimeConsumer#invokeRuntimeTypes()",
                "java:com.example.UserMapper#map()",
                "interface");
    }

    private Map<String, Object> assertEdge(String caller, String callee, String dispatch) {
        Map<String, Object> edge = ran.byType("callEdge").stream()
                .filter(record -> caller.equals(record.get("callerMethodId")) && callee.equals(record.get("calleeMethodId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing edge " + caller + " -> " + callee));
        if (dispatch != null) {
            assertEquals(dispatch, ((Map<?, ?>) edge.get("metadata")).get("dispatch"));
        }
        return edge;
    }

    private boolean hasEdge(String caller, String callee) {
        return countEdges(caller, callee) > 0;
    }

    private long countEdges(String caller, String callee) {
        return ran.byType("callEdge").stream()
                .filter(record -> caller.equals(record.get("callerMethodId")) && callee.equals(record.get("calleeMethodId")))
                .count();
    }

    private void assertCandidateMetadata(
            Map<String, Object> edge,
            String resolution,
            List<String> provenance) {
        Map<?, ?> metadata = (Map<?, ?>) edge.get("metadata");
        assertEquals(resolution, metadata.get("resolution"));
        assertEquals(provenance, metadata.get("provenance"));
    }

    private void assertDiagnostic(String code) {
        assertTrue(ran.byType("diagnostic").stream().anyMatch(record -> code.equals(record.get("code"))),
                () -> "missing diagnostic " + code + ": " + ran.byType("diagnostic"));
    }

    private Path compileFixture() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        List<String> sources;
        try (var files = Files.walk(FIXTURE)) {
            sources = files.filter(path -> path.toString().endsWith(".java"))
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }
        String lombokJar = lombokJar();
        List<String> args = new ArrayList<>(List.of(
                "--release", "21",
                "-parameters",
                "-classpath", lombokJar,
                "-processorpath", lombokJar,
                "-d", classesDir.toString()));
        args.addAll(sources);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(
                null, null, null, args.toArray(String[]::new)));
        return classesDir;
    }

    private static String lombokJar() throws URISyntaxException {
        return Path.of(RequiredArgsConstructor.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toString();
    }
}
