// Package pathx provides path utilities for project discovery.
package pathx

import (
	"errors"
	"os"
	"path/filepath"
)

// ErrNotFound is returned when a project root cannot be found.
var ErrNotFound = errors.New("project root not found")

// FindProjectRoot walks up from start and finds a directory containing one of the markers.
// Common markers include "go.mod", "build.gradle", "pom.xml", ".git", etc.
func FindProjectRoot(start string, markers ...string) (string, error) {
	if start == "" {
		return "", errors.New("start path is empty")
	}
	cur, err := filepath.Abs(start)
	if err != nil {
		return "", err
	}
	for {
		for _, m := range markers {
			if _, err := os.Stat(filepath.Join(cur, m)); err == nil {
				return cur, nil
			}
		}
		parent := filepath.Dir(cur)
		if parent == cur {
			return "", ErrNotFound
		}
		cur = parent
	}
}

