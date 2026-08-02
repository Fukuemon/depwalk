package analyze

import (
	"fmt"

	"github.com/Fukuemon/depwalk/core/internal/graph"
)

// Request は [Source] port へ渡す 1 回の解析を表す。すべて
// field is passed through to the Analyzer request without interpretation;
// the port implementation owns the wire form (request id, schema version,
// validation).
type Request struct {
	WorkspaceRoot string
	SourceRoots   []string
	Language      string
	Include       []string
	Exclude       []string
	Metadata      map[string]any
}

// Outcome は stream の終了後に [Source] port が報告する process 単位の結果。以下は
// record stream ends.
type Outcome struct {
	// Diagnostics は Analyzer が報告した致命的でない診断 (domain 値へ変換済み)。
	Diagnostics     []Diagnostic
	Failure         *AnalyzerFailure
	ValidationError error
	ExitCode        int
}

// Err は run が終わった原因の失敗を返す。正常終了なら nil。
//
// **判定順に意味がある。** Analyzer 側の致命的な結果 (error record または非ゼロ
// exit) を先に見るのは、stream の参照完全性検査が「Analyzer が実際になぜ諦めたか」
// を覆い隠さないようにするためである。呼び出し側は field を直接読まず本メソッドを
// 使うことで、この優先順位を得る。
func (o Outcome) Err() error {
	if o.Failure != nil {
		return o.Failure
	}
	if o.ExitCode != 0 {
		return fmt.Errorf("analyzer process exited with code %d", o.ExitCode)
	}
	if o.ValidationError != nil {
		return fmt.Errorf("analyzer stdout did not follow the analyzer protocol: %w", o.ValidationError)
	}
	return nil
}

// Source は use case が domain 型の解析結果を受け取る port。node と edge は届いた
// 順に callback へ流し、process 単位の結果は stream の終了時に返す。
//
// interface は利用側 (本 package) で定義する。実装は protocol package の ACL
// adapter で、cli が [New] へ注入する。提供側で定義すると use case が wire 表現を
// 知ることになり、依存が逆向きになる。
type Source interface {
	Run(request Request, onNode func(graph.Node), onEdge func(graph.Edge)) (Outcome, error)
}

// Diagnostic は致命的でない Analyzer の診断を domain 値へ写したもの。
// Metadata は opaque で、Core は解釈しない。
type Diagnostic struct {
	Severity        string
	Code            string
	Message         string
	Location        *graph.SourceLocation
	RelatedMethodID string
	Metadata        map[string]any
}
