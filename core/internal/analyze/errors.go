package analyze

import (
	"fmt"

	"github.com/Fukuemon/depwalk/core/internal/graph"
)

// AnalyzerFailure is returned by [Runner.Run] when the Analyzer reports a
// fatal error record. It preserves the full structured failure —
// top-level code, message, source location, opaque metadata, and ordered
// details — without interpreting Analyzer-specific codes or metadata keys.
type AnalyzerFailure struct {
	Code     string
	Message  string
	Location *graph.SourceLocation
	Metadata map[string]any
	Details  []FailureDetail
}

// FailureDetail is one language-agnostic structured detail of a fatal
// Analyzer failure.
type FailureDetail struct {
	Code     string
	Message  string
	Location *graph.SourceLocation
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

// Error returns the wrapped error's message.
func (e *InputError) Error() string { return e.Err.Error() }

// Unwrap returns the wrapped error so callers can inspect its cause.
func (e *InputError) Unwrap() error { return e.Err }
