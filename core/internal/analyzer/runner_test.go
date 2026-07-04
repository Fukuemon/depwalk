package analyzer

import (
	"bytes"
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
		{name: "diagnostic-only", wantRecords: 1, wantDiagnostics: 1},
		{name: "error-record", wantRecords: 1, wantAnalyzerErr: true},
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

			result, err := runner.Run(request)
			if err != nil {
				t.Fatalf("Run() error = %v", err)
			}

			wantExitCode := readExitCode(t, scenarioDir)
			if result.ExitCode != wantExitCode {
				t.Fatalf("ExitCode = %d, want %d", result.ExitCode, wantExitCode)
			}
			if len(result.Records) != tt.wantRecords {
				t.Fatalf("len(Records) = %d, want %d", len(result.Records), tt.wantRecords)
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

func TestRunnerRejectsInvalidRequest(t *testing.T) {
	t.Parallel()

	runner := New(Command{Path: os.Args[0]})
	_, err := runner.Run(protocol.AnalysisRequest{})
	if err == nil {
		t.Fatal("Run() error = nil, want error")
	}
}

func TestParseStdoutValidation(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name        string
		stdout      string
		wantRecords int
	}{
		{
			name:   "blank line",
			stdout: "\n",
		},
		{
			name:   "core request record",
			stdout: `{"schemaVersion":"1","recordType":"analysisRequest","requestId":"request-1","workspaceRoot":"/workspace","language":"java"}` + "\n",
		},
		{
			name:        "unknown caller method id",
			stdout:      `{"schemaVersion":"1","recordType":"methodSymbol","methodId":"method:callee","language":"java","symbolKind":"method","qualifiedName":"example.Callee.run","signature":"run():void"}` + "\n" + `{"schemaVersion":"1","recordType":"callEdge","edgeId":"edge:1","callerMethodId":"method:missing","calleeMethodId":"method:callee"}` + "\n",
			wantRecords: 2,
		},
		{
			name:        "unknown callee method id",
			stdout:      `{"schemaVersion":"1","recordType":"methodSymbol","methodId":"method:caller","language":"java","symbolKind":"method","qualifiedName":"example.Caller.run","signature":"run():void"}` + "\n" + `{"schemaVersion":"1","recordType":"callEdge","edgeId":"edge:1","callerMethodId":"method:caller","calleeMethodId":"method:missing"}` + "\n",
			wantRecords: 2,
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			result := parseStdout(strings.NewReader(tt.stdout))
			if result.ValidationError == nil {
				t.Fatal("ValidationError = nil, want error")
			}
			if len(result.Records) != tt.wantRecords {
				t.Fatalf("len(Records) = %d, want %d", len(result.Records), tt.wantRecords)
			}
		})
	}
}

func TestParseStdoutAllowsEdgesAfterSymbols(t *testing.T) {
	t.Parallel()

	stdout := `{"schemaVersion":"1","recordType":"callEdge","edgeId":"edge:1","callerMethodId":"method:caller","calleeMethodId":"method:callee"}` + "\n" +
		`{"schemaVersion":"1","recordType":"methodSymbol","methodId":"method:caller","language":"java","symbolKind":"method","qualifiedName":"example.Caller.run","signature":"run():void"}` + "\n" +
		`{"schemaVersion":"1","recordType":"methodSymbol","methodId":"method:callee","language":"java","symbolKind":"method","qualifiedName":"example.Callee.run","signature":"run():void"}` + "\n"

	result := parseStdout(strings.NewReader(stdout))
	if result.ValidationError != nil {
		t.Fatalf("ValidationError = %v, want nil", result.ValidationError)
	}
	if len(result.Records) != 3 {
		t.Fatalf("len(Records) = %d, want 3", len(result.Records))
	}
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
