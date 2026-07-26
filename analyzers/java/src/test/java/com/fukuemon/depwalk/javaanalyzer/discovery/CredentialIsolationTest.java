package com.fukuemon.depwalk.javaanalyzer.discovery;

import com.fukuemon.depwalk.javaanalyzer.Main;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * credential 非漏洩と副作用境界の negative test
 * (java-analyzer feature doc「Gradle runtime と安全境界」/
 * adr/0006-adopt-gradle-tooling-api-discovery.md)。
 * 高 entropy dummy marker を run ごとに生成して build logic から意図的に出力させ、
 * depwalk が生成・転送する output へ byte 一致で現れないことを検証する。
 * marker を含む test 入力 (fixture copy / gradle.properties) 自体は検査対象外。
 * arbitrary build logic の外部副作用の sandbox 保証はしない。
 */
class CredentialIsolationTest {

    private static String newMarker() {
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        return "DEPWALK-DUMMY-" + HexFormat.of().formatHex(random);
    }

    private record Ran(int exitCode, String stdout, String stderr) {
    }

    private Ran runAnalyzer(String requestJson) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit = Main.run(
                new ByteArrayInputStream(requestJson.getBytes(StandardCharsets.UTF_8)), stdout, stderr);
        return new Ran(exit,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private Path markerWorkspace(String marker, boolean failConfiguration) throws Exception {
        Path workspace = Files.createTempDirectory("depwalk-credential-").toRealPath();
        Path src = workspace.resolve("src/main/java/com/example/leak");
        Files.createDirectories(src);
        Files.writeString(src.resolve("App.java"),
                "package com.example.leak;\npublic class App { void run() { helper(); } void helper() {} }\n");
        Files.writeString(workspace.resolve("settings.gradle"), "rootProject.name = 'leak-fixture'\n");
        // marker は fixture source へ固定せず、test 専用 Gradle property から注入する。
        Files.writeString(workspace.resolve("gradle.properties"), "depwalkTestMarker=" + marker + "\n");
        String failLine = failConfiguration
                ? "throw new GradleException('configuration failed with credential ' + depwalkMarker)\n"
                : "";
        Files.writeString(workspace.resolve("build.gradle"),
                "apply plugin: 'java'\n"
                        + "def depwalkMarker = providers.gradleProperty('depwalkTestMarker').getOrElse('none')\n"
                        + "println('leak-stdout credential=' + depwalkMarker)\n"
                        + "System.err.println('leak-stderr credential=' + depwalkMarker)\n"
                        + "logger.lifecycle('leak-logger credential=' + depwalkMarker)\n"
                        + failLine);
        return workspace;
    }

    /**
     * depwalk が生成し得る artifact へ marker が残っていないことを検証する。
     * marker を含む test 入力 (gradle.properties / build.gradle) は検査対象外。
     */
    private static void assertNoMarkerInGeneratedArtifacts(Path workspace, String marker) throws Exception {
        // 検査除外は workspace 直下の既知 test 入力 2 file に限定する (同名の
        // 生成物が deeper path に現れた場合は漏洩として検出する)。
        var knownInputs = java.util.Set.of(
                workspace.resolve("gradle.properties"), workspace.resolve("build.gradle"));
        try (Stream<Path> paths = Files.walk(workspace)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (knownInputs.contains(path)) {
                    continue;
                }
                byte[] content = Files.readAllBytes(path);
                assertFalse(new String(content, StandardCharsets.ISO_8859_1).contains(marker),
                        "marker leaked into a generated artifact: " + workspace.relativize(path));
            }
        }
    }

    /** JSON string へ埋め込む path を escape する (Windows の backslash 対策)。 */
    private static String jsonPath(Path path) {
        return path.toAbsolutePath().toString().replace("\\", "\\\\");
    }

    private static void deleteRecursively(Path root) throws Exception {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void discoveryNeverForwardsInjectedMarkerBytes() throws Exception {
        String marker = newMarker();
        Path workspace = markerWorkspace(marker, false);
        try {
            Ran ran = runAnalyzer("{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                    + "\"requestId\":\"leak-ok\",\"workspaceRoot\":\"" + jsonPath(workspace) + "\",\"language\":\"java\"}");

            assertEquals(0, ran.exitCode(), ran.stderr());
            assertFalse(ran.stdout().contains(marker), "marker leaked to Protocol stdout");
            assertFalse(ran.stderr().contains(marker), "marker leaked to Analyzer stderr");
            // 固定安全通知と discovery の固定行は残る。
            assertTrue(ran.stderr().contains(GradleModelDiscovery.SAFETY_NOTICE), ran.stderr());
            assertTrue(ran.stderr().contains("discovery phase=end"), ran.stderr());
            assertNoMarkerInGeneratedArtifacts(workspace, marker);
        } finally {
            deleteRecursively(workspace);
        }
    }

    @Test
    void discoveryFailureSanitizesMarkerFromErrorOutput() throws Exception {
        String marker = newMarker();
        Path workspace = markerWorkspace(marker, true);
        try {
            Ran ran = runAnalyzer("{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                    + "\"requestId\":\"leak-fail\",\"workspaceRoot\":\"" + jsonPath(workspace) + "\",\"language\":\"java\"}");

            assertEquals(1, ran.exitCode());
            assertFalse(ran.stdout().contains(marker), "marker leaked to the error record");
            assertFalse(ran.stderr().contains(marker), "marker leaked to Analyzer stderr");
            // 安定 category / phase / 明示 override 案内は残る。
            assertTrue(ran.stdout().contains("JAVA_GRADLE_MODEL_ERROR"), ran.stdout());
            assertTrue(ran.stdout().contains("--source-root"), ran.stdout());
            assertTrue(ran.stdout().contains("\"phase\""), ran.stdout());
            assertNoMarkerInGeneratedArtifacts(workspace, marker);
        } finally {
            deleteRecursively(workspace);
        }
    }

    @Test
    void explicitOverrideNeverTouchesGradleRuntime() throws Exception {
        String marker = newMarker();
        Path workspace = markerWorkspace(marker, true);
        try {
            Ran ran = runAnalyzer("{\"schemaVersion\":\"1\",\"recordType\":\"analysisRequest\","
                    + "\"requestId\":\"leak-explicit\",\"workspaceRoot\":\"" + jsonPath(workspace) + "\","
                    + "\"sourceRoots\":[\"src/main/java\"],\"language\":\"java\","
                    + "\"metadata\":{\"classpath\":[],\"javaLanguageLevel\":[\"17\"]}}");

            // build script が throw する fixture でも、明示 override は Gradle を評価しない。
            assertEquals(0, ran.exitCode(), ran.stderr());
            assertFalse(ran.stdout().contains(marker));
            assertFalse(ran.stderr().contains(marker));
            assertFalse(ran.stderr().contains("build-model discovery"),
                    "explicit override must not emit the discovery safety notice: " + ran.stderr());
        } finally {
            deleteRecursively(workspace);
        }
    }
}
