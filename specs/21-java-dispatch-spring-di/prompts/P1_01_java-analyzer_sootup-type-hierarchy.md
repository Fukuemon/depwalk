# SootUp による型階層補完と Interface Dispatch / Override 候補の解決

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

本 feature は P1〜P3 を同一 `feature/21` branch と同一 PR で直列実行する。prompt ごとに branch / PR を作り直さない。

1. 現在の branch が `feature/21` であることを確認する
2. 作業ツリーが clean であることを確認する
3. 既存 Draft PR があれば流用し、なければ `develop` 向けに作成する
4. 本 prompt の完了条件を PR description に追記する

### ステップ 1: SootUp 依存の追加と lazy view 構築

1. `analyzers/java/build.gradle.kts` の `dependencies` に `org.soot-oss:sootup.core:2.0.0`、`org.soot-oss:sootup.java.core:2.0.0`、`org.soot-oss:sootup.java.bytecode.frontend:2.0.0` を追加する。`sootup.callgraph` は追加しない。2.0.0 は設計時点の Maven Central 安定版で、bytecode の `AnalysisInputLocation` / `View` に必要な最小 module に固定済みのため、実装時に別 artifact/version を選び直さない。
2. `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/sootup/` パッケージを新設し、SootUp の `View` (型階層照会の入口) を lazy に構築するコンポーネントを実装する。eager な全クラス読み込みはしない (D5 の設計原則)。
3. 照会対象は次の 3 種を含める: (a) `analysisRequest.metadata.classpath` の依存 jar、(b) 同じ `classpath` に指定された解析対象プロジェクトの classes output directory、(c) 既存の source 解析結果 (JavaParser/SymbolSolver との連携)。新しい metadata key は追加しない。
4. classes output directory から source の binary name と一致する `.class` を引き、依存 classes directory と自プロジェクト classes directory を path 名で推測しない。
5. 明示された classpath entry が存在しない、または読めない場合は既存 pre-flight の `JAVA_MISSING_JAR` fatal を維持する。自動ビルドは実行しない。
6. 自プロジェクトの classes directory が classpath に指定されず source に対応する bytecode を取得できない場合は、ステップ 3 の E3 diagnostic 経路へ委譲する。
7. `context/project.md` の Quick Commands (`cd analyzers/java && ./gradlew shadowJar`) でビルドが通ることを確認する。
8. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す。
9. 指摘を対応してから次へ。

### ステップ 2: Interface Dispatch / Override 候補の索引化

1. SootUp の型階層情報から、interface / 基底型のメソッドに対する override / interface 実装候補を索引化するコンポーネントを実装する (`analysis/sootup/` 内)。
2. 索引化のみを行い、call graph 生成 (呼び出し連鎖の解決) は行わない (D1: SootUp は型階層補完のみに使う。call graph 生成は委譲しない)。
3. 既存の `analysis/graph/CallGraphBuilder.java` (call edge 生成の正本) が、この索引を「入力」として参照できる形にする。SootUp 側が edge を直接生成しない。
4. 既存の `analysis/attribution/AttributionResolver.java` / `AttributionResult.java` / `TypeSite.java` の帰属型決定規則と、索引化した実装候補の対応関係を明確にする (既存の帰属型規則を変更しない)。
5. テストを先に書く (TDD)。`analyzers/java/src/test/resources/fixtures/` に interface dispatch / override / Lombok 生成コンストラクタを含む最小 fixture を追加する。
6. `cd analyzers/java && ./gradlew test` を実行する。
7. diff レビューを回し、指摘を対応してから次へ。

### ステップ 3: bytecode 読み込み失敗時の diagnostic (E3)

1. pre-flight を通過した class file を SootUp が解釈・索引化できない場合、または自プロジェクトの classes directory が classpath に指定されず source に対応する bytecode を取得できない場合に、対象と原因を `diagnostic` として出力し、JavaParser 結果のみで解析を継続する。明示された classpath entry の欠落・読み取り不能はこの経路に入れず、既存 `JAVA_MISSING_JAR` fatal とする。
2. `JavaDiagnosticCode` (`analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/JavaDiagnosticCode.java`) に、この分岐用の新規 code `JAVA_SOOTUP_UNAVAILABLE` (severity: `warning`。既存 code の命名パターン `JAVA_<SCREAMING_SNAKE>` に合わせる) を追加する。この code は `design/features/java-analyzer/DesignDoc_java-analyzer.md` の「diagnostic / error code 体系」表 (正本) にも反映が必要な durable な情報のため、この doc への追記は phase: sync として扱う。
3. テストを先に書く (TDD): (a) 存在しない classpath entry は `JAVA_MISSING_JAR` で fatal、(b) 有効な entry 内の解釈不能 class file は `JAVA_SOOTUP_UNAVAILABLE` で継続、(c) 自プロジェクト classes directory 未指定では同 diagnostic を出して JavaParser 結果で継続、の 3 ケースを検証する。
4. `cd analyzers/java && ./gradlew test` を実行する。
5. diff レビューを回し、指摘を対応してから次へ。

### ステップ最終: 最終確認

1. 全テスト / lint / typecheck がパスすることを確認 (`cd analyzers/java && ./gradlew test`)
2. spec の `## 上位資料からの変更点` に必要な追記がないかを phase: track に渡す
3. PR / MR を Ready に変更しレビュアーを指名する

## 実装コンテキスト

- spec: `specs/21-java-dispatch-spring-di/index.md`
- 参照する appendix: なし (該当なしと spec で確定済み)
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `analyzers/java/build.gradle.kts` (依存追加)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/sootup/` (新設)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/graph/CallGraphBuilder.java` (既存、連携のみ・変更は最小限)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/attribution/` (既存、参照のみ)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/JavaDiagnosticCode.java` (新規 code 追加)
  - `analyzers/java/src/test/resources/fixtures/` (新規 fixture 追加)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/preflight/PreflightValidator.java` (既存 fatal 契約を変更していないことの確認)

## 前提条件

- 完了しているべき phase / 依存 prompt: なし (P1、他 prompt に依存しない基盤作業)
- 完了後に着手可能になる後続 prompt: `P2_01_java-analyzer_spring-di-resolution.md`
- 必要な repo 状態: `analyzers/java` が `./gradlew test` でビルド・テスト可能な状態であること

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない (SootUp の具体的な API 呼び出し方法が不明な場合は、公式ドキュメント / 実在する API を確認してから実装する。存在しない API を呼び出さない)
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- SootUp の依存追加と lazy view 構築 (自プロジェクトのコンパイル済み class を含む)
- Interface Dispatch / Override 候補の索引化 (型階層照会のみ)
- SootUp が bytecode を読めない場合の diagnostic 出力 (E3)

### 実装しない範囲

- SootUp による call graph 生成 (D1 により委譲しない)
- Spring Bean / DI 解決 (P2 の責務)
- 候補の call edge 化・重複排除・Protocol 出力拡張 (P3 の責務)
- Spring Boot fixture の新規作成 (P3 の責務)
- Core / Traversal / Output / Analyzer Protocol の変更

## 設計仕様

spec (`specs/21-java-dispatch-spring-di/index.md`) より抜粋:

> **D1 (解決済み)**: SootUp を型階層補完のみに使う。call graph 生成までは委譲しない。ADR-0005 の責務境界 (JavaParser = source AST / 呼び出し式 / symbol 抽出、SootUp = bytecode / 依存 jar の型階層・override・interface 実装候補の補完) と整合する。既存資産 (CallGraphBuilder / AttributionResolver / methodId 正規化) をそのまま延長する。
>
> **前提制約 (D1 付随)**: 解析対象は単一 source root プロジェクトのみとする。Gradle マルチモジュール (複数 source root) 対応は #21 のスコープ外 (→ #24)。
>
> **D7 (解決済み)**: Lombok (`@AllArgsConstructor` / `@RequiredArgsConstructor` 等) が生成するコンストラクタは、SootUp の bytecode 型階層照会対象に自プロジェクトのコンパイル済み class を含めることで解決する。理由: Lombok はコンパイル時にコンストラクタをバイトコードへ実体化するため、SootUp の照会対象に自プロジェクトのコンパイル済み class を含めれば、JavaParser (source-level) からは見えないコンストラクタも解決できる。
>
> **入力契約 (D7 付随)**: 解析対象プロジェクトの classes output directory は既存 `analysisRequest.metadata.classpath` に追加する。明示 entry の欠落・読み取り不能は既存 `JAVA_MISSING_JAR` fatal。classes directory 未指定または pre-flight 通過後の SootUp 解釈失敗だけを E3 で継続する。
>
> **D5 の設計原則**: SootUp の view 構築は lazy に行い、型階層解決に必要なクラスのみ読み込む (eager な全クラス読み込みをしない)。
>
> **E3 (エラーケース)**: pre-flight 通過後に SootUp が class file を解釈・索引化できない、または自プロジェクト bytecode が classpath にない → `JAVA_SOOTUP_UNAVAILABLE` を出し JavaParser 結果のみで解析継続する。
>
> **スコープ (やること)**: Interface Dispatch、継承、override、interface default method の解決 / SootUp による bytecode・依存 jar の型階層・dispatch 情報の補完。

## テスト観点

spec の `### Testing` より抜粋:

- [context/testing.md](../../../context/testing.md) の Java Analyzer 三層 (Java unit / Go process contract (fake, JVM 不要) / 実 jar E2E) を踏襲する。
- Lombok (`@AllArgsConstructor` / `@RequiredArgsConstructor` 等) でコンストラクタを生成するクラスへの constructor 解決テストを追加する。fixture にはコンストラクタを明示しない Lombok 生成クラスを含める (D7)。本 prompt では Java unit test レベルで検証する (Spring Boot fixture への統合は P3)。
- interface 注入を含むサンプルで、宣言型 (interface) のメソッドが callee に現れ `dispatch: interface` が立つこと (Phase1 の既存観点を維持)。

## 検証コマンド

- ビルド: `cd analyzers/java && ./gradlew shadowJar`
- Unit test: `cd analyzers/java && ./gradlew test`

## 完了条件

- [ ] ステップ 0 で `feature/21` と既存 Draft PR の状態を確認した
- [ ] SootUp 2.0.0 の固定済み3 moduleを `build.gradle.kts` に追加し、`sootup.callgraph` を追加していない
- [ ] SootUp の view 構築が lazy であり、自プロジェクトのコンパイル済み class を含む 3 種の照会対象 (依存 jar / 自プロジェクト class / JavaParser 連携) を実装した
- [ ] Interface Dispatch / Override 候補の索引化を実装し、call graph 生成は行っていないことを確認した
- [ ] Lombok 生成コンストラクタが解決できることをテストで確認した
- [ ] `JAVA_MISSING_JAR` fatal を維持し、E3 の限定条件だけで `JAVA_SOOTUP_UNAVAILABLE` を出すことをテストで確認した
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (新規 diagnostic code `JAVA_SOOTUP_UNAVAILABLE` の feature doc 反映を含む)
- [ ] PR / MR を Ready に変更しレビュアーを指名した
- [ ] 未解決の仕様質問が残っていない
