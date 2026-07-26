package analyze

import "github.com/Fukuemon/depwalk/core/internal/graph"

// Request describes one analysis run handed to the [Source] port. Every
// field is passed through to the Analyzer request without interpretation;
// the port implementation owns the wire form (request id, schema version,
// validation).
type Request struct {
	WorkspaceRoot string
	SourceRoots   []string
	Language      string
	Include       []string
	Exclude       []string
	Metadata      map[string]any
}

// Outcome is the process-level result the [Source] port reports after the
// record stream ends.
type Outcome struct {
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

// Source is the port through which the use case receives domain-typed
// analysis results: nodes and edges are streamed to the callbacks as they
// arrive, and the process-level outcome is returned once the stream ends.
// The interface is defined consumer-side; the protocol
// package's ACL adapter implements it, and cli injects that adapter into
// [New].
type Source interface {
	Run(request Request, onNode func(graph.Node), onEdge func(graph.Edge)) (Outcome, error)
}

// Diagnostic is a non-fatal Analyzer diagnostic translated to domain
// values. Metadata is opaque and never interpreted by Core.
type Diagnostic struct {
	Severity        string
	Code            string
	Message         string
	Location        *graph.SourceLocation
	RelatedMethodID string
	Metadata        map[string]any
}
