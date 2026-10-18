// Package graphtest は [graph.Graph] を読むコードのテスト用に、呼び出しグラフの
// fixture を組み立てる。
//
// graph 本体ではなく別 package に置いてある。本番の graph API を depwalk 自身が
// 必要とする分 ([graph.Graph.AddNode] / [graph.Graph.AddEdge]) に留めるためで、
// テストの都合で本番 API を太らせない。本番コードから import してはならない。
package graphtest

import "github.com/Fukuemon/depwalk/core/internal/graph"

// Builder は [graph.Graph] を組み立てる。ゼロ値は使えない。[NewBuilder] で作る。
type Builder struct {
	g *graph.Graph
}

// NewBuilder は空の graph を組み立てる [Builder] を返す。
func NewBuilder() *Builder {
	return &Builder{g: graph.New()}
}

// Node は symbol を持たない node を登録する。登録済みの id なら何もしない。
func (b *Builder) Node(id string) *Builder {
	b.g.AddNode(graph.Node{ID: id})
	return b
}

// NodeWithSymbol は symbol を持つ node を登録する。
//
// [graph.Graph.AddNode] と同じく先に登録した node が勝つ。[Builder.Edge] が
// 自動で足した node の後に呼ぶと symbol は入らないので、edge より先に呼ぶ。
func (b *Builder) NodeWithSymbol(id string, symbol graph.Symbol) *Builder {
	b.g.AddNode(graph.Node{ID: id, Symbol: symbol})
	return b
}

// Edge は有向 edge を登録する。
//
// 未登録の両端 node は自動で足す。テストが edge の列挙だけで graph を
// 書けるようにするためで、node の事前登録を強いると fixture が冗長になる。
func (b *Builder) Edge(id, callerID, calleeID string) *Builder {
	return b.EdgeWithCallSite(id, callerID, calleeID, nil)
}

// EdgeWithCallSite は callSite を持つ有向 edge を登録する。両端の node の扱いは
// [Builder.Edge] と同じ。
func (b *Builder) EdgeWithCallSite(id, callerID, calleeID string, callSite *graph.SourceLocation) *Builder {
	b.g.AddNode(graph.Node{ID: callerID})
	b.g.AddNode(graph.Node{ID: calleeID})
	b.g.AddEdge(graph.Edge{ID: id, CallerID: callerID, CalleeID: calleeID, CallSite: callSite})
	return b
}

// Build は組み立てた graph を返す。返した後も同じ graph を共有するので、Build の
// 後に Builder へ登録した node と edge も返した graph に入る。
func (b *Builder) Build() *graph.Graph {
	return b.g
}
