package analyze

import (
	"errors"
	"reflect"
	"strings"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/graph"
)

// fakeSource is an in-package AnalysisSource fake: it streams the
// configured nodes / edges to the callbacks and returns the configured
// outcome, recording the request it received. Process-level behavior
// (fake Analyzer subprocesses) is tested against the ACL adapter in the
// protocol package.
type fakeSource struct {
	nodes   []graph.Node
	edges   []graph.Edge
	outcome AnalysisOutcome
	err     error

	gotRequest AnalysisRequest
	called     bool
}

func (f *fakeSource) RunAnalysis(
	request AnalysisRequest,
	onNode func(graph.Node),
	onEdge func(graph.Edge),
) (AnalysisOutcome, error) {
	f.called = true
	f.gotRequest = request
	if f.err != nil {
		return AnalysisOutcome{}, f.err
	}
	for _, node := range f.nodes {
		onNode(node)
	}
	for _, edge := range f.edges {
		onEdge(edge)
	}
	return f.outcome, nil
}

func successSource() *fakeSource {
	return &fakeSource{
		nodes: []graph.Node{
			{ID: "method:caller", Symbol: graph.Symbol{QualifiedName: "example.Caller.run", Signature: "run():void"}},
			{ID: "method:callee", Symbol: graph.Symbol{QualifiedName: "example.Callee.run", Signature: "run():void"}},
		},
		edges: []graph.Edge{
			{ID: "edge:1", CallerID: "method:caller", CalleeID: "method:callee"},
		},
		outcome: AnalysisOutcome{
			Diagnostics: []Diagnostic{{Severity: "warning", Code: "JAVA_UNRESOLVED_SYMBOL", Message: "unresolved"}},
		},
	}
}

func querySource() *fakeSource {
	return &fakeSource{
		nodes: []graph.Node{
			{ID: "opaque-caller", Symbol: graph.Symbol{QualifiedName: "com.example.Controller.call", Signature: "com.example.Controller#call()"}},
			{ID: "opaque-target", Symbol: graph.Symbol{QualifiedName: "com.example.Service.find", Signature: "com.example.Service#find(java.lang.Long)"}},
		},
		edges: []graph.Edge{
			{ID: "opaque-edge", CallerID: "opaque-caller", CalleeID: "opaque-target"},
		},
	}
}

func TestRunBuildsGraphFromSourceRecords(t *testing.T) {
	t.Parallel()

	result, err := New(successSource()).Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
		AnalyzerMeta:  []string{"classpath=/a.jar", "classpath=/b.jar"},
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if result.MethodCount != 2 {
		t.Fatalf("MethodCount = %d, want 2", result.MethodCount)
	}
	if result.CallEdgeCount != 1 {
		t.Fatalf("CallEdgeCount = %d, want 1", result.CallEdgeCount)
	}
	if len(result.Diagnostics) != 1 {
		t.Fatalf("len(Diagnostics) = %d, want 1", len(result.Diagnostics))
	}
	if _, ok := result.Graph.Node("method:caller"); !ok {
		t.Fatal("Graph does not contain method:caller")
	}
	if _, ok := result.Graph.Node("method:callee"); !ok {
		t.Fatal("Graph does not contain method:callee")
	}
	if result.MethodQuery != nil {
		t.Fatalf("MethodQuery = %+v, want nil without Options.Method", result.MethodQuery)
	}
}

func TestRunPassesRequestFieldsThroughToTheSource(t *testing.T) {
	t.Parallel()

	source := successSource()
	workspaceRoot := t.TempDir()
	_, err := New(source).Run(Options{
		WorkspaceRoot: workspaceRoot,
		SourceRoots:   []string{"module-b/src", "module-a/src"},
		Language:      "java",
		AnalyzerMeta:  []string{"classpath=/a.jar", "classpath=/b.jar"},
		Include:       []string{"src/**", "generated/**"},
		Exclude:       []string{"**/vendor/**", "**/*Test.java"},
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	want := AnalysisRequest{
		WorkspaceRoot: workspaceRoot,
		SourceRoots:   []string{"module-b/src", "module-a/src"},
		Language:      "java",
		Include:       []string{"src/**", "generated/**"},
		Exclude:       []string{"**/vendor/**", "**/*Test.java"},
		Metadata:      map[string]any{"classpath": []string{"/a.jar", "/b.jar"}},
	}
	if !reflect.DeepEqual(source.gotRequest, want) {
		t.Fatalf("request = %#v, want %#v", source.gotRequest, want)
	}
}

func TestRunRejectsInvalidAnalyzerMetaBeforeCallingTheSource(t *testing.T) {
	t.Parallel()

	source := successSource()
	_, err := New(source).Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
		AnalyzerMeta:  []string{"missing-equals"},
	})
	if err == nil {
		t.Fatal("Run() error = nil, want metadata validation error")
	}
	if source.called {
		t.Fatal("source was called, want validation to fail first")
	}
}

func TestRunRequiresAnAnalysisSource(t *testing.T) {
	t.Parallel()

	_, err := New(nil).Run(Options{WorkspaceRoot: t.TempDir(), Language: "java"})
	if err == nil {
		t.Fatal("Run() error = nil, want error for a missing AnalysisSource")
	}
}

func TestRunPropagatesSourceError(t *testing.T) {
	t.Parallel()

	want := errors.New("launch failed")
	_, err := New(&fakeSource{err: want}).Run(Options{WorkspaceRoot: t.TempDir(), Language: "java"})
	if !errors.Is(err, want) {
		t.Fatalf("Run() error = %v, want the source error", err)
	}
}

func TestRunPropagatesAnalyzerFailure(t *testing.T) {
	t.Parallel()

	source := &fakeSource{outcome: AnalysisOutcome{
		Failure:  &AnalyzerFailure{Code: "JAVA_FATAL", Message: "boom"},
		ExitCode: 1,
	}}
	_, err := New(source).Run(Options{WorkspaceRoot: t.TempDir(), Language: "java"})
	if err == nil {
		t.Fatal("Run() error = nil, want error for an analyzer error record")
	}
	var failure *AnalyzerFailure
	if !errors.As(err, &failure) {
		t.Fatalf("Run() error = %v, want *AnalyzerFailure", err)
	}
	if failure.Code != "JAVA_FATAL" {
		t.Fatalf("failure code = %q, want JAVA_FATAL", failure.Code)
	}
}

func TestRunKeepsFatalReasonOverValidationErrorAndPublishesNothing(t *testing.T) {
	t.Parallel()

	// A fatal record and a validation error on the same stream: the fatal
	// reason must win, and no partial result may be published.
	source := &fakeSource{
		nodes: []graph.Node{{ID: "method:caller"}},
		edges: []graph.Edge{{ID: "edge:1", CallerID: "method:caller", CalleeID: "method:missing"}},
		outcome: AnalysisOutcome{
			Failure:         &AnalyzerFailure{Code: "JAVA_FATAL", Message: "boom"},
			ValidationError: errors.New("dangling edge"),
			ExitCode:        1,
		},
	}
	result, err := New(source).Run(Options{WorkspaceRoot: t.TempDir(), Language: "java"})
	var failure *AnalyzerFailure
	if !errors.As(err, &failure) {
		t.Fatalf("Run() error = %v, want *AnalyzerFailure keeping the fatal reason", err)
	}
	if result.Graph != nil || result.MethodCount != 0 || result.CallEdgeCount != 0 || result.Diagnostics != nil {
		t.Fatalf("Result = %+v, want zero value on fatal failure", result)
	}
}

func TestRunFailsOnValidationErrorAfterCleanExit(t *testing.T) {
	t.Parallel()

	source := &fakeSource{outcome: AnalysisOutcome{ValidationError: errors.New("dangling edge")}}
	_, err := New(source).Run(Options{WorkspaceRoot: t.TempDir(), Language: "java"})
	if err == nil || !strings.Contains(err.Error(), "analyzer stdout did not follow the analyzer protocol") {
		t.Fatalf("Run() error = %v, want a protocol failure on clean exit", err)
	}
}

func TestRunPropagatesNonZeroExit(t *testing.T) {
	t.Parallel()

	source := &fakeSource{outcome: AnalysisOutcome{ExitCode: 3}}
	_, err := New(source).Run(Options{WorkspaceRoot: t.TempDir(), Language: "java"})
	if err == nil || !strings.Contains(err.Error(), "exited with code 3") {
		t.Fatalf("Run() error = %v, want error for a non-zero exit code", err)
	}
}

func TestRunQueryMatchesFullSignatureAndReturnsTraversal(t *testing.T) {
	t.Parallel()

	result, err := New(querySource()).Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
		Method:        "com.example.Service#find(java.lang.Long)",
		Direction:     graph.DirectionCaller,
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if result.MethodQuery == nil {
		t.Fatal("MethodQuery = nil, want traversal outcome for a method query")
	}
	if result.MethodQuery.Request.StartID != "opaque-target" {
		t.Fatalf("StartID = %q, want opaque-target", result.MethodQuery.Request.StartID)
	}
	if _, reached := result.MethodQuery.Result.Nodes["opaque-caller"]; !reached {
		t.Fatalf("traversal nodes = %#v, want opaque-caller reached", result.MethodQuery.Result.Nodes)
	}
}

func TestRunQueryMatchesUniqueSelectorWithoutSignature(t *testing.T) {
	t.Parallel()

	result, err := New(querySource()).Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
		Method:        "com.example.Service#find",
		Direction:     graph.DirectionCaller,
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if result.MethodQuery.Request.StartID != "opaque-target" {
		t.Fatalf("StartID = %q, want opaque-target", result.MethodQuery.Request.StartID)
	}
}

func TestRunQueryPassesMaxDepthToTraversal(t *testing.T) {
	t.Parallel()

	maxDepth := 0
	result, err := New(querySource()).Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
		Method:        "com.example.Service#find(java.lang.Long)",
		Direction:     graph.DirectionCaller,
		MaxDepth:      &maxDepth,
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if got := len(result.MethodQuery.Result.Nodes); got != 1 {
		t.Fatalf("reached nodes = %d, want start-only with max-depth 0", got)
	}
	cutoffs := result.MethodQuery.Result.DepthCutoffs
	if len(cutoffs) != 1 || cutoffs["opaque-edge"].Edge.CallerID != "opaque-caller" {
		t.Fatalf("cutoffs = %#v, want the caller edge cut at depth 0", cutoffs)
	}
}

func TestRunQueryReturnsTypedAmbiguousSelectorErrorWithCandidates(t *testing.T) {
	t.Parallel()

	source := &fakeSource{
		nodes: []graph.Node{
			{ID: "opaque-long", Symbol: graph.Symbol{QualifiedName: "com.example.Service.find", Signature: "com.example.Service#find(java.lang.Long)"}},
			{ID: "opaque-string", Symbol: graph.Symbol{QualifiedName: "com.example.Service.find", Signature: "com.example.Service#find(java.lang.String)"}},
		},
	}
	_, err := New(source).Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
		Method:        "com.example.Service#find",
		Direction:     graph.DirectionCaller,
	})
	var inputErr *InputError
	if !errors.As(err, &inputErr) {
		t.Fatalf("Run() error = %v, want *InputError", err)
	}
	for _, candidate := range []string{
		"com.example.Service#find(java.lang.Long)",
		"com.example.Service#find(java.lang.String)",
	} {
		if !strings.Contains(err.Error(), candidate) {
			t.Errorf("error %q missing candidate %q", err, candidate)
		}
	}
}

func TestRunQueryReturnsTypedNotFoundSelectorError(t *testing.T) {
	t.Parallel()

	_, err := New(querySource()).Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
		Method:        "com.example.Missing#run",
		Direction:     graph.DirectionCaller,
	})
	var inputErr *InputError
	if !errors.As(err, &inputErr) {
		t.Fatalf("Run() error = %v, want *InputError", err)
	}
	if !strings.Contains(err.Error(), "did not match any method") {
		t.Fatalf("Run() error = %v, want not-found explanation", err)
	}
}

func TestSelectMethodMatchesNestedBinaryNameWithoutSignature(t *testing.T) {
	t.Parallel()

	g := graph.New()
	g.AddNode(graph.Node{
		ID: "opaque-nested",
		Symbol: graph.Symbol{
			QualifiedName: "com.example.Outer.Inner.run",
			Signature:     "com.example.Outer$Inner#run()",
		},
	})

	got, err := selectMethod(g, "com.example.Outer$Inner#run")
	if err != nil {
		t.Fatalf("selectMethod() error = %v", err)
	}
	if got.ID != "opaque-nested" {
		t.Fatalf("selectMethod().ID = %q, want opaque-nested", got.ID)
	}
}

func TestSelectMethodReportsNestedOverloadsWithoutSignature(t *testing.T) {
	t.Parallel()

	g := graph.New()
	for _, node := range []graph.Node{
		{
			ID: "opaque-long",
			Symbol: graph.Symbol{
				QualifiedName: "com.example.Outer.Inner.run",
				Signature:     "com.example.Outer$Inner#run(java.lang.Long)",
			},
		},
		{
			ID: "opaque-string",
			Symbol: graph.Symbol{
				QualifiedName: "com.example.Outer.Inner.run",
				Signature:     "com.example.Outer$Inner#run(java.lang.String)",
			},
		},
	} {
		g.AddNode(node)
	}

	_, err := selectMethod(g, "com.example.Outer$Inner#run")
	var inputErr *InputError
	if !errors.As(err, &inputErr) {
		t.Fatalf("selectMethod() error = %v, want *InputError", err)
	}
	for _, candidate := range []string{
		"com.example.Outer$Inner#run(java.lang.Long)",
		"com.example.Outer$Inner#run(java.lang.String)",
	} {
		if !strings.Contains(err.Error(), candidate) {
			t.Errorf("error %q missing candidate %q", err, candidate)
		}
	}
}
