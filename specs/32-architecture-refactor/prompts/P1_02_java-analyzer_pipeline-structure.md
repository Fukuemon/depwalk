# Java Analyzer に pipeline package を新設し実行順の知識を集約する

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止。対象ファイルは本 prompt に記載済み)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- Gradle build 構成 (shadowJar / compatibility matrix) は package 移動の機械的追随以外変更しない
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

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` に従う。

1. 最新の base branch (`main`) を取得する (#32 の設計 PR がマージ済みであることを確認する)
2. 作業ブランチ `feature/35` を作成する
3. PR テンプレートを確認し、issue #35 の完了条件を description に転記する
4. Draft PR を作成して push する

### ステップ 1: pipeline package の新設と移動

1. `analysis/pipeline` package を新設し、`analysis/AnalysisRunner.java` を `analysis/pipeline/AnalysisRunner.java` へ移動する (package 宣言・import・呼び出し元 `Main` の参照を機械的に更新)
2. `analysis/TypeSolverFactory.java` を `analysis/context/TypeSolverFactory.java` へ移動する (同様に機械的更新)
3. `## 検証コマンド` を実行し、diff レビューを回す

### ステップ 2: 実行順の README 明文化

1. `analysis/pipeline/README.md` (または package-info.java の javadoc) に段階の実行順を「設計仕様」のとおり明文化する
2. diff レビューを回す

### ステップ最終: 最終確認

1. 全テスト (unit / E2E / compatibility matrix) がパスすることを確認する
2. commit する (PR は P3_02 完了まで Draft のまま)

## 実装コンテキスト

- spec: `specs/32-architecture-refactor/index.md` (解決済みの論点 D7 / Content・Data 設計の Java 配置図)
- 正本: `design/features/java-analyzer/DesignDoc_java-analyzer.md` の「内部 package 構成と依存境界」、`adr/0007-layered-architecture-refactor.md`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path: `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/` (Main.java、analysis/AnalysisRunner.java、analysis/TypeSolverFactory.java、analysis/context/)、`analyzers/java/src/test/`

## 前提条件

- 完了しているべき phase / 依存 prompt: なし (#32 設計 PR の main マージのみ。issue #34 側とは独立・並行可)
- 完了後に着手可能になる後続 prompt: `P2_02_java-analyzer_sootup-facade.md`
- 必要な repo 状態: main に改訂済み `design/features/java-analyzer/DesignDoc_java-analyzer.md` が存在する

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- `analysis/pipeline` 新設と `AnalysisRunner` の移動 (機械的な package / import 更新)
- `TypeSolverFactory` の `analysis/context` への移動
- 実行順の README 明文化

### 実装しない範囲

- SootUp facade 化・`sootup.*` import の除去 — P2_02 の責務
- ArchUnit 導入 — P3_02 の責務
- 既存 9 sub-package (scope / context / augment / attribution / sootup / spring / graph / completeness / normalize) の再配置 (既存粒度を維持する。移動対象は上記 2 クラスのみ)
- 解析ロジック・出力・Protocol の一切の変更
- Core (Go) 側 — issue #34 の責務

## 設計仕様

spec #32 D7 (確定 + 2026-07-24 精緻化) の抜粋:

- `javaanalyzer` 直下 (`protocol` / `io` / `preflight` / `discovery`) は現状維持
- `analysis` 配下は既存 sub-package の粒度が段階として概ね妥当。再編の実体は ① `pipeline/` 新設 (実行順を知る唯一の場所として `AnalysisRunner` を移動)、② `sootup/` の facade 化 (P2_02)、③ `TypeSolverFactory` の `context/` への移動、④ 実行順の README 明文化
- **段階の実行順は `pipeline` (Runner) だけが知る**。README に明文化する実行順 (AnalysisRunner の実測):
  1. scope 列挙 (`scope`)
  2. context 構築 — JavaParser + augment (`context` / `augment`)
  3. attribution 準備 (`attribution`)
  4. SootUp 型階層 index (`sootup`)
  5. Spring DI index (`spring`)
  6. call graph 構築 (`graph`)
  7. completeness 検査 (`completeness`)
  8. io 出力 (`io`)
  - `normalize` は段階横断の naming util

## テスト観点

- 既存テストスイート (Java unit / E2E / golden / Gradle compatibility matrix) がテスト本体のロジック変更なし (package / import の機械的修正のみ) で全件 PASS する (受け入れ基準 4)
- 外部挙動 (JSONL Protocol / diagnostic / exit code) が一切変わらない

## 検証コマンド

- `cd analyzers/java && ./gradlew shadowJar`
- `cd analyzers/java && ./gradlew test`
- `(cd analyzers/java && ./gradlew shadowJar) && ./analyzers/java/gradlew --no-daemon -p testdata/fixtures/java/spring-project clean writeDepwalkClasspath && (cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -count=1)` (要 JDK 25)
- `(cd analyzers/java && ./gradlew gradleCompatibilityTest)`
- `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 でブランチ `feature/35` と Draft PR を作成した
- [ ] `analysis/pipeline/AnalysisRunner` と `analysis/context/TypeSolverFactory` へ移動済み
- [ ] 実行順が pipeline の README に明文化されている
- [ ] テスト本体のロジック変更が diff に含まれていない
- [ ] `## 検証コマンド` がすべてパスする
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] 未解決の仕様質問が残っていない
