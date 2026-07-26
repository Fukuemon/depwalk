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
