package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import com.fukuemon.depwalk.javaanalyzer.protocol.CallEdge;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSelector;
import com.fukuemon.depwalk.javaanalyzer.protocol.MethodSymbol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code reachableFromEntrypoints}: 母集合 ({@link GraphAccumulator} が保持する全 node) のうち、
 * entrypoints から callee 方向に推移的に到達するものに限定する。
 */
public final class ReachabilityFilter {

    private ReachabilityFilter() {
    }

    /**
     * entrypoint から到達可能な部分 graph と未一致 selector。
     *
     * @param nodes 到達可能な method symbol
     * @param edges 両端が到達可能な call edge
     * @param unmatchedSelectors node に一致しなかった entrypoint の表示文字列
     */
    public record Result(List<MethodSymbol> nodes, List<CallEdge> edges, List<String> unmatchedSelectors) {
    }

    /**
     * entrypoint selector を node に照合し、callee 方向の到達可能部分 graph を返す。
     * どの node にも一致しなかった selector は除外せず {@code unmatchedSelectors} で返す。
     */
    public static Result apply(GraphAccumulator accumulator, List<MethodSelector> entrypoints) {
        Map<String, MethodSymbol> allNodes = accumulator.nodesByMethodId();
        List<CallEdge> allEdges = accumulator.edges();

        Set<String> entrypointMethodIds = new LinkedHashSet<>();
        List<String> unmatched = new ArrayList<>();
        for (MethodSelector selector : entrypoints) {
            List<String> matches = matchSelector(allNodes, selector);
            if (matches.isEmpty()) {
                unmatched.add(describeSelector(selector));
            } else {
                entrypointMethodIds.addAll(matches);
            }
        }

        Map<String, List<String>> calleesByCaller = new LinkedHashMap<>();
        for (CallEdge edge : allEdges) {
            calleesByCaller.computeIfAbsent(edge.callerMethodId(), k -> new ArrayList<>()).add(edge.calleeMethodId());
        }

        Set<String> reachable = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(entrypointMethodIds);
        reachable.addAll(entrypointMethodIds);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String callee : calleesByCaller.getOrDefault(current, List.of())) {
                if (reachable.add(callee)) {
                    queue.add(callee);
                }
            }
        }

        List<MethodSymbol> filteredNodes = new ArrayList<>();
        for (String methodId : reachable) {
            MethodSymbol symbol = allNodes.get(methodId);
            if (symbol != null) {
                filteredNodes.add(symbol);
            }
        }
        List<CallEdge> filteredEdges = new ArrayList<>();
        for (CallEdge edge : allEdges) {
            if (reachable.contains(edge.callerMethodId()) && reachable.contains(edge.calleeMethodId())) {
                filteredEdges.add(edge);
            }
        }
        return new Result(filteredNodes, filteredEdges, unmatched);
    }

    private static List<String> matchSelector(Map<String, MethodSymbol> nodes, MethodSelector selector) {
        List<String> matches = new ArrayList<>();
        for (MethodSymbol symbol : nodes.values()) {
            if (!symbol.qualifiedName().equals(selector.qualifiedName())) {
                continue;
            }
            if (selector.signature() != null && !selector.signature().equals(symbol.signature())) {
                continue;
            }
            matches.add(symbol.methodId());
        }
        return matches;
    }

    private static String describeSelector(MethodSelector selector) {
        return selector.signature() != null
                ? selector.qualifiedName() + " (" + selector.signature() + ")"
                : selector.qualifiedName();
    }
}
