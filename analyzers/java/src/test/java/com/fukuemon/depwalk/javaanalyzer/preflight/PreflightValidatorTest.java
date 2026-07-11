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
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private static AnalysisRequest requestWithLanguageAndMetadata(String language, Map<String, Object> metadata) {
        return new AnalysisRequest(
                "1",
                "analysisRequest",
                "req-1",
                "/workspace/depwalk",
                language,
                null,
                null,
                null,
                null,
                metadata);
    }
}
