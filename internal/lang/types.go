package lang

import "github.com/Fukuemon/depwalk/internal/ports"

// Driver bundles language-specific implementations.
// For MVP, we only register "java", but this shape allows future additions
// without changing the app/usecase signatures.
type Driver struct {
	Parser   ports.Parser
	Resolver ports.Resolver
	Index    ports.Index
	Renderer ports.Renderer
	Cache    ports.Cache

	// Optional per-language classpath strategy.
	Classpath ports.ClasspathProvider
}


