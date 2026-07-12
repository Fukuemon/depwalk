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
 * <p>1 回の {@link com.fukuemon.depwalk.javaanalyzer.analysis.AnalysisRunner#run} 実行の中で単一
 * thread から呼び出される前提であり、並行アクセスに対する保護は行わない。
 */
public final class GraphAccumulator {

    private final Map<String, MethodSymbol> nodesByMethodId = new LinkedHashMap<>();
    private final List<CallEdge> edges = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private long edgeSequence = 0;
    private long unresolvedCount = 0;

    /** node を追加する。同一 {@code methodId} が既にあれば何もしない (最初の登録を正とする)。 */
    public void addNode(MethodSymbol symbol) {
        nodesByMethodId.putIfAbsent(symbol.methodId(), symbol);
    }

    public boolean hasNode(String methodId) {
        return nodesByMethodId.containsKey(methodId);
    }

    public void addEdge(String callerMethodId, String calleeMethodId,
            com.fukuemon.depwalk.javaanalyzer.protocol.SourceLocation callSite,
            Map<String, Object> metadata) {
        edgeSequence++;
        String edgeId = "java-edge-" + edgeSequence;
        edges.add(CallEdge.of(edgeId, callerMethodId, calleeMethodId, callSite, metadata));
    }

    public void addDiagnostic(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public void incrementUnresolved() {
        unresolvedCount++;
    }

    public long unresolvedCount() {
        return unresolvedCount;
    }

    public Map<String, MethodSymbol> nodesByMethodId() {
        return nodesByMethodId;
    }

    public List<CallEdge> edges() {
        return edges;
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }
}
