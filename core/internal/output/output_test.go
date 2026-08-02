package output

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"reflect"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/graph/graphtest"
	"github.com/Fukuemon/depwalk/core/internal/traversal"
)

func TestBuildViewResolvesSymbolsAndSortsCollections(t *testing.T) {
	source := &graph.SourceLocation{Path: "a.go", StartLine: 10}
	callSite := &graph.SourceLocation{Path: "z.go", StartLine: 20}
	nodeMetadata := map[string]any{"declarationOrigin": "projectClasses"}
	edgeMetadata := map[string]any{"resolution": "springDi"}
	g := graph.New()
	g.AddNode(graph.Node{ID: "method:z", Symbol: graph.Symbol{QualifiedName: "example.Z", Signature: "()"}})
	g.AddNode(graph.Node{ID: "method:a", Symbol: graph.Symbol{QualifiedName: "example.A", Signature: "()", Source: source, Metadata: nodeMetadata}})
	g.AddNode(graph.Node{ID: "method:m", Symbol: graph.Symbol{QualifiedName: "example.M", Signature: "()"}})
	edgeZ := graph.Edge{ID: "edge:z", CallerID: "method:z", CalleeID: "method:m", CallSite: callSite, Metadata: edgeMetadata}
	edgeA := graph.Edge{ID: "edge:a", CallerID: "method:a", CalleeID: "method:m"}
	cutZ := graph.Edge{ID: "cut:z", CallerID: "method:x", CalleeID: "method:z"}
	cutA := graph.Edge{ID: "cut:a", CallerID: "method:x", CalleeID: "method:a", CallSite: callSite}
	in := Input{
		Graph: g,
		Result: traversal.Result{
			Status: traversal.StatusOK,
			Nodes:  map[string]bool{"method:z": true, "method:a": true, "method:m": true},
			Depths: map[string]int{"method:z": 1, "method:a": 1, "method:m": 0},
			Edges:  map[string]graph.Edge{"edge:z": edgeZ, "edge:a": edgeA},
			Cycles: map[string]bool{"edge:z": true},
			DepthCutoffs: map[string]traversal.DepthCutoff{
				"cut:z": {Edge: cutZ, TargetMinDepth: 2},
				"cut:a": {Edge: cutA, TargetMinDepth: 2},
			},
		},
		Request: traversal.Request{StartID: "method:m", Direction: graph.DirectionCaller},
	}

	got := buildView(in)

	if got.Status != traversal.StatusOK || got.Direction != graph.DirectionCaller {
		t.Errorf("View status/direction = %q/%q, want %q/%q", got.Status, got.Direction, traversal.StatusOK, graph.DirectionCaller)
	}
	if got.Start.ID != "method:m" || got.Start.QualifiedName != "example.M" {
		t.Errorf("View.Start = %#v, want resolved method:m", got.Start)
	}
	if ids := nodeViewIDs(got.Nodes); !reflect.DeepEqual(ids, []string{"method:a", "method:m", "method:z"}) {
		t.Errorf("View.Nodes IDs = %v, want sorted", ids)
	}
	if got.Nodes[0].QualifiedName != "example.A" || got.Nodes[0].Source != source || got.Nodes[0].MinDepth != 1 || !reflect.DeepEqual(got.Nodes[0].Metadata, nodeMetadata) {
		t.Errorf("View.Nodes[0] = %#v, want resolved method:a at depth 1", got.Nodes[0])
	}
	if ids := edgeViewIDs(got.Edges); !reflect.DeepEqual(ids, []string{"edge:a", "edge:z"}) {
		t.Errorf("View.Edges IDs = %v, want sorted", ids)
	}
	if !got.Edges[1].Cycle || got.Edges[1].CallSite != callSite || !reflect.DeepEqual(got.Edges[1].Metadata, edgeMetadata) {
		t.Errorf("View.Edges[1] = %#v, want cycle edge with call site", got.Edges[1])
	}
	if ids := cutoffViewIDs(got.Cutoffs); !reflect.DeepEqual(ids, []string{"cut:a", "cut:z"}) {
		t.Errorf("View.Cutoffs IDs = %v, want sorted", ids)
	}
	if got.Cutoffs[0].TargetMethodID != "method:x" || got.Cutoffs[0].TargetMinDepth != 2 {
		t.Errorf("View.Cutoffs[0] = %#v, want caller target method:x at depth 2", got.Cutoffs[0])
	}
	if !reflect.DeepEqual(got, buildView(in)) {
		t.Error("buildView returned different values for the same input")
	}
}

func TestBuildViewUsesCalleeAsCutoffTarget(t *testing.T) {
	edge := graph.Edge{ID: "edge:ab", CallerID: "method:a", CalleeID: "method:b"}
	in := Input{
		Graph: graph.New(),
		Result: traversal.Result{
			Status: traversal.StatusOK,
			Nodes:  map[string]bool{"method:a": true},
			Depths: map[string]int{"method:a": 0},
			DepthCutoffs: map[string]traversal.DepthCutoff{
				"edge:ab": {Edge: edge, TargetMinDepth: 1},
			},
		},
		Request: traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee},
	}

	got := buildView(in)
	if got.Cutoffs[0].TargetMethodID != "method:b" {
		t.Errorf("TargetMethodID = %q, want method:b", got.Cutoffs[0].TargetMethodID)
	}
}

func TestBuildViewAllowsMissingStartSymbol(t *testing.T) {
	in := Input{
		Graph: graph.New(),
		Result: traversal.Result{
			Status: traversal.StatusStartNotFound,
			Nodes:  map[string]bool{}, Depths: map[string]int{}, Edges: map[string]graph.Edge{},
			Cycles: map[string]bool{}, DepthCutoffs: map[string]traversal.DepthCutoff{},
		},
		Request: traversal.Request{StartID: "method:missing", Direction: graph.DirectionCallee},
	}

	got := buildView(in)
	if !reflect.DeepEqual(got.Start, NodeView{ID: "method:missing"}) {
		t.Errorf("View.Start = %#v, want ID-only start", got.Start)
	}
}

func TestWriteRejectsUnregisteredFormatBeforeWriting(t *testing.T) {
	var out bytes.Buffer
	err := Write(&out, Format("yaml"), Input{})
	if err == nil {
		t.Fatal("Write(yaml) returned nil error, want unsupported format error")
	}
	if out.Len() != 0 {
		t.Errorf("Write(yaml) wrote %q, want no output", out.String())
	}
}

func TestRegisteredFormatsReturnsRegisteredFormatNames(t *testing.T) {
	want := []string{"console", "json"}
	if got := RegisteredFormats(); !reflect.DeepEqual(got, want) {
		t.Fatalf("RegisteredFormats() = %v, want %v", got, want)
	}
}

func TestWriteBuildsViewAndCallsFormatter(t *testing.T) {
	formatter := &recordingFormatter{}

	g := graphtest.NewBuilder().Node("method:a").Build()
	in := Input{
		Graph: g,
		Result: traversal.Result{
			Status: traversal.StatusOK,
			Nodes:  map[string]bool{"method:a": true}, Depths: map[string]int{"method:a": 0},
			Edges: map[string]graph.Edge{}, Cycles: map[string]bool{}, DepthCutoffs: map[string]traversal.DepthCutoff{},
		},
		Request: traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee},
	}
	var out bytes.Buffer

	if err := write(&out, formatter, in); err != nil {
		t.Fatalf("write() returned error: %v", err)
	}
	if !formatter.called || formatter.view.Start.ID != "method:a" {
		t.Errorf("formatter call = called:%v view:%#v, want resolved method:a", formatter.called, formatter.view)
	}
	if out.String() != "formatted" {
		t.Errorf("write() output = %q, want formatted", out.String())
	}
}

func TestWriteReturnsFormatterError(t *testing.T) {
	want := errors.New("write failed")
	formatter := formatterFunc(func(_ io.Writer, _ View) error { return want })

	err := write(&bytes.Buffer{}, formatter, Input{Graph: graph.New()})
	if !errors.Is(err, want) {
		t.Errorf("write() error = %v, want %v", err, want)
	}
}

// Write が要求された format の registry 登録済み formatter へ振り分けること。
//
// 上の seam テストは formatter を直接渡すため、registry の対応付けが誤っていても
// 緑のままになる。その穴を本テストが塞ぐ。
func TestWriteDispatchesToTheFormatterOfTheRequestedFormat(t *testing.T) {
	g := graphtest.NewBuilder().Node("method:a").Build()
	in := Input{
		Graph: g,
		Result: traversal.Result{
			Status: traversal.StatusOK,
			Nodes:  map[string]bool{"method:a": true}, Depths: map[string]int{"method:a": 0},
			Edges: map[string]graph.Edge{}, Cycles: map[string]bool{}, DepthCutoffs: map[string]traversal.DepthCutoff{},
		},
		Request: traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee},
	}

	tests := []struct {
		format   Format
		wantJSON bool
	}{
		{format: FormatConsole, wantJSON: false},
		{format: FormatJSON, wantJSON: true},
	}
	for _, tt := range tests {
		t.Run(string(tt.format), func(t *testing.T) {
			var out bytes.Buffer
			if err := Write(&out, tt.format, in); err != nil {
				t.Fatalf("Write(%s) returned error: %v", tt.format, err)
			}
			if out.Len() == 0 {
				t.Fatalf("Write(%s) produced no output", tt.format)
			}
			if gotJSON := json.Valid(out.Bytes()); gotJSON != tt.wantJSON {
				t.Fatalf("Write(%s) produced JSON = %v, want %v (output: %q)", tt.format, gotJSON, tt.wantJSON, out.String())
			}
		})
	}
}

// Write が formatter の error を握り潰さずそのまま返すこと。
// registry 経由の経路を通すため、stub の formatter ではなく必ず失敗する入力を使う。
func TestWritePropagatesFormatterErrorThroughTheRegistry(t *testing.T) {
	want := errors.New("writer closed")
	failing := writerFunc(func([]byte) (int, error) { return 0, want })

	err := Write(failing, FormatJSON, Input{Graph: graph.New()})
	if !errors.Is(err, want) {
		t.Fatalf("Write() error = %v, want the writer error", err)
	}
}

type recordingFormatter struct {
	called bool
	view   View
}

func (f *recordingFormatter) Format(w io.Writer, view View) error {
	f.called = true
	f.view = view
	_, err := io.WriteString(w, "formatted")
	return err
}

// writerFunc adapts a function to io.Writer so a test can force a write
// failure.
type writerFunc func([]byte) (int, error)

func (f writerFunc) Write(p []byte) (int, error) { return f(p) }

type formatterFunc func(io.Writer, View) error

func (f formatterFunc) Format(w io.Writer, view View) error { return f(w, view) }

func nodeViewIDs(nodes []NodeView) []string {
	ids := make([]string, len(nodes))
	for i, node := range nodes {
		ids[i] = node.ID
	}
	return ids
}

func edgeViewIDs(edges []EdgeView) []string {
	ids := make([]string, len(edges))
	for i, edge := range edges {
		ids[i] = edge.ID
	}
	return ids
}

func cutoffViewIDs(cutoffs []CutoffView) []string {
	ids := make([]string, len(cutoffs))
	for i, cutoff := range cutoffs {
		ids[i] = cutoff.EdgeID
	}
	return ids
}
