package analyzer

import (
	"bufio"
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

	finish := func(waitErr error) {
		result.Stderr = string(<-stderrDone)
		result.ExitCode = exitCode(waitErr)
	}

	if _, err := stdin.Write(requestLine); err != nil {
		_ = stdin.Close()
		result = parseStdout(stdout)
		finish(cmd.Wait())
		return result, err
	}
	if err := stdin.Close(); err != nil {
		result = parseStdout(stdout)
		finish(cmd.Wait())
		return result, err
	}

	result = parseStdout(stdout)
	waitErr := cmd.Wait()
	finish(waitErr)

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
		if len(line) > 0 {
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
	result.validateRecordReferences()
	return result
}

func (r *Result) addRecord(record protocol.Record) {
	if !isAnalyzerRecord(record) {
		r.setValidationError(fmt.Errorf("analyzer stdout record type %T is not allowed", record))
		return
	}
	r.Records = append(r.Records, record)
	switch typed := record.(type) {
	case protocol.Diagnostic:
		r.Diagnostics = append(r.Diagnostics, typed)
	case protocol.AnalyzerError:
		errRecord := typed
		r.AnalyzerError = &errRecord
	}
}

func isAnalyzerRecord(record protocol.Record) bool {
	switch record.(type) {
	case protocol.MethodSymbol, protocol.CallEdge, protocol.Diagnostic, protocol.AnalyzerError:
		return true
	default:
		return false
	}
}

func (r *Result) validateRecordReferences() {
	methodIDs := map[string]struct{}{}
	var callEdges []protocol.CallEdge
	for _, record := range r.Records {
		switch typed := record.(type) {
		case protocol.MethodSymbol:
			methodIDs[typed.MethodID] = struct{}{}
		case protocol.CallEdge:
			callEdges = append(callEdges, typed)
		}
	}
	for _, edge := range callEdges {
		if _, ok := methodIDs[edge.CallerMethodID]; !ok {
			r.setValidationError(fmt.Errorf("callEdge %q references unknown callerMethodId %q", edge.EdgeID, edge.CallerMethodID))
			return
		}
		if _, ok := methodIDs[edge.CalleeMethodID]; !ok {
			r.setValidationError(fmt.Errorf("callEdge %q references unknown calleeMethodId %q", edge.EdgeID, edge.CalleeMethodID))
			return
		}
	}
}

func (r *Result) setValidationError(err error) {
	if r.ValidationError == nil {
		r.ValidationError = err
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
