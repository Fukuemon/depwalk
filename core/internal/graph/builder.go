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

// NodeWithSymbol registers a node with its symbol and returns the builder for chaining.
func (b *Builder) NodeWithSymbol(id string, symbol Symbol) *Builder {
	b.g.AddNode(Node{ID: id, Symbol: symbol})
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
func (b *Builder) EdgeWithCallSite(id, callerID, calleeID string, callSite *SourceLocation) *Builder {
	b.g.AddNode(Node{ID: callerID})
	b.g.AddNode(Node{ID: calleeID})
	b.g.AddEdge(Edge{ID: id, CallerID: callerID, CalleeID: calleeID, CallSite: callSite})
	return b
}

// Build returns the assembled [Graph].
func (b *Builder) Build() *Graph {
	return b.g
}
