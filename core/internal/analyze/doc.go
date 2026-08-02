// Package analyze は depwalk analyze の use case を組み立てる。[Request] を作り、
// [Source] port から domain 型の解析結果を受け取り、[graph.Graph] へ組み上げ、
// method query の探索を実行する。
//
// 本 package は言語に依存しない。language と analysisRequest.metadata は解釈せず
// そのまま通す。wire 表現にも依存しない。Analyzer Protocol の DTO も Analyzer の
// 起動コマンドもここには現れない。前者の実装は protocol、後者の解決は cli が持ち、
// コンポジションルートが両者を配線する。
//
// この分離があるため、言語別 Analyzer を足しても本 package は変わらない。
package analyze
