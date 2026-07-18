package com.fukuemon.depwalk.javaanalyzer.discovery;

import com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkGradleModel;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Tooling API 実装。Gradle operation の standard output / error は明示的な
 * discard sink へ接続し、Gradle 由来の例外は raw message / stack trace を
 * 持たない {@link ToolingRequestException} の固定 message へ sanitize する。
 */
public final class GradleToolingClient implements ToolingClient {

    private final String forcedGradleVersion;

    /** 通常経路: wrapper があれば build distribution、なければ同梱 version。 */
    public GradleToolingClient() {
        this(null);
    }

    /**
     * cross-version matrix test 用に target Gradle version を強制する
     * (wrapper 判定を行わない)。production 経路では使用しない。
     *
     * @param forcedGradleVersion 強制する Gradle version。{@code null} なら通常経路
     */
    public GradleToolingClient(String forcedGradleVersion) {
        this.forcedGradleVersion = forcedGradleVersion;
    }

    @Override
    public BuildEnvironmentInfo buildEnvironment(Path workspaceRoot) throws ToolingRequestException {
        try (ProjectConnection connection = connect(workspaceRoot)) {
            BuildEnvironment environment = connection.model(BuildEnvironment.class)
                    .setStandardOutput(OutputStream.nullOutputStream())
                    .setStandardError(OutputStream.nullOutputStream())
                    .get();
            String gradleVersion = environment.getGradle().getGradleVersion();
            Optional<Integer> daemonJavaMajor =
                    javaMajorFromJavaHome(environment.getJava().getJavaHome());
            return new BuildEnvironmentInfo(gradleVersion, daemonJavaMajor);
        } catch (RuntimeException e) {
            throw new ToolingRequestException(
                    DiscoveryFailure.Phase.CONNECT,
                    "could not read the target build environment through the Tooling API");
        }
    }

    @Override
    public DepwalkGradleModel model(Path workspaceRoot, Path initScript) throws ToolingRequestException {
        try (ProjectConnection connection = connect(workspaceRoot)) {
            return connection.model(DepwalkGradleModel.class)
                    .withArguments("--init-script", initScript.toString())
                    .setStandardOutput(OutputStream.nullOutputStream())
                    .setStandardError(OutputStream.nullOutputStream())
                    .get();
        } catch (RuntimeException e) {
            throw new ToolingRequestException(
                    DiscoveryFailure.Phase.MODEL_REQUEST,
                    "the target build could not provide the depwalk build model");
        }
    }

    private ProjectConnection connect(Path workspaceRoot) {
        GradleConnector connector = GradleConnector.newConnector()
                .forProjectDirectory(workspaceRoot.toFile());
        if (forcedGradleVersion != null) {
            connector.useGradleVersion(forcedGradleVersion);
        } else if (hasWrapper(workspaceRoot)) {
            connector.useBuildDistribution();
        } else {
            connector.useGradleVersion(GradleVersionSupport.BUNDLED_GRADLE_VERSION);
        }
        return connector.connect();
    }

    private static boolean hasWrapper(Path workspaceRoot) {
        return Files.isRegularFile(
                workspaceRoot.resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties"));
    }

    /**
     * daemon JVM の Java major を java home の {@code release} file から判定
     * する。JVM を起動せず、判定不能なら empty を返す。
     */
    static Optional<Integer> javaMajorFromJavaHome(File javaHome) {
        if (javaHome == null) {
            return Optional.empty();
        }
        Path release = javaHome.toPath().resolve("release");
        try {
            for (String line : Files.readAllLines(release)) {
                if (!line.startsWith("JAVA_VERSION=")) {
                    continue;
                }
                String value = line.substring("JAVA_VERSION=".length()).replace("\"", "").trim();
                return parseJavaMajor(value);
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    static Optional<Integer> parseJavaMajor(String javaVersion) {
        if (javaVersion == null || javaVersion.isEmpty()) {
            return Optional.empty();
        }
        String[] parts = javaVersion.split("\\.");
        try {
            int first = Integer.parseInt(parts[0].replaceAll("[^0-9].*$", ""));
            if (first == 1 && parts.length > 1) {
                return Optional.of(Integer.parseInt(parts[1]));
            }
            return Optional.of(first);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
