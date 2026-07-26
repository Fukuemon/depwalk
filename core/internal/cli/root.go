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

// ExitCode maps command results to the process exit code contract: 0 on
// success, 2 when the failure was caused by what the user supplied, and 1 for
// every runtime failure.
//
// Bad input is reported by two types — one for flag values rejected here, one
// for values the analyze use case rejects (an unknown or ambiguous method
// selector, an invalid request). Deciding that both mean exit 2 is this
// function's job; neither package needs to know about exit codes.
func ExitCode(err error) int {
	if err == nil {
		return 0
	}
	var flagErr *inputError
	var useCaseErr *analyze.InputError
	if errors.As(err, &flagErr) || errors.As(err, &useCaseErr) {
		return 2
	}
	return 1
}
