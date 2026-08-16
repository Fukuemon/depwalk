package com.fukuemon.depwalk.javaanalyzer.analysis.scope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.PathMatcher;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * include / exclude glob の separator 正規化: Windows の {@code \} 区切り相対 path でも
 * {@code com/example/**} 形式の glob と一致するよう、照合前に {@code /} 区切りへ正規化する。
 * (macOS / Linux の {@code /} 区切り入力の挙動は不変。)
 */
@DisplayName("include / exclude glob 照合前の path separator 正規化")
class ScopeFilesTest {

    @Test
    @DisplayName("バックスラッシュ区切りの相対 path は、照合前にスラッシュ区切りへ正規化される")
    void backslashSeparatedRelativePathIsNormalizedToForwardSlashes() {
        assertEquals("com/example/Foo.java",
                ScopeFiles.toMatchablePath("com\\example\\Foo.java").toString());
    }

    @Test
    @DisplayName("スラッシュ区切りの相対 path は、正規化を通しても変わらない")
    void forwardSlashSeparatedRelativePathIsUnchanged() {
        assertEquals("com/example/Foo.java",
                ScopeFiles.toMatchablePath("com/example/Foo.java").toString());
    }

    @Test
    @DisplayName("Windows 形式のバックスラッシュ区切り path でも、スラッシュ区切りの glob に一致する")
    void backslashSeparatedInputMatchesForwardSlashGlob() {
        List<PathMatcher> matchers = com.fukuemon.depwalk.javaanalyzer.analysis.context.ContextScope.toMatchers(List.of("com/example/**"));
        assertTrue(matchers.stream().anyMatch(m -> m.matches(ScopeFiles.toMatchablePath("com\\example\\lib\\Foo.java"))),
                "Windows-style backslash-separated relative path must match a forward-slash glob");
    }

    @Test
    @DisplayName("glob に該当しない path は、正規化した後でも一致しないままになる")
    void nonMatchingPathStillDoesNotMatchAfterNormalization() {
        List<PathMatcher> matchers = com.fukuemon.depwalk.javaanalyzer.analysis.context.ContextScope.toMatchers(List.of("com/example/**"));
        assertFalse(matchers.stream().anyMatch(m -> m.matches(ScopeFiles.toMatchablePath("org\\other\\Foo.java"))));
    }
}
