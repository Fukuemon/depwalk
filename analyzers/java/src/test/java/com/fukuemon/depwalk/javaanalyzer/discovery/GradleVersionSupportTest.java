package com.fukuemon.depwalk.javaanalyzer.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradleVersionSupportTest {

    @ParameterizedTest
    @CsvSource({
            "7.6.4, false",
            "7.6.5, true",
            "7.6.6, true",
            "8.14.5, true",
            "9.6.1, true",
            "9.6.9, true",
            "9.7.0, false",
            "10.0, false",
            "7.5, false",
    })
    void judgesSupportedGradleRange(String version, boolean want) {
        assertEquals(Optional.of(want), GradleVersionSupport.isSupportedGradleVersion(version));
    }

    @Test
    void reportsUndeterminableVersionsAsEmpty() {
        assertTrue(GradleVersionSupport.isSupportedGradleVersion(null).isEmpty());
        assertTrue(GradleVersionSupport.isSupportedGradleVersion("").isEmpty());
        assertTrue(GradleVersionSupport.isSupportedGradleVersion("custom-build").isEmpty());
        assertTrue(GradleVersionSupport.isSupportedGradleVersion("9.6.1-branch").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            // context/toolchain.md の CI anchor 3 組は必ず互換。
            "7.6.5, 8, true",
            "8.14.5, 17, true",
            "9.6.1, 25, true",
            // Gradle 公式 matrix の境界。
            "7.6.5, 19, true",
            "7.6.5, 20, false",
            "8.4.0, 20, true",
            "8.4.0, 21, false",
            "8.14.5, 24, true",
            "8.14.5, 25, false",
            "9.0.0, 16, false",
            "9.0.0, 17, true",
            "9.0.0, 25, false",
            "9.6.1, 16, false",
    })
    void judgesDaemonJvmCompatibility(String version, int javaMajor, boolean want) {
        assertEquals(Optional.of(want), GradleVersionSupport.isDaemonJvmCompatible(version, javaMajor));
    }

    @Test
    void reportsDaemonCompatibilityEmptyForUndeterminableGradleVersion(){
        assertTrue(GradleVersionSupport.isDaemonJvmCompatible("mystery", 17).isEmpty());
    }
}
