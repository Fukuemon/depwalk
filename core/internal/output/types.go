package output

import (
	"io"

	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/traversal"
)

// Format identifies an output representation.
type Format string

const (
	// FormatConsole selects human-readable console output.
	FormatConsole Format = "console"
	// FormatJSON selects machine-readable JSON output.
	FormatJSON Format = "json"
	// FormatDOT reserves Graphviz DOT output for Phase 4.
	FormatDOT Format = "dot"
	// FormatMermaid reserves Mermaid output for Phase 4.
	FormatMermaid Format = "mermaid"
)

// Input contains the graph, traversal result, and request needed to build a View.
type Input struct {
	Graph   *graph.Graph
	Result  traversal.Result
	Request traversal.Request
}

// View is the symbol-resolved, deterministically ordered input shared by formatters.
type View struct {
	Status    traversal.Status
	Direction graph.Direction
	Start     NodeView
	Nodes     []NodeView
	Edges     []EdgeView
	Cutoffs   []CutoffView
}

// NodeView describes one reached method. Symbol fields may be empty when unavailable.
type NodeView struct {
	ID            string
	QualifiedName string
	Signature     string
	Source        *graph.SourceLocation
	MinDepth      int
	Metadata      map[string]any
}

// EdgeView describes one reached call edge.
type EdgeView struct {
	ID       string
	CallerID string
	CalleeID string
	Cycle    bool
	CallSite *graph.SourceLocation
	Metadata map[string]any
}

// CutoffView describes one call edge excluded by the traversal depth limit.
type CutoffView struct {
	EdgeID         string
	CallerID       string
	CalleeID       string
	TargetMethodID string
	TargetMinDepth int
	CallSite       *graph.SourceLocation
}

// Formatter renders a View to a writer.
type Formatter interface {
	Format(w io.Writer, view View) error
}
