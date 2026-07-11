package com.fukuemon.depwalk.javaanalyzer.analysis;

import com.fukuemon.depwalk.javaanalyzer.JavaDiagnosticCode;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResolver;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.LiftExcludePackages;
import com.fukuemon.depwalk.javaanalyzer.analysis.graph.CallGraphBuilder;
import com.fukuemon.depwalk.javaanalyzer.analysis.graph.GraphAccumulator;
import com.fukuemon.depwalk.javaanalyzer.analysis.graph.ReachabilityFilter;
import com.fukuemon.depwalk.javaanalyzer.analysis.scope.ScopeFiles;
import com.fukuemon.depwalk.javaanalyzer.io.RecordWriter;
import com.fukuemon.depwalk.javaanalyzer.protocol.AnalysisRequest;
import com.fukuemon.depwalk.javaanalyzer.protocol.CallEdge;
import com.fukuemon.depwalk.javaanalyzer.protocol.Diagnostic;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSelector;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.Problem;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Java Analyzer 解析本体のオーケストレーション。P1_02 の scaffold (request 受領 / pre-flight /
 * {@link RecordWriter}) の上に、AST 解析・型解決・帰属型決定・record 生成を実装する。
 *
 * <p>性能方針 (design doc 「性能方針」): AST はファイル単位で逐次破棄する (保持するのは
 * SymbolSolver の型解決キャッシュと、node/edge/diagnostic の最小限の集計 = {@link GraphAccumulator})。
 * {@code reachableFromEntrypoints} は母集合全体からの到達可能性フィルタが必要なため、node/edge の
 * 集計自体は解析完了後に一括で {@link RecordWriter} へ書き出す (各 record 自体は書き込み時に
 * 即座に flush されるため、record 単位の streaming 契約は保たれる)。
 */
public final class AnalysisRunner {

    private static final String ANALYSIS_MODE_REACHABLE = "reachableFromEntrypoints";
    private static final String METADATA_CLASSPATH = "classpath";

    private AnalysisRunner() {
    }

    public record RunStats(long analyzedFileCount, long unresolvedCount) {
    }

    public static RunStats run(AnalysisRequest request, RecordWriter writer) throws IOException {
        Path workspaceRoot = Path.of(request.workspaceRoot()).toAbsolutePath().normalize();
        List<Path> scopeFileList = ScopeFiles.enumerate(workspaceRoot, request.include(), request.exclude());
        Set<Path> scopeFileSet = ScopeFiles.toMembershipSet(scopeFileList);

        List<String> classpathJars = readClasspath(request.metadata());
        CombinedTypeSolver typeSolver = TypeSolverFactory.create(workspaceRoot, classpathJars);
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        ParserConfiguration config = new ParserConfiguration().setSymbolResolver(symbolSolver);
        JavaParser parser = new JavaParser(config);

        LiftExcludePackages liftExcludePackages = LiftExcludePackages.fromMetadata(request.metadata());
        AttributionResolver attributionResolver = new AttributionResolver(scopeFileSet, liftExcludePackages);
        GraphAccumulator accumulator = new GraphAccumulator();
        CallGraphBuilder builder = new CallGraphBuilder(workspaceRoot, attributionResolver, accumulator);

        long analyzedFileCount = 0;
        for (Path file : scopeFileList) {
            ParseResult<CompilationUnit> result;
            try {
                result = parser.parse(file);
            } catch (IOException | ParseProblemException e) {
                writer.write(parseErrorDiagnostic(workspaceRoot, file, e.getMessage()));
                continue;
            }
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                writer.write(parseErrorDiagnostic(workspaceRoot, file, summarizeProblems(result.getProblems())));
                continue;
            }
            CompilationUnit cu = result.getResult().get();
            builder.process(cu);
            analyzedFileCount++;
            // cu はここでスコープを抜け、以降 GC 対象になる (AST の逐次破棄)。
        }

        List<MethodSymbol> nodesToWrite;
        List<CallEdge> edgesToWrite;
        if (ANALYSIS_MODE_REACHABLE.equals(request.analysisMode()) && hasEntrypoints(request)) {
            ReachabilityFilter.Result filtered = ReachabilityFilter.apply(accumulator, request.entrypoints());
            nodesToWrite = filtered.nodes();
            edgesToWrite = filtered.edges();
            for (String unmatched : filtered.unmatchedSelectors()) {
                writer.write(Diagnostic.of(
                        JavaDiagnosticCode.JAVA_ENTRYPOINT_NOT_FOUND.severity(),
                        JavaDiagnosticCode.JAVA_ENTRYPOINT_NOT_FOUND.code(),
                        "no method found for entrypoint selector: " + unmatched,
                        null,
                        null,
                        null));
            }
        } else {
            nodesToWrite = List.copyOf(accumulator.nodesByMethodId().values());
            edgesToWrite = accumulator.edges();
        }

        for (MethodSymbol node : nodesToWrite) {
            writer.write(node);
        }
        for (CallEdge edge : edgesToWrite) {
            writer.write(edge);
        }
        for (Diagnostic diagnostic : accumulator.diagnostics()) {
            writer.write(diagnostic);
        }

        return new RunStats(analyzedFileCount, accumulator.unresolvedCount());
    }

    private static boolean hasEntrypoints(AnalysisRequest request) {
        return request.entrypoints() != null && !request.entrypoints().isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static List<String> readClasspath(Map<String, Object> metadata) {
        Object raw = metadata.get(METADATA_CLASSPATH);
        return ((List<Object>) raw).stream().map(String.class::cast).toList();
    }

    private static Diagnostic parseErrorDiagnostic(Path workspaceRoot, Path file, String message) {
        String relative = workspaceRoot.relativize(file).toString();
        return Diagnostic.of(
                JavaDiagnosticCode.JAVA_PARSE_ERROR.severity(),
                JavaDiagnosticCode.JAVA_PARSE_ERROR.code(),
                message != null ? message : "failed to parse file",
                com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation.of(relative, 1),
                null,
                null);
    }

    private static String summarizeProblems(List<Problem> problems) {
        return problems.stream().map(Problem::getVerboseMessage).collect(Collectors.joining("; "));
    }
}
