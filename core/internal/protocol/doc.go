// Package protocol は depwalk Core と Analyzer process がやり取りする JSONL
// record を定義し、wire 形式と domain model の間の ACL (腐敗防止層) として働く。
//
// 本 package が持つのは Analyzer Protocol のデータ契約 (record DTO・検証・
// 厳格な JSONL parse) と、ACL の 2 つの半分である。Translator (translate.go) が
// wire record を graph の domain 値へ変換し、Adapter (adapter.go) が [Runner] で
// Analyzer を 1 回動かして analyze.Source port を実装する。
//
// process の起動そのものは analyzer package へ委ねる。wire 表現の知識と
// process 制御を同じ package に置かないためである。
package protocol
