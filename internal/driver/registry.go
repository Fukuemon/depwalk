package driver

import (
	"fmt"
	"sync"
)

var (
	mu       sync.RWMutex
	registry = map[string]Driver{}
)

// Register registers a driver with the given name.
func Register(name string, d Driver) {
	mu.Lock()
	defer mu.Unlock()
	d.Name = name
	registry[name] = d
}

// Get retrieves a driver by name.
func Get(name string) (Driver, error) {
	mu.RLock()
	defer mu.RUnlock()
	d, ok := registry[name]
	if !ok {
		return Driver{}, fmt.Errorf("unknown language driver: %s", name)
	}
	return d, nil
}

// List returns all registered driver names.
func List() []string {
	mu.RLock()
	defer mu.RUnlock()
	names := make([]string, 0, len(registry))
	for name := range registry {
		names = append(names, name)
	}
	return names
}

