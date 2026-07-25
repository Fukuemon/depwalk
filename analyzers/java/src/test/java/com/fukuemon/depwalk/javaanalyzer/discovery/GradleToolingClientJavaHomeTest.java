package com.fukuemon.depwalk.javaanalyzer.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** daemon JVM major の静的判定 (JVM 非起動) の境界。 */
class GradleToolingClientJavaHomeTest {

    @TempDir
    Path home;

    @Test
    void readsMajorFromReleaseFile() throws Exception {
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"17.0.9\"\n");
        assertEquals(Optional.of(17), GradleToolingClient.javaMajorFromJavaHome(home.toFile()));
    }

    @Test
    void fallsBackToRtJarLayoutForReleaselessJdk8() throws Exception {
        Files.createDirectories(home.resolve("lib"));
        Files.createDirectories(home.resolve("bin"));
        Files.writeString(home.resolve("lib/rt.jar"), "");
        Files.writeString(home.resolve("bin/java"), "");
        assertEquals(Optional.of(8), GradleToolingClient.javaMajorFromJavaHome(home.toFile()));
    }

    @Test
    void returnsEmptyWhenUndeterminable() {
        // 判定不能は empty のまま (呼び出し側が fatal + 明示 override 案内)。
        assertEquals(Optional.empty(), GradleToolingClient.javaMajorFromJavaHome(home.toFile()));
        assertEquals(Optional.empty(), GradleToolingClient.javaMajorFromJavaHome(null));
    }

    @Test
    void parsesLegacyAndModernVersionStrings() {
        assertEquals(Optional.of(8), GradleToolingClient.parseJavaMajor("1.8.0_492"));
        assertEquals(Optional.of(25), GradleToolingClient.parseJavaMajor("25"));
        assertEquals(Optional.empty(), GradleToolingClient.parseJavaMajor("unknown"));
    }
}
