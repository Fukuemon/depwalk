# E2E fixture と性能 baseline: 実 jar での caller/callee 照合

## 絶対ルール

- spec に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない。
- P1_01 (Core 配線) と P2_01 (解析本体) の成果を前提にし、Core / Analyzer の実装本体を変更しない。E2E で見つけた不具合は該当 prompt の責務として報告し、本 prompt 内で本体を書き換えない (テスト容易性のための軽微な修正も、理由を明示して確認を取る)。
- fixture は `testdata/fixtures/java/` に閉じる。fixture の Java コードは E2E の照合対象として設計し、実アプリケーションの機能を作り込まない。
- E2E は Go 標準 `testing` で書く。`testify` / mock generator / `go-cmp` を導入しない。
- 性能の数値目標を本 prompt で新設しない。baseline の実測値を記録するのが責務 (目標値の確定は baseline 取得後の別作業)。
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
2. P1_01 (`depwalk analyze` 配線) と P2_01 (解析本体) が完了していることを確認する。
3. `cd analyzers/java && ./gradlew shadowJar` で fat jar が build できることを確認する。
4. 検証: 作業開始前の branch と差分を記録する。

### ステップ 1: サンプル Java/Spring fixture を作成する

1. `testdata/fixtures/java/` にサンプル Java/Spring プロジェクトを作成する。含めるべき構造 (E2E の照合対象):
   - interface 注入 (Spring 風の interface 経由呼び出し → `dispatch: interface` の照合対象)
   - 継承 (override あり / なし → 帰属型の宣言サイト分岐の照合対象)
   - jar 由来メソッドの引き上げ対象 (例: 継承 library メソッドの呼び出し。fixture の classpath 構成に依存)
   - lambda を含むメソッド (`viaLambda: true` の照合対象)
   - パース不能ファイル (部分解析の継続の照合対象)
   - 未解決 symbol を生む呼び出し (`JAVA_UNRESOLVED_SYMBOL` の照合対象)
2. fixture の既知の caller / callee 集合 (期待値) を fixture と同じ場所に期待値ファイルとして置く。
3. fixture の classpath の準備方法 (依存なしで空配列にするか、最小の jar を用意するか) を fixture 内 README 等に明記する。
4. 検証: fixture が意図した構造 (上記 6 種) をすべて含むことをレビューで確認する。
5. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す。

### ステップ 2: E2E test を実装する

1. Go 標準 `testing` から `depwalk analyze` を実 jar (`--analyzer-cmd "java -jar <shadowJar 成果物>"`) で起動する E2E test を書く。JDK 25 + build 済み jar を前提とし、無い環境では skip する (CI の Go job では走らせない)。
2. 照合内容:
   - 既知の caller / callee 集合と出力の一致 (S1 / S2)
   - interface 注入サンプルで宣言型 (interface) のメソッドが callee に現れ `dispatch: interface` が立つこと
   - パース不能ファイル混在で `diagnostic` が出つつ他ファイルの解析が継続すること
   - 未解決 symbol 混在で `JAVA_UNRESOLVED_SYMBOL` の `diagnostic` が出つつ解決済み `callEdge` が揃うこと
3. 契約境界ごとに subtest を分け、失敗時に壊れた契約が test output から分かる構造にする。
4. 検証: `cd analyzers/java && ./gradlew shadowJar` の後、E2E test を実行してパスさせる。
5. diff レビューを回す。

### ステップ 3: 性能 baseline を計測して記録する

1. fixture プロジェクトに対する実測値を取得する: 解析ファイル数 / 所要時間は Analyzer の stderr 計測出力から、最大 RSS は Go 親プロセスの `os.ProcessState.SysUsage()` から取得する。
2. 実測値を `design/features/java-analyzer/DesignDoc_java-analyzer.md` の「性能方針」節に baseline として記入する (数値目標の確定は別作業。baseline の記録までが本 prompt の責務)。
3. 検証: feature doc の性能節に baseline (計測日 / fixture 規模 / 3 指標) が記録されていることを確認する。
4. diff レビューを回す。

### ステップ最終: 最終確認

1. `## 検証コマンド` の全コマンドがパスすることを確認する。
2. spec の `## 上位資料からの変更点` に必要な追記 (feature doc への baseline 記入) を phase: track に渡す。

## 実装コンテキスト

- spec: `specs/9-java-analyzer/index.md` (D9 / D10 は決定経緯。E2E 観点は `## テスト / 評価方針`)
- 設計の正本: `design/features/java-analyzer/DesignDoc_java-analyzer.md` (テスト観点 / 性能方針 — baseline の記入先)
- Issue: `#9` / Branch: `feature/9`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 実装対象:
  - `testdata/fixtures/java/` (サンプル Java/Spring fixture + 期待値)
  - E2E test (Go 標準 `testing`。配置は既存の E2E 配置規約に従い、無ければ `core/` 側の test として最小案を提示)
  - `design/features/java-analyzer/DesignDoc_java-analyzer.md` の性能節 (baseline 記入のみ)
- 参照する path (成果物として使用、変更しない):
  - `core/` (P1_01 の `depwalk analyze`)
  - `analyzers/java/` (P2_01 の解析本体 / shadowJar)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P1_01_core_analyze-command.md` + `P2_01_java-analyzer_extraction.md` (両方)
- 完了後に着手可能になる後続 prompt: なし。#9 Phase1 の実装 prompt 群は完了に向かう。
- 必要な repo 状態: `depwalk analyze` が実装済み / `analyzers/java/` の fat jar が build 可能 / 実行環境に JDK 25

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める。
- 推測で実装を進めない。
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する。
- E2E test の配置場所 / skip 条件の表現 / fixture の classpath 構成 (空配列 vs 最小 jar) で判断不能になった場合は、最小案を提示して確認する。
- E2E で Core / Analyzer 本体の不具合を見つけた場合は停止し、該当 prompt (P1_01 / P2_01) の責務として報告する。

## タスク境界

### 実装する範囲

- `testdata/fixtures/java/` のサンプル Java/Spring fixture (interface 注入 / 継承 / lambda / パース不能ファイル / 未解決 symbol を含む) と既知 caller/callee 期待値。
- 実 jar を `depwalk analyze` から起動して期待値と照合する E2E test (Go 標準 `testing`、JDK 25 前提、無い環境では skip)。
- 性能 baseline の計測 (解析ファイル数 / 所要時間 / 最大 RSS) と feature doc 性能節への記入。

### 実装しない範囲

- Core / Java Analyzer の実装本体の変更 → P1_01 / P2_01。
- 性能の数値目標の確定 (baseline 記録まで。目標値は baseline 取得後の別作業)。
- S3 (出力形式のパース可否) / CLI 出力レベルの最終照合 (CLI interface spec 完了後に完成する)。
- CI workflow の定義 (Go job / Java + E2E job の分割は context/testing.md に記録済み。workflow ファイルの作成は別作業)。

## 設計仕様

feature doc / spec からの抜粋:

**三層テストにおける E2E の位置づけ** (正本: feature doc「テスト観点」):

- Java unit test (JUnit) と Go process contract (fake analyzer) は P1/P2 の各 prompt が担う。E2E (実 jar) のみが JDK 25 + build 済み fat jar を要求し、`depwalk analyze` から実 jar を起動して既知の caller / callee 集合と出力を照合する (S1 / S2)。
- protocol 契約の検査ロジックを Java / Go に二重実装しない。両者が実際に噛み合うことは E2E が担保する。

**fixture の classpath**: classpath は必須入力だが、値としての空配列を許す。依存を持たない純 Java の fixture は空 classpath で解析でき、軽量な fixture を維持できる。

**性能方針** (正本: feature doc「性能方針」):

- Analyzer は解析ファイル数 / 所要時間 / 未解決件数を stderr に出力する (protocol 対象外)。
- 数値目標は未定。Phase1 実装時に fixture プロジェクトの実測値 (ファイル数 / 所要時間 / 最大 RSS) を baseline として記録し、その後に feature doc へ確定値を記録する。**baseline を測ることが Phase1 の完了条件に含まれる** (数値未定のまま放置されない状態にする)。

**起動方法** (正本: feature doc「起動契約」/ `context/project.md` Quick Commands):

- `depwalk analyze --analyzer-cmd "java -jar analyzers/java/build/libs/<jar>"` (classpath 等は `--analyzer-meta` で渡す)。

## テスト観点

spec「テスト / 評価方針 — E2E (実 jar / `testdata/fixtures/java/`)」からの抜粋:

- 既知の caller / callee 集合と `depwalk analyze` の出力の照合 (S1 / S2)。
- interface 注入を含むサンプルで、宣言型 (interface) のメソッドが callee に現れ `dispatch: interface` が立つこと (Phase1 の S4 前段)。
- パース不能ファイルを混ぜた fixture で、`diagnostic` が出つつ他ファイルの解析が継続すること。
- 未解決 symbol を含む fixture で、`JAVA_UNRESOLVED_SYMBOL` の `diagnostic` が出つつ解決済みの `callEdge` が揃うこと。
- 計測指標: fixture に対する解析ファイル数 / 所要時間 (Analyzer の stderr 計測出力から取得) と最大 RSS (Go 親プロセスの `os.ProcessState.SysUsage()` から取得)。未解決 symbol 件数 / パース失敗ファイル数は fixture の既知の期待値と照合する。

## 検証コマンド

- `cd analyzers/java && ./gradlew shadowJar`
- `cd core && go test ./...` (E2E は JDK 25 + jar がある環境でのみ実行され、無ければ skip)
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `git diff --check`

## 完了条件

- [ ] ステップ 0 で branch / P1_01・P2_01 完了状態 / fat jar build を確認した。
- [ ] `testdata/fixtures/java/` に 6 種の構造 (interface 注入 / 継承 / 引き上げ対象 / lambda / パース不能 / 未解決 symbol) を含む fixture がある。
- [ ] fixture の既知 caller / callee 期待値が置かれている。
- [ ] E2E test が実 jar を `depwalk analyze` から起動し、期待値と照合してパスする。
- [ ] E2E test が JDK 25 / jar の無い環境で skip される (Go job を壊さない)。
- [ ] `dispatch: interface` / 部分解析の継続 / 未解決 symbol の共存が E2E で照合されている。
- [ ] 性能 baseline (解析ファイル数 / 所要時間 / 最大 RSS) を計測し、`design/features/java-analyzer/DesignDoc_java-analyzer.md` の性能節に記入した。
- [ ] Core / Analyzer の実装本体を変更していない。
- [ ] `## 検証コマンド` がすべてパスする。
- [ ] 各ステップで diff レビューを実施し、指摘を対応した。
- [ ] 未解決の仕様質問が残っていない。
