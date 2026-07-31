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
 * 正本: design/features/java-analyzer/DesignDoc_java-analyzer.md 「pre-flight 検査」「metadata 契約」。
 * 型解決の途中で jar 欠落を遅延検出すると、出力済み record が「一見成功した出力」として観測され
 * うるため、解析開始前に一括で検査する。
 */
public final class PreflightValidator {

    private static final String LANGUAGE_JAVA = "java";
    private static final String METADATA_CLASSPATH = "classpath";
    private static final String METADATA_LIFT_EXCLUDE_PACKAGES = "liftExcludePackages";
    private static final String METADATA_ALLOW_INCOMPLETE_ANALYSIS = "allowIncompleteAnalysis";

    private PreflightValidator() {
    }

    /**
     * pre-flight 検査で確定した型付きの検証済み入力。本 record の値は起動時の呼び出し側 (entry point) が
     * 受け取り、{@code classpath} は解析 context 構築 ({@code AnalysisContextFactory}) へ、
     * {@code allowIncompleteAnalysis} は
     * {@link com.fukuemon.depwalk.javaanalyzer.analysis.pipeline.AnalysisRunner} の引数として渡す。
     * ここで検証していない Java 固有 metadata ({@code liftExcludePackages} 等) は、下流が
     * {@code request.metadata()} から改めて読み直す。
     *
     * @param classpath {@code metadata.classpath} の検証済み jar / classes dir path 一覧
     * @param allowIncompleteAnalysis {@code metadata.allowIncompleteAnalysis} の検証済み値 (既定 false)。
     *     true のとき、全救済後も残る primary diagnostic があっても request を fatal にせず、
     *     解決済み graph と診断を確認可能な形で公開する
     *     (java-analyzer feature doc「Parse・resolution・call 完全性」)
     */
    public record Validated(List<String> classpath, boolean allowIncompleteAnalysis) {
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
        // 共通追加 entry として扱う
        // (java-analyzer feature doc「Source root discovery と解析 context」)。
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
        if (metadata != null) {
            validateLiftExcludePackages(metadata);
            allowIncompleteAnalysis = readAllowIncompleteAnalysis(metadata);
        }

        return new Validated(classpath, allowIncompleteAnalysis);
    }

    /**
     * {@code allowIncompleteAnalysis} は key 不在なら既定値 false (完全性 gate は従来どおり fatal)。
     * 指定時は要素 1 の {@code ["true"]} / {@code ["false"]} でなければ {@code JAVA_INVALID_REQUEST}
     * で fatal とする (java-analyzer feature doc「metadata 契約」、javaPreview と同じ boolean flag
     * 表現規約)。
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
