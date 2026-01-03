package pipeline

import "github.com/Fukuemon/depwalk/internal/infra/output"

// Config holds pipeline execution configuration.
type Config struct {
	// Depth is the maximum traversal depth.
	Depth int

	// Format specifies the output format (tree, mermaid).
	Format output.Format

	// MaxNodes limits the number of nodes to prevent runaway exploration.
	// 0 means unlimited.
	MaxNodes int

	// Verbose enables detailed logging.
	Verbose bool

	// ProjectRoot is the root directory of the project.
	ProjectRoot string

	// IncludeTests includes test sources in scanning.
	IncludeTests bool

	// CacheDir is the directory for cache files.
	CacheDir string

	// NoCache disables caching.
	NoCache bool
}

// DefaultConfig returns a Config with sensible defaults.
func DefaultConfig() Config {
	return Config{
		Depth:        3,
		Format:       output.FormatTree,
		MaxNodes:     0,
		Verbose:      false,
		ProjectRoot:  "",
		IncludeTests: false,
		CacheDir:     ".depwalk",
		NoCache:      false,
	}
}

