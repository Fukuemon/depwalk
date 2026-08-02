package traversal

import (
	"fmt"

	"github.com/Fukuemon/depwalk/core/internal/graph"
)

// Order は走査の訪問順を選ぶ。[Result] には影響しない。
type Order string

const (
	// OrderBFS は幅優先で展開する。既定値。
	OrderBFS Order = "bfs"
	// OrderDFS は深さ優先で展開する。
	OrderDFS Order = "dfs"
)

// Status は探索が起点 node を見つけられたかを表す。
type Status string

const (
	// StatusOK は起点が存在し、結果が埋まっていることを表す。
	StatusOK Status = "ok"
	// StatusStartNotFound は起点が無かったことを表す (graph が空の場合を含む)。
	// error ではなく正常な「該当なし」として扱う。
	StatusStartNotFound Status = "startNotFound"
)

// Request は 1 回の探索を表す。
type Request struct {
	// StartID は起点 node の methodId。
	StartID string
	// Direction は caller / callee のどちらの到達可能性を見るかを選ぶ。
	Direction graph.Direction
	// MaxDepth は到達集合を minDepth <= MaxDepth の node に限る。
	// nil は無制限。0 なら起点だけが残る。
	MaxDepth *int
	// Order は訪問順を選ぶ。空なら [OrderBFS]。
	Order Order
}

// Result は探索の結果。Nodes と Edges は到達した誘導部分グラフを ID をキーとする
// 集合として持ち、順序は保証しない。
type Result struct {
	Status Status
	// Nodes は到達 node の ID 集合 (minDepth <= maxDepth)。起点を含む。
	Nodes map[string]bool
	// Depths は到達 node ごとの起点からの最短距離。キーは Nodes と同じ。
	Depths map[string]int
	// Edges は誘導部分グラフ。両端が到達 node である探索方向の edge をすべて含む。
	// 合流 edge も閉路の edge も除外しない。
	Edges map[string]graph.Edge
	// Cycles は Edges のうち到達部分グラフ内で閉路を構成するものに注釈を付ける
	// (self-loop または同一 SCC)。注釈しても Edges から外さない。実在する
	// 呼び出し関係であり、落とすと網羅性が崩れるため。
	Cycles map[string]bool
	// DepthCutoffs は到達 node から深さ上限の外の node へ向かう edge を
	// edge ID をキーに記録する。これらは Edges には含めない。
	DepthCutoffs map[string]DepthCutoff
}

// DepthCutoff は深さ上限で切られた edge と、その接続先 node の minDepth。
type DepthCutoff struct {
	Edge graph.Edge
	// TargetMinDepth は探索方向の接続先 node の minDepth (常に maxDepth より大きい)。
	TargetMinDepth int
}

// Traverse は g を 1 回探索し、到達した誘導部分グラフを返す。
//
// 不正な request は走査前に error にし、[Result] はゼロ値を返す (使ってはならない)。
// 起点が見つからない場合は error にせず、空の [Result] と
// [StatusStartNotFound] を返す。解析結果として「該当なし」は正常だからである。
//
// 到達集合は最短距離の走査 ([minDepths]) 1 本から導く。契約が順序に依存しない
// 以上、別の走査から導いても一致するか、静かに食い違うかのどちらかにしかならない。
// このため Order は検証するだけで走査を変えない。BFS / DFS の走査自体は
// [visitOrder] にあるが、本関数からは呼ばない。
func Traverse(g *graph.Graph, req Request) (Result, error) {
	if err := validate(req); err != nil {
		return Result{}, err
	}
	if _, ok := g.Node(req.StartID); !ok {
		return Result{
			Status:       StatusStartNotFound,
			Nodes:        map[string]bool{},
			Depths:       map[string]int{},
			Edges:        map[string]graph.Edge{},
			Cycles:       map[string]bool{},
			DepthCutoffs: map[string]DepthCutoff{},
		}, nil
	}

	depths := minDepths(g, req.StartID, req.Direction)
	nodes := make(map[string]bool, len(depths))
	for id, d := range depths {
		if req.MaxDepth != nil && d > *req.MaxDepth {
			continue
		}
		nodes[id] = true
	}
	return buildResult(g, req.Direction, nodes, depths), nil
}

func validate(req Request) error {
	switch req.Direction {
	case graph.DirectionCaller, graph.DirectionCallee:
	default:
		return fmt.Errorf("traversal: invalid direction %q: want %q or %q", req.Direction, graph.DirectionCaller, graph.DirectionCallee)
	}
	if req.MaxDepth != nil && *req.MaxDepth < 0 {
		return fmt.Errorf("traversal: invalid maxDepth %d: want unset or >= 0", *req.MaxDepth)
	}
	switch req.Order {
	case "", OrderBFS, OrderDFS:
	default:
		return fmt.Errorf("traversal: invalid order %q: want %q or %q", req.Order, OrderBFS, OrderDFS)
	}
	return nil
}
