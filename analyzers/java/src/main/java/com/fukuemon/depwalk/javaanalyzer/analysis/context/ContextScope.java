package com.fukuemon.depwalk.javaanalyzer.analysis.context;

import com.fukuemon.depwalk.javaanalyzer.JavaErrorCode;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.RelativePaths;
import com.fukuemon.depwalk.javaanalyzer.analysis.scope.ScopeFiles;
import com.fukuemon.depwalk.javaanalyzer.preflight.AnalyzerFatalException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 全 context の source root から include / exclude 適用後の Java file 集合を
 * 構築する。glob と SourceLocation の基準は workspaceRoot のままとし
 * (java-analyzer feature doc「Source root discovery と解析 context」)、
 * file は正規化済み絶対 path で 1 回だけ解析されるよう重複排除する。
 */
public final class ContextScope {

    private ContextScope() {
    }

    /**
     * 解析対象 file とその所有 context の対応。
     *
     * @param filesByContext context id → 昇順 file 一覧
     * @param allFiles workspace 相対 path 昇順の全 file
     * @param membership 帰属判定用の絶対・正規化済み path 集合
     */
    public record Scope(
            Map<String, List<Path>> filesByContext,
            List<Path> allFiles,
            Set<Path> membership) {
    }

    /**
     * 各 context の root から file を列挙し、workspace 相対 path で
     * include / exclude を適用する。
     *
     * @param workspaceRoot 絶対・正規化済み workspace root
     * @param contexts 解析 context
     * @param include workspace 相対 include glob (任意)
     * @param exclude workspace 相対 exclude glob (任意)
     * @return 所有 context 別と全体の file 集合
     * @throws AnalyzerFatalException 異なる context が同じ source binary name を
     *     宣言する場合 (record 出力前 fatal)
     */
    public static Scope enumerate(
            Path workspaceRoot,
            List<SourceSetAnalysisContext> contexts,
            List<String> include,
            List<String> exclude) throws AnalyzerFatalException {
        List<PathMatcher> includeMatchers = toMatchers(include);
        List<PathMatcher> excludeMatchers = toMatchers(exclude);

        Map<String, List<Path>> filesByContext = new LinkedHashMap<>();
        Map<String, String> binaryNameOwners = new LinkedHashMap<>();
        Set<Path> membership = new LinkedHashSet<>();
        List<Path> allFiles = new ArrayList<>();

        for (SourceSetAnalysisContext context : contexts) {
            List<Path> files = new ArrayList<>();
            for (Path root : context.sourceRoots()) {
                for (Path file : listJavaFiles(root)) {
                    Path absolute = file.toAbsolutePath().normalize();
                    Path relative = ScopeFiles.toMatchablePath(workspaceRoot.relativize(absolute).toString());
                    boolean included = includeMatchers.isEmpty()
                            || includeMatchers.stream().anyMatch(m -> m.matches(relative));
                    boolean excluded = excludeMatchers.stream().anyMatch(m -> m.matches(relative));
                    if (!included || excluded) {
                        continue;
                    }
                    // root 相対 path が source binary name の近似。異なる context の
                    // 同一 binary name は現行 methodId で区別できないため fatal
                    // (java-analyzer feature doc「Source root discovery と解析 context」)。
                    String binaryName = RelativePaths.toRecordPath(root.relativize(absolute).toString());
                    String owner = binaryNameOwners.putIfAbsent(binaryName, context.id());
                    if (owner != null && !owner.equals(context.id())) {
                        throw new AnalyzerFatalException(
                                JavaErrorCode.JAVA_INVALID_SOURCE_ROOTS,
                                "analysis contexts " + owner + " and " + context.id()
                                        + " declare the same source binary name: " + binaryName);
                    }
                    if (membership.add(absolute)) {
                        files.add(absolute);
                        allFiles.add(absolute);
                    }
                }
            }
            files.sort((a, b) -> a.toString().compareTo(b.toString()));
            filesByContext.put(context.id(), files);
        }
        allFiles.sort((a, b) -> workspaceRelative(workspaceRoot, a).compareTo(workspaceRelative(workspaceRoot, b)));
        return new Scope(filesByContext, List.copyOf(allFiles), membership);
    }

    /** workspace 相対の record path 表現を返す。 */
    public static String workspaceRelative(Path workspaceRoot, Path file) {
        return RelativePaths.toRecordPath(workspaceRoot.relativize(file).toString());
    }

    private static List<Path> listJavaFiles(Path root) {
        // directory symlink は再帰追跡しない (walk の既定挙動)。
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** include / exclude glob を {@link PathMatcher} 一覧へ変換する。 */
    public static List<PathMatcher> toMatchers(List<String> globs) {
        if (globs == null || globs.isEmpty()) {
            return List.of();
        }
        List<PathMatcher> matchers = new ArrayList<>();
        for (String glob : globs) {
            matchers.add(java.nio.file.FileSystems.getDefault().getPathMatcher("glob:" + glob));
        }
        return matchers;
    }
}
