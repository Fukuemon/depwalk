package graph

import "testing"

func TestBuilderBuildsNodesAndEdges(t *testing.T) {
	g := NewBuilder().
		Node("method:a").
		Node("method:b").
		Edge("edge:ab", "method:a", "method:b").
		Build()

	if _, ok := g.Node("method:a"); !ok {
		t.Error("Node(method:a) not found")
	}
	if _, ok := g.Node("method:b"); !ok {
		t.Error("Node(method:b) not found")
	}
	if got := g.Neighbors("method:a", DirectionCallee); len(got) != 1 {
		t.Errorf("Neighbors(a, callee) = %d edges, want 1", len(got))
	}
}

func TestBuilderEdgeRegistersEndpointNodes(t *testing.T) {
	g := NewBuilder().
		Edge("edge:ab", "method:a", "method:b").
		Build()

	if _, ok := g.Node("method:a"); !ok {
		t.Error("caller endpoint method:a not auto-registered")
	}
	if _, ok := g.Node("method:b"); !ok {
		t.Error("callee endpoint method:b not auto-registered")
	}
}

func TestBuilderBuildsDiamondGraph(t *testing.T) {
	// o -> a -> m, o -> b -> m (convergence at m)
	g := NewBuilder().
		Edge("edge:oa", "method:o", "method:a").
		Edge("edge:ob", "method:o", "method:b").
		Edge("edge:am", "method:a", "method:m").
		Edge("edge:bm", "method:b", "method:m").
		Build()

	if got := g.Neighbors("method:o", DirectionCallee); len(got) != 2 {
		t.Errorf("Neighbors(o, callee) = %d edges, want 2", len(got))
	}
	if got := g.Neighbors("method:m", DirectionCaller); len(got) != 2 {
		t.Errorf("Neighbors(m, caller) = %d edges, want 2", len(got))
	}
}

func TestBuilderBuildsCircularGraph(t *testing.T) {
	// a -> b -> a (mutual recursion) and c -> c (self loop)
	g := NewBuilder().
		Edge("edge:ab", "method:a", "method:b").
		Edge("edge:ba", "method:b", "method:a").
		Edge("edge:cc", "method:c", "method:c").
		Build()

	if got := g.Neighbors("method:a", DirectionCallee); len(got) != 1 {
		t.Errorf("Neighbors(a, callee) = %d edges, want 1", len(got))
	}
	if got := g.Neighbors("method:a", DirectionCaller); len(got) != 1 {
		t.Errorf("Neighbors(a, caller) = %d edges, want 1", len(got))
	}
	if got := g.Neighbors("method:c", DirectionCallee); len(got) != 1 {
		t.Errorf("Neighbors(c, callee) = %d edges, want 1", len(got))
	}
}
