package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.MethodIds;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.RelativePaths;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
import com.fukuemon.depwalk.javaanalyzer.analysis.spring.EntryPointIndex;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;
import com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 解析対象ソースに宣言されたメソッドを、安定した method ID で検索するための索引。
 *
 * <p>呼び出し先候補が宣言ファイルより先に処理されても定義位置を付与できるよう、グラフ生成前の
 * 走査で {@link MethodSymbol} を収集する。メモリ使用量を抑えるため、保持するのは protocol 出力に
 * 必要なシンボル情報だけであり、{@link CompilationUnit} やその他の AST node は保持しない。
 */
public final class SourceMethodIndex {

    private final Path workspaceRoot;
    private final EntryPointIndex entryPointIndex;
    private final Map<String, MethodSymbol> symbolsByMethodId = new LinkedHashMap<>();
    private final Map<String, List<String>> annotationFqnsByMethodId = new LinkedHashMap<>();

    /**
     * @param workspaceRoot source location を相対化する基準。絶対・正規化済みであること
     *     (未正規化だと relativize が失敗する)
     * @param entryPointIndex entry point 標識の解決に使う。{@link #find} 経由の candidate
     *     再対応付けでも宣言 walk と同じ metadata を返し、GraphAccumulator の first-wins
     *     重複排除で標識が走査順に依存して落ちないようにする
     */
    public SourceMethodIndex(Path workspaceRoot, EntryPointIndex entryPointIndex) {
        this.workspaceRoot = workspaceRoot;
        this.entryPointIndex = entryPointIndex;
    }

    /**
     * compilation unit 内で型解決できたメソッド宣言を索引へ追加する。
     *
     * <p>個別メソッドの型解決に失敗した場合は登録を見送り、通常のグラフ生成処理に診断を委ねる。
     * 同じ method ID が複数回現れた場合は、最初に登録したシンボルを保持する。
     *
     * @param unit 索引化する compilation unit。呼び出し後に参照は保持しない
     */
    public void accept(CompilationUnit unit) {
        for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
            try {
                ResolvedMethodDeclaration resolved = method.resolve();
                String declaringType = BinaryNames.forResolvedDeclaration(resolved.declaringType());
                List<String> parameterTypes = new ArrayList<>();
                for (int i = 0; i < resolved.getNumberOfParams(); i++) {
                    parameterTypes.add(BinaryNames.erasureOf(resolved.getParam(i).getType()));
                }
                String signature = MethodIds.signature(declaringType, resolved.getName(), parameterTypes);
                String methodId = MethodIds.methodId(signature);
                String qualifiedName = declaringType.replace('$', '.') + "." + resolved.getName();
                SourceLocation sourceLocation = method.getBegin().flatMap(position -> unit.getStorage().map(storage -> {
                    Path path = storage.getPath().toAbsolutePath().normalize();
                    return SourceLocation.of(
                            RelativePaths.toRecordPath(workspaceRoot.relativize(path).toString()),
                            position.line);
                })).orElse(null);
                symbolsByMethodId.putIfAbsent(methodId, MethodSymbol.of(
                        methodId,
                        "java",
                        "method",
                        qualifiedName,
                        signature,
                        sourceLocation,
                        null));
                // Annotation FQNs are resolved now (the AST is discarded after this pass)
                // but mapped to entry points lazily in find(): composed-annotation
                // declarations may live in a file that has not been accepted yet.
                List<String> annotationFqns = entryPointIndex.annotationFqnsOf(method);
                if (!annotationFqns.isEmpty()) {
                    annotationFqnsByMethodId.putIfAbsent(methodId, annotationFqns);
                }
            } catch (RuntimeException | LinkageError ignored) {
                // CallGraphBuilder の既存 unresolved declaration 経路が second pass で診断する。
            }
        }
    }

    /**
     * bytecode から得たメソッド候補に対応するソースシンボルを返す。
     * scope 外または型解決に失敗して未索引の場合は空。
     */
    public Optional<MethodSymbol> find(SootUpTypeHierarchyIndex.MethodCandidate candidate) {
        String signature = MethodIds.signature(
                candidate.declaringType(),
                candidate.methodName(),
                candidate.parameterTypes());
        String methodId = MethodIds.methodId(signature);
        MethodSymbol symbol = symbolsByMethodId.get(methodId);
        if (symbol == null) {
            return Optional.empty();
        }
        List<String> entryPoints = entryPointsFor(methodId);
        if (!entryPoints.isEmpty()) {
            symbol = MethodSymbol.of(
                    symbol.methodId(),
                    symbol.language(),
                    symbol.symbolKind(),
                    symbol.qualifiedName(),
                    symbol.signature(),
                    symbol.sourceLocation(),
                    Map.of("entryPoint", entryPoints));
        }
        return Optional.of(symbol);
    }

    /**
     * methodId に対応する entry point 標識 (辞書順 FQN)。標識なしは空リスト。
     *
     * <p>標識の解決を AST 可用性に依存させないための methodId 引きの正本 lookup。
     * 宣言 walk / call site 由来 / candidate 再対応付けのどの経路から同一 methodId の
     * symbol が生成されても、同じ標識が付く (first-wins 重複排除で標識が落ちない)。
     */
    public List<String> entryPointsFor(String methodId) {
        List<String> annotationFqns = annotationFqnsByMethodId.get(methodId);
        if (annotationFqns == null) {
            return List.of();
        }
        return entryPointIndex.entryPointsOfAnnotationFqns(annotationFqns);
    }
}
