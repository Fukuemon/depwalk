package com.fukuemon.depwalk.javaanalyzer.discovery;

import java.util.Optional;

/**
 * 自動 discovery の Gradle / daemon JVM 互換性判定。正本は
 * {@code context/toolchain.md} の Gradle discovery compatibility matrix:
 * target Gradle は {@code 7.6.5 <= version < 9.7.0}、daemon JVM は Gradle
 * 公式 Java compatibility matrix に従う。
 */
public final class GradleVersionSupport {

    /** 同梱 Tooling API / wrapper なし build へ使う version。 */
    public static final String BUNDLED_GRADLE_VERSION = "9.6.1";

    private GradleVersionSupport() {
    }

    /**
     * Gradle version 文字列を安定判定できた場合だけ supported 判定を返す。
     *
     * @param gradleVersion BuildEnvironment が報告した version 文字列
     * @return supported なら true。判定不能 (custom distribution 等) は empty
     */
    public static Optional<Boolean> isSupportedGradleVersion(String gradleVersion) {
        Optional<int[]> parsed = parseVersion(gradleVersion);
        return parsed.map(v -> compare(v, new int[] {7, 6, 5}) >= 0 && compare(v, new int[] {9, 7, 0}) < 0);
    }

    /**
     * target Gradle と選択済み daemon JVM major の組が Gradle 公式互換範囲内か
     * を返す。判定不能な version は empty。
     *
     * @param gradleVersion BuildEnvironment が報告した version 文字列
     * @param daemonJavaMajor daemon JVM の Java major version
     * @return 互換範囲内なら true。Gradle version を判定できない場合は empty
     */
    public static Optional<Boolean> isDaemonJvmCompatible(String gradleVersion, int daemonJavaMajor) {
        Optional<int[]> parsed = parseVersion(gradleVersion);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        int[] v = parsed.get();
        int minJava = v[0] >= 9 ? 17 : 8;
        return Optional.of(daemonJavaMajor >= minJava && daemonJavaMajor <= maxDaemonJava(v));
    }

    // Gradle 公式 Java compatibility matrix (daemon JVM の上限 Java major)。
    private static int maxDaemonJava(int[] v) {
        if (compare(v, new int[] {9, 1, 0}) >= 0) {
            return 25;
        }
        if (compare(v, new int[] {9, 0, 0}) >= 0) {
            return 24;
        }
        if (compare(v, new int[] {8, 14, 0}) >= 0) {
            return 24;
        }
        if (compare(v, new int[] {8, 10, 0}) >= 0) {
            return 23;
        }
        if (compare(v, new int[] {8, 8, 0}) >= 0) {
            return 22;
        }
        if (compare(v, new int[] {8, 5, 0}) >= 0) {
            return 21;
        }
        if (compare(v, new int[] {8, 3, 0}) >= 0) {
            return 20;
        }
        // 7.6.x〜8.2.x
        return 19;
    }

    private static Optional<int[]> parseVersion(String version) {
        if (version == null) {
            return Optional.empty();
        }
        String[] parts = version.trim().split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            return Optional.empty();
        }
        int[] numbers = new int[3];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
                return Optional.empty();
            }
            try {
                numbers[i] = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.of(numbers);
    }

    private static int compare(int[] left, int[] right) {
        for (int i = 0; i < 3; i++) {
            int diff = Integer.compare(left[i], right[i]);
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }
}
