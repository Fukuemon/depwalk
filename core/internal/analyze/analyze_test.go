package analyze

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"reflect"
	"strings"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/output"
	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

func TestRunBuildsGraphFromFakeAnalyzer(t *testing.T) {
	t.Parallel()

	result, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		AnalyzerCmd:   fakeAnalyzerCommand(t, "success"),
		AnalyzerMeta:  []string{"classpath=/a.jar", "classpath=/b.jar"},
		Getenv:        func(string) string { return "" },
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
}

func TestRunPropagatesAnalyzerError(t *testing.T) {
	t.Parallel()

	_, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		AnalyzerCmd:   fakeAnalyzerCommand(t, "analyzer-error"),
		Getenv:        func(string) string { return "" },
	})
	if err == nil {
		t.Fatal("Run() error = nil, want error for an analyzer error record")
	}
	var failure *AnalyzerFailure
	if !errors.As(err, &failure) {
		t.Fatalf("Run() error = %v, want *AnalyzerFailure", err)
	}
	if failure.Record.Code != "JAVA_FATAL" {
		t.Fatalf("failure code = %q, want JAVA_FATAL", failure.Record.Code)
	}
}

func TestRunKeepsFatalReasonOverReferenceIncompleteness(t *testing.T) {
	t.Parallel()

	// The fake analyzer streams a dangling call edge before its fatal error
	// record; the fatal reason must win over the reference-completeness
	// protocol failure, and no partial result may be published.
	result, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		AnalyzerCmd:   fakeAnalyzerCommand(t, "dangling-edge-then-error"),
		Getenv:        func(string) string { return "" },
	})
	var failure *AnalyzerFailure
	if !errors.As(err, &failure) {
		t.Fatalf("Run() error = %v, want *AnalyzerFailure keeping the fatal reason", err)
	}
	if result.Graph != nil || result.MethodCount != 0 || result.CallEdgeCount != 0 || result.Diagnostics != nil {
		t.Fatalf("Result = %+v, want zero value on fatal failure", result)
	}
}

func TestRunFailsOnReferenceIncompletenessAfterCleanExit(t *testing.T) {
	t.Parallel()

	_, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		AnalyzerCmd:   fakeAnalyzerCommand(t, "dangling-edge-clean-exit"),
		Getenv:        func(string) string { return "" },
	})
	if err == nil || !strings.Contains(err.Error(), "analyzer stdout did not follow the analyzer protocol") {
		t.Fatalf("Run() error = %v, want a protocol failure for reference incompleteness on clean exit", err)
	}
}

func TestRunPropagatesNonZeroExit(t *testing.T) {
	t.Parallel()

	_, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		AnalyzerCmd:   fakeAnalyzerCommand(t, "bad-exit"),
		Getenv:        func(string) string { return "" },
	})
	if err == nil {
		t.Fatal("Run() error = nil, want error for a non-zero exit code")
	}
}

func TestRunRejectsMissingAnalyzerCommand(t *testing.T) {
	t.Parallel()

	_, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		Getenv:        func(string) string { return "" },
	})
	if err == nil {
		t.Fatal("Run() error = nil, want validation error when no analyzer command is configured")
	}
}

func TestRunSendsExplicitFullGraphRequestWithFilters(t *testing.T) {
	t.Parallel()

	_, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		AnalyzerCmd:   fakeAnalyzerCommand(t, "request-options"),
		Include:       []string{"src/**", "generated/**"},
		Exclude:       []string{"**/vendor/**", "**/*Test.java"},
		Getenv:        func(string) string { return "" },
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
}

func TestRunOmitsUnsetFiltersAndEntrypoints(t *testing.T) {
	t.Parallel()

	_, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		AnalyzerCmd:   fakeAnalyzerCommand(t, "request-defaults"),
		Getenv:        func(string) string { return "" },
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
}

func TestRunMarksInvalidRequestValuesAsInputErrorBeforeAnalyzerLaunch(t *testing.T) {
	t.Parallel()

	_, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		AnalyzerCmd:   "definitely-not-a-real-analyzer",
		Include:       []string{"../outside/**"},
		Getenv:        func(string) string { return "" },
	})
	var inputErr *InputError
	if !errors.As(err, &inputErr) {
		t.Fatalf("Run() error = %v, want *InputError", err)
	}
}

func TestRunQueryMatchesFullSignatureAndWritesTraversalOutput(t *testing.T) {
	t.Parallel()

	var out bytes.Buffer
	_, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		AnalyzerCmd:   fakeAnalyzerCommand(t, "query-single"),
		Method:        "com.example.Service#find(java.lang.Long)",
		Direction:     graph.DirectionCaller,
		Format:        output.FormatJSON,
		Output:        &out,
		Getenv:        func(string) string { return "" },
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	var document struct {
		Start string `json:"start"`
		Nodes []struct {
			MethodID string `json:"methodId"`
		} `json:"nodes"`
	}
	if err := json.Unmarshal(out.Bytes(), &document); err != nil {
		t.Fatalf("query output is not valid JSON: %v\n%s", err, out.String())
	}
	if document.Start != "opaque-target" {
		t.Fatalf("start = %q, want opaque-target", document.Start)
	}
	if got := []string{document.Nodes[0].MethodID, document.Nodes[1].MethodID}; !reflect.DeepEqual(got, []string{"opaque-caller", "opaque-target"}) {
		t.Fatalf("node IDs = %v, want methodId-independent caller result", got)
	}
}

func TestRunQueryMatchesUniqueSelectorWithoutSignature(t *testing.T) {
	t.Parallel()

	var out bytes.Buffer
	_, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		AnalyzerCmd:   fakeAnalyzerCommand(t, "query-single"),
		Method:        "com.example.Service#find",
		Direction:     graph.DirectionCaller,
		Format:        output.FormatConsole,
		Output:        &out,
		Getenv:        func(string) string { return "" },
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if !strings.Contains(out.String(), "com.example.Service#find(java.lang.Long)") || !strings.Contains(out.String(), "com.example.Controller#call()") {
		t.Fatalf("console output = %q, want selected method and caller", out.String())
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

func TestRunQueryPassesMaxDepthToTraversal(t *testing.T) {
	t.Parallel()

	maxDepth := 0
	var out bytes.Buffer
	_, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		AnalyzerCmd:   fakeAnalyzerCommand(t, "query-single"),
		Method:        "com.example.Service#find(java.lang.Long)",
		Direction:     graph.DirectionCaller,
		MaxDepth:      &maxDepth,
		Format:        output.FormatJSON,
		Output:        &out,
		Getenv:        func(string) string { return "" },
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	var document struct {
		Nodes        []json.RawMessage `json:"nodes"`
		DepthCutoffs []struct {
			TargetMethodID string `json:"targetMethodId"`
		} `json:"depthCutoffs"`
	}
	if err := json.Unmarshal(out.Bytes(), &document); err != nil {
		t.Fatalf("query output is not valid JSON: %v", err)
	}
	if len(document.Nodes) != 1 || len(document.DepthCutoffs) != 1 || document.DepthCutoffs[0].TargetMethodID != "opaque-caller" {
		t.Fatalf("max-depth output = nodes:%d cutoffs:%#v, want start-only with caller cutoff", len(document.Nodes), document.DepthCutoffs)
	}
}

func TestRunQueryPropagatesOutputWriteFailure(t *testing.T) {
	t.Parallel()

	want := errors.New("write failed")
	_, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		AnalyzerCmd:   fakeAnalyzerCommand(t, "query-single"),
		Method:        "com.example.Service#find(java.lang.Long)",
		Direction:     graph.DirectionCaller,
		Format:        output.FormatConsole,
		Output:        writerFunc(func([]byte) (int, error) { return 0, want }),
		Getenv:        func(string) string { return "" },
	})
	if !errors.Is(err, want) {
		t.Fatalf("Run() error = %v, want wrapped output error", err)
	}
}

func TestRunQueryReturnsTypedAmbiguousSelectorErrorWithCandidates(t *testing.T) {
	t.Parallel()

	_, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		AnalyzerCmd:   fakeAnalyzerCommand(t, "query-overloaded"),
		Method:        "com.example.Service#find",
		Direction:     graph.DirectionCaller,
		Format:        output.FormatConsole,
		Output:        io.Discard,
		Getenv:        func(string) string { return "" },
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

	_, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		AnalyzerCmd:   fakeAnalyzerCommand(t, "query-single"),
		Method:        "com.example.Missing#run",
		Direction:     graph.DirectionCaller,
		Format:        output.FormatConsole,
		Output:        io.Discard,
		Getenv:        func(string) string { return "" },
	})
	var inputErr *InputError
	if !errors.As(err, &inputErr) {
		t.Fatalf("Run() error = %v, want *InputError", err)
	}
	if !strings.Contains(err.Error(), "did not match any method") {
		t.Fatalf("Run() error = %v, want not-found explanation", err)
	}
}

func TestRunWithoutMethodKeepsSummaryModeAndDoesNotWriteQueryOutput(t *testing.T) {
	t.Parallel()

	var out bytes.Buffer
	result, err := Run(Options{
		WorkspaceRoot: t.TempDir(),
		Language:      protocol.LanguageJava,
		AnalyzerCmd:   fakeAnalyzerCommand(t, "success"),
		Output:        &out,
		Getenv:        func(string) string { return "" },
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if out.Len() != 0 {
		t.Fatalf("query output = %q, want none without Method", out.String())
	}
	if result.MethodCount != 2 || result.CallEdgeCount != 1 {
		t.Fatalf("summary counts = %d/%d, want 2/1", result.MethodCount, result.CallEdgeCount)
	}
}

// fakeAnalyzerCommand returns an --analyzer-cmd string that re-invokes the
// current test binary as a fake Analyzer process (TestFakeAnalyzerHelperProcess
// below), keeping analyze package tests independent of a JVM.
func fakeAnalyzerCommand(t *testing.T, scenario string) string {
	t.Helper()

	return fmt.Sprintf(`"%s" -test.run=TestFakeAnalyzerHelperProcess -- --fake-analyzer %s`, os.Args[0], scenario)
}

// TestFakeAnalyzerHelperProcess is not a real test. It is re-executed as a
// subprocess by fakeAnalyzerCommand and acts as a minimal Analyzer Protocol
// implementation for tests in this package.
func TestFakeAnalyzerHelperProcess(t *testing.T) {
	scenario := helperScenario()
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
			diagnosticJSONL())
		os.Exit(0)
	case "analyzer-error":
		fmt.Print(`{"schemaVersion":"1","recordType":"error","code":"JAVA_FATAL","message":"boom"}` + "\n")
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
		assertFakeAnalyzerRequest(requestBytes, true)
		os.Exit(0)
	case "request-defaults":
		assertFakeAnalyzerRequest(requestBytes, false)
		os.Exit(0)
	case "query-single":
		fmt.Print(methodSymbolWithSignatureJSONL("opaque-caller", "com.example.Controller.call", "com.example.Controller#call()") +
			methodSymbolWithSignatureJSONL("opaque-target", "com.example.Service.find", "com.example.Service#find(java.lang.Long)") +
			callEdgeJSONL("opaque-edge", "opaque-caller", "opaque-target"))
		os.Exit(0)
	case "query-overloaded":
		fmt.Print(methodSymbolWithSignatureJSONL("opaque-long", "com.example.Service.find", "com.example.Service#find(java.lang.Long)") +
			methodSymbolWithSignatureJSONL("opaque-string", "com.example.Service.find", "com.example.Service#find(java.lang.String)"))
		os.Exit(0)
	default:
		os.Exit(2)
	}
}

func assertFakeAnalyzerRequest(requestBytes []byte, withFilters bool) {
	var request protocol.AnalysisRequest
	if err := json.Unmarshal(requestBytes, &request); err != nil {
		fmt.Fprintf(os.Stderr, "decode request: %v\n", err)
		os.Exit(2)
	}
	if request.AnalysisMode != protocol.AnalysisModeFullGraph {
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
		return
	}
	if _, present := raw["include"]; present {
		fmt.Fprintln(os.Stderr, "include field present, want omitted")
		os.Exit(2)
	}
	if _, present := raw["exclude"]; present {
		fmt.Fprintln(os.Stderr, "exclude field present, want omitted")
		os.Exit(2)
	}
}

func helperScenario() string {
	args := os.Args
	for i, arg := range args {
		if arg == "--fake-analyzer" && i+1 < len(args) {
			return args[i+1]
		}
	}
	return ""
}

func methodSymbolJSONL(methodID, qualifiedName string) string {
	return fmt.Sprintf(
		`{"schemaVersion":"1","recordType":"methodSymbol","methodId":%q,"language":"java","symbolKind":"method","qualifiedName":%q,"signature":"run():void"}`+"\n",
		methodID,
		qualifiedName,
	)
}

func methodSymbolWithSignatureJSONL(methodID, qualifiedName, signature string) string {
	return fmt.Sprintf(
		`{"schemaVersion":"1","recordType":"methodSymbol","methodId":%q,"language":"java","symbolKind":"method","qualifiedName":%q,"signature":%q}`+"\n",
		methodID,
		qualifiedName,
		signature,
	)
}

func callEdgeJSONL(edgeID, callerMethodID, calleeMethodID string) string {
	return fmt.Sprintf(
		`{"schemaVersion":"1","recordType":"callEdge","edgeId":%q,"callerMethodId":%q,"calleeMethodId":%q}`+"\n",
		edgeID,
		callerMethodID,
		calleeMethodID,
	)
}

func diagnosticJSONL() string {
	return `{"schemaVersion":"1","recordType":"diagnostic","severity":"warning","code":"JAVA_UNRESOLVED_SYMBOL","message":"unresolved"}` + "\n"
}

type writerFunc func([]byte) (int, error)

func (f writerFunc) Write(p []byte) (int, error) { return f(p) }
