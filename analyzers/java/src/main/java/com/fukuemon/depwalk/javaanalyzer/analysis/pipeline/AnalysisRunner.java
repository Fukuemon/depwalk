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
import com.fukuemon.depwalk.javaanalyzer.protocol.FailureDetail;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSelector;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;
import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 検証済みの解析要求を受け取り、Java の解析から protocol record 出力までをオーケストレーションする。
 *
 * <p>source file の列挙、AST 解析、型解決、Spring DI 索引化、呼び出し先候補の統合、帰属型決定、
 * 到達可能性フィルタ、{@link RecordWriter} への出力を1回の実行として調停する。
 *
 * <p>逐次破棄されるのは AST のみで (ファイル単位で parse し、処理後は参照を手放す)、
 * SymbolSolver の型解決キャッシュと {@link GraphAccumulator} が持つ node / edge / diagnostic は
 * 実行終了まで保持される。したがってモードを問わずグラフ本体はメモリ上に残り、モードによって
 * 変わるのは record を writer へ流す timing である:
 * <ul>
 *   <li>{@code fullGraph} (既定 / {@code reachableFromEntrypoints} かつ entrypoints 空の場合を含む):
 *       ファイル単位の解析が終わるごとに、その時点で新たに確定した node / edge / diagnostic を
 *       即座に {@link RecordWriter} へ flush する。母集合全体の解析完了を待たずに record を逐次出力
 *       できる (node / edge / diagnostic のいずれも登録順 list として追加のみされるため、
 *       出力済み件数を進捗 index として保持し、未出力分だけを書き出す)。</li>
 *   <li>{@code reachableFromEntrypoints} (entrypoints 指定あり): 到達可能性フィルタ
 *       ({@link ReachabilityFilter}) が母集合全体の adjacency を必要とするため、逐次 flush を行わず、
 *       node / edge / diagnostic のいずれも解析完了後に一括で書き出す (node / edge はフィルタ適用後の
 *       もの、diagnostic は {@link GraphAccumulator} に蓄積された全件)。即時 write は fullGraph モード
 *       のみである。</li>
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
     * @param contextBuildMillis context 別 TypeSolver / parser 構築の所要時間
     *     (java-analyzer feature doc「性能方針」の段階別計測として通常解析と分離する)
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
     *     request を fatal にせず、解決済み graph と診断を公開する
     *     ({@code metadata.allowIncompleteAnalysis}、
     *     java-analyzer feature doc「Parse・resolution・call 完全性」)
     * @return 解析したファイル数、未解決件数、parse pre-flight 時間、context 構築時間、
     *     call site ledger の集計 ({@link RunStats})
     * @throws IOException source の列挙・読み込みまたは protocol record の出力に失敗した場合
     * @throws AnalyzerFatalException scope の binary name 衝突または parse pre-flight 失敗
     * @throws IncompleteAnalysisException {@code allowIncompleteAnalysis} が false のまま、
     *     全救済後も primary diagnostic として残る in-scope call site があった場合
     *     (完全性 gate、java-analyzer feature doc「Parse・resolution・call 完全性」)
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

        // 各 classes output の所有 context (origin 判定用)。
        Map<Path, String> classesOutputOwners = new LinkedHashMap<>();
        for (SourceSetAnalysisContext context : contexts) {
            for (Path output : context.classesOutputs()) {
                classesOutputOwners.putIfAbsent(output, context.id());
            }
        }

        // Gradle project 依存の推移閉包は context ごとに一度だけ計算し、
        // solver / builder の各段で共有する (同じ結果を再計算しない)。
        Map<String, Set<String>> reachableDependencies = new LinkedHashMap<>();
        for (SourceSetAnalysisContext context : contexts) {
            reachableDependencies.put(context.id(), reachableDependencyIds(context, contextById));
        }

        long contextBuildStart = System.nanoTime();
        BytecodeIndexes bytecodeIndexes =
                buildBytecodeIndexes(contexts, contextById, reachableDependencies, classesOutputOwners);
        Map<String, SootUpTypeHierarchyIndex> sootUpByContext = bytecodeIndexes.sootUpByContext();
        Map<String, ProjectBytecodeMemberIndex> bytecodeIndexByContext = bytecodeIndexes.bytecodeIndexByContext();
        Map<String, JavaParser> parserByContext =
                buildParsers(contexts, contextById, reachableDependencies, bytecodeIndexByContext);
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

        SpringDiIndex springDiIndex = createSpringDiIndex(contexts, scope);
        SourceMethodIndex sourceMethodIndex = new SourceMethodIndex(workspaceRoot);
        GraphAccumulator accumulator = new GraphAccumulator();
        // resolver とは独立した call-site inventory と source 宣言索引
        // (feature doc「Parse・resolution・call 完全性」/
        //  adr/0005-adopt-sootup-and-spring-di-resolution.md)。
        CallSiteInventory inventory = new CallSiteInventory(workspaceRoot);
        WorkspaceSourceDeclarationIndex declIndex = new WorkspaceSourceDeclarationIndex(workspaceRoot);
        CallSiteOutcomeLedger ledger = new CallSiteOutcomeLedger(inventory);

        // Spring Bean/注入点と候補 method の source location は compact な first-pass index に落とす。
        // CompilationUnit は各 iteration で破棄し、fullGraph の AST 逐次破棄契約を維持する。
        for (Path file : scope.allFiles()) {
            CompilationUnit unit = parseOrFail(parserByContext.get(contextByFile.get(file).id()), file);
            // inventory / 宣言索引の不変条件違反は diagnostic へ降格せず internal fatal のまま伝播させる。
            inventory.accept(unit);
            declIndex.accept(unit, contextByFile.get(file).id());
            try {
                springDiIndex.accept(unit);
            } catch (RuntimeException | LinkageError e) {
                accumulator.incrementUnresolved();
                accumulator.addDiagnostic(Diagnostic.of(
                        JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.severity(),
                        JavaDiagnosticCode.JAVA_UNRESOLVED_SYMBOL.code(),
                        "failed to index Spring DI metadata: " + e.getMessage(),
                        SourceLocation.of(
                                RelativePaths.toRecordPath(workspaceRoot.relativize(file).toString()), 1),
                        null,
                        null));
                // Spring DI 索引に失敗した unit は source method 索引も見送る (従来の挙動)。
                continue;
            }
            // SourceMethodIndex は method 単位で解決失敗を自前で握る (未登録のまま second pass の
            // 診断へ委ねる) ため、Spring DI 索引の catch には含めない。含めると
            // SourceMethodIndex 由来の失敗まで "failed to index Spring DI metadata" として記録される。
            sourceMethodIndex.accept(unit);
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
            reachable.addAll(reachableDependencies.get(context.id()));
            builderByContext.put(context.id(), new CallGraphBuilder(
                    workspaceRoot,
                    attributionResolver,
                    accumulator,
                    sootUpByContext.get(context.id()),
                    sourceMethodIndex,
                    springResult,
                    ledger,
                    declIndex,
                    bytecodeIndexByContext.get(context.id()),
                    reachable));
        }

        boolean reachableMode = ANALYSIS_MODE_REACHABLE.equals(request.analysisMode()) && hasEntrypoints(request);

        // fullGraph (streaming) 用の「出力済み」進捗マーカー。reachableMode では未使用。
        StreamProgress progress = new StreamProgress();

        long analyzedFileCount = 0;
        for (Path file : scope.allFiles()) {
            SourceSetAnalysisContext context = contextByFile.get(file);
            CompilationUnit cu = parseOrFail(parserByContext.get(context.id()), file);
            builderByContext.get(context.id()).process(cu);
            analyzedFileCount++;
            // cu はここでスコープを抜け、以降 GC 対象になる (AST の逐次破棄)。

            if (!reachableMode) {
                flushNewRecords(accumulator, writer, progress);
            }
        }

        if (reachableMode) {
            writeReachableRecords(accumulator, request.entrypoints(), writer);
        }

        // 完全性 gate (feature doc「Parse・resolution・call 完全性」):
        // 全 entry の分類を検査し、primary
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
        // allowIncompleteAnalysis=true (feature doc「Parse・resolution・call 完全性」):
        // primary diagnostic が残っても
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

    /**
     * context 別 bytecode 索引の組。
     *
     * @param sootUpByContext context id ごとの型階層索引
     * @param bytecodeIndexByContext context id ごとの project bytecode member 索引
     */
    private record BytecodeIndexes(
            Map<String, SootUpTypeHierarchyIndex> sootUpByContext,
            Map<String, ProjectBytecodeMemberIndex> bytecodeIndexByContext) {
    }

    /**
     * context ごとの SootUp 型階層索引と project bytecode member 索引を構築する。
     *
     * <p>SootUp index は lazy のため全 context 分を先に用意し、solver の bytecode member 合成
     * (feature doc「solver 層の bytecode member 合成」) と builder の候補解決で同一 instance を共有する。
     * 合成は「呼出元 context の classpath 視点」で行う (依存 project の型も自 context の classpath に
     * 含まれる classes output から引く)。emit 時に declIndex + 到達可能 context の検査で owner を制約する。
     */
    private static BytecodeIndexes buildBytecodeIndexes(
            List<SourceSetAnalysisContext> contexts,
            Map<String, SourceSetAnalysisContext> contextById,
            Map<String, Set<String>> reachableDependencies,
            Map<Path, String> classesOutputOwners) {
        Map<String, SootUpTypeHierarchyIndex> sootUpByContext = new LinkedHashMap<>();
        Map<String, ProjectBytecodeMemberIndex> bytecodeIndexByContext = new LinkedHashMap<>();
        for (SourceSetAnalysisContext context : contexts) {
            // project 所有の classes output (自 context + 依存 project の output) を
            // external jar より先に登録し、同名 class は project bytecode を優先
            // する。member 救済の origin 検証
            // (feature doc「solver 層の bytecode member 合成」) にも同じ一覧を渡す。
            // 依存 project の output は classpath の形に依存せず model の project
            // 依存関係から解決する (Gradle model は依存 project を jar
            // として classpath へ返すことがあり、classpath 照合だけでは依存 output
            // が external artifact 扱いになって cross-module 救済が拒否されていた)。
            // 登録順 (自 context の output → 依存 project の output → classpath 上の
            // project output) がそのまま SootUp の classpath 優先順になるため、
            // 重複排除しつつ挿入順を保つ set を使う。
            Set<Path> projectOutputs = new LinkedHashSet<>(context.classesOutputs());
            for (String dependencyId : reachableDependencies.get(context.id())) {
                SourceSetAnalysisContext dependency = contextById.get(dependencyId);
                if (dependency == null) {
                    continue;
                }
                projectOutputs.addAll(dependency.classesOutputs());
            }
            for (Path path : context.classpath()) {
                if (classesOutputOwners.containsKey(path)) {
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
                    new ProjectBytecodeMemberIndex(index, List.copyOf(projectOutputs)));
        }
        return new BytecodeIndexes(sootUpByContext, bytecodeIndexByContext);
    }

    /**
     * context ごとの parser (TypeSolver 付き) を構築する。
     *
     * <p>solver には自 root、Gradle project 依存で到達可能な context の root、自 context の外部 entry
     * だけを登録し、非依存 module や異なる依存 version を混在させない
     * (java-analyzer feature doc「Source root discovery と解析 context」)。
     *
     * @throws IOException solver 用の source root / classpath entry を読み取れなかった場合
     */
    private static Map<String, JavaParser> buildParsers(
            List<SourceSetAnalysisContext> contexts,
            Map<String, SourceSetAnalysisContext> contextById,
            Map<String, Set<String>> reachableDependencies,
            Map<String, ProjectBytecodeMemberIndex> bytecodeIndexByContext) throws IOException {
        Map<String, JavaParser> parserByContext = new LinkedHashMap<>();
        for (SourceSetAnalysisContext context : contexts) {
            List<Path> solverRoots = new ArrayList<>(context.sourceRoots());
            for (String dependencyId : reachableDependencies.get(context.id())) {
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
            CombinedTypeSolver typeSolver = TypeSolverFactory.createForRoots(
                    solverRoots, solverEntries, context.languageLevel(), bytecodeIndexByContext.get(context.id()));
            ParserConfiguration config = new ParserConfiguration()
                    .setSymbolResolver(new JavaSymbolSolver(typeSolver))
                    .setLanguageLevel(context.languageLevel());
            parserByContext.put(context.id(), new JavaParser(config));
        }
        return parserByContext;
    }

    /**
     * 全 context の entry を統合した Spring DI 索引を作る。
     *
     * <p>SootUp index は context ごとに分離する (feature doc「Source root discovery と解析 context」) が、
     * Spring DI の Bean 母集合は request 全体の意味論 (module 間 DI 解決が成功条件) のため、DI 索引だけは
     * 全 context の entry を統合した index を使う。union は file を持つ context の entry だけで構成する。
     */
    private static SpringDiIndex createSpringDiIndex(
            List<SourceSetAnalysisContext> contexts, ContextScope.Scope scope) {
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
        return SpringDiIndex.create(SootUpTypeHierarchyIndex.fromClasspath(List.copyOf(unionEntries)));
    }

    /**
     * pre-flight 済み source file を parse する。
     * 失敗はここへ到達しない前提のため internal error として送出する。
     */
    private static CompilationUnit parseOrFail(JavaParser parser, Path file) {
        ParseResult<CompilationUnit> result;
        try {
            result = parser.parse(file);
        } catch (IOException | ParseProblemException e) {
            // pre-flight で全 file の parse 成功を確認済みのため、ここへの到達は内部エラー。
            throw new IllegalStateException("file failed to parse after a successful pre-flight: " + file, e);
        }
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            throw new IllegalStateException("file failed to parse after a successful pre-flight: " + file);
        }
        return result.getResult().get();
    }

    /** fullGraph (streaming) で writer へ書き出した node / edge / diagnostic の件数。 */
    private static final class StreamProgress {
        private int nodes;
        private int edges;
        private int diagnostics;
    }

    /**
     * 前回 flush 以降に {@link GraphAccumulator} へ追加された record だけを writer へ書き出す。
     * node / edge / diagnostic はいずれも追加のみの登録順 list のため、進捗を index で保持できる。
     */
    private static void flushNewRecords(
            GraphAccumulator accumulator, RecordWriter writer, StreamProgress progress) throws IOException {
        List<MethodSymbol> allNodes = accumulator.nodes();
        for (int i = progress.nodes; i < allNodes.size(); i++) {
            writer.write(allNodes.get(i));
        }
        progress.nodes = allNodes.size();

        List<CallEdge> allEdges = accumulator.edges();
        for (int i = progress.edges; i < allEdges.size(); i++) {
            writer.write(allEdges.get(i));
        }
        progress.edges = allEdges.size();

        List<Diagnostic> allDiagnostics = accumulator.diagnostics();
        for (int i = progress.diagnostics; i < allDiagnostics.size(); i++) {
            writer.write(allDiagnostics.get(i));
        }
        progress.diagnostics = allDiagnostics.size();
    }

    /**
     * reachableFromEntrypoints モードの一括出力。到達可能性フィルタを適用した node / edge と、
     * 蓄積された全 diagnostic (未一致 selector の診断を含む) を書き出す。
     */
    private static void writeReachableRecords(
            GraphAccumulator accumulator, List<MethodSelector> entrypoints, RecordWriter writer) throws IOException {
        ReachabilityFilter.Result filtered = ReachabilityFilter.apply(accumulator, entrypoints);
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

    private static IncompleteAnalysisException incompleteAnalysis(
            Map<CallSiteId, CallSiteOutcomeLedger.Outcome> primary, long sootUpUnavailableContexts) {
        List<FailureDetail> details = new ArrayList<>();
        Map<String, Long> reasonCounts = new TreeMap<>();
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
            // feature doc「diagnostic / error code 体系」: primary diagnostic として
            // 終端した call だけが、sanitize 済み
            // 診断項目 (resolutionPhase / exceptionClass / receiverKind /
            // receiverTypeResolved) を details へ載せる。
            if (outcome.diagnosticMetadata() != null) {
                metadata.putAll(outcome.diagnosticMetadata());
            }
            details.add(new FailureDetail(
                    outcome.code(),
                    outcome.reason(),
                    SourceLocation.of(id.path(), id.beginLine()),
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
        ArrayDeque<String> queue = new ArrayDeque<>(context.dependencyContextIds());
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
