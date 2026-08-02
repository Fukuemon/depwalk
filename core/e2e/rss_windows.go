//go:build windows

package e2e

import "os"

// maxRSS は Windows では取得できない。os.ProcessState.SysUsage() が比較可能な
// *syscall.Rusage を返さないため。
func maxRSS(state *os.ProcessState) (int64, bool) {
	return 0, false
}
