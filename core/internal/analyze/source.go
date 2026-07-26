package analyze

import (
	"fmt"

	"github.com/Fukuemon/depwalk/core/internal/graph"
)

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

// Err reports the failure the run ended with, or nil when the Analyzer
// finished cleanly.
//
// The order matters. A fatal Analyzer result — an error record, or a non-zero
// exit — keeps its own reason, because the stream's reference-completeness
// check must not mask why the Analyzer actually gave up. Callers get that
// precedence by using this method instead of reading the fields directly.
func (o Outcome) Err() error {
	if o.Failure != nil {
		return o.Failure
	}
	if o.ExitCode != 0 {
		return fmt.Errorf("analyzer process exited with code %d", o.ExitCode)
	}
	if o.ValidationError != nil {
		return fmt.Errorf("analyzer stdout did not follow the analyzer protocol: %w", o.ValidationError)
	}
	return nil
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
