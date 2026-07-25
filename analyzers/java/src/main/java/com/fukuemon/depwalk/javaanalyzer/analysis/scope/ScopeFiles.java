package com.fukuemon.depwalk.javaanalyzer.analysis.scope;

import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.RelativePaths;

import java.nio.file.Path;

/**
 * include / exclude glob 照合の path 正規化 helper。scope の列挙自体は
 * {@code analysis.context.ContextScope} が source root 起点で行う。
 */
public final class ScopeFiles {

    private ScopeFiles() {
    }

    /**
     * glob 照合用に相対 path 文字列を {@code /} 区切りへ正規化した {@link Path} に変換する
     * (Windows の {@code \} 区切りを吸収する。{@code /} 区切り入力はそのまま)。
     */
    public static Path toMatchablePath(String rawRelativePath) {
        return Path.of(RelativePaths.toRecordPath(rawRelativePath));
    }
}
