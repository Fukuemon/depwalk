# P3_01: Console formatter (tree 構築規則 1-9)

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

1. `P2_01_output_write-view.md` が完了し、`Format` / `Input` / `View` / `Formatter` と Formatter registry が使える状態であることを確認する
2. `feature/7` ブランチ上で作業する
3. 完了条件を把握し、作業開始前に todo 化する

### ステップ 1: `startNotFound` / 到達なし / cutoff のみ の分岐を実装する

1. テストを先に書く (TDD): 「`View.Status = startNotFound` のとき tree を組まず「該当なし: 起点メソッドが解析結果に存在しません (<start>)」を出力する」「`Edges` も `Cutoffs` も空のとき root 行 + `(呼び出し元なし)` (caller 方向) / `(呼び出し先なし)` (callee 方向) を出力する」「`Edges` は空だが `Cutoffs` が非空 (`maxDepth=0` 等) のとき root 行 + `… (depth limit: N edges cut)` を出力し `(呼び出し元なし)` は出さない」の golden test を `core/internal/output/testdata/golden/` に配置して書く
2. Console formatter (`core/internal/output` package 内、新規ファイルを作成してよい) を実装し、`Formatter` interface を満たす。`P2_01` の Formatter registry に `FormatConsole` として登録する
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ 2: tree 構築規則 1-6 (root / 子 / 兄弟順序 / 展開順序 / 初出のみ展開 / cycle・既出の標識) を実装する

1. テストを先に書く (TDD): 「合流 (ダイヤモンド) graph で共有 node の部分木が展開されるのは 1 回だけで、2 回目以降が `(既出)` の葉になる」「3 要素以上の SCC (A→B→C→A) で循環に属する node がすべて tree に現れ、`(cycle)` が付くのは経路上の祖先に戻る edge の先だけになる」「self-loop (`B → B`) が `(既出)` ではなく `(cycle)` になり、root の self-loop で root の部分木が二重出力されない」「兄弟の並び順が `qualifiedName` → `signature` → `methodId` の辞書順で固定される」の golden test を追加する
2. Console formatter に pre-order DFS の tree 構築を実装する。**node の展開に入る時点で、その node 自身を「展開済み」に記録し「経路上の祖先集合」に加える** (root を含む。self-loop / root 二重展開を防ぐための必須初期化)。再登場 node の標識判定は Console formatter が DFS 中に保持する経路 (祖先集合) で行い、**`Result.Cycles` (`View.EdgeView.Cycle`) は使わない**
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ 3: `… (depth limit: N edges cut)` (規則 7) を実装する

1. テストを先に書く (TDD): 「`depthLimit` cutoff を持つ node の下に `… (depth limit: N edges cut)` が子の最後に出て、N が当該 node からの cutoff edge 数に一致する」の golden test を追加する
2. cutoff edge の到達側 endpoint の子として、通常の子 edge をすべて出力した後の最後に 1 行出力する処理を実装する
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ 4: 行の書式 (node ラベル / 位置情報) を実装する

1. テストを先に書く (TDD): 「子行に `edge.CallSite` が出る」「root に宣言位置 (`Symbol.Source`) が出る」「位置情報が欠落している場合に位置表記が省略され破綻しない」の golden test を追加する
2. node ラベル (`qualifiedName` + `signature`) と位置情報の出力を実装する
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ 5: golden fixture 一式を揃え、決定性を検証する

1. `core/internal/output/testdata/golden/` に `## テスト観点` の全 fixture ケースを揃える (3 要素 SCC / self-loop / root self-loop / ダイヤモンド / cutoff / 到達なし / `maxDepth=0` / `maxDepth=0` + 起点 self-loop / `startNotFound`)
2. 「同一 `Result` から常に同一のバイト列が得られる」ことを検証する test を追加する (同じ `Input` から複数回 `Write` を呼び、バイト列が一致することを確認する)
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ最終: 最終確認

1. 全テスト / lint / typecheck がパスすることを確認する
2. spec の `## 上位資料からの変更点` に必要な追記がないかを確認する (本 prompt の範囲では追記不要。上位文書への反映は spec #7 の phase: sync で完了済み)
3. PR / MR を Ready に変更しレビュアーを指名する

## 実装コンテキスト

- spec: `specs/7-output/index.md` (決定時スナップショット。D2 (= Design Doc Open Question Q3) の決定経緯)
- feature doc (durable な契約の正本): `design/features/output/DesignDoc_output.md` (`### Console ツリー表現 (Q3 の正本)`)
- 参照する appendix:
  - `specs/7-output/index.md` 内の `## 機能仕様 > Testing` (Console の検証観点)
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `core/internal/output/output.go` (`P2_01` で実装済みの `Format` / `Input` / `View` / `Formatter` / Formatter registry。ここに Console formatter を追加登録する)
  - `core/internal/output/testdata/golden/` (golden fixture の配置先。新規ディレクトリ)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P2_01_output_write-view.md`
- 完了後に着手可能になる後続 prompt: なし (Phase4 の DOT / Mermaid prompt は本 spec では生成しない)
- 必要な repo 状態: `core/internal/output` に `Format` / `Input` / `View` / `Formatter` と Formatter registry が実装済みであること。`P3_02_output_json-formatter.md` と並列実行可 (別ファイルを編集するため衝突しない)

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- Console formatter (`Formatter` interface の実装)
- tree 構築規則 1-9 (root / 子 / 兄弟順序 / 展開順序 / 初出のみ展開 / `(cycle)` / `(既出)` / cutoff 行 / 到達なし / `startNotFound`)
- golden test 一式 (`core/internal/output/testdata/golden/`)

### 実装しない範囲

- `Format` / `Input` / `View` / `Formatter` / `Write` entry point の変更 (`P2_01` で確定済み。変更が必要なら停止して確認する)
- JSON formatter の実装 (`P3_02_output_json-formatter.md` の責務)
- DOT / Mermaid formatter の実装 (Phase4。本 spec では prompt を生成しない)
- `graph` / `traversal` package の変更

## 設計仕様

以下は `design/features/output/DesignDoc_output.md` (`### Console ツリー表現 (Q3 の正本)` と `### 公開 entry point と Formatter / View`) からの抜粋。

#### View (`P2_01` で確定。抜粋)

```go
// 全 formatter が共有する中間表現 (symbol 解決済み / sort 済み)
type View struct {
    Status    traversal.Status
    Direction graph.Direction // 探索方向 (Request から引き継ぐ)
    Start     NodeView
    Nodes     []NodeView   // methodId の辞書順
    Edges     []EdgeView   // edgeId の辞書順。Cycle flag を持つ
    Cutoffs   []CutoffView // edgeId の辞書順
}
```

- **「子」の判定と `(呼び出し元なし)` / `(呼び出し先なし)` の文言分岐は `View.Direction` を使う**: `Direction = caller` なら子は「呼び出し元」で到達なし時は `(呼び出し元なし)`、`Direction = callee` なら子は「呼び出し先」で `(呼び出し先なし)`。

#### tree 構築規則

1. **root** = 起点 node。
2. **子** = 誘導 edge 集合 (`View.Edges`) を探索方向に辿った先の node。caller 方向なら子は「呼び出し元」、callee 方向なら「呼び出し先」。
3. **兄弟の順序** = `qualifiedName` → `signature` → `methodId` の辞書順 (出力の決定性を担保)。
4. **展開順序** = 上記順序の pre-order DFS。**node の展開に入る時点で、その node 自身を「展開済み」に記録し「経路上の祖先集合」に加える** (root を含む)。これにより self-loop も規則 6 の `(cycle)` になり、root の self-loop で root が再展開されることもない。
5. **初出のみ展開** = 部分木を展開するのは tree 中で最初に出現したときの 1 回のみ。出力行数は O(到達 edge 数) に収まり、**停止性はこの規則だけで保証される**。
6. **再登場 node の標識** = 展開しない葉に 2 種類の標識を付ける。判定は Console formatter が DFS 中に保持する経路 (祖先集合) で行い、**`View.EdgeView.Cycle` は使わない** (この flag は同一 SCC の誘導 edge すべてを注釈するグラフ全体の性質であり、打ち切りに使うと 3 要素 SCC で最初の edge が切られ node が tree から消える):
   - **`(cycle)`** = 現在の経路上の祖先に戻る edge (back edge) の先。
   - **`(既出)`** = 祖先ではないが、別の枝で展開済みの node (合流)。
7. **`… (depth limit: N edges cut)`** = cutoff edge の到達側 endpoint の子として、**子の最後に** 1 行出す。N はその node からの cutoff edge 数。cutoff 先 (`TargetMethodID`) は到達集合外のため名前を出さない。
8. **到達なし** (`Edges` も `Cutoffs` も空) = root 行 + `(呼び出し元なし)` / `(呼び出し先なし)`。`Edges` が空でも `Cutoffs` が非空なら規則 7 の cutoff 行を出す (「到達なし」ではない)。
9. **`startNotFound`** = tree を組まず、次の文言を出す: `該当なし: 起点メソッドが解析結果に存在しません (<start>)`。

#### 行の書式

- node ラベル = `qualifiedName` + `signature`。
- 位置情報: 子行は `edge.CallSite` (呼び出し箇所)、root は宣言位置 (`Symbol.Source`)。欠落時は位置表記を省略する。メソッドの宣言位置は Console では出さない (JSON が両方持つ)。

```text
UserService.findById(Long)  [UserService.java:42]
├─ UserController.getUser(Long)  [UserController.java:31]
│  └─ ApiFilter.doFilter()  [ApiFilter.java:20]
├─ AdminController.getUser(Long)  [AdminController.java:18]
│  └─ ApiFilter.doFilter()  (既出)
├─ UserBatch.execute()  [UserBatch.java:55]
│  └─ … (depth limit: 2 edges cut)
└─ CacheWarmer.warm()  [CacheWarmer.java:8]
   └─ Scheduler.run()  [Scheduler.java:12]
      └─ UserService.findById(Long)  (cycle)
```

## テスト観点

以下は `design/features/output/DesignDoc_output.md#テスト観点` (fixture ケースの durable 正本) と `specs/7-output/index.md#機能仕様--testing` からの抜粋。

- 合流 (ダイヤモンド) graph で、共有 node の部分木が展開されるのは 1 回だけで、2 回目以降が `(既出)` の葉になること (出力行数が到達 edge 数に対して線形に収まること)
- 循環 (self-loop / 相互再帰) を含む graph で無限展開しないこと
- **3 要素以上の SCC (A→B→C→A) で、循環に属する node がすべて tree に現れること** (最初の edge で SCC 全体を切り落とさない)。`(cycle)` が付くのは経路上の祖先に戻る edge の先だけであること
- **self-loop (`B → B`) が `(既出)` ではなく `(cycle)` になること**、および **root の self-loop で root の部分木が二重出力されないこと**
- **`maxDepth=0` で `(呼び出し元なし)` を出さず、root 行 + `… (depth limit: N edges cut)` を出すこと**。起点 self-loop がある場合は誘導 edge (+ `cycle` 注釈) が残るため、通常の tree 経路を通ること (`maxDepth=0` + 起点 self-loop の fixture)
- 合流で再登場した node には `(既出)`、経路上の祖先に戻る場合は `(cycle)` と、標識が区別されること
- `depthLimit` cutoff を持つ node の下に `… (depth limit: N edges cut)` が出て、N が当該 node からの cutoff edge 数に一致すること
- 兄弟の並び順が `qualifiedName` → `signature` → `methodId` の辞書順で固定され、同一 `Result` から常に同一のバイト列が得られること (到達集合の map 順序に依存しないこと)
- 子行に `callSite`、root に宣言位置が出ること。位置が欠落している場合に位置表記を省略しても破綻しないこと
- 到達なし (`Edges` も `Cutoffs` も空) と `startNotFound` それぞれの文言が golden で検証されること
- fixture ケース (最低限揃える): 3 要素 SCC / self-loop / root self-loop / ダイヤモンド / cutoff / 到達なし / `maxDepth=0` / `maxDepth=0` + 起点 self-loop / `startNotFound`

## 検証コマンド

- ビルド: `cd core && go build ./...`
- Lint / typecheck: `cd core && go vet ./...`
- Format 確認: `cd core && test -z "$(gofmt -l .)"`
- Unit test: `cd core && go test ./...`
- 健全性検査: `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 で `P2_01` の完了を確認した
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] Console formatter が `Formatter` interface を実装し、`FormatConsole` として registry に登録されている
- [ ] tree 構築規則 1-9 がすべて実装され、golden test で検証されている
- [ ] `core/internal/output/testdata/golden/` に `## テスト観点` の fixture ケース (3 要素 SCC / self-loop / root self-loop / ダイヤモンド / cutoff / 到達なし / `maxDepth=0` / `maxDepth=0` + 起点 self-loop / `startNotFound`) が揃っている
- [ ] 同一 `Result` から常に同一のバイト列が得られることが test で検証されている
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (本 prompt では追記不要なことを確認した)
- [ ] PR / MR を Ready に変更しレビュアーを指名した
- [ ] 未解決の仕様質問が残っていない
