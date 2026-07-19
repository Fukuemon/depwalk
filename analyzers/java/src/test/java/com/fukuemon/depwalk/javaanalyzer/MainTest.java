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
                + "\"language\":\"java\",\"sourceRoots\":[\".\"],"
                + "\"metadata\":{\"classpath\":[],\"javaLanguageLevel\":[\"25\"]}}";

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = Main.run(inputStream(request), stdout, stderr);

        assertEquals(0, exitCode);
        String stdoutContent = stdout.toString(StandardCharsets.UTF_8);
        // 明示経路で classes output が無い場合の source-only warning だけを許可する。
        for (String line : stdoutContent.split("\n")) {
            if (!line.isBlank()) {
                assertTrue(line.contains("JAVA_SOOTUP_UNAVAILABLE"),
                        "empty workspace must emit no graph records: " + line);
            }
        }
        assertFalse(stderr.toString(StandardCharsets.UTF_8).isBlank(), "stderr should contain the metrics summary");
    }

    @Test
    void missingClasspathKeyProducesErrorRecordAndNonZeroExit() {
        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"/workspace/depwalk\","
                + "\"sourceRoots\":[\".\"],\"language\":\"java\"}";

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
                + "\"language\":\"kotlin\",\"sourceRoots\":[\".\"],"
                + "\"metadata\":{\"classpath\":[],\"javaLanguageLevel\":[\"25\"]}}";

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
                + "\"language\":\"java\",\"sourceRoots\":[\".\"],"
                + "\"metadata\":{\"classpath\":[\"/does/not/exist.jar\"],\"javaLanguageLevel\":[\"25\"]}}";

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
                + "\"language\":\"java\",\"sourceRoots\":[\".\"],"
                + "\"metadata\":{\"classpath\":[],\"javaLanguageLevel\":[\"25\"]},\"unknownField\":true}";

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = Main.run(inputStream(request), stdout, stderr);

        assertEquals(0, exitCode);
        // 空 workspace では graph record を出さない (unknown field が余分な
        // record 出力へ影響しないことを exit code だけでなく stdout でも固定)。
        for (String line : stdout.toString(StandardCharsets.UTF_8).split("\n")) {
            if (!line.isBlank()) {
                assertTrue(line.contains("\"recordType\":\"diagnostic\""),
                        "unexpected non-diagnostic record: " + line);
            }
        }
    }

    @Test
    void stderrNeverContainsProtocolRecords() {
        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"/workspace/depwalk\","
                + "\"sourceRoots\":[\".\"],\"language\":\"java\"}"; // triggers JAVA_MISSING_CLASSPATH error on stdout

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        Main.run(inputStream(request), stdout, stderr);

        assertFalse(stderr.toString(StandardCharsets.UTF_8).contains("\"recordType\""));
    }

    @Test
    void unreadableSourceRootIsRejectedBeforeAnalysis(@TempDir Path workspace) throws IOException {
        // 読取不能な明示 source root は、解析へ入る前の root 正規化
        // (AnalysisContextFactory) が JAVA_INVALID_SOURCE_ROOTS で決定的に拒否する。
        Path unreadableDir = Files.createDirectory(workspace.resolve("unreadable"));
        Files.writeString(unreadableDir.resolve("Ok.java"), "package com.example; class Ok { }");
        boolean permissionChangeApplied = unreadableDir.toFile().setReadable(false, false);
        assumeTrue(permissionChangeApplied, "platform does not support removing read permission");
        boolean actuallyUnreadable = !isDirectoryListable(unreadableDir);
        assumeTrue(actuallyUnreadable, "running as a user unaffected by permission bits (e.g. root); cannot reproduce");

        try {
            String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                    + "\"requestId\":\"req-1\",\"workspaceRoot\":\"" + jsonPath(unreadableDir) + "\","
                    + "\"language\":\"java\",\"sourceRoots\":[\".\"],"
                + "\"metadata\":{\"classpath\":[],\"javaLanguageLevel\":[\"25\"]}}";

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            int exitCode = Main.run(inputStream(request), stdout, stderr);

            assertEquals(1, exitCode);
            String stdoutContent = stdout.toString(StandardCharsets.UTF_8);
            assertTrue(stdoutContent.contains("\"recordType\":\"error\""));
            assertTrue(stdoutContent.contains("JAVA_INVALID_SOURCE_ROOTS"));
        } finally {
            unreadableDir.toFile().setReadable(true, false);
        }
    }

    @Test
    void ioExceptionDuringOutputWriteIsReportedOnStderrAndReturnsNonZeroExit() {
        // 明示 sourceRoots + metadata なし -> JAVA_MISSING_CLASSPATH の error record 書込を誘発する。
        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"/workspace/depwalk\","
                + "\"sourceRoots\":[\".\"],\"language\":\"java\"}";

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

    @Test
    void ioExceptionDuringAnalysisSetupProducesInternalErrorRecordAndNonZeroExit(
            @TempDir Path workspace, @TempDir Path classpathDir) throws IOException {
        // pre-flight only checks existence / readability (not zip validity), so a garbage-bytes file
        // named *.jar passes pre-flight but fails when JarTypeSolver's constructor tries to open it as
        // a zip during AnalysisRunner's setup (TypeSolverFactory.create).
        Path corruptJar = classpathDir.resolve("corrupt.jar");
        Files.write(corruptJar, new byte[] {0x00, 0x01, 0x02, 0x03});
        Files.writeString(workspace.resolve("Ok.java"), "package com.example; class Ok { }");

        String request = "{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                + "\"requestId\":\"req-1\",\"workspaceRoot\":\"" + jsonPath(workspace) + "\","
                + "\"language\":\"java\",\"sourceRoots\":[\".\"],"
                + "\"metadata\":{\"classpath\":[\"" + jsonPath(corruptJar) + "\"],\"javaLanguageLevel\":[\"25\"]}}";

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = Main.run(inputStream(request), stdout, stderr);

        assertEquals(1, exitCode);
        String stdoutContent = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(stdoutContent.contains("\"recordType\":\"error\""),
                "IOException during analysis setup must still produce an error record: " + stdoutContent);
        assertTrue(stdoutContent.contains("JAVA_INTERNAL_ERROR"));
        assertFalse(stderr.toString(StandardCharsets.UTF_8).isBlank(),
                "stderr should contain exception class name and message");
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
