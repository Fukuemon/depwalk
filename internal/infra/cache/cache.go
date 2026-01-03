// Package cache implements the Cache interface for persisting resolution results.
package cache

import (
	"context"

	"github.com/Fukuemon/depwalk/internal/model"
)

// Cache persists resolution results to avoid redundant Java helper calls.
type Cache struct {
	dir string
	// TODO: Add SQLite or file-based storage
}

// NewCache creates a new cache instance.
func NewCache(dir string) *Cache {
	return &Cache{dir: dir}
}

// GetResolvedDecl retrieves a cached MethodID for a DeclRange.
func (c *Cache) GetResolvedDecl(ctx context.Context, key model.DeclRange) (model.MethodID, bool, error) {
	// TODO: Implement SQLite lookup
	// Key: (file, startByte, endByte) -> MethodID
	return "", false, nil
}

// PutResolvedDecl stores a resolved MethodID for a DeclRange.
func (c *Cache) PutResolvedDecl(ctx context.Context, key model.DeclRange, val model.MethodID) error {
	// TODO: Implement SQLite insert
	return nil
}

// Invalidate removes cached entries for files that have changed.
func (c *Cache) Invalidate(ctx context.Context, files []string) error {
	// TODO: Implement invalidation by file hash
	return nil
}
