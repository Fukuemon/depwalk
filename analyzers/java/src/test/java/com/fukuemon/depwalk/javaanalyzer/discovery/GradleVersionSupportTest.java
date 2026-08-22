package com.fukuemon.depwalk.javaanalyzer.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("サポートする Gradle version 範囲と daemon JVM 互換の判定表")
class GradleVersionSupportTest {

    @DisplayName("Gradle version がサポート範囲 (7.6.5 以上 9.7.x 以下) に入るかどうかが判定される")
    @ParameterizedTest
    @CsvSource({
            "7.6.4, false",
            "7.6.5, true",
            "7.6.6, true",
            "8.14.5, true",
            "9.6.1, true",
            "9.6.9, true",
            "9.7.1, true",
            "9.7.9, true",
            "9.8.0, false",
            "10.0, false",
            "7.5, false",
    })
    void judgesSupportedGradleRange(String version, boolean want) {
        assertEquals(Optional.of(want), GradleVersionSupport.isSupportedGradleVersion(version));
    }

    @Test
    @DisplayName("null・空文字・非標準表記の version のとき、サポート可否を確定させずに empty を返す")
    void reportsUndeterminableVersionsAsEmpty() {
        assertTrue(GradleVersionSupport.isSupportedGradleVersion(null).isEmpty());
        assertTrue(GradleVersionSupport.isSupportedGradleVersion("").isEmpty());
        assertTrue(GradleVersionSupport.isSupportedGradleVersion("custom-build").isEmpty());
        assertTrue(GradleVersionSupport.isSupportedGradleVersion("9.6.1-branch").isEmpty());
    }

    @DisplayName("Gradle version と daemon JVM major の組の互換が、公式 matrix の境界どおりに判定される")
    @ParameterizedTest
    @CsvSource({
            // CI anchor の 3 組は必ず互換。
            "7.6.5, 8, true",
            "8.14.5, 17, true",
            "9.7.1, 25, true",
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
            "9.3.0, 25, true",
            "9.3.0, 26, false",
            "9.4.0, 26, true",
            "9.4.0, 27, false",
            "9.6.1, 16, false",
    })
    void judgesDaemonJvmCompatibility(String version, int javaMajor, boolean want) {
        assertEquals(Optional.of(want), GradleVersionSupport.isDaemonJvmCompatible(version, javaMajor));
    }

    @Test
    @DisplayName("Gradle version を判別できないとき、daemon JVM 互換も確定させずに empty を返す")
    void reportsDaemonCompatibilityEmptyForUndeterminableGradleVersion(){
        assertTrue(GradleVersionSupport.isDaemonJvmCompatible("mystery", 17).isEmpty());
    }
}
