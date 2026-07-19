package analyzer

import (
	"bytes"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

func TestRunnerScenarios(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name            string
		wantRecords     int
		wantDiagnostics int
		wantAnalyzerErr bool
		wantValidation  bool
	}{
		{name: "success", wantRecords: 3},
		{name: "diagnostic-only", wantDiagnostics: 1},
		{name: "error-record", wantAnalyzerErr: true},
		{name: "non-zero-exit"},
		{name: "invalid-stdout", wantValidation: true},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			scenarioDir := fixturePath(t, tt.name)
			request := readRequest(t, scenarioDir)
			runner := New(Command{
				Path: os.Args[0],
				Args: []string{"-test.run=TestHelperAnalyzerProcess", "--", "--helper-analyzer", scenarioDir},
			})

			var records []protocol.Record
			result, err := runner.Run(request, func(record protocol.Record) { records = append(records, record) })
			if err != nil {
				t.Fatalf("Run() error = %v", err)
			}

			wantExitCode := readExitCode(t, scenarioDir)
			if result.ExitCode != wantExitCode {
				t.Fatalf("ExitCode = %d, want %d", result.ExitCode, wantExitCode)
			}
			if len(records) != tt.wantRecords {
				t.Fatalf("len(records) = %d, want %d", len(records), tt.wantRecords)
			}
			if len(result.Diagnostics) != tt.wantDiagnostics {
				t.Fatalf("len(Diagnostics) = %d, want %d", len(result.Diagnostics), tt.wantDiagnostics)
			}
			if gotAnalyzerErr := result.AnalyzerError != nil; gotAnalyzerErr != tt.wantAnalyzerErr {
				t.Fatalf("AnalyzerError present = %v, want %v", gotAnalyzerErr, tt.wantAnalyzerErr)
			}
			if gotValidation := result.ValidationError != nil; gotValidation != tt.wantValidation {
				t.Fatalf("ValidationError present = %v, want %v", gotValidation, tt.wantValidation)
			}
			wantStderr := string(readFile(t, filepath.Join(scenarioDir, "stderr.txt")))
			if result.Stderr != wantStderr {
				t.Fatalf("Stderr = %q, want %q", result.Stderr, wantStderr)
			}
		})
	}
}

type failingWriter struct{}

func (w *failingWriter) Write(p []byte) (int, error) {
	return 0, fmt.Errorf("stderr forwarding failed")
}

// 転送先 writer が失敗しても stderr は EOF まで drain され、capture が欠けない。
func TestRunnerDrainsStderrWhenForwardWriterFails(t *testing.T) {
	t.Parallel()

	scenarioDir := fixturePath(t, "success")
	request := readRequest(t, scenarioDir)
	runner := New(Command{
		Path:   os.Args[0],
		Args:   []string{"-test.run=TestHelperAnalyzerProcess", "--", "--helper-analyzer", scenarioDir},
		Stderr: &failingWriter{},
	})

	result, err := runner.Run(request, func(protocol.Record) {})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	wantStderr := string(readFile(t, filepath.Join(scenarioDir, "stderr.txt")))
	if result.Stderr != wantStderr {
		t.Fatalf("Stderr = %q, want %q", result.Stderr, wantStderr)
	}
}

func TestRunnerRejectsInvalidRequest(t *testing.T) {
	t.Parallel()

	runner := New(Command{Path: os.Args[0]})
	_, err := runner.Run(protocol.AnalysisRequest{}, nil)
	if err == nil {
		t.Fatal("Run() error = nil, want error")
	}
}

func TestParseStdoutRejectsBlankJSONLLines(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name   string
		stdout string
	}{
		{name: "empty JSONL record", stdout: "\n"},
		{name: "whitespace-only JSONL record", stdout: "  \t\n"},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			assertStdoutValidationError(t, tt.stdout, 0)
		})
	}
}

func TestParseStdoutAcceptsOnlyAnalyzerResponseRecords(t *testing.T) {
	t.Parallel()

	stdout := `{"schemaVersion":"1","recordType":"analysisRequest","requestId":"request-1","workspaceRoot":"/workspace","language":"java"}` + "\n"

	assertStdoutValidationError(t, stdout, 0)
}

func TestParseStdoutValidatesCallEdgesReferenceMethodSymbols(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name   string
		stdout string
	}{
		{
			name:   "callerMethodId must reference an emitted methodSymbol",
			stdout: methodSymbolJSONL("method:callee", "example.Callee.run") + callEdgeJSONL("edge:1", "method:missing", "method:callee"),
		},
		{
			name:   "calleeMethodId must reference an emitted methodSymbol",
			stdout: methodSymbolJSONL("method:caller", "example.Caller.run") + callEdgeJSONL("edge:1", "method:caller", "method:missing"),
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			assertStdoutValidationError(t, tt.stdout, 2)
		})
	}
}

func TestParseStdoutValidatesCallEdgesAfterFullStream(t *testing.T) {
	t.Parallel()

	stdout := callEdgeJSONL("edge:1", "method:caller", "method:callee") +
		methodSymbolJSONL("method:caller", "example.Caller.run") +
		methodSymbolJSONL("method:callee", "example.Callee.run")

	var records []protocol.Record
	result := parseStdout(strings.NewReader(stdout), func(record protocol.Record) { records = append(records, record) })
	if result.ValidationError != nil {
		t.Fatalf("ValidationError = %v, want nil", result.ValidationError)
	}
	if len(records) != 3 {
		t.Fatalf("len(records) = %d, want 3", len(records))
	}
}

func TestParseStdoutSkipsReferenceValidationOnFatalStream(t *testing.T) {
	t.Parallel()

	stdout := callEdgeJSONL("edge:1", "method:caller", "method:missing") +
		`{"schemaVersion":"1","recordType":"error","code":"JAVA_FATAL","message":"fatal"}` + "\n"

	result := parseStdout(strings.NewReader(stdout), nil)
	if result.AnalyzerError == nil {
		t.Fatal("AnalyzerError = nil, want fatal error record")
	}
	if result.ValidationError != nil {
		t.Fatalf("ValidationError = %v, want nil (fatal stream discards prior records)", result.ValidationError)
	}
}

func assertStdoutValidationError(t *testing.T, stdout string, wantRecords int) {
	t.Helper()

	var records []protocol.Record
	result := parseStdout(strings.NewReader(stdout), func(record protocol.Record) { records = append(records, record) })
	if result.ValidationError == nil {
		t.Fatal("ValidationError = nil, want error")
	}
	if len(records) != wantRecords {
		t.Fatalf("len(records) = %d, want %d", len(records), wantRecords)
	}
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

func TestHelperAnalyzerProcess(t *testing.T) {
	args := os.Args
	for i, arg := range args {
		if arg != "--helper-analyzer" {
			continue
		}
		if i+1 >= len(args) {
			os.Exit(2)
		}
		runHelperAnalyzer(args[i+1])
		return
	}
}

func runHelperAnalyzer(scenarioDir string) {
	stdin, err := os.ReadFile(filepath.Join(scenarioDir, "request.jsonl"))
	if err != nil {
		_, _ = os.Stderr.WriteString(err.Error())
		os.Exit(2)
	}
	got, err := io.ReadAll(os.Stdin)
	if err != nil {
		_, _ = os.Stderr.WriteString(err.Error())
		os.Exit(2)
	}
	if !bytes.Equal(bytes.TrimSpace(got), bytes.TrimSpace(stdin)) {
		_, _ = os.Stderr.WriteString("unexpected stdin")
		os.Exit(2)
	}

	_, _ = os.Stdout.Write(readFileForHelper(filepath.Join(scenarioDir, "stdout.jsonl")))
	_, _ = os.Stderr.Write(readFileForHelper(filepath.Join(scenarioDir, "stderr.txt")))
	os.Exit(readExitCodeForHelper(filepath.Join(scenarioDir, "exit-code.txt")))
}

func fixturePath(t *testing.T, scenario string) string {
	t.Helper()

	return filepath.Join("..", "..", "..", "testdata", "analyzer-protocol", "scenarios", scenario)
}

func readRequest(t *testing.T, scenarioDir string) protocol.AnalysisRequest {
	t.Helper()

	record, err := protocol.ParseRecord(readFile(t, filepath.Join(scenarioDir, "request.jsonl")))
	if err != nil {
		t.Fatalf("request fixture error = %v", err)
	}
	request, ok := record.(protocol.AnalysisRequest)
	if !ok {
		t.Fatalf("request fixture type = %T, want protocol.AnalysisRequest", record)
	}
	return request
}

func readExitCode(t *testing.T, scenarioDir string) int {
	t.Helper()

	exitCode, err := strconv.Atoi(strings.TrimSpace(string(readFile(t, filepath.Join(scenarioDir, "exit-code.txt")))))
	if err != nil {
		t.Fatalf("exit-code fixture error = %v", err)
	}
	return exitCode
}

func readFile(t *testing.T, file string) []byte {
	t.Helper()

	content, err := os.ReadFile(file)
	if err != nil {
		t.Fatalf("ReadFile(%s) error = %v", file, err)
	}
	return content
}

func readFileForHelper(file string) []byte {
	content, err := os.ReadFile(file)
	if err != nil {
		_, _ = os.Stderr.WriteString(err.Error())
		os.Exit(2)
	}
	return content
}

func readExitCodeForHelper(file string) int {
	exitCode, err := strconv.Atoi(strings.TrimSpace(string(readFileForHelper(file))))
	if err != nil {
		_, _ = os.Stderr.WriteString(err.Error())
		os.Exit(2)
	}
	return exitCode
}
