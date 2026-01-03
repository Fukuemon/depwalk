package driver

import (
	"github.com/Fukuemon/depwalk/internal/infra/cache"
	"github.com/Fukuemon/depwalk/internal/infra/gradle"
	"github.com/Fukuemon/depwalk/internal/infra/index"
	"github.com/Fukuemon/depwalk/internal/infra/javahelper"
	"github.com/Fukuemon/depwalk/internal/infra/treesitter"
	"github.com/Fukuemon/depwalk/internal/pipeline"
)

// RegisterJava registers the Java language driver.
// This is the default driver for depwalk MVP.
func RegisterJava() {
	// Note: Components are created with default/empty config.
	// In practice, these will be initialized with proper config
	// when the pipeline runs (e.g., classpath from gradle).
	resolver := javahelper.NewResolver("")
	cacheInstance := cache.NewCache(".depwalk")

	d := Driver{
		Name:            "java",
		Parser:          treesitter.NewParser(),
		Resolver:        resolver,
		Index:           nil, // Created dynamically via IndexFactory
		Cache:           cacheInstance,
		Classpath:       gradle.NewClasspathProvider(),
		ResolverStarter: resolver,
		CacheManager:    cacheInstance,
		IndexFactory: func(sourceRoots []string, includeTests bool) pipeline.Index {
			return index.NewCallNameIndex(sourceRoots, includeTests)
		},
	}
	Register("java", d)
}

// RegisterDefaults registers all default language drivers.
func RegisterDefaults() {
	RegisterJava()
}
