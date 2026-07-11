# P3_02: JSON formatter (schema と版管理)

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

### ステップ 1: JSON schema の struct と marshal を実装する

1. テストを先に書く (TDD): 「出力が `encoding/json` でパースでき、`schemaVersion` / `status` / `direction` / `start` / `nodes[]` / `edges[]` / `depthCutoffs[]` を持つ」の golden test を `core/internal/output/testdata/golden/` に配置して書く
2. JSON formatter (`core/internal/output` package 内、新規ファイルを作成してよい) を実装し、`Formatter` interface を満たす。`## 設計仕様` の schema に従った struct を定義し、`encoding/json` で marshal する。`P2_01` の Formatter registry に `FormatJSON` として登録する
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ 2: 要素順序の決定性と `cycle` / optional field の扱いを実装する

1. テストを先に書く (TDD): 「`nodes[]` が `methodId`、`edges[]` / `depthCutoffs[]` が `edgeId` の辞書順で出力される」「同一 `Result` から常に同一のバイト列が得られる」「`cycle` が `false` の場合も field が出力される (省略されない)」「`sourceLocation` / `callSite` が欠落する場合に field ごと省略される」の golden test を追加する
2. `View` (`P2_01` で id 辞書順に sort 済み) をそのまま順に marshal すれば要素順序の決定性が満たされることを確認し、`cycle: false` を省略しない JSON tag (`omitempty` を付けない) と、`sourceLocation` / `callSite` を省略する JSON tag (`omitempty` を付ける、または `*SourceLocationView` のような pointer 型にする) を使い分けて実装する
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ 3: `minDepth` と `depthCutoffs[].targetMethodId` の dangling 参照を実装する

1. テストを先に書く (TDD): 「node の `minDepth` が起点からの最短距離に一致する (合流 graph で最短経路側の値を採ること)」「`depthCutoffs[]` の `targetMethodId` が `nodes[]` に存在しない (dangling) ことを **caller / callee 両方向で**検証する (caller 方向では `targetMethodId == callerMethodId`、callee 方向では `targetMethodId == calleeMethodId` になり、もう一方の endpoint は `nodes[]` に存在する)」の golden test / assertion test を追加する
2. `nodes[].minDepth` を `traversal.Result` の node ごとの minDepth (`P1_02` で公開済み) から埋め、`depthCutoffs[].targetMethodId` / `targetMinDepth` を `traversal.DepthCutoff` (探索方向の接続先 endpoint + `TargetMinDepth`) から埋める実装を行う
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ 4: `startNotFound` / 到達なしの JSON 表現を実装する

1. テストを先に書く (TDD): 「`status = startNotFound` のとき `nodes[]` / `edges[]` が空配列になる」「到達なし (`Edges` も `Cutoffs` も空) のとき `nodes[]` に起点 1 件、`edges[]` が空配列になる」「`Edges` は空だが `Cutoffs` が非空 (`maxDepth=0` 等) のとき `nodes[]` に起点 1 件、`edges[]` が空配列、`depthCutoffs[]` が非空になる」の golden test を追加する
2. `View.Status` / `View.Edges` / `View.Cutoffs` を見て JSON formatter 内で分岐する実装を行う
3. `## 検証コマンド` を実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ最終: 最終確認

1. 全テスト / lint / typecheck がパスすることを確認する
2. spec の `## 上位資料からの変更点` に必要な追記がないかを確認する (本 prompt の範囲では追記不要。上位文書への反映は spec #7 の phase: sync で完了済み)
3. PR / MR を Ready に変更しレビュアーを指名する

## 実装コンテキスト

- spec: `specs/7-output/index.md` (決定時スナップショット。D3 の決定経緯)
- feature doc (durable な契約の正本): `design/features/output/DesignDoc_output.md` (`### JSON 出力 (schema と版管理)`)
- 参照する appendix:
  - `specs/7-output/index.md` 内の `## 機能仕様 > Testing` (JSON の検証観点)
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `core/internal/output/output.go` (`P2_01` で実装済みの `Format` / `Input` / `View` / `Formatter` / Formatter registry。ここに JSON formatter を追加登録する)
  - `core/internal/output/testdata/golden/` (golden fixture の配置先。`P3_01` と共有するディレクトリだがファイル名は重複しない)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P2_01_output_write-view.md`
- 完了後に着手可能になる後続 prompt: なし (Phase4 の DOT / Mermaid prompt は本 spec では生成しない)
- 必要な repo 状態: `core/internal/output` に `Format` / `Input` / `View` / `Formatter` と Formatter registry が実装済みであること。`P3_01_output_console-formatter.md` と並列実行可 (別ファイルを編集するため衝突しない)

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- JSON formatter (`Formatter` interface の実装)
- JSON schema (`schemaVersion` / `status` / `direction` / `start` / `nodes[]` / `edges[]` / `depthCutoffs[]`) の struct と marshal
- 要素順序の決定性、`cycle` の非省略、`sourceLocation` / `callSite` の欠落時省略
- `minDepth` / `depthCutoffs[].targetMethodId` の実装
- golden test + `encoding/json` unmarshal 検証

### 実装しない範囲

- `Format` / `Input` / `View` / `Formatter` / `Write` entry point の変更 (`P2_01` で確定済み。変更が必要なら停止して確認する)
- Console formatter の実装 (`P3_01_output_console-formatter.md` の責務)
- DOT / Mermaid formatter の実装 (Phase4。本 spec では prompt を生成しない)
- `graph` / `traversal` package の変更

## 設計仕様

以下は `design/features/output/DesignDoc_output.md` (`### JSON 出力 (schema と版管理)` と `### 公開 entry point と Formatter / View`) からの抜粋。

#### View (`P2_01` で確定。抜粋)

```go
// 全 formatter が共有する中間表現 (symbol 解決済み / sort 済み)
type View struct {
    Status    traversal.Status
    Direction traversal.Direction // 探索方向 (Request から引き継ぐ)
    Start     NodeView
    Nodes     []NodeView   // methodId の辞書順
    Edges     []EdgeView   // edgeId の辞書順。Cycle flag を持つ
    Cutoffs   []CutoffView // edgeId の辞書順
}
```

- **`"direction"` の出力元は `View.Direction`** (`caller` / `callee` をそのまま文字列化する)。`targetMethodId` の検証観点は従来どおり (下記 `## テスト観点`)。

フラットな graph (`nodes[]` / `edges[]` / `depthCutoffs[]`) として出力し、tree にはしない。field 名は Analyzer Protocol の語彙を踏襲する。

```json
{
  "schemaVersion": "1.0",
  "status": "ok",
  "direction": "caller",
  "start": "<methodId>",
  "nodes": [
    {
      "methodId": "<methodId>",
      "qualifiedName": "com.example.UserService.findById",
      "signature": "(java.lang.Long)",
      "minDepth": 0,
      "sourceLocation": {
        "path": "src/main/java/com/example/UserService.java",
        "startLine": 42
      }
    }
  ],
  "edges": [
    {
      "edgeId": "<edgeId>",
      "callerMethodId": "<methodId>",
      "calleeMethodId": "<methodId>",
      "cycle": false,
      "callSite": {
        "path": "src/main/java/com/example/UserController.java",
        "startLine": 31
      }
    }
  ],
  "depthCutoffs": [
    {
      "edgeId": "<edgeId>",
      "callerMethodId": "<methodId>",
      "calleeMethodId": "<methodId>",
      "targetMethodId": "<methodId>",
      "targetMinDepth": 3,
      "callSite": { "path": "...", "startLine": 12 }
    }
  ]
}
```

- `status` = `ok` / `startNotFound`。`direction` = `caller` / `callee`。
- `edges[].cycle` は `View.EdgeView.Cycle` に対応し、**false でも省略しない**。
- `nodes[].minDepth` は起点からの最短距離。
- `sourceLocation` / `callSite` は欠落時 field ごと省略する。
- **`depthCutoffs[].targetMethodId` は探索方向の接続先** (= dangling する側): `direction=caller` なら `callerMethodId`、`callee` なら `calleeMethodId` と同値。cutoff 先の node は到達集合外のため **`nodes[]` に存在しない**。`targetMinDepth` はこの `targetMethodId` の minDepth。
- **要素順序**: `nodes[]` は `methodId`、`edges[]` / `depthCutoffs[]` は `edgeId` の辞書順に固定する (`View` が既に sort 済みのため、順に marshal すれば満たされる)。

#### 版管理

- `schemaVersion` は Analyzer Protocol とは独立の採番とする。固定値 `"1.0"` を使う。
- field の追加は後方互換 (additive、minor)。削除 / 意味変更 / 型変更は破壊的変更 (major)。利用者は未知 field を無視できることを前提にする。本 prompt では初版 (`"1.0"`) を固定 marshal すればよく、版切り替えロジックは実装しない。

#### エラー境界 (該当部分のみ抜粋)

| ケース                                              | JSON                                                   |
| --------------------------------------------------- | ------------------------------------------------------ |
| 起点不在 (`status=startNotFound`)                   | `status: "startNotFound"` + `nodes` / `edges` は空配列 |
| 到達なし (`Edges` も `Cutoffs` も空)                | 起点 1 件 + 空 `edges`                                 |
| `Edges` は空だが `Cutoffs` が非空 (`maxDepth=0` 等) | 起点 1 件 + 空 `edges` + 非空 `depthCutoffs`           |

## テスト観点

以下は `design/features/output/DesignDoc_output.md#テスト観点` (fixture ケースの durable 正本) と `specs/7-output/index.md#機能仕様--testing` からの抜粋。

- 出力が `encoding/json` でパースでき、`schemaVersion` / `status` / `direction` / `start` / `nodes[]` / `edges[]` / `depthCutoffs[]` を持つこと
- `nodes[]` は `methodId`、`edges[]` / `depthCutoffs[]` は `edgeId` の辞書順で、同一 `Result` から常に同一のバイト列が得られること
- node の `minDepth` が起点 0 からの最短距離に一致すること (合流 graph で最短経路側の値を採ること)
- `cycle` が `false` の場合も field が出力されること。`sourceLocation` / `callSite` が欠落する場合に field ごと省略されること
- `depthCutoffs[]` の `targetMethodId` が `nodes[]` に存在しない (dangling) ことを、**caller / callee の両方向で**検証すること。caller 方向では `targetMethodId == callerMethodId`、callee 方向では `targetMethodId == calleeMethodId` になり、もう一方の endpoint は `nodes[]` に存在すること
- `status = startNotFound` のとき `nodes[]` / `edges[]` が空配列になること
- 到達なし (`Edges` も `Cutoffs` も空) のとき `nodes[]` に起点 1 件、`edges[]` が空配列になること
- `Edges` は空だが `Cutoffs` が非空 (`maxDepth=0` 等) のとき `nodes[]` に起点 1 件、`edges[]` が空配列、`depthCutoffs[]` が非空になること

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
- [ ] JSON formatter が `Formatter` interface を実装し、`FormatJSON` として registry に登録されている
- [ ] JSON schema (`schemaVersion` / `status` / `direction` / `start` / `nodes[]` / `edges[]` / `depthCutoffs[]`) が実装され、`encoding/json` でパースできることが test で検証されている
- [ ] `nodes[]` / `edges[]` / `depthCutoffs[]` の要素順序が id 辞書順で決定的であることが test で検証されている
- [ ] `minDepth` / `depthCutoffs[].targetMethodId` の dangling 参照が caller / callee 両方向で test で検証されている
- [ ] `cycle: false` の非省略、`sourceLocation` / `callSite` 欠落時の省略が test で検証されている
- [ ] `startNotFound` / 到達なし / `maxDepth=0` 相当の JSON 表現が golden test で検証されている
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (本 prompt では追記不要なことを確認した)
- [ ] PR / MR を Ready に変更しレビュアーを指名した
- [ ] 未解決の仕様質問が残っていない
