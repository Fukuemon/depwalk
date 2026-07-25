# wire 変換を ACL 化し port + 手動 DI で依存方向を内向きに是正する

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止。対象ファイルは本 prompt に記載済み)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- DI ライブラリ (`google/wire` 等)・コード生成・新規外部依存を導入しない (手動 DI のみ)
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

1. 最新の develop から `feature/34` を作成する
2. Draft PR を作成して push する (PR 本文に完了条件を転記。`Closes #34`)

### ステップ 1: domain の自前値型

1. テストを先に書く / 更新する (TDD。graph の値型テスト)
2. `graph` に自前の `SourceLocation` 値型を定義し、`Symbol.Source` / `Edge.CallSite` を `*graph.SourceLocation` (自前型) へ置換する。`graph/convert.go` の変換関数 (`NodeFromMethodSymbol` 等) と deep copy 補助は `protocol` 側へ移す前提でこのステップでは仮置きしてよい
3. `## 検証コマンド` を実行する
4. diff レビューを回し、指摘を対応してから次へ

### ステップ 2: port 定義と ACL (Translator + Adapter)

1. テストを先に書く / 更新する
2. `analyze` に「domain 型 (graph の値型) を返す解析結果供給の port interface」を利用側ファイル内に小さく定義する (`port/` package は作らない。1〜2 メソッド、必要なら非公開)
3. `protocol` に ACL を実装する: wire DTO (`MethodSymbol` / `CallEdge` / `SourceLocation`) → domain 型 (`graph.Node` / `graph.Edge` / `graph.SourceLocation`) の Translator (deep copy 含む) と、`analyzer` (process 制御) を利用して port を満たす Adapter
4. `analyze` / `output` / `graph` から `protocol` への import を除去する
5. `## 検証コマンド` を実行し、diff レビューを回す

### ステップ 3: cli の手動 DI 配線

1. `cli` で ACL adapter を `analyze` の port へコンストラクタ注入する手動 DI に整理し、`cli` の内層迂回参照を配線目的に限定する
2. `var _ <port> = (*<Adapter>)(nil)` の interface 満足検証を cli の配線箇所に集約する
3. `## 検証コマンド` を実行し、diff レビューを回す

### ステップ最終: 最終確認

1. `core/` 配下で `protocol` を import しているのが `cli` (配線) と `protocol` 自身のみであることを確認する (`go list` の import 確認は許可された検証手順)
2. 全テスト / vet / fmt / E2E がパスすることを確認し、E2E 実行時間が現行から大きく逸脱していないことを確認する
3. commit する (PR は P2_01 完了まで Draft のまま)

## 実装コンテキスト

- spec: `specs/32-architecture-refactor/index.md` (解決済みの論点 D6 / Interface 設計 / フロー・シーケンス)
- 正本: `adr/0007-layered-architecture-refactor.md`、`context/architecture.md`、`design/features/graph/DesignDoc_graph.md` (データ構造 / 構築と公開の原子性)
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path: `core/internal/graph/` (graph.go / builder.go / convert.go)、`core/internal/analyze/`、`core/internal/protocol/`、`core/internal/analyzer/`、`core/internal/output/` (types.go / console.go / json.go)、`core/internal/cli/` (フラット構成を維持する。D8)

## 前提条件

- 完了しているべき phase / 依存 prompt: なし (issue #34 の最初の prompt)
- 完了後に着手可能になる後続 prompt: `P2_01_core_depguard-depgraph.md`
- 必要な repo 状態: develop が spec #32 (PR #36) マージ済み

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 変換コストや重複モデルが過大と判断した場合は実装を進めず停止して報告する (spec #32 例外シナリオ 2: trade-off 提示 → 必要なら ADR-0007 へ例外を記録)
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- `graph` の自前 `SourceLocation` 値型と `Symbol` / `Edge` の型置換
- `analyze` の port interface 定義 (利用側・小さく)
- `protocol` の Translator + Adapter (ACL)、変換関数の移設
- `graph` / `output` / `analyze` からの `protocol` import 除去、`cli` の手動 DI 配線と `var _` 集約

### 実装しない範囲

- package の物理移動・改名 (フラット構成を維持する。D8 で層ディレクトリ化は撤回済み)
- depguard / lint / 依存図生成の導入 — P2_01 の責務
- JSONL Protocol schema / parse / validate 仕様の変更 (protocol package の検証ロジックは不変)
- staging Graph の公開 / 破棄セマンティクスの変更 (挙動は現状維持。担当が ACL に移るだけ)

## 設計仕様

spec #32 D6 (確定) の抜粋:

- `graph` が自前の `Symbol` / `SourceLocation` 相当型を持ち、protocol import をゼロにする (受け入れ基準 2)。wire 型との重複定義は境界隔離のコストとして許容する
- `analyze` は domain 型を返す port interface を定義し、`protocol` (adapter) が wire → domain 変換を担って port を実装する
- 依存方向は architecture.md の package 単位の依存規則を例外なしで成立させる (`graph` / `analyze` / `output` から `protocol` への import ゼロ)
- 配線は `cli` でのコンストラクタ注入による手動 DI。`google/wire` 等は導入しない
- port は利用側 (`analyze`) のファイル内に小さく定義し、`port/` 専用 package を作らない。analyze は struct として公開し、cli 向けの先回り interface を作らない。`var _` 検証は cli に集約
- graph feature doc (改訂済み): 変換は 1 回だけ。wire 専用フィールド (`schemaVersion` / `recordType`) は graph model に持ち込まない。metadata は deep copy。`sourceLocation` / `callSite` は nil 許容
- 構築と公開の原子性 (不変): valid record を受領順に staging Graph へ登録し、process 成功 + 参照完全性成立時のみ公開。fatal / 非ゼロ exit / parse・schema error 時は staging と先行 diagnostic を全破棄

依存図 (辺は import 方向):

```text
cli → {protocol(配線+var _), analyzer(配線), analyze, output}
protocol → {analyze(port 実装), analyzer(process 起動に利用), graph}
output → {graph, traversal} / analyze → {graph, traversal} / traversal → graph
analyzer → (内層 import なし)
```

## テスト観点

- 既存テストスイート (unit / E2E / golden) がテスト本体のロジック変更なしで全件 PASS (変換の所在変更に伴うテストの import / 配線修正は可)
- graph model に wire 型 / wire 専用フィールドが現れない
- fatal 経路 (破棄) と成功経路 (公開) の挙動が現状と同一
- E2E 実行時間が現行から大きく逸脱しない (変換層は既存変換の再配置であり追加走査を導入しない)

## 検証コマンド

- `cd core && go build ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `cd core && go test ./...`
- `(cd analyzers/java && ./gradlew shadowJar) && ./analyzers/java/gradlew --no-daemon -p testdata/fixtures/java/spring-project clean writeDepwalkClasspath && (cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -count=1)` (要 JDK 25)
- `lefthook run pre-commit`

## 完了条件

- [ ] `graph` に自前 `SourceLocation` があり、`Symbol.Source` / `Edge.CallSite` が自前型になっている
- [ ] `graph` / `traversal` / `analyze` / `output` から `protocol` への import がゼロ
- [ ] port が `analyze` 内に小さく定義され、`protocol` が実装している
- [ ] `cli` の手動 DI 配線と `var _` 検証が cli に集約されている
- [ ] `## 検証コマンド` がすべてパスし、E2E 実行時間が大きく逸脱していない
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] 未解決の仕様質問が残っていない
