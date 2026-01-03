// Package cache implements the Cache interface for persisting resolution results using bbolt.
package cache

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	bolt "go.etcd.io/bbolt"

	"github.com/Fukuemon/depwalk/internal/model"
)

var (
	bucketResolvedDecls = []byte("resolved_decls")
	bucketFileHashes    = []byte("file_hashes")
)

// Cache persists resolution results to avoid redundant Java helper calls.
// Uses bbolt for embedded key-value storage.
type Cache struct {
	dir string
	db  *bolt.DB
}

// NewCache creates a new cache instance.
func NewCache(dir string) *Cache {
	return &Cache{dir: dir}
}

// Open opens the cache database. Must be called before using the cache.
func (c *Cache) Open() error {
	if err := os.MkdirAll(c.dir, 0755); err != nil {
		return fmt.Errorf("failed to create cache directory: %w", err)
	}

	dbPath := filepath.Join(c.dir, "cache.db")
	db, err := bolt.Open(dbPath, 0600, nil)
	if err != nil {
		return fmt.Errorf("failed to open cache database: %w", err)
	}
	c.db = db

	// Create buckets
	return c.db.Update(func(tx *bolt.Tx) error {
		if _, err := tx.CreateBucketIfNotExists(bucketResolvedDecls); err != nil {
			return err
		}
		if _, err := tx.CreateBucketIfNotExists(bucketFileHashes); err != nil {
			return err
		}
		return nil
	})
}

// Close closes the cache database.
func (c *Cache) Close() error {
	if c.db != nil {
		return c.db.Close()
	}
	return nil
}

// GetResolvedDecl retrieves a cached MethodID for a DeclRange.
// Returns empty string and false if not found or if file has changed.
func (c *Cache) GetResolvedDecl(ctx context.Context, key model.DeclRange) (model.MethodID, bool, error) {
	if c.db == nil {
		return "", false, nil
	}

	// Check if file has changed
	currentHash, err := c.computeFileHash(key.File)
	if err != nil {
		return "", false, nil // File not accessible, skip cache
	}

	var methodID model.MethodID
	var found bool

	err = c.db.View(func(tx *bolt.Tx) error {
		// Check stored file hash
		hashBucket := tx.Bucket(bucketFileHashes)
		storedHash := hashBucket.Get([]byte(key.File))
		if storedHash == nil || string(storedHash) != currentHash {
			// File changed or not cached
			return nil
		}

		// Get cached value
		declBucket := tx.Bucket(bucketResolvedDecls)
		cacheKey := formatDeclKey(key)
		value := declBucket.Get([]byte(cacheKey))
		if value != nil {
			methodID = model.MethodID(value)
			found = true
		}
		return nil
	})

	return methodID, found, err
}

// PutResolvedDecl stores a resolved MethodID for a DeclRange.
func (c *Cache) PutResolvedDecl(ctx context.Context, key model.DeclRange, val model.MethodID) error {
	if c.db == nil {
		return nil
	}

	// Compute current file hash
	currentHash, err := c.computeFileHash(key.File)
	if err != nil {
		return nil // File not accessible, skip caching
	}

	return c.db.Update(func(tx *bolt.Tx) error {
		// Store file hash
		hashBucket := tx.Bucket(bucketFileHashes)
		if err := hashBucket.Put([]byte(key.File), []byte(currentHash)); err != nil {
			return err
		}

		// Store resolved decl
		declBucket := tx.Bucket(bucketResolvedDecls)
		cacheKey := formatDeclKey(key)
		return declBucket.Put([]byte(cacheKey), []byte(val))
	})
}

// Invalidate removes cached entries for files that have changed.
func (c *Cache) Invalidate(ctx context.Context, files []string) error {
	if c.db == nil {
		return nil
	}

	return c.db.Update(func(tx *bolt.Tx) error {
		hashBucket := tx.Bucket(bucketFileHashes)
		declBucket := tx.Bucket(bucketResolvedDecls)

		for _, file := range files {
			// Remove file hash
			if err := hashBucket.Delete([]byte(file)); err != nil {
				return err
			}

			// Remove all decl entries for this file
			// We need to iterate and find keys starting with the file path
			prefix := file + ":"
			cursor := declBucket.Cursor()
			for k, _ := cursor.Seek([]byte(prefix)); k != nil && strings.HasPrefix(string(k), prefix); k, _ = cursor.Next() {
				if err := cursor.Delete(); err != nil {
					return err
				}
			}
		}
		return nil
	})
}

// InvalidateAll clears all cached data.
func (c *Cache) InvalidateAll(ctx context.Context) error {
	if c.db == nil {
		return nil
	}

	return c.db.Update(func(tx *bolt.Tx) error {
		// Delete and recreate buckets
		if err := tx.DeleteBucket(bucketResolvedDecls); err != nil && err != bolt.ErrBucketNotFound {
			return err
		}
		if err := tx.DeleteBucket(bucketFileHashes); err != nil && err != bolt.ErrBucketNotFound {
			return err
		}
		if _, err := tx.CreateBucket(bucketResolvedDecls); err != nil {
			return err
		}
		if _, err := tx.CreateBucket(bucketFileHashes); err != nil {
			return err
		}
		return nil
	})
}

// formatDeclKey creates a cache key from a DeclRange.
func formatDeclKey(d model.DeclRange) string {
	return fmt.Sprintf("%s:%d:%d", d.File, d.StartByte, d.EndByte)
}

// computeFileHash computes SHA256 hash of a file's contents.
func (c *Cache) computeFileHash(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()

	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}

	return hex.EncodeToString(h.Sum(nil)), nil
}
