# P2_01: Traversal Engine 探索 API (方向・深さ上限・探索順序・起点不在)

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

1. `P1_01_core_graph-view.md` が完了し、`core/internal/graph` の読み取り API が使える状態であることを確認する
2. `feature/6` ブランチ上で作業する
3. 完了条件を把握し、作業開始前に todo 化する

### ステップ 1: Traversal request 型と validation を実装する

1. テストを先に書く (TDD): 「探索方向に不正値を渡すと validation error になる」「深さ上限に負の整数を渡すと validation error になる」「探索順序に `bfs`/`dfs` 以外を渡すと validation error になる」の unit test を書く
2. `core/internal/traversal` package に Traversal request 型を定義する。field は下記「設計仕様」の Traversal request の概念に従う (起点 method ID、方向、深さ上限 (任意)、探索順序 (未指定時 `bfs`))
3. `## 検証コマンド` を実行する
4. diff レビューを回し、指摘を対応してから次へ

### ステップ 2: 起点不在 / 空 graph の処理を実装する

1. テストを先に書く (TDD): 「起点メソッドが graph に存在しない場合、panic せず空の到達集合 + `startNotFound` status を返す」「Graph が空の場合も同様に `startNotFound` を返す」の unit test を書く
2. Traversal のエントリポイント関数を実装し、起点 node を `core/internal/graph` の読み取り API で検索する。見つからない場合は空の到達集合と `startNotFound` status を返す (エラーを投げない)
3. `## 検証コマンド` を実行する
4. diff レビューを回し、指摘を対応してから次へ

### ステップ 3: BFS / DFS による minDepth 計算を実装する (内部訪問順序)

1. テストを先に書く (TDD): 「探索順序未指定時、内部の展開順序が BFS になる」「`dfs` を明示指定すると内部の展開順序が DFS になる」の white-box test (package 内 test) を書く。加えて「循環 graph で無限ループしない」の unit test を書く
2. 起点から探索方向 (caller / callee) に、訪問済み node の再展開を抑止しながら (無限ループ防止の内部機構) 各 node への `minDepth` (起点からの最短距離、起点は 0) を計算する内部処理を実装する。BFS / DFS のどちらの展開順序でも `minDepth` が最短距離として正確に計算されることを保証する (DFS の場合も BFS 相当の距離計算で minDepth を求める)
3. `## 検証コマンド` を実行する
4. diff レビューを回し、指摘を対応してから次へ

### ステップ 4: 深さ上限による打ち切り判定を実装する

1. テストを先に書く (TDD): 「深さ上限未指定時、到達可能 node を深さで打ち切らない」「深さ上限指定時、`minDepth <= maxDepth` の node が到達 node 集合に含まれる」「`maxDepth=0` のとき起点のみが到達 node 集合に含まれる」の unit test を書く
2. ステップ 3 で計算した `minDepth` を用い、`minDepth <= maxDepth` (未指定時は無制限) を満たす node の集合 (到達 node 集合) を決定する内部処理を実装する。この段階では到達 edge 集合・`cycle` 注釈・`depthLimit` cutoff の構築は行わない (`P3_01_traversal_result-model.md` の責務)
3. `## 検証コマンド` を実行する
4. diff レビューを回し、指摘を対応してから次へ

### ステップ最終: 最終確認

1. 全テスト / lint / typecheck がパスすることを確認する
2. spec の `## 上位資料からの変更点` に必要な追記がないかを確認する (本 prompt の範囲では追記不要)
3. PR / MR を Ready に変更しレビュアーを指名する

## 実装コンテキスト

- spec: `specs/6-traversal/index.md`
- feature doc (durable な契約の正本): `design/features/traversal/DesignDoc_traversal.md`
- 参照する appendix:
  - `specs/6-traversal/index.md` 内の `## 要件の解釈` (EARS)、`## Interface 設計 > Props / Request / Response`、`## Performance / Security 設計 > Performance`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `core/internal/traversal/traversal.go` (現在は `package traversal` のみの空 stub。ここに実装する)
  - `core/internal/graph` (P1 で実装した読み取り専用 API。読み取り API 経由でのみ探索し、内部構造には依存しない)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P1_01_core_graph-view.md`
- 完了後に着手可能になる後続 prompt: `P3_01_traversal_result-model.md`
- 必要な repo 状態: `core/internal/graph` に node / edge 登録・ID 取得・方向別隣接 edge 取得の API が実装済みであること

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- Traversal request 型と option (方向、深さ上限、探索順序) の validation
- 起点不在 / 空 graph 時の `startNotFound` 処理
- BFS / DFS による内部訪問順序の実装と、各 node への `minDepth` (最短距離) 計算
- `minDepth <= maxDepth` による到達 node 集合の決定

### 実装しない範囲

- 到達 edge 集合 (誘導部分グラフ) の構築、`cycle` 注釈 (SCC 判定)、`depthLimit` cutoff の記録、Traversal result 型の最終的な組み立て (`P3_01_traversal_result-model.md` の責務)
- `core/internal/graph` の実装変更
- Output Engine 向けの変換、CLI 引数

## 設計仕様

以下は `specs/6-traversal/index.md` からの抜粋。

`## 要件の解釈` (EARS) より:

> - WHEN Core が caller 探索を実行する時、システムは起点メソッドへ到達する呼び出し元を探索方向に従って列挙する。
> - WHEN Core が callee 探索を実行する時、システムは起点メソッドから到達する呼び出し先を探索方向に従って列挙する。
> - IF 起点メソッドがグラフに存在しない時、システムは空の探索結果と起点不在の状態を返す。

`## Interface 設計 > Props / Request / Response` の Traversal request 行より:

| 概念              | 主な field / 値                                                                                                           | 備考                     |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------- | ------------------------ |
| Traversal request | 起点 method ID、方向 (`caller` / `callee`)、深さ上限 (任意、未指定時は無制限)、探索順序 (`bfs` / `dfs`、未指定時は `bfs`) | CLI 引数名は後続で決める |

`## Interface 設計` の深さの定義 (D6) より:

> - **minDepth**: 起点から探索方向に沿った最短距離。起点自身は 0。合流 node は複数経路のうち最短の距離を採る。
> - **到達 node 集合**: `minDepth <= maxDepth` を満たす node (maxDepth 未指定時は全到達可能 node)。起点を含む。
> - **`maxDepth=0`**: 起点 node のみを到達集合に含み、self-loop 以外の隣接 edge が `depthLimit` cutoff になる。起点への self-loop は両端が到達 node のため誘導 edge (+ `cycle` 注釈) として残る (cutoff / 誘導 edge の記録自体は P3 の責務)。

`## Error / Fallback 設計 > エラーケース` より:

| #   | ケース                            | ユーザーへの見せ方                     | リカバリ                                      |
| --- | --------------------------------- | -------------------------------------- | --------------------------------------------- |
| 1   | 起点メソッドが graph に存在しない | 空結果 + `startNotFound` status を返す | CLI / Output が候補表示を行うかは後続で決める |
| 2   | 探索方向が未対応値                | 実行前 validation error                | caller / callee のいずれかを指定する          |
| 3   | 深さ上限が不正値                  | 実行前 validation error                | 未指定または 0 以上の整数を指定する           |
| 5   | 探索順序が未対応値                | 実行前 validation error                | `bfs` / `dfs` のいずれか、または未指定にする  |

`## Fallback` より:

> - Graph が空の場合、Traversal は空の到達 node / edge 集合を返す。起点メソッドはこの空 graph 上にも存在しないため、status は `startNotFound` とする (エラーケース #1 と同一の扱い)。

## テスト観点

- caller / callee 方向を unit test で検証する
- 探索方向に未対応値を渡した場合、実行前 validation error になること
- 深さ上限に不正値 (負の整数) を渡した場合、実行前 validation error になること
- 探索順序に未対応値を渡した場合、実行前 validation error になること
- 起点メソッドが graph に存在しない場合、panic せず空の到達集合 + `startNotFound` status を返すこと
- Graph が空の場合、空結果 + `startNotFound` status を返すこと
- 探索順序未指定時に内部訪問順序が BFS になること、`dfs` 明示指定時に内部訪問順序が DFS になること (white-box test で検証)
- 循環 graph で無限ループしないこと
- 深さ上限未指定時に、到達可能 node を深さで打ち切らないこと
- 深さ上限指定時に `minDepth <= maxDepth` の node が到達 node 集合に含まれること (`maxDepth=0` の境界ケースを含む)

## 検証コマンド

- ビルド: `cd core && go build ./...`
- Lint / typecheck: `cd core && go vet ./...`
- Format 確認: `cd core && test -z "$(gofmt -l .)"`
- Unit test: `cd core && go test ./...`
- 健全性検査: `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 で `P1_01_core_graph-view.md` の完了を確認した
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] Traversal request の validation (方向 / 深さ上限 / 探索順序) が実装されている
- [ ] 起点不在 / 空 graph 時に `startNotFound` を返す
- [ ] BFS / DFS どちらでも `minDepth` が正確に (最短距離として) 計算される
- [ ] `minDepth <= maxDepth` による到達 node 集合の決定が実装されている
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (本 prompt では追記不要なことを確認した)
- [ ] PR / MR を Ready に変更しレビュアーを指名した
- [ ] 未解決の仕様質問が残っていない
