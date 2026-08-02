package traversal

import "github.com/Fukuemon/depwalk/core/internal/graph"

// minDepths returns the shortest distance from start to every reachable
// node in the given direction (start = 0), computed breadth-first so the
// distances are exact regardless of the requested visit order. Visited
// nodes are never re-expanded, which also terminates cyclic graphs.
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

// visitOrder walks the graph from start and returns the node IDs in the
// order they were expanded: FIFO for [OrderBFS] (the default) and LIFO
// matching recursive depth-first order for [OrderDFS]. It implements the
// [Order] semantics of [Request] for consumers that need an ordered
// expansion; [Traverse] itself derives the reached set from
// [minDepths] because the result contract is order-independent. Visited
// nodes are never re-expanded.
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

// nextNode returns the node an edge leads to when walking in dir: the
// callee for [graph.DirectionCallee] and the caller for
// [graph.DirectionCaller].
func nextNode(e graph.Edge, dir graph.Direction) string {
	if dir == graph.DirectionCaller {
		return e.CallerID
	}
	return e.CalleeID
}
