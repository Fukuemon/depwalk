// Package analyze orchestrates the depwalk analyze use case: it builds the
// [Request], receives domain-typed analysis results through the [Source]
// port, assembles them into a [graph.Graph], and runs the method-query
// traversal.
//
// The package stays language-agnostic (S5): the language and the
// analysisRequest.metadata entries are opaque passthrough values. It is
// also wire-agnostic (spec #32 D6): Analyzer Protocol DTOs never appear
// here, and neither does the Analyzer launch command — resolving that and
// implementing [Source] belong to cli and protocol respectively, which the
// composition root wires together.
package analyze
