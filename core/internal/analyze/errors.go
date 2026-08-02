package analyze

import (
	"fmt"

	"github.com/Fukuemon/depwalk/core/internal/graph"
)

// AnalyzerFailure は Analyzer が致命的な error record を報告したときに
// [Runner.Run] が返す。
//
// 構造化された失敗をそのまま保つ (top-level の code / message / source location /
// opaque metadata / 順序付きの details)。Analyzer 固有の code や metadata の key は
// 解釈しない。解釈すると Core が言語固有の知識を持つことになる。
type AnalyzerFailure struct {
	Code     string
	Message  string
	Location *graph.SourceLocation
	Metadata map[string]any
	Details  []FailureDetail
}

// FailureDetail は致命的な Analyzer 失敗の、言語に依存しない構造化された明細 1 件。
type FailureDetail struct {
	Code     string
	Message  string
	Location *graph.SourceLocation
	Metadata map[string]any
}

func (e *AnalyzerFailure) Error() string {
	return fmt.Sprintf("analyzer reported a fatal error: %s: %s", e.Code, e.Message)
}

// InputError は解析要求や method query に渡された値が原因の error を標識する。
// CLI 側が exit code 2 の失敗を実行時失敗と区別するために使う。
// error 文字列を読んで分類しなくて済むようにするためである。
type InputError struct {
	Err error
}

func (e *InputError) Error() string { return e.Err.Error() }

func (e *InputError) Unwrap() error { return e.Err }
