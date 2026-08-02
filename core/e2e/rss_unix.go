//go:build !windows

package e2e

import (
	"os"
	"syscall"
)

// maxRSS は OS が報告するプロセスの最大 RSS を返す
// (getrusage(2) RUSAGE_CHILDREN を os.ProcessState.SysUsage() 経由で取得)。
// 取得できなければ false。
//
// **単位は platform 依存である。** Linux は kilobyte、Darwin は byte を返す。
// 正規化せずに platform をまたいで生の値を比較してはならない。
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
