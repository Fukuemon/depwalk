# Coreの複数root request、staging Graph、構造化failure処理

## 絶対ルール

- spec と上位正本に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止する。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証と diff レビューをスキップしない。
- CoreはGradle、Java、Analyzer固有error code、metadata keyを解釈しない。
- Traversalと成功時Output schemaを変更しない。
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

## 作業ステップ (この順序で実行する)

### ステップ 0: ブランチ準備

1. 現在のbranchが`feature/24`であることを確認する。
2. `git status --short`で意図しない差分がないことを確認する。
3. `P1_01_analyzer-protocol_multi-root-failure-contract.md`が完了していることを確認する。
4. Issue #24の既存Draft PRを流用し、本promptの完了条件をdescriptionへ追記する。
5. P2_02と並列実行する場合は、Core pathだけを変更しJava Analyzer pathへ入らないことを確認する。

### ステップ 1: `--source-root`からrequestを組み立てる

1. TDDでCobra flagと`analyze.Options`のtestを追加する。
2. repeatableな`--source-root <path>`を追加し、指定順を保持して`AnalysisRequest.SourceRoots`へ渡す。
3. 未指定時はfieldを省略する。空値やinvalid pathはProtocol validationのerrorとしてAnalyzer起動前に返す。
4. Coreはrootの存在、Gradle project、source set、Java package hierarchyを解釈しない。
5. CLI helpには、source root省略時は選択したAnalyzerのbuild-model discoveryへ委譲され、外部build toolによるbuild logic評価、network、credential provider、cache更新が発生し得ることと、明示rootでdiscoveryをbypassできることを言語共通の注意として常時表示する。Gradle固有のversionや実行詳細は書かない。
6. Analyzer stderrはProtocolとしてparseせず、成功・fatalのどちらでも受信順のままCore CLI stderrへ転送する。Coreは安全通知、metrics、Analyzer固有categoryを解釈しない。
7. Go testを実行し、P1のwire契約と一致することをdiffレビューする。

### ステップ 2: Graph Symbolへopaque metadataをdeep copyする

1. TDDでProtocol DTOからGraphへのmetadata変換testを追加する。
2. `graph.Symbol`へoptionalなgraph-owned metadataを追加する。
3. string、number、boolean、null、object、arrayを構造的に保持し、nested object / arrayまでdeep copyする。
4. metadata省略と空objectを混同しない。JSON表現不能値を文字列化・部分破棄しない。
5. Graph、Traversal、Outputはmetadata keyの意味を解釈しない。成功時Console / JSON / DOT / Mermaid出力へmetadataを追加しない。
6. 元Protocol DTOのnested値を変えてもGraph値が変わらないtestを追加する。
7. Go testを実行し、Traversal / Outputの観測可能契約が変わっていないことをdiffレビューする。

### ステップ 3: 非公開staging Graphとrequest原子性を実装する

1. TDDで、正常stream、edge先行、valid error、非ゼロexit、malformed recordのprocess testを追加する。
2. Analyzer stdoutのvalidなmethod / edgeを受信ごとにProtocol DTOからgraph-owned値へ変換し、非公開staging Graphへ1-pass登録する。
3. wire DTO全件をprocess終了まで保持する方式を残さない。diagnosticは成功確定まで非公開のrequest stateへ保持する。
4. validなerror recordまたは非ゼロexitではstaging Graphと先行diagnosticを破棄する。fatal requestではedge参照完全性を要求せず、元のfatal reasonを維持する。
5. errorなし、exit 0、stream全体のnode / edge参照完全性成功時だけGraph、diagnostic、件数を成功結果として公開する。
6. malformed JSON、invalid schema、正常exitだが参照不整合はProtocol failureとし、staging Graphを破棄する。
7. Analyzer側の全graph bufferやCore側のwire DTO全件bufferを追加しない。
8. Go testを実行し、request原子性とstreaming変換をdiffレビューする。

### ステップ 4: 構造化failureを言語非依存に表示する

1. TDDでunknown Analyzer code / metadataを持つ複数detailのCLI testを追加する。
2. Analyzer errorをtop-level source location / metadata / detailsまで保持する構造化failureとしてAnalyze Use CaseからCLIへ渡す。
3. CLIはtop-level summaryの後、各detailを配列順に表示する。共通fieldのindex、source location、code、messageを表示する。
4. detail metadataはobject keyを辞書順、array順を入力順に保つcompact canonical JSONで表示する。
5. Java固有code、`reason`、`target`、`candidates`などのmetadata keyで分岐しない。
6. success時のstdoutと既存graph output schemaを変更しない。
7. Go testを実行し、2つ目のAnalyzerを模したgeneric fixtureでも同じrendererが動くことをdiffレビューする。

### ステップ最終: 最終確認

1. `## 検証コマンド`をすべて実行する。
2. `go list`でJava / Gradle runtimeへの直接依存がないことを確認する。
3. P2_02のJava Analyzer pathへ変更していないことを確認する。
4. durable設計との差分が必要なら実装せずspec-lifecycleのtrackへ戻す。
5. 最終diffレビューを行い、指摘を対応する。

## 実装コンテキスト

- spec: `specs/24-gradle-multi-module-source-roots/index.md`
- Protocol正本: `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md`
- Graph正本: `design/features/graph/DesignDoc_graph.md`
- architecture: `context/architecture.md`
- ADR: `adr/0001-analyzer-protocol-jsonl-spi.md`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照・変更するpath:
  - `core/internal/cli/analyze.go`
  - `core/internal/cli/analyze_test.go`
  - `core/internal/analyze/analyze.go`
  - `core/internal/analyze/*_test.go`
  - `core/internal/analyzer/runner.go`
  - `core/internal/analyzer/runner_test.go`
  - `core/internal/graph/graph.go`
  - `core/internal/graph/convert.go`
  - `core/internal/graph/*_test.go`
  - `core/internal/protocol/`
  - `testdata/analyzer-protocol/scenarios/`
- 参照のみのpath:
  - `core/internal/traversal/`
  - `core/internal/output/`

## 前提条件

- 完了しているべきphase / 依存prompt: `P1_01_analyzer-protocol_multi-root-failure-contract.md`。
- 同一phaseで並列実行可能: `P2_02_java-analyzer_gradle-model-provider.md`。
- 完了後に着手可能になる後続prompt: `P6_01_core_required-cli-e2e.md`。
- 必要なrepo状態: P1のGo Protocol DTO / validation / fixtureが実装済みであること。

## 不明点ハンドリング

- 矛盾、欠落、未定義を見つけたら作業を止める。
- GradleやJava固有の条件をCoreへ推測で追加しない。
- staging Graphの所有者、failure型、CLI error伝播で既存APIと両立不能な場合は、変更候補と影響範囲を提示して確認する。
- 質問時は、止まっている作業単位、判断論点、選択肢、Coreの言語非依存性とrequest原子性への影響を整理する。

## タスク境界

### 実装する範囲

- repeatable `--source-root`とProtocol requestへの順序保持。
- Graph Symbolのopaque metadata deep copy。
- valid recordのstaging Graphへの逐次変換、成功時公開、fatal時破棄。
- 構造化Analyzer failureの保持と共通CLI renderer。
- Go unit / process contract test。

### 実装しない範囲

- Java Analyzer、Gradle Tooling API、custom model provider。
- root存在・symlink・包含・source set・language levelの解釈。
- Java固有error code / metadata keyの整形。
- Traversalロジックと成功時Output schema。
- 実Core CLI / 実Java Analyzerのrequired E2Eとrecording proxy。これはP6の責務である。

## 設計仕様

- Core CLIはrepeatable `--source-root`を指定順で`analysisRequest.sourceRoots`へ渡し、未指定時はfieldを省略する。
- CLI helpはAnalyzer discoveryが外部build toolingの評価・network・credential provider・cache更新を伴い得ることと、明示rootでbypassできることを言語共通に説明する。Analyzer stderrはparseせずCLI stderrへ転送する。
- CoreはGradle / Java固有のdiscoveryと型解決を知らない。
- Protocol recordは受信ごとにgraph-owned値へ変換して非公開staging Graphへ登録する。wire DTO全件は保持しない。
- Graph Symbol metadataはopaque JSONとしてnested値までdeep copyし、Traversalと既存Outputは解釈・露出しない。
- valid errorまたは非ゼロexitは同requestの先行recordをすべて無効にする。Graph、diagnostic、件数を成功結果として返さない。
- fatal requestではstaging Graphの参照完全性を要求せず、fatal reasonを参照不整合で上書きしない。
- exit 0、fatal不在、stream全体の参照完全性成功時だけGraphを公開する。
- Coreは共通FailureDetailを配列順に表示し、metadataはcanonical JSONで汎用表示する。Analyzer固有code / keyで分岐しない。

## テスト観点

- `--source-root`未指定ではfield省略、複数指定では順序保持、invalid値はAnalyzer起動前に失敗する。
- CLI helpにdiscovery副作用と明示bypassが常時表示され、Analyzer stderrの通知 / metricsが成功・fatalの両方で内容を解釈されず転送される。
- unknown nested metadataをGraphへdeep copyし、元DTO変更後もGraph値が変わらない。
- metadata省略と空objectを区別し、Traversal / Output結果がmetadataに依存しない。
- method / edgeを受信ごとにstaging Graphへ登録し、wire DTO全件を保持しない。
- edge先行の正常streamは終了後参照検査で成功できる。
- valid errorまたは非ゼロexitでは先行Graph / diagnosticを破棄し、fatal reasonを維持する。
- malformed recordと正常exit時の参照不整合はProtocol failureになる。
- unknown Analyzer code / metadataを持つdetailを共通fieldとcanonical JSONで順序どおり表示する。
- success時stdout / Output schemaが退行しない。

## 検証コマンド

- `cd core && go test ./internal/protocol/... ./internal/graph/... ./internal/analyzer/... ./internal/analyze/... ./internal/cli/...`
- `cd core && go test ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `cd core && go list -deps ./...`
- `git diff --check`

## 完了条件

- [ ] ステップ0でbranch、差分、P1完了、並列path境界を確認した。
- [ ] repeatable `--source-root`を指定順でrequestへ渡し、未指定時はfieldを省略した。
- [ ] CLI helpへ言語共通のdiscovery副作用 / 明示bypass説明を追加し、Analyzer stderrを解釈せず転送した。
- [ ] CoreへGradle / Java固有解釈を追加していない。
- [ ] Graph Symbol metadataをnested値までdeep copyし、省略と空objectを区別した。
- [ ] Traversalと成功時Output schemaがmetadataに依存していない。
- [ ] valid recordを受信ごとに非公開staging Graphへ1-pass登録した。
- [ ] success時だけGraph / diagnostic / 件数を公開し、fatal時は先行stateを破棄した。
- [ ] fatal requestで参照完全性を要求せず、fatal reasonを維持した。
- [ ] 構造化failureの全共通fieldとdetailsを保持した。
- [ ] Analyzer固有code / metadata keyに依存しないCLI rendererを実装した。
- [ ] wire DTO全件buffer、Analyzer全graph buffer、成功時Output変更を追加していない。
- [ ] 全作業ステップとdiffレビューを完了した。
- [ ] `## 検証コマンド`がすべてパスした。
- [ ] 未解決の仕様質問が残っていない。
