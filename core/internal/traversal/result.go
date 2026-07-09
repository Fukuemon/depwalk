package traversal

import "github.com/Fukuemon/depwalk/core/internal/graph"

// buildResult classifies every edge adjacent to a reached node into the
// induced edge set or a depth cutoff, then annotates cycle edges. The
// classification depends only on the reached node set and minDepth, so
// the result is identical for any visit order.
//
// depths may be nil when the traversal ran without a depth limit; in
// that case every adjacent node is reached and no cutoff is recorded.
func buildResult(g *graph.Graph, dir graph.Direction, nodes map[string]bool, depths map[string]int) Result {
	edges := map[string]graph.Edge{}
	cutoffs := map[string]DepthCutoff{}
	for id := range nodes {
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
		Edges:        edges,
		Cycles:       cycleEdges(nodes, edges),
		DepthCutoffs: cutoffs,
	}
}

// cycleEdges returns the IDs of induced edges that lie on a cycle of the
// reached subgraph. An edge lies on a cycle exactly when its endpoints
// belong to the same strongly connected component (a self-loop trivially
// does). Convergent (diamond) edges connect distinct components and are
// never annotated.
func cycleEdges(nodes map[string]bool, edges map[string]graph.Edge) map[string]bool {
	adjacency := map[string][]string{}
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
