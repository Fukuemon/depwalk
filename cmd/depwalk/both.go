package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/Fukuemon/depwalk/internal/driver"
	"github.com/Fukuemon/depwalk/internal/infra/output"
	"github.com/Fukuemon/depwalk/internal/pipeline"
	"github.com/Fukuemon/depwalk/pkg/pathx"
	"github.com/spf13/cobra"
)

func newBothCmd() *cobra.Command {
	var (
		depth    int
		format   string
		maxNodes int
	)

	cmd := &cobra.Command{
		Use:   "both <selector>",
		Short: "Explore both callees and callers from a method",
		Long: `Explore both outgoing calls (callees) and incoming calls (callers) from the specified method.

This command combines the functionality of 'callees' and 'callers' commands,
providing a complete view of method dependencies in both directions.

Selector formats:
  file:line[:col]  - Start from the method containing this position
  file#method      - Start from the named method (must be unambiguous)

Examples:
  depwalk both src/main/java/com/example/Service.java:42
  depwalk both src/main/java/com/example/Service.java#process --depth 3
  depwalk both src/Service.java:10 --format mermaid`,
		Args: cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			ctx := rootContext()
			selectorRaw := args[0]

			// Get language driver
			d, err := driver.Get(rf.lang)
			if err != nil {
				return err
			}

			// Detect project root
			projectRoot := rf.projectRoot
			if projectRoot == "" {
				selectorDir := filepath.Dir(selectorRaw)
				if idx := strings.Index(selectorRaw, ":"); idx > 0 {
					selectorDir = filepath.Dir(selectorRaw[:idx])
				}
				if idx := strings.Index(selectorRaw, "#"); idx > 0 {
					selectorDir = filepath.Dir(selectorRaw[:idx])
				}

				detected, err := pathx.FindProjectRoot(selectorDir, "build.gradle", "build.gradle.kts", "pom.xml", ".git")
				if err != nil {
					detected, err = pathx.FindProjectRoot(".", "build.gradle", "build.gradle.kts", "pom.xml", ".git")
					if err != nil {
						return fmt.Errorf("could not detect project root: %w", err)
					}
				}
				projectRoot = detected
			}

			// Determine source roots
			sourceRoots := []string{
				filepath.Join(projectRoot, "src", "main", "java"),
			}
			if rf.includeTS {
				sourceRoots = append(sourceRoots, filepath.Join(projectRoot, "src", "test", "java"))
			}

			// Find the Java helper jar
			jarPath := findHelperJar(projectRoot)

			// Open cache
			if !rf.noCache {
				if err := d.OpenCache(); err != nil {
					fmt.Fprintf(os.Stderr, "Warning: failed to open cache: %v\n", err)
				}
				defer d.CloseCache()
			}

			// Create Index for callers lookup (needed for both command)
			d.CreateIndex(sourceRoots, rf.includeTS)

			// Start the resolver
			if err := d.StartResolver(ctx, sourceRoots, jarPath); err != nil {
				return fmt.Errorf("failed to start resolver: %w", err)
			}
			defer d.StopResolver()

			// Build pipeline config
			cfg := pipeline.Config{
				Depth:        depth,
				Format:       output.Format(format),
				MaxNodes:     maxNodes,
				Verbose:      rf.verbose,
				ProjectRoot:  projectRoot,
				IncludeTests: rf.includeTS,
				CacheDir:     rf.cacheDir,
				NoCache:      rf.noCache,
			}

			// Create and run pipeline
			deps := d.Dependencies()
			p := pipeline.NewBothPipeline(deps, cfg)
			result, err := p.Run(ctx, selectorRaw)
			if err != nil {
				return err
			}

			fmt.Fprint(cmd.OutOrStdout(), result)
			return nil
		},
	}

	cmd.Flags().IntVar(&depth, "depth", 3, "search depth for each direction")
	cmd.Flags().StringVar(&format, "format", "tree", "output format: tree|mermaid")
	cmd.Flags().IntVar(&maxNodes, "max-nodes", 0, "max nodes limit per direction (0 = unlimited)")

	return cmd
}

