package com.fukuemon.depwalk.javaanalyzer.preflight;

import com.fukuemon.depwalk.javaanalyzer.JavaErrorCode;
import com.fukuemon.depwalk.javaanalyzer.protocol.AnalysisRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 解析開始前に一括で行う pre-flight 検査。
 * 正本: design/features/java-analyzer/DesignDoc_java-analyzer.md 「pre-flight 検査」「metadata 契約」。
 * 型解決の途中で jar 欠落を遅延検出すると、出力済み record が「一見成功した出力」として観測され
 * うるため、解析開始前に一括で検査する。
 */
public final class PreflightValidator {

    private static final String LANGUAGE_JAVA = "java";
    private static final String METADATA_CLASSPATH = "classpath";
    private static final String METADATA_LIFT_EXCLUDE_PACKAGES = "liftExcludePackages";

    private PreflightValidator() {
    }

    /**
     * pre-flight 検査で確定した型付きの検証済み入力。下流 ({@code AnalysisRunner}) は raw metadata を
     * 再 cast せず本 record の値を使う。
     *
     * @param classpath {@code metadata.classpath} の検証済み jar / classes dir path 一覧
     */
    public record Validated(List<String> classpath) {
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
        // 共通追加 entry として扱う (spec #24 D6)。
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
        if (metadata != null) {
            validateLiftExcludePackages(metadata);
        }

        return new Validated(classpath);
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
        Object raw = metadata.get(METADATA_LIFT_EXCLUDE_PACKAGES);
        if (!(raw instanceof List<?> rawList)) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "analysisRequest.metadata.liftExcludePackages must be a string array");
        }
        for (Object element : rawList) {
            if (!(element instanceof String)) {
                throw new AnalyzerFatalException(
                        JavaErrorCode.JAVA_INVALID_REQUEST,
                        "analysisRequest.metadata.liftExcludePackages element must be a string: " + element);
            }
        }
    }

    private static List<String> readClasspath(Object value) throws AnalyzerFatalException {
        if (!(value instanceof List<?> rawList)) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "analysisRequest.metadata.classpath must be a string array");
        }
        try {
            return rawList.stream()
                    .map(element -> {
                        if (!(element instanceof String s)) {
                            throw new IllegalArgumentException("classpath element must be a string: " + element);
                        }
                        return s;
                    })
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new AnalyzerFatalException(JavaErrorCode.JAVA_INVALID_REQUEST, e.getMessage());
        }
    }
}
