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
        assertTrue(((Map<String, Object>) details.get(0).get("sourceLocation")).get("path")
                .equals("com/example/A.java"));
        assertTrue(((Map<String, Object>) details.get(1).get("sourceLocation")).get("path")
                .equals("com/example/B.java"));
        for (Map<String, Object> detail : details) {
            assertEquals("JAVA_UNRESOLVED_SYMBOL", detail.get("code"));
            Map<String, Object> metadata = (Map<String, Object>) detail.get("metadata");
            assertTrue(metadata.containsKey("callKind"));
            assertTrue(metadata.containsKey("reason"));
            String serialized = detail.toString();
            assertFalse(serialized.contains(workspace.toString()), "no absolute paths in details");
            assertFalse(serialized.contains("Exception"), "no raw exception text in details");
        }

        Map<String, Object> metadata = (Map<String, Object>) error.get("metadata");
        assertEquals(3, ((Number) metadata.get("total")).intValue());
        Map<String, Object> reasonCounts = (Map<String, Object>) metadata.get("reasonCounts");
        int sum = reasonCounts.values().stream().mapToInt(v -> ((Number) v).intValue()).sum();
        assertEquals(3, sum, "reasonCounts must agree with total");

        assertTrue(ran.stderr().contains("silentOmission=0") || !ran.stderr().contains("silentOmission"),
                "production stderr carries only aggregate counts");
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
