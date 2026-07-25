package output

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/traversal"
)

func TestConsoleGolden(t *testing.T) {
	tests := []struct {
		name string
		view View
	}{
		{name: "three-scc", view: threeSCCView()},
		{name: "self-loop", view: selfLoopView()},
		{name: "root-self-loop", view: rootSelfLoopView()},
		{name: "diamond", view: diamondView()},
		{name: "cutoff", view: cutoffView()},
		{name: "no-reach", view: noReachView()},
		{name: "max-depth-zero", view: maxDepthZeroView()},
		{name: "max-depth-zero-root-self-loop", view: maxDepthZeroRootSelfLoopView()},
		{name: "start-not-found", view: startNotFoundView()},
		{name: "sibling-order", view: siblingOrderView()},
	}

	formatter, ok := formatters[FormatConsole]
	if !ok {
		t.Fatal("FormatConsole formatter is not registered")
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			want, err := os.ReadFile(filepath.Join("testdata", "golden", "console-"+tc.name+".txt"))
			if err != nil {
				t.Fatalf("read golden: %v", err)
			}
			for i := 0; i < 3; i++ {
				var got bytes.Buffer
				if err := formatter.Format(&got, tc.view); err != nil {
					t.Fatalf("Format() returned error: %v", err)
				}
				if !bytes.Equal(got.Bytes(), want) {
					t.Errorf("Format() output:\n%s\nwant:\n%s", got.Bytes(), want)
				}
			}
		})
	}
}

func TestConsoleWriteIsDeterministicForMapInput(t *testing.T) {
	g := graph.New()
	for _, node := range []graph.Node{
		{ID: "method:c", Symbol: graph.Symbol{QualifiedName: "C", Signature: "()"}},
		{ID: "method:a", Symbol: graph.Symbol{QualifiedName: "A", Signature: "()"}},
		{ID: "method:b", Symbol: graph.Symbol{QualifiedName: "B", Signature: "()"}},
	} {
		g.AddNode(node)
	}
	edgeAB := graph.Edge{ID: "edge:ab", CallerID: "method:a", CalleeID: "method:b"}
	edgeAC := graph.Edge{ID: "edge:ac", CallerID: "method:a", CalleeID: "method:c"}
	in := Input{
		Graph: g,
		Result: traversal.Result{
			Status: traversal.StatusOK,
			Nodes:  map[string]bool{"method:c": true, "method:a": true, "method:b": true},
			Depths: map[string]int{"method:c": 1, "method:a": 0, "method:b": 1},
			Edges:  map[string]graph.Edge{"edge:ac": edgeAC, "edge:ab": edgeAB},
			Cycles: map[string]bool{}, DepthCutoffs: map[string]traversal.DepthCutoff{},
		},
		Request: traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee},
	}

	var want string
	for i := 0; i < 5; i++ {
		var got bytes.Buffer
		if err := Write(&got, FormatConsole, in); err != nil {
			t.Fatalf("Write(console) returned error: %v", err)
		}
		if i == 0 {
			want = got.String()
			continue
		}
		if got.String() != want {
			t.Errorf("Write(console) run %d = %q, want %q", i, got.String(), want)
		}
	}
}

func TestConsoleIgnoresOpaqueMetadata(t *testing.T) {
	withoutMetadata := consoleView("method:a",
		[]NodeView{node("method:a", "A"), node("method:b", "B")},
		[]EdgeView{edge("edge:ab", "method:a", "method:b")}, nil)
	withMetadata := withoutMetadata
	withMetadata.Start.Metadata = map[string]any{"declarationOrigin": "projectClasses"}
	withMetadata.Nodes = append([]NodeView(nil), withoutMetadata.Nodes...)
	withMetadata.Nodes[0].Metadata = map[string]any{"declarationOrigin": "projectClasses"}
	withMetadata.Edges = append([]EdgeView(nil), withoutMetadata.Edges...)
	withMetadata.Edges[0].Metadata = map[string]any{"resolution": "springDi"}

	formatter := consoleFormatter{}
	var want bytes.Buffer
	if err := formatter.Format(&want, withoutMetadata); err != nil {
		t.Fatalf("Format(without metadata) returned error: %v", err)
	}
	var got bytes.Buffer
	if err := formatter.Format(&got, withMetadata); err != nil {
		t.Fatalf("Format(with metadata) returned error: %v", err)
	}
	if got.String() != want.String() {
		t.Errorf("Format() with metadata = %q, want unchanged %q", got.String(), want.String())
	}
}

func TestConsoleSiblingOrderUsesMethodIDAsFinalNodeKey(t *testing.T) {
	tree := newConsoleTree(siblingOrderView())
	children := tree.children["method:root"]
	want := []string{"method:first", "method:a", "method:b", "method:z"}
	for i, child := range children {
		if child.node.ID != want[i] {
			t.Errorf("children[%d].ID = %q, want %q", i, child.node.ID, want[i])
		}
	}
}

func TestFormatNodeUsesNormalizedSignatureWithoutDuplicatingQualifiedName(t *testing.T) {
	node := NodeView{
		ID:            "java:com.example.UserService#findById(java.lang.Long)",
		QualifiedName: "com.example.UserService.findById",
		Signature:     "com.example.UserService#findById(java.lang.Long)",
	}

	got := formatNode(node, nil)
	want := "com.example.UserService#findById(java.lang.Long)"
	if got != want {
		t.Errorf("formatNode() = %q, want %q", got, want)
	}
}

func TestFormatNodeFallsBackWhenSignatureIsUnavailable(t *testing.T) {
	tests := []struct {
		name string
		node NodeView
		want string
	}{
		{
			name: "qualified name",
			node: NodeView{ID: "method:a", QualifiedName: "example.Service.call"},
			want: "example.Service.call",
		},
		{
			name: "method id",
			node: NodeView{ID: "method:missing"},
			want: "method:missing",
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := formatNode(tc.node, nil); got != tc.want {
				t.Errorf("formatNode() = %q, want %q", got, tc.want)
			}
		})
	}
}

func threeSCCView() View {
	return consoleView("method:a",
		[]NodeView{node("method:a", "A"), node("method:b", "B"), node("method:c", "C")},
		[]EdgeView{
			edge("edge:ab", "method:a", "method:b"),
			edge("edge:bc", "method:b", "method:c"),
			edge("edge:ca", "method:c", "method:a"),
		}, nil)
}

func selfLoopView() View {
	return consoleView("method:a",
		[]NodeView{node("method:a", "A"), node("method:b", "B")},
		[]EdgeView{
			edge("edge:ab", "method:a", "method:b"),
			edge("edge:bb", "method:b", "method:b"),
		}, nil)
}

func rootSelfLoopView() View {
	return consoleView("method:a", []NodeView{node("method:a", "A")},
		[]EdgeView{edge("edge:aa", "method:a", "method:a")}, nil)
}

func diamondView() View {
	rootSource := &graph.SourceLocation{Path: "root.go", StartLine: 10}
	callSite := &graph.SourceLocation{Path: "caller.go", StartLine: 20}
	view := consoleView("method:a",
		[]NodeView{
			{ID: "method:a", QualifiedName: "A", Signature: "A()", Source: rootSource},
			node("method:c", "C"), node("method:d", "D"), node("method:b", "B"),
		},
		[]EdgeView{
			edge("edge:ac", "method:a", "method:c"),
			{ID: "edge:ab", CallerID: "method:a", CalleeID: "method:b", CallSite: callSite},
			edge("edge:cd", "method:c", "method:d"),
			edge("edge:bd", "method:b", "method:d"),
		}, nil)
	return view
}

func cutoffView() View {
	return consoleView("method:a",
		[]NodeView{node("method:a", "A"), node("method:b", "B")},
		[]EdgeView{edge("edge:ab", "method:a", "method:b")},
		[]CutoffView{
			cutoff("cut:bc", "method:b", "method:c"),
			cutoff("cut:bd", "method:b", "method:d"),
		})
}

func noReachView() View {
	view := consoleView("method:a", []NodeView{node("method:a", "A")}, nil, nil)
	view.Direction = graph.DirectionCaller
	return view
}

func maxDepthZeroView() View {
	return consoleView("method:a", []NodeView{node("method:a", "A")}, nil,
		[]CutoffView{cutoff("cut:ab", "method:a", "method:b"), cutoff("cut:ac", "method:a", "method:c")})
}

func maxDepthZeroRootSelfLoopView() View {
	return consoleView("method:a", []NodeView{node("method:a", "A")},
		[]EdgeView{edge("edge:aa", "method:a", "method:a")},
		[]CutoffView{cutoff("cut:ab", "method:a", "method:b")})
}

func startNotFoundView() View {
	return View{
		Status: traversal.StatusStartNotFound, Direction: graph.DirectionCallee,
		Start: NodeView{ID: "method:missing"},
	}
}

func siblingOrderView() View {
	return consoleView("method:root",
		[]NodeView{
			node("method:root", "Root"),
			{ID: "method:z", QualifiedName: "Child", Signature: "Child(B)"},
			{ID: "method:b", QualifiedName: "Child", Signature: "Child(A)"},
			{ID: "method:a", QualifiedName: "Child", Signature: "Child(A)"},
			{ID: "method:first", QualifiedName: "Alpha", Signature: "Alpha()"},
		},
		[]EdgeView{
			edge("edge:z", "method:root", "method:z"),
			edge("edge:b", "method:root", "method:b"),
			edge("edge:a", "method:root", "method:a"),
			edge("edge:first", "method:root", "method:first"),
		}, nil)
}

func consoleView(startID string, nodes []NodeView, edges []EdgeView, cutoffs []CutoffView) View {
	start := NodeView{ID: startID}
	for _, candidate := range nodes {
		if candidate.ID == startID {
			start = candidate
			break
		}
	}
	return View{
		Status: traversal.StatusOK, Direction: graph.DirectionCallee, Start: start,
		Nodes: nodes, Edges: edges, Cutoffs: cutoffs,
	}
}

func node(id, name string) NodeView {
	return NodeView{ID: id, QualifiedName: name, Signature: name + "()"}
}

func edge(id, callerID, calleeID string) EdgeView {
	return EdgeView{ID: id, CallerID: callerID, CalleeID: calleeID}
}

func cutoff(id, callerID, calleeID string) CutoffView {
	return CutoffView{EdgeID: id, CallerID: callerID, CalleeID: calleeID, TargetMethodID: calleeID}
}
