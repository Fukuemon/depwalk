package traversal

import "github.com/Fukuemon/depwalk/core/internal/graph"

// minDepths は起点から到達可能な各 node への最短距離を返す (起点は 0)。
//
// 幅優先で走査する。[Request] の Order は結果に影響しない契約なので、距離は
// 指定された訪問順に関係なく正確でなければならない。
// 訪問済み node を再展開しないため、循環があっても停止する。
func minDepths(g *graph.Graph, startID string, dir graph.Direction) map[string]int {
	depths := map[string]int{startID: 0}
	queue := []string{startID}
	for head := 0; head < len(queue); head++ {
		id := queue[head]
		for _, e := range g.Neighbors(id, dir) {
			next := nextNode(e, dir)
			if _, seen := depths[next]; seen {
				continue
			}
			depths[next] = depths[id] + 1
			queue = append(queue, next)
		}
	}
	return depths
}

// visitOrder は起点から graph を辿り、展開した順に node ID を返す。
// [OrderBFS] (既定) は FIFO、[OrderDFS] は再帰的な深さ優先と同じ順になる。
//
// [Traverse] からは呼ばれない。到達集合の契約が訪問順に依存しないため、
// [Traverse] は [minDepths] だけで結果を導く。本関数は順序付きの展開を要する
// consumer (将来の tree 出力など) のために残してある。
func visitOrder(g *graph.Graph, startID string, dir graph.Direction, order Order) []string {
	visited := map[string]bool{startID: true}
	frontier := []string{startID}
	var out []string
	for len(frontier) > 0 {
		var id string
		if order == OrderDFS {
			id = frontier[len(frontier)-1]
			frontier = frontier[:len(frontier)-1]
		} else {
			id = frontier[0]
			frontier = frontier[1:]
		}
		out = append(out, id)

		edges := g.Neighbors(id, dir)
		if order == OrderDFS {
			// 逆順に積む。そうしないと最初の隣接 node が最後に展開され、
			// 再帰的な深さ優先の順序と食い違う。
			for i := len(edges) - 1; i >= 0; i-- {
				next := nextNode(edges[i], dir)
				if visited[next] {
					continue
				}
				visited[next] = true
				frontier = append(frontier, next)
			}
			continue
		}
		for _, e := range edges {
			next := nextNode(e, dir)
			if visited[next] {
				continue
			}
			visited[next] = true
			frontier = append(frontier, next)
		}
	}
	return out
}

// nextNode は dir 方向に辿ったときの接続先 node を返す。
func nextNode(e graph.Edge, dir graph.Direction) string {
	if dir == graph.DirectionCaller {
		return e.CallerID
	}
	return e.CalleeID
}
