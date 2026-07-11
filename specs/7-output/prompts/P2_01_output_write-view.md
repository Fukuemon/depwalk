# P2_01: Output package の公開 entry point (`Write`) と `View` 構築

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

1. `P1_01_core_graph-symbol.md` と `P1_02_traversal_min-depth.md` が完了し、`graph.Node.Symbol` / `graph.Edge.CallSite` と `traversal.Result` の node ごとの `minDepth` が使える状態であることを確認する
2. `feature/7` ブランチ上で作業する
3. 完了条件を把握し、作業開始前に todo 化する

### ステップ 1: `Format` / `Input` / `View` の型を定義する

1. テストを先に書く (TDD) は型定義そのものには不要なため、次のステップ (Formatter registry) のテストに含める形で進めてよい
2. `core/internal/output/output.go` (現在は `package output` のみの stub) に次を実装する:
   - `Format` 定数: `FormatConsole` (`"console"`) / `FormatJSON` (`"json"`) / `FormatDOT` (`"dot"`) / `FormatMermaid` (`"mermaid"`)
   - `Input` 型 (`Graph *graph.Graph` / `Result traversal.Result` / `Request traversal.Request`)
   - `View` 型 (`Status traversal.Status` / `Start NodeView` / `Nodes []NodeView` / `Edges []EdgeView` / `Cutoffs []CutoffView`)
   - `NodeView` (methodId + symbol 情報。symbol 欠落 = ID のみを許容する構造にする)
   - `EdgeView` (edgeId + 両端 methodId + `Cycle bool` + `CallSite` 相当の位置情報)
   - `CutoffView` (edgeId + 両端 methodId + `TargetMethodID` + `TargetMinDepth` + 位置情報)
   - `Formatter` interface (`Format(w io.Writer, v View) error`)
3. `## 検証コマンド` を実行する (この時点では `View` を構築する関数がまだないため、型定義がコンパイルできることのみ確認できればよい)
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ 2: `View` 構築 (symbol 解決 + sort) を実装する

1. テストを先に書く (TDD): 「`Input` から `View` を構築すると、到達 node すべてが `Nodes` に symbol 解決済みで含まれる」「`Nodes` が `methodId` の辞書順に sort される」「`Edges` / `Cutoffs` が `edgeId` の辞書順に sort される」「`status = startNotFound` のとき `View.Start` が symbol 欠落 (ID のみ) を許容する」「同一 `Input` から構築した `View` が常に同じ内容になる (決定性)」の unit test を書く
2. `Input` から `View` を構築する内部関数を実装する。Graph の読み取り API (`graph.Graph.Node`) で `Result.Nodes` の各 methodId から `Symbol` / `CallSite` を解決し、`Nodes` / `Edges` / `Cutoffs` を id の辞書順に固定する
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ 3: `Write` entry point と Formatter registry を実装する

1. テストを先に書く (TDD): 「未対応 format (未登録 format) を指定すると、何も書き出さずに `error` を返す」「対応 format を指定すると `View` が構築され、対応する Formatter が呼ばれる」「`Write` は format 検証 → `View` 構築 → Formatter 選択の順で処理する」の unit test を書く。Formatter 呼び出しの検証には、テスト専用の仮 Formatter (呼ばれたことを記録するだけの実装、または固定文字列を書き出す実装) を用いてよい
2. `func Write(w io.Writer, f Format, in Input) error` を実装する: ① `f` が未対応 format なら何も書き出さず `error` を返す ② `Input` から `View` を構築する ③ `f` に対応する `Formatter` を選ぶ ④ `formatter.Format(w, view)` を呼ぶ。Console / JSON の実 Formatter は P3 で実装されるため、本 prompt では registry (format → Formatter のマッピング) を用意し、**P3 が差し替えやすい構造**にする (例: package 内の `map[Format]Formatter` にコンストラクタ時 or `init` で登録する、あるいは `Write` 内で明示的に分岐しつつ Console / JSON 分岐は「未登録」を返す仮実装にする、のいずれか。unexported な形で構わない)
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ最終: 最終確認

1. 全テスト / lint / typecheck がパスすることを確認する
2. spec の `## 上位資料からの変更点` に必要な追記がないかを確認する (本 prompt の範囲では追記不要。上位文書への反映は spec #7 の phase: sync で完了済み)
3. PR / MR を Ready に変更しレビュアーを指名する

## 実装コンテキスト

- spec: `specs/7-output/index.md` (決定時スナップショット。D5 / D6 の決定経緯)
- feature doc (durable な契約の正本): `design/features/output/DesignDoc_output.md` (`## 設計 > 公開 entry point と Formatter / View`、`### エラー境界`)
- 参照する appendix:
  - `specs/7-output/index.md` 内の `## 実装対象`、`## 解決済みの論点 > D5` / `D6`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `core/internal/output/output.go` (現在は `package output` のみの空 stub。ここに実装する)
  - `core/internal/graph/graph.go` (`P1_01` で拡張済みの `Node.Symbol` / `Edge.CallSite`。読み取り API 経由でのみ参照する)
  - `core/internal/traversal/traversal.go` (`Result` / `Request` / `Status`。`P1_02` で拡張済みの `minDepth` を含む)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P1_01_core_graph-symbol.md`、`P1_02_traversal_min-depth.md`
- 完了後に着手可能になる後続 prompt: `P3_01_output_console-formatter.md`、`P3_02_output_json-formatter.md` (両者は本 prompt の `View` / `Formatter` を前提に並列実装できる)
- 必要な repo 状態: `core/internal/graph` に `Symbol` / `CallSite`、`core/internal/traversal` に node ごとの `minDepth` が実装済みであること

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- `Format` 定数、`Input` / `View` / `NodeView` / `EdgeView` / `CutoffView` 型、`Formatter` interface
- `Input` → `View` の構築 (symbol 解決 + id 辞書順 sort)
- 公開 entry point `Write(w io.Writer, f Format, in Input) error` (format 検証 / View 構築 / Formatter 選択)
- Formatter registry (P3 が差し替えやすい構造。Console / JSON は仮実装や未登録エラーでよい)

### 実装しない範囲

- Console formatter の tree 構築 (`P3_01_output_console-formatter.md` の責務)
- JSON formatter の schema 実装 (`P3_02_output_json-formatter.md` の責務)
- DOT / Mermaid formatter の実装 (Phase4。本 spec では prompt を生成しない)
- Analyze Use Case からの `output.Write` 呼び出し配線 (CLI interface spec の対象)
- `graph` / `traversal` package の変更

## 設計仕様

以下は `design/features/output/DesignDoc_output.md` (`## 設計 > 公開 entry point と Formatter / View`) からの抜粋。

```go
// core/internal/output

type Format string

const (
    FormatConsole Format = "console"
    FormatJSON    Format = "json"
    FormatDOT     Format = "dot"     // Phase4
    FormatMermaid Format = "mermaid" // Phase4
)

type Input struct {
    Graph   *graph.Graph
    Result  traversal.Result
    Request traversal.Request // direction / start を持つ (Result は保持しない)
}

// Write は Output package の唯一の公開 entry point。
//  1. f が未対応 format なら、何も書き出さずに error を返す
//  2. Input から View を構築する (symbol 解決 + sort)
//  3. f に対応する Formatter を選び、formatter.Format(w, view) を呼ぶ
func Write(w io.Writer, f Format, in Input) error

// 全 formatter が共有する中間表現 (symbol 解決済み / sort 済み)
type View struct {
    Status  traversal.Status
    Start   NodeView
    Nodes   []NodeView   // methodId の辞書順
    Edges   []EdgeView   // edgeId の辞書順。Cycle flag を持つ
    Cutoffs []CutoffView // edgeId の辞書順
}

type Formatter interface {
    Format(w io.Writer, v View) error
}
```

- **決定性の規約は `View` 構築に 1 本化する**: `Nodes` / `Edges` / `Cutoffs` を id の辞書順に固定し、同一 `Input` から常に同一内容の `View` を構築する。
- symbol (`QualifiedName` / `Signature` / `Source` / `CallSite`) は Graph の読み取り API から解決する。`NodeView` は symbol 欠落 (ID のみ。`startNotFound` 時の起点など) を許容する。
- `traversal.Request` を入力に含めるのは、`traversal.Result` が `direction` / `start` を保持しないため。
- **未対応 format の検証・`View` の構築・Formatter の選択は `Write` が担う**。`Formatter` は「`View` を描く」ことだけに責務を絞る。

`## エラー境界` より (該当部分のみ抜粋):

| ケース             | 戻り値                                        |
| ------------------ | --------------------------------------------- |
| 未対応 format 指定 | `error` (出力前に validation。対応形式を案内) |

- `startNotFound` / 到達なしは `Write` では分岐せず、各 Formatter が `View.Status` / `View.Edges` / `View.Cutoffs` を見て形式ごとに表現する (P3 の責務)。本 prompt では `Write` がこれらを理由に `error` を返さないことのみ守ればよい。

## テスト観点

- `Write` に未対応 format (未登録) を渡すと、`w` に何も書き出されず `error` が返ること
- `Write` に対応 format を渡すと、対応する `Formatter.Format` が呼ばれること
- `Input` から `View` を構築すると、到達 node すべてが `Nodes` に symbol 解決済みで含まれること
- `View.Nodes` が `methodId` の辞書順、`View.Edges` / `View.Cutoffs` が `edgeId` の辞書順に sort されること
- 同一 `Input` から構築した `View` が常に同一内容になること (決定性)
- `status = startNotFound` のとき `View.Start` が symbol 欠落 (ID のみ) を許容すること

## 検証コマンド

- ビルド: `cd core && go build ./...`
- Lint / typecheck: `cd core && go vet ./...`
- Format 確認: `cd core && test -z "$(gofmt -l .)"`
- Unit test: `cd core && go test ./...`
- 健全性検査: `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 で `P1_01` / `P1_02` の完了を確認した
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] `Format` / `Input` / `View` / `NodeView` / `EdgeView` / `CutoffView` / `Formatter` が実装されている
- [ ] `Input` から `View` を構築する処理が symbol 解決 + id 辞書順 sort を行う
- [ ] `Write(w io.Writer, f Format, in Input) error` が未対応 format を出力前に弾く
- [ ] Formatter registry が P3 (Console / JSON) から差し替え可能な構造になっている
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (本 prompt では追記不要なことを確認した)
- [ ] PR / MR を Ready に変更しレビュアーを指名した
- [ ] 未解決の仕様質問が残っていない
