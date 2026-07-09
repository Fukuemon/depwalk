package graph

// Builder assembles a [Graph] fluently. It exists mainly for tests and
// fixtures that need to lay out call graphs (linear, diamond, circular,
// deep) concisely; production code feeding Analyzer records can use
// [Graph.AddNode] and [Graph.AddEdge] directly.
type Builder struct {
	g *Graph
}

// NewBuilder returns a [Builder] wrapping an empty [Graph].
func NewBuilder() *Builder {
	return &Builder{g: New()}
}

// Node registers a node by ID and returns the builder for chaining.
func (b *Builder) Node(id string) *Builder {
	b.g.AddNode(Node{ID: id})
	return b
}

// Edge registers a directed edge and returns the builder for chaining.
// Endpoint nodes that are not registered yet are added automatically so
// tests can describe a graph by its edges alone.
func (b *Builder) Edge(id, callerID, calleeID string) *Builder {
	b.g.AddNode(Node{ID: callerID})
	b.g.AddNode(Node{ID: calleeID})
	b.g.AddEdge(Edge{ID: id, CallerID: callerID, CalleeID: calleeID})
	return b
}

// Build returns the assembled [Graph].
func (b *Builder) Build() *Graph {
	return b.g
}
