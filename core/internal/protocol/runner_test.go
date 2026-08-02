package protocol

import (
	"bufio"
	"bytes"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/analyzer"
)

// record 単位の Runner テスト。analyzer package は opaque な行を流すだけで、
// record の parse と参照完全性は ACL である本 package が持つ。

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

			scenarioDir := scenarioFixturePath(tt.name)
			request := readRequest(t, scenarioDir)
			runner := NewRunner(analyzer.Command{
				Path: os.Args[0],
				Args: []string{"-test.run=TestProtocolHelperAnalyzerProcess", "--", "--protocol-helper-analyzer", scenarioDir},
			})

			var records []Record
			result, err := runner.Run(request, func(record Record) { records = append(records, record) })
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
			wantStderr := string(readScenarioFile(t, filepath.Join(scenarioDir, "stderr.txt")))
			if result.Stderr != wantStderr {
				t.Fatalf("Stderr = %q, want %q", result.Stderr, wantStderr)
			}
		})
	}
}

func TestRunnerRejectsInvalidRequest(t *testing.T) {
	t.Parallel()

	runner := NewRunner(analyzer.Command{Path: os.Args[0]})
	_, err := runner.Run(AnalysisRequest{}, nil)
	if err == nil {
		t.Fatal("Run() error = nil, want error")
	}
}

// collectStdout は stdout を 1 行ずつ record collector へ渡す。[Runner.Run] が
// analyzer package から行を受け取る流れをそのまま模す。
func collectStdout(stdout string, onRecord func(Record)) RunResult {
	collector := newRecordCollector(onRecord)
	reader := bufio.NewReader(strings.NewReader(stdout))
	for {
		line, err := reader.ReadBytes('\n')
		if len(line) > 0 {
			collector.addLine(line)
		}
		if err != nil {
			break
		}
	}
	return collector.finalize(nil)
}

func TestCollectorRejectsBlankJSONLLines(t *testing.T) {
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

func TestCollectorAcceptsOnlyAnalyzerResponseRecords(t *testing.T) {
	t.Parallel()

	stdout := `{"schemaVersion":"1","recordType":"analysisRequest","requestId":"request-1","workspaceRoot":"/workspace","language":"java"}` + "\n"

	assertStdoutValidationError(t, stdout, 0)
}

func TestCollectorValidatesCallEdgesReferenceMethodSymbols(t *testing.T) {
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

func TestCollectorValidatesCallEdgesAfterFullStream(t *testing.T) {
	t.Parallel()

	stdout := callEdgeJSONL("edge:1", "method:caller", "method:callee") +
		methodSymbolJSONL("method:caller", "example.Caller.run") +
		methodSymbolJSONL("method:callee", "example.Callee.run")

	var records []Record
	result := collectStdout(stdout, func(record Record) { records = append(records, record) })
	if result.ValidationError != nil {
		t.Fatalf("ValidationError = %v, want nil", result.ValidationError)
	}
	if len(records) != 3 {
		t.Fatalf("len(records) = %d, want 3", len(records))
	}
}

func TestCollectorSkipsReferenceValidationOnFatalStream(t *testing.T) {
	t.Parallel()

	stdout := callEdgeJSONL("edge:1", "method:caller", "method:missing") +
		`{"schemaVersion":"1","recordType":"error","code":"JAVA_FATAL","message":"fatal"}` + "\n"

	result := collectStdout(stdout, nil)
	if result.AnalyzerError == nil {
		t.Fatal("AnalyzerError = nil, want fatal error record")
	}
	if result.ValidationError != nil {
		t.Fatalf("ValidationError = %v, want nil (fatal stream discards prior records)", result.ValidationError)
	}
}

func TestCollectorReportsReadErrorAsValidationError(t *testing.T) {
	t.Parallel()

	// stdout の読み取り失敗は検証エラーとして現れる。ただし先に parse エラーが
	// 記録されていればそちらが優先される。
	readErr := fmt.Errorf("read analyzer stdout: connection reset")
	result := newRecordCollector(nil).finalize(readErr)
	if result.ValidationError == nil || !strings.Contains(result.ValidationError.Error(), "read analyzer stdout") {
		t.Fatalf("ValidationError = %v, want the read error", result.ValidationError)
	}

	collector := newRecordCollector(nil)
	collector.addLine([]byte("not-json\n"))
	result = collector.finalize(readErr)
	if result.ValidationError == nil || strings.Contains(result.ValidationError.Error(), "read analyzer stdout") {
		t.Fatalf("ValidationError = %v, want the earlier parse error to win", result.ValidationError)
	}
}

func assertStdoutValidationError(t *testing.T, stdout string, wantRecords int) {
	t.Helper()

	var records []Record
	result := collectStdout(stdout, func(record Record) { records = append(records, record) })
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

// TestProtocolHelperAnalyzerProcess はテストではない。TestRunnerScenarios の
// ために、fixture のシナリオを 1 つ fake Analyzer プロセスとして再生する。
func TestProtocolHelperAnalyzerProcess(t *testing.T) {
	args := os.Args
	for i, arg := range args {
		if arg != "--protocol-helper-analyzer" {
			continue
		}
		if i+1 >= len(args) {
			os.Exit(2)
		}
		replayScenario(args[i+1])
		return
	}
}

func replayScenario(scenarioDir string) {
	want := readScenarioFileForHelper(filepath.Join(scenarioDir, "request.jsonl"))
	got, err := io.ReadAll(os.Stdin)
	if err != nil {
		_, _ = os.Stderr.WriteString(err.Error())
		os.Exit(2)
	}
	if !bytes.Equal(bytes.TrimSpace(got), bytes.TrimSpace(want)) {
		_, _ = os.Stderr.WriteString("unexpected stdin")
		os.Exit(2)
	}

	_, _ = os.Stdout.Write(readScenarioFileForHelper(filepath.Join(scenarioDir, "stdout.jsonl")))
	_, _ = os.Stderr.Write(readScenarioFileForHelper(filepath.Join(scenarioDir, "stderr.txt")))
	exitCode, err := strconv.Atoi(strings.TrimSpace(string(readScenarioFileForHelper(filepath.Join(scenarioDir, "exit-code.txt")))))
	if err != nil {
		_, _ = os.Stderr.WriteString(err.Error())
		os.Exit(2)
	}
	os.Exit(exitCode)
}

func scenarioFixturePath(scenario string) string {
	return filepath.Join("..", "..", "..", "testdata", "analyzer-protocol", "scenarios", scenario)
}

func readRequest(t *testing.T, scenarioDir string) AnalysisRequest {
	t.Helper()

	record, err := ParseRecord(readScenarioFile(t, filepath.Join(scenarioDir, "request.jsonl")))
	if err != nil {
		t.Fatalf("request fixture error = %v", err)
	}
	request, ok := record.(AnalysisRequest)
	if !ok {
		t.Fatalf("request fixture type = %T, want AnalysisRequest", record)
	}
	return request
}

func readExitCode(t *testing.T, scenarioDir string) int {
	t.Helper()

	exitCode, err := strconv.Atoi(strings.TrimSpace(string(readScenarioFile(t, filepath.Join(scenarioDir, "exit-code.txt")))))
	if err != nil {
		t.Fatalf("exit-code fixture error = %v", err)
	}
	return exitCode
}

func readScenarioFile(t *testing.T, file string) []byte {
	t.Helper()

	content, err := os.ReadFile(file)
	if err != nil {
		t.Fatalf("ReadFile(%s) error = %v", file, err)
	}
	return content
}

func readScenarioFileForHelper(file string) []byte {
	content, err := os.ReadFile(file)
	if err != nil {
		_, _ = os.Stderr.WriteString(err.Error())
		os.Exit(2)
	}
	return content
}
