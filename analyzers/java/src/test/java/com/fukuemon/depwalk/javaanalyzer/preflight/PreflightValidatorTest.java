package com.fukuemon.depwalk.javaanalyzer.preflight;

import com.fukuemon.depwalk.javaanalyzer.JavaErrorCode;
import com.fukuemon.depwalk.javaanalyzer.protocol.AnalysisRequest;
import org.junit.jupiter.api.DisplayName;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void allowIncompleteAnalysisStaysDisabledByExplicitFalseFlag() throws Exception {
        AnalysisRequest request = requestWithLanguageAndMetadata(
                "java", Map.of("classpath", List.of(), "allowIncompleteAnalysis", List.of("false")));

        PreflightValidator.Validated validated = PreflightValidator.validate(request);

        assertFalse(validated.allowIncompleteAnalysis());
    }

    @Test
    void rejectsAllowIncompleteAnalysisWithNonCanonicalValue() {
        AnalysisRequest request = requestWithLanguageAndMetadata(
                "java", Map.of("classpath", List.of(), "allowIncompleteAnalysis", List.of("TRUE")));

        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class, () -> PreflightValidator.validate(request));
        assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
    }

    @Test
    void rejectsAllowIncompleteAnalysisWithMultipleElements() {
        AnalysisRequest request = requestWithLanguageAndMetadata(
                "java", Map.of("classpath", List.of(), "allowIncompleteAnalysis", List.of("true", "false")));

        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class, () -> PreflightValidator.validate(request));
        assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
    }

    private AnalysisRequest requestWithLanguageAndMetadata(String language, Map<String, Object> metadata) {
        return requestWithLanguageAndMetadataAndWorkspaceRoot(language, metadata, tempDir.toString());
    }

    @Test
    @DisplayName("明示 sourceRoots 経路のとき、gradleJavaHome は解釈も検証もされないままになる")
    void gradleJavaHomeIsIgnoredOnExplicitSourceRoots() {
        // 明示 root は Gradle runtime を一切通らないため、この経路では key を
        // 解釈しない (検証もしない)。
        AnalysisRequest request = requestWithLanguageAndMetadata("java", Map.of(
                "classpath", List.of(),
                "gradleJavaHome", List.of("/no/such/home")));

        PreflightValidator.Validated validated =
                assertDoesNotThrow(() -> PreflightValidator.validate(request));
        assertNull(validated.gradleJavaHome());
    }

    @Test
    @DisplayName("discovery 経路で起動可能な java home を渡すとき、gradleJavaHome として受理される")
    void gradleJavaHomeAcceptsLaunchableJavaHomeOnDiscovery() throws IOException {
        Path javaHome = tempDir.resolve("jdk");
        Files.createDirectories(javaHome.resolve("bin"));
        Path javaBinary = javaHome.resolve("bin").resolve("java");
        Files.writeString(javaBinary, "");
        assertTrue(javaBinary.toFile().setExecutable(true));

        AnalysisRequest request = discoveryRequestWithMetadata(Map.of(
                "gradleJavaHome", List.of(javaHome.toString())));

        PreflightValidator.Validated validated =
                assertDoesNotThrow(() -> PreflightValidator.validate(request));
        assertEquals(javaHome, validated.gradleJavaHome());
    }

    @Test
    @DisplayName("discovery 経路で起動不能な path を渡すとき、JAVA_INVALID_REQUEST で拒否される")
    void gradleJavaHomeRejectsNonLaunchablePathOnDiscovery() {
        AnalysisRequest request = discoveryRequestWithMetadata(Map.of(
                "gradleJavaHome", List.of(tempDir.resolve("missing").toString())));

        AnalyzerFatalException e =
                assertThrows(AnalyzerFatalException.class, () -> PreflightValidator.validate(request));
        assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
    }

    @Test
    @DisplayName("discovery 経路で空白・非文字列・空配列の gradleJavaHome を渡すとき、いずれも拒否される")
    void gradleJavaHomeRejectsBlankAndNonStringAndEmptyOnDiscovery() {
        for (Object value : List.of(List.of(" "), List.of(42), List.of())) {
            AnalysisRequest request = discoveryRequestWithMetadata(Map.of("gradleJavaHome", value));
            AnalyzerFatalException e =
                    assertThrows(AnalyzerFatalException.class, () -> PreflightValidator.validate(request));
            assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode(), String.valueOf(value));
        }
    }

    @Test
    @DisplayName("discovery 経路で gradleJavaHome の要素数が 1 でないとき、拒否される")
    void gradleJavaHomeRejectsWrongElementCountOnDiscovery() {
        AnalysisRequest request = discoveryRequestWithMetadata(Map.of(
                "gradleJavaHome", List.of("/a", "/b")));

        AnalyzerFatalException e =
                assertThrows(AnalyzerFatalException.class, () -> PreflightValidator.validate(request));
        assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
    }

    /** 自動 discovery 経路 (sourceRoots 未指定) の request。 */
    private AnalysisRequest discoveryRequestWithMetadata(Map<String, Object> metadata) {
        return new AnalysisRequest(
                "1",
                "analysisRequest",
                "req-1",
                tempDir.toString(),
                null,
                "java",
                null,
                null,
                null,
                null,
                metadata);
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
