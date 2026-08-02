package output

import (
	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/traversal"
)

// Format は出力表現を識別する。
type Format string

const (
	// FormatConsole は人が読む console 出力。
	FormatConsole Format = "console"
	// FormatJSON は機械処理向けの JSON 出力。
	FormatJSON Format = "json"
)

// Input は View を組み立てるのに要る graph・探索結果・要求をまとめたもの。
type Input struct {
	Graph   *graph.Graph
	Result  traversal.Result
	Request traversal.Request
}

// View は symbol を解決し順序を確定させた、formatter 共通の入力。
//
// 各 formatter が個別に解決すると、形式ごとに順序や欠損の扱いがずれる。
type View struct {
	Status    traversal.Status
	Direction graph.Direction
	Start     NodeView
	Nodes     []NodeView
	Edges     []EdgeView
	Cutoffs   []CutoffView
}

// NodeView は到達した 1 メソッド。symbol が取れない場合、各 field は空になる。
type NodeView struct {
	ID            string
	QualifiedName string
	Signature     string
	Source        *graph.SourceLocation
	MinDepth      int
	Metadata      map[string]any
}

// EdgeView は到達した 1 本の呼び出し edge。
type EdgeView struct {
	ID       string
	CallerID string
	CalleeID string
	Cycle    bool
	CallSite *graph.SourceLocation
	Metadata map[string]any
}

// CutoffView は深さ上限で除外された 1 本の呼び出し edge。
type CutoffView struct {
	EdgeID         string
	CallerID       string
	CalleeID       string
	TargetMethodID string
	TargetMinDepth int
	CallSite       *graph.SourceLocation
}
