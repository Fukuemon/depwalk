// Package cli defines the depwalk command line interface and acts as the
// composition root.
//
// It owns flag definitions, input validation, error rendering to stderr,
// and the exit code contract. As the outermost layer it also resolves the
// Analyzer launch command (ADR-0003), wires the protocol ACL adapter into
// the analyze use case's port by hand (no DI library, spec #32 D6), and
// renders traversal results through the output package.
package cli

import (
	"errors"

	"github.com/spf13/cobra"

	"github.com/Fukuemon/depwalk/core/internal/analyze"
)

func newRootCommand() *cobra.Command {
	root := &cobra.Command{
		Use:   "depwalk",
		Short: "Analyze dependency paths in source code",
	}
	root.AddCommand(newAnalyzeCommand())
	return root
}

// Execute runs the depwalk root command against the process arguments.
// The returned error is already rendered to stderr by Cobra; callers map it
// to a process exit status with [ExitCode].
func Execute() error {
	return newRootCommand().Execute()
}

// ExitCode maps command results to the process exit code contract.
func ExitCode(err error) int {
	if err == nil {
		return 0
	}
	var inputErr *analyze.InputError
	if errors.As(err, &inputErr) {
		return 2
	}
	return 1
}
