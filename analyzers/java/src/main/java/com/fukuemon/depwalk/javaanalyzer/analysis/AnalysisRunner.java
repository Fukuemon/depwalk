package com.fukuemon.depwalk.javaanalyzer.analysis;

import com.fukuemon.depwalk.javaanalyzer.JavaDiagnosticCode;
import com.fukuemon.depwalk.javaanalyzer.JavaErrorCode;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResolver;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.LiftExcludePackages;
import com.fukuemon.depwalk.javaanalyzer.analysis.graph.CallGraphBuilder;
import com.fukuemon.depwalk.javaanalyzer.analysis.graph.GraphAccumulator;
import com.fukuemon.depwalk.javaanalyzer.analysis.graph.ReachabilityFilter;
import com.fukuemon.depwalk.javaanalyzer.analysis.graph.SourceMethodIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.context.AnalysisContextFactory;
import com.fukuemon.depwalk.javaanalyzer.analysis.context.ContextScope;
import com.fukuemon.depwalk.javaanalyzer.analysis.context.ParsePreflight;
import com.fukuemon.depwalk.javaanalyzer.analysis.context.SourceSetAnalysisContext;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.RelativePaths;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.fukuemon.depwalk.javaanalyzer.preflight.AnalyzerFatalException;
import com.fukuemon.depwalk.javaanalyzer.analysis.spring.SpringDiagnosticEmitter;
import com.fukuemon.depwalk.javaanalyzer.analysis.spring.SpringDiIndex;
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
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 検証済みの解析要求を受け取り、Java の解析から protocol record 出力までをオーケストレーションする。
 *
 * <p>source file の列挙、AST 解析、型解決、Spring DI 索引化、呼び出し先候補の統合、帰属型決定、
 * 到達可能性フィルタ、{@link RecordWriter} への出力を1回の実行として調停する。
 *
 * <p>AST はファイル単位で逐次破棄する (保持するのは
 * SymbolSolver の型解決キャッシュと、node/edge/diagnostic の最小限の集計 = {@link GraphAccumulator})。
 * record の書き出しはモードによって次のように異なる:
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

    /**
     * 1回の Analyzer 実行で集計した処理件数。
     *
     * @param analyzedFileCount AST 解析と graph 生成を完了した source file 数
     * @param unresolvedCount call edge または DI 候補を解決できなかった件数
     * @param parsePreflightMillis 全 file parse pre-flight の所要時間 (通常解析と分離して計測)
     */
    public record RunStats(long analyzedFileCount, long unresolvedCount, long parsePreflightMillis) {
    }

    /**
     * 解析要求を実行し、method symbol、call edge、diagnostic を writer へ出力する。
     *
     * @param request workspace、解析 mode、entrypoint、metadata を含む検証済み解析要求
     * @param contextResult 構築済みの解析 context と非 fatal warning
     * @param writer JSONL protocol record の出力先
     * @return 解析したファイル数、未解決件数、pre-flight 時間
     * @throws IOException source の列挙・読み込みまたは protocol record の出力に失敗した場合
     * @throws AnalyzerFatalException scope の binary name 衝突または parse pre-flight 失敗
     */
    public static RunStats run(
            AnalysisRequest request,
            AnalysisContextFactory.Result contextResult,
            RecordWriter writer) throws IOException, AnalyzerFatalException {
        Path workspaceRoot = Path.of(request.workspaceRoot()).toAbsolutePath().normalize();
        List<SourceSetAnalysisContext> contexts = contextResult.contexts();
        ContextScope.Scope scope =
                ContextScope.enumerate(workspaceRoot, contexts, request.include(), request.exclude());

        Map<String, SourceSetAnalysisContext> contextById = new LinkedHashMap<>();
        for (SourceSetAnalysisContext context : contexts) {
            contextById.put(context.id(), context);
        }
        Map<Path, SourceSetAnalysisContext> contextByFile = new LinkedHashMap<>();
        for (SourceSetAnalysisContext context : contexts) {
            for (Path file : scope.filesByContext().get(context.id())) {
                contextByFile.put(file, context);
            }
        }

        // context ごとの parser / TypeSolver。solver には自 root、Gradle project
        // 依存で到達可能な context の root、自 context の外部 entry だけを登録し、
        // 非依存 module や異なる依存 version を混在させない (D6)。
        Map<String, JavaParser> parserByContext = new LinkedHashMap<>();
        for (SourceSetAnalysisContext context : contexts) {
            List<Path> solverRoots = new ArrayList<>(context.sourceRoots());
            for (String dependencyId : reachableDependencyIds(context, contextById)) {
                SourceSetAnalysisContext dependency = contextById.get(dependencyId);
                if (dependency != null) {
                    solverRoots.addAll(dependency.sourceRoots());
                }
            }
            List<Path> solverEntries = new ArrayList<>(context.classpath());
            for (Path output : context.classesOutputs()) {
                if (!solverEntries.contains(output)) {
                    solverEntries.add(output);
                }
            }
            CombinedTypeSolver typeSolver =
                    TypeSolverFactory.createForRoots(solverRoots, solverEntries, context.languageLevel());
            ParserConfiguration config = new ParserConfiguration()
                    .setSymbolResolver(new JavaSymbolSolver(typeSolver))
                    .setLanguageLevel(context.languageLevel());
            parserByContext.put(context.id(), new JavaParser(config));
        }

        // graph record 出力前に全 file の parse を検証する。失敗は request 全体 fatal。
        long preflightMillis =
                ParsePreflight.verify(workspaceRoot, scope.allFiles(), contextByFile, parserByContext);

        // pre-flight 成功後に context 構築時の warning を出力する (fatal 時は 1 件も出さない)。
        for (Diagnostic warning : contextResult.warnings()) {
            writer.write(warning);
        }

        LiftExcludePackages liftExcludePackages = LiftExcludePackages.fromMetadata(request.metadata());
        AttributionResolver attributionResolver = new AttributionResolver(scope.membership(), liftExcludePackages);

        // SootUp index は context ごとに分離する (D6)。Spring DI の Bean 母集合は
        // request 全体の意味論 (module 間 DI 解決が成功条件) のため、DI 索引だけは
        // 全 context の entry を統合した index を使う。
        Map<String, SootUpTypeHierarchyIndex> sootUpByContext = new LinkedHashMap<>();
        Set<String> unionEntries = new LinkedHashSet<>();
        for (SourceSetAnalysisContext context : contexts) {
            List<String> entries = new ArrayList<>();
            for (Path path : context.classpath()) {
                entries.add(path.toString());
            }
            for (Path path : context.classesOutputs()) {
                entries.add(path.toString());
            }
            unionEntries.addAll(entries);
            sootUpByContext.put(context.id(), SootUpTypeHierarchyIndex.fromClasspath(entries));
        }
        SpringDiIndex springDiIndex =
                SpringDiIndex.create(SootUpTypeHierarchyIndex.fromClasspath(List.copyOf(unionEntries)));
        SourceMethodIndex sourceMethodIndex = new SourceMethodIndex(workspaceRoot);
        GraphAccumulator accumulator = new GraphAccumulator();

        // Spring Bean/注入点と候補 method の source location は compact な first-pass index に落とす。
        // CompilationUnit は各 iteration で破棄し、fullGraph の AST 逐次破棄契約を維持する。
        for (Path file : scope.allFiles()) {
            JavaParser parser = parserByContext.get(contextByFile.get(file).id());
            try {
                ParseResult<CompilationUnit> result = parser.parse(file);
                if (result.isSuccessful() && result.getResult().isPresent()) {
                    CompilationUnit unit = result.getResult().get();
                    springDiIndex.accept(unit);
                    sourceMethodIndex.accept(unit);
                }
            } catch (IOException | ParseProblemException e) {
                // pre-flight で全 file の parse 成功を確認済みのため、ここへの到達は内部エラー。
                throw new IllegalStateException("file failed to parse after a successful pre-flight: " + file, e);
            } catch (RuntimeException | LinkageError e) {
                accumulator.incrementUnresolved();
                accumulator.addDiagnostic(Diagnostic.of(
                        JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.severity(),
                        JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.code(),
                        "failed to index Spring DI metadata: " + e.getMessage(),
                        com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation.of(
                                RelativePaths.toRecordPath(workspaceRoot.relativize(file).toString()), 1),
                        null,
                        null));
            }
        }
        SpringDiIndex.Result springResult = springDiIndex.build();

        SpringDiagnosticEmitter.emit(springResult, workspaceRoot, accumulator);
        Map<String, CallGraphBuilder> builderByContext = new LinkedHashMap<>();
        for (SourceSetAnalysisContext context : contexts) {
            builderByContext.put(context.id(), new CallGraphBuilder(
                    workspaceRoot,
                    attributionResolver,
                    accumulator,
                    sootUpByContext.get(context.id()),
                    sourceMethodIndex,
                    springResult));
        }

        boolean reachableMode = ANALYSIS_MODE_REACHABLE.equals(request.analysisMode()) && hasEntrypoints(request);

        // fullGraph (streaming) 用の「出力済み」進捗マーカー。reachableMode では未使用。
        Set<String> writtenMethodIds = new HashSet<>();
        int edgesWrittenCount = 0;
        int diagnosticsWrittenCount = 0;

        long analyzedFileCount = 0;
        for (Path file : scope.allFiles()) {
            SourceSetAnalysisContext context = contextByFile.get(file);
            ParseResult<CompilationUnit> result;
            try {
                result = parserByContext.get(context.id()).parse(file);
            } catch (IOException | ParseProblemException e) {
                throw new IllegalStateException("file failed to parse after a successful pre-flight: " + file, e);
            }
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                throw new IllegalStateException("file failed to parse after a successful pre-flight: " + file);
            }
            CompilationUnit cu = result.getResult().get();
            builderByContext.get(context.id()).process(cu);
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

        return new RunStats(analyzedFileCount, accumulator.unresolvedCount(), preflightMillis);
    }

    private static boolean hasEntrypoints(AnalysisRequest request) {
        return request.entrypoints() != null && !request.entrypoints().isEmpty();
    }

    /** Gradle project 依存で推移的に到達可能な context id を返す (自身は含めない)。 */
    private static Set<String> reachableDependencyIds(
            SourceSetAnalysisContext context, Map<String, SourceSetAnalysisContext> contextById) {
        Set<String> reachable = new LinkedHashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>(context.dependencyContextIds());
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (id.equals(context.id()) || !reachable.add(id)) {
                continue;
            }
            SourceSetAnalysisContext dependency = contextById.get(id);
            if (dependency != null) {
                queue.addAll(dependency.dependencyContextIds());
            }
        }
        return reachable;
    }
}
