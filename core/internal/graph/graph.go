// Package graph holds the call graph built from Analyzer records and
// provides the read-only view that the Traversal Engine explores.
//
// A [Graph] stores nodes keyed by the Analyzer Protocol methodId and
// directed edges keyed by edgeId. Callers register records via
// [Graph.AddNode] and [Graph.AddEdge], and readers look up nodes with
// [Graph.Node] and walk adjacency with [Graph.Neighbors]. The package
// never mutates registered data after insertion; traversal packages
// depend only on this read API, not on the internal representation.
package graph

import "github.com/Fukuemon/depwalk/core/internal/protocol"

// Direction selects which adjacency a graph read follows.
type Direction string

const (
	// DirectionCaller walks edges toward the methods that call a node.
	DirectionCaller Direction = "caller"
	// DirectionCallee walks edges toward the methods a node calls.
	DirectionCallee Direction = "callee"
)

// Node is a call graph node identified by the Analyzer Protocol methodId.
type Node struct {
	ID     string
	Symbol Symbol
}

// Symbol describes a method represented by a [Node]. Source is optional.
// Metadata is an optional graph-owned opaque JSON object copied from the
// Analyzer record; the graph, traversal, and output layers never interpret
// its keys. A nil map means the record had no metadata; an empty map means
// the record carried an explicit empty object.
type Symbol struct {
	QualifiedName string
	Signature     string
	Source        *protocol.SourceLocation
	Metadata      map[string]any
}

// Edge is a directed call edge identified by the Analyzer Protocol edgeId.
// CallerID and CalleeID reference [Node] IDs. Metadata is an optional
// graph-owned opaque JSON object with the same omitted-versus-empty semantics
// as [Symbol.Metadata].
type Edge struct {
	ID       string
	CallerID string
	CalleeID string
	CallSite *protocol.SourceLocation
	Metadata map[string]any
}

// Graph is an in-memory call graph. The zero value is not usable; create
// one with [New].
type Graph struct {
	nodes map[string]Node
	edges map[string]Edge
	// outgoing maps a caller node ID to the edges it calls (callee direction).
	outgoing map[string][]Edge
	// incoming maps a callee node ID to the edges that call it (caller direction).
	incoming map[string][]Edge
}

// New returns an empty [Graph].
func New() *Graph {
	return &Graph{
		nodes:    map[string]Node{},
		edges:    map[string]Edge{},
		outgoing: map[string][]Edge{},
		incoming: map[string][]Edge{},
	}
}

// AddNode registers a node. A node whose ID is already registered is
// ignored (first registration wins), matching the deterministic methodId
// contract of the Analyzer Protocol.
func (g *Graph) AddNode(n Node) {
	if _, ok := g.nodes[n.ID]; ok {
		return
	}
	g.nodes[n.ID] = n
}

// AddEdge registers a directed edge. An edge whose ID is already
// registered is ignored (first registration wins). Callers must only
// register edges whose endpoints are (or will be) registered nodes: the
// Analyzer Protocol guarantees this by rejecting edges to unresolved
// symbols, and the record-loading layer is responsible for upholding it.
func (g *Graph) AddEdge(e Edge) {
	if _, ok := g.edges[e.ID]; ok {
		return
	}
	g.edges[e.ID] = e
	g.outgoing[e.CallerID] = append(g.outgoing[e.CallerID], e)
	g.incoming[e.CalleeID] = append(g.incoming[e.CalleeID], e)
}

// Node returns the node registered under id. The second result reports
// whether the node exists; callers must check it instead of relying on
// the zero value.
func (g *Graph) Node(id string) (Node, bool) {
	n, ok := g.nodes[id]
	return n, ok
}

// Neighbors returns the edges adjacent to the node id in the given
// direction: for [DirectionCallee] the edges the node calls, for
// [DirectionCaller] the edges that call the node. Unknown IDs, leaf
// nodes, and invalid directions yield an empty slice. The returned
// slice is shared with the graph's internal adjacency and must not be
// modified by the caller.
func (g *Graph) Neighbors(id string, dir Direction) []Edge {
	switch dir {
	case DirectionCaller:
		return g.incoming[id]
	case DirectionCallee:
		return g.outgoing[id]
	default:
		return nil
	}
}
