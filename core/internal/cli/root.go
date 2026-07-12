package cli

import "github.com/spf13/cobra"

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
