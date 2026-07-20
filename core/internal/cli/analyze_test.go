package cli

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/output"
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

func TestAnalyzeCommandPassesSourceRootsInOrder(t *testing.T) {
	t.Parallel()

	cmd := newAnalyzeCommand()
	var stdout, stderr bytes.Buffer
	cmd.SetOut(&stdout)
	cmd.SetErr(&stderr)
	cmd.SetArgs([]string{
		t.TempDir(),
		"--language=java",
		"--analyzer-cmd=" + fakeAnalyzerCommand(t, "echo-source-roots"),
		"--source-root=module-b/src/main/java",
		"--source-root=module-a/src/main/java",
	})

	if err := cmd.Execute(); err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if got := stderr.String(); !strings.Contains(got, "sourceRoots=module-b/src/main/java,module-a/src/main/java") {
		t.Fatalf("stderr = %q, want the request sourceRoots echoed in flag order", got)
	}
}

func TestAnalyzeCommandOmitsSourceRootsWhenFlagNotGiven(t *testing.T) {
	t.Parallel()

	cmd := newAnalyzeCommand()
	var stdout, stderr bytes.Buffer
	cmd.SetOut(&stdout)
	cmd.SetErr(&stderr)
	cmd.SetArgs([]string{
		t.TempDir(),
		"--language=java",
		"--analyzer-cmd=" + fakeAnalyzerCommand(t, "echo-source-roots"),
	})

	if err := cmd.Execute(); err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if got := stderr.String(); !strings.Contains(got, "sourceRoots=(absent)") {
		t.Fatalf("stderr = %q, want the sourceRoots field to be absent from the request", got)
	}
}

func TestAnalyzeCommandRejectsInvalidSourceRootBeforeAnalyzerLaunch(t *testing.T) {
	t.Parallel()

	cmd := newAnalyzeCommand()
	cmd.SetOut(io.Discard)
	cmd.SetErr(io.Discard)
	cmd.SetArgs([]string{
		t.TempDir(),
		"--language=java",
		"--analyzer-cmd=/nonexistent/analyzer/binary",
		"--source-root=../outside",
	})

	err := cmd.Execute()
	if err == nil {
		t.Fatal("Execute() error = nil, want protocol validation error for invalid --source-root")
	}
	if !strings.Contains(err.Error(), "sourceRoots") {
		t.Fatalf("Execute() error = %v, want a sourceRoots validation error (before analyzer launch)", err)
	}
	if got := ExitCode(err); got != 2 {
		t.Fatalf("ExitCode(error) = %d, want 2", got)
	}
}

func TestAnalyzeCommandPassesQueryFlagsAndFiltersInOrder(t *testing.T) {
	t.Parallel()

	cmd := newAnalyzeCommand()
	var stdout, stderr bytes.Buffer
	cmd.SetOut(&stdout)
	cmd.SetErr(&stderr)
	cmd.SetArgs([]string{
		t.TempDir(),
		"--language=java",
		"--analyzer-cmd=" + fakeAnalyzerCommand(t, "query-with-filters"),
		"--method=com.example.Service#find(java.lang.Long)",
		"--direction=caller",
		"--max-depth=0",
		"--format=json",
		"--include=src/**",
		"--include=generated/**",
		"--exclude=**/vendor/**",
		"--exclude=**/*Test.java",
	})

	if err := cmd.Execute(); err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	var document struct {
		Direction    string            `json:"direction"`
		Start        string            `json:"start"`
		Nodes        []json.RawMessage `json:"nodes"`
		DepthCutoffs []json.RawMessage `json:"depthCutoffs"`
	}
	if err := json.Unmarshal(stdout.Bytes(), &document); err != nil {
		t.Fatalf("stdout is not query JSON: %v\n%s", err, stdout.String())
	}
	if document.Direction != "caller" || document.Start != "opaque-target" || len(document.Nodes) != 1 || len(document.DepthCutoffs) != 1 {
		t.Fatalf("query output = %+v, want caller max-depth=0 from opaque-target", document)
	}
	wantFilters := "filters=src/**,generated/**|**/vendor/**,**/*Test.java"
	if !strings.Contains(stderr.String(), wantFilters) {
		t.Fatalf("stderr = %q, want %q", stderr.String(), wantFilters)
	}
	if strings.Contains(stdout.String(), "diagnostic") || strings.Contains(stdout.String(), wantFilters) {
		t.Fatalf("stdout = %q, want traversal result only", stdout.String())
	}
}

func TestAnalyzeCommandUsesQueryDefaults(t *testing.T) {
	t.Parallel()

	cmd := newAnalyzeCommand()
	var stdout bytes.Buffer
	cmd.SetOut(&stdout)
	cmd.SetErr(io.Discard)
	cmd.SetArgs([]string{
		t.TempDir(),
		"--language=java",
		"--analyzer-cmd=" + fakeAnalyzerCommand(t, "query-single"),
		"--method=com.example.Service#find(java.lang.Long)",
	})

	if err := cmd.Execute(); err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if !strings.Contains(stdout.String(), "com.example.Service#find(java.lang.Long)") || !strings.Contains(stdout.String(), "com.example.Controller#call()") {
		t.Fatalf("stdout = %q, want default console/caller query output", stdout.String())
	}
	if strings.Contains(stdout.String(), "analyzed ") {
		t.Fatalf("stdout = %q, want no legacy summary during a method query", stdout.String())
	}
}

func TestAnalyzeCommandRejectsInvalidQueryFlagValuesWithExit2(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		flag     string
		wantText []string
	}{
		{name: "direction", flag: "--direction=sideways", wantText: []string{"caller", "callee"}},
		{name: "format", flag: "--format=yaml", wantText: output.RegisteredFormats()},
		{name: "max-depth", flag: "--max-depth=-1", wantText: []string{"max-depth", ">= 0"}},
		{name: "max-depth-type", flag: "--max-depth=abc", wantText: []string{"max-depth", "invalid syntax"}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			cmd := newAnalyzeCommand()
			cmd.SetOut(io.Discard)
			cmd.SetErr(io.Discard)
			cmd.SetArgs([]string{
				t.TempDir(),
				"--language=java",
				"--analyzer-cmd=/nonexistent/analyzer/binary",
				tt.flag,
			})

			err := cmd.Execute()
			if err == nil {
				t.Fatal("Execute() error = nil, want input error")
			}
			if got := ExitCode(err); got != 2 {
				t.Fatalf("ExitCode(error) = %d, want 2", got)
			}
			for _, want := range tt.wantText {
				if !strings.Contains(err.Error(), want) {
					t.Errorf("error = %q, want allowed value %q", err, want)
				}
			}
		})
	}
}

func TestAnalyzeCommandRejectsInvalidFiltersBeforeAnalyzerLaunchWithExit2(t *testing.T) {
	t.Parallel()

	for _, flag := range []string{"--include=../outside/**", "--exclude=/absolute/**"} {
		flag := flag
		t.Run(flag, func(t *testing.T) {
			t.Parallel()
			cmd := newAnalyzeCommand()
			cmd.SetOut(io.Discard)
			cmd.SetErr(io.Discard)
			cmd.SetArgs([]string{
				t.TempDir(),
				"--language=java",
				"--analyzer-cmd=/nonexistent/analyzer/binary",
				flag,
			})

			err := cmd.Execute()
			if err == nil {
				t.Fatal("Execute() error = nil, want request validation error")
			}
			if got := ExitCode(err); got != 2 {
				t.Fatalf("ExitCode(error) = %d, want 2", got)
			}
		})
	}
}

func TestAnalyzeCommandSelectorErrorsUseExit2AndStderr(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		scenario string
		method   string
		wants    []string
	}{
		{
			name: "ambiguous", scenario: "query-overloaded", method: "com.example.Service#find",
			wants: []string{"com.example.Service#find(java.lang.Long)", "com.example.Service#find(java.lang.String)"},
		},
		{name: "not-found", scenario: "query-single", method: "com.example.Missing#run", wants: []string{"did not match any method"}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			cmd := newAnalyzeCommand()
			var stdout, stderr bytes.Buffer
			cmd.SetOut(&stdout)
			cmd.SetErr(&stderr)
			cmd.SetArgs([]string{
				t.TempDir(),
				"--language=java",
				"--analyzer-cmd=" + fakeAnalyzerCommand(t, tt.scenario),
				"--method=" + tt.method,
			})

			err := cmd.Execute()
			if err == nil {
				t.Fatal("Execute() error = nil, want selector input error")
			}
			if got := ExitCode(err); got != 2 {
				t.Fatalf("ExitCode(error) = %d, want 2", got)
			}
			for _, want := range tt.wants {
				if !strings.Contains(stderr.String(), want) {
					t.Errorf("stderr = %q, want %q", stderr.String(), want)
				}
			}
			if stdout.Len() != 0 {
				t.Errorf("stdout = %q, want empty on selector error", stdout.String())
			}
		})
	}
}

func TestAnalyzeCommandRuntimeFailuresUseExit1(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		scenario string
		command  string
	}{
		{name: "launch", command: "/nonexistent/analyzer/binary"},
		{name: "protocol", scenario: "invalid-protocol"},
		{name: "analyzer-fatal", scenario: "analyzer-error"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			command := tt.command
			if command == "" {
				command = fakeAnalyzerCommand(t, tt.scenario)
			}
			cmd := newAnalyzeCommand()
			cmd.SetOut(io.Discard)
			cmd.SetErr(io.Discard)
			cmd.SetArgs([]string{t.TempDir(), "--language=java", "--analyzer-cmd=" + command})

			err := cmd.Execute()
			if err == nil {
				t.Fatal("Execute() error = nil, want runtime error")
			}
			if got := ExitCode(err); got != 1 {
				t.Fatalf("ExitCode(error) = %d, want 1", got)
			}
		})
	}
}

func TestAnalyzeCommandOutputFailureUsesExit1(t *testing.T) {
	t.Parallel()

	want := errors.New("stdout closed")
	cmd := newAnalyzeCommand()
	cmd.SetOut(writerFunc(func([]byte) (int, error) { return 0, want }))
	cmd.SetErr(io.Discard)
	cmd.SetArgs([]string{
		t.TempDir(),
		"--language=java",
		"--analyzer-cmd=" + fakeAnalyzerCommand(t, "query-single"),
		"--method=com.example.Service#find(java.lang.Long)",
	})

	err := cmd.Execute()
	if !errors.Is(err, want) {
		t.Fatalf("Execute() error = %v, want wrapped output error", err)
	}
	if got := ExitCode(err); got != 1 {
		t.Fatalf("ExitCode(error) = %d, want 1", got)
	}
}

func TestAnalyzeCommandNoReachAndCutoffAreSuccessful(t *testing.T) {
	t.Parallel()

	for _, maxDepth := range []string{"", "--max-depth=0"} {
		maxDepth := maxDepth
		t.Run(maxDepth, func(t *testing.T) {
			t.Parallel()
			cmd := newAnalyzeCommand()
			cmd.SetOut(io.Discard)
			cmd.SetErr(io.Discard)
			args := []string{
				t.TempDir(),
				"--language=java",
				"--analyzer-cmd=" + fakeAnalyzerCommand(t, "query-single"),
				"--method=com.example.Service#find(java.lang.Long)",
				"--direction=callee",
			}
			if maxDepth != "" {
				args = append(args, maxDepth)
			}
			cmd.SetArgs(args)
			if err := cmd.Execute(); err != nil {
				t.Fatalf("Execute() error = %v, want success", err)
			}
		})
	}
	if got := ExitCode(nil); got != 0 {
		t.Fatalf("ExitCode(nil) = %d, want 0", got)
	}
}

func TestAnalyzeCommandHelpExplainsDiscoverySideEffectsAndBypass(t *testing.T) {
	t.Parallel()

	long := newAnalyzeCommand().Long
	for _, want := range []string{"build model", "network", "credential", "caches", "bypasses build-model discovery"} {
		if !strings.Contains(long, want) {
			t.Fatalf("Long help = %q, want it to mention %q", long, want)
		}
	}
	if strings.Contains(strings.ToLower(long), "gradle") {
		t.Fatalf("Long help = %q, must stay language-agnostic and not name a build tool", long)
	}
}

func TestAnalyzeCommandForwardsAnalyzerStderrVerbatim(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		scenario string
		wantErr  bool
	}{
		{name: "success", scenario: "stderr-notes-success", wantErr: false},
		{name: "fatal", scenario: "stderr-notes-fatal", wantErr: true},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			cmd := newAnalyzeCommand()
			var stdout, stderr bytes.Buffer
			cmd.SetOut(&stdout)
			cmd.SetErr(&stderr)
			cmd.SetArgs([]string{
				t.TempDir(),
				"--language=java",
				"--analyzer-cmd=" + fakeAnalyzerCommand(t, tt.scenario),
			})

			err := cmd.Execute()
			if (err != nil) != tt.wantErr {
				t.Fatalf("Execute() error = %v, wantErr %v", err, tt.wantErr)
			}
			if got := stderr.String(); !strings.Contains(got, "analyzer-note: discovery phase=start elapsed=1ms") {
				t.Fatalf("stderr = %q, want the analyzer stderr forwarded verbatim", got)
			}
		})
	}
}

func TestAnalyzeCommandRendersStructuredFailureDetails(t *testing.T) {
	t.Parallel()

	cmd := newAnalyzeCommand()
	var stdout, stderr bytes.Buffer
	cmd.SetOut(&stdout)
	cmd.SetErr(&stderr)
	cmd.SetArgs([]string{
		t.TempDir(),
		"--language=java",
		"--analyzer-cmd=" + fakeAnalyzerCommand(t, "error-with-details"),
	})

	if err := cmd.Execute(); err == nil {
		t.Fatal("Execute() error = nil, want error for a fatal analyzer error record")
	}

	got := stderr.String()
	summary := strings.Index(got, "Error: analyzer reported a fatal error: SOME_ANALYZER_CODE: unresolved calls remain")
	first := strings.Index(got, `detail[0] DETAIL_CODE_B: first detail`)
	firstAt := strings.Index(got, "at module-b/src/App.java:12")
	firstMeta := strings.Index(got, `metadata {"candidates":["z","a"],"kind":"virtual"}`)
	second := strings.Index(got, `detail[1] DETAIL_CODE_A: second detail`)
	if summary < 0 || first < 0 || firstAt < 0 || firstMeta < 0 || second < 0 {
		t.Fatalf("stderr = %q, want summary, ordered details, location, and canonical metadata", got)
	}
	if !(summary < first && first < firstAt && firstAt < firstMeta && firstMeta < second) {
		t.Fatalf("stderr = %q, want summary before detail[0] before detail[1]", got)
	}
	if strings.Contains(got, "Usage:") {
		t.Fatalf("stderr = %q, want no usage block after the structured failure render", got)
	}
	if strings.Contains(stdout.String(), "analyzed") {
		t.Fatalf("stdout = %q, want no success summary on fatal failure", stdout.String())
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

	stdin, err := io.ReadAll(os.Stdin)
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
	case "echo-source-roots":
		fmt.Print(diagnosticWithMessageJSONL("sourceRoots=" + requestSourceRootsForHelper(stdin)))
		os.Exit(0)
	case "stderr-notes-success":
		fmt.Fprint(os.Stderr, "analyzer-note: discovery phase=start elapsed=1ms\n")
		fmt.Print(methodSymbolJSONL("method:caller", "example.Caller.run"))
		os.Exit(0)
	case "stderr-notes-fatal":
		fmt.Fprint(os.Stderr, "analyzer-note: discovery phase=start elapsed=1ms\n")
		fmt.Print(`{"schemaVersion":"1","recordType":"error","code":"JAVA_FATAL","message":"boom"}` + "\n")
		os.Exit(1)
	case "error-with-details":
		fmt.Print(methodSymbolJSONL("method:caller", "example.Caller.run") +
			`{"schemaVersion":"1","recordType":"error","code":"SOME_ANALYZER_CODE","message":"unresolved calls remain","details":[` +
			`{"code":"DETAIL_CODE_B","message":"first detail","sourceLocation":{"path":"module-b/src/App.java","startLine":12},"metadata":{"kind":"virtual","candidates":["z","a"]}},` +
			`{"code":"DETAIL_CODE_A","message":"second detail"}]}` + "\n")
		os.Exit(1)
	case "query-single":
		fmt.Print(methodSymbolWithSignatureJSONL("opaque-caller", "com.example.Controller.call", "com.example.Controller#call()") +
			methodSymbolWithSignatureJSONL("opaque-target", "com.example.Service.find", "com.example.Service#find(java.lang.Long)") +
			callEdgeJSONL("opaque-edge", "opaque-caller", "opaque-target"))
		os.Exit(0)
	case "query-with-filters":
		fmt.Print(methodSymbolWithSignatureJSONL("opaque-caller", "com.example.Controller.call", "com.example.Controller#call()") +
			methodSymbolWithSignatureJSONL("opaque-target", "com.example.Service.find", "com.example.Service#find(java.lang.Long)") +
			callEdgeJSONL("opaque-edge", "opaque-caller", "opaque-target") +
			diagnosticWithMessageJSONL("filters="+requestStringArrayForHelper(stdin, "include")+"|"+requestStringArrayForHelper(stdin, "exclude")))
		os.Exit(0)
	case "query-overloaded":
		fmt.Print(methodSymbolWithSignatureJSONL("opaque-long", "com.example.Service.find", "com.example.Service#find(java.lang.Long)") +
			methodSymbolWithSignatureJSONL("opaque-string", "com.example.Service.find", "com.example.Service#find(java.lang.String)"))
		os.Exit(0)
	case "invalid-protocol":
		fmt.Print("not-json\n")
		os.Exit(0)
	default:
		os.Exit(2)
	}
}

// requestSourceRootsForHelper reports the analysisRequest.sourceRoots values
// exactly as received on stdin, or "(absent)" when the field was omitted.
func requestSourceRootsForHelper(stdin []byte) string {
	return requestStringArrayForHelper(stdin, "sourceRoots")
}

func requestStringArrayForHelper(stdin []byte, field string) string {
	var request map[string]any
	if err := json.Unmarshal(bytes.TrimSpace(stdin), &request); err != nil {
		return "(unparseable)"
	}
	value, ok := request[field]
	if !ok {
		return "(absent)"
	}
	roots, ok := value.([]any)
	if !ok {
		return "(malformed)"
	}
	texts := make([]string, 0, len(roots))
	for _, root := range roots {
		texts = append(texts, fmt.Sprint(root))
	}
	return strings.Join(texts, ",")
}

func diagnosticWithMessageJSONL(message string) string {
	return fmt.Sprintf(
		`{"schemaVersion":"1","recordType":"diagnostic","severity":"info","code":"ECHO","message":%q}`+"\n",
		message,
	)
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
