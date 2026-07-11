package com.fukuemon.depwalk.javaanalyzer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    void preflightPassWithEmptyClasspathProducesZeroRecordsAndExitZero() {
        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"/workspace/depwalk\","
                + "\"language\":\"java\",\"metadata\":{\"classpath\":[]}}";

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = Main.run(inputStream(request), stdout, stderr);

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(StandardCharsets.UTF_8).isEmpty(), "no protocol records expected yet (P2_01 responsibility)");
        assertFalse(stderr.toString(StandardCharsets.UTF_8).isBlank(), "stderr should contain the metrics summary");
    }

    @Test
    void missingClasspathKeyProducesErrorRecordAndNonZeroExit() {
        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"/workspace/depwalk\","
                + "\"language\":\"java\"}";

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = Main.run(inputStream(request), stdout, stderr);

        assertEquals(1, exitCode);
        String stdoutContent = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(stdoutContent.contains("\"recordType\":\"error\""));
        assertTrue(stdoutContent.contains("JAVA_MISSING_CLASSPATH"));
    }

    @Test
    void unsupportedLanguageProducesInvalidRequestErrorAndNonZeroExit() {
        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"/workspace/depwalk\","
                + "\"language\":\"kotlin\",\"metadata\":{\"classpath\":[]}}";

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = Main.run(inputStream(request), stdout, stderr);

        assertEquals(1, exitCode);
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("JAVA_INVALID_REQUEST"));
    }

    @Test
    void missingJarProducesMissingJarErrorAndNonZeroExit() {
        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"/workspace/depwalk\","
                + "\"language\":\"java\",\"metadata\":{\"classpath\":[\"/does/not/exist.jar\"]}}";

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = Main.run(inputStream(request), stdout, stderr);

        assertEquals(1, exitCode);
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("JAVA_MISSING_JAR"));
    }

    @Test
    void unknownFieldsInRequestAreIgnored() {
        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"/workspace/depwalk\","
                + "\"language\":\"java\",\"metadata\":{\"classpath\":[]},\"unknownField\":true}";

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = Main.run(inputStream(request), stdout, stderr);

        assertEquals(0, exitCode);
    }

    @Test
    void stderrNeverContainsProtocolRecords() {
        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"/workspace/depwalk\","
                + "\"language\":\"java\"}"; // triggers JAVA_MISSING_CLASSPATH error on stdout

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        Main.run(inputStream(request), stdout, stderr);

        assertFalse(stderr.toString(StandardCharsets.UTF_8).contains("\"recordType\""));
    }

    private static ByteArrayInputStream inputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
