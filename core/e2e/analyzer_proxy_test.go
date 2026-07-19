package e2e

import (
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
)

// TestAnalyzerRecordingProxyHelperProcess is not a real test. It is re-executed
// as a subprocess and acts as the test-only transparent recording proxy
// (spec #24 P6): it forwards Core's stdin bytes to the real Analyzer, relays
// the Analyzer's stdout / stderr / exit status without transformation, and
// only records verification copies into the given capture directory. It never
// synthesizes, reorders, re-serializes, or filters records.
func TestAnalyzerRecordingProxyHelperProcess(t *testing.T) {
	captureDir, command, malformed := proxyHelperArgs(os.Args)
	if malformed {
		// 引数不足のまま無言で return すると「空出力 + exit 0 の Analyzer」に
		// 見え、空 graph の偽成功になるため非ゼロで落とす。
		fmt.Fprintln(os.Stderr, "recording proxy: --proxy-capture requires <dir> <command...>")
		os.Exit(97)
	}
	if captureDir == "" {
		return
	}
	os.Exit(runRecordingProxy(os.Stdin, os.Stdout, os.Stderr, captureDir, command))
}

// proxyHelperArgs extracts "--proxy-capture <dir> <command...>" after the
// test-binary "--" separator; returns ("", nil, false) when not invoked as a
// helper and malformed=true when the flag is present without <dir> <command>.
func proxyHelperArgs(args []string) (string, []string, bool) {
	for i, arg := range args {
		if arg == "--proxy-capture" {
			if i+2 < len(args) {
				return args[i+1], args[i+2:], false
			}
			return "", nil, true
		}
	}
	return "", nil, false
}

// runRecordingProxy launches the real Analyzer command and relays all four
// channels byte-transparently while teeing copies into captureDir. A proxy or
// Analyzer startup / capture failure exits non-zero and is never downgraded
// to success.
func runRecordingProxy(stdin io.Reader, stdout, stderr io.Writer, captureDir string, command []string) int {
	if len(command) == 0 {
		fmt.Fprintln(stderr, "recording proxy: analyzer command is required")
		return 97
	}
	requestFile, err := os.Create(filepath.Join(captureDir, "request.jsonl"))
	if err != nil {
		fmt.Fprintf(stderr, "recording proxy: create capture: %v\n", err)
		return 97
	}
	defer requestFile.Close()
	stdoutFile, err := os.Create(filepath.Join(captureDir, "stdout.jsonl"))
	if err != nil {
		fmt.Fprintf(stderr, "recording proxy: create capture: %v\n", err)
		return 97
	}
	defer stdoutFile.Close()
	stderrFile, err := os.Create(filepath.Join(captureDir, "stderr.txt"))
	if err != nil {
		fmt.Fprintf(stderr, "recording proxy: create capture: %v\n", err)
		return 97
	}
	defer stderrFile.Close()

	cmd := exec.Command(command[0], command[1:]...)
	// capture へ写るのは Analyzer が実際に読んだ bytes。現行 Protocol は
	// 1 行の analysisRequest を必ず読み切るため request 全体が写る。
	cmd.Stdin = io.TeeReader(stdin, requestFile)
	cmd.Stdout = io.MultiWriter(stdout, stdoutFile)
	cmd.Stderr = io.MultiWriter(stderr, stderrFile)

	runErr := cmd.Run()
	exit := 0
	if runErr != nil {
		var exitErr *exec.ExitError
		if errors.As(runErr, &exitErr) {
			exit = exitErr.ExitCode()
		} else {
			fmt.Fprintf(stderr, "recording proxy: failed to run analyzer: %v\n", runErr)
			return 97
		}
	}
	if err := os.WriteFile(filepath.Join(captureDir, "exit-code.txt"),
		[]byte(fmt.Sprintf("%d\n", exit)), 0o644); err != nil {
		fmt.Fprintf(stderr, "recording proxy: write capture: %v\n", err)
		return 97
	}
	return exit
}

// TestProxyEchoChildHelperProcess is a fixed child used by the transparency
// unit test: echoes stdin to stdout with a prefix line, writes one stderr
// line, and exits 3.
func TestProxyEchoChildHelperProcess(t *testing.T) {
	// process 全体の env を汚す t.Setenv でなく、起動引数でシナリオを指定する。
	if !hasArg(os.Args, "--proxy-echo-child") {
		return
	}
	content, _ := io.ReadAll(os.Stdin)
	fmt.Fprintf(os.Stdout, "child-stdout:%s", content)
	fmt.Fprint(os.Stderr, "child-stderr-line\n")
	os.Exit(3)
}

func hasArg(args []string, want string) bool {
	for _, arg := range args {
		if arg == want {
			return true
		}
	}
	return false
}

func TestRecordingProxyRelaysAllChannelsByteTransparently(t *testing.T) {
	t.Parallel()

	captureDir := t.TempDir()
	var stdout, stderr strings.Builder

	exit := runRecordingProxy(
		strings.NewReader("payload-bytes\n"),
		&stdout,
		&stderr,
		captureDir,
		[]string{os.Args[0], "-test.run=TestProxyEchoChildHelperProcess", "--", "--proxy-echo-child"},
	)

	if exit != 3 {
		t.Fatalf("exit = %d, want the child's exit status 3", exit)
	}
	if got := stdout.String(); got != "child-stdout:payload-bytes\n" {
		t.Fatalf("stdout = %q, want the child's bytes unmodified", got)
	}
	if got := stderr.String(); !strings.Contains(got, "child-stderr-line") {
		t.Fatalf("stderr = %q, want the child's stderr relayed", got)
	}
	for file, want := range map[string]string{
		"request.jsonl": "payload-bytes\n",
		"stdout.jsonl":  "child-stdout:payload-bytes\n",
		"exit-code.txt": "3\n",
	} {
		content, err := os.ReadFile(filepath.Join(captureDir, file))
		if err != nil {
			t.Fatalf("capture %s missing: %v", file, err)
		}
		if string(content) != want {
			t.Fatalf("capture %s = %q, want %q", file, content, want)
		}
	}
}

func TestRecordingProxyFailsNonZeroOnStartupFailure(t *testing.T) {
	t.Parallel()

	var stderr strings.Builder
	exit := runRecordingProxy(
		strings.NewReader(""), io.Discard, &stderr, t.TempDir(),
		[]string{"/nonexistent/analyzer/binary"})
	if exit == 0 {
		t.Fatalf("exit = 0, want non-zero for analyzer startup failure; stderr=%s", stderr.String())
	}
}
