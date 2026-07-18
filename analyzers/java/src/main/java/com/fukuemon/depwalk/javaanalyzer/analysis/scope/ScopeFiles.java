package com.fukuemon.depwalk.javaanalyzer.analysis.scope;

import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.RelativePaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * {@code include} / {@code exclude} 適用後の scope (Java ソースファイル集合) を列挙する。
 * 帰属型決定規則の「宣言サイトが scope 内」判定は、本クラスが列挙したファイル集合への
 * membership check として実装する。
 */
public final class ScopeFiles {

    private ScopeFiles() {
    }

    /**
     * workspaceRoot 配下の {@code .java} ファイルのうち、include (未指定なら全件) にマッチし、
     * exclude にマッチしないものを、決定的な順序 (path 文字列の昇順) で列挙する。
     * glob との照合は {@link #toMatchablePath(String)} で {@code /} 区切りへ正規化した相対 path に
     * 対して行う (Windows の {@code \} 区切りでも {@code com/example/**} 形式の glob が機能する。
     * macOS / Linux の挙動は不変)。
     *
     * @param workspaceRoot 列挙の起点 directory
     * @param include 対象に含める相対 path glob。未指定または空なら全 Java source
     * @param exclude 対象外にする相対 path glob。未指定または空なら除外なし
     * @return 絶対・正規化済み path を辞書順に並べた Java source 一覧
     * @throws UncheckedIOException workspace の走査に失敗した場合
     */
    public static List<Path> enumerate(Path workspaceRoot, List<String> include, List<String> exclude) {
        List<PathMatcher> includeMatchers = toMatchers(include);
        List<PathMatcher> excludeMatchers = toMatchers(exclude);

        try (Stream<Path> walk = Files.walk(workspaceRoot)) {
            List<Path> result = new ArrayList<>();
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        Path relative = toMatchablePath(workspaceRoot.relativize(p).toString());
                        boolean included = includeMatchers.isEmpty()
                                || includeMatchers.stream().anyMatch(m -> m.matches(relative));
                        boolean excluded = excludeMatchers.stream().anyMatch(m -> m.matches(relative));
                        if (included && !excluded) {
                            result.add(p.toAbsolutePath().normalize());
                        }
                    });
            result.sort((a, b) -> a.toString().compareTo(b.toString()));
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * glob 照合用に相対 path 文字列を {@code /} 区切りへ正規化した {@link Path} に変換する
     * (Windows の {@code \} 区切りを吸収する。{@code /} 区切り入力はそのまま)。
     */
    public static Path toMatchablePath(String rawRelativePath) {
        return Path.of(RelativePaths.toRecordPath(rawRelativePath));
    }

    /**
     * {@link #enumerate(Path, List, List)} の結果を membership check 用 set に変換する。
     *
     * @param scopeFiles scope に含まれる source file path
     * @return 絶対・正規化済み path の挿入順 set
     */
    public static Set<Path> toMembershipSet(List<Path> scopeFiles) {
        Set<Path> set = new LinkedHashSet<>();
        for (Path p : scopeFiles) {
            set.add(p.toAbsolutePath().normalize());
        }
        return set;
    }

    static List<PathMatcher> toMatchers(List<String> globs) {
        if (globs == null || globs.isEmpty()) {
            return List.of();
        }
        List<PathMatcher> matchers = new ArrayList<>();
        for (String glob : globs) {
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob));
        }
        return matchers;
    }
}
