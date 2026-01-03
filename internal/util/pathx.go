package util

import (
	"errors"
	"os"
	"path/filepath"
)

// FindProjectRoot walks up from start and finds a directory containing one of the markers.
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
			return "", errors.New("project root not found")
		}
		cur = parent
	}
}

