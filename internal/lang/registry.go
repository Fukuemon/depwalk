package lang

import (
	"fmt"
	"sync"
)

var (
	mu       sync.RWMutex
	registry = map[string]Driver{}
)

func Register(name string, d Driver) {
	mu.Lock()
	defer mu.Unlock()
	registry[name] = d
}

func Get(name string) (Driver, error) {
	mu.RLock()
	defer mu.RUnlock()
	d, ok := registry[name]
	if !ok {
		return Driver{}, fmt.Errorf("unknown language driver: %s", name)
	}
	return d, nil
}

func RegisterDefaults() {
	// Java driver is registered here. Currently, adapters are stubs and app.Run will
	// return a clear error until wiring is implemented.
	Register("java", Driver{})
}


