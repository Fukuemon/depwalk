package model

// Graph is a minimal call graph model (directed edges).
// It is intentionally small for MVP and can be extended later.
type Graph struct {
	Nodes map[MethodID]struct{}
	Edges map[MethodID]map[MethodID]struct{} // from -> set(to)
}

// NewGraph creates an empty Graph.
func NewGraph() *Graph {
	return &Graph{
		Nodes: map[MethodID]struct{}{},
		Edges: map[MethodID]map[MethodID]struct{}{},
	}
}

// AddNode adds a node to the graph.
func (g *Graph) AddNode(id MethodID) {
	if g.Nodes == nil {
		g.Nodes = map[MethodID]struct{}{}
	}
	g.Nodes[id] = struct{}{}
}

// AddEdge adds a directed edge from -> to.
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

// Successors returns outgoing neighbors of a node.
func (g *Graph) Successors(id MethodID) []MethodID {
	edges, ok := g.Edges[id]
	if !ok {
		return nil
	}
	result := make([]MethodID, 0, len(edges))
	for to := range edges {
		result = append(result, to)
	}
	return result
}

// Predecessors returns incoming neighbors of a node.
func (g *Graph) Predecessors(id MethodID) []MethodID {
	var result []MethodID
	for from, edges := range g.Edges {
		if _, ok := edges[id]; ok {
			result = append(result, from)
		}
	}
	return result
}

