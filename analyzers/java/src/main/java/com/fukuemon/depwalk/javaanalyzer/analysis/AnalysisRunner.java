package com.fukuemon.depwalk.javaanalyzer.analysis;

import com.fukuemon.depwalk.javaanalyzer.JavaDiagnosticCode;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResolver;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.LiftExcludePackages;
import com.fukuemon.depwalk.javaanalyzer.analysis.graph.CallGraphBuilder;
import com.fukuemon.depwalk.javaanalyzer.analysis.graph.GraphAccumulator;
import com.fukuemon.depwalk.javaanalyzer.analysis.graph.ReachabilityFilter;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.RelativePaths;
import com.fukuemon.depwalk.javaanalyzer.analysis.scope.ScopeFiles;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.fukuemon.depwalk.javaanalyzer.io.RecordWriter;
import com.fukuemon.depwalk.javaanalyzer.protocol.AnalysisRequest;
import com.fukuemon.depwalk.javaanalyzer.protocol.CallEdge;
import com.fukuemon.depwalk.javaanalyzer.protocol.Diagnostic;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Java Analyzer 解析本体のオーケストレーション。P1_02 の scaffold (request 受領 / pre-flight /
 * {@link RecordWriter}) の上に、AST 解析・型解決・帰属型決定・record 生成を実装する。
 *
 * <p>性能方針 (design doc 「性能方針」/ D9): AST はファイル単位で逐次破棄する (保持するのは
 * SymbolSolver の型解決キャッシュと、node/edge/diagnostic の最小限の集計 = {@link GraphAccumulator})。
 * record の書き出しはモードによって挙動が異なる (M1):
 * <ul>
 *   <li>{@code fullGraph} (既定 / {@code reachableFromEntrypoints} かつ entrypoints 空の場合を含む):
 *       ファイル単位の解析が終わるごとに、その時点で新たに確定した node / edge / diagnostic を
 *       即座に {@link RecordWriter} へ flush する。母集合全体を待たずに record を逐次出力するため、
 *       グラフ全体をメモリ保持しない (node の重複出力を避けるため、出力済み {@code methodId} の集合
 *       のみ保持する)。</li>
 *   <li>{@code reachableFromEntrypoints} (entrypoints 指定あり): 到達可能性フィルタ
 *       ({@link ReachabilityFilter}) が母集合全体の adjacency を必要とするため、node / edge は
 *       解析完了後に一括でフィルタ + 書き出しを行う。diagnostic はこのモードでも検出時に即 write
 *       する。</li>
 * </ul>
 */
public final class AnalysisRunner {

    private static final String ANALYSIS_MODE_REACHABLE = "reachableFromEntrypoints";

    private AnalysisRunner() {
    }

    public record RunStats(long analyzedFileCount, long unresolvedCount) {
    }

    /**
     * @param classpath pre-flight ({@code PreflightValidator#validate}) で型検証済みの
     *                  jar / classes dir path 一覧。raw metadata をここで再 cast しない。
     */
    public static RunStats run(AnalysisRequest request, List<String> classpath, RecordWriter writer) throws IOException {
        Path workspaceRoot = Path.of(request.workspaceRoot()).toAbsolutePath().normalize();
        List<Path> scopeFileList = ScopeFiles.enumerate(workspaceRoot, request.include(), request.exclude());
        Set<Path> scopeFileSet = ScopeFiles.toMembershipSet(scopeFileList);

        // JavaParser の既定 languageLevel (POPULAR = JAVA_11 相当) は record を構文サポートしないため
        // (「Record Declarations are not supported... starting from 'JAVA_14'」でパース自体が失敗する)、
        // toolchain の JDK バージョン (build.gradle) に合わせて JAVA_25 まで引き上げる (メインパーサ /
        // JavaParserTypeSolver 内部パーサの両方に一致させる必要がある)。
        ParserConfiguration.LanguageLevel languageLevel = ParserConfiguration.LanguageLevel.JAVA_25;
        CombinedTypeSolver typeSolver = TypeSolverFactory.create(workspaceRoot, classpath, languageLevel);
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        ParserConfiguration config = new ParserConfiguration()
                .setSymbolResolver(symbolSolver)
                .setLanguageLevel(languageLevel);
        JavaParser parser = new JavaParser(config);

        LiftExcludePackages liftExcludePackages = LiftExcludePackages.fromMetadata(request.metadata());
        AttributionResolver attributionResolver = new AttributionResolver(scopeFileSet, liftExcludePackages);
        GraphAccumulator accumulator = new GraphAccumulator();
        SootUpTypeHierarchyIndex sootUpIndex = SootUpTypeHierarchyIndex.fromClasspath(classpath);
        CallGraphBuilder builder = new CallGraphBuilder(
                workspaceRoot, attributionResolver, accumulator, sootUpIndex);

        boolean reachableMode = ANALYSIS_MODE_REACHABLE.equals(request.analysisMode()) && hasEntrypoints(request);

        // fullGraph (streaming) 用の「出力済み」進捗マーカー。reachableMode では未使用。
        Set<String> writtenMethodIds = new HashSet<>();
        int edgesWrittenCount = 0;
        int diagnosticsWrittenCount = 0;

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

            if (!reachableMode) {
                for (MethodSymbol node : accumulator.nodesByMethodId().values()) {
                    if (writtenMethodIds.add(node.methodId())) {
                        writer.write(node);
                    }
                }
                List<CallEdge> allEdges = accumulator.edges();
                for (int i = edgesWrittenCount; i < allEdges.size(); i++) {
                    writer.write(allEdges.get(i));
                }
                edgesWrittenCount = allEdges.size();

                List<Diagnostic> allDiagnostics = accumulator.diagnostics();
                for (int i = diagnosticsWrittenCount; i < allDiagnostics.size(); i++) {
                    writer.write(allDiagnostics.get(i));
                }
                diagnosticsWrittenCount = allDiagnostics.size();
            }
        }

        if (reachableMode) {
            ReachabilityFilter.Result filtered = ReachabilityFilter.apply(accumulator, request.entrypoints());
            for (String unmatched : filtered.unmatchedSelectors()) {
                writer.write(Diagnostic.of(
                        JavaDiagnosticCode.JAVA_ENTRYPOINT_NOT_FOUND.severity(),
                        JavaDiagnosticCode.JAVA_ENTRYPOINT_NOT_FOUND.code(),
                        "no method found for entrypoint selector: " + unmatched,
                        null,
                        null,
                        null));
            }
            for (MethodSymbol node : filtered.nodes()) {
                writer.write(node);
            }
            for (CallEdge edge : filtered.edges()) {
                writer.write(edge);
            }
            for (Diagnostic diagnostic : accumulator.diagnostics()) {
                writer.write(diagnostic);
            }
        }

        return new RunStats(analyzedFileCount, accumulator.unresolvedCount());
    }

    private static boolean hasEntrypoints(AnalysisRequest request) {
        return request.entrypoints() != null && !request.entrypoints().isEmpty();
    }

    private static Diagnostic parseErrorDiagnostic(Path workspaceRoot, Path file, String message) {
        String relative = RelativePaths.toRecordPath(workspaceRoot.relativize(file).toString());
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
