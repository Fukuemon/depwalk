package cli

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"

	"github.com/spf13/cobra"

	"github.com/Fukuemon/depwalk/core/internal/analyze"
	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

// analyzeFlags holds the --analyzer-cmd / --language / --analyzer-meta /
// --source-root flag values for newAnalyzeCommand. The full CLI flag surface
// (output format, traversal direction, depth limits, ...) is out of scope for
// this initial wiring and is left to a later CLI interface spec.
type analyzeFlags struct {
	analyzerCmd  string
	language     string
	analyzerMeta []string
	sourceRoots  []string
}

// analyzeLongHelp stays language-agnostic: it describes Analyzer-side source
// root discovery and its possible side effects without naming any build tool.
const analyzeLongHelp = `Run an Analyzer process and build a call graph.

When no --source-root is given, the selected Analyzer discovers source roots
from the workspace's build model. That discovery may evaluate build logic of
the target workspace, access the network, use credential providers already
configured for the build tool, and update the build tool's caches.

Passing one or more --source-root values bypasses build-model discovery
completely: the Analyzer uses only the explicit roots, in the given order.`

func newAnalyzeCommand() *cobra.Command {
	flags := &analyzeFlags{}

	cmd := &cobra.Command{
		Use:   "analyze [path]",
		Short: "Run an Analyzer process and build a call graph",
		Long:  analyzeLongHelp,
		Args:  cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			workspaceRoot, err := resolveWorkspaceRoot(args)
			if err != nil {
				return err
			}
			if flags.language == "" {
				return errors.New("--language is required")
			}

			result, err := analyze.Run(analyze.Options{
				WorkspaceRoot:  workspaceRoot,
				SourceRoots:    flags.sourceRoots,
				Language:       protocol.Language(flags.language),
				AnalyzerCmd:    flags.analyzerCmd,
				AnalyzerMeta:   flags.analyzerMeta,
				AnalyzerStderr: cmd.ErrOrStderr(),
				Getenv:         os.Getenv,
			})
			if err != nil {
				var failure *analyze.AnalyzerFailure
				if errors.As(err, &failure) {
					renderAnalyzerFailure(cmd.ErrOrStderr(), failure)
					// The full failure, summary first, is already rendered;
					// suppress cobra's trailing duplicate summary and the
					// usage block that would follow it.
					cmd.SilenceErrors = true
					cmd.SilenceUsage = true
				}
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
	cmd.Flags().StringArrayVar(&flags.sourceRoots, "source-root", nil, "workspace-relative source root passed through to the Analyzer request in the given order; bypasses Analyzer build-model discovery (repeatable)")

	return cmd
}

// renderAnalyzerFailure prints the structured Analyzer failure: the top-level
// summary first, then each failure detail in array order. Only the common
// protocol fields are rendered; Analyzer-specific codes and metadata keys are
// shown verbatim, never interpreted.
func renderAnalyzerFailure(w io.Writer, failure *analyze.AnalyzerFailure) {
	fmt.Fprintf(w, "Error: %s\n", failure.Error())
	record := failure.Record
	if record.Source != nil {
		fmt.Fprintf(w, "  at %s\n", formatSourceLocation(record.Source))
	}
	if record.Metadata != nil {
		fmt.Fprintf(w, "  metadata %s\n", canonicalJSON(record.Metadata))
	}
	for i, detail := range record.Details {
		fmt.Fprintf(w, "detail[%d] %s: %s\n", i, detail.Code, detail.Message)
		if detail.Source != nil {
			fmt.Fprintf(w, "  at %s\n", formatSourceLocation(detail.Source))
		}
		if detail.Metadata != nil {
			fmt.Fprintf(w, "  metadata %s\n", canonicalJSON(detail.Metadata))
		}
	}
}

func formatSourceLocation(location *protocol.SourceLocation) string {
	text := fmt.Sprintf("%s:%d", location.Path, location.StartLine)
	if location.StartColumn != nil {
		text = fmt.Sprintf("%s:%d", text, *location.StartColumn)
	}
	// end 位置を保持している record は範囲として併記する (Protocol は保持
	// しているのに renderer が捨てると利用者へ届かないため)。
	if location.EndLine != nil {
		end := fmt.Sprintf("%d", *location.EndLine)
		if location.EndColumn != nil {
			end = fmt.Sprintf("%s:%d", end, *location.EndColumn)
		}
		text = fmt.Sprintf("%s-%s", text, end)
	}
	return text
}

// canonicalJSON renders opaque metadata as compact JSON with object keys in
// lexicographic order (encoding/json sorts map keys) and arrays in input
// order.
func canonicalJSON(value any) string {
	encoded, err := json.Marshal(value)
	if err != nil {
		return fmt.Sprintf("(unrenderable metadata: %v)", err)
	}
	return string(encoded)
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
