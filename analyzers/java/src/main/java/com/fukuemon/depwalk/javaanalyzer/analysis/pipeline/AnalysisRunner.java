package com.fukuemon.depwalk.javaanalyzer.analysis.pipeline;

import com.fukuemon.depwalk.javaanalyzer.JavaDiagnosticCode;
import com.fukuemon.depwalk.javaanalyzer.JavaErrorCode;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.AttributionResolver;
import com.fukuemon.depwalk.javaanalyzer.analysis.attribution.LiftExcludePackages;
import com.fukuemon.depwalk.javaanalyzer.analysis.graph.CallGraphBuilder;
import com.fukuemon.depwalk.javaanalyzer.analysis.graph.GraphAccumulator;
import com.fukuemon.depwalk.javaanalyzer.analysis.graph.ReachabilityFilter;
import com.fukuemon.depwalk.javaanalyzer.analysis.graph.SourceMethodIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteInventory;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteOutcomeLedger;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.CallSiteId;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.IncompleteAnalysisException;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.ProjectBytecodeMemberIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.completeness.WorkspaceSourceDeclarationIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.context.AnalysisContextFactory;
import com.fukuemon.depwalk.javaanalyzer.analysis.context.ContextScope;
import com.fukuemon.depwalk.javaanalyzer.analysis.context.ParsePreflight;
import com.fukuemon.depwalk.javaanalyzer.analysis.context.ResolvedDeclarationOrigin;
import com.fukuemon.depwalk.javaanalyzer.analysis.context.SolverOriginIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.context.SourceSetAnalysisContext;
import com.fukuemon.depwalk.javaanalyzer.analysis.context.TypeSolverFactory;
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
     * @param contextBuildMillis context 別 TypeSolver / parser 構築の所要時間 (D8 の分離計測)
     * @param callSiteSummary call site ledger の総数と終端種別・理由別集計 (stderr 用)
     */
    public record RunStats(
            long analyzedFileCount,
            long unresolvedCount,
            long parsePreflightMillis,
            long contextBuildMillis,
            String callSiteSummary) {
    }

    /**
     * 解析要求を実行し、method symbol、call edge、diagnostic を writer へ出力する。
     *
     * @param request workspace、解析 mode、entrypoint、metadata を含む検証済み解析要求
     * @param contextResult 構築済みの解析 context と非 fatal warning
     * @param writer JSONL protocol record の出力先
     * @param allowIncompleteAnalysis true のとき、全救済後も残る primary diagnostic があっても
     *     request を fatal にせず、解決済み graph と診断を公開する ({@code metadata.allowIncompleteAnalysis}、spec #27)
     * @return 解析したファイル数、未解決件数、pre-flight 時間
     * @throws IOException source の列挙・読み込みまたは protocol record の出力に失敗した場合
     * @throws AnalyzerFatalException scope の binary name 衝突または parse pre-flight 失敗
     */
    public static RunStats run(
            AnalysisRequest request,
            AnalysisContextFactory.Result contextResult,
            RecordWriter writer,
            boolean allowIncompleteAnalysis) throws IOException, AnalyzerFatalException, IncompleteAnalysisException {
        // source root は real path で保持されるため、record path / glob の基準も
        // real path の workspaceRoot に揃える (symlink を含む workspace 対策)。
        Path workspaceRoot;
        try {
            workspaceRoot = Path.of(request.workspaceRoot()).toRealPath();
        } catch (IOException e) {
            throw new AnalyzerFatalException(
                    JavaErrorCode.JAVA_INVALID_REQUEST,
                    "failed to resolve the real path of analysisRequest.workspaceRoot");
        }
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
        // 各 classes output の所有 context (origin 判定用)。
        Map<Path, String> classesOutputOwners = new LinkedHashMap<>();
        for (SourceSetAnalysisContext context : contexts) {
            for (Path output : context.classesOutputs()) {
                classesOutputOwners.putIfAbsent(output, context.id());
            }
        }

        long contextBuildStart = System.nanoTime();
        // SootUp index は lazy のため全 context 分を先に用意し、solver の
        // bytecode member 合成 (D31) と builder の候補解決で同一 instance を共有する。
        // D31 の合成は「呼出元 context の classpath 視点」で行う (依存 project の型も
        // 自 context の classpath に含まれる classes output から引く)。emit 時に
        // declIndex + 到達可能 context の検査 (D16) で owner を制約する。
        Map<String, SootUpTypeHierarchyIndex> sootUpByContext = new LinkedHashMap<>();
        Map<String, ProjectBytecodeMemberIndex> bytecodeIndexByContext = new LinkedHashMap<>();
        for (SourceSetAnalysisContext context : contexts) {
            // project 所有の classes output (自 context + 依存 project の output) を
            // external jar より先に登録し、同名 class は project bytecode を優先
            // する。member 救済の origin 検証 (D16) にも同じ一覧を渡す。
            // 依存 project の output は classpath の形に依存せず model の project
            // 依存関係から解決する (spec #27 ⑧: Gradle model は依存 project を jar
            // として classpath へ返すことがあり、classpath 照合だけでは依存 output
            // が external artifact 扱いになって cross-module 救済が拒否されていた)。
            List<Path> projectOutputs = new ArrayList<>(context.classesOutputs());
            for (String dependencyId : reachableDependencyIds(context, contextById)) {
                SourceSetAnalysisContext dependency = contextById.get(dependencyId);
                if (dependency == null) {
                    continue;
                }
                for (Path output : dependency.classesOutputs()) {
                    if (!projectOutputs.contains(output)) {
                        projectOutputs.add(output);
                    }
                }
            }
            for (Path path : context.classpath()) {
                if (classesOutputOwners.containsKey(path) && !projectOutputs.contains(path)) {
                    projectOutputs.add(path);
                }
            }
            List<String> entries = new ArrayList<>();
            for (Path path : projectOutputs) {
                entries.add(path.toString());
            }
            for (Path path : context.classpath()) {
                if (!classesOutputOwners.containsKey(path)) {
                    entries.add(path.toString());
                }
            }
            SootUpTypeHierarchyIndex index = SootUpTypeHierarchyIndex.fromClasspath(entries);
            sootUpByContext.put(context.id(), index);
            bytecodeIndexByContext.put(context.id(),
                    new ProjectBytecodeMemberIndex(index, projectOutputs));
        }

        Map<String, JavaParser> parserByContext = new LinkedHashMap<>();
        Map<String, SolverOriginIndex> originsByContext = new LinkedHashMap<>();
        for (SourceSetAnalysisContext context : contexts) {
            SolverOriginIndex origins = new SolverOriginIndex();
            List<Path> solverRoots = new ArrayList<>(context.sourceRoots());
            for (Path root : context.sourceRoots()) {
                origins.register(root, ResolvedDeclarationOrigin.source(context.id()));
            }
            for (String dependencyId : reachableDependencyIds(context, contextById)) {
                SourceSetAnalysisContext dependency = contextById.get(dependencyId);
                if (dependency != null) {
                    solverRoots.addAll(dependency.sourceRoots());
                    for (Path root : dependency.sourceRoots()) {
                        origins.register(root, ResolvedDeclarationOrigin.source(dependencyId));
                    }
                }
            }
            List<Path> solverEntries = new ArrayList<>(context.classpath());
            for (Path output : context.classesOutputs()) {
                if (!solverEntries.contains(output)) {
                    solverEntries.add(output);
                }
            }
            for (Path entry : solverEntries) {
                String owner = classesOutputOwners.get(entry);
                origins.register(entry, owner != null
                        ? ResolvedDeclarationOrigin.projectClasses(owner)
                        : ResolvedDeclarationOrigin.externalArtifact(entry.toString()));
            }
            CombinedTypeSolver typeSolver = TypeSolverFactory.createForRoots(
                    solverRoots, solverEntries, context.languageLevel(), bytecodeIndexByContext.get(context.id()));
            ParserConfiguration config = new ParserConfiguration()
                    .setSymbolResolver(new JavaSymbolSolver(typeSolver))
                    .setLanguageLevel(context.languageLevel());
            parserByContext.put(context.id(), new JavaParser(config));
            originsByContext.put(context.id(), origins);
        }
        long contextBuildMillis = (System.nanoTime() - contextBuildStart) / 1_000_000;

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
        // DI 索引の union は file を持つ context の entry だけで構成する。
        Set<String> unionEntries = new LinkedHashSet<>();
        for (SourceSetAnalysisContext context : contexts) {
            if (scope.filesByContext().get(context.id()).isEmpty()) {
                continue;
            }
            for (Path path : context.classpath()) {
                unionEntries.add(path.toString());
            }
            for (Path path : context.classesOutputs()) {
                unionEntries.add(path.toString());
            }
        }
        SpringDiIndex springDiIndex =
                SpringDiIndex.create(SootUpTypeHierarchyIndex.fromClasspath(List.copyOf(unionEntries)));
        SourceMethodIndex sourceMethodIndex = new SourceMethodIndex(workspaceRoot);
        GraphAccumulator accumulator = new GraphAccumulator();
        // resolver とは独立した call-site inventory と source 宣言索引 (spec #24 D17 / D18)。
        CallSiteInventory inventory = new CallSiteInventory(workspaceRoot);
        WorkspaceSourceDeclarationIndex declIndex = new WorkspaceSourceDeclarationIndex(workspaceRoot);
        CallSiteOutcomeLedger ledger = new CallSiteOutcomeLedger(inventory);

        // Spring Bean/注入点と候補 method の source location は compact な first-pass index に落とす。
        // CompilationUnit は各 iteration で破棄し、fullGraph の AST 逐次破棄契約を維持する。
        for (Path file : scope.allFiles()) {
            JavaParser parser = parserByContext.get(contextByFile.get(file).id());
            CompilationUnit unit;
            try {
                ParseResult<CompilationUnit> result = parser.parse(file);
                if (!result.isSuccessful() || result.getResult().isEmpty()) {
                    throw new IllegalStateException("file failed to parse after a successful pre-flight: " + file);
                }
                unit = result.getResult().get();
            } catch (IOException | ParseProblemException e) {
                // pre-flight で全 file の parse 成功を確認済みのため、ここへの到達は内部エラー。
                throw new IllegalStateException("file failed to parse after a successful pre-flight: " + file, e);
            }
            // inventory / 宣言索引の不変条件違反は diagnostic へ降格せず internal fatal のまま伝播させる。
            inventory.accept(unit);
            declIndex.accept(unit, contextByFile.get(file).id());
            try {
                springDiIndex.accept(unit);
                sourceMethodIndex.accept(unit);
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
            if (scope.filesByContext().get(context.id()).isEmpty()) {
                continue;
            }
            Set<String> reachable = new LinkedHashSet<>();
            reachable.add(context.id());
            reachable.addAll(reachableDependencyIds(context, contextById));
            builderByContext.put(context.id(), new CallGraphBuilder(
                    workspaceRoot,
                    attributionResolver,
                    accumulator,
                    sootUpByContext.get(context.id()),
                    sourceMethodIndex,
                    springResult,
                    originsByContext.get(context.id()),
                    ledger,
                    declIndex,
                    bytecodeIndexByContext.get(context.id()),
                    context.id(),
                    reachable));
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

        // 完全性 gate (spec #24 D20 / D22): 全 entry の分類を検査し、primary
        // diagnostic が残れば全件 details 付きで request 全体を fatal にする。
        ledger.validateComplete();
        Map<CallSiteId, CallSiteOutcomeLedger.Outcome> primary = ledger.primaryDiagnostics();
        if (!primary.isEmpty() && !allowIncompleteAnalysis) {
            // fatal は先行 warning record を無効化するため、SootUp を利用できず
            // source-only で解析した context 数を upstream cause として error
            // metadata へ自己完結に保持する (bytecode 救済欠如が原因の診断補助)。
            long sootUpUnavailableContexts = contexts.stream()
                    .filter(SourceSetAnalysisContext::sootUpUnavailable)
                    .count();
            throw incompleteAnalysis(primary, sootUpUnavailableContexts);
        }
        // allowIncompleteAnalysis=true (spec #27): primary diagnostic が残っても
        // request を fatal にせず success として完了する。解決済み edge / 明示除外は
        // 通常どおり公開され、残存 primary diagnostic は各 call site の検出時点で
        // 既に streaming 済みの diagnostic record (reportUnresolved 経由) と、
        // callSiteSummary の diagnostic[...] 集計で確認できる。graph が部分的で
        // あることを隠さない。

        return new RunStats(
                analyzedFileCount,
                accumulator.unresolvedCount(),
                preflightMillis,
                contextBuildMillis,
                ledger.summary());
    }

    private static IncompleteAnalysisException incompleteAnalysis(
            Map<CallSiteId, CallSiteOutcomeLedger.Outcome> primary, long sootUpUnavailableContexts) {
        List<com.fukuemon.depwalk.javaanalyzer.protocol.FailureDetail> details = new ArrayList<>();
        Map<String, Long> reasonCounts = new java.util.TreeMap<>();
        for (Map.Entry<CallSiteId, CallSiteOutcomeLedger.Outcome> entry : primary.entrySet()) {
            CallSiteId id = entry.getKey();
            CallSiteOutcomeLedger.Outcome outcome = entry.getValue();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("callKind", id.callKind().label());
            metadata.put("reason", outcome.reason());
            if (outcome.target() != null) {
                metadata.put("target", outcome.target());
            }
            if (outcome.candidates() != null && !outcome.candidates().isEmpty()) {
                metadata.put("candidates", outcome.candidates());
            }
            // spec #27 D2: primary diagnostic として終端した call だけが、sanitize 済み
            // 診断項目 (resolutionPhase / exceptionClass / receiverKind /
            // receiverTypeResolved) を details へ載せる。
            if (outcome.diagnosticMetadata() != null) {
                metadata.putAll(outcome.diagnosticMetadata());
            }
            details.add(new com.fukuemon.depwalk.javaanalyzer.protocol.FailureDetail(
                    outcome.code(),
                    outcome.reason(),
                    com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation.of(id.path(), id.beginLine()),
                    metadata));
            reasonCounts.merge(outcome.code() + ":" + outcome.reason(), 1L, Long::sum);
        }
        Map<String, Object> topMetadata = new LinkedHashMap<>();
        topMetadata.put("total", (long) details.size());
        topMetadata.put("reasonCounts", reasonCounts);
        if (sootUpUnavailableContexts > 0) {
            topMetadata.put("sootUpUnavailableContexts", sootUpUnavailableContexts);
        }
        return new IncompleteAnalysisException(
                "unresolved in-scope call sites remain after all resolvers and bytecode recovery",
                details,
                topMetadata);
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
