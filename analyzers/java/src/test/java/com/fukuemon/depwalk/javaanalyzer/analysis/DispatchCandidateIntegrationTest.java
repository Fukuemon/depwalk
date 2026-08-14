package com.fukuemon.depwalk.javaanalyzer.analysis;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

@DisplayName("SootUp 候補と Spring DI 解決を統合した dispatch 候補の呼び出し関係 (edge) の生成")
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
    @DisplayName("interface 宣言への呼び出し関係 (edge) を残したまま、@Primary / @Qualifier で確定した実装だけへ候補の edge を追加する")
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
    @DisplayName("候補を 1 つに確定できないとき、全候補へ曖昧 (ambiguous) な呼び出し関係 (edge) を重複なく出し、診断 JAVA_AMBIGUOUS_CANDIDATE を出す")
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
    @DisplayName("呼び出しの receiver が DI の注入点でないとき、SootUp が絞り込んだ型階層の候補だけで呼び出し関係 (edge) を決める")
    void usesSootUpCandidatesWhenCallReceiverIsNotAnInjectionPoint() {
        String caller = "java:com.example.DispatchOnlyConsumer#run(com.example.AuditService)";
        for (String callee : List.of(
                "java:com.example.AuditOne#audit()",
                "java:com.example.AuditTwo#audit()")) {
            Map<String, Object> edge = assertEdge(caller, callee, null);
            assertCandidateMetadata(edge, "ambiguous", List.of("sootup"));
        }

        String childCaller = "java:com.example.DispatchOnlyConsumer#runChild(com.example.ChildDispatchService)";
        Map<String, Object> childEdge = assertEdge(
                childCaller,
                "java:com.example.ChildDispatchImplementation#invoke()",
                null);
        assertCandidateMetadata(childEdge, "unique", List.of("sootup"));
        assertFalse(hasEdge(
                childCaller,
                "java:com.example.ParentOnlyDispatchImplementation#invoke()"));

        String genericChildCaller =
                "java:com.example.DispatchOnlyConsumer#runGenericChild(com.example.ChildDispatchService)";
        Map<String, Object> genericChildEdge = assertEdge(
                genericChildCaller,
                "java:com.example.ChildDispatchImplementation#invoke()",
                null);
        assertCandidateMetadata(genericChildEdge, "unique", List.of("sootup"));
        assertFalse(hasEdge(
                genericChildCaller,
                "java:com.example.ParentOnlyDispatchImplementation#invoke()"));

        String intersectionCaller =
                "java:com.example.DispatchOnlyConsumer#runIntersection(com.example.IntersectionDispatchA)";
        Map<String, Object> intersectionEdge = assertEdge(
                intersectionCaller,
                "java:com.example.IntersectionDispatchBoth#invoke()",
                null);
        assertCandidateMetadata(intersectionEdge, "unique", List.of("sootup"));
        assertFalse(hasEdge(
                intersectionCaller,
                "java:com.example.IntersectionDispatchAOnly#invoke()"));
        assertFalse(hasEdge(
                intersectionCaller,
                "java:com.example.SharedIntersectionDispatchBase#invoke()"));

        String shadowedCaller = "java:com.example.PrimaryConsumer#checkoutWithShadowedParameter(com.example.PaymentService)";
        for (String callee : List.of(
                "java:com.example.PaypalPayment#pay()",
                "java:com.example.StripePayment#pay()")) {
            Map<String, Object> edge = assertEdge(shadowedCaller, callee, null);
            assertCandidateMetadata(edge, "ambiguous", List.of("sootup"));
        }

        String foreignFieldCaller = "java:com.example.ForeignFieldConsumer#checkoutOther(com.example.PaymentHolder)";
        for (String callee : List.of(
                "java:com.example.PaypalPayment#pay()",
                "java:com.example.StripePayment#pay()")) {
            Map<String, Object> edge = assertEdge(foreignFieldCaller, callee, null);
            assertCandidateMetadata(edge, "ambiguous", List.of("sootup"));
        }
    }

    @Test
    @DisplayName("constructor 引数の名前が field と異なる場合でも、代入先 field を receiver として注入解決で候補を確定する")
    void mapsConstructorParameterToItsAssignedFieldReceiver() {
        Map<String, Object> edge = assertEdge(
                "java:com.example.RenamedConstructorConsumer#checkout()",
                "java:com.example.PaypalPayment#pay()",
                null);
        assertCandidateMetadata(edge, "unique", List.of("sootup", "spring-di"));
        assertFalse(hasEdge(
                "java:com.example.RenamedConstructorConsumer#checkout()",
                "java:com.example.StripePayment#pay()"));
    }

    @Test
    @DisplayName("setter 引数の名前が field と異なる場合でも、代入先 field を receiver として注入解決で候補を確定する")
    void mapsSetterParameterToItsAssignedFieldReceiver() {
        Map<String, Object> edge = assertEdge(
                "java:com.example.RenamedSetterConsumer#checkout()",
                "java:com.example.PaypalPayment#pay()",
                null);
        assertCandidateMetadata(edge, "unique", List.of("sootup", "spring-di"));
        assertFalse(hasEdge(
                "java:com.example.RenamedSetterConsumer#checkout()",
                "java:com.example.StripePayment#pay()"));
    }

    @Test
    @DisplayName("同名の引数が別々の setter にあるとき、宣言ごとに区別して各々の注入解決を適用する")
    void distinguishesSameNamedParametersByTheirDeclarations() {
        String paymentCaller = "java:com.example.SameNamedSetterParametersConsumer#setPayment(com.example.PaymentService)";
        Map<String, Object> payment = assertEdge(
                paymentCaller,
                "java:com.example.PaypalPayment#pay()",
                null);
        assertCandidateMetadata(payment, "unique", List.of("sootup", "spring-di"));
        assertFalse(hasEdge(paymentCaller, "java:com.example.StripePayment#pay()"));

        String auditCaller = "java:com.example.SameNamedSetterParametersConsumer#setAudit(com.example.AuditService)";
        for (String callee : List.of(
                "java:com.example.AuditOne#audit()",
                "java:com.example.AuditTwo#audit()")) {
            Map<String, Object> edge = assertEdge(auditCaller, callee, null);
            assertCandidateMetadata(edge, "ambiguous", List.of("sootup", "spring-di"));
        }
    }

    @Test
    @DisplayName("@Profile 付き bean が候補のとき、条件付きの曖昧 (ambiguous) として記録し、診断 JAVA_CONDITIONAL_BEAN を出す")
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
    @DisplayName("super を明示した呼び出しは親 class の宣言に固定され、override 実装への候補の呼び出し関係 (edge) を追加しない")
    void keepsExplicitSuperDispatchBoundToTheSuperclassDeclaration() {
        String declaration = "java:com.example.BaseProcessor#process()";
        String override = "java:com.example.OverridingProcessor#process()";
        for (String caller : List.of(
                "java:com.example.OverridingProcessor#callSuper()",
                "java:com.example.OverridingProcessor#referenceSuper()")) {
            assertEdge(caller, declaration, null);
            assertFalse(hasEdge(caller, override),
                    () -> "explicit super dispatch must not add an override candidate: " + caller);
        }
    }

    @Test
    @DisplayName("Lombok 生成 constructor の注入を解決し、実行時に提供される型は JAVA_RUNTIME_PROVIDED / JAVA_UNRESOLVED_SYMBOL の診断で区別する")
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

    @Test
    @DisplayName("Spring の注入先 bean が見つからないとき、SootUp の候補へ代替して曖昧 (ambiguous) な呼び出し関係 (edge) を出す")
    void fallsBackToSootCandidatesForUnresolvedSpringInjection() {
        String caller = "java:com.example.UnresolvedBeanConsumer#execute()";
        for (String callee : List.of(
                "java:com.example.NonBeanOne#execute()",
                "java:com.example.NonBeanTwo#execute()")) {
            Map<String, Object> edge = assertEdge(caller, callee, null);
            assertCandidateMetadata(edge, "ambiguous", List.of("sootup"));
        }
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
