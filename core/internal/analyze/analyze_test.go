package analyze

import (
	"errors"
	"fmt"
	"io"
	"os"
	"strings"
	"testing"

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

	if _, err := io.ReadAll(os.Stdin); err != nil {
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
	default:
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
