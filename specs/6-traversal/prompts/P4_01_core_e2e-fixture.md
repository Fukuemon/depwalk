# P4_01: S1 / S2 fixture と Traversal 層 E2E 照合テスト

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- **完了条件のタスク化**: 作業開始前に「完了条件」セクションの各項目を todo として登録し、各ステップ完了時に状態を更新すること。タスク化せずに作業を開始することは禁止

### 実装アンチパターンの回避 (必守)

- スコープ厳守: spec / 本 prompt に明記された機能のみ実装する。未要求の機能追加・
  先回りの抽象化・無関係なリファクタ・暗黙の互換維持をしない。
- 既存規約への整合: 命名・エラー処理・ログ・テスト・API 連携方式は、対象コードベースの
  既存パターンに合わせる。新方式を持ち込む場合は理由を述べて確認を取る。
- 観測可能な契約の保持: UI 文言・イベント名・戻り値・エラーメッセージ・ログ形式・API を
  要求なく変更しない。変更が必要なら理由と影響を明記する。
- 推測の排除: 要件・業務ルール・API 仕様が不明なら停止して確認する。それらしいが
  誤った実装 (存在しない API 呼び出し / 非互換な引数) を避け、import と API の実在を確認する。
- fallback の最小化: `??` / `||` / 既定引数 / 多段 fallback / 暗黙のエラー握り潰しは
  「任意データ」に限定する。必須データの欠落は隠さず明示的に失敗させる。
- 過剰実装の排除: 単純な条件分岐を strategy / handler map に置換しない。
  要求も計測もない caching / memoization を入れない。
- dead code を残さない: 到達不能コード・未使用の変数 / 関数 / import / export・
  変更後に不要化した型定義を削除する。
- 判断の記録: 非自明な設計判断は理由 (or spec / ADR へのリンク) を残す。

## 作業ステップ (この順序で実行する)

### ステップ 0: ブランチ準備

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` skill に従う (issue #6、現在の作業ブランチ `feature/6` を継続利用してよい)。

1. `P1_01_core_graph-view.md` / `P2_01_traversal_search-api.md` / `P3_01_traversal_result-model.md` がすべて完了していることを確認する
2. `feature/6` ブランチ上で作業する
3. 完了条件を把握し、作業開始前に todo 化する

### ステップ 1: S1 / S2 fixture (既知の呼び出し関係集合) を定義する

1. `testdata/fixtures/` 配下に、Traversal 層 E2E 用の fixture を置く。fixture は「graph の入力データ (node / edge の一覧)」と「期待される到達 node / edge 集合 (caller 方向 = S1、callee 方向 = S2)」の組で構成する。形式は下記「設計仕様 > fixture 形式の慣行」に従う JSON / JSONL とする (実際の Java/Spring repo のソースコードは置かない。それは Java Analyzer / CLI interface の spec と同期して後続で決める)
2. fixture には少なくとも次のグラフ構造を含める: 直線 (A→B→C)、合流 (ダイヤモンド型)、自己再帰 (self-loop)、相互再帰 (2 node の SCC)、深さ上限で打ち切られる深い経路
3. `## 検証コマンド` のうちビルド / format を実行する
4. diff レビューを回し、指摘を対応してから次へ

### ステップ 2: Traversal 層 E2E 照合テストを実装する

1. テストを先に書く (TDD): fixture を読み込み、`core/internal/graph` の公開 API (P1 の test builder 含む) で graph を構築し、`core/internal/traversal` のエントリポイントを呼び、返された到達 node / edge 集合を fixture の期待値と照合する E2E test を書く
2. 照合規則は「設計仕様」の既知集合の定義に従う: 到達 node 集合は起点を含む。到達 edge 集合は誘導部分グラフ (cycle 注釈付き edge を含む) で、`depthLimit` cutoff の edge は含まない
3. caller 方向 (S1) と callee 方向 (S2) の両方を検証する
4. `## 検証コマンド` を実行する
5. diff レビューを回し、指摘を対応してから次へ

### ステップ最終: 最終確認

1. 全テスト / lint / typecheck がパスすることを確認する
2. spec の `## 上位資料からの変更点` に必要な追記がないかを確認する (fixture の配置形式が `context/testing.md` の記述と乖離した場合は停止して確認する)
3. PR / MR を Ready に変更しレビュアーを指名する

## 実装コンテキスト

- spec: `specs/6-traversal/index.md`
- feature doc (durable な契約の正本): `design/features/traversal/DesignDoc_traversal.md`
- 参照する appendix:
  - `specs/6-traversal/index.md` 内の `## 要件の解釈 > 成功条件`、`## 機能仕様 > Testing`、`## テスト / 評価方針`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `testdata/fixtures/` (E2E fixture の配置先。新規作成)
  - `testdata/analyzer-protocol/records/valid/` (既存 JSONL contract fixture。形式の慣行の参考として読み取りのみ。変更しない)
  - `core/internal/graph` (P1 の公開 API / test builder)
  - `core/internal/traversal` (P2 / P3 で実装したエントリポイントと Traversal result)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P1_01_core_graph-view.md`、`P2_01_traversal_search-api.md`、`P3_01_traversal_result-model.md`
- 完了後に着手可能になる後続 prompt: なし (#6 の最終 prompt。CLI 出力レベルの E2E は CLI interface spec で扱う)
- 必要な repo 状態: Traversal result (到達 node / edge 集合、`cycle` 注釈、`depthLimit` cutoff) が返せる状態であること

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- `testdata/fixtures/` の Traversal 層 E2E fixture (graph 入力 + S1/S2 期待集合)
- fixture を用いた caller / callee 方向の到達 node / edge 集合の照合テスト

### 実装しない範囲

- 実サンプル Java/Spring repo の作成 (Java Analyzer / CLI interface の spec と同期して後続で決める)
- CLI 出力を経由した E2E 照合 (CLI interface spec の対象)
- `core/internal/graph` / `core/internal/traversal` のロジック変更 (P1-P3 で実装済み。テストで欠陥を見つけた場合は停止して報告する)
- 大規模 fixture での探索時間 / peak memory の固定 budget 設定 (後続で決める。計測コードは不要)

## 設計仕様

以下は `specs/6-traversal/index.md` からの抜粋。

`## 要件の解釈 > 成功条件` より:

> - E2E fixture では S1 / S2 の既知 caller / callee 集合と、Traversal Engine が返す到達 node / edge 集合の一致を検証できる。CLI 出力を経由した E2E 照合は CLI interface spec の対象とする。

`## 機能仕様 > Testing` の既知集合の定義より:

> S1 / S2 の既知集合との一致は `testdata/fixtures/` の E2E で検証する。既知集合の定義: 到達 node 集合には起点 node 自身を含む。到達 edge 集合は両端が到達 node 集合に属する探索方向の全 edge (誘導部分グラフ) であり、`cycle` 注釈付き edge も含む。`depthLimit` cutoff の edge は含まない。

`## Content / Data 設計 > コンテンツ配置` より:

| path                | 用途                                                 |
| ------------------- | ---------------------------------------------------- |
| `testdata/fixtures` | S1 / S2 E2E fixture。具体 fixture は後続 spec と同期 |

`## 上位文書整合` の注記 (検証の分界) より:

> #6 の成功条件は Traversal Engine が返す到達 node / edge 集合の一致に限定して検証する。S1 / S2 を CLI 出力レベルで満たす最終的な E2E 照合は、CLI interface spec の実装後に本 spec の Traversal 層 E2E と組み合わせて完成する。

### fixture 形式の慣行 (既存 `testdata/analyzer-protocol/records/valid/` の contract fixture と揃える)

- JSONL の場合は 1 行 = 1 record とする。JSON の場合も 1 ファイル = 1 fixture ケースとする
- key は camelCase (`methodId`, `edgeId`, `callerMethodId`, `calleeMethodId` — `core/internal/protocol/types.go` の json tag と同一)
- 例 (既存 contract fixture の record 形式):

```jsonl
{"schemaVersion":"1","recordType":"methodSymbol","methodId":"method:com.example.App.main","language":"java","symbolKind":"method","qualifiedName":"com.example.App.main","signature":"main(java.lang.String[]):void"}
{"schemaVersion":"1","recordType":"callEdge","edgeId":"edge:1","callerMethodId":"method:com.example.App.main","calleeMethodId":"method:com.example.Service.run"}
```

- Traversal E2E fixture では、上記の graph 入力 (node / edge) に加えて期待値 (S1 / S2 の到達 node ID 集合・到達 edge ID 集合・cycle 注釈対象・depthLimit cutoff 対象) を表現する。期待値部分の schema は本 prompt 実装時に fixture ファイル内で自己記述的に定義してよい (protocol record と混同しない key 構成にする)

## テスト観点

- caller 方向 (S1) で既知の呼び出し元集合を返せること
- callee 方向 (S2) で既知の呼び出し先集合を返せること
- fixture の期待値照合が「到達 node 集合 (起点含む) + 到達 edge 集合 (誘導部分グラフ、cycle 注釈付き edge 含む、depthLimit cutoff edge 除く)」の規則に従うこと
- 合流 / 自己再帰 / 相互再帰 / 深さ上限打ち切りを含む fixture で期待集合と一致すること

## 検証コマンド

- ビルド: `cd core && go build ./...`
- Lint / typecheck: `cd core && go vet ./...`
- Format 確認: `cd core && test -z "$(gofmt -l .)"`
- Unit test / E2E: `cd core && go test ./...`
- 健全性検査: `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 で P1-P3 の完了を確認した
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] `testdata/fixtures/` に直線 / 合流 / 自己再帰 / 相互再帰 / 深さ打ち切りを含む fixture がある
- [ ] caller (S1) / callee (S2) 両方向の照合テストが fixture の期待集合と一致する
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (乖離がないことを確認した)
- [ ] PR / MR を Ready に変更しレビュアーを指名した
- [ ] 未解決の仕様質問が残っていない
