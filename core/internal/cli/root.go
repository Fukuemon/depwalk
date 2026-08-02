package cli

import (
	"errors"

	"github.com/spf13/cobra"

	"github.com/Fukuemon/depwalk/core/internal/analyze"
)

func newRootCommand() *cobra.Command {
	root := &cobra.Command{
		Use:   "depwalk",
		Short: "Analyze dependency paths in source code",
	}
	root.AddCommand(newAnalyzeCommand())
	return root
}

// Execute はプロセス引数に対して depwalk の root command を実行する。
// 返る error は Cobra が既に stderr へ描画済み。呼び出し側は [ExitCode] で
// プロセスの終了状態へ写す。
func Execute() error {
	return newRootCommand().Execute()
}

// ExitCode はコマンドの結果を exit code の契約へ写す。成功は 0、利用者が渡した
// 値が原因の失敗は 2、それ以外の実行時失敗は 1。
//
// 入力の誤りは 2 つの型で報告される。ここで弾いた flag 値と、analyze use case が
// 弾いた値 (未知・曖昧な method selector、不正な要求) である。両方を 2 と決める
// のは本関数の仕事であり、どちらの package も exit code を知る必要はない。
func ExitCode(err error) int {
	if err == nil {
		return 0
	}
	var flagErr *inputError
	var useCaseErr *analyze.InputError
	if errors.As(err, &flagErr) || errors.As(err, &useCaseErr) {
		return 2
	}
	return 1
}
