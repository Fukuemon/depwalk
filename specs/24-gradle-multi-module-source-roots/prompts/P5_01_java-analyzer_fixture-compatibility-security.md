# Multi-module fixture、Gradle互換性、security境界の統合検証

## 絶対ルール

- spec と上位正本に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止する。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証と diff レビューをスキップしない。
- fixtureの期待集合を実出力から無条件に上書きしない。設計仕様から固定する。
- credentialをtest log、snapshot、failure messageへ残さない。
- Core production code、Protocol schema、Traversal / Outputを変更しない。
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
3. P2_02、P3_01、P4_01が完了していることを確認する。
4. Issue #24の既存Draft PRを流用し、本promptの完了条件をdescriptionへ追記する。
5. production契約の不足をtest側fallbackで補わない。

### ステップ 1: 3 module primary fixtureを作る

1. `testdata/fixtures/java/multi-module-spring-project/`へGradle multi-project fixtureを追加する。
2. root settingsから`:app`、`:service`、`:repository`をincludeし、root project自体にはJava sourceを置かない。
3. `:app`は標準`app/src/main/java`にentrypoint / controllerを持ち、`:service`へ依存する。
4. `:service`は`projectDir = file("modules/service")`へ移動し、service interface / `@Service`実装 / constructor injectionを持ち、`:repository`へ依存する。
5. `:repository`はmain source directoryを`repository/src/domain/java`へ変更し、repository interface / `@Repository`実装を持つ。
6. controllerからservice、serviceからrepositoryへの通常callと、module境界をまたぐDI / interface dispatchを固定する。
7. fixture buildはclasses outputと明示override用global classpath manifestを生成する。Analyzer自身はtaskを起動しない。
8. method、edge、dispatch / DI provenance、diagnostic、workspace相対location、inventory / outcome集計の固定期待集合をtestdataへ置く。
9. fixture buildとJava testを実行し、期待集合が設計から導かれていることをdiffレビューする。

### ステップ 2: 自動discoveryと明示overrideの実jar同値testを作る

1. 自動discoveryはworkspace rootだけを渡し、requestに`sourceRoots`、`metadata.classpath`、language metadataを入れない。
2. 3 project / main context、3 root、変更projectDir、custom source dirを検出し、project依存方向で型解決することを検証する。
3. 明示overrideは3 root、global classpath manifest、language levelを渡し、Tooling API起動、provider展開、安全通知が0件であることを検証する。
4. discovery固有metricsを除き、両経路のmethod / edge / diagnostic / owner metadata / location / outcome集計を同じ固定期待集合で照合する。
5. module directoryを含むinclude / excludeが対象集合へ反映されるtestを追加する。
6. single-root明示とsingle-project discoveryの既存unit / E2Eをrequired regressionとして残す。
7. classes output欠落、root欠落、symlink escape、包含root、model ambiguity、binary name衝突は小さいunit / process fixtureへ分離する。
8. Java testとshadowJarを実行し、happy pathとnegative fixtureの責務をdiffレビューする。

### ステップ 3: Gradle / daemon JVM cross-version matrixを実装する

1. `gradleCompatibilityTest` taskまたは同等のrepository標準入口を実装する。
2. 同一custom model fixtureを次の固定CI anchorで実行する。
   - Gradle `7.6.5` / daemon JDK 8
   - Gradle `8.14.5` / daemon JDK 17
   - Gradle `9.6.1` / daemon JDK 25
3. Analyzer clientは全runでJDK 25へ固定する。
4. 各組合せでprovider load、model fields、task非実行、output隔離、固定graphを検証する。
5. provider artifactがJava 8 classfileで、Analyzer JDK 25 classが混入しないartifact testをmatrixへ含める。
6. supported range外、provider load失敗、daemon JVM非互換を安定reason付きfatalにするnegative testを追加する。
7. 各minor全件のmatrixやdaemon JDK自動download /選択を実装しない。
8. `gradleCompatibilityTest`を実行し、version正本と一致することをdiffレビューする。

### ステップ 4: credential非漏洩と副作用境界をnegative testで固定する

1. local test repositoryを使用するfixtureを追加する。test processがrunごとに高entropyのdummy credential markerを生成し、test専用Gradle propertyまたはenvironmentから注入する。fixture sourceへ値を固定せず、実credentialを使用しない。
2. build scriptは注入されたmarkerをstdout、stderr、Gradle logger、throwする例外messageへ意図的に含める。
3. 自動discoveryを実Analyzer jarで実行し、markerがProtocol、Analyzer stdout / stderr、failure detail、test capture /生成artifactへbyte一致で現れないことを検証する。markerを含むtest入力source / property自体を「漏洩output」の検査対象に混ぜない。
4. repository accessとGradle user cache更新が発生し得ることをtest専用directoryで観測する。
5. 安定category、phase、固定message、明示override案内は残ることを検証する。
6. 明示overrideではGradle接続、repository access、cache更新、安全通知が0件であることを検証する。
7. arbitrary build logicのfile / network / child process / daemon logをsandboxできるという保証をtestへ書かない。
8. testを実行し、dummy値を失敗出力へ再表示しないtest harnessになっていることをdiffレビューする。

### ステップ 5: 性能値と完全性metricsを計測する

1. 明示single-root、single-project discovery、multi-module discoveryを同一checkout / Gradle user home / daemon・cache状態で計測する。
2. 各経路は初回1回とwarm 3回を実行し、warm中央値を算出する。
3. total wall time、provider展開、Gradle configuration / classpath解決、model転送、context構築、parse pre-flight、source解析、最大RSSを記録する。
4. project / context / root / file数、unresolved symbol、bytecode-only member、inventory / ledger / outcome、silentOmission、error.details serialized bytesを記録する。
5. correctness期待集合を先にgateし、性能値へ数値上限を設けない。
6. 計測日、command、commit、fixture、JDK / Gradle、OS / architectureと結果をJava Analyzer feature docの性能方針へ追記する。実測していない値を推測しない。
7. 数値SLOはIssue #22へ残す。
8. 計測結果とdoc差分をレビューする。

### ステップ最終: 最終確認

1. `## 検証コマンド`をすべて実行する。
2. single / multi、auto / explicit、3つのversion anchor、security negativeがrequired testとして再現可能であることを確認する。
3. Core production code、Protocol schema、Traversal / Outputを変更していないことを確認する。
4. durable設計との差分が必要なら実装せずspec-lifecycleのtrackへ戻す。
5. 最終diffレビューを行い、指摘を対応する。

## 実装コンテキスト

- spec: `specs/24-gradle-multi-module-source-roots/index.md`
- Java Analyzer正本: `design/features/java-analyzer/DesignDoc_java-analyzer.md`
- testing正本: `context/testing.md`
- toolchain正本: `context/toolchain.md`
- infrastructure正本: `context/infrastructure.md`
- command契約: `context/project.md`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照・変更するpath:
  - `testdata/fixtures/java/`
  - `analyzers/java/build.gradle.kts`
  - `analyzers/java/src/test/`
  - P2_02〜P4_01で追加したGradle discovery / context / completenessのtest seam。
  - `design/features/java-analyzer/DesignDoc_java-analyzer.md`のPerformance節。
- 参照のみのpath:
  - `core/e2e/`
  - `core/internal/`
  - `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md`

## 前提条件

- 完了しているべきphase / 依存prompt:
  - `P2_02_java-analyzer_gradle-model-provider.md`
  - `P3_01_java-analyzer_source-context-preflight.md`
  - `P4_01_java-analyzer_call-completeness-bytecode.md`
- 完了後に着手可能になる後続prompt: `P6_01_core_required-cli-e2e.md`。
- 必要なrepo状態: Java Analyzer production契約が実装済みで、実jarをbuildできること。

## 不明点ハンドリング

- 矛盾、欠落、未定義を見つけたら作業を止める。
- fixture期待集合、Gradle / JDK供給方法、test-only repository隔離を推測で決めない。
- 固定CI anchorのJDKを実行環境で利用できない場合はtestをskipで成功扱いせず、必要環境と未実行範囲を報告して確認する。
- 質問時は、止まっている作業単位、判断論点、選択肢、required gateとsecurity保証への影響を整理する。

## タスク境界

### 実装する範囲

- 3 module Spring fixtureと固定期待集合。
- 自動discovery / 明示overrideの実jar同値test。
- Gradle `7.6.5` / `8.14.5` / `9.6.1`とdaemon JDK matrix。
- dummy credential / output隔離 / cache副作用negative test。
- 3経路の初回 / warm性能と完全性metrics計測・記録。

### 実装しない範囲

- Java Analyzer production契約の再設計。
- Core production codeとtest-only recording proxy /実CLI E2E。
- Protocol schema、Graph、Traversal、Output。
- external included buildをprimary happy pathへ含めること。
- 数値SLOとGradle全minor matrix。

## 設計仕様

- primary fixtureはapp → service → repositoryの2段依存、変更projectDir、custom main source directoryを持つ。
- autoはworkspace rootだけ、explicitは3 roots / global classpath / language metadataを使い、Tooling APIを完全bypassする。
- 両経路はdiscovery metricsを除き同じmethod / edge / diagnostic / metadata / location / outcome集合を返す。
- matrixは`7.6.5/JDK8`、`8.14.5/JDK17`、`9.6.1/JDK25`で、Analyzer clientはJDK25である。
- non-leak保証はdepwalk生成・転送outputとtest artifactに限定し、arbitrary build logicの外部副作用は対象外である。
- 性能は初回値とwarm 3回中央値を分け、correctnessをgateするが数値上限は設けない。

## テスト観点

- root settings、変更projectDir、custom main source directoryをmodelから検出する。
- module間通常call、constructor DI、interface dispatchの固定期待集合。
- auto / explicitのrequest差とgraph同値性、workspace相対location、module glob。
- single-root / single-project regression。
- provider binary / model / task非実行 / output隔離の3 version anchor。
- unsupported version / provider / daemon JVMの安定fatal reason。
- dummy credentialが全depwalk output / capture / artifactへ現れない。
- explicit bypassでGradle runtime副作用が0件である。
- correctness成功後に初回 / warm性能と完全性metricsを再現可能に記録する。

## 検証コマンド

- `cd analyzers/java && ./gradlew test`
- `cd analyzers/java && ./gradlew shadowJar`
- `cd analyzers/java && ./gradlew gradleCompatibilityTest`
- `git diff --check`

## 完了条件

- [ ] ステップ0でbranch、差分、P2_02 / P3_01 / P4_01完了を確認した。
- [ ] app / service / repositoryの3 module fixtureと固定期待集合を追加した。
- [ ] auto / explicit実jar testが同じgraph / metadata / location / outcomeを検証した。
- [ ] single-root / single-project regressionをrequired gateとして維持した。
- [ ] 3つのGradle / daemon JDK anchorを`gradleCompatibilityTest`で実行した。
- [ ] provider classfile、task非実行、output隔離、unsupported fatalをmatrixで検証した。
- [ ] dummy credentialのbyte非出力と明示bypassの副作用0件を検証した。
- [ ] correctness後に3経路の初回 / warm性能と完全性metricsを計測した。
- [ ] 実測値と環境をJava Analyzer feature docへ記録し、数値SLOを追加していない。
- [ ] Core production code、Protocol schema、Traversal / Outputを変更していない。
- [ ] 全作業ステップとdiffレビューを完了した。
- [ ] `## 検証コマンド`がすべてパスした。
- [ ] 未解決の仕様質問が残っていない。
