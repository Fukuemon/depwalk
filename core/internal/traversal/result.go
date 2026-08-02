package traversal

import "github.com/Fukuemon/depwalk/core/internal/graph"

// buildResult は到達 node に隣接する edge を、誘導 edge 集合と深さ打ち切りに
// 分類し、閉路を構成する edge に注釈を付ける。
//
// 分類は到達 node 集合と minDepth だけに依存するため、訪問順が変わっても
// 結果は変わらない。depths は到達可能な全 node を含んでいる必要がある
// ([minDepths] の戻り値)。
func buildResult(g *graph.Graph, dir graph.Direction, nodes map[string]bool, depths map[string]int) Result {
	edges := make(map[string]graph.Edge, len(nodes))
	cutoffs := map[string]DepthCutoff{}
	reachedDepths := make(map[string]int, len(nodes))
	for id := range nodes {
		reachedDepths[id] = depths[id]
		for _, e := range g.Neighbors(id, dir) {
			next := nextNode(e, dir)
			if nodes[next] {
				edges[e.ID] = e
				continue
			}
			cutoffs[e.ID] = DepthCutoff{Edge: e, TargetMinDepth: depths[next]}
		}
	}

	return Result{
		Status:       StatusOK,
		Nodes:        nodes,
		Depths:       reachedDepths,
		Edges:        edges,
		Cycles:       cycleEdges(nodes, edges),
		DepthCutoffs: cutoffs,
	}
}

// cycleEdges は到達部分グラフ内で閉路を構成する誘導 edge の ID を返す。
//
// 判定は「両端が同じ強連結成分に属するか」で行う (self-loop は自明に該当)。
// 合流 (ダイヤモンド型) の edge は異なる成分をつなぐため注釈されない。
// 経路を辿って判定すると合流を閉路と誤判定するため、この方法を採る。
func cycleEdges(nodes map[string]bool, edges map[string]graph.Edge) map[string]bool {
	adjacency := make(map[string][]string, len(nodes))
	for _, e := range edges {
		adjacency[e.CallerID] = append(adjacency[e.CallerID], e.CalleeID)
	}
	component := sccComponents(nodes, adjacency)

	cycles := map[string]bool{}
	for id, e := range edges {
		if component[e.CallerID] == component[e.CalleeID] {
			cycles[id] = true
		}
	}
	return cycles
}
