# Gradle Tooling API discovery と custom model provider

## 絶対ルール

- spec と上位正本に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止する。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証と diff レビューをスキップしない。
- Gradle task、compile、source生成をmodel取得から起動しない。
- Gradle由来のstdout / stderr、raw例外、progress自由文をdepwalkの出力へ転送しない。
- CoreとProtocol schemaを変更しない。
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
5. P2_01と並列実行する場合は`analyzers/java/`だけを変更しCore pathへ入らない。

### ステップ 1: Tooling APIとprovider artifactのbuild境界を作る

1. TDDでartifact構成、version、classfile majorを検証するtestを追加する。
2. Analyzer本体へGradle Tooling API `9.6.1`を同梱する。Analyzer wrapperも`9.6.1`へ更新する。
3. custom model providerをAnalyzer本体と別artifactとしてbuildする。providerのcompile baselineはGradle API `7.6.5`、Java `--release 8`、classfile major 52とする。
4. provider artifactへAnalyzer本体のJDK 25 classやGradle `7.6.5`より新しいAPI参照を混入させない。
5. providerが返すmodelへ、build identifier、project path、project directory、`main` Java source directories、`main.compileClasspath`の解決済みfiles、`main` classes output directories、compile classpath上のproject依存、source language level、preview有効性を含める。
6. Java pluginを持たないprojectは解析contextを生成しない。`test`と名前付きsource setは解析modelへ含めず、除外名と件数をsummary用データとして返す。
7. shadowJarとprovider artifactのtestを実行し、diffレビューでbinary境界を確認する。

### ステップ 2: 一時init scriptからcustom modelを取得する

1. TDDでprovider展開、model取得、cleanup、失敗categoryを検証するtest seamを追加する。
2. `sourceRoots`省略時だけ、provider artifactとinit scriptを実行ごとに一意なOS temporary directoryへ展開する。
3. 対象workspaceのsettings / build scriptへ変更を加えず、temporary init scriptからproviderをGradle daemonへ注入する。
4. Tooling API operationにはtaskを指定しない。source生成、compile、testその他のtaskを起動しない。
5. wrapperがないbuildはTooling API同梱version`9.6.1`を使用する。
6. connection終了後にtemporary directoryをbest effortで削除する。削除失敗は解析結果を失敗させず、絶対pathやraw例外を含まない安定categoryだけをstderrへ記録する。workspaceへfallback配置しない。
7. provider非互換、必須field欠落、serialization失敗、classpath解決失敗を`JAVA_GRADLE_MODEL_ERROR`へ変換し、IDE modelやfilesystem推測へfallbackしない。
8. testを実行し、workspaceへのprovider / init script書込みがないことをdiffレビューする。

### ステップ 3: version / daemon JVMの互換性guardを実装する

1. target Gradleのsupported rangeを`7.6.5 <= version < 9.7.0`として検証する。
2. versionを安定判定できないcustom distributionもunsupportedとしてfatalにする。
3. target Gradleと選択済みdaemon JVMの組はGradle公式互換範囲に従って検証する。depwalkはdaemon JDKをdownload、同梱、自動選択しない。
4. 安定reasonは`unsupported-gradle-version`、`provider-incompatible`、`daemon-jvm-incompatible`を使用し、Gradle / JVM設定修正または明示overrideを案内する。
5. Analyzer runtime JDK 25、daemon JVM、project compile toolchain、source language levelを相互に代用しない。
6. unit / integration testを実行し、supported range外を不完全modelへ降格しないことをdiffレビューする。

### ステップ 4: output隔離と安全通知を実装する

1. Tooling API operationのstandard output / standard errorを明示的なdiscard sinkへ接続する。
2. progress listenerはAnalyzer生成のphase、開始・終了、経過時間、成功 / 安定categoryだけを扱う。display name、descriptor、failure messageなどGradle由来自由文を出力しない。
3. Gradle例外はraw`getMessage()`、`toString()`、stack trace、repository URL、header、credential、cache絶対pathを使わず、安定category、失敗phase、固定message、明示override案内へsanitizeする。
4. discovery開始前に、build logic評価、network、Gradle credential resolution、user cache更新が発生し得ることと、明示overrideで回避できることをstderrへ固定文で通知する。
5. repository credentialをdepwalk固有CLI / Protocol / metadataで受け取らず、取得・保存しない。
6. `sourceRoots`が1件以上ある場合はprovider展開、build評価、Gradle接続、dependency resolution、通知を完全にbypassする入口を用意する。root / context構築はP3で接続する。
7. negative unit testでGradle由来自由文がdepwalk出力へ出ないことを検証し、包括的negative fixtureはP5へ残す。
8. testを実行し、raw output転送やredaction方式を追加していないことをdiffレビューする。

### ステップ最終: 最終確認

1. `## 検証コマンド`をすべて実行する。
2. provider artifactのclassfile major 52とAPI baselineを検証する。
3. Core、Protocol schema、Java解析context / call解決へ変更していないことを確認する。
4. durable設計との差分が必要なら実装せずspec-lifecycleのtrackへ戻す。
5. 最終diffレビューを行い、指摘を対応する。

## 実装コンテキスト

- spec: `specs/24-gradle-multi-module-source-roots/index.md`
- Java Analyzer正本: `design/features/java-analyzer/DesignDoc_java-analyzer.md`
- toolchain正本: `context/toolchain.md`
- infrastructure正本: `context/infrastructure.md`
- ADR: `adr/0006-adopt-gradle-tooling-api-discovery.md`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照・変更するpath:
  - `analyzers/java/build.gradle.kts`
  - `analyzers/java/settings.gradle.kts`
  - `analyzers/java/gradle/wrapper/gradle-wrapper.properties`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/`
  - `analyzers/java/src/test/java/com/fukuemon/depwalk/javaanalyzer/`
  - 新規provider source setまたはsubprojectは`analyzers/java/`配下に閉じる。
- 参照のみのpath:
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/protocol/`
  - `context/project.md`

## 前提条件

- 完了しているべきphase / 依存prompt: `P1_01_analyzer-protocol_multi-root-failure-contract.md`。
- 同一phaseで並列実行可能: `P2_01_core_request-staging-failure.md`。
- 完了後に着手可能になる後続prompt: `P3_01_java-analyzer_source-context-preflight.md`。
- 必要なrepo状態: P1のJava `AnalysisRequest.sourceRoots`と`ErrorRecord.details`が実装済みであること。

## 不明点ハンドリング

- 矛盾、欠落、未定義を見つけたら作業を止める。
- Tooling API / Gradle APIは公式`9.6.1` / `7.6.5`の実在APIを確認して使う。存在しないAPIや別versionの便利APIでbaselineを破らない。
- provider artifact分割が既存Gradle構成と両立不能なら、候補構成、classfile / dependency境界、影響を提示して確認する。
- 質問時は、止まっている作業単位、判断論点、選択肢、互換性・security boundaryへの影響を整理する。

## タスク境界

### 実装する範囲

- Tooling API `9.6.1`とAnalyzer wrapper更新。
- Gradle API `7.6.5` / Java 8 classfileの別provider artifact。
- `main` source set custom modelと一時init script注入。
- target Gradle / daemon JVM互換性guard。
- build output discard、例外sanitize、安全通知、明示bypass入口。
- provider / adapterのunit・integration test seam。

### 実装しない範囲

- source rootのreal-path /重複 /包含 / file列挙。
- `SourceSetAnalysisContext`のTypeSolver / SootUp構築とparse pre-flight。
- call-site inventory、bytecode-only member、completeness gate。
- multi-module primary fixture、cross-version required matrix、credential negative E2E。これらはP5の責務である。
- CoreとProtocol schema。

## 設計仕様

- 自動discoveryは`sourceRoots`省略時だけ実行し、各in-scope projectの`main` Java source setをcustom modelで取得する。
- Analyzer本体はTooling API `9.6.1` / JDK 25、providerはGradle API `7.6.5` / Java 8 classfileとする。
- target Gradleは`7.6.5`以上`9.7.0`未満とし、wrapperなしは`9.6.1`を使う。
- providerはbuild identifier、project identity / directory、main roots / classpath / classes output / project dependencies、source language level / previewを返す。
- Tooling operationはtaskを実行しない。ただしbuild configurationとclasspath解決によるnetwork / credential / user cache副作用は発生し得る。
- providerとinit scriptはtemporary directoryへ展開し、workspaceへ書かない。cleanup失敗は安定categoryのwarningとする。
- Gradle outputはdiscardし、raw例外 / progress自由文を転送しない。depwalk生成の固定情報だけを出力する。
- 明示root時はTooling API runtimeを完全bypassする。

## テスト観点

- AnalyzerにTooling API `9.6.1`が同梱され、wrapperも`9.6.1`である。
- providerがGradle API `7.6.5` baseline、Java 8 classfileで、Analyzer JDK 25 classを含まない。
- custom modelがmain roots / classpath / outputs / dependencies / language level / previewを返す。
- Java pluginなしprojectとmain以外のsource setを解析context候補から除外する。
- taskを指定せず、workspaceへprovider / init script / classes / generated sourceを書かない。
- temporary resourceをcleanupし、失敗時もworkspaceへfallback配置しない。
- supported range外、provider不整合、daemon JVM非互換を安定reason付きfatalにする。
- Gradle stdout / stderr / progress自由文 / raw例外をdepwalk出力へ転送しない。
- 明示root時はprovider展開、Gradle接続、通知が0件である。

## 検証コマンド

- `cd analyzers/java && ./gradlew test`
- `cd analyzers/java && ./gradlew shadowJar`
- `cd analyzers/java && ./gradlew dependencies`
- `git diff --check`

## 完了条件

- [ ] ステップ0でbranch、差分、P1完了、並列path境界を確認した。
- [ ] AnalyzerとwrapperをTooling API / Gradle `9.6.1`へ固定した。
- [ ] providerをGradle API `7.6.5` / Java 8 classfileの別artifactとして実装した。
- [ ] providerが確定済みmain model fieldsを返し、main以外を解析候補へ混入させていない。
- [ ] 一時init scriptからproviderを注入し、taskとworkspace書込みを要求していない。
- [ ] temporary cleanupと安定categoryのcleanup warningを実装した。
- [ ] target Gradle rangeとdaemon JVM互換性guard、安定reasonを実装した。
- [ ] Gradle outputをdiscardし、raw例外 / progress自由文をsanitizeした。
- [ ] 自動discovery開始前の固定安全通知を実装した。
- [ ] 明示root時にTooling API runtimeを完全bypassする入口を実装した。
- [ ] Core、Protocol schema、source context、call解決へ踏み込んでいない。
- [ ] 全作業ステップとdiffレビューを完了した。
- [ ] `## 検証コマンド`がすべてパスした。
- [ ] 未解決の仕様質問が残っていない。
