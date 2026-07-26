package protocol

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"reflect"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/analyze"
	"github.com/Fukuemon/depwalk/core/internal/analyzer"
	"github.com/Fukuemon/depwalk/core/internal/graph"
)

// Adapter tests moved from the analyze package: they drive a fake Analyzer
// subprocess through the full ACL (wire request composition, record
// parsing, wire → domain translation) via the analyze.Source port.

func fakeAnalyzerAdapter(scenario string) *Adapter {
	return NewAdapter(analyzer.Command{
		Path: os.Args[0],
		Args: []string{"-test.run=TestAdapterFakeAnalyzerProcess", "--", "--adapter-fake-analyzer", scenario},
	})
}

// runAdapter collects the streamed domain values alongside the outcome.
func runAdapter(adapter *Adapter, request analyze.Request) ([]graph.Node, []graph.Edge, analyze.Outcome, error) {
	var nodes []graph.Node
	var edges []graph.Edge
	outcome, err := adapter.Run(request,
		func(node graph.Node) { nodes = append(nodes, node) },
		func(edge graph.Edge) { edges = append(edges, edge) },
	)
	return nodes, edges, outcome, err
}

func TestAdapterStreamsTranslatedDomainValues(t *testing.T) {
	t.Parallel()

	nodes, edges, outcome, err := runAdapter(fakeAnalyzerAdapter("success"), analyze.Request{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
		Metadata:      map[string]any{"classpath": []string{"/a.jar", "/b.jar"}},
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if outcome.ExitCode != 0 || outcome.Failure != nil || outcome.ValidationError != nil {
		t.Fatalf("outcome = %+v, want clean success", outcome)
	}
	wantNodes := []graph.Node{
		{ID: "method:caller", Symbol: graph.Symbol{QualifiedName: "example.Caller.run", Signature: "run():void"}},
		{ID: "method:callee", Symbol: graph.Symbol{QualifiedName: "example.Callee.run", Signature: "run():void"}},
	}
	if !reflect.DeepEqual(nodes, wantNodes) {
		t.Fatalf("nodes = %#v, want %#v", nodes, wantNodes)
	}
	wantEdges := []graph.Edge{{ID: "edge:1", CallerID: "method:caller", CalleeID: "method:callee"}}
	if !reflect.DeepEqual(edges, wantEdges) {
		t.Fatalf("edges = %#v, want %#v", edges, wantEdges)
	}
	wantDiagnostics := []analyze.Diagnostic{{Severity: "warning", Code: "JAVA_UNRESOLVED_SYMBOL", Message: "unresolved"}}
	if !reflect.DeepEqual(outcome.Diagnostics, wantDiagnostics) {
		t.Fatalf("Diagnostics = %#v, want %#v", outcome.Diagnostics, wantDiagnostics)
	}
}

func TestAdapterTranslatesAnalyzerErrorRecord(t *testing.T) {
	t.Parallel()

	_, _, outcome, err := runAdapter(fakeAnalyzerAdapter("analyzer-error"), analyze.Request{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if outcome.Failure == nil {
		t.Fatal("Failure = nil, want the fatal analyzer error record")
	}
	if outcome.Failure.Code != "JAVA_FATAL" || outcome.Failure.Message != "boom" {
		t.Fatalf("Failure = %+v, want JAVA_FATAL / boom", outcome.Failure)
	}
	if outcome.ExitCode != 1 {
		t.Fatalf("ExitCode = %d, want 1", outcome.ExitCode)
	}
}

func TestAdapterTranslatesStructuredFailureDetails(t *testing.T) {
	t.Parallel()

	_, _, outcome, err := runAdapter(fakeAnalyzerAdapter("error-with-details"), analyze.Request{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	failure := outcome.Failure
	if failure == nil {
		t.Fatal("Failure = nil, want structured failure")
	}
	wantLocation := &graph.SourceLocation{Path: "module-a/src/Main.java", StartLine: 3}
	if !reflect.DeepEqual(failure.Location, wantLocation) {
		t.Fatalf("Source = %#v, want %#v", failure.Location, wantLocation)
	}
	wantMetadata := map[string]any{"phase": "completeness"}
	if !reflect.DeepEqual(failure.Metadata, wantMetadata) {
		t.Fatalf("Metadata = %#v, want %#v", failure.Metadata, wantMetadata)
	}
	want := []analyze.FailureDetail{
		{
			Code:     "DETAIL_CODE_B",
			Message:  "first detail",
			Location: &graph.SourceLocation{Path: "module-b/src/App.java", StartLine: 12},
			Metadata: map[string]any{"kind": "virtual", "candidates": []any{"z", "a"}},
		},
		{Code: "DETAIL_CODE_A", Message: "second detail"},
	}
	if !reflect.DeepEqual(failure.Details, want) {
		t.Fatalf("Details = %#v, want %#v", failure.Details, want)
	}
}

func TestAdapterKeepsFatalReasonOverReferenceIncompleteness(t *testing.T) {
	t.Parallel()

	// The fake analyzer streams a dangling call edge before its fatal error
	// record; the fatal record must be reported and the reference-
	// completeness failure suppressed (fatal streams discard prior records).
	_, _, outcome, err := runAdapter(fakeAnalyzerAdapter("dangling-edge-then-error"), analyze.Request{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if outcome.Failure == nil {
		t.Fatal("Failure = nil, want the fatal record to win")
	}
	if outcome.ValidationError != nil {
		t.Fatalf("ValidationError = %v, want nil on a fatal stream", outcome.ValidationError)
	}
}

func TestAdapterReportsReferenceIncompletenessAfterCleanExit(t *testing.T) {
	t.Parallel()

	_, _, outcome, err := runAdapter(fakeAnalyzerAdapter("dangling-edge-clean-exit"), analyze.Request{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if outcome.ValidationError == nil {
		t.Fatal("ValidationError = nil, want reference-completeness failure on clean exit")
	}
}

func TestAdapterReportsNonZeroExit(t *testing.T) {
	t.Parallel()

	_, _, outcome, err := runAdapter(fakeAnalyzerAdapter("bad-exit"), analyze.Request{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if outcome.ExitCode != 3 {
		t.Fatalf("ExitCode = %d, want 3", outcome.ExitCode)
	}
}

// assertRequestScenarioPassed fails the test when the fake Analyzer
// rejected the wire request. The fake reports assertion failures by
// exiting non-zero, and a non-zero exit is carried in the outcome rather
// than returned as an error, so the exit code must be checked explicitly.
func assertRequestScenarioPassed(t *testing.T, outcome analyze.Outcome, err error) {
	t.Helper()

	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if outcome.ExitCode != 0 {
		t.Fatalf("fake analyzer rejected the wire request (exit %d)", outcome.ExitCode)
	}
}

func TestAdapterSendsExplicitFullGraphRequestWithFilters(t *testing.T) {
	t.Parallel()

	_, _, outcome, err := runAdapter(fakeAnalyzerAdapter("request-options"), analyze.Request{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
		SourceRoots:   []string{"module-b/src", "module-a/src"},
		Include:       []string{"src/**", "generated/**"},
		Exclude:       []string{"**/vendor/**", "**/*Test.java"},
	})
	assertRequestScenarioPassed(t, outcome, err)
}

func TestAdapterOmitsUnsetFiltersAndEntrypoints(t *testing.T) {
	t.Parallel()

	_, _, outcome, err := runAdapter(fakeAnalyzerAdapter("request-defaults"), analyze.Request{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
	})
	assertRequestScenarioPassed(t, outcome, err)
}

// The no-op guard for the two tests above: a deliberately wrong wire
// request must make them fail. The fake analyzer's "request-options"
// scenario expects filters, so sending none has to be rejected.
func TestAdapterRequestAssertionsDetectAWrongRequest(t *testing.T) {
	t.Parallel()

	_, _, outcome, err := runAdapter(fakeAnalyzerAdapter("request-options"), analyze.Request{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if outcome.ExitCode == 0 {
		t.Fatal("ExitCode = 0, want the fake analyzer to reject a request without the expected filters")
	}
}

func TestAdapterMarksInvalidRequestValuesAsInputErrorBeforeAnalyzerLaunch(t *testing.T) {
	t.Parallel()

	_, _, _, err := runAdapter(NewAdapter(analyzer.Command{Path: "definitely-not-a-real-analyzer"}), analyze.Request{
		WorkspaceRoot: t.TempDir(),
		Language:      "java",
		Include:       []string{"../outside/**"},
	})
	var inputErr *analyze.InputError
	if !errors.As(err, &inputErr) {
		t.Fatalf("Run() error = %v, want *analyze.InputError", err)
	}
}

// TestAdapterFakeAnalyzerProcess is not a real test. It is re-executed as a
// subprocess by fakeAnalyzerAdapter and acts as a minimal Analyzer Protocol
// implementation for the adapter tests, keeping them independent of a JVM.
func TestAdapterFakeAnalyzerProcess(t *testing.T) {
	scenario := adapterHelperScenario()
	if scenario == "" {
		return
	}

	requestBytes, err := io.ReadAll(os.Stdin)
	if err != nil {
		os.Exit(2)
	}

	switch scenario {
	case "success":
		fmt.Print(methodSymbolJSONL("method:caller", "example.Caller.run") +
			methodSymbolJSONL("method:callee", "example.Callee.run") +
			callEdgeJSONL("edge:1", "method:caller", "method:callee") +
			`{"schemaVersion":"1","recordType":"diagnostic","severity":"warning","code":"JAVA_UNRESOLVED_SYMBOL","message":"unresolved"}` + "\n")
		os.Exit(0)
	case "analyzer-error":
		fmt.Print(`{"schemaVersion":"1","recordType":"error","code":"JAVA_FATAL","message":"boom"}` + "\n")
		os.Exit(1)
	case "error-with-details":
		fmt.Print(`{"schemaVersion":"1","recordType":"error","code":"SOME_ANALYZER_CODE","message":"unresolved calls remain",` +
			`"sourceLocation":{"path":"module-a/src/Main.java","startLine":3},"metadata":{"phase":"completeness"},"details":[` +
			`{"code":"DETAIL_CODE_B","message":"first detail","sourceLocation":{"path":"module-b/src/App.java","startLine":12},"metadata":{"kind":"virtual","candidates":["z","a"]}},` +
			`{"code":"DETAIL_CODE_A","message":"second detail"}]}` + "\n")
		os.Exit(1)
	case "dangling-edge-then-error":
		fmt.Print(methodSymbolJSONL("method:caller", "example.Caller.run") +
			callEdgeJSONL("edge:1", "method:caller", "method:missing") +
			`{"schemaVersion":"1","recordType":"error","code":"JAVA_FATAL","message":"boom"}` + "\n")
		os.Exit(1)
	case "dangling-edge-clean-exit":
		fmt.Print(methodSymbolJSONL("method:caller", "example.Caller.run") +
			callEdgeJSONL("edge:1", "method:caller", "method:missing"))
		os.Exit(0)
	case "bad-exit":
		os.Exit(3)
	case "request-options":
		assertAdapterRequest(requestBytes, true)
		os.Exit(0)
	case "request-defaults":
		assertAdapterRequest(requestBytes, false)
		os.Exit(0)
	default:
		os.Exit(2)
	}
}

// assertAdapterRequest verifies the wire request the adapter composed:
// schema fields, generated request id, fullGraph mode, and the presence /
// omission of the optional filter fields.
func assertAdapterRequest(requestBytes []byte, withFilters bool) {
	var request AnalysisRequest
	if err := json.Unmarshal(requestBytes, &request); err != nil {
		fmt.Fprintf(os.Stderr, "decode request: %v\n", err)
		os.Exit(2)
	}
	if request.SchemaVersion != SchemaVersion || request.RecordType != RecordTypeAnalysisRequest {
		fmt.Fprintf(os.Stderr, "schema/recordType = %q/%q, want %q/%q\n", request.SchemaVersion, request.RecordType, SchemaVersion, RecordTypeAnalysisRequest)
		os.Exit(2)
	}
	if request.RequestID == "" {
		fmt.Fprintln(os.Stderr, "requestId is empty, want a generated id")
		os.Exit(2)
	}
	if request.AnalysisMode != AnalysisModeFullGraph {
		fmt.Fprintf(os.Stderr, "analysisMode = %q, want fullGraph\n", request.AnalysisMode)
		os.Exit(2)
	}
	if len(request.Entrypoints) != 0 {
		fmt.Fprintf(os.Stderr, "entrypoints = %#v, want empty\n", request.Entrypoints)
		os.Exit(2)
	}
	var raw map[string]json.RawMessage
	if err := json.Unmarshal(requestBytes, &raw); err != nil {
		fmt.Fprintf(os.Stderr, "decode raw request: %v\n", err)
		os.Exit(2)
	}
	if _, present := raw["entrypoints"]; present {
		fmt.Fprintln(os.Stderr, "entrypoints field present, want omitted")
		os.Exit(2)
	}
	if withFilters {
		if !reflect.DeepEqual(request.Include, []string{"src/**", "generated/**"}) || !reflect.DeepEqual(request.Exclude, []string{"**/vendor/**", "**/*Test.java"}) {
			fmt.Fprintf(os.Stderr, "filters = %#v/%#v, want ordered values\n", request.Include, request.Exclude)
			os.Exit(2)
		}
		if !reflect.DeepEqual(request.SourceRoots, []string{"module-b/src", "module-a/src"}) {
			fmt.Fprintf(os.Stderr, "sourceRoots = %#v, want the values in flag order\n", request.SourceRoots)
			os.Exit(2)
		}
		return
	}
	for _, field := range []string{"include", "exclude", "sourceRoots"} {
		if _, present := raw[field]; present {
			fmt.Fprintf(os.Stderr, "%s field present, want omitted\n", field)
			os.Exit(2)
		}
	}
}

func adapterHelperScenario() string {
	args := os.Args
	for i, arg := range args {
		if arg == "--adapter-fake-analyzer" && i+1 < len(args) {
			return args[i+1]
		}
	}
	return ""
}
