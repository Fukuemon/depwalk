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

    private PreflightValidator() {
    }

    /**
     * @throws AnalyzerFatalException {@code JAVA_INVALID_REQUEST} / {@code JAVA_MISSING_CLASSPATH} /
     *                                 {@code JAVA_MISSING_JAR} のいずれか
     */
    public static void validate(AnalysisRequest request) throws AnalyzerFatalException {
        if (!LANGUAGE_JAVA.equals(request.language())) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "unsupported language: " + request.language());
        }

        Map<String, Object> metadata = request.metadata();
        if (metadata == null || !metadata.containsKey(METADATA_CLASSPATH)) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_MISSING_CLASSPATH,
                    "analysisRequest.metadata is missing required key \"classpath\"");
        }

        for (String entry : readClasspath(metadata.get(METADATA_CLASSPATH))) {
            Path path = Path.of(entry);
            if (!Files.exists(path) || !Files.isReadable(path)) {
                throw new AnalyzerFatalException(
                        JavaErrorCode.JAVA_MISSING_JAR,
                        "classpath entry does not exist or is not readable: " + entry);
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
