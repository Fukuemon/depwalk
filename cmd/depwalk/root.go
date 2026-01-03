package main

import (
	"context"
	"fmt"
	"os"

	"github.com/Fukuemon/depwalk/internal/driver"
	"github.com/spf13/cobra"
)

type rootFlags struct {
	lang        string
	verbose     bool
	projectRoot string
	cacheDir    string
	noCache     bool
	includeTS   bool
}

var rf rootFlags

func newRootCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "depwalk",
		Short: "depwalk: callers/callees explorer for Java/Spring projects",
		Long: `depwalk analyzes Java/Spring Boot projects to explore method call dependencies.

It uses tree-sitter for fast AST traversal and JavaParser for strict type resolution,
providing accurate caller/callee graphs even with overloading and inheritance.

Examples:
  # Explore callees from a specific line
  depwalk callees src/main/java/com/example/FooService.java:42 --depth 3

  # Explore callers of a method
  depwalk callers src/main/java/com/example/FooService.java#doThing --format mermaid

  # Include test sources
  depwalk callees src/main/java/Service.java:10 --include-tests`,
	}

	cmd.PersistentFlags().StringVar(&rf.lang, "lang", "java", "language driver (currently only 'java')")
	cmd.PersistentFlags().BoolVar(&rf.verbose, "verbose", false, "verbose logging")
	cmd.PersistentFlags().StringVar(&rf.projectRoot, "project-root", "", "project root (auto-detect if empty)")
	cmd.PersistentFlags().StringVar(&rf.cacheDir, "cache-dir", ".depwalk", "cache directory")
	cmd.PersistentFlags().BoolVar(&rf.noCache, "no-cache", false, "disable cache")
	cmd.PersistentFlags().BoolVar(&rf.includeTS, "include-tests", false, "include src/test/* in scan")

	cmd.AddCommand(newCalleesCmd())
	cmd.AddCommand(newCallersCmd())
	return cmd
}

// Execute runs the CLI.
func Execute() {
	root := newRootCmd()
	root.SetOut(os.Stdout)
	root.SetErr(os.Stderr)

	// Register language drivers.
	driver.RegisterDefaults()

	if err := root.Execute(); err != nil {
		_, _ = fmt.Fprintln(os.Stderr, err.Error())
		os.Exit(1)
	}
}

func rootContext() context.Context {
	return context.Background()
}
