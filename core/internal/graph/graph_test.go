package graph

import (
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

func TestNodeReturnsRegisteredNode(t *testing.T) {
	g := New()
	g.AddNode(Node{ID: "method:a"})

	got, ok := g.Node("method:a")
	if !ok {
		t.Fatalf("Node(%q) not found, want found", "method:a")
	}
	if got.ID != "method:a" {
		t.Errorf("Node(%q).ID = %q, want %q", "method:a", got.ID, "method:a")
	}
}

func TestNodeReturnsRegisteredSymbol(t *testing.T) {
	source := &protocol.SourceLocation{Path: "service.go", StartLine: 12}
	want := Symbol{QualifiedName: "example.Service.Run", Signature: "()", Source: source}
	g := New()
	g.AddNode(Node{ID: "method:a", Symbol: want})

	got, ok := g.Node("method:a")
	if !ok {
		t.Fatal("Node(method:a) not found")
	}
	if got.Symbol != want {
		t.Errorf("Node(method:a).Symbol = %#v, want %#v", got.Symbol, want)
	}
}

func TestNodeAllowsNilSymbolSource(t *testing.T) {
	g := New()
	g.AddNode(Node{ID: "method:a", Symbol: Symbol{QualifiedName: "example.Service.Run"}})

	got, ok := g.Node("method:a")
	if !ok {
		t.Fatal("Node(method:a) not found")
	}
	if got.Symbol.Source != nil {
		t.Errorf("Node(method:a).Symbol.Source = %#v, want nil", got.Symbol.Source)
	}
}

func TestNodeReportsMissingNode(t *testing.T) {
	g := New()

	if _, ok := g.Node("method:missing"); ok {
		t.Errorf("Node(%q) found, want not found", "method:missing")
	}
}

func TestNodeReportsMissingNodeOnEmptyGraph(t *testing.T) {
	g := New()

	if _, ok := g.Node(""); ok {
		t.Error("Node(\"\") found on empty graph, want not found")
	}
}

func TestAddNodeIgnoresDuplicateID(t *testing.T) {
	g := New()
	g.AddNode(Node{ID: "method:a"})
	g.AddNode(Node{ID: "method:a"})

	got, ok := g.Node("method:a")
	if !ok {
		t.Fatal("Node not found after duplicate AddNode")
	}
	if got.ID != "method:a" {
		t.Errorf("Node ID = %q, want %q", got.ID, "method:a")
	}
}

func TestNeighborsReturnsCalleeEdges(t *testing.T) {
	g := New()
	g.AddNode(Node{ID: "method:a"})
	g.AddNode(Node{ID: "method:b"})
	g.AddNode(Node{ID: "method:c"})
	g.AddEdge(Edge{ID: "edge:ab", CallerID: "method:a", CalleeID: "method:b"})
	g.AddEdge(Edge{ID: "edge:ac", CallerID: "method:a", CalleeID: "method:c"})
	g.AddEdge(Edge{ID: "edge:bc", CallerID: "method:b", CalleeID: "method:c"})

	got := g.Neighbors("method:a", DirectionCallee)
	if len(got) != 2 {
		t.Fatalf("Neighbors(a, callee) returned %d edges, want 2", len(got))
	}
	ids := map[string]bool{}
	for _, e := range got {
		ids[e.ID] = true
	}
	if !ids["edge:ab"] || !ids["edge:ac"] {
		t.Errorf("Neighbors(a, callee) = %v, want edge:ab and edge:ac", ids)
	}
}

func TestNeighborsReturnsCallerEdges(t *testing.T) {
	g := New()
	g.AddNode(Node{ID: "method:a"})
	g.AddNode(Node{ID: "method:b"})
	g.AddNode(Node{ID: "method:c"})
	g.AddEdge(Edge{ID: "edge:ac", CallerID: "method:a", CalleeID: "method:c"})
	g.AddEdge(Edge{ID: "edge:bc", CallerID: "method:b", CalleeID: "method:c"})

	got := g.Neighbors("method:c", DirectionCaller)
	if len(got) != 2 {
		t.Fatalf("Neighbors(c, caller) returned %d edges, want 2", len(got))
	}
	ids := map[string]bool{}
	for _, e := range got {
		ids[e.ID] = true
	}
	if !ids["edge:ac"] || !ids["edge:bc"] {
		t.Errorf("Neighbors(c, caller) = %v, want edge:ac and edge:bc", ids)
	}
}

func TestNeighborsReturnsEmptyForLeafNode(t *testing.T) {
	g := New()
	g.AddNode(Node{ID: "method:a"})
	g.AddNode(Node{ID: "method:b"})
	g.AddEdge(Edge{ID: "edge:ab", CallerID: "method:a", CalleeID: "method:b"})

	if got := g.Neighbors("method:b", DirectionCallee); len(got) != 0 {
		t.Errorf("Neighbors(b, callee) = %v, want empty", got)
	}
	if got := g.Neighbors("method:a", DirectionCaller); len(got) != 0 {
		t.Errorf("Neighbors(a, caller) = %v, want empty", got)
	}
}

func TestNeighborsReturnsEmptyForUnknownNode(t *testing.T) {
	g := New()

	if got := g.Neighbors("method:missing", DirectionCallee); len(got) != 0 {
		t.Errorf("Neighbors(missing, callee) = %v, want empty", got)
	}
}

func TestNeighborsReturnsEmptyForInvalidDirection(t *testing.T) {
	g := New()
	g.AddNode(Node{ID: "method:a"})
	g.AddNode(Node{ID: "method:b"})
	g.AddEdge(Edge{ID: "edge:ab", CallerID: "method:a", CalleeID: "method:b"})

	if got := g.Neighbors("method:a", Direction("sideways")); len(got) != 0 {
		t.Errorf("Neighbors(a, invalid direction) = %v, want empty (no silent callee fallback)", got)
	}
	if got := g.Neighbors("method:a", Direction("")); len(got) != 0 {
		t.Errorf("Neighbors(a, zero direction) = %v, want empty", got)
	}
}

func TestAddEdgeIgnoresDuplicateID(t *testing.T) {
	g := New()
	g.AddNode(Node{ID: "method:a"})
	g.AddNode(Node{ID: "method:b"})
	g.AddEdge(Edge{ID: "edge:ab", CallerID: "method:a", CalleeID: "method:b"})
	g.AddEdge(Edge{ID: "edge:ab", CallerID: "method:a", CalleeID: "method:b"})

	if got := g.Neighbors("method:a", DirectionCallee); len(got) != 1 {
		t.Errorf("Neighbors(a, callee) returned %d edges after duplicate AddEdge, want 1", len(got))
	}
}

func TestNeighborsReturnsRegisteredCallSite(t *testing.T) {
	callSite := &protocol.SourceLocation{Path: "caller.go", StartLine: 24}
	g := New()
	g.AddEdge(Edge{ID: "edge:ab", CallerID: "method:a", CalleeID: "method:b", CallSite: callSite})

	got := g.Neighbors("method:a", DirectionCallee)
	if len(got) != 1 {
		t.Fatalf("Neighbors(method:a, callee) = %d edges, want 1", len(got))
	}
	if got[0].CallSite != callSite {
		t.Errorf("Neighbors(method:a, callee)[0].CallSite = %#v, want %#v", got[0].CallSite, callSite)
	}
}

func TestEdgeAllowsNilCallSite(t *testing.T) {
	g := New()
	g.AddEdge(Edge{ID: "edge:ab", CallerID: "method:a", CalleeID: "method:b"})

	got := g.Neighbors("method:a", DirectionCallee)
	if len(got) != 1 {
		t.Fatalf("Neighbors(method:a, callee) = %d edges, want 1", len(got))
	}
	if got[0].CallSite != nil {
		t.Errorf("Neighbors(method:a, callee)[0].CallSite = %#v, want nil", got[0].CallSite)
	}
}

func TestSelfLoopEdgeAppearsInBothDirections(t *testing.T) {
	g := New()
	g.AddNode(Node{ID: "method:a"})
	g.AddEdge(Edge{ID: "edge:aa", CallerID: "method:a", CalleeID: "method:a"})

	if got := g.Neighbors("method:a", DirectionCallee); len(got) != 1 {
		t.Errorf("Neighbors(a, callee) with self-loop = %d edges, want 1", len(got))
	}
	if got := g.Neighbors("method:a", DirectionCaller); len(got) != 1 {
		t.Errorf("Neighbors(a, caller) with self-loop = %d edges, want 1", len(got))
	}
}
