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
