package driver

import (
	"github.com/Fukuemon/depwalk/internal/infra/cache"
	"github.com/Fukuemon/depwalk/internal/infra/gradle"
	"github.com/Fukuemon/depwalk/internal/infra/javahelper"
	"github.com/Fukuemon/depwalk/internal/infra/treesitter"
)

// RegisterJava registers the Java language driver.
// This is the default driver for depwalk MVP.
func RegisterJava() {
	// Note: Components are created with default/empty config.
	// In practice, these will be initialized with proper config
	// when the pipeline runs (e.g., classpath from gradle).
	resolver := javahelper.NewResolver("")

	d := Driver{
		Name:            "java",
		Parser:          treesitter.NewParser(),
		Resolver:        resolver,
		Index:           nil, // TODO: implement
		Cache:           cache.NewCache(".depwalk"),
		Classpath:       gradle.NewClasspathProvider(),
		ResolverStarter: resolver, // javahelper.Resolver implements ResolverLifecycle
	}
	Register("java", d)
}

// RegisterDefaults registers all default language drivers.
func RegisterDefaults() {
	RegisterJava()
}
