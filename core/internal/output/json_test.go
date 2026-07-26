package output

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/graph/graphtest"
	"github.com/Fukuemon/depwalk/core/internal/traversal"
)

func TestJSONGolden(t *testing.T) {
	tests := []struct {
		name string
		view View
	}{
		{name: "graph", view: jsonGraphView()},
		{name: "start-not-found", view: jsonStartNotFoundView()},
		{name: "no-reach", view: jsonNoReachView()},
		{name: "max-depth-zero", view: jsonMaxDepthZeroView()},
	}

	formatter, ok := formatters()[FormatJSON]
	if !ok {
		t.Fatal("FormatJSON formatter is not registered")
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			want, err := os.ReadFile(filepath.Join("testdata", "golden", "json-"+tc.name+".json"))
			if err != nil {
				t.Fatalf("read golden: %v", err)
			}
			var compact bytes.Buffer
			if err := json.Compact(&compact, want); err != nil {
				t.Fatalf("compact golden: %v", err)
			}
			compact.WriteByte('\n')
			want = compact.Bytes()
			var got bytes.Buffer
			if err := formatter.Format(&got, tc.view); err != nil {
				t.Fatalf("Format() returned error: %v", err)
			}
			if !json.Valid(got.Bytes()) {
				t.Fatalf("Format() returned invalid JSON: %s", got.Bytes())
			}
			if !bytes.Equal(got.Bytes(), want) {
				t.Errorf("Format() output:\n%s\nwant:\n%s", got.Bytes(), want)
			}
		})
	}
}

func TestJSONDocumentPreservesOrderAndOptionalFields(t *testing.T) {
	view := jsonGraphView()
	document := newJSONDocument(view)

	if got := []string{document.Nodes[0].MethodID, document.Nodes[1].MethodID, document.Nodes[2].MethodID}; !reflect.DeepEqual(got, []string{"method:a", "method:m", "method:z"}) {
		t.Errorf("node order = %v, want methodId order", got)
	}
	if got := []string{document.Edges[0].EdgeID, document.Edges[1].EdgeID}; !reflect.DeepEqual(got, []string{"edge:a", "edge:z"}) {
		t.Errorf("edge order = %v, want edgeId order", got)
	}
	if got := []string{document.DepthCutoffs[0].EdgeID, document.DepthCutoffs[1].EdgeID}; !reflect.DeepEqual(got, []string{"cut:a", "cut:z"}) {
		t.Errorf("cutoff order = %v, want edgeId order", got)
	}
	if document.Nodes[0].MinDepth != 0 || document.Nodes[2].MinDepth != 2 {
		t.Errorf("node depths = %v, want View depths", document.Nodes)
	}

	data, err := json.Marshal(document)
	if err != nil {
		t.Fatalf("json.Marshal() returned error: %v", err)
	}
	var raw map[string]any
	if err := json.Unmarshal(data, &raw); err != nil {
		t.Fatalf("json.Unmarshal() returned error: %v", err)
	}
	nodes := raw["nodes"].([]any)
	edges := raw["edges"].([]any)
	if _, ok := nodes[0].(map[string]any)["sourceLocation"]; ok {
		t.Error("sourceLocation present for node with nil Source")
	}
	if _, ok := edges[0].(map[string]any)["callSite"]; ok {
		t.Error("callSite present for edge with nil CallSite")
	}
	if cycle, ok := edges[0].(map[string]any)["cycle"]; !ok || cycle != false {
		t.Errorf("cycle field = %v, present:%v; want explicit false", cycle, ok)
	}
	if _, ok := nodes[0].(map[string]any)["metadata"]; ok {
		t.Error("metadata present for node with nil Metadata")
	}
	if _, ok := edges[0].(map[string]any)["metadata"]; ok {
		t.Error("metadata present for edge with nil Metadata")
	}
	if got := nodes[1].(map[string]any)["metadata"]; !reflect.DeepEqual(got, map[string]any{"declarationOrigin": "projectClasses"}) {
		t.Errorf("node metadata = %#v, want opaque value", got)
	}
	if got := edges[1].(map[string]any)["metadata"]; !reflect.DeepEqual(got, map[string]any{"resolution": "springDi"}) {
		t.Errorf("edge metadata = %#v, want opaque value", got)
	}
}

func TestJSONCutoffTargetIsDanglingInBothDirections(t *testing.T) {
	tests := []struct {
		name      string
		direction graph.Direction
		cutoff    CutoffView
		want      string
	}{
		{
			name: "caller", direction: graph.DirectionCaller,
			cutoff: CutoffView{EdgeID: "edge:ab", CallerID: "method:a", CalleeID: "method:b", TargetMethodID: "method:a", TargetMinDepth: 2},
			want:   "method:a",
		},
		{
			name: "callee", direction: graph.DirectionCallee,
			cutoff: CutoffView{EdgeID: "edge:ab", CallerID: "method:a", CalleeID: "method:b", TargetMethodID: "method:b", TargetMinDepth: 2},
			want:   "method:b",
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			reachedID := tc.cutoff.CallerID
			if tc.direction == graph.DirectionCaller {
				reachedID = tc.cutoff.CalleeID
			}
			view := View{
				Status: traversal.StatusOK, Direction: tc.direction, Start: NodeView{ID: reachedID},
				Nodes: []NodeView{{ID: reachedID}}, Edges: []EdgeView{}, Cutoffs: []CutoffView{tc.cutoff},
			}
			document := newJSONDocument(view)
			got := document.DepthCutoffs[0]
			if got.TargetMethodID != tc.want {
				t.Errorf("targetMethodId = %q, want %q", got.TargetMethodID, tc.want)
			}
			if got.TargetMethodID == document.Nodes[0].MethodID {
				t.Errorf("targetMethodId %q is not dangling", got.TargetMethodID)
			}
			if tc.direction == graph.DirectionCaller && got.TargetMethodID != got.CallerMethodID {
				t.Error("caller targetMethodId does not equal callerMethodId")
			}
			if tc.direction == graph.DirectionCallee && got.TargetMethodID != got.CalleeMethodID {
				t.Error("callee targetMethodId does not equal calleeMethodId")
			}
		})
	}
}

func TestJSONWriteIsDeterministicForMapInput(t *testing.T) {
	g := graph.New()
	for _, node := range []graph.Node{
		{ID: "method:z", Symbol: graph.Symbol{QualifiedName: "Z", Signature: "()"}},
		{ID: "method:a", Symbol: graph.Symbol{QualifiedName: "A", Signature: "()"}},
	} {
		g.AddNode(node)
	}
	edge := graph.Edge{ID: "edge:az", CallerID: "method:a", CalleeID: "method:z"}
	in := Input{
		Graph: g,
		Result: traversal.Result{
			Status: traversal.StatusOK,
			Nodes:  map[string]bool{"method:z": true, "method:a": true},
			Depths: map[string]int{"method:z": 1, "method:a": 0},
			Edges:  map[string]graph.Edge{"edge:az": edge}, Cycles: map[string]bool{},
			DepthCutoffs: map[string]traversal.DepthCutoff{},
		},
		Request: traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee},
	}

	var want string
	for i := 0; i < 5; i++ {
		var got bytes.Buffer
		if err := Write(&got, FormatJSON, in); err != nil {
			t.Fatalf("Write(json) returned error: %v", err)
		}
		if i == 0 {
			want = got.String()
		} else if got.String() != want {
			t.Errorf("Write(json) run %d differs: %q != %q", i, got.String(), want)
		}
	}
}

func TestJSONWriteCarriesShortestDepthFromTraversal(t *testing.T) {
	g := graphtest.NewBuilder().
		Edge("edge:oa", "method:o", "method:a").
		Edge("edge:aa2", "method:a", "method:a2").
		Edge("edge:a2m", "method:a2", "method:m").
		Edge("edge:ob", "method:o", "method:b").
		Edge("edge:bm", "method:b", "method:m").
		Build()
	req := traversal.Request{StartID: "method:o", Direction: graph.DirectionCallee}
	result, err := traversal.Traverse(g, req)
	if err != nil {
		t.Fatalf("Traverse() returned error: %v", err)
	}

	document := writeJSONDocument(t, Input{Graph: g, Result: result, Request: req})
	for _, node := range document.Nodes {
		if node.MethodID == "method:m" {
			if node.MinDepth != 2 {
				t.Errorf("method:m minDepth = %d, want shortest depth 2", node.MinDepth)
			}
			return
		}
	}
	t.Error("method:m missing from nodes")
}

func TestJSONWriteDerivesDanglingCutoffTargetInBothDirections(t *testing.T) {
	g := graphtest.NewBuilder().Edge("edge:ab", "method:a", "method:b").Build()
	tests := []struct {
		name       string
		request    traversal.Request
		wantTarget string
	}{
		{
			name: "caller", request: traversal.Request{StartID: "method:b", Direction: graph.DirectionCaller, MaxDepth: intPointer(0)},
			wantTarget: "method:a",
		},
		{
			name: "callee", request: traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee, MaxDepth: intPointer(0)},
			wantTarget: "method:b",
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			result, err := traversal.Traverse(g, tc.request)
			if err != nil {
				t.Fatalf("Traverse() returned error: %v", err)
			}
			document := writeJSONDocument(t, Input{Graph: g, Result: result, Request: tc.request})
			if len(document.DepthCutoffs) != 1 {
				t.Fatalf("depthCutoffs = %#v, want one", document.DepthCutoffs)
			}
			cutoff := document.DepthCutoffs[0]
			if cutoff.TargetMethodID != tc.wantTarget {
				t.Errorf("targetMethodId = %q, want %q", cutoff.TargetMethodID, tc.wantTarget)
			}
			for _, node := range document.Nodes {
				if node.MethodID == cutoff.TargetMethodID {
					t.Errorf("targetMethodId %q is present in nodes", cutoff.TargetMethodID)
				}
			}
		})
	}
}

func writeJSONDocument(t *testing.T, in Input) jsonDocument {
	t.Helper()
	var out bytes.Buffer
	if err := Write(&out, FormatJSON, in); err != nil {
		t.Fatalf("Write(json) returned error: %v", err)
	}
	var document jsonDocument
	if err := json.Unmarshal(out.Bytes(), &document); err != nil {
		t.Fatalf("json.Unmarshal() returned error: %v", err)
	}
	return document
}

func intPointer(value int) *int { return &value }

// jsonLocation maps the fields by hand, so this pins that every one of
// them (path / startLine plus the three optional fields) marshals under
// its wire-compatible name and none are swapped.
func TestJSONSourceLocationMarshalsAllOptionalFields(t *testing.T) {
	t.Parallel()

	location := &graph.SourceLocation{
		Path:        "m.go",
		StartLine:   10,
		StartColumn: intPointer(4),
		EndLine:     intPointer(12),
		EndColumn:   intPointer(8),
	}
	encoded, err := json.Marshal(jsonLocation(location))
	if err != nil {
		t.Fatalf("json.Marshal() error = %v", err)
	}
	want := `{"path":"m.go","startLine":10,"startColumn":4,"endLine":12,"endColumn":8}`
	if string(encoded) != want {
		t.Fatalf("marshaled location = %s, want %s", encoded, want)
	}
}

func jsonGraphView() View {
	source := &graph.SourceLocation{Path: "m.go", StartLine: 10}
	callSite := &graph.SourceLocation{Path: "z.go", StartLine: 20}
	return View{
		Status: traversal.StatusOK, Direction: graph.DirectionCaller, Start: NodeView{ID: "method:a"},
		Nodes: []NodeView{
			{ID: "method:a", QualifiedName: "A", Signature: "()", MinDepth: 0},
			{ID: "method:m", QualifiedName: "M", Signature: "()", Source: source, MinDepth: 1, Metadata: map[string]any{"declarationOrigin": "projectClasses"}},
			{ID: "method:z", QualifiedName: "Z", Signature: "()", MinDepth: 2},
		},
		Edges: []EdgeView{
			{ID: "edge:a", CallerID: "method:m", CalleeID: "method:a", Cycle: false},
			{ID: "edge:z", CallerID: "method:z", CalleeID: "method:m", Cycle: true, CallSite: callSite, Metadata: map[string]any{"resolution": "springDi"}},
		},
		Cutoffs: []CutoffView{
			{EdgeID: "cut:a", CallerID: "method:outside-a", CalleeID: "method:z", TargetMethodID: "method:outside-a", TargetMinDepth: 3},
			{EdgeID: "cut:z", CallerID: "method:outside-z", CalleeID: "method:z", TargetMethodID: "method:outside-z", TargetMinDepth: 3, CallSite: callSite},
		},
	}
}

func jsonStartNotFoundView() View {
	return View{
		Status: traversal.StatusStartNotFound, Direction: graph.DirectionCallee,
		Start: NodeView{ID: "method:missing"}, Nodes: []NodeView{}, Edges: []EdgeView{}, Cutoffs: []CutoffView{},
	}
}

func jsonNoReachView() View {
	return View{
		Status: traversal.StatusOK, Direction: graph.DirectionCallee, Start: node("method:a", "A"),
		Nodes: []NodeView{node("method:a", "A")}, Edges: []EdgeView{}, Cutoffs: []CutoffView{},
	}
}

func jsonMaxDepthZeroView() View {
	return View{
		Status: traversal.StatusOK, Direction: graph.DirectionCallee, Start: node("method:a", "A"),
		Nodes: []NodeView{node("method:a", "A")}, Edges: []EdgeView{},
		Cutoffs: []CutoffView{{
			EdgeID: "edge:ab", CallerID: "method:a", CalleeID: "method:b",
			TargetMethodID: "method:b", TargetMinDepth: 1,
		}},
	}
}
