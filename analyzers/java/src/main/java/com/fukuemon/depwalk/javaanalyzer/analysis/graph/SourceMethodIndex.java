package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.BinaryNames;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.MethodIds;
import com.fukuemon.depwalk.javaanalyzer.analysis.normalize.RelativePaths;
import com.fukuemon.depwalk.javaanalyzer.analysis.sootup.SootUpTypeHierarchyIndex;
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
    private final Map<String, MethodSymbol> symbolsByMethodId = new LinkedHashMap<>();

    /**
     * 指定した workspace を基準に source location を相対 path へ正規化する索引を生成する。
     *
     * @param workspaceRoot 解析対象 workspace の絶対・正規化済み root path
     */
    public SourceMethodIndex(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
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
            } catch (RuntimeException | LinkageError ignored) {
                // CallGraphBuilder の既存 unresolved declaration 経路が second pass で診断する。
            }
        }
    }

    /**
     * bytecode から得たメソッド候補に対応するソースシンボルを返す。
     *
     * @param candidate 宣言型・メソッド名・引数型で識別された bytecode 上の候補
     * @return 対応するソース宣言が索引済みならそのシンボル、scope 外または未解決なら空
     */
    public Optional<MethodSymbol> find(SootUpTypeHierarchyIndex.MethodCandidate candidate) {
        String signature = MethodIds.signature(
                candidate.declaringType(),
                candidate.methodName(),
                candidate.parameterTypes());
        return Optional.ofNullable(symbolsByMethodId.get(MethodIds.methodId(signature)));
    }
}
