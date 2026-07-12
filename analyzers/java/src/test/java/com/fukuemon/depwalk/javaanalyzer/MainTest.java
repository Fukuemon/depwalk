package com.fukuemon.depwalk.javaanalyzer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MainTest {

    @Test
    void preflightPassWithEmptyClasspathProducesZeroRecordsAndExitZero(@TempDir Path emptyWorkspace) {
        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"" + jsonPath(emptyWorkspace) + "\","
                + "\"language\":\"java\",\"metadata\":{\"classpath\":[]}}";

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = Main.run(inputStream(request), stdout, stderr);

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(StandardCharsets.UTF_8).isEmpty(), "empty workspace has no scope files to analyze");
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
    void unknownFieldsInRequestAreIgnored(@TempDir Path emptyWorkspace) {
        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"" + jsonPath(emptyWorkspace) + "\","
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

    @Test
    void runtimeExceptionDuringAnalysisProducesInternalErrorRecordAndNonZeroExit(@TempDir Path workspace) throws IOException {
        // H1: pre-flight (M4) now validates workspaceRoot up front, so a nonexistent workspaceRoot
        // is rejected there (JAVA_INVALID_REQUEST) rather than reaching the analysis phase. To still
        // exercise an uncaught RuntimeException surviving from *inside* AnalysisRunner (e.g. an
        // UncheckedIOException from file enumeration), make workspaceRoot pass pre-flight (it exists
        // and is a directory) but unreadable, so Files.walk() inside ScopeFiles fails after pre-flight.
        Path unreadableDir = Files.createDirectory(workspace.resolve("unreadable"));
        Files.writeString(unreadableDir.resolve("Ok.java"), "package com.example; class Ok { }");
        boolean permissionChangeApplied = unreadableDir.toFile().setReadable(false, false);
        assumeTrue(permissionChangeApplied, "platform does not support removing read permission");
        boolean actuallyUnreadable = !isDirectoryListable(unreadableDir);
        assumeTrue(actuallyUnreadable, "running as a user unaffected by permission bits (e.g. root); cannot reproduce");

        try {
            String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                    + "\"requestId\":\"req-1\",\"workspaceRoot\":\"" + jsonPath(unreadableDir) + "\","
                    + "\"language\":\"java\",\"metadata\":{\"classpath\":[]}}";

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            int exitCode = Main.run(inputStream(request), stdout, stderr);

            assertEquals(1, exitCode);
            String stdoutContent = stdout.toString(StandardCharsets.UTF_8);
            assertTrue(stdoutContent.contains("\"recordType\":\"error\""));
            assertTrue(stdoutContent.contains("JAVA_INTERNAL_ERROR"));
            String stderrContent = stderr.toString(StandardCharsets.UTF_8);
            assertFalse(stderrContent.isBlank(), "stderr should contain exception class name and message");
        } finally {
            unreadableDir.toFile().setReadable(true, false);
        }
    }

    @Test
    void ioExceptionDuringOutputWriteIsReportedOnStderrAndReturnsNonZeroExit() {
        // language=java, no metadata -> triggers a JAVA_MISSING_CLASSPATH error record write attempt.
        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"/workspace/depwalk\","
                + "\"language\":\"java\"}";

        java.io.OutputStream brokenOut = new java.io.OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("simulated broken stdout");
            }
        };
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = Main.run(inputStream(request), brokenOut, stderr);

        assertEquals(1, exitCode);
        assertFalse(stderr.toString(StandardCharsets.UTF_8).isBlank(),
                "stderr must describe the output write failure instead of silently exiting");
    }

    private static boolean isDirectoryListable(Path dir) {
        try (var stream = Files.newDirectoryStream(dir)) {
            stream.iterator().hasNext();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static ByteArrayInputStream inputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonPath(Path path) {
        return path.toAbsolutePath().toString().replace("\\", "\\\\");
    }
}
