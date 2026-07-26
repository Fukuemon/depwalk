// Package analyze orchestrates the depwalk analyze use case: it composes
// the analysis request, receives domain-typed analysis results through the
// [Source] port, assembles them into a [graph.Graph], and runs the
// method-query traversal.
//
// The package stays language-agnostic (S5): it treats the launch command
// as an opaque string and analysisRequest.metadata as an opaque
// passthrough map. It is also wire-agnostic (spec #32 D6): Analyzer
// Protocol DTOs never appear here. The protocol package's ACL adapter
// implements [Source], and cli wires the two together.
package analyze
