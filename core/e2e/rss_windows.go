//go:build windows

package e2e

import "os"

// maxRSS is unavailable on Windows: os.ProcessState.SysUsage() does not
// expose a comparable *syscall.Rusage there.
func maxRSS(state *os.ProcessState) (int64, bool) {
	return 0, false
}
