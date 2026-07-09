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

// Result is the outcome of a traversal. Nodes carries the reached node
// IDs as a set: no order is guaranteed.
type Result struct {
	Status Status
	Nodes  map[string]bool
}

// Traverse runs one traversal over g and returns the reached set.
// Invalid request options fail before any walk; a missing start node
// yields an empty [Result] with [StatusStartNotFound] instead of an
// error.
func Traverse(g *graph.Graph, req Request) (Result, error) {
	if err := validate(req); err != nil {
		return Result{}, err
	}
	if _, ok := g.Node(req.StartID); !ok {
		return Result{Status: StatusStartNotFound, Nodes: map[string]bool{}}, nil
	}

	visited := visitOrder(g, req.StartID, req.Direction, req.Order)
	nodes := make(map[string]bool, len(visited))
	if req.MaxDepth == nil {
		for _, id := range visited {
			nodes[id] = true
		}
		return Result{Status: StatusOK, Nodes: nodes}, nil
	}

	// The depth limit is defined over minDepth (shortest distance), so a
	// DFS walk that reaches a node the long way around cannot exclude it.
	depths := minDepths(g, req.StartID, req.Direction)
	for _, id := range visited {
		if depths[id] > *req.MaxDepth {
			continue
		}
		nodes[id] = true
	}
	return Result{Status: StatusOK, Nodes: nodes}, nil
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
