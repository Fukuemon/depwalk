package com.fukuemon.depwalk.javaanalyzer.discovery;

import com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkGradleModel;
import com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkProjectModel;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** discovery unit test 用の Tooling API fake。 */
final class FakeToolingClient implements ToolingClient {

    String gradleVersion = "9.6.1";
    Optional<Integer> daemonJavaMajor = Optional.of(25);
    DepwalkGradleModel model;
    ToolingRequestException environmentFailure;
    ToolingRequestException modelFailure;
    boolean modelRequested;

    static FakeToolingClient healthy() {
        FakeToolingClient client = new FakeToolingClient();
        client.model = model(List.of(project(
                "/workspace/app",
                ":app",
                List.of(new File("/workspace/app/src/main/java")),
                List.of(new File("/cache/lib.jar")),
                List.of(new File("/workspace/app/build/classes/java/main")))));
        return client;
    }

    @Override
    public BuildEnvironmentInfo buildEnvironment(Path workspaceRoot) throws ToolingRequestException {
        if (environmentFailure != null) {
            throw environmentFailure;
        }
        return new BuildEnvironmentInfo(gradleVersion, daemonJavaMajor);
    }

    @Override
    public DepwalkGradleModel model(Path workspaceRoot, Path initScript) throws ToolingRequestException {
        modelRequested = true;
        if (modelFailure != null) {
            throw modelFailure;
        }
        return model;
    }

    static DepwalkGradleModel model(List<DepwalkProjectModel> projects) {
        return new DepwalkGradleModel() {
            @Override
            public File getBuildRootDirectory() {
                return new File("/workspace");
            }

            @Override
            public List<? extends DepwalkProjectModel> getProjects() {
                return projects;
            }

            @Override
            public List<String> getExcludedSourceSetNames() {
                return List.of("test");
            }

            @Override
            public int getExcludedSourceSetCount() {
                return 1;
            }
        };
    }

    static DepwalkProjectModel project(
            String directory,
            String path,
            List<File> sourceDirectories,
            List<File> classpath,
            List<File> classesOutputs) {
        return new DepwalkProjectModel() {
            @Override
            public String getProjectPath() {
                return path;
            }

            @Override
            public File getProjectDirectory() {
                return new File(directory);
            }

            @Override
            public List<File> getMainJavaSourceDirectories() {
                return sourceDirectories;
            }

            @Override
            public List<File> getMainCompileClasspath() {
                return classpath;
            }

            @Override
            public List<File> getMainClassesOutputDirectories() {
                return classesOutputs;
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
