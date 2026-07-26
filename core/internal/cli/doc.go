// Package cli defines the depwalk command line interface and acts as the
// composition root.
//
// It owns flag definitions, input validation, error rendering to stderr,
// and the exit code contract. As the outermost layer it also resolves the
// Analyzer launch command (ADR-0003), wires the protocol ACL adapter into
// the analyze use case's port by hand (no DI library, spec #32 D6), and
// renders traversal results through the output package.
package cli
