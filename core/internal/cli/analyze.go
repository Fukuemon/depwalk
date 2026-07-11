package cli

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/spf13/cobra"

	"github.com/Fukuemon/depwalk/core/internal/analyze"
	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

// analyzeFlags holds the --analyzer-cmd / --language / --analyzer-meta
// flag values for newAnalyzeCommand. The full CLI flag surface (output
// format, traversal direction, depth limits, ...) is out of scope for this
// initial wiring and is left to a later CLI interface spec.
type analyzeFlags struct {
	analyzerCmd  string
	language     string
	analyzerMeta []string
}

func newAnalyzeCommand() *cobra.Command {
	flags := &analyzeFlags{}

	cmd := &cobra.Command{
		Use:   "analyze [path]",
		Short: "Run an Analyzer process and build a call graph",
		Args:  cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			workspaceRoot, err := resolveWorkspaceRoot(args)
			if err != nil {
				return err
			}
			if flags.language == "" {
				return fmt.Errorf("--language is required")
			}

			result, err := analyze.Run(analyze.Options{
				WorkspaceRoot: workspaceRoot,
				Language:      protocol.Language(flags.language),
				AnalyzerCmd:   flags.analyzerCmd,
				AnalyzerMeta:  flags.analyzerMeta,
				Getenv:        os.Getenv,
			})
			if err != nil {
				return err
			}

			for _, diagnostic := range result.Diagnostics {
				fmt.Fprintf(cmd.ErrOrStderr(), "diagnostic [%s] %s: %s\n", diagnostic.Severity, diagnostic.Code, diagnostic.Message)
			}
			fmt.Fprintf(cmd.OutOrStdout(), "analyzed %d method(s), %d call edge(s)\n", result.MethodCount, result.CallEdgeCount)
			return nil
		},
	}

	cmd.Flags().StringVar(&flags.analyzerCmd, "analyzer-cmd", "", "Analyzer launch command (falls back to DEPWALK_ANALYZER_CMD)")
	cmd.Flags().StringVar(&flags.language, "language", "", "source language passed through to the Analyzer request (required)")
	cmd.Flags().StringArrayVar(&flags.analyzerMeta, "analyzer-meta", nil, "key=value metadata passed through to the Analyzer request (repeatable)")

	return cmd
}

func resolveWorkspaceRoot(args []string) (string, error) {
	path := "."
	if len(args) == 1 {
		path = args[0]
	}
	absPath, err := filepath.Abs(path)
	if err != nil {
		return "", fmt.Errorf("resolve workspace root %q: %w", path, err)
	}
	return absPath, nil
}
