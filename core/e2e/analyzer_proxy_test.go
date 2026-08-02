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

// TestAnalyzerRecordingProxyHelperProcess はテストではない。subprocess として
// 再実行され、テスト専用の透過記録プロキシとして働く。
//
// Core の stdin をそのまま実 Analyzer へ渡し、Analyzer の stdout / stderr /
// exit status を無変換で中継し、検証用の複製を capture directory へ記録するだけ。
// record の合成・並べ替え・再直列化・除外は一切しない。
func TestAnalyzerRecordingProxyHelperProcess(t *testing.T) {
	captureDir, command, malformed := proxyHelperArgs(os.Args)
	if malformed {
		// 引数不足で黙って返すと「出力せず 0 で終了した Analyzer」に見え、空の
		// graph が成功として通ってしまう。声高に失敗させる。
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
// Analyzer の起動失敗・capture 失敗は非ゼロ exit にし、成功へ格下げしない。
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
	// capture には Analyzer が実際に読んだ byte が入る。現行 Protocol は
	// analysisRequest の 1 行を必ず最後まで読むため、要求全体が記録される。
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

// TestProxyEchoChildHelperProcess は透過性の unit test が使う固定の子プロセス。
// stdin を接頭辞付きで stdout へ返し、stderr へ 1 行書き、3 で終了する。
func TestProxyEchoChildHelperProcess(t *testing.T) {
	// シナリオの選択は t.Setenv ではなく起動引数で行う。t.Setenv はプロセス
	// 全体の環境を汚すため。
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
