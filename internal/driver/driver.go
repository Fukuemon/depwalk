// Package driver provides language-specific driver implementations.
//
// A Driver bundles all language-specific components needed for analysis.
// This allows depwalk to support multiple languages in the future
// (e.g., Kotlin) without changing the pipeline logic.
package driver

import (
	"context"

	"github.com/Fukuemon/depwalk/internal/pipeline"
)

// Driver bundles language-specific implementations.
// For MVP, we only register "java", but this shape allows future additions
// without changing the pipeline signatures.
type Driver struct {
	// Name identifies this driver (e.g., "java", "kotlin").
	Name string

	// Parser extracts AST information (tree-sitter based).
	Parser pipeline.Parser

	// Resolver resolves to stable MethodIDs (JavaParser based).
	Resolver pipeline.Resolver

	// Index provides reverse lookup for callers.
	Index pipeline.Index

	// Cache stores resolution results.
	Cache pipeline.Cache

	// Classpath extracts classpath from build systems.
	Classpath pipeline.ClasspathProvider

	// ResolverStarter is an optional interface for starting the resolver.
	// If set, it will be called before running the pipeline.
	ResolverStarter ResolverLifecycle
}

// ResolverLifecycle provides lifecycle management for resolvers that need it.
type ResolverLifecycle interface {
	Start(ctx context.Context) error
	Stop() error
	SetSourceRoots(roots []string)
	SetJarPath(jarPath string)
}

// Dependencies converts Driver to pipeline.Dependencies.
func (d Driver) Dependencies() pipeline.Dependencies {
	return pipeline.Dependencies{
		Parser:   d.Parser,
		Resolver: d.Resolver,
		Index:    d.Index,
		Cache:    d.Cache,
	}
}

// StartResolver starts the resolver if it implements ResolverLifecycle.
func (d Driver) StartResolver(ctx context.Context, sourceRoots []string, jarPath string) error {
	if d.ResolverStarter != nil {
		d.ResolverStarter.SetSourceRoots(sourceRoots)
		d.ResolverStarter.SetJarPath(jarPath)
		return d.ResolverStarter.Start(ctx)
	}
	return nil
}

// StopResolver stops the resolver if it implements ResolverLifecycle.
func (d Driver) StopResolver() error {
	if d.ResolverStarter != nil {
		return d.ResolverStarter.Stop()
	}
	return nil
}
