// Package protocol defines the JSONL records exchanged between depwalk Core
// and Analyzer processes, and acts as the ACL (anti-corruption layer)
// between that wire format and the domain model.
//
// The package owns the Analyzer Protocol data contract — record DTOs,
// validation, and strict JSONL parsing — plus the two ACL halves (spec #32
// D6): the Translator (translate.go) that converts wire records into
// graph-owned domain values, and the Adapter (adapter.go) that implements
// the analyze.AnalysisSource port by driving one Analyzer run through
// [Runner]. Raw process execution is delegated to the analyzer package.
package protocol
