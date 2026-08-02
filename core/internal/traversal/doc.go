// Package traversal は [graph.Graph] が持つ呼び出しグラフ上で
// caller / callee の到達可能性を計算する。
//
// 結果の契約は「どう辿ったか」ではなく graph の性質として定義する
// (design/features/traversal/DesignDoc_traversal.md)。到達 node 集合は、
// 起点からの最短距離 (minDepth、起点は 0) が深さ上限以内である node すべてである。
//
// [Request] の Order は受け取って検証するだけで、探索にも結果にも影響しない。
// 到達集合が上記のとおり順序に依存しないため、走査は最短距離の 1 本で足りる。
package traversal
