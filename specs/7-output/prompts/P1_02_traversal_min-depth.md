# P1_02: Traversal result に node ごとの minDepth を公開する

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

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` skill に従う (issue #7、現在の作業ブランチ `feature/7` を継続利用してよい)。

1. 最新の base branch (`main`) との差分を確認する
2. `feature/7` ブランチ上で作業する (新規ブランチは不要)
3. 完了条件を把握し、作業開始前に todo 化する

### ステップ 1: `Result` に node ごとの minDepth を公開する

1. テストを先に書く (TDD): 「到達 node の `minDepth` が起点 (0) からの最短距離に一致する」「起点自身の `minDepth` が 0 である」「深さ上限を指定したとき、到達集合内の `minDepth` が `maxDepth` を超えない」の unit test を `core/internal/traversal/result_test.go` (または該当する既存テストファイル) に追加する
2. `core/internal/traversal/traversal.go` の `Result.Nodes map[string]bool` を、node ごとの深さが引ける形へ拡張する。**既存の到達集合の意味論 (`minDepth <= maxDepth` の node が含まれる) は変えない**。API 変更の形は、既存 test の修正が最小になる形を選ぶこと (例: `Nodes` を `map[string]int` に変える、または新規 field `Depths map[string]int` を追加して `Nodes map[string]bool` を残す、のいずれか)。**既存 test の修正量を実際に見積もってから決める** (`buildResult` / `Traverse` が `nodes map[string]bool` を内部で使っている箇所、`result_test.go` / `traversal_test.go` / `search_test.go` / `fixture_e2e_test.go` の `res.Nodes` 参照箇所を確認する)
3. `buildResult` (`core/internal/traversal/result.go`) と `Traverse` (`core/internal/traversal/traversal.go`) を、`minDepths` (`core/internal/traversal/search.go`) がすでに計算している `depths map[string]int` を `Result` へ伝播するように更新する (新しい計算は追加しない。既存の `minDepths` の戻り値をそのまま使う)
4. `## 検証コマンド` を実行する
5. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
6. 指摘を対応してから次へ

### ステップ 2: 既存 unit test / E2E fixture test の通過を確認する

1. `core/internal/traversal` の既存 test ファイル (`traversal_test.go` / `search_test.go` / `result_test.go` / `fixture_e2e_test.go`) を、ステップ 1 の API 変更に合わせて最小限修正する。**到達集合の意味論・`cycle` の意味論・`depthLimit` の意味論を検証しているアサーションの意図は変えない** (`map[string]bool` の存在チェックを新しい形の存在チェックに書き換えるだけに留める)
2. `## 検証コマンド` の unit test をすべて実行し、全テストが通ることを確認する
3. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
4. 指摘を対応してから次へ

### ステップ 3: 合流 graph で最短経路側の値になることの test を追加する

1. テストを先に書く (TDD): 「ダイヤモンド型 (合流) graph で、複数経路から到達する node の `minDepth` が最短経路側の距離になる」の unit test を追加する (`graph.NewBuilder()` で深さの異なる複数経路を持つ graph を組み立てる。例: 起点から 1 経路は 2 edge、別経路は 3 edge で同じ node に到達する構成)
2. `## 検証コマンド` を実行する
3. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
4. 指摘を対応してから次へ

### ステップ最終: 最終確認

1. 全テスト / lint / typecheck がパスすることを確認する
2. spec の `## 上位資料からの変更点` に必要な追記がないかを確認する (本 prompt の範囲では追記不要。上位文書 (`design/features/traversal/DesignDoc_traversal.md`) への反映は spec #7 の phase: sync で完了済み)
3. PR / MR を Ready に変更しレビュアーを指名する

## 実装コンテキスト

- spec: `specs/7-output/index.md` (決定時スナップショット。D3 の変更提案の決定経緯)
- feature doc (durable な契約の正本): `design/features/traversal/DesignDoc_traversal.md` (`## 設計 > データ構造 / コンテンツモデル` および `#### 到達集合の定義`)
- 参照する appendix:
  - `specs/7-output/index.md` 内の `## 実装対象` (`traversal` 行)、`## 解決済みの論点 > D3` 末尾の「上位文書への影響」
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `core/internal/traversal/traversal.go` (`Result` 型の定義。ここを拡張する)
  - `core/internal/traversal/result.go` (`buildResult`。`depths` を受け取っている)
  - `core/internal/traversal/search.go` (`minDepths`。深さ計算はここで完結済み。変更しない)
  - `core/internal/traversal/result_test.go` / `traversal_test.go` / `search_test.go` / `fixture_e2e_test.go` (既存 test。`Result.Nodes` を参照している箇所を修正する)

## 前提条件

- 完了しているべき phase / 依存 prompt: なし (P1_01 と並列実行可。両者は別 package (`traversal` / `graph`) を編集するため衝突しない)
- 完了後に着手可能になる後続 prompt: `P2_01_output_write-view.md`
- 必要な repo 状態: `core/internal/traversal` に #6 で実装済みの `minDepths` / `buildResult` / `Traverse` があること (現行実装のまま)

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- `traversal.Result` が node ごとの `minDepth` を公開するための API 拡張 (additive)
- `buildResult` / `Traverse` から `minDepths` の計算結果を `Result` へ伝播する配線
- 既存 unit test / E2E fixture test の該当箇所の最小修正
- 合流 graph での最短経路側 minDepth の test 追加

### 実装しない範囲

- 到達集合・`cycle` 注釈・`depthLimit` cutoff の意味論の変更 (#6 で確定済み。本 prompt は additive 拡張のみ)
- minDepth の計算アルゴリズム自体の変更 (`minDepths` は変更しない。既存の計算結果を伝播するだけ)
- `graph` package の変更 (`P1_01_core_graph-symbol.md` の責務)
- `output` package の実装 (`P2_01` 以降の責務)

## 設計仕様

以下は `design/features/traversal/DesignDoc_traversal.md` からの抜粋。

`## 設計 > データ構造 / コンテンツモデル` より:

> | Traversal result | 到達 node 集合 (**node ごとの `minDepth` を保持**)、到達 edge 集合、status (`ok` / `startNotFound`)、`cycle` 注釈、`depthLimit` cutoff | Output Engine が consumer。tree は保持しない。到達 node / edge 集合は順序を保証しない。`minDepth` の公開は spec #7 (D3) による additive 拡張 |

`#### 到達集合の定義` より:

> - **minDepth**: 起点から探索方向に沿った最短距離。起点自身は 0。合流 (複数経路で同一 node へ到達するダイヤモンド型構造) がある場合、最短の距離を採る。**Traversal result は到達 node ごとの minDepth を公開する** (consumer の JSON 出力が利用する。到達判定の内部値と同一であり、到達集合 / `cycle` / `depthLimit` の意味論は変えない)。
> - **到達 node 集合**: `minDepth <= maxDepth` を満たす node (maxDepth 未指定時は全到達可能 node)。起点を含む。

現行実装 (`core/internal/traversal/search.go`) の `minDepths` は既に `map[string]int` (node ID → 最短距離) を返しており、`Traverse` (`traversal.go`) がこれを内部でしか使っていない (`Result` へは伝播されていない)。本 prompt はこの既存計算結果を `Result` へ公開する配線を追加するのみで、新しい距離計算ロジックは実装しない。

## テスト観点

- 到達 node の `minDepth` が起点 (0) からの最短距離に一致すること
- 起点自身の `minDepth` が 0 であること
- 深さ上限 (`MaxDepth`) を指定したとき、到達集合内のすべての `minDepth` が `MaxDepth` を超えないこと
- 合流 (ダイヤモンド型) graph で、複数経路から到達する node の `minDepth` が最短経路側の値になること
- 既存の到達集合 (`minDepth <= maxDepth` の node)・`cycle` 注釈・`depthLimit` cutoff の意味論が変わっていないこと (既存 unit test / E2E fixture test がすべて通ることで検証する)
- `startNotFound` のとき (起点が存在しない) `Result` が空であること (既存の挙動を維持)

## 検証コマンド

- ビルド: `cd core && go build ./...`
- Lint / typecheck: `cd core && go vet ./...`
- Format 確認: `cd core && test -z "$(gofmt -l .)"`
- Unit test: `cd core && go test ./...`
- 健全性検査: `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 でブランチ状態を確認した (既存ブランチ `feature/7` を継続利用)
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] `traversal.Result` が node ごとの `minDepth` を公開している (additive 拡張。既存の到達集合 / `cycle` / `depthLimit` の意味論は変わっていない)
- [ ] 既存 unit test / E2E fixture test がすべて通る
- [ ] 合流 graph で最短経路側の `minDepth` になることを検証する test が追加されている
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (本 prompt では追記不要なことを確認した)
- [ ] PR / MR を Ready に変更しレビュアーを指名した
- [ ] 未解決の仕様質問が残っていない
