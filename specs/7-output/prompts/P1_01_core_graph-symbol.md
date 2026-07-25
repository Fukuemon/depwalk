# P1_01: Graph に symbol 値型を追加する (Node.Symbol / Edge.CallSite)

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

### ステップ 1: `graph.Symbol` 値型を追加し `Node` / `Edge` を拡張する

1. テストを先に書く (TDD): 「`AddNode` で登録した `Symbol` を `Node` から取得できる」「`AddEdge` で登録した `CallSite` を `Edge` から取得できる」「`Symbol.Source` / `Edge.CallSite` が nil でも panic しない」の unit test を `core/internal/graph/graph_test.go` に追加する
2. `core/internal/graph/graph.go` に `Symbol` 型 (`QualifiedName string` / `Signature string` / `Source *protocol.SourceLocation`) を追加し、`Node` に `Symbol Symbol` field、`Edge` に `CallSite *protocol.SourceLocation` field を追加する (`## 設計仕様` の型定義をそのまま使う)
3. 既存の `Node{ID: ...}` / `Edge{ID: ..., CallerID: ..., CalleeID: ...}` リテラルは `Symbol` / `CallSite` を省略しても zero value でコンパイルが通ることを確認する (additive 拡張。既存 test を書き換えない)
4. `## 検証コマンド` を実行する
5. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
6. 指摘を対応してから次へ

### ステップ 2: wire record → graph 値型の変換関数を追加する

1. テストを先に書く (TDD): 「`protocol.MethodSymbol` から `graph.Node` へ変換すると `MethodID` が `ID` に、`QualifiedName` / `Signature` / `Source` が `Symbol` にマップされる」「`protocol.CallEdge` から `graph.Edge` へ変換すると `EdgeID` / `CallerMethodID` / `CalleeMethodID` / `CallSite` がマップされる」「`Source` / `CallSite` が nil の record からも変換できる (nil のまま伝播する)」「変換結果に `schemaVersion` / `recordType` に相当する field が存在しない (型定義上そもそも持てない)」の unit test を書く
2. `core/internal/graph` package に `protocol.MethodSymbol` → `graph.Node`、`protocol.CallEdge` → `graph.Edge` への変換関数を追加する (関数名は Go の慣用に従い自分で決めてよい。例: `NodeFromSymbol` / `EdgeFromCallEdge` 等)。**Analyze Use Case 層からこの変換関数を呼び出す配線は実装しない** (`## タスク境界 > 実装しない範囲` を参照。CLI interface spec の対象でスコープ外)
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ 3: test builder (`builder.go`) を対応更新する

1. テストを先に書く (TDD): 「builder で `Symbol` / `CallSite` を指定して node / edge を組み立てられる」「`Symbol` / `CallSite` を指定しない既存の `Node(id)` / `Edge(id, caller, callee)` 呼び出しがそのまま動く (後方互換)」の unit test を `core/internal/graph/builder_test.go` に追加する
2. `core/internal/graph/builder.go` の `Builder` に、`Symbol` / `CallSite` を指定できる API を追加する (既存の `Node(id string) *Builder` / `Edge(id, callerID, calleeID string) *Builder` は残し、シグネチャを壊さない。新規メソッドとして追加するか、既存メソッドを維持したまま別名メソッドを足す形にする)
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ最終: 最終確認

1. 全テスト / lint / typecheck がパスすることを確認する
2. spec の `## 上位資料からの変更点` に必要な追記がないかを確認する (本 prompt の範囲では追記不要。上位文書への反映は spec #7 の phase: sync で完了済み)
3. PR / MR を Ready に変更しレビュアーを指名する

## 実装コンテキスト

- spec: `specs/7-output/index.md` (決定時スナップショット。D1 の決定経緯)
- feature doc (durable な契約の正本): `design/features/graph/DesignDoc_graph.md` (`## 設計 > データ構造 / コンテンツモデル`)
- 参照する appendix:
  - `specs/7-output/index.md` 内の `## 実装対象`、`## 解決済みの論点 > D1`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `core/internal/graph/graph.go` (現行実装。`Node` / `Edge` はここにある)
  - `core/internal/graph/builder.go` (test builder。ここに対応追加する)
  - `core/internal/protocol/types.go` (`MethodSymbol` / `CallEdge` / `SourceLocation` の wire schema。フィールド名はこのファイルの定義をそのまま使う。変更しない)

## 前提条件

- 完了しているべき phase / 依存 prompt: なし (P1_02 と並列実行可。両者は別ファイルを編集するため衝突しない)
- 完了後に着手可能になる後続 prompt: `P2_01_output_write-view.md`
- 必要な repo 状態: `core/internal/graph` に #6 で実装済みの `Node{ID}` / `Edge{ID, CallerID, CalleeID}` があること (現行実装のまま)

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- `core/internal/graph` package: `Symbol` 値型、`Node.Symbol` / `Edge.CallSite` field の追加
- `protocol.MethodSymbol` / `protocol.CallEdge` (wire record) → `graph` 値型への変換関数
- test builder (`builder.go`) の `Symbol` / `CallSite` 対応

### 実装しない範囲

- Analyze Use Case 層からの変換関数の呼び出し配線 (CLI interface spec の対象。本 prompt はスコープ外)
- `traversal` package の変更 (`P1_02_traversal_min-depth.md` の責務)
- `output` package の実装 (`P2_01` 以降の責務)
- `protocol.MethodSymbol` / `CallEdge` / `SourceLocation` の wire schema 変更

## 設計仕様

以下は `design/features/graph/DesignDoc_graph.md` (`## 設計 > データ構造 / コンテンツモデル`) からの抜粋。

```go
// core/internal/graph
type Node struct {
    ID     string // Analyzer の methodId (不透明な stable ID)
    Symbol Symbol
}

type Symbol struct {
    QualifiedName string
    Signature     string
    Source        *protocol.SourceLocation // 宣言位置 (optional)
}

type Edge struct {
    ID       string
    CallerID string
    CalleeID string
    CallSite *protocol.SourceLocation // 呼び出し箇所 (optional)
}
```

- **変換は graph 構築時 (Analyze Use Case 層) に 1 回だけ**行う契約。本 prompt は変換関数を `graph` package に用意するところまでで、呼び出し配線 (Analyze Use Case からの呼び出し) は対象外。
- **wire 専用フィールド (`schemaVersion` / `recordType`) は graph model に持ち込まない**。
- `SourceLocation` は `protocol` package の型をそのまま再利用する (再定義しない)。
- `sourceLocation` / `callSite` は Protocol 上 optional であり、graph でも nil を許容する。

`core/internal/protocol/types.go` の wire record 定義 (変更しない。変換元として使う):

```go
type MethodSymbol struct {
    MethodID      string
    QualifiedName string
    Signature     string
    Source        *SourceLocation `json:"sourceLocation,omitempty"`
    // ほか SchemaVersion / RecordType / Language / SymbolKind / Metadata は wire 専用
}

type CallEdge struct {
    EdgeID         string
    CallerMethodID string
    CalleeMethodID string
    CallSite       *SourceLocation `json:"callSite,omitempty"`
    // ほか SchemaVersion / RecordType / Metadata は wire 専用
}
```

## テスト観点

- `Node.Symbol` / `Edge.CallSite` を登録・取得できること
- `Symbol.Source` / `Edge.CallSite` が nil でも panic しないこと (optional の扱い)
- `protocol.MethodSymbol` → `graph.Node`、`protocol.CallEdge` → `graph.Edge` の変換で `QualifiedName` / `Signature` / `Source` / `CallSite` が正しくマップされること
- 変換元の wire record が `Source` / `CallSite` を持たない場合も変換できること (nil のまま伝播)
- graph model に `schemaVersion` / `recordType` に相当する field が存在しないこと (型定義のレビューで確認する。実行時アサーションは不要)
- test builder が `Symbol` / `CallSite` を指定して graph を組み立てられ、既存の `Symbol` / `CallSite` を指定しない呼び出しがそのまま動くこと (後方互換)

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
- [ ] `graph.Symbol` 値型が追加され、`Node.Symbol` / `Edge.CallSite` が使える
- [ ] `protocol.MethodSymbol` / `CallEdge` → `graph` 値型の変換関数が実装されている (Analyze Use Case への配線はしていない)
- [ ] test builder が `Symbol` / `CallSite` 対応済みで、既存呼び出しの後方互換が保たれている
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (本 prompt では追記不要なことを確認した)
- [ ] PR / MR を Ready に変更しレビュアーを指名した
- [ ] 未解決の仕様質問が残っていない
