package protocol

import (
	"encoding/json"
	"fmt"

	"github.com/Fukuemon/depwalk/core/internal/analyzer"
)

// Runner runs one Analyzer process for one analysis request and parses its
// stdout as Analyzer Protocol records. Process control (spawn / stdio /
// exit code) is delegated to the analyzer package, which treats the
// payload as opaque lines; the protocol-aware half of the exchange lives
// here.
type Runner struct {
	command analyzer.Command
}

// NewRunner returns a Runner that launches the Analyzer with command.
func NewRunner(command analyzer.Command) Runner {
	return Runner{command: command}
}

// RunResult contains protocol-level results of an Analyzer run. Method
// symbol and call edge records are not buffered here; they are handed to
// the onRecord callback of [Runner.Run] as they arrive.
type RunResult struct {
	// Diagnostics contains diagnostic records parsed from stdout.
	Diagnostics []Diagnostic
	// AnalyzerError contains the fatal Analyzer error record, if one was emitted.
	AnalyzerError *AnalyzerError
	// ValidationError contains the first Core-side stdout validation error.
	ValidationError error
	// ExitCode is the Analyzer process exit code.
	ExitCode int
	// Stderr is the raw Analyzer stderr output.
	Stderr string
}

// Run starts an Analyzer process, sends one analysisRequest JSONL record,
// and parses stdout records until the process exits. Each valid
// methodSymbol and callEdge record is passed to onRecord as it is
// received; onRecord may be nil when the caller does not consume graph
// records.
func (r Runner) Run(request AnalysisRequest, onRecord func(Record)) (RunResult, error) {
	if err := request.Validate(); err != nil {
		return RunResult{}, err
	}
	requestLine, err := json.Marshal(request)
	if err != nil {
		return RunResult{}, err
	}
	requestLine = append(requestLine, '\n')

	collector := newRecordCollector(onRecord)
	procResult, runErr := analyzer.New(r.command).Run(requestLine, collector.addLine)
	result := collector.finalize(procResult.ReadError)
	result.ExitCode = procResult.ExitCode
	result.Stderr = procResult.Stderr
	if runErr != nil {
		return result, runErr
	}
	return result, nil
}

// recordCollector consumes the Analyzer stdout stream one JSONL line at a
// time. Graph records are converted downstream via onRecord instead of
// being buffered; only method IDs and edge endpoints are retained for the
// post-stream reference-completeness check.
type recordCollector struct {
	onRecord   func(Record)
	references *referenceChecker
	result     RunResult
}

func newRecordCollector(onRecord func(Record)) *recordCollector {
	return &recordCollector{onRecord: onRecord, references: newReferenceChecker()}
}

func (c *recordCollector) addLine(line []byte) {
	record, parseErr := ParseRecord(line)
	if parseErr != nil {
		c.setValidationError(parseErr)
		return
	}
	c.addRecord(record)
}

func (c *recordCollector) addRecord(record Record) {
	switch typed := record.(type) {
	case MethodSymbol:
		c.references.addMethodID(typed.MethodID)
		if c.onRecord != nil {
			c.onRecord(record)
		}
	case CallEdge:
		c.references.addEdge(typed.EdgeID, typed.CallerMethodID, typed.CalleeMethodID)
		if c.onRecord != nil {
			c.onRecord(record)
		}
	case Diagnostic:
		c.result.Diagnostics = append(c.result.Diagnostics, typed)
	case AnalyzerError:
		errRecord := typed
		c.result.AnalyzerError = &errRecord
	default:
		c.setValidationError(fmt.Errorf("analyzer stdout record type %T is not allowed", record))
	}
}

// finalize closes the stream: a stdout read failure is reported as a
// validation error unless an earlier one was already recorded, and the
// reference-completeness check runs last.
func (c *recordCollector) finalize(readErr error) RunResult {
	if readErr != nil {
		c.setValidationError(readErr)
	}
	// A fatal stream makes Core discard every preceding record, so dangling
	// references are not reported as a separate validation failure (the
	// fatal contract in design/features/graph/DesignDoc_graph.md).
	if c.result.AnalyzerError == nil {
		if err := c.references.validate(); err != nil {
			c.setValidationError(err)
		}
	}
	return c.result
}

func (c *recordCollector) setValidationError(err error) {
	if c.result.ValidationError == nil {
		c.result.ValidationError = err
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
