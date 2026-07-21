package com.fukuemon.depwalk.javaanalyzer.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** spec #24 D20 / D22: 未解決 in-scope call の全件 details 付き fatal 化。 */
class IncompleteAnalysisTest {

    @TempDir
    Path workspace;

    @SuppressWarnings("unchecked")
    @Test
    void unresolvedCallsFailTheRequestWithOrderedSelfContainedDetails() throws Exception {
        write("com/example/A.java", """
                package com.example;
                public class A {
                    void first() { MissingOne.go(); }
                }
                """);
        write("com/example/B.java", """
                package com.example;
                public class B {
                    void second() { MissingTwo.go(); new MissingThree(); }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(1, ran.exitCode(), ran.stderr());
        List<Map<String, Object>> errors = ran.byType("error");
        assertEquals(1, errors.size());
        Map<String, Object> error = errors.get(0);
        assertEquals("JAVA_INCOMPLETE_ANALYSIS", error.get("code"));

        List<Map<String, Object>> details = (List<Map<String, Object>>) error.get("details");
        assertEquals(3, details.size(), "all primary diagnostics must be reported without truncation");
        // 決定順 (workspace 相対 path → 位置)。
        assertEquals("com/example/A.java",
                ((Map<String, Object>) details.get(0).get("sourceLocation")).get("path"));
        assertEquals("com/example/B.java",
                ((Map<String, Object>) details.get(1).get("sourceLocation")).get("path"));
        for (Map<String, Object> detail : details) {
            assertEquals("JAVA_UNRESOLVED_SYMBOL", detail.get("code"));
            Map<String, Object> metadata = (Map<String, Object>) detail.get("metadata");
            assertTrue(metadata.containsKey("callKind"));
            assertTrue(metadata.containsKey("reason"));
            // spec #27 D2: 診断 metadata (解決段階 / 例外クラス名 / receiver 式種別 /
            // receiver 型取得成否)。call 解決の失敗段階を表すため、caller 宣言側の
            // 失敗 (unresolved-caller) には載らない。exceptionClass はクラス名のみで、
            // message や source 断片を含む自由文であってはならない (他 field は
            // 既存の安定値契約 (reason / target / callKind) が sanitize を担保する)。
            if (!"unresolved-caller".equals(metadata.get("reason"))) {
                assertTrue(metadata.containsKey("resolutionPhase"), () -> "resolutionPhase missing: " + metadata);
                assertTrue(metadata.containsKey("receiverKind"), () -> "receiverKind missing: " + metadata);
                assertTrue(metadata.containsKey("receiverTypeResolved"), () -> "receiverTypeResolved missing: " + metadata);
            }
            Object exceptionClass = metadata.get("exceptionClass");
            if (exceptionClass != null) {
                assertTrue(((String) exceptionClass).matches("[\\w.$]+"),
                        () -> "exceptionClass must be a bare class name: " + exceptionClass);
            }
            String serialized = detail.toString();
            assertFalse(serialized.contains(workspace.toString()), "no absolute paths in details");
        }

        Map<String, Object> metadata = (Map<String, Object>) error.get("metadata");
        assertEquals(3, ((Number) metadata.get("total")).intValue());
        Map<String, Object> reasonCounts = (Map<String, Object>) metadata.get("reasonCounts");
        int sum = reasonCounts.values().stream().mapToInt(v -> ((Number) v).intValue()).sum();
        assertEquals(3, sum, "reasonCounts must agree with total");
        // fatal は先行 warning を無効化するため、SootUp 未利用 (source-only) の
        // context 数を upstream cause として error metadata で自己完結に保持する。
        assertEquals(1, ((Number) metadata.get("sootUpUnavailableContexts")).intValue());

        // serialized byte 数 metric: details が空でなく、全 detail が payload に載る。
        byte[] serialized = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsBytes(error);
        assertTrue(serialized.length > 200, "serialized error record bytes = " + serialized.length);

        // ledger summary (集計行) は成功経路だけの出力。fatal 経路では出力しない。
        assertFalse(ran.stderr().contains("silentOmission"),
                "fatal path must not print the ledger summary: " + ran.stderr());
    }

    @SuppressWarnings("unchecked")
    @Test
    void callsUnderUnresolvableCallerDeclarationsRemainInTheCompletenessGate() throws Exception {
        // caller 宣言 (parameter 型が未解決) が placeholder へ落ちる場合、その配下の
        // call site は edge を出せないため emitted でなく primary diagnostic として
        // fatal に残る (D14 / D20)。
        write("com/example/C.java", """
                package com.example;
                public class C {
                    void broken(Missing param) { helper(); }
                    void helper() {}
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(1, ran.exitCode(), ran.stderr());
        List<Map<String, Object>> errors = ran.byType("error");
        assertEquals(1, errors.size());
        assertEquals("JAVA_INCOMPLETE_ANALYSIS", errors.get(0).get("code"));
        List<Map<String, Object>> details = (List<Map<String, Object>>) errors.get(0).get("details");
        assertTrue(details.stream().anyMatch(d ->
                        "unresolved-caller".equals(((Map<String, Object>) d.get("metadata")).get("reason"))),
                () -> "expected an unresolved-caller primary diagnostic: " + details);
    }

    @SuppressWarnings("unchecked")
    @Test
    void diagnosticMetadataIdentifiesFailedStagePerCallKind() throws Exception {
        // spec #27 D2: callKind ごとに失敗段階・receiver 情報が details へ載る。
        write("com/example/D.java", """
                package com.example;
                public class D {
                    void methodCall() { MissingType.go(); }
                    Runnable methodReference() { return MissingRef::run; }
                    Object objectCreation() { return new MissingCtor(); }
                }
                """);
        write("com/example/E.java", """
                package com.example;
                public class E extends MissingBase {
                    E() { super(1); }
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(1, ran.exitCode(), ran.stderr());
        List<Map<String, Object>> errors = ran.byType("error");
        assertEquals(1, errors.size());
        List<Map<String, Object>> details = (List<Map<String, Object>>) errors.get(0).get("details");

        Map<String, Object> methodCall = detailByCallKind(details, "method-call");
        assertEquals("bytecode-rescue", metadataOf(methodCall).get("resolutionPhase"));
        assertEquals("NameExpr", metadataOf(methodCall).get("receiverKind"));
        assertEquals(Boolean.FALSE, metadataOf(methodCall).get("receiverTypeResolved"));
        assertTrue(((String) metadataOf(methodCall).get("exceptionClass")).matches("[\\w.$]+"));

        // spec #27 ④⑤ の救済追加後は、method reference / explicit super も
        // bytecode 救済を試みてから diagnostic 化するため phase は bytecode-rescue。
        Map<String, Object> methodReference = detailByCallKind(details, "method-reference");
        assertEquals("bytecode-rescue", metadataOf(methodReference).get("resolutionPhase"));
        assertEquals("TypeExpr", metadataOf(methodReference).get("receiverKind"));
        assertEquals(Boolean.FALSE, metadataOf(methodReference).get("receiverTypeResolved"));

        Map<String, Object> objectCreation = detailByCallKind(details, "object-creation");
        assertEquals("bytecode-rescue", metadataOf(objectCreation).get("resolutionPhase"));
        assertEquals("none", metadataOf(objectCreation).get("receiverKind"));

        Map<String, Object> explicitSuper = detailByCallKind(details, "explicit-constructor-invocation");
        assertEquals("bytecode-rescue", metadataOf(explicitSuper).get("resolutionPhase"));
        assertEquals("super", metadataOf(explicitSuper).get("receiverKind"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> detailByCallKind(List<Map<String, Object>> details, String callKind) {
        return details.stream()
                .filter(d -> callKind.equals(((Map<String, Object>) d.get("metadata")).get("callKind")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no detail for callKind " + callKind + ": " + details));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadataOf(Map<String, Object> detail) {
        return (Map<String, Object>) detail.get("metadata");
    }

    @Test
    void cleanWorkspaceSucceedsWithZeroSilentOmission() throws Exception {
        write("com/example/Ok.java", """
                package com.example;
                public class Ok {
                    void run() { helper(); }
                    void helper() {}
                }
                """);

        AnalysisTestSupport.Ran ran = AnalysisTestSupport.run(
                workspace, AnalysisTestSupport.classpathMetadata(), null, null, null, null);

        assertEquals(0, ran.exitCode(), ran.stderr());
        assertTrue(ran.stderr().contains("silentOmission=0"), ran.stderr());
        assertTrue(ran.stderr().contains("emitted="), ran.stderr());
    }

    private void write(String relative, String source) throws Exception {
        Path file = workspace.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }
}
