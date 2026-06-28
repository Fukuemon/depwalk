package cli

import "github.com/spf13/cobra"

func newRootCommand() *cobra.Command {
	return &cobra.Command{
		Use:   "depwalk",
		Short: "Analyze dependency paths in source code",
	}
}

func Execute() error {
	return newRootCommand().Execute()
}
