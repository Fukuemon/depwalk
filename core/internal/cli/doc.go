// Package cli は depwalk のコマンドラインインターフェースを定義し、
// コンポジションルートとして働く。
//
// flag 定義・入力検証・stderr へのエラー描画・exit code の契約を持つ。
// 最外層として Analyzer 起動コマンドの解決 (adr/0003-analyzer-command-resolution.md)、
// protocol の ACL adapter を analyze use case の port へ配線すること、
// 探索結果を output package で描画することも担う。
//
// 配線は DI ライブラリを使わず手で書く。依存グラフが 1 箇所のコードとして
// 読めるほうが、生成された配線を追うより速い。
package cli
