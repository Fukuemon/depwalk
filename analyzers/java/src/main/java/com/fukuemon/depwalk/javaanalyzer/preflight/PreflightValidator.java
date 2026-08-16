package com.fukuemon.depwalk.javaanalyzer.preflight;

import com.fukuemon.depwalk.javaanalyzer.JavaErrorCode;
import com.fukuemon.depwalk.javaanalyzer.protocol.AnalysisRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析開始前に一括で行う pre-flight 検査。
 * 型解決の途中で jar 欠落を遅延検出すると、出力済み record が「一見成功した出力」として観測され
 * うるため、解析開始前に一括で検査する。
 */
public final class PreflightValidator {

    private static final String LANGUAGE_JAVA = "java";
    private static final String METADATA_CLASSPATH = "classpath";
    private static final String METADATA_LIFT_EXCLUDE_PACKAGES = "liftExcludePackages";
    private static final String METADATA_ALLOW_INCOMPLETE_ANALYSIS = "allowIncompleteAnalysis";
    private static final String METADATA_GRADLE_JAVA_HOME = "gradleJavaHome";

    private PreflightValidator() {
    }

    /**
     * pre-flight 検査で確定した型付きの検証済み入力。
     * ここで検証していない Java 固有 metadata ({@code liftExcludePackages} 等) は、下流が
     * {@code request.metadata()} から改めて読み直す。
     *
     * @param classpath {@code metadata.classpath} の検証済み jar / classes dir path 一覧
     * @param allowIncompleteAnalysis {@code metadata.allowIncompleteAnalysis} の検証済み値 (既定 false)。
     *     true のとき、全救済後も残る primary diagnostic があっても request を fatal にせず、
     *     解決済み graph と診断を確認可能な形で公開する
     * @param gradleJavaHome {@code metadata.gradleJavaHome} の検証済み値 (自動 discovery 時のみ。
     *     null は未指定 = daemon JVM の選択を Gradle に委ねる)。明示 {@code sourceRoots} 経路は
     *     Gradle runtime を bypass するため解釈せず無視する
     */
    public record Validated(List<String> classpath, boolean allowIncompleteAnalysis, Path gradleJavaHome) {
    }

    /**
     * 解析要求と classpath metadata を検査し、下流が安全に使える型付き入力へ変換する。
     *
     * @param request Core から受信した解析要求
     * @return 型検証済みの入力値 ({@link Validated})
     * @throws AnalyzerFatalException {@code JAVA_INVALID_REQUEST} / {@code JAVA_MISSING_CLASSPATH} /
     *                                 {@code JAVA_MISSING_JAR} のいずれか
     */
    public static Validated validate(AnalysisRequest request) throws AnalyzerFatalException {
        if (!LANGUAGE_JAVA.equals(request.language())) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "unsupported language: " + request.language());
        }

        Map<String, Object> metadata = request.metadata();
        // classpath key は明示 sourceRoots 経路で必須 (空配列可)。自動 discovery
        // 経路では context classpath を Gradle model から取得するため、任意の
        // 共通追加 entry として扱う。
        boolean explicitSourceRoots = request.sourceRoots() != null;
        if (metadata == null || !metadata.containsKey(METADATA_CLASSPATH)) {
            if (explicitSourceRoots) {
                throw new AnalyzerFatalException(
                        JavaErrorCode.JAVA_MISSING_CLASSPATH,
                        "analysisRequest.metadata is missing required key \"classpath\"");
            }
        }

        List<String> classpath = metadata != null && metadata.containsKey(METADATA_CLASSPATH)
                ? readClasspath(metadata.get(METADATA_CLASSPATH))
                : List.of();
        for (String entry : classpath) {
            Path path = Path.of(entry);
            if (!Files.exists(path) || !Files.isReadable(path)) {
                throw new AnalyzerFatalException(
                        JavaErrorCode.JAVA_MISSING_JAR,
                        "classpath entry does not exist or is not readable: " + entry);
            }
        }

        validateWorkspaceRoot(request.workspaceRoot());
        boolean allowIncompleteAnalysis = false;
        Path gradleJavaHome = null;
        if (metadata != null) {
            validateLiftExcludePackages(metadata);
            allowIncompleteAnalysis = readAllowIncompleteAnalysis(metadata);
            if (!explicitSourceRoots) {
                gradleJavaHome = readGradleJavaHome(metadata);
            }
        }

        return new Validated(classpath, allowIncompleteAnalysis, gradleJavaHome);
    }

    /**
     * {@code gradleJavaHome} は自動 discovery の Gradle daemon JVM を明示 override する。
     * key 不在なら null (選択は Gradle に委ねる)。指定時は要素 1 の string で、実在する
     * directory かつ {@code bin/java} を持つ java home でなければ
     * {@code JAVA_INVALID_REQUEST} で fatal とする。
     */
    private static Path readGradleJavaHome(Map<String, Object> metadata) throws AnalyzerFatalException {
        if (!metadata.containsKey(METADATA_GRADLE_JAVA_HOME)) {
            return null;
        }
        Object raw = metadata.get(METADATA_GRADLE_JAVA_HOME);
        if (!(raw instanceof List<?> rawList) || rawList.size() != 1) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "analysisRequest.metadata.gradleJavaHome must be a single-element array");
        }
        if (!(rawList.get(0) instanceof String value) || value.isBlank()) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "analysisRequest.metadata.gradleJavaHome must contain a non-blank java home path");
        }
        Path javaHome;
        try {
            javaHome = Path.of(value);
        } catch (java.nio.file.InvalidPathException e) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "analysisRequest.metadata.gradleJavaHome is not a valid path");
        }
        boolean launchable = Files.isDirectory(javaHome)
                && (Files.isExecutable(javaHome.resolve("bin").resolve("java"))
                        || Files.isExecutable(javaHome.resolve("bin").resolve("java.exe")));
        if (!launchable) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "analysisRequest.metadata.gradleJavaHome does not point to a launchable java home: " + value);
        }
        return javaHome;
    }

    /**
     * {@code allowIncompleteAnalysis} は key 不在なら既定値 false (完全性 gate は従来どおり fatal)。
     * 指定時は要素 1 の {@code ["true"]} / {@code ["false"]} でなければ {@code JAVA_INVALID_REQUEST}
     * で fatal とする (javaPreview と同じ boolean flag 表現規約)。
     */
    private static boolean readAllowIncompleteAnalysis(Map<String, Object> metadata) throws AnalyzerFatalException {
        if (!metadata.containsKey(METADATA_ALLOW_INCOMPLETE_ANALYSIS)) {
            return false;
        }
        Object raw = metadata.get(METADATA_ALLOW_INCOMPLETE_ANALYSIS);
        if (raw instanceof List<?> rawList && rawList.size() == 1) {
            Object value = rawList.get(0);
            if ("true".equals(value)) {
                return true;
            }
            if ("false".equals(value)) {
                return false;
            }
        }
        throw new AnalyzerFatalException(
                JavaErrorCode.JAVA_INVALID_REQUEST,
                "analysisRequest.metadata.allowIncompleteAnalysis must be [\"true\"] or [\"false\"]");
    }

    /**
     * {@code workspaceRoot} が null / 空 / 存在しない / directory でない場合は {@code JAVA_INVALID_REQUEST}
     * で fatal とする。
     */
    private static void validateWorkspaceRoot(String workspaceRoot) throws AnalyzerFatalException {
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "analysisRequest.workspaceRoot must not be null or empty");
        }
        Path path = Path.of(workspaceRoot);
        if (!Files.exists(path)) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "analysisRequest.workspaceRoot does not exist: " + workspaceRoot);
        }
        if (!Files.isDirectory(path)) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "analysisRequest.workspaceRoot is not a directory: " + workspaceRoot);
        }
    }

    /**
     * {@code liftExcludePackages} は key 不在なら既定値 (呼び出し側で処理)。指定時は文字列配列でなければ
     * {@code JAVA_INVALID_REQUEST} で fatal とする。空配列は「除外なし」として正当。
     */
    private static void validateLiftExcludePackages(Map<String, Object> metadata) throws AnalyzerFatalException {
        if (!metadata.containsKey(METADATA_LIFT_EXCLUDE_PACKAGES)) {
            return;
        }
        readStringArray(
                metadata.get(METADATA_LIFT_EXCLUDE_PACKAGES),
                "analysisRequest.metadata.liftExcludePackages must be a string array",
                "analysisRequest.metadata.liftExcludePackages element must be a string: ");
    }

    private static List<String> readClasspath(Object value) throws AnalyzerFatalException {
        return readStringArray(
                value,
                "analysisRequest.metadata.classpath must be a string array",
                "classpath element must be a string: ");
    }

    /**
     * metadata 値を文字列配列として読む。配列でない場合と要素が文字列でない場合を
     * それぞれ {@code JAVA_INVALID_REQUEST} で fatal とする。
     *
     * @param value 検査対象の metadata 値
     * @param arrayMessage 配列でない場合の error message
     * @param elementMessagePrefix 要素が文字列でない場合の error message 前置き (末尾に値を連結する)
     * @return 宣言順の文字列一覧
     * @throws AnalyzerFatalException 配列でない、または文字列でない要素を含む場合
     */
    private static List<String> readStringArray(Object value, String arrayMessage, String elementMessagePrefix)
            throws AnalyzerFatalException {
        if (!(value instanceof List<?> rawList)) {
            throw new AnalyzerFatalException(JavaErrorCode.JAVA_INVALID_REQUEST, arrayMessage);
        }
        List<String> values = new ArrayList<>(rawList.size());
        for (Object element : rawList) {
            if (!(element instanceof String text)) {
                throw new AnalyzerFatalException(
                        JavaErrorCode.JAVA_INVALID_REQUEST, elementMessagePrefix + element);
            }
            values.add(text);
        }
        return List.copyOf(values);
    }
}
