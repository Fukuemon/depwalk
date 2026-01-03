package main

import (
	"github.com/Fukuemon/depwalk/internal/app"
	"github.com/Fukuemon/depwalk/internal/lang"
	"github.com/spf13/cobra"
)

func newCallersCmd() *cobra.Command {
	var cf commonFlags
	cmd := &cobra.Command{
		Use:   "callers <selector>",
		Short: "Explore callers (incoming calls) recursively",
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
			return app.Run(rootContext(), "callers", selectorRaw, cfg, deps)
		},
	}
	addCommonFlags(cmd, &cf)
	return cmd
}


