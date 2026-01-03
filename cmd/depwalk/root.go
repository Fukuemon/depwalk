package main

import (
	"context"
	"fmt"
	"os"

	"github.com/Fukuemon/depwalk/internal/lang"
	"github.com/spf13/cobra"
)

type rootFlags struct {
	lang      string
	verbose   bool
	project   string
	cacheDir  string
	noCache   bool
	includeTS bool
}

var rf rootFlags

func newRootCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "depwalk",
		Short: "depwalk: callers/callees explorer for Java/Spring projects",
	}

	cmd.PersistentFlags().StringVar(&rf.lang, "lang", "java", "language driver (currently only 'java')")
	cmd.PersistentFlags().BoolVar(&rf.verbose, "verbose", false, "verbose logging")
	cmd.PersistentFlags().StringVar(&rf.project, "project-root", "", "project root (auto-detect if empty)")
	cmd.PersistentFlags().StringVar(&rf.cacheDir, "cache-dir", ".depwalk", "cache directory")
	cmd.PersistentFlags().BoolVar(&rf.noCache, "no-cache", false, "disable cache")
	cmd.PersistentFlags().BoolVar(&rf.includeTS, "include-tests", false, "include src/test/* in scan")

	cmd.AddCommand(newCalleesCmd())
	cmd.AddCommand(newCallersCmd())
	return cmd
}

func Execute() {
	root := newRootCmd()
	root.SetOut(os.Stdout)
	root.SetErr(os.Stderr)

	// Wire language registry (Java only for now).
	lang.RegisterDefaults()

	if err := root.Execute(); err != nil {
		// Cobra usually prints usage on arg errors; keep stderr concise.
		_, _ = fmt.Fprintln(os.Stderr, err.Error())
		os.Exit(1)
	}
}

func rootContext() context.Context { return context.Background() }
