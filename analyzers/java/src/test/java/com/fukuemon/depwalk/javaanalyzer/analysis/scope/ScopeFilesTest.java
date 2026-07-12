package com.fukuemon.depwalk.javaanalyzer.analysis.scope;

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
class ScopeFilesTest {

    @Test
    void backslashSeparatedRelativePathIsNormalizedToForwardSlashes() {
        assertEquals("com/example/Foo.java",
                ScopeFiles.toMatchablePath("com\\example\\Foo.java").toString());
    }

    @Test
    void forwardSlashSeparatedRelativePathIsUnchanged() {
        assertEquals("com/example/Foo.java",
                ScopeFiles.toMatchablePath("com/example/Foo.java").toString());
    }

    @Test
    void backslashSeparatedInputMatchesForwardSlashGlob() {
        List<PathMatcher> matchers = ScopeFiles.toMatchers(List.of("com/example/**"));
        assertTrue(matchers.stream().anyMatch(m -> m.matches(ScopeFiles.toMatchablePath("com\\example\\lib\\Foo.java"))),
                "Windows-style backslash-separated relative path must match a forward-slash glob");
    }

    @Test
    void nonMatchingPathStillDoesNotMatchAfterNormalization() {
        List<PathMatcher> matchers = ScopeFiles.toMatchers(List.of("com/example/**"));
        assertFalse(matchers.stream().anyMatch(m -> m.matches(ScopeFiles.toMatchablePath("org\\other\\Foo.java"))));
    }
}
