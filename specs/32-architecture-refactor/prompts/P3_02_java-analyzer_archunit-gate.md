# ArchUnit で外部ライブラリ隔離を機械検査し doc の path を追随する

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止。対象は本 prompt に記載済み)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- ArchUnit は test 依存としてのみ追加する (本体依存・Gradle build 構成のそれ以外の変更は禁止)
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

1. `feature/35` を checkout する (P1_02 で作成済み。Draft PR 継続)
2. P2_02 の完了 (SootUp facade 化済み・全テスト PASS) を確認する

### ステップ 1: ArchUnit の導入とルール記述

1. ArchUnit をバージョン固定の test 依存として `analyzers/java` に追加する
2. 「設計仕様」の隔離 3 段階を JUnit テスト (例: `ArchitectureTest`) として記述する
3. **検証としてまず意図的な違反 import** (例: `analysis/graph` に `sootup.*` を import する一時変更) **を作り、テストが FAIL することを確認してから戻す**
4. `## 検証コマンド` を実行し、diff レビューを回す

### ステップ 2: doc の path 追随 (Java 側)

1. `design/features/java-analyzer/DesignDoc_java-analyzer.md` の旧 package 参照 (`analysis` 直下の `AnalysisRunner` 等、実装と食い違う path 記述) を再編後の実態へ追随する
2. 文書の `最終更新` を更新する
3. diff レビューを回す

### ステップ最終: 最終確認

1. 全テスト (unit + ArchUnit / E2E / compatibility) がパスすることを確認する
2. spec #32 の `## 上位資料からの変更点` の「子 issue で実施」行 (Java 側 path 追随) に反映済み注記を追記する
3. PR を Ready に変更しレビュアーを指名する (merge で issue #35 クローズ)

## 実装コンテキスト

- spec: `specs/32-architecture-refactor/index.md` (解決済みの論点 D3 / D7)
- 正本: `context/engineering.md` (層依存 gate)、`design/features/java-analyzer/DesignDoc_java-analyzer.md` の「内部 package 構成と依存境界」、`adr/0007-layered-architecture-refactor.md`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path: `analyzers/java/build.gradle.kts` (test 依存の追加のみ)、`analyzers/java/src/test/java/com/fukuemon/depwalk/javaanalyzer/` (ArchitectureTest 新規)、`design/features/java-analyzer/DesignDoc_java-analyzer.md`

## 前提条件

- 完了しているべき phase / 依存 prompt: `P2_02_java-analyzer_sootup-facade.md`
- 完了後に着手可能になる後続 prompt: なし (issue #35 の最終 prompt)
- 必要な repo 状態: `feature/35` に P2_02 まで commit 済み (`sootup.*` の漏れゼロ)

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- ルール記述で既存コードに未知の違反が見つかった場合は、勝手に例外を設けず停止して報告する

## タスク境界

### 実装する範囲

- ArchUnit の test 依存追加 (バージョン固定) と隔離 3 段階の JUnit テスト
- java-analyzer feature doc の path 追随と最終更新の同期

### 実装しない範囲

- Gradle build 構成のその他の変更 (shadowJar / compatibility matrix / toolchain)
- 段階間の実行順依存の検査 (隔離 3 段階のみが本 issue のスコープ。追加ルールは別途判断)
- Core (Go) 側の lint — issue #34 の責務
- 解析ロジックの変更

## 設計仕様

spec #32 D3 / D7 (確定) の抜粋 — ArchUnit で検査する隔離 3 段階:

1. `sootup..` に依存してよいのは `..analysis.sootup..` のみ
2. `org.gradle.tooling..` に依存してよいのは `..discovery..` のみ
3. `com.github.javaparser..` に依存してよいのは `..analysis..` 配下のみ (`io` / `protocol` / `preflight` / `discovery` / 直下 `Main` からの依存は禁止)

記述イメージ (正確な API は導入バージョンの ArchUnit 公式ドキュメントに従う):

```java
@AnalyzeClasses(packages = "com.fukuemon.depwalk.javaanalyzer")
class ArchitectureTest {
  @ArchTest
  static final ArchRule sootupIsolated =
      noClasses().that().resideOutsideOfPackage("..analysis.sootup..")
          .should().dependOnClassesThat().resideInAPackage("sootup..");
  // gradle tooling / javaparser も同様に 3 ルール
}
```

- 既存の `./gradlew test` で実行されるため、新しい gate 配線 (lefthook / CI の変更) は不要
- ルールの正本は `context/engineering.md` の層依存 gate (Java)。食い違いがあれば停止

## テスト観点

- IF 隔離違反の import が追加された場合、THEN ArchUnit テストが FAIL する (受け入れ基準 3 の Java 側)。意図的違反での FAIL 確認を必ず実施する
- 既存コードは P2_02 完了時点で違反ゼロのはずであり、ルール追加によって既存コードの変更が発生しないこと
- feature doc の記述と実装の package 構造の一致 (受け入れ基準 5)

## 検証コマンド

- `cd analyzers/java && ./gradlew test`
- `cd analyzers/java && ./gradlew shadowJar`
- `(cd analyzers/java && ./gradlew shadowJar) && ./analyzers/java/gradlew --no-daemon -p testdata/fixtures/java/spring-project clean writeDepwalkClasspath && (cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -count=1)` (要 JDK 25)
- `(cd analyzers/java && ./gradlew gradleCompatibilityTest)`
- `lefthook run pre-commit`

## 完了条件

- [ ] ArchUnit が test 依存 (バージョン固定) として追加され、隔離 3 段階のルールが JUnit テストで記述されている
- [ ] 意図的な違反 import でテストが FAIL することを確認した (一時変更は戻し済み)
- [ ] java-analyzer feature doc の path 参照が実態へ追随し、最終更新を更新した
- [ ] spec #32 の変更点テーブルに反映済み注記を追記した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] PR を Ready に変更しレビュアーを指名した
- [ ] 未解決の仕様質問が残っていない
