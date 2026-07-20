// Package analyze orchestrates the depwalk analyze use case: it resolves
// the Analyzer launch command, builds the analysisRequest, runs the
// Analyzer process through [analyzer.Runner], and assembles the resulting
// records into a [graph.Graph].
//
// The package stays language-agnostic (S5): it treats the launch command
// as an opaque string and analysisRequest.metadata as an opaque
// passthrough map. It does not know about Java, jar files, or any other
// Analyzer runtime.
package analyze

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"slices"
	"strings"

	"github.com/Fukuemon/depwalk/core/internal/analyzer"
	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/output"
	"github.com/Fukuemon/depwalk/core/internal/protocol"
	"github.com/Fukuemon/depwalk/core/internal/traversal"
)

// Options configures one depwalk analyze run.
type Options struct {
	// WorkspaceRoot is the absolute path to the workspace being analyzed.
	WorkspaceRoot string
	// SourceRoots holds the raw --source-root flag values in the order they
	// were given. Core passes them through to analysisRequest.sourceRoots
	// without interpreting build systems or package hierarchies (S5); an
	// empty slice means the flag was not given and the field is omitted.
	SourceRoots []string
	// Language is passed through to analysisRequest.language without
	// interpretation (S5).
	Language protocol.Language
	// AnalyzerCmd is the --analyzer-cmd flag value, if given.
	AnalyzerCmd string
	// AnalyzerMeta holds the raw --analyzer-meta key=value flag values, in
	// the order they were given.
	AnalyzerMeta []string
	// Include and Exclude hold the raw workspace-relative glob values in the
	// order they were given. Empty slices omit the corresponding request fields.
	Include []string
	Exclude []string
	// Method selects the graph node to traverse. An empty value keeps the
	// legacy summary-only behavior and ignores the remaining query fields.
	Method string
	// Direction and MaxDepth configure traversal for a method query.
	Direction graph.Direction
	MaxDepth  *int
	// Format and Output select and receive the rendered traversal result.
	Format output.Format
	Output io.Writer
	// AnalyzerStderr optionally receives the Analyzer stderr stream as it
	// arrives, without interpretation, on both success and failure.
	AnalyzerStderr io.Writer
	// Getenv reads an environment variable. It defaults to os.Getenv when
	// nil; tests can inject a fake to avoid depending on process
	// environment.
	Getenv func(string) string
}

// AnalyzerFailure is returned by [Run] when the Analyzer reports a fatal
// error record. It preserves the full structured failure — top-level code,
// message, source location, opaque metadata, and ordered details — without
// interpreting Analyzer-specific codes or metadata keys.
type AnalyzerFailure struct {
	Record protocol.AnalyzerError
}

// Error returns the top-level failure summary.
func (e *AnalyzerFailure) Error() string {
	return fmt.Sprintf("analyzer reported a fatal error: %s: %s", e.Record.Code, e.Record.Message)
}

// InputError marks an error caused by values supplied for an analysis request
// or method query. CLI callers use it to distinguish exit code 2 failures from
// runtime failures without interpreting error text.
type InputError struct {
	Err error
}

func (e *InputError) Error() string { return e.Err.Error() }

func (e *InputError) Unwrap() error { return e.Err }

// Result is the outcome of a successful depwalk analyze run.
type Result struct {
	// Graph is the call graph built from the Analyzer's methodSymbol and
	// callEdge records.
	Graph *graph.Graph
	// Diagnostics contains non-fatal diagnostic records reported by the
	// Analyzer.
	Diagnostics []protocol.Diagnostic
	// MethodCount is the number of methodSymbol records folded into Graph.
	MethodCount int
	// CallEdgeCount is the number of callEdge records folded into Graph.
	CallEdgeCount int
}

// Run resolves the Analyzer launch command, executes the Analyzer process,
// and builds a call graph from its output.
//
// A non-nil error is returned when the request cannot be built or
// validated, when the Analyzer stdout fails protocol validation, or when
// the Analyzer reports a fatal error (an "error" record or a non-zero
// exit code); these are all fatal failures the caller should propagate as
// a non-zero process exit.
func Run(opts Options) (Result, error) {
	getenv := opts.Getenv
	if getenv == nil {
		getenv = os.Getenv
	}

	command, err := ResolveCommand(opts.AnalyzerCmd, getenv)
	if err != nil {
		return Result{}, err
	}
	argv, err := SplitCommand(command)
	if err != nil {
		return Result{}, err
	}

	metadata, err := BuildMetadata(opts.AnalyzerMeta)
	if err != nil {
		return Result{}, err
	}

	requestID, err := newRequestID()
	if err != nil {
		return Result{}, err
	}

	request := protocol.AnalysisRequest{
		SchemaVersion: protocol.SchemaVersion,
		RecordType:    protocol.RecordTypeAnalysisRequest,
		RequestID:     requestID,
		WorkspaceRoot: opts.WorkspaceRoot,
		Language:      opts.Language,
		AnalysisMode:  protocol.AnalysisModeFullGraph,
		Metadata:      metadata,
	}
	if len(opts.SourceRoots) > 0 {
		request.SourceRoots = opts.SourceRoots
	}
	if len(opts.Include) > 0 {
		request.Include = opts.Include
	}
	if len(opts.Exclude) > 0 {
		request.Exclude = opts.Exclude
	}
	if err := request.Validate(); err != nil {
		return Result{}, &InputError{Err: fmt.Errorf("invalid analysis request: %w", err)}
	}

	runner := analyzer.New(analyzer.Command{
		Path:   argv[0],
		Args:   argv[1:],
		Stderr: opts.AnalyzerStderr,
	})

	// stagingGraph receives records one at a time, converted to graph-owned
	// values as they arrive; it stays private request state until the run is
	// confirmed successful and is discarded (never published) on any fatal
	// outcome, keeping the request atomic.
	stagingGraph := graph.New()
	methodCount, callEdgeCount := 0, 0
	runResult, err := runner.Run(request, func(record protocol.Record) {
		switch typed := record.(type) {
		case protocol.MethodSymbol:
			stagingGraph.AddNode(graph.NodeFromMethodSymbol(typed))
			methodCount++
		case protocol.CallEdge:
			stagingGraph.AddEdge(graph.EdgeFromCallEdge(typed))
			callEdgeCount++
		}
	})
	if err != nil {
		return Result{}, err
	}
	// A fatal Analyzer outcome (error record or non-zero exit) keeps its own
	// reason: the runner's reference-completeness validation error must not
	// mask it, so the fatal checks run first.
	if runResult.AnalyzerError != nil {
		return Result{}, &AnalyzerFailure{Record: *runResult.AnalyzerError}
	}
	if runResult.ExitCode != 0 {
		return Result{}, fmt.Errorf("analyzer process exited with code %d", runResult.ExitCode)
	}
	if runResult.ValidationError != nil {
		return Result{}, fmt.Errorf("analyzer stdout did not follow the analyzer protocol: %w", runResult.ValidationError)
	}

	result := Result{
		Graph:         stagingGraph,
		Diagnostics:   runResult.Diagnostics,
		MethodCount:   methodCount,
		CallEdgeCount: callEdgeCount,
	}
	if opts.Method == "" {
		return result, nil
	}
	if opts.Output == nil {
		return Result{}, errors.New("analyze: output writer is required for a method query")
	}

	start, err := selectMethod(stagingGraph, opts.Method)
	if err != nil {
		return Result{}, err
	}
	requestForTraversal := traversal.Request{
		StartID:   start.ID,
		Direction: opts.Direction,
		MaxDepth:  opts.MaxDepth,
	}
	traversalResult, err := traversal.Traverse(stagingGraph, requestForTraversal)
	if err != nil {
		return Result{}, fmt.Errorf("traverse method %q: %w", opts.Method, err)
	}
	if err := output.Write(opts.Output, opts.Format, output.Input{
		Graph: stagingGraph, Result: traversalResult, Request: requestForTraversal,
	}); err != nil {
		return Result{}, fmt.Errorf("write %s output: %w", opts.Format, err)
	}
	return result, nil
}

func selectMethod(g *graph.Graph, selector string) (graph.Node, error) {
	nodes := g.Nodes()
	matches := make([]graph.Node, 0, 1)
	if strings.Contains(selector, "(") {
		for _, node := range nodes {
			if node.Symbol.Signature == selector {
				matches = append(matches, node)
			}
		}
	} else {
		separator := strings.LastIndex(selector, "#")
		qualifiedName := ""
		if separator > 0 && separator < len(selector)-1 {
			qualifiedName = selector[:separator] + "." + selector[separator+1:]
		}
		for _, node := range nodes {
			if qualifiedName != "" && node.Symbol.QualifiedName == qualifiedName {
				matches = append(matches, node)
			}
		}
	}

	if len(matches) == 1 {
		return matches[0], nil
	}
	if len(matches) == 0 {
		return graph.Node{}, &InputError{Err: fmt.Errorf("method selector %q did not match any method", selector)}
	}
	candidates := make([]string, len(matches))
	for i, node := range matches {
		candidates[i] = node.Symbol.Signature
	}
	slices.Sort(candidates)
	return graph.Node{}, &InputError{Err: fmt.Errorf(
		"method selector %q is ambiguous; candidates: %s",
		selector,
		strings.Join(candidates, ", "),
	)}
}

func newRequestID() (string, error) {
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return "", fmt.Errorf("generate request id: %w", err)
	}
	return hex.EncodeToString(buf), nil
}
