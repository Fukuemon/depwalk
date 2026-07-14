package com.fukuemon.depwalk.javaanalyzer.analysis.spring;

import com.fukuemon.depwalk.javaanalyzer.JavaDiagnosticCode;
import com.fukuemon.depwalk.javaanalyzer.analysis.graph.GraphAccumulator;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.RelativePaths;
import com.fukuemon.depwalk.javaanalyzer.protocol.Diagnostic;
import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** P2 の中間結果を既存 {@link Diagnostic} schema へ非破壊で変換する。 */
public final class SpringDiagnosticEmitter {

    private SpringDiagnosticEmitter() {
    }

    public static void emit(
            SpringDiIndex.Result result,
            Path workspaceRoot,
            GraphAccumulator accumulator) {
        for (SpringDiIndex.InjectionResolution resolution : result.resolutions()) {
            switch (resolution.status()) {
                case UNIQUE -> {
                    // 診断不要。
                }
                case UNRESOLVED -> {
                    accumulator.incrementUnresolved();
                    add(
                            JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL,
                            "no Spring Bean candidate for " + resolution.injectionPoint().injectedType(),
                            resolution,
                            workspaceRoot,
                            accumulator);
                }
                case RUNTIME_PROVIDED -> add(
                        JavaDiagnosticCode.JAVA_RUNTIME_PROVIDED,
                        "implementation is provided at runtime for " + resolution.injectionPoint().injectedType(),
                        resolution,
                        workspaceRoot,
                        accumulator);
                case AMBIGUOUS -> {
                    if (resolution.candidates().size() > 1) {
                        add(
                                JavaDiagnosticCode.JAVA_AMBIGUOUS_CANDIDATE,
                                "multiple Spring Bean candidates remain for " + resolution.injectionPoint().injectedType(),
                                resolution,
                                workspaceRoot,
                                accumulator);
                    }
                    if (!conditionTypesOf(resolution).isEmpty()) {
                        add(
                                JavaDiagnosticCode.JAVA_CONDITIONAL_BEAN,
                                "conditional Spring Bean was retained without evaluating runtime conditions",
                                resolution,
                                workspaceRoot,
                                accumulator);
                    }
                }
            }
        }
    }

    private static void add(
            JavaDiagnosticCode code,
            String message,
            SpringDiIndex.InjectionResolution resolution,
            Path workspaceRoot,
            GraphAccumulator accumulator) {
        accumulator.addDiagnostic(Diagnostic.of(
                code.severity(),
                code.code(),
                message,
                sourceLocationOf(resolution.injectionPoint(), workspaceRoot),
                null,
                metadataOf(resolution)));
    }

    private static Map<String, Object> metadataOf(SpringDiIndex.InjectionResolution resolution) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("injectedType", resolution.injectionPoint().injectedType());
        metadata.put("targetName", resolution.injectionPoint().targetName());
        metadata.put("resolution", resolution.status().name().toLowerCase(java.util.Locale.ROOT));
        List<String> candidateTypes = resolution.candidates().stream()
                .map(candidate -> candidate.bean().implementationType())
                .distinct()
                .sorted()
                .toList();
        if (!candidateTypes.isEmpty()) {
            metadata.put("candidates", candidateTypes);
        }
        List<String> conditionTypes = conditionTypesOf(resolution);
        if (!conditionTypes.isEmpty()) {
            metadata.put("conditionTypes", conditionTypes);
        }
        return metadata;
    }

    private static List<String> conditionTypesOf(SpringDiIndex.InjectionResolution resolution) {
        Set<String> types = new LinkedHashSet<>();
        for (SpringDiIndex.BeanCandidate candidate : resolution.candidates()) {
            types.addAll(candidate.bean().conditionTypes());
        }
        return types.stream().sorted().toList();
    }

    private static SourceLocation sourceLocationOf(
            SpringDiIndex.InjectionPoint injection,
            Path workspaceRoot) {
        if (injection.sourcePath() == null || injection.sourceLine() <= 0) {
            return null;
        }
        Path sourcePath = Path.of(injection.sourcePath()).toAbsolutePath().normalize();
        if (!sourcePath.startsWith(workspaceRoot)) {
            return null;
        }
        return SourceLocation.of(
                RelativePaths.toRecordPath(workspaceRoot.relativize(sourcePath).toString()),
                injection.sourceLine());
    }
}
