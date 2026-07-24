# SootUp を analysis/sootup の facade に封じ込め自前型で公開する

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止。対象クラス 7 件は本 prompt に記載済み)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- SootUp の依存 module / バージョン (`sootup.core` / `sootup.java.core` / `sootup.java.bytecode.frontend` 2.0.0) を変更しない
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
2. P1_02 の完了 (pipeline 移動済み・全テスト PASS) を確認する

### ステップ 1: facade の設計と導入

1. テストを先に書く / 更新する (facade の公開型に対する unit test)
2. `analysis/sootup` に、利用側 6 クラス (7 ファイル) が現在 `sootup.*` 型で受け渡ししている情報 (型階層照会・bytecode member 照会・クラス/メソッドシグネチャ) を **自前型 (record / interface) で公開する facade** を定義する。既存 `SootUpTypeHierarchyIndex` の公開 API を自前型へ置き換える形でよい
3. `## 検証コマンド` を実行し、diff レビューを回す

### ステップ 2: 利用側 6 クラスの sootup import 除去

1. 次の 6 クラス (7 ファイル) から `sootup.*` の import を除去し、facade の自前型経由に置き換える (1〜2 クラスずつ進め、都度テストを回す):
   - `analysis/pipeline/AnalysisRunner`
   - `analysis/graph/CallGraphBuilder`
   - `analysis/graph/SourceMethodIndex`
   - `analysis/spring/SpringDiIndex`
   - `analysis/completeness/ProjectBytecodeMemberIndex`
   - `analysis/augment/AugmentedJavaParserClassDeclaration` / `analysis/augment/SynthesizedBytecodeMethodDeclaration`
2. `## 検証コマンド` を実行し、diff レビューを回す

### ステップ最終: 最終確認

1. `analysis/sootup` 以外に `sootup.` の import が残っていないことを確認する (import 確認の grep は許可された検証手順)
2. 全テスト (unit / E2E / compatibility) がパスすることを確認する
3. commit する (PR は P3_02 完了まで Draft のまま)

## 実装コンテキスト

- spec: `specs/32-architecture-refactor/index.md` (解決済みの論点 D7)
- 正本: `design/features/java-analyzer/DesignDoc_java-analyzer.md` の「内部 package 構成と依存境界」「型解決」「solver 層の bytecode member 合成」、`adr/0007-layered-architecture-refactor.md`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path: `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/` (sootup/ / pipeline/ / graph/ / spring/ / completeness/ / augment/)、`analyzers/java/src/test/`

## 前提条件

- 完了しているべき phase / 依存 prompt: `P1_02_java-analyzer_pipeline-structure.md`
- 完了後に着手可能になる後続 prompt: `P3_02_java-analyzer_archunit-gate.md`
- 必要な repo 状態: `feature/35` に P1_02 が commit 済み

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- facade の自前型化で性能 (解析時間) への影響が疑われる変換 (大量 copy 等) が必要になった場合は停止して報告する
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- `analysis/sootup` の facade (自前型公開)
- 上記 6 クラス (7 ファイル) の `sootup.*` import 除去と facade 経由化

### 実装しない範囲

- ArchUnit による機械検査 — P3_02 の責務
- SootUp の利用範囲・解析ロジック (型階層・override・interface 実装候補の索引) の変更 — 既存の解決規則 (feature doc 正本) を一切変えない
- JavaParser / SymbolSolver の隔離 (analysis 配下では自由に使ってよい)
- Gradle 依存の変更

## 設計仕様

spec #32 D7 精緻化 (確定) の抜粋:

- **SootUp は `analysis/sootup` (adapter) に完全封じ込め、facade が自前型で公開する**。現状 7 クラスに漏れている `sootup.*` import を除去する
- JavaParser / SymbolSolver は解析エンジンの中核として `analysis` 配下では自由に使ってよい (本 prompt では触らない)
- 外部挙動 (JSONL 出力・diagnostic・完全性 gate の判定) は一切変えない。facade は既存の照会結果と同じ情報を自前型で返すだけであり、解決規則の意味論を変更しない
- SootUp の役割 (feature doc 正本・不変): 型階層・override・interface 実装候補の索引のみ (call graph 生成は委譲しない)。project 所有 classes output を external jar より優先して登録する既存規則も不変

## テスト観点

- 既存テストスイート (Java unit / E2E / golden / compatibility matrix) がテスト本体のロジック変更なしで全件 PASS (facade 導入に伴うテストの import / 型修正は可)
- `analysis/sootup` 以外に `sootup.*` import がゼロ
- 解析結果 (methodSymbol / callEdge / diagnostic) が現状と同一 (golden で担保)
- E2E 実行時間が現行から大きく逸脱しない

## 検証コマンド

- `cd analyzers/java && ./gradlew shadowJar`
- `cd analyzers/java && ./gradlew test`
- `(cd analyzers/java && ./gradlew shadowJar) && ./analyzers/java/gradlew --no-daemon -p testdata/fixtures/java/spring-project clean writeDepwalkClasspath && (cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -count=1)` (要 JDK 25)
- `(cd analyzers/java && ./gradlew gradleCompatibilityTest)`
- `lefthook run pre-commit`

## 完了条件

- [ ] `analysis/sootup` の facade が自前型で公開されている
- [ ] 対象 7 ファイルから `sootup.*` import が除去され、`analysis/sootup` 以外に残っていない
- [ ] 解析ロジックの意味論変更が diff に含まれていない
- [ ] `## 検証コマンド` がすべてパスし、E2E 実行時間が大きく逸脱していない
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] 未解決の仕様質問が残っていない
