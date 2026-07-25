//go:build !windows

package e2e

import (
	"os"
	"syscall"
)

// maxRSS returns the process's maximum resident set size as reported by the
// OS (getrusage(2) RUSAGE_CHILDREN, via os.ProcessState.SysUsage()), or
// false if unavailable.
//
// The unit is platform-dependent: Linux reports kilobytes, Darwin reports
// bytes. Callers must not compare raw values across platforms without
// normalizing first.
func maxRSS(state *os.ProcessState) (int64, bool) {
	if state == nil {
		return 0, false
	}
	rusage, ok := state.SysUsage().(*syscall.Rusage)
	if !ok {
		return 0, false
	}
	return int64(rusage.Maxrss), true
}
