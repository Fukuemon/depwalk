package graphtest_test

import (
	"reflect"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/graphtest"
)

func TestBuilderBuildsNodesAndEdges(t *testing.T) {
	g := graphtest.NewBuilder().
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
	if got := g.Neighbors("method:a", graph.DirectionCallee); len(got) != 1 {
		t.Errorf("Neighbors(a, callee) = %d edges, want 1", len(got))
	}
}

func TestBuilderEdgeRegistersEndpointNodes(t *testing.T) {
	g := graphtest.NewBuilder().
		Edge("edge:ab", "method:a", "method:b").
		Build()

	if _, ok := g.Node("method:a"); !ok {
		t.Error("caller endpoint method:a not auto-registered")
	}
	if _, ok := g.Node("method:b"); !ok {
		t.Error("callee endpoint method:b not auto-registered")
	}
}

func TestBuilderBuildsNodeWithSymbolAndEdgeWithCallSite(t *testing.T) {
	source := &graph.SourceLocation{Path: "callee.go", StartLine: 8}
	callSite := &graph.SourceLocation{Path: "caller.go", StartLine: 13}
	symbol := graph.Symbol{QualifiedName: "example.Callee.Run", Signature: "()", Source: source}

	g := graphtest.NewBuilder().
		NodeWithSymbol("method:a", graph.Symbol{QualifiedName: "example.Caller.Run", Signature: "()"}).
		NodeWithSymbol("method:b", symbol).
		EdgeWithCallSite("edge:ab", "method:a", "method:b", callSite).
		Build()

	gotNode, ok := g.Node("method:b")
	if !ok {
		t.Fatal("Node(method:b) not found")
	}
	if !reflect.DeepEqual(gotNode.Symbol, symbol) {
		t.Errorf("Node(method:b).Symbol = %#v, want %#v", gotNode.Symbol, symbol)
	}
	edges := g.Neighbors("method:a", graph.DirectionCallee)
	if len(edges) != 1 {
		t.Fatalf("Neighbors(method:a, callee) = %d edges, want 1", len(edges))
	}
	if edges[0].CallSite != callSite {
		t.Errorf("edge CallSite = %#v, want %#v", edges[0].CallSite, callSite)
	}
}

func TestBuilderExistingMethodsUseZeroValueMetadata(t *testing.T) {
	g := graphtest.NewBuilder().
		Node("method:a").
		Edge("edge:ab", "method:a", "method:b").
		Build()

	node, ok := g.Node("method:a")
	if !ok {
		t.Fatal("Node(method:a) not found")
	}
	if !reflect.DeepEqual(node.Symbol, graph.Symbol{}) {
		t.Errorf("Node(method:a).Symbol = %#v, want zero value", node.Symbol)
	}
	edges := g.Neighbors("method:a", graph.DirectionCallee)
	if len(edges) != 1 || edges[0].CallSite != nil {
		t.Errorf("Neighbors(method:a, callee) = %#v, want one edge with nil CallSite", edges)
	}
}

func TestBuilderBuildsDiamondGraph(t *testing.T) {
	// o -> a -> m, o -> b -> m (convergence at m)
	g := graphtest.NewBuilder().
		Edge("edge:oa", "method:o", "method:a").
		Edge("edge:ob", "method:o", "method:b").
		Edge("edge:am", "method:a", "method:m").
		Edge("edge:bm", "method:b", "method:m").
		Build()

	if got := g.Neighbors("method:o", graph.DirectionCallee); len(got) != 2 {
		t.Errorf("Neighbors(o, callee) = %d edges, want 2", len(got))
	}
	if got := g.Neighbors("method:m", graph.DirectionCaller); len(got) != 2 {
		t.Errorf("Neighbors(m, caller) = %d edges, want 2", len(got))
	}
}

func TestBuilderBuildsCircularGraph(t *testing.T) {
	// a -> b -> a (mutual recursion) and c -> c (self loop)
	g := graphtest.NewBuilder().
		Edge("edge:ab", "method:a", "method:b").
		Edge("edge:ba", "method:b", "method:a").
		Edge("edge:cc", "method:c", "method:c").
		Build()

	if got := g.Neighbors("method:a", graph.DirectionCallee); len(got) != 1 {
		t.Errorf("Neighbors(a, callee) = %d edges, want 1", len(got))
	}
	if got := g.Neighbors("method:a", graph.DirectionCaller); len(got) != 1 {
		t.Errorf("Neighbors(a, caller) = %d edges, want 1", len(got))
	}
	if got := g.Neighbors("method:c", graph.DirectionCallee); len(got) != 1 {
		t.Errorf("Neighbors(c, callee) = %d edges, want 1", len(got))
	}
}
