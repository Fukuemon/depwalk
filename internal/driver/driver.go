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

	// CacheManager is an optional interface for cache lifecycle.
	CacheManager CacheLifecycle

	// IndexFactory creates an Index for the given source roots.
	IndexFactory IndexFactory
}

// ResolverLifecycle provides lifecycle management for resolvers that need it.
type ResolverLifecycle interface {
	Start(ctx context.Context) error
	Stop() error
	SetSourceRoots(roots []string)
	SetJarPath(jarPath string)
}

// CacheLifecycle provides lifecycle management for caches.
type CacheLifecycle interface {
	Open() error
	Close() error
}

// IndexFactory creates an Index for the given configuration.
type IndexFactory func(sourceRoots []string, includeTests bool) pipeline.Index

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

// OpenCache opens the cache if it implements CacheLifecycle.
func (d Driver) OpenCache() error {
	if d.CacheManager != nil {
		return d.CacheManager.Open()
	}
	return nil
}

// CloseCache closes the cache if it implements CacheLifecycle.
func (d Driver) CloseCache() error {
	if d.CacheManager != nil {
		return d.CacheManager.Close()
	}
	return nil
}

// CreateIndex creates an Index using the IndexFactory.
func (d *Driver) CreateIndex(sourceRoots []string, includeTests bool) {
	if d.IndexFactory != nil {
		d.Index = d.IndexFactory(sourceRoots, includeTests)
	}
}
