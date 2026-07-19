package com.fukuemon.depwalk.javaanalyzer.analysis.context;

import com.fukuemon.depwalk.javaanalyzer.JavaDiagnosticCode;
import com.fukuemon.depwalk.javaanalyzer.JavaErrorCode;
import com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkGradleModel;
import com.fukuemon.depwalk.javaanalyzer.discovery.model.DepwalkProjectModel;
import com.fukuemon.depwalk.javaanalyzer.preflight.AnalyzerFatalException;
import com.fukuemon.depwalk.javaanalyzer.protocol.Diagnostic;

import com.github.javaparser.ParserConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 明示 {@code sourceRoots} または discovery model から解析 context を構築する。
 * root の workspace / real-path 境界、重複、包含は TypeSolver 構築と file 列挙の
 * 前にここで確定し、filesystem 規約への fallback は行わない (spec #24 D5 / D7)。
 */
public final class AnalysisContextFactory {

    private static final String METADATA_JAVA_LANGUAGE_LEVEL = "javaLanguageLevel";
    private static final String METADATA_JAVA_PREVIEW = "javaPreview";

    private AnalysisContextFactory() {
    }

    /**
     * context 構築の結果。診断 (warning) は成功確定まで下流が保持・出力する。
     *
     * @param contexts 解析 context (1 件以上)
     * @param warnings 除外 root / source-only 降格などの非 fatal warning
     */
    public record Result(List<SourceSetAnalysisContext> contexts, List<Diagnostic> warnings) {
    }

    /**
     * 明示 {@code sourceRoots} から 1 つの synthetic context を構築する。
     *
     * @param workspaceRoot 絶対・正規化済み workspace root
     * @param sourceRoots 明示された workspace 相対 root (1 件以上)
     * @param classpath 検証済み {@code metadata.classpath}
     * @param metadata 生 metadata ({@code javaLanguageLevel} / {@code javaPreview} を読む)
     * @return synthetic context 1 件と warning
     * @throws AnalyzerFatalException root / language level 構成が不正な場合
     */
    public static Result explicitContext(
            Path workspaceRoot,
            List<String> sourceRoots,
            List<String> classpath,
            Map<String, Object> metadata) throws AnalyzerFatalException {
        List<Path> roots = resolveExplicitRoots(workspaceRoot, sourceRoots);
        ParserConfiguration.LanguageLevel level = explicitLanguageLevel(metadata);
        boolean preview = explicitPreview(metadata);
        if (LanguageLevels.resolve(languageLevelText(metadata), preview).isEmpty()) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "metadata.javaLanguageLevel with javaPreview is not supported by the parser");
        }
        List<Path> classpathPaths = new ArrayList<>();
        List<Path> classesOutputs = new ArrayList<>();
        for (String entry : classpath) {
            Path path = parseRequestPath(entry, "metadata.classpath entry").toAbsolutePath().normalize();
            classpathPaths.add(path);
            if (Files.isDirectory(path)) {
                classesOutputs.add(path);
            }
        }
        List<Diagnostic> warnings = new ArrayList<>();
        if (classesOutputs.isEmpty()) {
            warnings.add(sootUpUnavailableWarning(SourceSetAnalysisContext.EXPLICIT_CONTEXT_ID,
                    "no classes output directory on metadata.classpath; continuing source-only"));
        }
        SourceSetAnalysisContext context = new SourceSetAnalysisContext(
                SourceSetAnalysisContext.EXPLICIT_CONTEXT_ID,
                roots,
                List.copyOf(classpathPaths),
                List.copyOf(classesOutputs),
                List.of(),
                LanguageLevels.resolve(languageLevelText(metadata), preview).orElse(level),
                preview);
        return new Result(List.of(context), warnings);
    }

    /**
     * discovery model から project / {@code main} source set ごとの context を
     * 構築する。
     *
     * @param workspaceRoot 絶対・正規化済み workspace root
     * @param model provider が返した build model
     * @param commonClasspath {@code metadata.classpath} の共通追加 entry (任意)
     * @return context 一覧と warning
     * @throws AnalyzerFatalException model の root / classpath / level 構成が不正な場合
     */
    public static Result discoveredContexts(
            Path workspaceRoot,
            DepwalkGradleModel model,
            List<String> commonClasspath) throws AnalyzerFatalException {
        List<Diagnostic> warnings = new ArrayList<>();
        Map<String, SourceSetAnalysisContext> contexts = new LinkedHashMap<>();
        Map<Path, String> rootOwners = new LinkedHashMap<>();

        List<Path> commonEntries = new ArrayList<>();
        for (String entry : commonClasspath) {
            commonEntries.add(parseRequestPath(entry, "metadata.classpath entry").toAbsolutePath().normalize());
        }

        int externalBuildProjectCount = 0;
        for (DepwalkProjectModel project : model.getProjects()) {
            Path projectDir = project.getProjectDirectory().toPath().toAbsolutePath().normalize();
            if (!realPathWithin(projectDir, workspaceRoot)) {
                // external included build は in-scope root 検証の前に識別して除外する
                // (解決済み artifact は classpath 側で利用できる)。model は build 識別子を
                // 持たないため、warning は run あたり 1 件へ集約する。
                externalBuildProjectCount++;
                continue;
            }

            String contextId = project.getProjectPath() + "|main";
            List<Path> roots = new ArrayList<>();
            for (File dir : project.getMainJavaSourceDirectories()) {
                Path root = dir.toPath().toAbsolutePath().normalize();
                if (!Files.exists(root)) {
                    warnings.add(warning(JavaDiagnosticCode.JAVA_SOURCE_ROOT_EXCLUDED,
                            "excluded a source directory that does not exist yet in project " + project.getProjectPath()));
                    continue;
                }
                if (!Files.isDirectory(root) || !Files.isReadable(root)) {
                    throw new AnalyzerFatalException(
                            JavaErrorCode.JAVA_INVALID_SOURCE_ROOTS,
                            "discovered source root is not a readable directory in project " + project.getProjectPath());
                }
                Path real = toRealPath(root, JavaErrorCode.JAVA_INVALID_SOURCE_ROOTS);
                if (!real.startsWith(toRealPath(workspaceRoot, JavaErrorCode.JAVA_INVALID_REQUEST))) {
                    throw new AnalyzerFatalException(
                            JavaErrorCode.JAVA_INVALID_SOURCE_ROOTS,
                            "in-scope project " + project.getProjectPath()
                                    + " references a source root outside the workspace");
                }
                String owner = rootOwners.putIfAbsent(real, contextId);
                if (owner != null && !owner.equals(contextId)) {
                    throw new AnalyzerFatalException(
                            JavaErrorCode.JAVA_INVALID_SOURCE_ROOTS,
                            "the same source root belongs to multiple analysis contexts: " + owner + " and " + contextId);
                }
                if (!roots.contains(real)) {
                    roots.add(real);
                }
            }
            if (roots.isEmpty()) {
                continue;
            }

            List<Path> classpathPaths = new ArrayList<>();
            for (File entry : project.getMainCompileClasspath()) {
                Path path = entry.toPath().toAbsolutePath().normalize();
                if (!Files.exists(path)) {
                    // model 取得は task を実行しないため、workspace 内の project 依存
                    // build output は未 build だと存在しない。依存 project の型は
                    // 依存 context の source root が solver へ入るため、workspace 内
                    // entry の欠落は warning で除外し source 解析を継続する。
                    // workspace 外 (external artifact) の欠落は従来どおり fatal。
                    if (withinWorkspace(path, workspaceRoot)) {
                        warnings.add(sootUpUnavailableWarning(contextId,
                                "excluded an unbuilt workspace classpath entry; continuing without its bytecode in project "
                                        + project.getProjectPath()));
                        continue;
                    }
                    throw new AnalyzerFatalException(
                            JavaErrorCode.JAVA_MISSING_JAR,
                            "a resolved compile classpath entry is missing or unreadable in project "
                                    + project.getProjectPath());
                }
                if (!Files.isReadable(path)) {
                    throw new AnalyzerFatalException(
                            JavaErrorCode.JAVA_MISSING_JAR,
                            "a resolved compile classpath entry is missing or unreadable in project "
                                    + project.getProjectPath());
                }
                classpathPaths.add(path);
            }
            classpathPaths.addAll(commonEntries);

            List<Path> classesOutputs = new ArrayList<>();
            for (File output : project.getMainClassesOutputDirectories()) {
                Path path = output.toPath().toAbsolutePath().normalize();
                if (Files.isDirectory(path)) {
                    classesOutputs.add(path);
                }
            }
            if (classesOutputs.isEmpty()) {
                warnings.add(sootUpUnavailableWarning(contextId,
                        "project classes output is missing; continuing source-only for " + project.getProjectPath()));
            }

            Optional<ParserConfiguration.LanguageLevel> level =
                    LanguageLevels.resolve(project.getSourceLanguageLevel(), project.isPreviewEnabled());
            if (level.isEmpty()) {
                throw new AnalyzerFatalException(
                        JavaErrorCode.JAVA_INVALID_REQUEST,
                        "the source language level of project " + project.getProjectPath()
                                + " is missing, ambiguous, or unsupported by the parser: "
                                + project.getSourceLanguageLevel());
            }

            List<String> dependencyIds = new ArrayList<>();
            for (String dependencyPath : project.getProjectDependencyPaths()) {
                dependencyIds.add(dependencyPath + "|main");
            }

            contexts.put(contextId, new SourceSetAnalysisContext(
                    contextId,
                    List.copyOf(roots),
                    List.copyOf(classpathPaths),
                    List.copyOf(classesOutputs),
                    List.copyOf(dependencyIds),
                    level.get(),
                    project.isPreviewEnabled()));
        }

        if (externalBuildProjectCount > 0) {
            warnings.add(warning(JavaDiagnosticCode.JAVA_SOURCE_ROOT_EXCLUDED,
                    "excluded " + externalBuildProjectCount
                            + " project(s) of external included builds from the analysis scope"));
        }

        // composite / included build は v1 の model 対象外 (root build の project
        // 階層だけを解析する)。黙って脱落させず 1 build につき 1 件 warning を出し、
        // source を解析したい場合の明示 override を案内する。
        for (File includedBuildRoot : model.getIncludedBuildRootDirectories()) {
            warnings.add(warning(JavaDiagnosticCode.JAVA_SOURCE_ROOT_EXCLUDED,
                    "excluded included build " + includedBuildRoot.getName()
                            + " from the analysis scope; pass --source-root to analyze its sources explicitly"));
        }

        rejectContainedRoots(rootOwners.keySet(), JavaErrorCode.JAVA_INVALID_SOURCE_ROOTS);

        if (contexts.isEmpty() || rootOwners.isEmpty()) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_NO_SOURCE_ROOTS,
                    "no usable Java source root remains after discovery normalization");
        }
        return new Result(List.copyOf(contexts.values()), warnings);
    }

    private static List<Path> resolveExplicitRoots(Path workspaceRoot, List<String> sourceRoots)
            throws AnalyzerFatalException {
        if (sourceRoots.isEmpty()) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "analysisRequest.sourceRoots must not be an explicit empty array");
        }
        Path workspaceReal = toRealPath(workspaceRoot, JavaErrorCode.JAVA_INVALID_REQUEST);
        Set<Path> seen = new LinkedHashSet<>();
        for (String value : sourceRoots) {
            if (value.isEmpty() || value.contains("\\") || value.startsWith("/")
                    || parseRequestPath(value, "analysisRequest.sourceRoots element").isAbsolute()
                    || containsParentSegment(value)) {
                throw new AnalyzerFatalException(
                        JavaErrorCode.JAVA_INVALID_REQUEST,
                        "analysisRequest.sourceRoots element must be a workspace-relative path without .. segments");
            }
            Path candidate = workspaceRoot.resolve(value).normalize();
            if (!Files.exists(candidate) || !Files.isDirectory(candidate) || !Files.isReadable(candidate)) {
                throw new AnalyzerFatalException(
                        JavaErrorCode.JAVA_INVALID_SOURCE_ROOTS,
                        "explicit source root is missing, not a directory, or unreadable: " + value);
            }
            Path real = toRealPath(candidate, JavaErrorCode.JAVA_INVALID_SOURCE_ROOTS);
            if (!real.startsWith(workspaceReal)) {
                throw new AnalyzerFatalException(
                        JavaErrorCode.JAVA_INVALID_SOURCE_ROOTS,
                        "explicit source root resolves outside the workspace: " + value);
            }
            // real path 正規化後の完全重複は先頭だけを残す (順序は入力順を維持)。
            seen.add(real);
        }
        rejectContainedRoots(seen, JavaErrorCode.JAVA_INVALID_SOURCE_ROOTS);
        return List.copyOf(seen);
    }

    /** 異なる root 間の親子包含を TypeSolver 構築・file 列挙の前に拒否する。 */
    private static void rejectContainedRoots(Set<Path> roots, JavaErrorCode code) throws AnalyzerFatalException {
        for (Path left : roots) {
            for (Path right : roots) {
                if (!left.equals(right) && right.startsWith(left)) {
                    throw new AnalyzerFatalException(
                            code,
                            "source roots must not contain each other: " + left + " contains " + right);
                }
            }
        }
    }

    private static ParserConfiguration.LanguageLevel explicitLanguageLevel(Map<String, Object> metadata)
            throws AnalyzerFatalException {
        String text = languageLevelText(metadata);
        Optional<ParserConfiguration.LanguageLevel> level = LanguageLevels.resolve(text, false);
        if (level.isEmpty()) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "metadata.javaLanguageLevel must be a canonical decimal major version supported by the parser");
        }
        return level.get();
    }

    private static String languageLevelText(Map<String, Object> metadata) throws AnalyzerFatalException {
        Object raw = metadata == null ? null : metadata.get(METADATA_JAVA_LANGUAGE_LEVEL);
        if (!(raw instanceof List<?> list) || list.size() != 1 || !(list.get(0) instanceof String text)) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "metadata.javaLanguageLevel must be a one-element string array for explicit source roots");
        }
        return text;
    }

    private static boolean explicitPreview(Map<String, Object> metadata) throws AnalyzerFatalException {
        Object raw = metadata == null ? null : metadata.get(METADATA_JAVA_PREVIEW);
        if (raw == null) {
            return false;
        }
        if (raw instanceof List<?> list && list.size() == 1 && (List.of("true", "false").contains(list.get(0)))) {
            return "true".equals(list.get(0));
        }
        throw new AnalyzerFatalException(
                JavaErrorCode.JAVA_INVALID_REQUEST,
                "metadata.javaPreview must be [\"true\"] or [\"false\"]");
    }

    /** 自動 discovery 経路では language metadata の明示指定を invalid とする。 */
    public static void rejectLanguageMetadataOnDiscovery(Map<String, Object> metadata) throws AnalyzerFatalException {
        if (metadata != null
                && (metadata.containsKey(METADATA_JAVA_LANGUAGE_LEVEL) || metadata.containsKey(METADATA_JAVA_PREVIEW))) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "metadata.javaLanguageLevel / javaPreview are only valid with explicit sourceRoots");
        }
    }

    private static boolean containsParentSegment(String value) {
        for (String part : value.split("/")) {
            if ("..".equals(part)) {
                return true;
            }
        }
        return false;
    }

    /**
     * request 由来の文字列を Path 化する。NUL 等を含む不正値の
     * {@link java.nio.file.InvalidPathException} を internal error でなく
     * invalid request として拒否する。
     */
    private static Path parseRequestPath(String value, String what) throws AnalyzerFatalException {
        try {
            return Path.of(value);
        } catch (java.nio.file.InvalidPathException e) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST, what + " is not a valid filesystem path");
        }
    }

    /**
     * 存在しない可能性のある path が workspace 配下かを判定する。実在する最寄りの
     * 祖先を real path 化して比較し、symlink 配下の workspace でも安定させる。
     */
    private static boolean withinWorkspace(Path path, Path workspaceRoot) {
        Path ancestor = path;
        while (ancestor != null && !Files.exists(ancestor)) {
            ancestor = ancestor.getParent();
        }
        if (ancestor == null) {
            return false;
        }
        return realPathWithin(ancestor, workspaceRoot);
    }

    private static boolean realPathWithin(Path path, Path workspaceRoot) {
        try {
            return path.toRealPath().startsWith(workspaceRoot.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static Path toRealPath(Path path, JavaErrorCode code) throws AnalyzerFatalException {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            throw new AnalyzerFatalException(code, "failed to resolve the real path of " + path.getFileName());
        }
    }

    private static Diagnostic sootUpUnavailableWarning(String contextId, String message) {
        return warning(JavaDiagnosticCode.JAVA_SOOTUP_UNAVAILABLE, message + " (context " + contextId + ")");
    }

    private static Diagnostic warning(JavaDiagnosticCode code, String message) {
        return Diagnostic.of(code.severity(), code.code(), message, null, null, null);
    }
}
