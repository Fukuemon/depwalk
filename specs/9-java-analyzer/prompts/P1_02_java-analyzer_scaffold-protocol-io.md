# Java Analyzer scaffold と protocol I/O 基盤

## 絶対ルール

- spec に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない。
- 実装は `analyzers/java/` に閉じる。Core (`core/`) の変更は一切行わない。
- 実装言語は Java (Kotlin は不採用と決定済み。理由は feature doc 参照)。
- protocol の schema (`analysisRequest` / record 種別 / field) を変更・拡張しない。準拠側として実装する。
- JavaParser による解析本体 (AST 解析 / 型解決 / `methodSymbol` / `callEdge` の生成) は実装しない (P2_01 の責務)。
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

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` に従う。Issue は `#9`。

1. 現在の branch が `feature/9` (または派生の実装 branch) であることを確認する。
2. `git status --short` で意図しない差分がないことを確認する。
3. 検証: 作業開始前の branch と差分を記録する。

### ステップ 1: Gradle project を scaffold する

1. `analyzers/java/` に Gradle (Kotlin DSL) の単一 module を作成する: `gradlew` wrapper 同梱 / Gradle toolchain で JDK 25 を固定 / Shadow plugin で単一 fat jar を生成 / JUnit を test framework にする。
2. 依存に JavaParser + SymbolSolver を宣言する (使用は P2_01。本 prompt では build が通ることまで)。
3. 内部 package 構成は責務の目安 (request 受領 / AST 解析・型解決 / 帰属型決定 / record 出力) に沿って本 prompt で最小構成を決める。
4. 検証: `cd analyzers/java && ./gradlew test` と `cd analyzers/java && ./gradlew shadowJar` がパスし、`java -jar build/libs/<jar>` が起動する。
5. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す。

### ステップ 2: stdin からの `analysisRequest` 受領を実装する

1. テストを先に書く (TDD): valid / invalid request、未知 field の無視を検証する。
2. stdin から JSONL 1 件の `analysisRequest` を読み取り、DTO に deserialize する。未知 field は protocol の規則どおり無視する。
3. 検証: `cd analyzers/java && ./gradlew test` を実行する。
4. diff レビューを回す。

### ステップ 3: pre-flight 検査を実装する

1. テストを先に書く: 下記「設計仕様」の error 3 種と、空配列 classpath が正当な入力であることを検証する。
2. 解析開始前に一括で検査する: `language != "java"` → `JAVA_INVALID_REQUEST` / metadata に `classpath` key が無い → `JAVA_MISSING_CLASSPATH` / classpath の jar が存在しない・読めない → `JAVA_MISSING_JAR`。いずれも `error` record を stdout へ出力し、非ゼロ exit code で終了する。
3. 検証: `cd analyzers/java && ./gradlew test` を実行する。
4. diff レビューを回し、検査が解析開始前に一括で行われる構造 (遅延検出しない) であることを確認する。

### ステップ 4: JSONL 書出基盤と stderr 計測の枠を実装する

1. テストを先に書く: 全 record に `schemaVersion` `"1"` と `recordType` が含まれること、record が逐次 flush されること、stderr に protocol record が混ざらないことを検証する。
2. `methodSymbol` / `callEdge` / `diagnostic` / `error` を JSONL 1 行 1 record で stdout へ書き出す writer を実装する。streaming flush (グラフ全体をメモリ保持しない) を前提の API にする。
3. stderr へ計測サマリ (解析ファイル数 / 所要時間 / 未解決件数) を出力する枠を用意する (値の集計は P2_01 で埋まる)。
4. pre-flight を通過した場合は、現段階では record 0 件で exit code 0 で終了する (0 件の正常解析は protocol 上 success)。
5. 検証: `cd analyzers/java && ./gradlew test`、`./gradlew shadowJar` 後に fat jar へ手で `analysisRequest` を流し、exit code / stdout / stderr の分離を確認する。
6. diff レビューを回す。

### ステップ最終: 最終確認

1. `## 検証コマンド` の全コマンドがパスすることを確認する。
2. `core/` に差分がないことを `git status --short` で確認する。
3. spec の `## 上位資料からの変更点` に必要な追記がないかを phase: track に渡す。

## 実装コンテキスト

- spec: `specs/9-java-analyzer/index.md` (D1 / D3 / D8 は決定経緯)
- 設計の正本: `design/features/java-analyzer/DesignDoc_java-analyzer.md` (実装基盤 / metadata 契約 / diagnostic・error code 体系 / 性能方針)
- protocol 契約の正本: `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md` / `adr/0001-analyzer-protocol-jsonl-spi.md`
- Issue: `#9` / Branch: `feature/9`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 実装対象:
  - `analyzers/java/` (新規 Gradle module)
- 参照する path:
  - `testdata/analyzer-protocol/` (protocol の JSONL fixture。record 形の参考として read-only)
- 参照しない path:
  - `core/` (一切変更しない)

## 前提条件

- 完了しているべき phase / 依存 prompt: なし (P1_01 と並列可。変更ファイルが衝突しない)
- 完了後に着手可能になる後続 prompt: `P2_01_java-analyzer_extraction.md`
- 必要な repo 状態: JDK 25 が実行環境にあること

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める。
- 推測で実装を進めない。
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する。
- Gradle plugin の version / JSON library の選定など本 prompt に未記載の実装詳細は、最小案 (安定版 / 標準的な選択) を提示して確認する。protocol schema に関わる judgment は必ず停止する。

## タスク境界

### 実装する範囲

- `analyzers/java/` の Gradle (Kotlin DSL) + Shadow + JDK 25 toolchain scaffold。
- stdin からの `analysisRequest` 読み取り (JSONL 1 件 / 未知 field 無視)。
- pre-flight 検査 (`JAVA_INVALID_REQUEST` / `JAVA_MISSING_CLASSPATH` / `JAVA_MISSING_JAR`) と `error` + 非ゼロ exit。
- JSONL record 書出基盤 (`schemaVersion` `"1"` / streaming flush)。
- stderr 計測出力の枠。
- 上記の JUnit test。

### 実装しない範囲

- JavaParser / SymbolSolver による解析本体 (AST 解析 / 型解決 / 帰属型決定 / `methodSymbol`・`callEdge` 生成) → P2_01。
- `JAVA_UNRESOLVED_SYMBOL` / `JAVA_PARSE_ERROR` / `JAVA_ENTRYPOINT_NOT_FOUND` の diagnostic 生成 → P2_01。
- Core 側の変更 (`depwalk analyze` 配線) → P1_01。
- E2E fixture / baseline 計測 → P2_02。
- 後続 feature #21 の範囲 (SootUp 型階層補完 / Spring Bean・DI 解決 — ADR-0005)。

## 設計仕様

feature doc (正本: `design/features/java-analyzer/DesignDoc_java-analyzer.md`) からの抜粋:

**実装基盤**:

- build tool: Gradle (Kotlin DSL)。`gradlew` wrapper を同梱し、CI に Gradle 本体の事前インストールを要求しない。
- JDK: 25 LTS。Gradle toolchain で固定する (Analyzer 自身が動く JVM の version。解析対象ソースの言語レベルとは独立)。
- 配布形態: 単一 fat jar (Gradle Shadow plugin)。Core は `java -jar <path>` の 1 コマンドで起動できる。

**process contract** (正本: analyzer-protocol feature doc / ADR-0001):

- stdin: `analysisRequest` 1 件 → close。stdout: JSONL 逐次 (1 行 1 record)。stderr: 計測ログ (protocol 対象外)。exit code: 0 = 成功、非ゼロ = fatal。
- 全 record は `schemaVersion` (Phase1 は `"1"`) と `recordType` を必須 field に持つ。
- `analysisRequest` の未知 field は無視する。`methodSymbol` / `callEdge` が 0 件の正常解析は success。

**metadata 契約 (Java 固有 key)**:

| key                   | 型          | 必須/任意                       | 意味                                                                           |
| --------------------- | ----------- | ------------------------------- | ------------------------------------------------------------------------------ |
| `classpath`           | string 配列 | 必須 (key として。空配列は許容) | 依存 jar / classes dir の path。key 不在は `JAVA_MISSING_CLASSPATH` の `error` |
| `liftExcludePackages` | string 配列 | 任意                            | 引き上げ除外 package。解釈は P2_01 の責務 (本 prompt では受領のみ)             |

**pre-flight 検査**: classpath key の有無 / 指定 jar の存在・読み取り可否 / `language` の検査は、解析開始前に一括で行う。型解決の途中で jar 欠落を遅延検出すると、出力済み record が「一見成功した出力」として観測されうるため。fatal は `error` + 非ゼロ exit で即時停止する。

**error code (fatal / 非ゼロ exit)**:

| code                     | 出る場面                                                                                  |
| ------------------------ | ----------------------------------------------------------------------------------------- |
| `JAVA_MISSING_CLASSPATH` | `analysisRequest.metadata` に classpath の key が無い (値としての空配列は error にしない) |
| `JAVA_MISSING_JAR`       | classpath に指定された jar が存在しない / 読めない                                        |
| `JAVA_INVALID_REQUEST`   | `analysisRequest` が Java Analyzer として処理できない (未対応 `language` 等)              |
| `JAVA_INTERNAL_ERROR`    | 上記以外の継続不能な内部エラー                                                            |

**性能方針** (書出基盤に関わる分のみ): record を逐次 stdout へ flush し、Analyzer 側にグラフ全体をメモリ保持しない。計測 (解析ファイル数 / 所要時間 / 未解決件数) は stderr に出力する (protocol record としては出さない)。

## テスト観点

spec「テスト / 評価方針 — Java unit test」からの抜粋 (本 prompt の範囲のみ):

- pre-flight 検査 (classpath key 不在 / jar 欠落 / `language != "java"`) が解析開始前に fatal になること。
- `diagnostic` / `error` の code と severity の対応 (本 prompt では error 4 種の code / 非ゼロ exit)。
- classpath の値としての空配列が正当な入力として通ること (`JAVA_MISSING_CLASSPATH` にならない)。
- 全出力 record に `schemaVersion` `"1"` と `recordType` が含まれること。
- stderr に protocol record が混ざらないこと。
- `analysisRequest` の未知 field を無視できること。

## 検証コマンド

- `cd analyzers/java && ./gradlew test`
- `cd analyzers/java && ./gradlew shadowJar`
- `git diff --check`
- (Core 非変更の確認) `git status --short` で `core/` 配下に差分がないこと

## 完了条件

- [ ] ステップ 0 で branch と差分状態を確認した。
- [ ] `analyzers/java/` に Gradle (Kotlin DSL) module があり、`gradlew` wrapper が同梱されている。
- [ ] Gradle toolchain で JDK 25 が固定されている。
- [ ] `./gradlew shadowJar` で単一 fat jar が生成され、`java -jar` で起動できる。
- [ ] stdin から `analysisRequest` を 1 件読み取り、未知 field を無視できる。
- [ ] pre-flight 検査 3 種 (`JAVA_INVALID_REQUEST` / `JAVA_MISSING_CLASSPATH` / `JAVA_MISSING_JAR`) が解析開始前に一括で行われ、`error` + 非ゼロ exit になる。
- [ ] classpath の空配列が正当な入力として通る。
- [ ] JSONL 書出基盤が `schemaVersion` `"1"` / `recordType` を必須で含み、逐次 flush する。
- [ ] stderr 計測出力の枠があり、protocol record と分離されている。
- [ ] `core/` に差分がない。
- [ ] `## 検証コマンド` がすべてパスする。
- [ ] 各ステップで diff レビューを実施し、指摘を対応した。
- [ ] 未解決の仕様質問が残っていない。
