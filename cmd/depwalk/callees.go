package main

import (
	"github.com/Fukuemon/depwalk/internal/app"
	"github.com/Fukuemon/depwalk/internal/lang"
	"github.com/spf13/cobra"
)

type commonFlags struct {
	depth    int
	format   string
	maxNodes int
}

func addCommonFlags(cmd *cobra.Command, cf *commonFlags) {
	cmd.Flags().IntVar(&cf.depth, "depth", 3, "search depth")
	cmd.Flags().StringVar(&cf.format, "format", "tree", "output format: tree|mermaid")
	cmd.Flags().IntVar(&cf.maxNodes, "max-nodes", 0, "max nodes limit (0 = unlimited)")
}

func newCalleesCmd() *cobra.Command {
	var cf commonFlags
	cmd := &cobra.Command{
		Use:   "callees <selector>",
		Short: "Explore callees (outgoing calls) recursively",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			selectorRaw := args[0]

			driver, err := lang.Get(rf.lang)
			if err != nil {
				return err
			}
			cfg := app.RunConfig{Depth: cf.depth, Format: cf.format, Verbose: rf.verbose, MaxNodes: cf.maxNodes}
			deps := app.Dependencies{
				Parser:   driver.Parser,
				Resolver: driver.Resolver,
				Index:    driver.Index,
				Renderer: driver.Renderer,
				Cache:    driver.Cache,
			}
			return app.Run(rootContext(), "callees", selectorRaw, cfg, deps)
		},
	}
	addCommonFlags(cmd, &cf)
	return cmd
}
