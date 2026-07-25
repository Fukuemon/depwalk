package com.fukuemon.depwalk.javaanalyzer.discovery;

import com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkGradleModel;
import com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkProjectModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gradle discovery compatibility matrix (context/toolchain.md の CI anchor)。
 * 同一 custom model fixture を固定 anchor (target Gradle × daemon JDK) で実行し、
 * provider load、model fields、task 非実行、output 隔離を検証する。
 * Analyzer client は全 run で現行 test JVM (JDK 25) に固定される。
 *
 * <p>daemon JDK home は build.gradle.kts の {@code gradleCompatibilityTest} task が
 * toolchain 解決して system property で注入する ({@code depwalk.matrix.jdk<major>})。
 * 未解決の anchor は skip 成功にせず fail する。
 */
@Tag("gradle-compat")
class GradleCompatibilityMatrixTest {

    @ParameterizedTest(name = "Gradle {0} / daemon JDK {1}")
    @CsvSource({
            "7.6.5, 8",
            "8.14.5, 17",
            "9.6.1, 25",
    })
    void discoversTheSameModelOnEachAnchor(String gradleVersion, int daemonJavaMajor) throws Exception {
        String jdkHome = System.getProperty("depwalk.matrix.jdk" + daemonJavaMajor);
        assertNotNull(jdkHome, "daemon JDK " + daemonJavaMajor
                + " was not provisioned; run via ./gradlew gradleCompatibilityTest");

        // fixture を一時 copy し、daemon JVM を gradle.properties で固定する。
        Path source = Path.of("..", "..", "testdata", "fixtures", "java", "multi-module-spring-project")
                .toAbsolutePath().normalize();
        Path workspace = Files.createTempDirectory("depwalk-matrix-" + gradleVersion + "-").toRealPath();
        try {
            copyFixture(source, workspace);
            Files.writeString(workspace.resolve("gradle.properties"),
                    "org.gradle.java.home=" + jdkHome + "\n");

            ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
            PrintStream stderr = new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8);
            GradleModelDiscovery discovery =
                    new GradleModelDiscovery(new GradleToolingClient(gradleVersion), stderr);

            DepwalkGradleModel model = discovery.discover(workspace);

            Map<String, DepwalkProjectModel> projects = model.getProjects().stream()
                    .collect(Collectors.toMap(DepwalkProjectModel::getProjectPath, p -> p));
            assertEquals(3, projects.size(), projects.keySet().toString());

            // projectDir 変更と custom main source directory を model から検出する。
            assertRoot(projects.get(":app"), workspace, "app/src/main/java");
            assertRoot(projects.get(":service"), workspace, "modules/service/src/main/java");
            assertRoot(projects.get(":repository"), workspace, "repository/src/domain/java");
            assertEquals("17", projects.get(":app").getSourceLanguageLevel());
            assertEquals("17", projects.get(":repository").getSourceLanguageLevel());
            assertEquals(List.of(":service"), projects.get(":app").getProjectDependencyPaths());
            assertEquals(List.of(":repository"), projects.get(":service").getProjectDependencyPaths());

            // task 非実行: copyFixture は build/ を除外するため両 path とも
            // discovery 前は不在 (前提)。model 取得後も不在のままであることが
            // compile / task 非実行の検証になる。
            assertFalse(Files.exists(workspace.resolve("app/build/classes")),
                    "model request must not execute compile tasks");
            assertFalse(Files.exists(workspace.resolve("build/depwalk-classpath.txt")));

            // output 隔離: stderr は depwalk 生成の固定行だけで、Gradle 自由文を含まない。
            String output = stderrBuffer.toString(StandardCharsets.UTF_8);
            assertTrue(output.contains("depwalk: discovery phase=end gradleVersion=" + gradleVersion), output);
            for (String line : output.split("\n")) {
                if (!line.isBlank()) {
                    assertTrue(line.startsWith("depwalk: "), "non-depwalk stderr line: " + line);
                }
            }
        } finally {
            try (Stream<Path> paths = Files.walk(workspace)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @org.junit.jupiter.api.Test
    void unsupportedGradleVersionFailsWithStableReasonOnRealDaemon() throws Exception {
        String jdkHome = System.getProperty("depwalk.matrix.jdk17");
        org.junit.jupiter.api.Assertions.assertNotNull(jdkHome,
                "daemon JDK 17 was not provisioned; run via ./gradlew gradleCompatibilityTest");
        Path source = Path.of("..", "..", "testdata", "fixtures", "java", "multi-module-spring-project")
                .toAbsolutePath().normalize();
        Path workspace = Files.createTempDirectory("depwalk-matrix-neg-").toRealPath();
        try {
            copyFixture(source, workspace);
            Files.writeString(workspace.resolve("gradle.properties"), "org.gradle.java.home=" + jdkHome + "\n");
            ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
            PrintStream stderr = new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8);

            DiscoveryFailure failure = org.junit.jupiter.api.Assertions.assertThrows(DiscoveryFailure.class,
                    () -> new GradleModelDiscovery(new GradleToolingClient("7.6.4"), stderr)
                            .discover(workspace));

            assertEquals(DiscoveryFailure.Category.UNSUPPORTED_GRADLE_VERSION, failure.category());
            assertTrue(failure.userMessage().contains("unsupported-gradle-version"));
            assertTrue(failure.userMessage().contains("--source-root"));
        } finally {
            try (Stream<Path> paths = Files.walk(workspace)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @org.junit.jupiter.api.Test
    void reportsIncludedBuildRootsForExclusionWarnings() throws Exception {
        String jdkHome = System.getProperty("depwalk.matrix.jdk17");
        assertNotNull(jdkHome, "daemon JDK 17 was not provisioned; run via ./gradlew gradleCompatibilityTest");
        Path workspace = Files.createTempDirectory("depwalk-matrix-included-").toRealPath();
        try {
            Files.writeString(workspace.resolve("settings.gradle"), """
                    rootProject.name = 'composite-root'
                    includeBuild 'tooling-build'
                    """);
            Files.writeString(workspace.resolve("build.gradle"), "plugins { id 'java' }\n");
            Path rootSrc = workspace.resolve("src/main/java/com/example");
            Files.createDirectories(rootSrc);
            Files.writeString(rootSrc.resolve("Root.java"), "package com.example; public class Root {}\n");
            Path inner = workspace.resolve("tooling-build");
            Files.createDirectories(inner);
            Files.writeString(inner.resolve("settings.gradle"), "rootProject.name = 'tooling-build'\n");
            Files.writeString(inner.resolve("build.gradle"), "plugins { id 'java' }\n");
            Files.writeString(workspace.resolve("gradle.properties"),
                    "org.gradle.java.home=" + jdkHome + "\n");
            ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
            PrintStream stderr = new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8);

            DepwalkGradleModel model = new GradleModelDiscovery(new GradleToolingClient("8.14.5"), stderr)
                    .discover(workspace);

            // included build の project は model に現れず、root は warning 用に報告される。
            assertEquals(1, model.getProjects().size(), () -> model.getProjects().toString());
            assertEquals(1, model.getIncludedBuildRootDirectories().size());
            assertEquals("tooling-build", model.getIncludedBuildRootDirectories().get(0).getName());
        } finally {
            try (Stream<Path> paths = Files.walk(workspace)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @org.junit.jupiter.api.Test
    void normalizesLegacySourceCompatibilityToCanonicalMajor() throws Exception {
        String jdkHome = System.getProperty("depwalk.matrix.jdk17");
        assertNotNull(jdkHome, "daemon JDK 17 was not provisioned; run via ./gradlew gradleCompatibilityTest");
        Path workspace = Files.createTempDirectory("depwalk-matrix-legacy-").toRealPath();
        try {
            Files.writeString(workspace.resolve("settings.gradle"), "rootProject.name = 'legacy'\n");
            // release 未指定の Java 8 project は JavaVersion.toString() が "1.8" を
            // 返すため、provider が canonical major "8" へ正規化することを固定する。
            Files.writeString(workspace.resolve("build.gradle"), """
                    plugins { id 'java' }
                    java {
                        sourceCompatibility = JavaVersion.VERSION_1_8
                        targetCompatibility = JavaVersion.VERSION_1_8
                    }
                    """);
            Path src = workspace.resolve("src/main/java/com/example");
            Files.createDirectories(src);
            Files.writeString(src.resolve("Legacy.java"), "package com.example; public class Legacy {}\n");
            Files.writeString(workspace.resolve("gradle.properties"),
                    "org.gradle.java.home=" + jdkHome + "\n");
            ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
            PrintStream stderr = new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8);

            DepwalkGradleModel model = new GradleModelDiscovery(new GradleToolingClient("8.14.5"), stderr)
                    .discover(workspace);

            assertEquals(1, model.getProjects().size());
            assertEquals("8", model.getProjects().get(0).getSourceLanguageLevel());
        } finally {
            try (Stream<Path> paths = Files.walk(workspace)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static void assertRoot(DepwalkProjectModel project, Path workspace, String expectedRelative) {
        assertNotNull(project);
        List<String> roots = project.getMainJavaSourceDirectories().stream()
                .map(dir -> workspace.relativize(dir.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/'))
                .toList();
        assertTrue(roots.contains(expectedRelative), roots.toString());
    }

    private static void copyFixture(Path source, Path target) throws Exception {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.filter(p -> {
                Path rel = source.relativize(p);
                for (Path segment : rel) {
                    String name = segment.toString();
                    if (name.equals("build") || name.equals(".gradle")) {
                        return false;
                    }
                }
                return true;
            }).toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
