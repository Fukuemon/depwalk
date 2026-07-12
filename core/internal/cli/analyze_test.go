package cli

import (
	"bytes"
	"fmt"
	"io"
	"os"
	"strings"
	"testing"
)

func TestAnalyzeCommandBuildsGraphThroughFakeAnalyzer(t *testing.T) {
	t.Parallel()

	cmd := newAnalyzeCommand()
	var stdout, stderr bytes.Buffer
	cmd.SetOut(&stdout)
	cmd.SetErr(&stderr)
	cmd.SetArgs([]string{
		t.TempDir(),
		"--language=java",
		"--analyzer-cmd=" + fakeAnalyzerCommand(t, "success"),
		"--analyzer-meta=classpath=/a.jar",
	})

	if err := cmd.Execute(); err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if got := stdout.String(); !strings.Contains(got, "analyzed 2 method(s), 1 call edge(s)") {
		t.Fatalf("stdout = %q, want a summary of 2 methods and 1 call edge", got)
	}
	if got := stderr.String(); !strings.Contains(got, "diagnostic [warning]") {
		t.Fatalf("stderr = %q, want the propagated diagnostic", got)
	}
}

func TestAnalyzeCommandRejectsMissingLanguage(t *testing.T) {
	t.Parallel()

	cmd := newAnalyzeCommand()
	cmd.SetOut(io.Discard)
	cmd.SetErr(io.Discard)
	cmd.SetArgs([]string{
		t.TempDir(),
		"--analyzer-cmd=" + fakeAnalyzerCommand(t, "success"),
	})

	if err := cmd.Execute(); err == nil {
		t.Fatal("Execute() error = nil, want validation error for missing --language")
	}
}

func TestAnalyzeCommandRejectsMissingAnalyzerCommand(t *testing.T) {
	t.Parallel()

	cmd := newAnalyzeCommand()
	cmd.SetOut(io.Discard)
	cmd.SetErr(io.Discard)
	cmd.SetArgs([]string{
		t.TempDir(),
		"--language=java",
	})

	if err := cmd.Execute(); err == nil {
		t.Fatal("Execute() error = nil, want validation error when no analyzer command is configured")
	}
}

func TestAnalyzeCommandFailsOnFatalAnalyzerError(t *testing.T) {
	t.Parallel()

	cmd := newAnalyzeCommand()
	cmd.SetOut(io.Discard)
	cmd.SetErr(io.Discard)
	cmd.SetArgs([]string{
		t.TempDir(),
		"--language=java",
		"--analyzer-cmd=" + fakeAnalyzerCommand(t, "analyzer-error"),
	})

	if err := cmd.Execute(); err == nil {
		t.Fatal("Execute() error = nil, want error for a fatal analyzer error record")
	}
}

// fakeAnalyzerCommand returns an --analyzer-cmd string that re-invokes the
// current test binary as a fake Analyzer process (TestFakeAnalyzerHelperProcess
// below), keeping cli package tests independent of a JVM.
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
