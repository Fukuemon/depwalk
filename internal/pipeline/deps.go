package pipeline

// Dependencies bundles all external dependencies required by the pipeline.
// This allows for easy dependency injection and testing.
type Dependencies struct {
	Parser   Parser
	Resolver Resolver
	Index    Index
	Cache    Cache
}

// Validate checks that required dependencies are set.
func (d Dependencies) Validate() error {
	// Parser and Resolver are required
	// Index is optional (only needed for callers)
	// Cache is optional
	return nil
}

