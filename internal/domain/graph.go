package domain

// Graph is a minimal call graph model (directed edges).
// It is intentionally small for MVP and can be extended later.
type Graph struct {
	Nodes map[MethodID]struct{}
	Edges map[MethodID]map[MethodID]struct{} // from -> set(to)
}

func NewGraph() *Graph {
	return &Graph{
		Nodes: map[MethodID]struct{}{},
		Edges: map[MethodID]map[MethodID]struct{}{},
	}
}

func (g *Graph) AddEdge(from, to MethodID) {
	if g.Nodes == nil {
		g.Nodes = map[MethodID]struct{}{}
	}
	if g.Edges == nil {
		g.Edges = map[MethodID]map[MethodID]struct{}{}
	}
	g.Nodes[from] = struct{}{}
	g.Nodes[to] = struct{}{}
	if g.Edges[from] == nil {
		g.Edges[from] = map[MethodID]struct{}{}
	}
	g.Edges[from][to] = struct{}{}
}



