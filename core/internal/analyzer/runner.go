package analyzer

import (
	"bufio"
	"bytes"
	"errors"
	"fmt"
	"io"
	"os/exec"
)

// Command describes the Analyzer process invocation.
type Command struct {
	// Path is the executable path.
	Path string
	// Args are command-line arguments passed to Path.
	Args []string
	// Dir is the optional working directory.
	Dir string
	// Stderr optionally receives the Analyzer stderr stream as it arrives,
	// without interpretation. Core never parses Analyzer stderr as protocol
	// data. When nil, stderr is only captured into [Result.Stderr].
	Stderr io.Writer
}

// Runner starts one Analyzer process for one analysis request.
type Runner struct {
	command Command
}

// New returns a Runner for command.
func New(command Command) Runner {
	return Runner{command: command}
}

// Result contains process status returned by an Analyzer process. Stdout
// content is not buffered here; it is handed to the onLine callback of
// [Runner.Run] one line at a time, as it arrives.
type Result struct {
	// ExitCode is the Analyzer process exit code.
	ExitCode int
	// Stderr is the raw Analyzer stderr output.
	Stderr string
	// ReadError is the first stdout read failure, if any. Lines received
	// before the failure were already handed to onLine; reading stops at
	// the failure.
	ReadError error
}

// Run starts an Analyzer process, writes input to its stdin, and streams
// stdout to onLine one line at a time (delimiter included; a trailing
// unterminated line is delivered as-is) until EOF. onLine may be nil when
// the caller does not consume stdout. The payload is opaque to this
// package: composing the request line and interpreting stdout lines are
// the protocol package's responsibility.
func (r Runner) Run(input []byte, onLine func(line []byte)) (Result, error) {
	var result Result
	if r.command.Path == "" {
		return result, errors.New("analyzer command path is required")
	}

	cmd := exec.Command(r.command.Path, r.command.Args...)
	cmd.Dir = r.command.Dir

	stdin, err := cmd.StdinPipe()
	if err != nil {
		return result, err
	}
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return result, err
	}
	stderr, err := cmd.StderrPipe()
	if err != nil {
		return result, err
	}

	if err := cmd.Start(); err != nil {
		return result, err
	}

	stderrDone := make(chan []byte, 1)
	go func() {
		var buf bytes.Buffer
		source := io.Reader(stderr)
		if r.command.Stderr != nil {
			source = io.TeeReader(stderr, r.command.Stderr)
		}
		if _, err := buf.ReadFrom(source); err != nil {
			// 転送先 writer の失敗で drain を止めると pipe が詰まり Analyzer が
			// 終了できなくなるため、以降は転送せず EOF まで読み切る。
			_, _ = buf.ReadFrom(stderr)
		}
		stderrDone <- buf.Bytes()
	}()

	// finish drains stderr to EOF before calling Wait so the pipe is fully
	// read when the process handle is released (os/exec requires reads to
	// complete before Wait) and no trailing stderr output is lost.
	finish := func() error {
		result.Stderr = string(<-stderrDone)
		waitErr := cmd.Wait()
		result.ExitCode = exitCode(waitErr)
		return waitErr
	}

	if _, err := stdin.Write(input); err != nil {
		_ = stdin.Close()
		result.ReadError = readStdout(stdout, onLine)
		_ = finish()
		return result, err
	}
	if err := stdin.Close(); err != nil {
		result.ReadError = readStdout(stdout, onLine)
		_ = finish()
		return result, err
	}

	result.ReadError = readStdout(stdout, onLine)
	waitErr := finish()

	if waitErr != nil && result.ExitCode == 0 {
		return result, waitErr
	}
	return result, nil
}

// readStdout streams stdout to onLine one line at a time and returns the
// first non-EOF read failure, at which point reading stops.
func readStdout(stdout io.Reader, onLine func(line []byte)) error {
	reader := bufio.NewReader(stdout)
	for {
		line, err := reader.ReadBytes('\n')
		if len(line) > 0 && onLine != nil {
			onLine(line)
		}
		if err == nil {
			continue
		}
		if errors.Is(err, io.EOF) {
			return nil
		}
		return fmt.Errorf("read analyzer stdout: %w", err)
	}
}

func exitCode(err error) int {
	if err == nil {
		return 0
	}
	var exitErr *exec.ExitError
	if errors.As(err, &exitErr) {
		return exitErr.ExitCode()
	}
	return 0
}
