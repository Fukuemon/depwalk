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
    void rejectsUnparseableSourceRootAsInvalidRequest() {
        // NUL を含む値は InvalidPathException を漏らさず invalid request にする。
        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class,
                () -> AnalysisContextFactory.explicitContext(
                        workspace, List.of("src\0bad"), List.of(), metadata("17")));
        assertEquals(JavaErrorCode.JAVA_INVALID_REQUEST, e.errorCode());
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

    @Test
    void skipsUnbuiltWorkspaceClasspathEntriesWithAWarning() throws Exception {
        // model 取得は task を実行しないため、workspace 内の project 依存 build
        // output は fresh checkout で存在しない。fatal でなく warning で除外する。
        Files.createDirectories(workspace.resolve("app/src/main/java"));
        Path unbuilt = workspace.resolve("service/build/libs/service.jar");

        AnalysisContextFactory.Result result = AnalysisContextFactory.discoveredContexts(
                workspace,
                model(project(":app", workspace.resolve("app"),
                        List.of(workspace.resolve("app/src/main/java")), List.of(unbuilt))),
                List.of());

        assertEquals(1, result.contexts().size());
        assertTrue(result.contexts().get(0).classpath().stream().noneMatch(unbuilt::equals));
        assertTrue(result.warnings().stream()
                .anyMatch(w -> "JAVA_SOOTUP_UNAVAILABLE".equals(w.code())
                        && w.message().contains("unbuilt")));
    }

    @Test
    void failsOnMissingExternalClasspathArtifacts() throws Exception {
        Files.createDirectories(workspace.resolve("app/src/main/java"));
        Path external = workspace.getParent().resolve("depwalk-missing-external.jar");

        AnalyzerFatalException e = assertThrows(AnalyzerFatalException.class,
                () -> AnalysisContextFactory.discoveredContexts(
                        workspace,
                        model(project(":app", workspace.resolve("app"),
                                List.of(workspace.resolve("app/src/main/java")), List.of(external))),
                        List.of()));
        assertEquals(JavaErrorCode.JAVA_MISSING_JAR, e.errorCode());
    }

    @Test
    void warnsForEachExcludedIncludedBuild() throws Exception {
        Files.createDirectories(workspace.resolve("app/src/main/java"));
        Path includedBuild = workspace.resolve("tooling-build");
        Files.createDirectories(includedBuild);

        var base = model(project(":app", workspace.resolve("app"),
                List.of(workspace.resolve("app/src/main/java")), List.of()));
        var withIncluded = new com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkGradleModel() {
            @Override
            public java.io.File getBuildRootDirectory() {
                return base.getBuildRootDirectory();
            }

            @Override
            public List<? extends com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkProjectModel> getProjects() {
                return base.getProjects();
            }

            @Override
            public List<String> getExcludedSourceSetNames() {
                return List.of();
            }

            @Override
            public int getExcludedSourceSetCount() {
                return 0;
            }

            @Override
            public List<java.io.File> getIncludedBuildRootDirectories() {
                return List.of(includedBuild.toFile());
            }
        };

        AnalysisContextFactory.Result result =
                AnalysisContextFactory.discoveredContexts(workspace, withIncluded, List.of());

        assertTrue(result.warnings().stream()
                .anyMatch(w -> "JAVA_SOURCE_ROOT_EXCLUDED".equals(w.code())
                        && w.message().contains("included build tooling-build")
                        && w.message().contains("--source-root")));
    }

    private com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkGradleModel model(
            com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkProjectModel... projects) {
        return new com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkGradleModel() {
            @Override
            public java.io.File getBuildRootDirectory() {
                return workspace.toFile();
            }

            @Override
            public List<? extends com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkProjectModel> getProjects() {
                return List.of(projects);
            }

            @Override
            public List<String> getExcludedSourceSetNames() {
                return List.of();
            }

            @Override
            public int getExcludedSourceSetCount() {
                return 0;
            }
        };
    }

    private static com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkProjectModel project(
            String path, Path projectDir, List<Path> sourceDirs, List<Path> classpath) {
        return new com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkProjectModel() {
            @Override
            public String getProjectPath() {
                return path;
            }

            @Override
            public java.io.File getProjectDirectory() {
                return projectDir.toFile();
            }

            @Override
            public List<java.io.File> getMainJavaSourceDirectories() {
                return sourceDirs.stream().map(Path::toFile).toList();
            }

            @Override
            public List<java.io.File> getMainCompileClasspath() {
                return classpath.stream().map(Path::toFile).toList();
            }

            @Override
            public List<java.io.File> getMainClassesOutputDirectories() {
                return List.of();
            }

            @Override
            public List<String> getProjectDependencyPaths() {
                return List.of();
            }

            @Override
            public String getSourceLanguageLevel() {
                return "17";
            }

            @Override
            public boolean isPreviewEnabled() {
                return false;
            }
        };
    }
}
