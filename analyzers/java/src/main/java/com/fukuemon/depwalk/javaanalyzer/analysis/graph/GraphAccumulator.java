package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.protocol.CallEdge;
import com.fukuemon.depwalk.javaanalyzer.protocol.Diagnostic;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1 回の解析実行で構築する node / edge / diagnostic の最小限のインメモリ集計。
 * AST 自体 (CompilationUnit) は保持しない (ファイル単位で逐次破棄する)。
 * node は {@code methodId} で重複排除する (宣言列挙と call site 由来の引き上げが同じ node を
 * 指すことがあるため)。
 *
 * <p>first-wins ({@code putIfAbsent}) で情報が失われない根拠: 同一 {@code methodId} の node を生成する
 * 全経路 (宣言列挙 / call site 由来 SCOPE_INTERNAL / synthetic default constructor / lift) は同一内容の
 * {@link MethodSymbol} を生成する。synthetic default constructor は {@code CallGraphBuilder} が宣言型の
 * AST 位置へフォールバックすることで synthetic node と call site 由来 node の内容が一致し、LIFTED node の
 * methodId は subtype 側の名前で構成されるため宣言列挙とは衝突しない。このため後着を捨てても内容は
 * 不変であり、fullGraph streaming で flush 済みの node と後続ファイル由来の同一 methodId が衝突しても
 * 出力の劣化は起きない。
 *
 * <p>1 回の {@link com.fukuemon.depwalk.javaanalyzer.analysis.pipeline.AnalysisRunner} 実行の中で単一
 * thread から呼び出される前提であり、並行アクセスに対する保護は行わない。
 */
public final class GraphAccumulator {

    private final Map<String, MethodSymbol> nodesByMethodId = new LinkedHashMap<>();
    /** {@code nodesByMethodId} と同じ登録順の node 列 (streaming の進捗を index で追うため)。 */
    private final List<MethodSymbol> nodes = new ArrayList<>();
    private final List<CallEdge> edges = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private long edgeSequence = 0;
    private long unresolvedCount = 0;

    public GraphAccumulator() {
    }

    /** node を追加する。同一 {@code methodId} が既にあれば最初の登録を保持する。 */
    public void addNode(MethodSymbol symbol) {
        if (nodesByMethodId.putIfAbsent(symbol.methodId(), symbol) == null) {
            nodes.add(symbol);
        }
    }

    public boolean hasNode(String methodId) {
        return nodesByMethodId.containsKey(methodId);
    }

    /** 呼び出し関係を追加し、解析実行内で一意な edge ID ({@code java-edge-<連番>}) を採番する。 */
    public void addEdge(String callerMethodId, String calleeMethodId,
            com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation callSite,
            Map<String, Object> metadata) {
        edgeSequence++;
        String edgeId = "java-edge-" + edgeSequence;
        edges.add(CallEdge.of(edgeId, callerMethodId, calleeMethodId, callSite, metadata));
    }

    /** 継続可能な (解析を中断させない) 診断を追加する。 */
    public void addDiagnostic(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public void incrementUnresolved() {
        unresolvedCount++;
    }

    public long unresolvedCount() {
        return unresolvedCount;
    }

    /** 登録済み node を method ID key の挿入順 map で返す (順序が出力の決定性の一部)。 */
    public Map<String, MethodSymbol> nodesByMethodId() {
        return nodesByMethodId;
    }

    /**
     * 登録済み node を登録順の list で返す。
     *
     * <p>{@link #nodesByMethodId()} の値と同一順序・同一内容で、要素は追加のみされる。
     * 逐次 flush 側は「どこまで書き出したか」を index で保持でき、既出判定に全件走査を要しない。
     */
    public List<MethodSymbol> nodes() {
        return nodes;
    }

    /** 登録済み call edge を挿入順で返す (順序が出力の決定性の一部)。 */
    public List<CallEdge> edges() {
        return edges;
    }

    /** 登録済み diagnostic を挿入順で返す (順序が出力の決定性の一部)。 */
    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }
}
