// Package analyzer runs Analyzer processes: it owns spawn, stdin / stdout /
// stderr streaming, and exit code handling only.
//
// The JSONL payload is opaque byte lines here. Composing the analysis
// request and parsing / validating Analyzer Protocol records are the
// protocol package's responsibility (ACL); this package depends on no
// other internal package.
package analyzer
