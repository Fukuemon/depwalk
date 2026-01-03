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

func newCallersCmd() *cobra.Command {
	var (
		depth    int
		format   string
		maxNodes int
	)

	cmd := &cobra.Command{
		Use:   "callers <selector>",
		Short: "Explore callers (incoming calls) recursively",
		Long: `Explore methods that call the specified method, recursively.

Selector formats:
  file:line[:col]  - Start from the method containing this position
  file#method      - Start from the named method (must be unambiguous)

Examples:
  depwalk callers src/main/java/com/example/Service.java:42
  depwalk callers src/main/java/com/example/Service.java#process --depth 5
  depwalk callers src/Service.java:10 --format mermaid`,
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
				// Try to detect from the selector's file path first
				selectorDir := filepath.Dir(selectorRaw)
				if idx := strings.Index(selectorRaw, ":"); idx > 0 {
					selectorDir = filepath.Dir(selectorRaw[:idx])
				}
				if idx := strings.Index(selectorRaw, "#"); idx > 0 {
					selectorDir = filepath.Dir(selectorRaw[:idx])
				}

				detected, err := pathx.FindProjectRoot(selectorDir, "build.gradle", "build.gradle.kts", "pom.xml", ".git")
				if err != nil {
					// Fallback to current directory
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

			// Create Index for callers lookup
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

			// Create and run pipeline with updated dependencies
			deps := d.Dependencies()
			p := pipeline.NewCallersPipeline(deps, cfg)
			result, err := p.Run(ctx, selectorRaw)
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
