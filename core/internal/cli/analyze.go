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

// コンポジションルートとして、cli が ACL adapter を analyze の port へ手で注入する。
// interface 充足の検査は実装側の package ではなく、配線のあるここに置く。
// 配線と検査が離れると、配線を変えたときに検査が置き去りになる。
var _ analyze.Source = (*protocol.Adapter)(nil)

// analyzeFlags は newAnalyzeCommand が受ける flag 一式。Analyzer の起動入力、
// ソースの絞り込み、method query の選択肢からなる。
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

// analyzeLongHelp は言語に依存しない書き方を保つ。Analyzer 側の source root
// discovery とその副作用を、特定の build tool 名を出さずに説明する。
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
			// stdout に出してよいのは query の出力だけ。RunE の失敗では Cobra の
			// usage 表示を抑止する。通常の error は Cobra が stderr へ出し、
			// AnalyzerFailure は独自の描画を保つ。
			cmd.SilenceUsage = true
			workspaceRoot, err := resolveWorkspaceRoot(args)
			if err != nil {
				return err
			}
			if flags.language == "" {
				return invalidInput("--language is required")
			}
			if flags.direction != string(graph.DirectionCaller) && flags.direction != string(graph.DirectionCallee) {
				return invalidInput(
					"invalid --direction %q: want %q or %q",
					flags.direction,
					graph.DirectionCaller,
					graph.DirectionCallee,
				)
			}
			registeredFormats := output.RegisteredFormats()
			if !slices.Contains(registeredFormats, flags.format) {
				return invalidInput(
					"invalid --format %q: registered formats: %s",
					flags.format,
					strings.Join(registeredFormats, ", "),
				)
			}
			if flags.maxDepth < 0 {
				return invalidInput("invalid --max-depth %d: want >= 0", flags.maxDepth)
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
					// サマリを先頭にした失敗の全文は描画済み。後続で cobra が
					// 出す重複サマリと usage を抑止する。
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
		// flag の parse は RunE より前に走る。そのため parse / 型の失敗はここで
		// 分類する。RunE の意味検証には載らない。
		cmd.SilenceUsage = true
		return &inputError{err: err}
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

// renderAnalyzerFailure は構造化された Analyzer の失敗を出力する。top-level の
// サマリを先に、続けて各明細を配列順に出す。
//
// 描画するのは protocol の共通 field だけ。Analyzer 固有の code や metadata の key は
// そのまま出し、解釈しない。解釈すると Core が言語固有の知識を持つ。
func renderAnalyzerFailure(w io.Writer, failure *analyze.AnalyzerFailure) {
	fmt.Fprintf(w, "Error: %s\n", failure.Error())
	if failure.Location != nil {
		fmt.Fprintf(w, "  at %s\n", formatSourceLocation(failure.Location))
	}
	if failure.Metadata != nil {
		fmt.Fprintf(w, "  metadata %s\n", canonicalJSON(failure.Metadata))
	}
	for i, detail := range failure.Details {
		fmt.Fprintf(w, "detail[%d] %s: %s\n", i, detail.Code, detail.Message)
		if detail.Location != nil {
			fmt.Fprintf(w, "  at %s\n", formatSourceLocation(detail.Location))
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
	// 終了位置を持つ record は範囲として描画する。Protocol が保持している情報を
	// ここで落とすと、利用者から見えなくなるため。
	if location.EndLine != nil {
		end := fmt.Sprintf("%d", *location.EndLine)
		if location.EndColumn != nil {
			end = fmt.Sprintf("%s:%d", end, *location.EndColumn)
		}
		text = fmt.Sprintf("%s-%s", text, end)
	}
	return text
}

// canonicalJSON は opaque な metadata を、object の key を辞書順 (encoding/json が
// map の key を整列する)、配列を入力順にした compact な JSON として描画する。
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
