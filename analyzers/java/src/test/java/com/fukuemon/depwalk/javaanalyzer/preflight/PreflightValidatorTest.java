package com.fukuemon.depwalk.javaanalyzer.preflight;

import com.fukuemon.depwalk.javaanalyzer.JavaErrorCode;
import com.fukuemon.depwalk.javaanalyzer.protocol.AnalysisRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreflightValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsUnsupportedLanguage() {
        AnalysisRequest request = requestWithLanguageAndMetadata("kotlin", Map.of("classpath", List.of()));

        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class, () -> PreflightValidator.validate(request));
        assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
    }

    @Test
    void rejectsMissingClasspathKey() {
        AnalysisRequest request = requestWithLanguageAndMetadata("java", Map.of());

        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class, () -> PreflightValidator.validate(request));
        assertEquals(JavaErrorCode.JAVA_MISSING_CLASSPATH, e.errorCode());
    }

    @Test
    void allowsEmptyClasspathArray() {
        AnalysisRequest request = requestWithLanguageAndMetadata("java", Map.of("classpath", List.of()));

        assertDoesNotThrow(() -> PreflightValidator.validate(request));
    }

    @Test
    void rejectsMissingJar() {
        String missingJar = tempDir.resolve("does-not-exist.jar").toString();
        AnalysisRequest request = requestWithLanguageAndMetadata("java", Map.of("classpath", List.of(missingJar)));

        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class, () -> PreflightValidator.validate(request));
        assertEquals(JavaErrorCode.JAVA_MISSING_JAR, e.errorCode());
    }

    @Test
    void allowsExistingReadableClasspathEntry() throws IOException {
        Path jar = tempDir.resolve("existing.jar");
        Files.writeString(jar, "not-a-real-jar-but-exists");
        AnalysisRequest request = requestWithLanguageAndMetadata("java", Map.of("classpath", List.of(jar.toString())));

        assertDoesNotThrow(() -> PreflightValidator.validate(request));
    }

    @Test
    void rejectsNullWorkspaceRoot() {
        AnalysisRequest request = requestWithLanguageAndMetadataAndWorkspaceRoot(
                "java", Map.of("classpath", List.of()), null);

        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class, () -> PreflightValidator.validate(request));
        assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
    }

    @Test
    void rejectsEmptyWorkspaceRoot() {
        AnalysisRequest request = requestWithLanguageAndMetadataAndWorkspaceRoot(
                "java", Map.of("classpath", List.of()), "  ");

        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class, () -> PreflightValidator.validate(request));
        assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
    }

    @Test
    void rejectsNonExistentWorkspaceRoot() {
        String missing = tempDir.resolve("does-not-exist").toString();
        AnalysisRequest request = requestWithLanguageAndMetadataAndWorkspaceRoot(
                "java", Map.of("classpath", List.of()), missing);

        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class, () -> PreflightValidator.validate(request));
        assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
    }

    @Test
    void rejectsWorkspaceRootThatIsNotADirectory() throws IOException {
        Path file = tempDir.resolve("not-a-directory");
        Files.writeString(file, "content");
        AnalysisRequest request = requestWithLanguageAndMetadataAndWorkspaceRoot(
                "java", Map.of("classpath", List.of()), file.toString());

        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class, () -> PreflightValidator.validate(request));
        assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
    }

    @Test
    void allowsExistingDirectoryWorkspaceRoot() {
        AnalysisRequest request = requestWithLanguageAndMetadata("java", Map.of("classpath", List.of()));

        assertDoesNotThrow(() -> PreflightValidator.validate(request));
    }

    @Test
    void rejectsLiftExcludePackagesThatIsNotAList() {
        AnalysisRequest request = requestWithLanguageAndMetadata(
                "java", Map.of("classpath", List.of(), "liftExcludePackages", "com.example"));

        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class, () -> PreflightValidator.validate(request));
        assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
    }

    @Test
    void rejectsLiftExcludePackagesWithNonStringElement() {
        AnalysisRequest request = requestWithLanguageAndMetadata(
                "java", Map.of("classpath", List.of(), "liftExcludePackages", List.of("com.example", 42)));

        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class, () -> PreflightValidator.validate(request));
        assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
    }

    @Test
    void allowsEmptyLiftExcludePackagesArrayAsNoExclusions() {
        AnalysisRequest request = requestWithLanguageAndMetadata(
                "java", Map.of("classpath", List.of(), "liftExcludePackages", List.of()));

        assertDoesNotThrow(() -> PreflightValidator.validate(request));
    }

    @Test
    void validateReturnsTypedClasspathForDownstreamUse() throws Exception {
        Path jar = tempDir.resolve("dep.jar");
        Files.writeString(jar, "exists");
        AnalysisRequest request = requestWithLanguageAndMetadata("java", Map.of("classpath", List.of(jar.toString())));

        PreflightValidator.Validated validated = PreflightValidator.validate(request);

        assertEquals(List.of(jar.toString()), validated.classpath());
    }

    @Test
    void allowIncompleteAnalysisDefaultsToFalseWhenKeyIsAbsent() throws Exception {
        AnalysisRequest request = requestWithLanguageAndMetadata("java", Map.of("classpath", List.of()));

        PreflightValidator.Validated validated = PreflightValidator.validate(request);

        assertFalse(validated.allowIncompleteAnalysis());
    }

    @Test
    void allowIncompleteAnalysisIsEnabledOnlyByExplicitTrueFlag() throws Exception {
        AnalysisRequest request = requestWithLanguageAndMetadata(
                "java", Map.of("classpath", List.of(), "allowIncompleteAnalysis", List.of("true")));

        PreflightValidator.Validated validated = PreflightValidator.validate(request);

        assertTrue(validated.allowIncompleteAnalysis());
    }

    private AnalysisRequest requestWithLanguageAndMetadata(String language, Map<String, Object> metadata) {
        return requestWithLanguageAndMetadataAndWorkspaceRoot(language, metadata, tempDir.toString());
    }

    private static AnalysisRequest requestWithLanguageAndMetadataAndWorkspaceRoot(
            String language, Map<String, Object> metadata, String workspaceRoot) {
        return new AnalysisRequest(
                "1",
                "analysisRequest",
                "req-1",
                workspaceRoot,
                // classpath key の必須契約は明示 sourceRoots 経路のもの (discovery 経路では任意)。
                List.of("."),
                language,
                null,
                null,
                null,
                null,
                metadata);
    }
}
