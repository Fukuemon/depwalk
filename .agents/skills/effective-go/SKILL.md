---
name: effective-go
description: >-
  Go コードの作成・レビュー・リファクタリング時に、公式ベストプラクティスと整合済みの Go 規約 (ファイル分割・責務分離・godoc)
  を適用する。"Go を書く" / "Go のレビュー" / "godoc" / "effective-go" で起動する。
---

# Effective Go

本テンプレートの Go 規約。公式ベストプラクティス (Effective Go / Google Go Style Guide /
Go Doc Comments / CodeReviewComments) と整合するよう著作時に吟味済みで、文書全体をそのまま規範として適用する。
公式が形を定めていない領域 (ファイル構成等) は本規約が標準形を定め、公式より意図的に厳しい箇所には
「本規約独自」と注記している (レビューでの逸脱は、公式由来の規範より緩やかにプロジェクト判断で扱ってよい)。

## いつ使うか

- 新しい Go コードを書くとき
- Go コードをレビュー・リファクタリングするとき
- godoc コメントを書く・直すとき

## 中核原則 (常に適用)

- **フォーマット**: `gofmt` に常に従う (交渉の余地なし)
- **命名**: アンダースコアを使わない。exported は MixedCaps、unexported は mixedCaps。Getter に `Get` プレフィックスを付けない
- **エラー処理**: エラーは必ず検査して返す。panic で代用しない
- **並行性**: メモリ共有ではなく通信で共有する (channel を使う)
- **interface**: 小さく保つ (1〜2 メソッド)。**interface を受け取り、具体型を返す**。実需要が出るまで定義しない
- **ドキュメント**: exported な識別子すべてに、宣言名で始まる完全な文の doc comment を書く

## References (作業内容に応じて読む)

| 作業                                            | 読む reference                                                                     |
| ----------------------------------------------- | ---------------------------------------------------------------------------------- |
| パッケージ内のファイルをどう割るか              | [references/file-separation.md](references/file-separation.md)                     |
| パッケージ / struct / interface / func の切り方 | [references/responsibility-separation.md](references/responsibility-separation.md) |
| doc comment (godoc) の書き方                    | [references/godoc.md](references/godoc.md)                                         |

## 情報源 (公式)

- Effective Go: https://go.dev/doc/effective_go
- Google Go Style Guide: https://google.github.io/styleguide/go/
- Go Doc Comments: https://go.dev/doc/comment
- Code Review Comments: https://go.dev/wiki/CodeReviewComments
- 標準ライブラリ: idiomatic なパターンの参照実装として使う
