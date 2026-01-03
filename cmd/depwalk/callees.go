package main

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/Fukuemon/depwalk/internal/driver"
	"github.com/Fukuemon/depwalk/internal/infra/output"
	"github.com/Fukuemon/depwalk/internal/pipeline"
	"github.com/Fukuemon/depwalk/pkg/pathx"
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
				detected, err := pathx.FindProjectRoot(".")
				if err != nil {
					return fmt.Errorf("could not detect project root: %w", err)
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
			p := pipeline.NewCalleesPipeline(d.Dependencies(), cfg)
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

// findHelperJar looks for the depwalk-helper jar in common locations.
func findHelperJar(projectRoot string) string {
	candidates := []string{
		filepath.Join(projectRoot, "java", "depwalk-helper", "build", "libs", "depwalk-helper-0.1.0-all.jar"),
		filepath.Join(projectRoot, "build", "libs", "depwalk-helper-0.1.0-all.jar"),
		"java/depwalk-helper/build/libs/depwalk-helper-0.1.0-all.jar",
	}

	for _, c := range candidates {
		if _, err := os.Stat(c); err == nil {
			return c
		}
	}

	return ""
}
