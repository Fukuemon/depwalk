package com.fukuemon.depwalk.javaanalyzer.analysis.context;

import com.fukuemon.depwalk.javaanalyzer.JavaErrorCode;
import com.fukuemon.depwalk.javaanalyzer.preflight.AnalyzerFatalException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisContextFactoryTest {

    @TempDir
    Path workspace;

    private static Map<String, Object> metadata(String level) {
        return Map.of("classpath", List.of(), "javaLanguageLevel", List.of(level));
    }

    @Test
    void buildsSyntheticContextFromExplicitRoots() throws Exception {
        Files.createDirectories(workspace.resolve("module-a/src/main/java"));
        Files.createDirectories(workspace.resolve("module-b/src/main/java"));

        AnalysisContextFactory.Result result = AnalysisContextFactory.explicitContext(
                workspace,
                List.of("module-a/src/main/java", "module-b/src/main/java"),
                List.of(),
                metadata("17"));

        assertEquals(1, result.contexts().size());
        SourceSetAnalysisContext context = result.contexts().get(0);
        assertEquals(SourceSetAnalysisContext.EXPLICIT_CONTEXT_ID, context.id());
        assertEquals(2, context.sourceRoots().size());
        assertTrue(context.sootUpUnavailable());
        assertTrue(result.warnings().stream()
                .anyMatch(w -> "JAVA_SOOTUP_UNAVAILABLE".equals(w.code())));
    }

    @Test
    void deduplicatesIdenticalExplicitRootsKeepingTheFirst() throws Exception {
        Files.createDirectories(workspace.resolve("src/main/java"));

        AnalysisContextFactory.Result result = AnalysisContextFactory.explicitContext(
                workspace,
                List.of("src/main/java", "src/main/java"),
                List.of(),
                metadata("17"));

        assertEquals(1, result.contexts().get(0).sourceRoots().size());
    }

    @Test
    void rejectsContainedExplicitRoots() throws Exception {
        Files.createDirectories(workspace.resolve("module/src/main/java"));

        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class,
                () -> AnalysisContextFactory.explicitContext(
                        workspace, List.of(".", "module/src/main/java"), List.of(), metadata("17")));
        assertEquals(JavaErrorCode.JAVA_INVALID_SOURCE_ROOTS, e.errorCode());
    }

    @Test
    void rejectsRootResolvingOutsideWorkspaceViaSymlink() throws Exception {
        Path outside = Files.createTempDirectory("depwalk-outside");
        Path link = workspace.resolve("linked");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            return; // platform without symlink support
        }

        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class,
                () -> AnalysisContextFactory.explicitContext(
                        workspace, List.of("linked"), List.of(), metadata("17")));
        assertEquals(JavaErrorCode.JAVA_INVALID_SOURCE_ROOTS, e.errorCode());
    }

    @Test
    void rejectsInvalidExplicitRootShapes() {
        for (String bad : List.of("", "a\\b", "/abs", "../up")) {
            AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class,
                    () -> AnalysisContextFactory.explicitContext(
                            workspace, List.of(bad), List.of(), metadata("17")),
                    "root should be rejected: " + bad);
            assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
        }
    }

    @Test
    void rejectsExplicitEmptySourceRoots() {
        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class,
                () -> AnalysisContextFactory.explicitContext(
                        workspace, List.of(), List.of(), metadata("17")));
        assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
    }

    @Test
    void rejectsNonCanonicalOrUnsupportedLanguageLevels() {
        for (String bad : List.of("1.8", "", "08", "banana", "999")) {
            AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class,
                    () -> AnalysisContextFactory.explicitContext(
                            workspace, List.of("."), List.of(), metadata(bad)),
                    "level should be rejected: " + bad);
            assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
        }
    }

    @Test
    void rejectsInvalidPreviewValues() {
        Map<String, Object> metadata = Map.of(
                "classpath", List.of(),
                "javaLanguageLevel", List.of("17"),
                "javaPreview", List.of("yes"));
        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class,
                () -> AnalysisContextFactory.explicitContext(workspace, List.of("."), List.of(), metadata));
        assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
    }

    @Test
    void rejectsLanguageMetadataOnDiscoveryRoute() {
        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class,
                () -> AnalysisContextFactory.rejectLanguageMetadataOnDiscovery(
                        Map.of("javaLanguageLevel", List.of("17"))));
        assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
    }

    @Test
    void resolvesCanonicalLevelsOnly() {
        assertTrue(LanguageLevels.resolve("17", false).isPresent());
        assertTrue(LanguageLevels.resolve("8", false).isPresent());
        assertTrue(LanguageLevels.resolve("1.8", false).isEmpty());
        assertTrue(LanguageLevels.resolve(null, false).isEmpty());
    }
}
