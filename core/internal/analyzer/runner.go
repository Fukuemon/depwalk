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

// Result contains process status returned by an Analyzer process. Method
// symbol and call edge records are not buffered here; they are handed to
// the onRecord callback of [Runner.Run] as they arrive.
type Result struct {
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
// parses stdout records until the process exits. Each valid methodSymbol and
// callEdge record is passed to onRecord as it is received; onRecord may be nil
// when the caller does not consume graph records.
func (r Runner) Run(request protocol.AnalysisRequest, onRecord func(protocol.Record)) (Result, error) {
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
		source := io.Reader(stderr)
		if r.command.Stderr != nil {
			source = io.TeeReader(stderr, r.command.Stderr)
		}
		content, _ := io.ReadAll(source)
		stderrDone <- content
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

	if _, err := stdin.Write(requestLine); err != nil {
		_ = stdin.Close()
		result = parseStdout(stdout, onRecord)
		_ = finish()
		return result, err
	}
	if err := stdin.Close(); err != nil {
		result = parseStdout(stdout, onRecord)
		_ = finish()
		return result, err
	}

	result = parseStdout(stdout, onRecord)
	waitErr := finish()

	if waitErr != nil && result.ExitCode == 0 {
		return result, waitErr
	}
	return result, nil
}

// parseStdout consumes the Analyzer stdout stream one JSONL record at a
// time. Graph records are converted downstream via onRecord instead of being
// buffered; only method IDs and edge endpoints are retained for the
// post-stream reference-completeness check.
func parseStdout(stdout io.Reader, onRecord func(protocol.Record)) Result {
	var result Result
	references := newReferenceChecker()
	reader := bufio.NewReader(stdout)
	for {
		line, err := reader.ReadBytes('\n')
		if len(line) > 0 {
			record, parseErr := protocol.ParseRecord(line)
			if parseErr != nil && result.ValidationError == nil {
				result.ValidationError = parseErr
			}
			if parseErr == nil {
				result.addRecord(record, references, onRecord)
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
	if err := references.validate(); err != nil {
		result.setValidationError(err)
	}
	return result
}

func (r *Result) addRecord(record protocol.Record, references *referenceChecker, onRecord func(protocol.Record)) {
	switch typed := record.(type) {
	case protocol.MethodSymbol:
		references.addMethodID(typed.MethodID)
		if onRecord != nil {
			onRecord(record)
		}
	case protocol.CallEdge:
		references.addEdge(typed.EdgeID, typed.CallerMethodID, typed.CalleeMethodID)
		if onRecord != nil {
			onRecord(record)
		}
	case protocol.Diagnostic:
		r.Diagnostics = append(r.Diagnostics, typed)
	case protocol.AnalyzerError:
		errRecord := typed
		r.AnalyzerError = &errRecord
	default:
		r.setValidationError(fmt.Errorf("analyzer stdout record type %T is not allowed", record))
	}
}

// referenceChecker retains only the identifiers needed to verify that every
// call edge references an emitted method symbol once the stream ends.
type referenceChecker struct {
	methodIDs map[string]struct{}
	edges     []edgeReference
}

type edgeReference struct {
	edgeID   string
	callerID string
	calleeID string
}

func newReferenceChecker() *referenceChecker {
	return &referenceChecker{methodIDs: map[string]struct{}{}}
}

func (c *referenceChecker) addMethodID(methodID string) {
	c.methodIDs[methodID] = struct{}{}
}

func (c *referenceChecker) addEdge(edgeID, callerID, calleeID string) {
	c.edges = append(c.edges, edgeReference{edgeID: edgeID, callerID: callerID, calleeID: calleeID})
}

func (c *referenceChecker) validate() error {
	for _, edge := range c.edges {
		if _, ok := c.methodIDs[edge.callerID]; !ok {
			return fmt.Errorf("callEdge %q references unknown callerMethodId %q", edge.edgeID, edge.callerID)
		}
		if _, ok := c.methodIDs[edge.calleeID]; !ok {
			return fmt.Errorf("callEdge %q references unknown calleeMethodId %q", edge.edgeID, edge.calleeID)
		}
	}
	return nil
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
