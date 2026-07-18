package com.fukuemon.depwalk.javaanalyzer.discovery;

import com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkGradleModel;
import com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkProjectModel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradleModelDiscoveryTest {

    private final ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
    private final PrintStream stderr = new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8);

    @Test
    void explicitSourceRootsBypassDiscoveryCompletely() {
        assertTrue(GradleModelDiscovery.isExplicitOverride(List.of("module-a/src/main/java")));
        assertTrue(GradleModelDiscovery.isExplicitOverride(List.of(".")));
        assertFalse(GradleModelDiscovery.isExplicitOverride(null));
    }

    @Test
    void printsFixedSafetyNoticeBeforeDiscovery() throws Exception {
        FakeToolingClient client = FakeToolingClient.healthy();

        new GradleModelDiscovery(client, stderr).discover(Path.of("/workspace"));

        String output = stderrBuffer.toString(StandardCharsets.UTF_8);
        int notice = output.indexOf(GradleModelDiscovery.SAFETY_NOTICE);
        int start = output.indexOf("depwalk: discovery phase=start");
        assertTrue(notice >= 0 && start > notice, () -> "stderr = " + output);
        assertTrue(output.contains("depwalk: discovery phase=end gradleVersion=9.6.1 projects=1 sourceRoots=1"),
                () -> "stderr = " + output);
    }

    @Test
    void rejectsUnsupportedGradleVersionWithStableReason() {
        FakeToolingClient client = FakeToolingClient.healthy();
        client.gradleVersion = "7.6.4";

        DiscoveryFailure failure = assertThrows(DiscoveryFailure.class,
                () -> new GradleModelDiscovery(client, stderr).discover(Path.of("/workspace")));

        assertEquals(DiscoveryFailure.Category.UNSUPPORTED_GRADLE_VERSION, failure.category());
        assertTrue(failure.userMessage().contains("unsupported-gradle-version"));
        assertTrue(failure.userMessage().contains("--source-root"));
        assertFalse(client.modelRequested, "unsupported version must not reach the model request");
    }

    @Test
    void rejectsUndeterminableCustomDistribution() {
        FakeToolingClient client = FakeToolingClient.healthy();
        client.gradleVersion = "acme-custom";

        DiscoveryFailure failure = assertThrows(DiscoveryFailure.class,
                () -> new GradleModelDiscovery(client, stderr).discover(Path.of("/workspace")));

        assertEquals(DiscoveryFailure.Category.UNSUPPORTED_GRADLE_VERSION, failure.category());
    }

    @Test
    void rejectsIncompatibleOrUnknownDaemonJvm() {
        FakeToolingClient incompatible = FakeToolingClient.healthy();
        incompatible.daemonJavaMajor = Optional.of(8);
        incompatible.gradleVersion = "9.6.1";
        DiscoveryFailure failure = assertThrows(DiscoveryFailure.class,
                () -> new GradleModelDiscovery(incompatible, stderr).discover(Path.of("/workspace")));
        assertEquals(DiscoveryFailure.Category.DAEMON_JVM_INCOMPATIBLE, failure.category());

        FakeToolingClient unknown = FakeToolingClient.healthy();
        unknown.daemonJavaMajor = Optional.empty();
        DiscoveryFailure unknownFailure = assertThrows(DiscoveryFailure.class,
                () -> new GradleModelDiscovery(unknown, stderr).discover(Path.of("/workspace")));
        assertEquals(DiscoveryFailure.Category.DAEMON_JVM_INCOMPATIBLE, unknownFailure.category());
    }

    @Test
    void sanitizesModelRequestFailuresToFixedMessages() {
        FakeToolingClient client = FakeToolingClient.healthy();
        client.modelFailure = new ToolingClient.ToolingRequestException(
                DiscoveryFailure.Phase.MODEL_REQUEST,
                "the target build could not provide the depwalk build model");

        DiscoveryFailure failure = assertThrows(DiscoveryFailure.class,
                () -> new GradleModelDiscovery(client, stderr).discover(Path.of("/workspace")));

        assertEquals(DiscoveryFailure.Category.MODEL_REQUEST_FAILED, failure.category());
        String output = stderrBuffer.toString(StandardCharsets.UTF_8) + failure.userMessage();
        assertFalse(output.contains("repo.internal.example"),
                "no repository URL from the underlying Gradle failure may leak");
        assertFalse(output.contains("Exception"), "no raw exception text may leak");
    }

    @Test
    void rejectsModelWithoutAnyJavaSourceRoot() {
        FakeToolingClient client = FakeToolingClient.healthy();
        client.model = FakeToolingClient.model(List.of(
                FakeToolingClient.project("/workspace", ":app", List.of(), List.of(), List.of())));

        DiscoveryFailure failure = assertThrows(DiscoveryFailure.class,
                () -> new GradleModelDiscovery(client, stderr).discover(Path.of("/workspace")));

        assertEquals(DiscoveryFailure.Category.NO_JAVA_SOURCE_ROOTS, failure.category());
    }

    @Test
    void rejectsIncompleteProviderModel() {
        FakeToolingClient client = FakeToolingClient.healthy();
        client.model = FakeToolingClient.model(List.of(new DepwalkProjectModel() {
            @Override
            public String getProjectPath() {
                return null;
            }

            @Override
            public File getProjectDirectory() {
                return new File("/workspace/app");
            }

            @Override
            public List<File> getMainJavaSourceDirectories() {
                return List.of(new File("/workspace/app/src/main/java"));
            }

            @Override
            public List<File> getMainCompileClasspath() {
                return List.of();
            }

            @Override
            public List<File> getMainClassesOutputDirectories() {
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
        }));

        DiscoveryFailure failure = assertThrows(DiscoveryFailure.class,
                () -> new GradleModelDiscovery(client, stderr).discover(Path.of("/workspace")));

        assertEquals(DiscoveryFailure.Category.PROVIDER_INCOMPATIBLE, failure.category());
    }
}
