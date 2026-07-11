package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.protocol.CallEdge;
import com.fukuemon.depwalk.javaanalyzer.protocol.Diagnostic;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 1 回の解析実行で構築する node / edge / diagnostic の最小限のインメモリ集計。
 * AST 自体 (CompilationUnit) は保持しない (ファイル単位で逐次破棄する)。
 * node は {@code methodId} で重複排除する (宣言列挙と call site 由来の引き上げが同じ node を
 * 指すことがあるため)。
 */
public final class GraphAccumulator {

    private final Map<String, MethodSymbol> nodesByMethodId = new LinkedHashMap<>();
    private final List<CallEdge> edges = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final AtomicLong edgeSequence = new AtomicLong(0);
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
        String edgeId = "java-edge-" + edgeSequence.incrementAndGet();
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
