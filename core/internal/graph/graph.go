// Package graph は Analyzer の record から構築した呼び出しグラフを保持し、
// Traversal Engine が探索する読み取り専用の view を提供する。
//
// [Graph] は node を Analyzer Protocol の methodId、有向 edge を edgeId で
// キーに持つ。登録は [Graph.AddNode] / [Graph.AddEdge]、参照は [Graph.Node] と
// [Graph.Neighbors] で行う。
//
// 登録後のデータは変更しない。探索側はこの読み取り API だけに依存し、
// 内部表現には依存しない。内部表現を変えても探索側が壊れないようにするためである。
package graph

// Direction は graph の読み取りがどちらの隣接を辿るかを選ぶ。
type Direction string

const (
	// DirectionCaller は node を呼んでいるメソッドへ向かう edge を辿る。
	DirectionCaller Direction = "caller"
	// DirectionCallee は node が呼んでいるメソッドへ向かう edge を辿る。
	DirectionCallee Direction = "callee"
)

// Node は Analyzer Protocol の methodId で識別される呼び出しグラフの node。
type Node struct {
	ID     string
	Symbol Symbol
}

// SourceLocation は workspace root からの相対でソース範囲を表す。
//
// Analyzer の wire 形式とは独立した graph 固有の値型である。wire record は
// protocol 境界で本型へ変換する。domain 層から wire 表現への import を
// ゼロにするためであり、型の重複は境界隔離のコストとして受け入れる
// (判断の正本は adr/0007-layered-architecture-refactor.md)。
//
// StartColumn / EndLine / EndColumn は optional で、Analyzer が省いた場合は nil。
type SourceLocation struct {
	Path        string
	StartLine   int
	StartColumn *int
	EndLine     *int
	EndColumn   *int
}

// Symbol は [Node] が表すメソッドの情報。Source は optional。
//
// Metadata は Analyzer の record から複製した opaque な JSON object で、
// graph / traversal / output のいずれも key の意味を解釈しない。
// nil は「record に metadata が無かった」、空 map は「明示的に空の object を
// 持っていた」を表す。この差は JSON 出力に現れるため潰さない。
type Symbol struct {
	QualifiedName string
	Signature     string
	Source        *SourceLocation
	Metadata      map[string]any
}

// Edge は Analyzer Protocol の edgeId で識別される有向の呼び出し edge。
// CallerID / CalleeID は [Node] の ID を参照する。
// Metadata は [Symbol.Metadata] と同じく opaque で、nil と空 map を区別する。
type Edge struct {
	ID       string
	CallerID string
	CalleeID string
	CallSite *SourceLocation
	Metadata map[string]any
}

// Graph は in-memory の呼び出しグラフ。ゼロ値は使えない。[New] で作る。
type Graph struct {
	nodes map[string]Node
	edges map[string]Edge
	// outgoing は caller の node ID から、その node が呼ぶ edge への対応 (callee 方向)。
	outgoing map[string][]Edge
	// incoming は callee の node ID から、その node を呼ぶ edge への対応 (caller 方向)。
	incoming map[string][]Edge
}

// New は空の [Graph] を返す。
func New() *Graph {
	return &Graph{
		nodes:    map[string]Node{},
		edges:    map[string]Edge{},
		outgoing: map[string][]Edge{},
		incoming: map[string][]Edge{},
	}
}

// AddNode は node を登録する。
//
// 登録済みの ID は無視する (先勝ち)。Analyzer Protocol が methodId を決定的に
// 生成する契約なので、同じ ID なら同じ node であり、上書きする意味がない。
func (g *Graph) AddNode(n Node) {
	if _, ok := g.nodes[n.ID]; ok {
		return
	}
	g.nodes[n.ID] = n
}

// AddEdge は有向 edge を登録する。登録済みの ID は無視する (先勝ち)。
//
// 両端が登録済み (または後で登録される) node であることは呼び出し側の責任。
// 本関数では検査しない。参照完全性は stream 全体を見ないと判定できず、
// その責務は protocol 層の ACL が持つためである。
func (g *Graph) AddEdge(e Edge) {
	if _, ok := g.edges[e.ID]; ok {
		return
	}
	g.edges[e.ID] = e
	g.outgoing[e.CallerID] = append(g.outgoing[e.CallerID], e)
	g.incoming[e.CalleeID] = append(g.incoming[e.CalleeID], e)
}

// Node は id で登録された node を返す。第 2 戻り値は存在したかどうか。
// ゼロ値で判定せず、必ずこちらを見ること。
func (g *Graph) Node(id string) (Node, bool) {
	n, ok := g.nodes[id]
	return n, ok
}

// Nodes は登録済みの全 node の snapshot を返す。順序は保証しない。
//
// 返す slice は呼び出し側が変更してよい。ただし要素内の pointer / map は
// [Graph.Node] と同じく読み取り専用として扱うこと。
func (g *Graph) Nodes() []Node {
	nodes := make([]Node, 0, len(g.nodes))
	for _, node := range g.nodes {
		nodes = append(nodes, node)
	}
	return nodes
}

// Neighbors は id の node に隣接する edge を dir 方向で返す。
// 未知の ID・葉 node・不正な direction はいずれも空を返す。
//
// 返す slice は graph 内部の隣接情報と共有している。**呼び出し側が変更しては
// ならない。** 複製を返さないのは、探索がホットパスで毎回の確保を避けるため。
func (g *Graph) Neighbors(id string, dir Direction) []Edge {
	switch dir {
	case DirectionCaller:
		return g.incoming[id]
	case DirectionCallee:
		return g.outgoing[id]
	default:
		return nil
	}
}
