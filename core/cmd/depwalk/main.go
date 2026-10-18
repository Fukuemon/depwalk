// Command depwalk は、ソースコードの呼び出しグラフを Analyzer で作り、メソッドの
// caller と callee を辿る CLI である。
package main

import (
	"os"

	"github.com/Fukuemon/depwalk/core/internal/cli"
)

func main() {
	if err := cli.Execute(); err != nil {
		os.Exit(cli.ExitCode(err))
	}
}
