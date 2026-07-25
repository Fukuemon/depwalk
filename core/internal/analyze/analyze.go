// Package analyze orchestrates the depwalk analyze use case: it composes
// the analysis request, receives domain-typed analysis results through the
// [AnalysisSource] port, assembles them into a [graph.Graph], and runs the
// method-query traversal.
//
// The package stays language-agnostic (S5): it treats the launch command
// as an opaque string and analysisRequest.metadata as an opaque
// passthrough map. It is also wire-agnostic (spec #32 D6): Analyzer
// Protocol DTOs never appear here. The protocol package's ACL adapter
// implements [AnalysisSource], and cli wires the two together.
package analyze

import (
	"errors"
	"fmt"
	"slices"
	"strings"

	"github.com/Fukuemon/depwalk/core/internal/graph"
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
	Language string
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
}

// AnalysisRequest describes one analysis run handed to the
// [AnalysisSource] port. Every field is passed through to the Analyzer
// request without interpretation (S5); the port implementation owns the
// wire form (request id, schema version, validation).
type AnalysisRequest struct {
	WorkspaceRoot string
	SourceRoots   []string
	Language      string
	Include       []string
	Exclude       []string
	Metadata      map[string]any
}

// AnalysisOutcome is the process-level outcome the [AnalysisSource] port
// reports after the record stream ends.
type AnalysisOutcome struct {
	// Diagnostics contains non-fatal diagnostic records reported by the
	// Analyzer, translated to domain values.
	Diagnostics []Diagnostic
	// Failure is the fatal Analyzer error record, if one was emitted.
	Failure *AnalyzerFailure
	// ValidationError is the first Core-side stdout validation error.
	ValidationError error
	// ExitCode is the Analyzer process exit code.
	ExitCode int
}

// AnalysisSource is the port through which the use case receives
// domain-typed analysis results: nodes and edges are streamed to the
// callbacks as they arrive, and the process-level outcome is returned
// once the stream ends. The interface is defined consumer-side (spec #32
// D6); the protocol package's ACL adapter implements it, and cli injects
// that adapter into [New].
type AnalysisSource interface {
	RunAnalysis(request AnalysisRequest, onNode func(graph.Node), onEdge func(graph.Edge)) (AnalysisOutcome, error)
}

// Diagnostic is a non-fatal Analyzer diagnostic translated to domain
// values. Metadata is opaque and never interpreted by Core.
type Diagnostic struct {
	Severity        string
	Code            string
	Message         string
	Source          *graph.SourceLocation
	RelatedMethodID string
	Metadata        map[string]any
}

// AnalyzerFailure is returned by [Runner.Run] when the Analyzer reports a
// fatal error record. It preserves the full structured failure —
// top-level code, message, source location, opaque metadata, and ordered
// details — without interpreting Analyzer-specific codes or metadata keys.
type AnalyzerFailure struct {
	Code     string
	Message  string
	Source   *graph.SourceLocation
	Metadata map[string]any
	Details  []FailureDetail
}

// FailureDetail is one language-agnostic structured detail of a fatal
// Analyzer failure.
type FailureDetail struct {
	Code     string
	Message  string
	Source   *graph.SourceLocation
	Metadata map[string]any
}

// Error returns the top-level failure summary.
func (e *AnalyzerFailure) Error() string {
	return fmt.Sprintf("analyzer reported a fatal error: %s: %s", e.Code, e.Message)
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
	Diagnostics []Diagnostic
	// MethodCount is the number of methodSymbol records folded into Graph.
	MethodCount int
	// CallEdgeCount is the number of callEdge records folded into Graph.
	CallEdgeCount int
	// MethodQuery holds the traversal outcome of a method query; nil when
	// Options.Method was empty.
	MethodQuery *MethodQuery
}

// MethodQuery is the traversal request and result of a method query, kept
// together so the caller can render them (the output formatter consumes
// both).
type MethodQuery struct {
	Request traversal.Request
	Result  traversal.Result
}

// Runner runs the analyze use case against an injected [AnalysisSource].
// It is published as a struct, not an interface: callers that need an
// abstraction define one on their side (spec #32 D6).
type Runner struct {
	source AnalysisSource
}

// New returns a [Runner] backed by source.
func New(source AnalysisSource) Runner {
	return Runner{source: source}
}

// Run executes the Analyzer through the port and builds a call graph from
// its records.
//
// A non-nil error is returned when the request cannot be built or
// validated, when the Analyzer stdout fails protocol validation, or when
// the Analyzer reports a fatal error (an "error" record or a non-zero
// exit code); these are all fatal failures the caller should propagate as
// a non-zero process exit.
func (r Runner) Run(opts Options) (Result, error) {
	if r.source == nil {
		return Result{}, errors.New("analyze: an AnalysisSource is required")
	}

	metadata, err := BuildMetadata(opts.AnalyzerMeta)
	if err != nil {
		return Result{}, err
	}

	request := AnalysisRequest{
		WorkspaceRoot: opts.WorkspaceRoot,
		SourceRoots:   opts.SourceRoots,
		Language:      opts.Language,
		Include:       opts.Include,
		Exclude:       opts.Exclude,
		Metadata:      metadata,
	}

	// stagingGraph receives domain values one at a time as the port streams
	// them; it stays private request state until the run is confirmed
	// successful and is discarded (never published) on any fatal outcome,
	// keeping the request atomic.
	stagingGraph := graph.New()
	methodCount, callEdgeCount := 0, 0
	outcome, err := r.source.RunAnalysis(request,
		func(node graph.Node) {
			stagingGraph.AddNode(node)
			methodCount++
		},
		func(edge graph.Edge) {
			stagingGraph.AddEdge(edge)
			callEdgeCount++
		},
	)
	if err != nil {
		return Result{}, err
	}
	// A fatal Analyzer outcome (error record or non-zero exit) keeps its own
	// reason: the stream's reference-completeness validation error must not
	// mask it, so the fatal checks run first.
	if outcome.Failure != nil {
		return Result{}, outcome.Failure
	}
	if outcome.ExitCode != 0 {
		return Result{}, fmt.Errorf("analyzer process exited with code %d", outcome.ExitCode)
	}
	if outcome.ValidationError != nil {
		return Result{}, fmt.Errorf("analyzer stdout did not follow the analyzer protocol: %w", outcome.ValidationError)
	}

	result := Result{
		Graph:         stagingGraph,
		Diagnostics:   outcome.Diagnostics,
		MethodCount:   methodCount,
		CallEdgeCount: callEdgeCount,
	}
	if opts.Method == "" {
		return result, nil
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
	result.MethodQuery = &MethodQuery{Request: requestForTraversal, Result: traversalResult}
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
		for _, node := range nodes {
			signatureName, _, hasArgumentList := strings.Cut(node.Symbol.Signature, "(")
			if hasArgumentList && signatureName == selector {
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
