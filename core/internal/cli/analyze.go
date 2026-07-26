package cli

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"slices"
	"strings"

	"github.com/spf13/cobra"

	"github.com/Fukuemon/depwalk/core/internal/analyze"
	"github.com/Fukuemon/depwalk/core/internal/analyzer"
	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/output"
	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

// cli はコンポジションルートとして ACL adapter を analyze の port へ手動 DI
// で注入する。port の満足検証はこの配線箇所に集約する (spec #32 D6)。
var _ analyze.Source = (*protocol.Adapter)(nil)

// analyzeFlags holds the complete flag surface for newAnalyzeCommand:
// Analyzer launch inputs, source filters, and method query options.
type analyzeFlags struct {
	analyzerCmd  string
	language     string
	analyzerMeta []string
	sourceRoots  []string
	method       string
	direction    string
	maxDepth     int
	format       string
	include      []string
	exclude      []string
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
			// Query output is the only content allowed on stdout. Suppress Cobra's
			// usage block for all RunE failures; Cobra still renders ordinary error
			// messages to stderr, while AnalyzerFailure keeps its custom renderer.
			cmd.SilenceUsage = true
			workspaceRoot, err := resolveWorkspaceRoot(args)
			if err != nil {
				return err
			}
			if flags.language == "" {
				return &analyze.InputError{Err: errors.New("--language is required")}
			}
			if flags.direction != string(graph.DirectionCaller) && flags.direction != string(graph.DirectionCallee) {
				return &analyze.InputError{Err: fmt.Errorf(
					"invalid --direction %q: want %q or %q",
					flags.direction,
					graph.DirectionCaller,
					graph.DirectionCallee,
				)}
			}
			registeredFormats := output.RegisteredFormats()
			if !slices.Contains(registeredFormats, flags.format) {
				return &analyze.InputError{Err: fmt.Errorf(
					"invalid --format %q: registered formats: %s",
					flags.format,
					strings.Join(registeredFormats, ", "),
				)}
			}
			if flags.maxDepth < 0 {
				return &analyze.InputError{Err: fmt.Errorf("invalid --max-depth %d: want >= 0", flags.maxDepth)}
			}
			var maxDepth *int
			if cmd.Flags().Changed("max-depth") {
				maxDepth = &flags.maxDepth
			}

			command, err := resolveAnalyzerCommand(flags.analyzerCmd, os.Getenv)
			if err != nil {
				return err
			}
			argv, err := splitAnalyzerCommand(command)
			if err != nil {
				return err
			}

			runner := analyze.New(protocol.NewAdapter(analyzer.Command{
				Path:   argv[0],
				Args:   argv[1:],
				Stderr: cmd.ErrOrStderr(),
			}))
			result, err := runner.Run(analyze.Options{
				WorkspaceRoot: workspaceRoot,
				SourceRoots:   flags.sourceRoots,
				Language:      flags.language,
				AnalyzerMeta:  flags.analyzerMeta,
				Include:       flags.include,
				Exclude:       flags.exclude,
				Method:        flags.method,
				Direction:     graph.Direction(flags.direction),
				MaxDepth:      maxDepth,
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

			if result.MethodQuery != nil {
				if err := output.Write(cmd.OutOrStdout(), output.Format(flags.format), output.Input{
					Graph:   result.Graph,
					Result:  result.MethodQuery.Result,
					Request: result.MethodQuery.Request,
				}); err != nil {
					return fmt.Errorf("write %s output: %w", flags.format, err)
				}
			}

			for _, diagnostic := range result.Diagnostics {
				fmt.Fprintf(cmd.ErrOrStderr(), "diagnostic [%s] %s: %s\n", diagnostic.Severity, diagnostic.Code, diagnostic.Message)
			}
			if flags.method == "" {
				fmt.Fprintf(cmd.OutOrStdout(), "analyzed %d method(s), %d call edge(s)\n", result.MethodCount, result.CallEdgeCount)
			}
			return nil
		},
	}
	cmd.SetFlagErrorFunc(func(cmd *cobra.Command, err error) error {
		// Flag parsing happens before RunE, so classify parse/type failures here
		// instead of relying on RunE's semantic validation.
		cmd.SilenceUsage = true
		return &analyze.InputError{Err: err}
	})

	cmd.Flags().StringVar(&flags.analyzerCmd, "analyzer-cmd", "", "Analyzer launch command (falls back to DEPWALK_ANALYZER_CMD)")
	cmd.Flags().StringVar(&flags.language, "language", "", "source language passed through to the Analyzer request (required)")
	cmd.Flags().StringArrayVar(&flags.analyzerMeta, "analyzer-meta", nil, "key=value metadata passed through to the Analyzer request (repeatable)")
	cmd.Flags().StringArrayVar(&flags.sourceRoots, "source-root", nil, "workspace-relative source root passed through to the Analyzer request in the given order; bypasses Analyzer build-model discovery (repeatable)")
	cmd.Flags().StringVar(&flags.method, "method", "", "method selector: <binary-type>#<method>[(<argument-types>)]")
	cmd.Flags().StringVar(&flags.direction, "direction", string(graph.DirectionCaller), "traversal direction: caller or callee")
	cmd.Flags().IntVar(&flags.maxDepth, "max-depth", 0, "maximum traversal depth (default: unlimited)")
	cmd.Flags().StringVar(&flags.format, "format", string(output.FormatConsole), "output format registered by the output engine")
	cmd.Flags().StringArrayVar(&flags.include, "include", nil, "workspace-relative source path glob to include (repeatable)")
	cmd.Flags().StringArrayVar(&flags.exclude, "exclude", nil, "workspace-relative source path glob to exclude (repeatable)")

	return cmd
}

// renderAnalyzerFailure prints the structured Analyzer failure: the top-level
// summary first, then each failure detail in array order. Only the common
// protocol fields are rendered; Analyzer-specific codes and metadata keys are
// shown verbatim, never interpreted.
func renderAnalyzerFailure(w io.Writer, failure *analyze.AnalyzerFailure) {
	fmt.Fprintf(w, "Error: %s\n", failure.Error())
	if failure.Source != nil {
		fmt.Fprintf(w, "  at %s\n", formatSourceLocation(failure.Source))
	}
	if failure.Metadata != nil {
		fmt.Fprintf(w, "  metadata %s\n", canonicalJSON(failure.Metadata))
	}
	for i, detail := range failure.Details {
		fmt.Fprintf(w, "detail[%d] %s: %s\n", i, detail.Code, detail.Message)
		if detail.Source != nil {
			fmt.Fprintf(w, "  at %s\n", formatSourceLocation(detail.Source))
		}
		if detail.Metadata != nil {
			fmt.Fprintf(w, "  metadata %s\n", canonicalJSON(detail.Metadata))
		}
	}
}

func formatSourceLocation(location *graph.SourceLocation) string {
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
