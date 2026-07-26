// Package graphtest provides call graph fixtures for testing code that reads
// a [graph.Graph].
//
// It is a separate package so that the production graph API stays limited to
// what depwalk itself needs ([graph.Graph.AddNode] and [graph.Graph.AddEdge]);
// the fluent builder here exists only to lay out linear, diamond, circular,
// and deep graphs concisely in tests. Production code must not import it.
package graphtest

import "github.com/Fukuemon/depwalk/core/internal/graph"

// Builder assembles a [graph.Graph] fluently. The zero value is not usable;
// create one with [NewBuilder].
type Builder struct {
	g *graph.Graph
}

// NewBuilder returns a [Builder] wrapping an empty [graph.Graph].
func NewBuilder() *Builder {
	return &Builder{g: graph.New()}
}

// Node registers a node by ID and returns the builder for chaining.
func (b *Builder) Node(id string) *Builder {
	b.g.AddNode(graph.Node{ID: id})
	return b
}

// NodeWithSymbol registers a node with its symbol and returns the builder
// for chaining.
func (b *Builder) NodeWithSymbol(id string, symbol graph.Symbol) *Builder {
	b.g.AddNode(graph.Node{ID: id, Symbol: symbol})
	return b
}

// Edge registers a directed edge and returns the builder for chaining.
// Endpoint nodes that are not registered yet are added automatically so
// tests can describe a graph by its edges alone.
func (b *Builder) Edge(id, callerID, calleeID string) *Builder {
	return b.EdgeWithCallSite(id, callerID, calleeID, nil)
}

// EdgeWithCallSite registers a directed edge with an optional call site and
// returns the builder for chaining.
func (b *Builder) EdgeWithCallSite(id, callerID, calleeID string, callSite *graph.SourceLocation) *Builder {
	b.g.AddNode(graph.Node{ID: callerID})
	b.g.AddNode(graph.Node{ID: calleeID})
	b.g.AddEdge(graph.Edge{ID: id, CallerID: callerID, CalleeID: calleeID, CallSite: callSite})
	return b
}

// Build returns the assembled [graph.Graph].
func (b *Builder) Build() *graph.Graph {
	return b.g
}
