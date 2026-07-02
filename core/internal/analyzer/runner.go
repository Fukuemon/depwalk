package analyzer

import (
	"bufio"
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os/exec"

	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

// Command describes the Analyzer process invocation.
type Command struct {
	// Path is the executable path.
	Path string
	// Args are command-line arguments passed to Path.
	Args []string
	// Dir is the optional working directory.
	Dir string
}

// Runner starts one Analyzer process for one analysis request.
type Runner struct {
	command Command
}

// New returns a Runner for command.
func New(command Command) Runner {
	return Runner{command: command}
}

// Result contains records and process status returned by an Analyzer process.
type Result struct {
	// Records contains valid protocol records parsed from stdout.
	Records []protocol.Record
	// Diagnostics contains diagnostic records parsed from stdout.
	Diagnostics []protocol.Diagnostic
	// AnalyzerError contains the fatal Analyzer error record, if one was emitted.
	AnalyzerError *protocol.AnalyzerError
	// ValidationError contains the first Core-side stdout validation error.
	ValidationError error
	// ExitCode is the Analyzer process exit code.
	ExitCode int
	// Stderr is the raw Analyzer stderr output.
	Stderr string
}

// Run starts an Analyzer process, sends one analysisRequest JSONL record, and
// parses stdout records until the process exits.
func (r Runner) Run(request protocol.AnalysisRequest) (Result, error) {
	var result Result
	if r.command.Path == "" {
		return result, errors.New("analyzer command path is required")
	}
	if err := request.Validate(); err != nil {
		return result, err
	}

	requestLine, err := json.Marshal(request)
	if err != nil {
		return result, err
	}
	requestLine = append(requestLine, '\n')

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
		content, _ := io.ReadAll(stderr)
		stderrDone <- content
	}()

	if _, err := stdin.Write(requestLine); err != nil {
		_ = stdin.Close()
		_ = cmd.Wait()
		return result, err
	}
	if err := stdin.Close(); err != nil {
		_ = cmd.Wait()
		return result, err
	}

	result = parseStdout(stdout)
	waitErr := cmd.Wait()
	result.Stderr = string(<-stderrDone)
	result.ExitCode = exitCode(waitErr)

	if waitErr != nil && result.ExitCode == 0 {
		return result, waitErr
	}
	return result, nil
}

func parseStdout(stdout io.Reader) Result {
	var result Result
	reader := bufio.NewReader(stdout)
	for {
		line, err := reader.ReadBytes('\n')
		if len(bytes.TrimSpace(line)) > 0 {
			record, parseErr := protocol.ParseRecord(line)
			if parseErr != nil && result.ValidationError == nil {
				result.ValidationError = parseErr
			}
			if parseErr == nil {
				result.addRecord(record)
			}
		}
		if err == nil {
			continue
		}
		if errors.Is(err, io.EOF) {
			break
		}
		if result.ValidationError == nil {
			result.ValidationError = fmt.Errorf("read analyzer stdout: %w", err)
		}
		break
	}
	return result
}

func (r *Result) addRecord(record protocol.Record) {
	r.Records = append(r.Records, record)
	switch typed := record.(type) {
	case protocol.Diagnostic:
		r.Diagnostics = append(r.Diagnostics, typed)
	case protocol.AnalyzerError:
		errRecord := typed
		r.AnalyzerError = &errRecord
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
