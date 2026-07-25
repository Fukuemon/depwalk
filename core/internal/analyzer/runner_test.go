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
)

// runScenario runs the helper Analyzer process for one fixture scenario and
// returns the streamed stdout lines with the process result. The payload is
// opaque to analyzer, so tests assert on raw lines, not records.
func runScenario(t *testing.T, scenario string, stderr io.Writer) ([]string, Result) {
	t.Helper()

	scenarioDir := fixturePath(t, scenario)
	runner := New(Command{
		Path:   os.Args[0],
		Args:   []string{"-test.run=TestHelperAnalyzerProcess", "--", "--helper-analyzer", scenarioDir},
		Stderr: stderr,
	})

	input := readFile(t, filepath.Join(scenarioDir, "request.jsonl"))
	var lines []string
	result, err := runner.Run(input, func(line []byte) { lines = append(lines, string(line)) })
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	return lines, result
}

func TestRunnerStreamsStdoutLinesAndReportsProcessStatus(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name      string
		wantLines int
	}{
		{name: "success", wantLines: 3},
		{name: "diagnostic-only", wantLines: 1},
		{name: "error-record", wantLines: 1},
		{name: "non-zero-exit", wantLines: 0},
		{name: "invalid-stdout", wantLines: 1},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			scenarioDir := fixturePath(t, tt.name)
			lines, result := runScenario(t, tt.name, nil)

			wantStdout := string(readFile(t, filepath.Join(scenarioDir, "stdout.jsonl")))
			if got := strings.Join(lines, ""); got != wantStdout {
				t.Fatalf("streamed stdout = %q, want the fixture stdout %q", got, wantStdout)
			}
			if len(lines) != tt.wantLines {
				t.Fatalf("len(lines) = %d, want %d", len(lines), tt.wantLines)
			}
			if wantExitCode := readExitCode(t, scenarioDir); result.ExitCode != wantExitCode {
				t.Fatalf("ExitCode = %d, want %d", result.ExitCode, wantExitCode)
			}
			if result.ReadError != nil {
				t.Fatalf("ReadError = %v, want nil", result.ReadError)
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
	_, result := runScenario(t, "success", &failingWriter{})

	wantStderr := string(readFile(t, filepath.Join(scenarioDir, "stderr.txt")))
	if result.Stderr != wantStderr {
		t.Fatalf("Stderr = %q, want %q", result.Stderr, wantStderr)
	}
}

func TestRunnerRequiresCommandPath(t *testing.T) {
	t.Parallel()

	runner := New(Command{})
	_, err := runner.Run([]byte("{}\n"), nil)
	if err == nil {
		t.Fatal("Run() error = nil, want error")
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
