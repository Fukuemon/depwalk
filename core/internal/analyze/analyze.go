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
	"fmt"
	"os"

	"github.com/Fukuemon/depwalk/core/internal/analyzer"
	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

// Options configures one depwalk analyze run.
type Options struct {
	// WorkspaceRoot is the absolute path to the workspace being analyzed.
	WorkspaceRoot string
	// Language is passed through to analysisRequest.language without
	// interpretation (S5).
	Language protocol.Language
	// AnalyzerCmd is the --analyzer-cmd flag value, if given.
	AnalyzerCmd string
	// AnalyzerMeta holds the raw --analyzer-meta key=value flag values, in
	// the order they were given.
	AnalyzerMeta []string
	// Getenv reads an environment variable. It defaults to os.Getenv when
	// nil; tests can inject a fake to avoid depending on process
	// environment.
	Getenv func(string) string
}

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
		Metadata:      metadata,
	}
	if err := request.Validate(); err != nil {
		return Result{}, err
	}

	runner := analyzer.New(analyzer.Command{
		Path: argv[0],
		Args: argv[1:],
	})
	runResult, err := runner.Run(request)
	if err != nil {
		return Result{}, err
	}
	if runResult.ValidationError != nil {
		return Result{}, fmt.Errorf("analyzer stdout did not follow the analyzer protocol: %w", runResult.ValidationError)
	}
	if runResult.AnalyzerError != nil {
		return Result{}, fmt.Errorf("analyzer reported a fatal error: %s: %s", runResult.AnalyzerError.Code, runResult.AnalyzerError.Message)
	}
	if runResult.ExitCode != 0 {
		return Result{}, fmt.Errorf("analyzer process exited with code %d", runResult.ExitCode)
	}

	g, methodCount, callEdgeCount := buildGraph(runResult.Records)
	return Result{
		Graph:         g,
		Diagnostics:   runResult.Diagnostics,
		MethodCount:   methodCount,
		CallEdgeCount: callEdgeCount,
	}, nil
}

func buildGraph(records []protocol.Record) (g *graph.Graph, methodCount, callEdgeCount int) {
	g = graph.New()
	for _, record := range records {
		switch typed := record.(type) {
		case protocol.MethodSymbol:
			g.AddNode(graph.NodeFromMethodSymbol(typed))
			methodCount++
		case protocol.CallEdge:
			g.AddEdge(graph.EdgeFromCallEdge(typed))
			callEdgeCount++
		}
	}
	return g, methodCount, callEdgeCount
}

func newRequestID() (string, error) {
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return "", fmt.Errorf("generate request id: %w", err)
	}
	return hex.EncodeToString(buf), nil
}
