# 実Core CLIと実Java Analyzerを通すrequired E2E

## 絶対ルール

- spec と上位正本に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止する。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証と diff レビューをスキップしない。
- test-only proxyをfake Analyzer、record変換器、production debug機能にしない。
- 実Analyzer起動・capture・期待集合照合の失敗をskipまたは成功へ降格しない。
- Java Analyzer production code、Protocol schema、Traversal / Outputを変更しない。
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
3. `P2_01_core_request-staging-failure.md`と`P5_01_java-analyzer_fixture-compatibility-security.md`が完了していることを確認する。
4. Issue #24の既存Draft PRを流用し、本promptの完了条件をdescriptionへ追記する。
5. Java Analyzer jar、fixture classes、classpath manifestを事前buildできることを確認する。

### ステップ 1: test-only透過recording proxyを実装する

1. proxyはCore CLIの既存`--analyzer-cmd`から起動できるtest helperとして`core/e2e/`またはtest専用commandへ置く。
2. Coreから受けたstdin bytesを実Analyzer stdinへ変換せず転送する。
3. 実Analyzerのstdout / stderr / exit statusをCoreへ変換せず中継する。
4. request、raw stdout JSONL、stderr、exit statusの複製だけをtestごとのtemporary directoryへ記録する。
5. proxyはrecordを補完、再順序化、再serialize、filterしない。production option / Protocol / graph output schemaを変更しない。
6. capture directoryは解析workspace外に置き、test終了時に削除する。
7. stdin、stdout、stderr、exitのbyte透過性と、実Analyzer起動 / capture失敗時の非ゼロ終了をunit testで固定する。
8. Go testを実行し、fake Analyzerになっていないことをdiffレビューする。

### ステップ 2: 自動discoveryの実CLI E2Eを追加する

1. build済み実Core CLI binaryへmulti-module fixture workspace、`--language java`、proxyを指す`--analyzer-cmd`だけを渡す。
2. captured requestに`sourceRoots`、`metadata.classpath`、`metadata.javaLanguageLevel`、`metadata.javaPreview`が存在しないことを検証する。
3. raw Analyzer JSONLからmethod / edge / diagnostic / owner metadataをparseし、P5の固定期待集合と完全一致させる。
4. stderrのdiscovery通知、project / context / root数、inventory / outcome、`silentOmission = 0`を固定期待値と照合する。Gradle由来自由文がないことも確認する。
5. Core CLI stdout / stderr / exit statusを同じrunで検証し、Analyzer終了前の成功やGraph件数公開を許さない。
6. include / excludeをCLIから渡す既存経路が未実装の場合は、本promptで新しいflagを推測追加せず、既存Core APIまたは下位testで既に固定済みの観点として扱い、差異を報告する。
7. required E2Eを実行し、fake / process contract testで代替していないことをdiffレビューする。

### ステップ 3: 明示overrideの実CLI E2Eを追加する

1. 同じfixtureへrepeatable `--source-root`をapp、service、repositoryの順で渡す。
2. fixtureのglobal classpath manifestを行単位で読み、各entryをrepeatableな`--analyzer-meta classpath=<entry>`として指定順で渡す。`javaLanguageLevel`は`--analyzer-meta javaLanguageLevel=<major>`を1件だけ渡し、必要なfixtureだけ`javaPreview=true`を1件渡す。
3. captured requestのroot順、classpath、language metadataが入力どおりであることを検証する。
4. stderrとcaptureにdiscovery開始、安全通知、provider展開、Gradle接続がないことを検証する。
5. raw graph / diagnostic / metadata / location / outcomeが自動discoveryの固定期待集合と一致することを検証する。
6. Core CLI stdout / stderr / exit statusを同じrunで検証する。
7. required E2Eを実行し、Tooling API bypassをfixture側の推測で判定していないことをdiffレビューする。

### ステップ 4: fatal時のrequest原子性とfailure表示を実CLIで固定する

1. test-only fixture / modeで、method / edge / diagnosticを先行出力後にvalid errorを返す実Analyzer process経路を作る。proxyはrecordを生成しない。
2. Core CLIが先行Graph、diagnostic、件数を成功結果として表示せず、非ゼロexitになることを検証する。
3. 複数`error.details`の共通fieldとcanonical metadata JSONが配列順でstderrへ表示されることを検証する。
4. errorなし非ゼロexit、malformed stdout、正常exitだがedge参照不整合も、成功Graphを公開しないことを検証する。
5. fatal時はstaging Graphの参照不整合で元errorを上書きしないことを検証する。
6. Java固有code / metadata keyに依存しないgeneric failure fixtureも同じrendererで検証する。
7. required E2EとGo testを実行し、Coreのrequest原子性をdiffレビューする。

### ステップ 5: repository標準required gateへ接続する

1. `TestGradleMultiProjectCLI`または同等の明示名で、`DEPWALK_E2E_REQUIRED=1`時に自動 / 明示両経路を必須実行する。
2. `context/project.md`の`Multi-project required E2E` commandで再現できるようtest setupを整える。command契約を変更する必要がある場合は先にtrack判断を求める。
3. Java Analyzer jar、fixture classes / manifest、Core CLI binaryのbuild失敗をtest failureにする。
4. required環境変数未指定時の通常unit suiteと、指定時required E2Eの境界を既存E2E規約へ合わせる。
5. pre-commitとrequired E2Eを実行し、CIで再現可能な前提をdiffレビューする。

### ステップ最終: 最終確認

1. `## 検証コマンド`をすべて実行する。
2. auto / explicit / fatalの3経路でrequest、raw stream、CLI出力、exitを同一runで照合する。
3. Java production code、Protocol schema、Traversal / Outputを変更していないことを確認する。
4. durable設計との差分が必要なら実装せずspec-lifecycleのtrackへ戻す。
5. 最終diffレビューを行い、指摘を対応する。

## 実装コンテキスト

- spec: `specs/24-gradle-multi-module-source-roots/index.md`
- testing正本: `context/testing.md`
- command契約: `context/project.md`
- Protocol正本: `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md`
- Graph正本: `design/features/graph/DesignDoc_graph.md`
- Java Analyzer正本: `design/features/java-analyzer/DesignDoc_java-analyzer.md`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照・変更するpath:
  - `core/e2e/`
  - `core/cmd/depwalk/`
  - 必要最小限の`core/internal/analyze/`、`core/internal/analyzer/`、`core/internal/cli/`test seam。
  - `testdata/fixtures/java/multi-module-spring-project/`のP5成果。
  - `testdata/analyzer-protocol/scenarios/`のP1 / P2成果。
- 参照のみのpath:
  - `analyzers/java/build/libs/java-analyzer.jar`
  - `analyzers/java/src/main/`
  - `core/internal/graph/`
  - `core/internal/traversal/`
  - `core/internal/output/`

## 前提条件

- 完了しているべきphase / 依存prompt:
  - `P2_01_core_request-staging-failure.md`
  - `P5_01_java-analyzer_fixture-compatibility-security.md`
- 完了後に着手可能になる後続prompt: なし。Issue #24の実装prompt群は完了に向かう。
- 必要なrepo状態: 実Core CLI、実Analyzer jar、multi-module fixture、fixed expectationsをbuildできること。

## 不明点ハンドリング

- 矛盾、欠落、未定義を見つけたら作業を止める。
- proxyで不足するproduction機能を補完・変換しない。
- CLI flagとspecが不一致の場合は新flagを推測追加せず、欠落、必要なproduction変更、観測契約への影響を提示する。
- 質問時は、止まっている作業単位、判断論点、選択肢、required E2Eの透過性とproduction wiringへの影響を整理する。

## タスク境界

### 実装する範囲

- test-only透過recording proxyとbyte透過unit test。
- 実Core CLI / 実Analyzer jarのauto / explicit required E2E。
- captured request、raw JSONL / stderr / exit、CLI stdout / stderr / exitの同一run照合。
- fatal時のstaging state破棄と共通failure detail表示E2E。
- repository標準`DEPWALK_E2E_REQUIRED=1` gateへの接続。

### 実装しない範囲

- fake Analyzerでproduction wiringを代替すること。
- production debug / graph dump optionとProtocol debug record。
- Java Analyzer production codeとGradle model /解析契約。
- Protocol schema、Traversal、成功時Output schema。
- performance再計測とGradle cross-version matrix。これらはP5で完了済みである。

## 設計仕様

- proxyはstdin、stdout、stderr、exitを変換せず中継し、検証用複製だけをtemporary directoryへ保存する。
- auto requestはsourceRoots / explicit metadataを省略し、explicit requestはroot順とmetadataを保持する。
- raw Analyzer graphはP5の固定期待集合と照合し、CLIのproduction責務を広げない。
- auto / explicitはdiscovery固有metricsを除いて同じ成功結果を返し、explicitではTooling APIが起動しない。
- valid errorまたは非ゼロexitでは先行Graph / diagnostic /件数を公開せず、failure detailsだけを共通rendererで表示する。
- required gateは実binary / jar / fixture build失敗、proxy失敗、capture失敗、期待差分を失敗にする。

## テスト観点

- proxyのstdin / stdout / stderr / exitのbyte透過性とcapture isolation。
- auto requestのfield省略、discovery通知、固定graph / outcome、CLI成功終了。
- explicit requestのroot順 / metadata、Tooling API非起動、autoとのgraph同値性。
- valid error / 非ゼロexit前の先行record破棄、failure detail順序とcanonical metadata表示。
- fatal時に参照不整合で元errorを上書きしない。
- malformed stdout、参照不整合、proxy / Analyzer起動失敗をsuccessへ降格しない。
- `DEPWALK_E2E_REQUIRED=1`で実CLI / jarの両経路が必須実行される。

## 検証コマンド

- `cd analyzers/java && ./gradlew shadowJar`
- `cd core && go test ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -run TestGradleMultiProjectCLI -count=1`
- `lefthook run pre-commit`
- `git diff --check`

## 完了条件

- [ ] ステップ0でbranch、差分、P2_01 / P5_01完了、artifact準備を確認した。
- [ ] test-only proxyがstdin / stdout / stderr / exitを変換せず中継した。
- [ ] captureをtest temporary directoryへ隔離し、失敗をE2E failureにした。
- [ ] auto実CLI E2Eでrequest field省略、raw graph / metrics、CLI出力 / exitを照合した。
- [ ] explicit実CLI E2Eでroot順 / metadata、Tooling API非起動、autoとの同値性を照合した。
- [ ] fatal E2Eで先行Graph / diagnostic /件数を破棄し、detailsを共通表示した。
- [ ] malformed /参照不整合 /非ゼロexitで成功Graphを公開していない。
- [ ] `DEPWALK_E2E_REQUIRED=1`のrepository標準gateへ両経路を接続した。
- [ ] fake Analyzer、record変換、production debug option、Protocol追加を行っていない。
- [ ] Java production code、Protocol schema、Traversal / Outputを変更していない。
- [ ] 全作業ステップとdiffレビューを完了した。
- [ ] `## 検証コマンド`がすべてパスした。
- [ ] 未解決の仕様質問が残っていない。
