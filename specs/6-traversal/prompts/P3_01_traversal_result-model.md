# P3_01: Traversal result モデル (誘導部分グラフ・cycle 注釈・depthLimit cutoff)

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

1. `P2_01_traversal_search-api.md` が完了し、minDepth 計算と到達 node 集合の決定が使える状態であることを確認する
2. `feature/6` ブランチ上で作業する
3. 完了条件を把握し、作業開始前に todo 化する

### ステップ 1: 到達 edge 集合 (誘導部分グラフ) の構築を実装する

1. テストを先に書く (TDD): 「両端が到達 node 集合に属する探索方向の edge がすべて到達 edge 集合に含まれる」「合流 (ダイヤモンド型) graph で複数経路の edge がすべて含まれる」「`minDepth > maxDepth` の node への edge は到達 edge 集合に含まれない」の unit test を書く
2. P2 で確定した到達 node 集合をもとに、到達 node の探索方向の全 edge を走査し、接続先も到達 node 集合に属する edge を到達 edge 集合 (誘導部分グラフ) として収集する処理を実装する
3. `## 検証コマンド` を実行する
4. diff レビューを回し、指摘を対応してから次へ

### ステップ 2: depthLimit cutoff の記録を実装する

1. テストを先に書く (TDD): 「到達 node から `minDepth > maxDepth` の node への edge が `depthLimit` cutoff として記録される」「`maxDepth=0` のとき self-loop 以外の隣接 edge が `depthLimit` cutoff になり、起点への self-loop は誘導 edge + `cycle` 注釈として残る」「深さ上限未指定時は `depthLimit` cutoff が空になる」の unit test を書く
2. ステップ 1 の edge 走査で、接続先 node の `minDepth > maxDepth` となる edge を `depthLimit` cutoff (対象 edge + 接続先 node の minDepth) として記録する処理を実装する。cutoff の対象は edge のみで、node 自体は cutoff 対象にしない
3. `## 検証コマンド` を実行する
4. diff レビューを回し、指摘を対応してから次へ

### ステップ 3: cycle 注釈 (SCC 判定) を実装する

1. テストを先に書く (TDD): 「自己再帰 (self-loop) edge が `cycle` 注釈を持つ」「相互再帰 (同一 SCC 内) の edge が `cycle` 注釈を持つ」「合流 (ダイヤモンド型) の edge は `cycle` と誤標識されない」「`cycle` 注釈付き edge も到達 edge 集合に含まれる」の unit test を書く
2. 到達部分グラフ (到達 node 集合 + 到達 edge 集合) に対して SCC (強連結成分) を計算し (Tarjan 等、`O(V + E)` のアルゴリズム)、閉路を構成する edge (self-loop、または両端が同一の非自明 SCC に属する edge) に `cycle` 注釈を付与する処理を実装する。注釈付き edge を到達 edge 集合から除外しない
3. `## 検証コマンド` を実行する
4. diff レビューを回し、指摘を対応してから次へ

### ステップ 4: Traversal result 型を組み立て、順序非依存性を検証する

1. テストを先に書く (TDD): 「BFS / DFS のどちらを指定しても、到達 node / edge 集合・`cycle` 注釈・`depthLimit` cutoff の内容が同一になる (順序非依存性)」「Traversal result が tree ではなく集合として返り、順序保証がない」の unit test を書く
2. 到達 node 集合、到達 edge 集合、status (`ok` / `startNotFound`)、`cycle` 注釈、`depthLimit` cutoff を保持する Traversal result 型を確定し、P2 のエントリポイント関数から返す
3. `## 検証コマンド` を実行する
4. diff レビューを回し、指摘を対応してから次へ

### ステップ最終: 最終確認

1. 全テスト / lint / typecheck がパスすることを確認する
2. spec の `## 上位資料からの変更点` に必要な追記がないかを確認する (本 prompt の範囲では追記不要。確定した Go 型名が feature doc の契約から乖離した場合は停止して確認する)
3. PR / MR を Ready に変更しレビュアーを指名する

## 実装コンテキスト

- spec: `specs/6-traversal/index.md`
- feature doc (durable な契約の正本): `design/features/traversal/DesignDoc_traversal.md`
- 参照する appendix:
  - `specs/6-traversal/index.md` 内の `## Interface 設計 > Props / Request / Response` と「到達集合の定義 (D6)」、`## フロー / シーケンス > Flowchart`、`## テスト / 評価方針 > テスト観点`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `core/internal/traversal/` (P2 で実装した request 型・minDepth 計算・到達 node 集合の決定。この package に追記する)
  - `core/internal/graph` (P1 で実装した読み取り専用 API。読み取り API 経由でのみ参照する)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P1_01_core_graph-view.md`、`P2_01_traversal_search-api.md`
- 完了後に着手可能になる後続 prompt: `P4_01_core_e2e-fixture.md`
- 必要な repo 状態: `core/internal/traversal` に minDepth 計算と到達 node 集合の決定が実装済みであること

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- 到達 edge 集合 (誘導部分グラフ) の構築
- `depthLimit` cutoff の記録 (対象 edge + 接続先 minDepth)
- `cycle` 注釈 (到達部分グラフの SCC 判定、`O(V + E)`)
- Traversal result 型の確定と、BFS / DFS 順序非依存性の検証

### 実装しない範囲

- Traversal request の validation、minDepth 計算、起点不在処理 (P2 で実装済み。変更しない)
- `core/internal/graph` の実装変更
- E2E fixture / `testdata/fixtures/` の整備 (`P4_01_core_e2e-fixture.md` の責務)
- Output Engine 向けの変換 (tree 表現の構築は #7 Output の責務)、CLI 引数

## 設計仕様

以下は `specs/6-traversal/index.md` からの抜粋。

`## Interface 設計 > Props / Request / Response` より:

| 概念              | 主な field / 値                                                                                    | 備考                                                                                                                                                            |
| ----------------- | -------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Traversal result  | 到達 node 集合、到達 edge 集合、status (`ok` / `startNotFound`)、`cycle` 注釈、`depthLimit` cutoff | Output Engine が consumer。tree は保持しない。到達 node / edge 集合は順序を保証しない                                                                           |
| Cycle 注釈        | 対象 edge の集合                                                                                   | 到達部分グラフ内で閉路を構成する edge (自己再帰 self-loop、または同一 SCC 内の edge)。**到達 edge 集合にも含まれる** (呼び出し関係として実在するため除外しない) |
| DepthLimit cutoff | 対象 edge の集合、接続先 node の minDepth                                                          | 到達 node から `minDepth > maxDepth` の node への edge。**到達 edge 集合には含まれない**。対象は edge のみで node 自体は cutoff 対象にしない                    |

到達集合の定義 (D6) より:

> - **minDepth**: 起点から探索方向に沿った最短距離。起点自身は 0。合流 node は複数経路のうち最短の距離を採る。
> - **到達 node 集合**: `minDepth <= maxDepth` を満たす node (maxDepth 未指定時は全到達可能 node)。起点を含む。
> - **到達 edge 集合**: 両端が到達 node 集合に属する、探索方向に沿った全 edge (誘導部分グラフ)。合流 edge も `cycle` 注釈付き edge も含む。
> - **`maxDepth=0`**: 起点 node のみを到達集合に含み、self-loop 以外の隣接 edge が `depthLimit` cutoff になる。起点への self-loop は両端が到達 node のため誘導 edge (+ `cycle` 注釈) として残る。
>
> この定義により、結果は BFS / DFS の選択・訪問順序に一切依存せず決定的になる。DFS option 指定時も、maxDepth 判定は minDepth 基準で行う。

`## フロー / シーケンス > Flowchart` の段階 2 (edge の分類) より:

> 到達 node の探索方向の全 edge を走査し、
>
> - 接続先 node が到達 node 集合に含まれない (`minDepth > maxDepth`) → `depthLimit` cutoff に記録 (到達 edge 集合には含めない)
> - 含まれる → 到達 edge 集合へ追加 (誘導部分グラフ)。さらに edge が到達部分グラフ内で閉路を構成する (self-loop または同一 SCC 内) なら `cycle` 注釈を付与 (到達 edge 集合からは除外しない)

`## Performance / Security 設計 > Performance` より:

> - Traversal は node 数 `V`、edge 数 `E` に対して `O(V + E)` を基本方針にする。minDepth 計算 (BFS 相当)、誘導 edge 集合の収集、SCC 判定 (Tarjan 等) はいずれも `O(V + E)` で、全体もこれに収まる。

## テスト観点

- 自己再帰 (self-loop) / 相互再帰 (SCC) を含む graph で、閉路を構成する edge が `cycle` 注釈を持ち、かつ到達 edge 集合にも含まれること
- 合流 (ダイヤモンド型) graph で、同一 node への複数経路の edge がすべて到達 edge 集合に含まれ、`cycle` と誤標識されないこと
- BFS / DFS のどちらを指定しても、到達 node / edge 集合・`cycle` 注釈・`depthLimit` cutoff の内容が同一であること (順序非依存性)
- 深さ上限指定時に `minDepth > maxDepth` の node への edge を `depthLimit` cutoff として保持できること (`maxDepth=0` の境界ケース、深い経路と浅い経路の両方を持つ合流 node が浅い経路の minDepth で到達集合に入るケースを含む)
- Traversal 結果が tree ではなく、到達 node 集合 + edge 集合 (誘導部分グラフ) として返ること。到達 node / edge 集合に順序保証がないこと
- 探索結果モデルの unit test は、到達 node 集合、到達 edge 集合、`cycle` 注釈、`depthLimit` cutoff が期待どおりに返ることを検証する

## 検証コマンド

- ビルド: `cd core && go build ./...`
- Lint / typecheck: `cd core && go vet ./...`
- Format 確認: `cd core && test -z "$(gofmt -l .)"`
- Unit test: `cd core && go test ./...`
- 健全性検査: `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 で `P2_01_traversal_search-api.md` の完了を確認した
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] 到達 edge 集合が誘導部分グラフとして構築される (合流 edge を失わない)
- [ ] `depthLimit` cutoff が minDepth 基準で記録される
- [ ] `cycle` 注釈が SCC 判定で付与され、到達 edge 集合から除外されない
- [ ] BFS / DFS の順序非依存性が unit test で検証されている
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (本 prompt では追記不要なことを確認した)
- [ ] PR / MR を Ready に変更しレビュアーを指名した
- [ ] 未解決の仕様質問が残っていない
