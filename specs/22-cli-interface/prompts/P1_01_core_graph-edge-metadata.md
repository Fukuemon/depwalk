# P1_01: graph.Edge への opaque Metadata 保持 (D11)

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- 別 prompt の責務範囲 (タスク境界の「実装しない範囲」) に踏み込まない

### 実装アンチパターンの回避 (必守)

- スコープ厳守: spec / 本 prompt に明記された機能のみ実装する。未要求の機能追加・先回りの抽象化・無関係なリファクタ・暗黙の互換維持をしない。
- 既存規約への整合: 命名・エラー処理・ログ・テスト・API 連携方式は、対象コードベースの既存パターンに合わせる。新方式を持ち込む場合は理由を述べて確認を取る。
- 観測可能な契約の保持: UI 文言・イベント名・戻り値・エラーメッセージ・ログ形式・API を要求なく変更しない。変更が必要なら理由と影響を明記する。
- 推測の排除: 要件・業務ルール・API 仕様が不明なら停止して確認する。それらしいが誤った実装 (存在しない API 呼び出し / 非互換な引数) を避け、import と API の実在を確認する。
- fallback の最小化: `??` / `||` / 既定引数 / 多段 fallback / 暗黙のエラー握り潰しは「任意データ」に限定する。必須データの欠落は隠さず明示的に失敗させる。
- 過剰実装の排除: 単純な条件分岐を strategy / handler map に置換しない。要求も計測もない caching / memoization を入れない。
- dead code を残さない: 到達不能コード・未使用の変数 / 関数 / import / export・変更後に不要化した型定義を削除する。
- 判断の記録: 非自明な設計判断は理由 (or spec / ADR へのリンク) を残す。
- **完了条件のタスク化**: 作業開始前に「完了条件」セクションの各項目を todo として登録し、各ステップ完了時に状態を更新すること。タスク化せずに作業を開始することは禁止。

## 作業ステップ (この順序で実行する)

### ステップ 0: ブランチ準備

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` に従う。issue #22 の作業ブランチ (`feature/22`) が既に存在する場合はそれを継続利用し、最新化して開始する。

### ステップ 1: Edge.Metadata の追加と保持

1. テストを先に書く (TDD): `core/internal/graph/convert_test.go` に、`protocol.CallEdge.Metadata` が `Edge.Metadata` へ nested value 込みで deep copy されること (元 DTO の後変更が graph へ波及しないこと)、metadata なし record で `Edge.Metadata` が nil のままなことを検証するテストを追加する。既存の Node 側テスト `TestNodeFromMethodSymbolDeepCopiesOpaqueMetadata` (同ファイル) と対称の構造にする。
2. 実装する: `core/internal/graph/graph.go` の `Edge` 構造体に `Metadata map[string]any` を追加し、`core/internal/graph/convert.go` の `EdgeFromCallEdge` で既存の `copyMetadataObject` (同ファイル、#24 で導入済み) を使って保持する。
3. `## 検証コマンド` を実行する。
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回し、指摘を対応してから次へ。

### ステップ最終: 最終確認

1. 全テスト / vet / gofmt がパスすることを確認する。
2. spec の `## 上位資料からの変更点` に追記が必要な差分がないか確認する (graph feature doc へは sync 済みのため通常は不要)。

## 実装コンテキスト

- spec: `specs/22-cli-interface/index.md` (D11 とその拡張・進捗注記)
- durable 正本: `design/features/graph/DesignDoc_graph.md` (graph 値型節 — 反映済み)
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `core/internal/graph/graph.go`
  - `core/internal/graph/convert.go`
  - `core/internal/graph/convert_test.go`

## 前提条件

- 依存 prompt: なし (本 prompt が実装 phase の起点)
- 完了後に着手可能になる後続 prompt: `P2_01_output_metadata-registered-formats.md`
- 必要な repo 状態: `feature/22` が develop (#24 マージ済み) に rebase 済みで、`graph.Symbol.Metadata` と `copyMetadataObject` が存在すること

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- `graph.Edge` への `Metadata map[string]any` の追加
- `EdgeFromCallEdge` での deep copy 保持とそのテスト

### 実装しない範囲

- `output` package の変更 (EdgeView/NodeView への表出は P2_01)
- analyze use case / CLI / E2E の変更 (P3-P5)
- metadata キー (`resolution`/`provenance` 等) の意味解釈・console 表現 (spec のスコープ外)

## 設計仕様

spec #22 D11 (抜粋):

- call edge metadata は JSON のみ透過 (passthrough) とする。`graph.Edge` に Metadata (map[string]any) を非破壊で追加し、graph convert で破棄をやめて保持する。スキーマ非依存 (#21 が確定させた `resolution` / `provenance` 等のキー名に依存しない)。
- 保持は #24 が `NodeFromMethodSymbol` で確立した deep copy 方針 (`copyMetadataObject`: nested map / array を再帰 copy、scalar はそのまま) に合わせる。protocol DTO の後変更が graph を変えないようにするため。
- nil map は「record に metadata なし」、空 map は「明示的な空 object」として区別を保つ。

durable 正本 (`design/features/graph/DesignDoc_graph.md`) の該当契約: 「`methodSymbol.metadata` / `callEdge.metadata` は Graph が所有する opaque 属性として nested map / array を含め deep copy する (`Symbol.Metadata` / `Edge.Metadata`)。Graph / Traversal は値の意味を解釈しない。」

## テスト観点

spec `## テスト / 評価方針` の graph convert 行 (抜粋): `EdgeFromCallEdge` が `callEdge.metadata` を deep copy で保持すること (#24 の `NodeFromMethodSymbol` 側テストと対称)、metadata なし record で nil のままなこと。

## 検証コマンド

- `cd core && go build ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `cd core && go test ./...`

## 完了条件

- [ ] ステップ 0 でブランチを準備した
- [ ] `Edge.Metadata` の deep copy テストを先に書いた
- [ ] `graph.go` / `convert.go` を実装した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] spec の `## 上位資料からの変更点` に必要な追記がないことを確認した
- [ ] 未解決の仕様質問が残っていない
