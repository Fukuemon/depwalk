// Package traversal computes caller / callee reachability over the call
// graph held by [graph.Graph].
//
// The result contract is defined as a property of the graph, not of the
// walk that produced it (design/features/traversal/DesignDoc_traversal.md):
// the reached node set is every node whose shortest distance from the
// start (minDepth, start = 0) is within the optional depth limit. The
// BFS / DFS order option only controls the internal visit order and never
// changes the observable result.
package traversal

import (
	"fmt"

	"github.com/Fukuemon/depwalk/core/internal/graph"
)

// Order selects the internal visit order of the walk. It has no effect
// on the observable [Result].
type Order string

const (
	// OrderBFS expands nodes breadth-first. It is the default.
	OrderBFS Order = "bfs"
	// OrderDFS expands nodes depth-first.
	OrderDFS Order = "dfs"
)

// Status reports whether a traversal found its start node.
type Status string

const (
	// StatusOK means the start node existed and the result is populated.
	StatusOK Status = "ok"
	// StatusStartNotFound means the start node was absent (including an
	// empty graph). It is a normal "no match" result, not an error.
	StatusStartNotFound Status = "startNotFound"
)

// Request describes one traversal run.
type Request struct {
	// StartID is the methodId of the start node.
	StartID string
	// Direction selects caller or callee reachability.
	Direction graph.Direction
	// MaxDepth limits the reached set to nodes with minDepth <= MaxDepth.
	// nil means unlimited. Zero keeps only the start node.
	MaxDepth *int
	// Order selects the internal visit order. Empty defaults to [OrderBFS].
	Order Order
}

// Result is the outcome of a traversal. Nodes and Edges carry the
// reached induced subgraph as sets keyed by ID: no order is guaranteed.
type Result struct {
	Status Status
	// Nodes is the reached node ID set (minDepth <= maxDepth), including
	// the start node.
	Nodes map[string]bool
	// Edges is the induced subgraph: every edge in the traversal
	// direction whose both endpoints are reached, including convergent
	// and cycle edges.
	Edges map[string]graph.Edge
	// Cycles annotates the Edges entries that lie on a cycle of the
	// reached subgraph (self-loop or same SCC). Annotated edges stay in
	// Edges because they are real call relations.
	Cycles map[string]bool
	// DepthCutoffs records edges from a reached node to a node beyond
	// the depth limit, keyed by edge ID. These edges are not in Edges.
	DepthCutoffs map[string]DepthCutoff
}

// DepthCutoff is an edge cut by the depth limit together with the
// minDepth of the node it leads to.
type DepthCutoff struct {
	Edge graph.Edge
	// TargetMinDepth is the minDepth of the beyond-limit node the edge
	// leads to in the traversal direction (always > maxDepth).
	TargetMinDepth int
}

// Traverse runs one traversal over g and returns the reached induced
// subgraph. Invalid request options fail before any walk with the
// zero-value [Result], which must not be used; a missing start node
// yields an empty [Result] with [StatusStartNotFound] instead of an
// error.
//
// The reached set is derived from a single shortest-distance walk
// ([minDepths]) because the contract defines it order-independently:
// deriving it from any other walk could only agree or silently diverge.
// The Order option is therefore validated but does not alter the walk;
// its BFS / DFS semantics live in [visitOrder] for consumers that need
// an ordered expansion (kept per spec D1 for future tree-style output).
func Traverse(g *graph.Graph, req Request) (Result, error) {
	if err := validate(req); err != nil {
		return Result{}, err
	}
	if _, ok := g.Node(req.StartID); !ok {
		return Result{
			Status:       StatusStartNotFound,
			Nodes:        map[string]bool{},
			Edges:        map[string]graph.Edge{},
			Cycles:       map[string]bool{},
			DepthCutoffs: map[string]DepthCutoff{},
		}, nil
	}

	depths := minDepths(g, req.StartID, req.Direction)
	nodes := make(map[string]bool, len(depths))
	for id, d := range depths {
		if req.MaxDepth != nil && d > *req.MaxDepth {
			continue
		}
		nodes[id] = true
	}
	return buildResult(g, req.Direction, nodes, depths), nil
}

func validate(req Request) error {
	switch req.Direction {
	case graph.DirectionCaller, graph.DirectionCallee:
	default:
		return fmt.Errorf("traversal: invalid direction %q: want %q or %q", req.Direction, graph.DirectionCaller, graph.DirectionCallee)
	}
	if req.MaxDepth != nil && *req.MaxDepth < 0 {
		return fmt.Errorf("traversal: invalid maxDepth %d: want unset or >= 0", *req.MaxDepth)
	}
	switch req.Order {
	case "", OrderBFS, OrderDFS:
	default:
		return fmt.Errorf("traversal: invalid order %q: want %q or %q", req.Order, OrderBFS, OrderDFS)
	}
	return nil
}
