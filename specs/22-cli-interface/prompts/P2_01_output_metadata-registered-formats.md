# P2_01: output の Metadata 透過 (NodeView/EdgeView + JSON) と RegisteredFormats() 公開 (D11 / D5 拡張)

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

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` に従う。issue #22 の作業ブランチ (`feature/22`) を継続利用し、P1_01 の commit を含む状態から開始する。

### ステップ 1: NodeView / EdgeView への Metadata 追加と JSON 表出

1. テストを先に書く (TDD): `core/internal/output` の既存テストに、(a) metadata を持つ graph からの JSON 出力で `nodes[].metadata` / `edges[].metadata` が値そのまま出力されること、(b) metadata なしの node/edge では field ごと省略されること (既存 golden が変わらないこと = 後方互換)、(c) console 出力が metadata を一切表出しないこと、を検証するテストを追加する。golden fixture は package-local `core/internal/output/testdata/golden/` の既存規約に従い追加・更新する。
2. 実装する: `NodeView` / `EdgeView` に `Metadata map[string]any` を追加し、View 構築時に graph の `Symbol.Metadata` / `Edge.Metadata` から引き継ぐ。JSON formatter は `omitempty` で表出する。console formatter は変更しない。
3. `## 検証コマンド` を実行する。
4. diff レビューを回し、指摘を対応してから次へ。

### ステップ 2: RegisteredFormats() の公開 API 化

1. テストを先に書く: `output.RegisteredFormats()` が registry に登録済みの format 名を返すこと (現時点: `console` / `json` を含む) を検証する。
2. 実装する: 既存の unexported `registeredFormats()` (`core/internal/output/registry.go`) をラップする形で `RegisteredFormats() []string` を公開する。package 構成は変更しない。
3. `## 検証コマンド` を実行し、diff レビューを回す。

### ステップ最終: 最終確認

1. 全テスト / vet / gofmt がパスすることを確認する。
2. spec の `## 上位資料からの変更点` に追記が必要な差分がないか確認する (output feature doc へは sync 済みのため通常は不要)。

## 実装コンテキスト

- spec: `specs/22-cli-interface/index.md` (D5 拡張 / D11 / Props・Request・Response 節)
- durable 正本: `design/features/output/DesignDoc_output.md` (View 構造・JSON schema・entry point 節 — 反映済み)
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `core/internal/output/` (View / formatter / registry とそのテスト・golden)

## 前提条件

- 依存 prompt: `P1_01_core_graph-edge-metadata.md` (完了済みであること — `graph.Edge.Metadata` を参照するため)
- 完了後に着手可能になる後続 prompt: `P3_01_core_analyze-query-orchestration.md`
- 必要な repo 状態: `graph.Symbol.Metadata` (#24 実装済み) と `graph.Edge.Metadata` (P1_01) が存在すること

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- `NodeView` / `EdgeView` への `Metadata` 追加と View 構築時の引き継ぎ
- JSON formatter の `nodes[].metadata` / `edges[].metadata` (omitempty) 表出とテスト・golden
- `output.RegisteredFormats() []string` の公開

### 実装しない範囲

- console formatter への metadata 表出 (spec D11 で見送りを確定済み)
- metadata キーの意味解釈・整形 (opaque のまま運ぶ)
- graph / analyze use case / CLI / E2E の変更 (P1 / P3-P5)
- dot / mermaid formatter の追加 (D5 で CLI 非露出を確定済み)

## 設計仕様

spec #22 (抜粋):

- **D11**: JSON formatter は omitempty で edge / node に metadata をそのまま載せる。スキーマ非依存 (キー名に依存しない)。console への人間向け表現は見送り。
- **Props / Request / Response 節**: `output.NodeView` に `Metadata protocol.Metadata` (`map[string]any`、`omitempty`) を追加する (Edge 側の `EdgeView.Metadata` と対称)。JSON スキーマの後方互換な追加 (新規 optional フィールド) であり、既存の `nodes[].id`/`qualifiedName`/`signature`/`source`/`minDepth` は変更しない。
- **D5 拡張**: `output` package の unexported `registeredFormats() []string` を `output.RegisteredFormats() []string` として公開 API 化し、CLI がこれを (a) `--format` の検証、(b) 未登録値のエラーメッセージでの一覧表示、の両方に使う。formatter 実装 + registry 登録を追加するだけで CLI 側は無変更のまま新形式が有効になる。

durable 正本 (`design/features/output/DesignDoc_output.md`) の該当契約: 「`nodes[].metadata` / `edges[].metadata` (optional、additive): graph が保持する opaque metadata を意味解釈せずそのまま載せる。欠落時 (nil) は field ごと省略する (omitempty)。」「Formatter は View 以外に依存しない」「field の追加は後方互換 (additive、minor)」。View 境界の全数対応表に `nodes[].metadata` → `View.Nodes[].Metadata`、`edges[].metadata` → `View.Edges[].Metadata` の行が追加済み。

## テスト観点

spec `## テスト / 評価方針` の output 行 (抜粋): `NodeView`/`EdgeView` の Metadata が JSON へ omitempty で表出されること (metadata なしでフィールド不在 = 後方互換)、console が metadata を表出しないこと、`RegisteredFormats()` が登録済み format を返すこと。golden は package-local `testdata/golden/` の既存規約に従い更新。

## 検証コマンド

- `cd core && go build ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `cd core && go test ./...`

## 完了条件

- [ ] ステップ 0 でブランチを準備した
- [ ] JSON 表出 / 後方互換 / console 非表出のテストを先に書いた
- [ ] `NodeView`/`EdgeView` の Metadata と JSON formatter を実装した
- [ ] `RegisteredFormats()` を公開しテストした
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] spec の `## 上位資料からの変更点` に必要な追記がないことを確認した
- [ ] 未解決の仕様質問が残っていない
