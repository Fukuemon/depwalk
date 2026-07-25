# godoc (doc comment) の書き方

目次: 何に書くか / 書式の原則 / パッケージコメントと doc.go / 型・関数コメントに書くこと / Deprecated / リンク・リスト等の記法 / よくある間違い / チェックリスト

規範の正本は公式の [Go Doc Comments](https://go.dev/doc/comment)。本 reference はその要点と運用判断をまとめる。

## 何に書くか

- **exported なトップレベル識別子 (型 / 関数 / 定数 / 変数) すべてに doc comment を書く**
- 非自明な unexported にも書く。その場合も exported と同じ書式で書く (Google Decisions)
- 全パラメータの機械的な列挙は不要。**エラーしやすい点・非自明な点だけ**書く。context キャンセルの標準挙動のような自明なことは書かない (Google Best Practices)

## 書式の原則

- **完全な文で書き、宣言名から書き始め、ピリオドで終える**

  ```go
  // Quote returns a double-quoted Go string literal representing s.
  func Quote(s string) string { ... }
  ```

- struct フィールドの行末コメントのみ断片 (文でない形) が許される
- `gofmt` がコメントも整形する。整形結果に逆らわない
- Getter に `Get` プレフィックスを付けない (`obj.Owner()` であって `obj.GetOwner()` ではない)

## パッケージコメントと doc.go

- パッケージコメントは `package` 句の**直前・空行なし**に置き、`Package xxx ...` で始める。
  `main` パッケージは `Binary xxx` / `Command xxx` 等で始める (CodeReviewComments — Package Comments)
- **複数ファイル構成では 1 ファイルだけ**にパッケージコメントを置く — これが `doc.go` 慣行の根拠
- doc.go を使うかの判断 (本規約独自):
  - パッケージが複数ファイル → `doc.go` を作ってそこに置く
  - 公開関数が 1 つの小さいパッケージ → その関数のファイルに置いてよい
  - 既にパッケージコメントがあるファイルが存在する → 指示がない限りそのファイルを優先する
- 参考実装: [net/http の doc.go](https://cs.opensource.google/go/go/+/master:src/net/http/doc.go) — 目的・主要な使用パターン・関連型への参照・注意事項の書き方の見本

## 型・関数コメントに書くこと

- **型**: zero value のまま使えるか (`bytes.Buffer` の例)、並行アクセスに安全か (`Regexp` の例) を書く
- **関数**: 戻り値の意味、エラーになる条件、特殊ケース (空入力・境界) を書く
- 実装の詳細を別の識別子に委ねる場合は、参照先をリンクで明示する

## Deprecated

段落を `Deprecated:` で始める。パッケージ・型・関数・メソッドのいずれにも使える:

```go
// Package old provides ...
//
// Deprecated: Use package new instead.
```

## リンク・リスト等の記法 (Go 1.19+)

- **ドキュメントリンク**: `[Name]` (同一パッケージ) / `[pkg.Name]` (他パッケージ) / `[pkg/path.Name]` (フルパス)

  ```go
  // NewReader returns a new [Reader] that reads from r.
  // See [io.Reader] for the interface definition.
  func NewReader(r io.Reader) *Reader { ... }
  ```

- `# Heading` で見出し、`-` で箇条書き・数字で番号付きリスト、タブインデントでコードブロック

## よくある間違い

- TODO 等の継続行をインデントする → コードブロックとして解釈されて壊れる
- ネストしたリストを書く → サポートされていない
- 宣言名以外の言葉 (「This function ...」等) で書き始める

## チェックリスト

- [ ] exported 識別子すべてに doc comment があるか
- [ ] 各コメントが宣言名で始まる完全な文か
- [ ] パッケージコメントは 1 ファイルだけにあるか (`doc.go` 判断に従ったか)
- [ ] 型コメントに zero value / 並行安全性、関数コメントにエラー条件が書かれているか (該当する場合)
- [ ] 関連識別子への参照が `[Name]` / `[pkg.Name]` リンクになっているか
- [ ] 非推奨化は `Deprecated:` 段落になっているか
