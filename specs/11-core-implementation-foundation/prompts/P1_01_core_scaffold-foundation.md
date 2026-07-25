# Core scaffold foundation

## 絶対ルール

- spec に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない。
- `core/` には Go scaffold と空 package 境界だけを作る。Protocol parser、Analyzer process 制御、graph、traversal、output のロジックは実装しない。
- `analyzers/java/`、`testdata/analyzer-protocol/`、`testdata/fixtures/` は placeholder directory として Git 追跡可能にするだけに留める。
- Analyzer runtime / implementation への直接 import を Core に追加しない。
- 後続 spec の対象である timeout、stderr 上限、record size 上限、runtime budget、開発ツール version 固定方法を決めない。
- **完了条件のタスク化**: 作業開始前に「完了条件」セクションの各項目を todo として登録し、各ステップ完了時に状態を更新すること。タスク化せずに作業を開始することは禁止。

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

## 作業ステップ

### ステップ 0: ブランチ準備

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` に従う。Issue は `#11`、Branch pattern は `feature/<issue-id>`。

1. 最新の base branch を取得する。
2. 作業ブランチ `feature/11` を作成する。
3. PR テンプレートを確認し、完了条件を description に転記する。
4. Draft PR を作成して push する。
5. 検証: `git status --short` で意図しない変更がないことを確認する。

### ステップ 1: Go module と Cobra root command を作る

1. `core/go.mod` を作成し、module path を `github.com/Fukuemon/depwalk/core` にする。
2. runtime dependency として `github.com/spf13/cobra` を追加する。
3. `core/cmd/depwalk/main.go` と、必要なら `core/internal/cli/root.go` を作成する。
4. root command は Cobra を実際に import / 使用し、`go mod tidy` で Cobra が除去されない最小実装にする。
5. `depwalk analyze ...` の subcommand、引数、exit code、エラー表示は実装しない。
6. 検証: `cd core && go mod tidy`、`cd core && go build ./cmd/depwalk`、`cd core && go test ./...` を実行する。
7. diff レビューを実施し、Cobra root command 以外の CLI interface を先取りしていないことを確認する。

### ステップ 2: Core internal package 境界を作る

1. 次の path を作成する。
   - `core/internal/cli/`
   - `core/internal/analyze/`
   - `core/internal/protocol/`
   - `core/internal/analyzer/`
   - `core/internal/graph/`
   - `core/internal/traversal/`
   - `core/internal/output/`
2. 各 directory に `package` 宣言のみの stub `.go` を置く。必要最小限の package comment は可。
3. package 名は `cli`、`analyze`、`protocol`、`analyzer`、`graph`、`traversal`、`output` とする。
4. Protocol parser、validator、Analyzer process 制御、graph data structure、traversal engine、output formatter は実装しない。
5. 検証: `cd core && go test ./...`、`cd core && go vet ./...`、`cd core && test -z "$(gofmt -l .)"` を実行する。
6. diff レビューを実施し、空 package 境界以外のロジックが入っていないことを確認する。

### ステップ 3: Analyzer / fixture placeholder を作る

1. 次の path を作成する。
   - `analyzers/java/`
   - `testdata/analyzer-protocol/`
   - `testdata/fixtures/`
2. 各 directory に `.gitkeep` などの placeholder file を置き、Git 追跡可能にする。
3. Java Analyzer 実装、fixture 内容、contract test fixture は追加しない。
4. 検証: `git status --short` で placeholder file だけが追加されていることを確認する。
5. diff レビューを実施し、Java 固有 build / runtime 設定を追加していないことを確認する。

### ステップ 4: scaffold validation を通す

1. `cd core && go mod tidy` を実行する。
2. `cd core && go test ./...` を実行する。
3. `cd core && go vet ./...` を実行する。
4. `cd core && go build ./cmd/depwalk` を実行する。
5. `cd core && test -z "$(gofmt -l .)"` を実行する。
6. `cd core && go list -deps ./...` を実行し、結果に `github.com/Fukuemon/depwalk/analyzers`、`analyzers/`、Analyzer runtime library への直接 import が含まれないことを確認する。
7. `cd core && go list -f '{{.ImportPath}} {{.Imports}}' ./...` を実行し、同じ直接 import が含まれないことを確認する。
8. `go mod tidy` 後に `core/go.mod` / `core/go.sum` の意図しない差分が出ていないことを確認する。
9. diff レビューを実施し、検証結果と残差分を PR に反映する。

### ステップ最終: 最終確認

1. `## 検証コマンド` の全コマンドがパスすることを確認する。
2. spec の `## 上位資料からの変更点` に必要な追記がないかを `spec-track` に渡す。
3. PR を Ready に変更し、レビュアーを指名する。

## 実装コンテキスト

- spec: `specs/11-core-implementation-foundation/index.md`
- review: `specs/11-core-implementation-foundation/review.md`
- Issue: `#11`
- Branch: `feature/11`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- Core module path: `github.com/Fukuemon/depwalk/core`
- 正本:
  - `adr/0002-core-implementation-foundation.md`
  - `context/project.md`
  - `context/architecture.md`
  - `context/toolchain.md`
  - `context/testing.md`
  - `context/engineering.md`
- 参照する path:
  - `core/go.mod`
  - `core/cmd/depwalk/`
  - `core/internal/cli/`
  - `core/internal/analyze/`
  - `core/internal/protocol/`
  - `core/internal/analyzer/`
  - `core/internal/graph/`
  - `core/internal/traversal/`
  - `core/internal/output/`
  - `analyzers/java/`
  - `testdata/analyzer-protocol/`
  - `testdata/fixtures/`

## 前提条件

- 完了しているべき phase / 依存 prompt: なし。spec #11 の設計、ADR、context handoff、spec-review PASS は完了済み。
- 完了後に着手可能になる後続 prompt: issue #12 の Analyzer Protocol parser / validator / contract test、Traversal spec、Output spec、Java Analyzer spec、CLI interface spec。
- 必要な repo 状態: `core/`、`analyzers/java/`、`testdata/analyzer-protocol/`、`testdata/fixtures/` の scaffold が未作成、または本 prompt の範囲内で整合可能な状態。
- Go toolchain が利用可能であること。

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める。
- 推測で実装を進めない。
- 質問するときは、止まっている作業単位、判断が必要な論点、選択肢を整理する。
- `core/cmd/depwalk` の root command 表示文言、Cobra version、placeholder file 名、package comment の要否で判断不能になった場合は、最小 scaffold を満たす選択肢を提示して確認する。
- timeout、stderr 上限、record size 上限、runtime budget、開発ツール version 固定方法は後続 spec の未確定事項であり、本 prompt では決めない。

## タスク境界

### 実装する範囲

- `core/go.mod` の作成。
- Cobra 依存の追加。
- `core/cmd/depwalk/` の最小 `main` と Cobra root command。
- `core/internal/{cli,analyze,protocol,analyzer,graph,traversal,output}` の空 package stub。
- `analyzers/java/`、`testdata/analyzer-protocol/`、`testdata/fixtures/` の placeholder。
- scaffold validation と依存境界 smoke check。

### 実装しない範囲

- Analyzer Protocol / SPI / Model schema の再設計。
- Protocol parser、Protocol validator、contract test code、fixture 内容。
- Java Analyzer の AST 解析、型解決、DI 解決方式、build / runtime 設定。
- Traversal engine、graph data structure、caller / callee 探索。
- Console / JSON / DOT / Mermaid 出力。
- `depwalk analyze ...` の引数、exit code、エラー表示。
- Runtime Trace、APM、Reflection、AspectJ Runtime、実行時 Proxy 解析。
- IDE Plugin / Web UI / サーバ常駐。
- `golangci-lint` / `govulncheck` の導入や version 固定。

## 設計仕様

- Core 実装言語は Go、dependency 管理は Go modules、task runner は初期導入なし、test framework は Go 標準 `testing`。
- 初期 runtime dependency は CLI framework の `github.com/spf13/cobra` のみに抑える。
- Analyzer Protocol / JSONL / process 実行 / graph / output / test は標準ライブラリと内部 package で開始する。
- Core module path は `github.com/Fukuemon/depwalk/core`。
- Core と Analyzer は top-level directory を分ける。Core は `core/`、言語別 Analyzer は `analyzers/<language>/`。
- 本 prompt で作る directory / package 構成:

```text
depwalk/
├── core/
│   ├── go.mod
│   ├── cmd/
│   │   └── depwalk/
│   └── internal/
│       ├── cli/
│       ├── analyze/
│       ├── protocol/
│       ├── analyzer/
│       ├── graph/
│       ├── traversal/
│       └── output/
├── analyzers/
│   └── java/
└── testdata/
    ├── analyzer-protocol/
    └── fixtures/
```

- `cmd/depwalk` は Cobra root command を起動できる最小状態にする。`depwalk analyze` の interface は実装しない。
- `core/internal/...` は `package` 宣言のみの stub `.go` を持つ compile 対象の空 package に留める。
- `analyzers/java/`、`testdata/analyzer-protocol/`、`testdata/fixtures/` は placeholder file で Git 追跡可能にする。
- WHEN Core 実装を scaffold する時、開発者は ADR で承認された Core 実装言語と package manager を使用する。
- WHEN Core と Analyzer の境界を実装する時、システムは ADR-0001 と Analyzer Protocol feature doc に定義された JSONL process SPI を変更せずに実装する。
- WHEN 新しい Analyzer を追加する時、Core は Analyzer の内部 runtime / library に直接依存しない。
- IF 選定候補が Core に特定 Analyzer runtime への直接依存を要求する時、その候補は Design Doc P1-P4 と矛盾するため採用しない。
- WHEN scaffold validation を実行する時、Core の dependency graph と import は `analyzers/<language>/` 実装や Analyzer runtime library を含まないことを確認できる。

## テスト観点

- spec #11 の最小 scaffold 後、`cd core && go test ./...`、`cd core && go vet ./...`、`cd core && go build ./cmd/depwalk`、`cd core && test -z "$(gofmt -l .)"` が成功すること。
- `cd core && go mod tidy` 実行後に、`go.mod` / `go.sum` の意図しない差分が出ないこと。
- `go fmt ./...` は format 適用コマンドであり、quality gate の判定は `gofmt -l` の空確認を正とする。
- scaffold validation では、`cd core && go list -deps ./...` と `cd core && go list -f '{{.ImportPath}} {{.Imports}}' ./...` の結果に、`github.com/Fukuemon/depwalk/analyzers`、`analyzers/`、Analyzer runtime library への直接 import が含まれないことを確認する。
- Protocol strict validation、nil slice / nil map、Analyzer process contract の詳細検証は issue #12 で扱い、本 prompt では実装しない。
- 初期 test framework は Go 標準の `testing` とし、assertion library / mock generator は導入しない。

## 検証コマンド

- `cd core && go mod tidy`
- `cd core && go test ./...`
- `cd core && go vet ./...`
- `cd core && go build ./cmd/depwalk`
- `cd core && test -z "$(gofmt -l .)"`
- `cd core && go list -deps ./...`
- `cd core && go list -f '{{.ImportPath}} {{.Imports}}' ./...`
- `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 でブランチと Draft PR を作成した。
- [ ] `core/go.mod` が `github.com/Fukuemon/depwalk/core` で作成され、Cobra 依存が追加されている。
- [ ] `core/cmd/depwalk/` に最小 `main` と Cobra root command がある。
- [ ] `core/internal/{cli,analyze,protocol,analyzer,graph,traversal,output}` に `package` 宣言のみの stub `.go` がある。
- [ ] `analyzers/java/`、`testdata/analyzer-protocol/`、`testdata/fixtures/` が placeholder file で Git 追跡可能になっている。
- [ ] Protocol parser、validator、contract test、Traversal、Output、Java Analyzer、CLI interface の実装ロジックを先取りしていない。
- [ ] `cd core && go mod tidy` 後に `core/go.mod` / `core/go.sum` の意図しない差分が出ていない。
- [ ] `cd core && go test ./...` がパスする。
- [ ] `cd core && go vet ./...` がパスする。
- [ ] `cd core && go build ./cmd/depwalk` がパスする。
- [ ] `cd core && test -z "$(gofmt -l .)"` がパスする。
- [ ] `go list -deps` / `go list -f '{{.ImportPath}} {{.Imports}}'` で Analyzer runtime / implementation への直接 import がないことを確認した。
- [ ] `lefthook run pre-commit` がパスする、または未実行理由を PR に明記した。
- [ ] 各ステップで diff レビューを実施し、指摘を対応した。
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った、または追記不要を確認した。
- [ ] PR を Ready に変更しレビュアーを指名した。
- [ ] 未解決の仕様質問が残っていない。
