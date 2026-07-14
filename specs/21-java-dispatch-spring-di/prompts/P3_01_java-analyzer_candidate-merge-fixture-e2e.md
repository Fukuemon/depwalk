# 候補統合・Protocol 出力拡張・Spring Boot fixture・統合 E2E

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

P1/P2 と同じ `feature/21` branch / PR を継続利用する。P2 の完了を前提にし、prompt ごとに branch / PR を作り直さない。

1. 現在の branch が `feature/21` であることを確認する
2. P1/P2 の完了条件とテストが満たされていることを確認する
3. 既存 Draft PR の description に本 prompt の完了条件を追記する

### ステップ 1: 候補統合・重複排除と CallEdge 生成 (D2)

1. P1 (SootUp 型階層照会) と P2 (Spring DI 中間結果) の出力を統合し、call site ごとに caller → 各実装候補への複数 `CallEdge` を生成するロジックを `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/graph/CallGraphBuilder.java` (既存) と連携させて実装する。宣言型 (interface / 基底型) への既存 edge も保持する。
2. 各実装候補 edge の `metadata` に `resolution` (`unique` / `ambiguous`) と `provenance` (重複なし・辞書順の `sootup` / `spring-di` 配列) を付与する。条件付き候補では `conditional: true` と条件アノテーション FQN の辞書順配列 `conditionTypes` も付与する。宣言型 edge の既存 metadata は変更しない。
3. edgeId 単位で重複排除を行う。
4. テストを先に書く (TDD)。複数候補が edge として出力されること、宣言型 edge が保持されることを検証する fixture を追加する。
5. `cd analyzers/java && ./gradlew test` を実行する。
6. diff レビューを回し、指摘を対応してから次へ。

### ステップ 2: diagnostic 出力の拡張 (E1/E2/E4、D6 の観測責務境界)

1. P2 で生成した中間結果 (未解決・曖昧・runtime-provided・条件付き) を `Diagnostic` record として出力する処理を実装する。
2. `JavaDiagnosticCode` (`analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/JavaDiagnosticCode.java`) に、既存命名パターン `JAVA_<SCREAMING_SNAKE>` に合わせて次の 3 code を追加する:
   - `JAVA_RUNTIME_PROVIDED` (severity: `info`) — E1 で既知の runtime-provided マーカー (Spring Data `Repository` / MyBatis `@Mapper`) に該当した場合。D3/D4 の「意図的に解決しない」という位置づけに合わせ、対応不要な情報として扱う。
   - `JAVA_AMBIGUOUS_CANDIDATE` (severity: `warning`) — E2 で複数候補が残り絞り込めない場合。レビューが必要な曖昧さのため。
   - `JAVA_CONDITIONAL_BEAN` (severity: `info`) — E4 で条件付き Bean を静的に確定できない場合。D3 により評価しない設計上の既定動作のため。
     E1 のうち runtime-provided マーカーに該当しない未解決は、既存の `JAVA_UNRESOLVED_SYMBOL` (severity: `warning`) を再利用する (新規 code を追加しない)。
     この 3 code は `design/features/java-analyzer/DesignDoc_java-analyzer.md` の「diagnostic / error code 体系」表 (正本) にも反映が必要な durable な情報のため、この doc への追記は phase: sync として扱う (ステップ5の性能計測と同様)。
3. 曖昧性・解決根拠の観測は Analyzer JSONL (`callEdge.metadata` / `diagnostic`) までを本 feature の責務とする (D6)。CLI 出力 (Console / JSON) への表出は実装しない (#22 の管轄)。
4. テストを先に書く (TDD)。E1 (0件・runtime-provided 判定含む) / E2 (複数件) / E4 (条件付き) それぞれで期待する diagnostic が出力されることを検証する。
5. `cd analyzers/java && ./gradlew test` を実行する。
6. diff レビューを回し、指摘を対応してから次へ。

### ステップ 3: Spring Boot fixture の新規作成

1. `testdata/fixtures/java/spring-project/` に単一 source root の独立 Gradle project (`settings.gradle.kts` / `build.gradle.kts` / `src/main/java`) を新規作成する。Java unit 用の最小 fixture は `analyzers/java/src/test/resources/fixtures/` に置き、実 jar E2E fixture と混在させない。
2. fixture の `build.gradle.kts` は Java plugin、Maven Central、Java toolchain 25、`JavaCompile.options.release=21` を設定する。依存は `org.springframework.boot:spring-boot-autoconfigure:4.1.0`、`org.springframework.data:spring-data-commons:4.1.0`、`org.mybatis:mybatis:3.5.19`、`compileOnly` / `annotationProcessor` の `org.projectlombok:lombok:1.18.46` に固定する。
3. `writeDepwalkClasspath` task を定義し、`sourceSets.main.output.classesDirs` と `configurations.runtimeClasspath` の実在 path を絶対 path・重複なし・辞書順・1 行 1 entry で `build/depwalk-classpath.txt` に書く。task は `classes` に依存させる。Lombok jar は compile-only のため manifest に含めず、生成済み constructor は classes directory の `.class` から読む。
4. fixture に DI (constructor / field / setter injection)、stereotype、`@Qualifier`、`@Primary`、条件付き Bean (`@Profile` / `@ConditionalOnProperty`)、Spring Data `Repository`、MyBatis `@Mapper` インターフェース (D8)、Lombok (`@AllArgsConstructor` / `@RequiredArgsConstructor` 等) でコンストラクタを生成するクラス (D7) を含める。
5. `analyzers/java/gradlew -p testdata/fixtures/java/spring-project clean classes writeDepwalkClasspath` を repo root から実行し、`build/classes/java/main` と全 runtime jar が manifest に存在すること、Lombok 生成 constructor が `.class` に存在することを確認する。
6. fixture 内の既知の caller / callee 集合を、E2E テストの期待値として整理する。
7. diff レビューを回し、指摘を対応してから次へ。

### ステップ 4: 統合 E2E テスト

1. Go 側の実 jar E2E (`context/testing.md` の三層テストのうち実 jar E2E 層) に、ステップ 3 の fixture を使った統合テストを追加する。テストは `build/depwalk-classpath.txt` を読み、各行を順に `analysisRequest.metadata.classpath` へ設定して、workspace/source root を `testdata/fixtures/java/spring-project` に向ける。manifest 不在・空・entry 不在は setup failure とする。
2. `core/e2e` の test code を追加する。本番 Core code は変更しない。
3. CLI 出力レベルの照合は行わない。Core の metadata passthrough と CLI JSON 表出は [Issue #22](https://github.com/Fukuemon/depwalk/issues/22) の D11 が担う。本 prompt は #22 に依存せず、Analyzer JSONL の metadata と graph 上の caller / callee 集合を検証する。
4. `(cd analyzers/java && ./gradlew shadowJar) && analyzers/java/gradlew -p testdata/fixtures/java/spring-project clean classes writeDepwalkClasspath && (cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -count=1)` を repo root から実行する。
5. diff レビューを回し、指摘を対応してから次へ。

### ステップ 5: 性能計測・記録 (D5)

1. Issue #9 baseline (`testdata/fixtures/java/project`、10 ファイル、約500ms、最大RSS 約122MiB) と同一 fixture、または本 feature の Spring Boot fixture で、SootUp / Spring 解析追加分の解析時間・最大 RSS を計測する。
2. 数値の合否基準は設けない (D5)。計測結果を `design/features/java-analyzer/DesignDoc_java-analyzer.md` の性能方針節に増分として記録する (この doc への追記は phase: sync として扱う。本 prompt では計測と記録のみ行い、記録内容の spec への反映漏れがないかを最終確認ステップで phase: track に渡す)。
3. 計測手順・環境 (JDK バージョン、OS/アーキテクチャ) を記録に含める (既存 baseline の記録形式に合わせる)。

### ステップ最終: 最終確認

1. 全テスト / lint / typecheck がパスすることを確認 (`cd analyzers/java && ./gradlew test` および Go 側 `cd core && go test ./...`)
2. spec の `## 上位資料からの変更点` に必要な追記がないかを phase: track に渡す (性能計測結果の feature doc への反映を含む)
3. PR / MR を Ready に変更しレビュアーを指名する

## 実装コンテキスト

- spec: `specs/21-java-dispatch-spring-di/index.md`
- 参照する appendix: なし
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/graph/CallGraphBuilder.java` (既存、統合ロジックを追加)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/protocol/CallEdge.java` / `Diagnostic.java` (既存 record、フィールド構成は変更しない)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/JavaDiagnosticCode.java` (新規 code 追加)
  - `analyzers/java/src/test/resources/fixtures/` (Java unit 用の最小 fixture)
  - `testdata/fixtures/java/spring-project/` (実 jar E2E fixture)
  - `core/e2e/` (test code のみ追加。本番 Core code は変更しない)
  - `design/features/java-analyzer/DesignDoc_java-analyzer.md` (性能方針節、計測結果の記録先)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P1_01_java-analyzer_sootup-type-hierarchy.md`、`P2_01_java-analyzer_spring-di-resolution.md`
- 完了後に着手可能になる後続 prompt: なし。CLI / Core レベルの metadata 生存確認は #22 D11 の実装後に #22 側で検証するが、#21 の完了条件ではない
- 必要な repo 状態: 同じ `feature/21` branch 上で P1 / P2 の変更とテストが完了していること。JDK 25 + Java Analyzer fat jar のビルドが可能な環境であること (E2E 実行に必要)

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- P1/P2 の出力を統合した候補の CallEdge 化・重複排除 (D2)
- diagnostic 出力の拡張 (E1/E2/E4、D6 の観測責務境界内)
- Spring Boot fixture の新規作成 (Lombok / MyBatis Mapper を含む)
- 統合 E2E テスト (graph レベルの照合)
- 性能計測・記録 (D5、数値の合否基準は設けない)

### 実装しない範囲

- CLI 出力 (Console / JSON) への edge 単位 metadata 表出 (#22 の管轄)
- CLI 引数の完全仕様確定 (#22 の管轄)
- Analyzer Protocol の破壊的変更 (`CallEdge` / `Diagnostic` の既存フィールド構成は変更しない)
- 性能の数値合否判定 (SLO 確定は #22)
- Core / Traversal / Output の本番 code 変更 (`core/e2e` の test code 追加は実装範囲)

## 設計仕様

spec (`specs/21-java-dispatch-spring-di/index.md`) より抜粋:

> **D2 (解決済み)**: 複数 dispatch 候補は call site ごとの複数 CallEdge とする。実装候補 edge の metadata は `resolution` (`unique` / `ambiguous`)、`provenance` (重複なし・辞書順の `sootup` / `spring-di` 配列)、必要に応じて `conditional` / `conditionTypes` を持つ。宣言型 edge の既存 metadata は変更しない。
>
> **D6 (解決済み)**: 曖昧性・解決根拠の観測は、#21 では Analyzer JSONL の metadata + diagnostic までを責務とする。CLI 出力への edge 単位の metadata 表出は #22 の論点として引き継ぐ。
>
> **D5 (解決済み)**: SootUp / Spring 解析追加の性能増分について、数値の合否基準は定めない。「計測と記録」を受け入れ基準とする。完了条件は「同一 fixture での before/after (解析時間・最大 RSS) を計測し、feature doc の性能節に増分を記録する」まで。
>
> **Spring Boot fixture (決定済み)**: `testdata/fixtures/java/` に単一 source root の Spring Boot fixture を新規作成する。DI (constructor / field / setter injection)、stereotype、`@Qualifier`、`@Primary`、条件付き Bean、Spring Data Repository、MyBatis `@Mapper` (D8)、Lombok 生成コンストラクタ (D7) を含める。
>
> **EARS**: WHEN Spring Boot E2E fixture を解析したとき、既知の caller / callee 集合と一致する。検証は graph 上の既知 caller / callee 集合との照合を基本とし、CLI 出力レベルの照合は CLI interface spec (#22) 完了後に完成する。
>
> **S5 (Design Doc 成功条件)**: Core / analyzer-protocol の破壊的変更を発生させない。

## テスト観点

spec の `### Testing` より抜粋 (Java unit / Go process contract / 実 jar E2E の三層):

- 実 jar E2E: 既知の caller / callee 集合と解析結果 graph の照合。interface 注入を含むサンプルで宣言型のメソッドが callee に現れ `dispatch: interface` が立つこと。
- Spring Boot fixture: DI・stereotype・`@Qualifier`・`@Primary`・条件付き Bean・Spring Data Repository・MyBatis `@Mapper`・Lombok 生成コンストラクタを含めた統合検証。
- Go 側 process contract: 既存の contract test 観点 (stdin close / 逐次 parse / stderr 非 parse / exit code) は本 prompt の変更で壊れていないことを回帰確認する (新規実装は不要)。

## 検証コマンド

- ビルド: `cd analyzers/java && ./gradlew shadowJar`
- Java unit test: `cd analyzers/java && ./gradlew test`
- Go unit test: `cd core && go test ./...`
- Lint / typecheck (Go): `cd core && go vet ./...`
- E2E: `(cd analyzers/java && ./gradlew shadowJar) && analyzers/java/gradlew -p testdata/fixtures/java/spring-project clean classes writeDepwalkClasspath && (cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -count=1)`
- 健全性検査: `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 で P1/P2 と同じ `feature/21` / Draft PR を継続していることを確認した
- [ ] P1/P2 の出力を統合し、call site ごとの複数 CallEdge + 宣言型 edge 保持を実装した (D2)
- [ ] `CallEdge.metadata` / `Diagnostic` へ解決根拠を非破壊で追加した (`CallEdge` / `Diagnostic` の record フィールド構成は変更していない)
- [ ] E1/E2/E4 の diagnostic 出力を実装した
- [ ] Spring Boot fixture (Lombok / MyBatis Mapper を含む) を新規作成した
- [ ] fixture を固定依存で build し、classes directory と runtime jar を `build/depwalk-classpath.txt` 経由で `analysisRequest.metadata.classpath` に渡した
- [ ] 統合 E2E テストで既知の caller / callee 集合と graph が一致することを確認した
- [ ] 性能計測 (解析時間・最大 RSS) を実施し、記録した (feature doc への反映は phase: track / sync へ引き継ぎ)
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (性能計測結果、および新規 diagnostic code 3 件の feature doc 反映を含む)
- [ ] PR / MR を Ready に変更しレビュアーを指名した
- [ ] 未解決の仕様質問が残っていない
