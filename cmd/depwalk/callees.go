package main

import (
	"fmt"

	"github.com/Fukuemon/depwalk/internal/driver"
	"github.com/Fukuemon/depwalk/internal/infra/output"
	"github.com/Fukuemon/depwalk/internal/pipeline"
	"github.com/spf13/cobra"
)

func newCalleesCmd() *cobra.Command {
	var (
		depth    int
		format   string
		maxNodes int
	)

	cmd := &cobra.Command{
		Use:   "callees <selector>",
		Short: "Explore callees (outgoing calls) recursively",
		Long: `Explore methods called by the specified method, recursively.

Selector formats:
  file:line[:col]  - Start from the method containing this position
  file#method      - Start from the named method (must be unambiguous)

Examples:
  depwalk callees src/main/java/com/example/Service.java:42
  depwalk callees src/main/java/com/example/Service.java#process --depth 5
  depwalk callees src/Service.java:10 --format mermaid`,
		Args: cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			selectorRaw := args[0]

			// Get language driver
			d, err := driver.Get(rf.lang)
			if err != nil {
				return err
			}

			// Build pipeline config
			cfg := pipeline.Config{
				Depth:        depth,
				Format:       output.Format(format),
				MaxNodes:     maxNodes,
				Verbose:      rf.verbose,
				ProjectRoot:  rf.projectRoot,
				IncludeTests: rf.includeTS,
				CacheDir:     rf.cacheDir,
				NoCache:      rf.noCache,
			}

			// Create and run pipeline
			p := pipeline.NewCalleesPipeline(d.Dependencies(), cfg)
			result, err := p.Run(rootContext(), selectorRaw)
			if err != nil {
				return err
			}

			fmt.Fprint(cmd.OutOrStdout(), result)
			return nil
		},
	}

	cmd.Flags().IntVar(&depth, "depth", 3, "search depth")
	cmd.Flags().StringVar(&format, "format", "tree", "output format: tree|mermaid")
	cmd.Flags().IntVar(&maxNodes, "max-nodes", 0, "max nodes limit (0 = unlimited)")

	return cmd
}
