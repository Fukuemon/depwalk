# P1_01: Graph Engine (node / edge graph view と test builder)

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

1. 最新の base branch (`main`) との差分を確認する
2. `feature/6` ブランチ上で作業する (新規ブランチは不要)
3. 完了条件を把握し、作業開始前に todo 化する

### ステップ 1: node / edge の graph データ構造を定義する

1. テストを先に書く (TDD): 「node / edge を登録できる」「登録した node を ID で取得できる」「caller 方向 / callee 方向の隣接 edge を取得できる」の unit test を書く
2. `core/internal/graph` package に以下を実装する:
   - node を識別する型 (`core/internal/protocol` の `MethodSymbol.MethodID` を key とする)
   - edge を識別する型 (`core/internal/protocol` の `CallEdge.EdgeID`、`CallerMethodID`、`CalleeMethodID` を保持)
   - node / edge を登録する API (`AddNode` / `AddEdge` 相当)
   - 起点 node の存在確認 API (`GetNode` 相当。存在しない場合は「見つからない」ことを呼び出し側が判定できる戻り値にする)
   - 探索方向 (caller / callee) に応じた隣接 edge を返す読み取り専用 API (`Neighbors` 相当)
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ 2: test builder を実装する

1. テストを先に書く (TDD): 「builder で node / edge をまとめて構築できる」「構築した graph を Traversal 側が読み取り API で参照できる」の unit test を書く
2. `core/internal/graph` の公開 API のみを使って graph を組み立てる test builder (`core/internal/graph` package 内、または `_test.go` 内のヘルパー) を実装する。P2-P4 の unit test / E2E fixture がこの builder を使って circular graph / diamond graph / 深い graph を組み立てられるようにする
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ最終: 最終確認

1. 全テスト / lint / typecheck がパスすることを確認する
2. spec の `## 上位資料からの変更点` に必要な追記がないかを確認する (本 prompt の範囲では追記不要)
3. PR / MR を Ready に変更しレビュアーを指名する

## 実装コンテキスト

- spec: `specs/6-traversal/index.md`
- feature doc (durable な契約の正本): `design/features/traversal/DesignDoc_traversal.md`
- 参照する appendix:
  - `specs/6-traversal/index.md` 内の `## 実装対象`、`## 機能仕様 > Reuse Policy`、`## Content / Data 設計 > コンテンツ配置 / package / route`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `core/internal/graph/graph.go` (現在は `package graph` のみの空 stub。ここに実装する)
  - `core/internal/protocol/types.go` (`MethodSymbol` / `CallEdge` / `SourceLocation` の Model schema。フィールド名はこのファイルの定義をそのまま使う。変更しない)

## 前提条件

- 完了しているべき phase / 依存 prompt: なし (Analyzer Protocol / SPI は #12 で実装済み。`core/internal/protocol` の型をそのまま使う)
- 完了後に着手可能になる後続 prompt: `P2_01_traversal_search-api.md`
- 必要な repo 状態: `core/go.mod` (module `github.com/Fukuemon/depwalk/core`, go 1.22.2) がそのまま使える状態であること

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- `core/internal/graph` package: node / edge の登録、ID による node 取得、方向 (caller / callee) 別の隣接 edge 取得という読み取り専用 API
- test builder (graph を組み立てて Traversal 側のテストで使えるようにするヘルパー)

### 実装しない範囲

- BFS / DFS 探索ロジック、minDepth 計算、`cycle` 注釈 (SCC 判定)、`depthLimit` cutoff の判定 (`P2_01_traversal_search-api.md` / `P3_01_traversal_result-model.md` の責務)
- Traversal request / result の型定義 (P2 の責務)
- Output Engine 向けの変換
- Analyzer 実装、CLI 引数

## 設計仕様

以下は `specs/6-traversal/index.md` からの抜粋。

`## 機能仕様 > Reuse Policy` より:

> - Traversal Engine は `core/internal/traversal` に閉じる。
> - Graph の node / edge 管理は `core/internal/graph` に閉じ、Traversal は graph が公開する読み取り API 経由で探索する。
> - Analyzer 固有情報や Java 固有 metadata を Traversal の分岐条件にしない。

`## Content / Data 設計 > コンテンツ配置 / package / route` より:

| path                      | 用途                                          |
| ------------------------- | --------------------------------------------- |
| `core/internal/graph`     | node / edge 管理、Traversal が読む graph view |
| `core/internal/traversal` | caller / callee 探索、探索 option、探索結果   |

`## 実装対象` より:

| モジュール  | 実装有無 | 主な責務                                                    |
| ----------- | :------: | ----------------------------------------------------------- |
| `core`      |    ◯     | Graph / Traversal package 境界、use case からの呼び出し口   |
| `traversal` |    ◯     | caller / callee 探索、循環 / 深さ上限の扱い、探索結果モデル |

`core/internal/protocol/types.go` の Model schema (変更しない。node/edge の ID として使う):

- `MethodSymbol.MethodID string` — node の一意 ID
- `CallEdge.EdgeID string` — edge の一意 ID
- `CallEdge.CallerMethodID string` / `CallEdge.CalleeMethodID string` — edge の両端

## テスト観点

- node / edge を登録でき、ID で node を取得できること
- 存在しない node ID を取得しようとした場合、呼び出し側が「見つからない」と判定できる戻り値になること (panic しない)
- caller 方向 / callee 方向で、それぞれ正しい隣接 edge のみが返ること
- test builder で組み立てた graph が、公開読み取り API を通じて期待通りに参照できること (循環 graph / 合流 (ダイヤモンド型) graph / 深い graph を builder で組み立てられること)

## 検証コマンド

- ビルド: `cd core && go build ./...`
- Lint / typecheck: `cd core && go vet ./...`
- Format 確認: `cd core && test -z "$(gofmt -l .)"`
- Unit test: `cd core && go test ./...`
- 健全性検査: `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 でブランチ状態を確認した (既存ブランチ `feature/6` を継続利用)
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] `core/internal/graph` に node / edge 登録、ID 取得、方向別隣接 edge 取得の読み取り専用 API が実装されている
- [ ] test builder が循環 / 合流 (ダイヤモンド型) / 深い graph を組み立てられる
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (本 prompt では追記不要なことを確認した)
- [ ] PR / MR を Ready に変更しレビュアーを指名した
- [ ] 未解決の仕様質問が残っていない
