# P5_01: CLI プロセス E2E (golden 照合・exit code) と SLO 計測 (D9)

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- 別 prompt の責務範囲 (タスク境界の「実装しない範囲」) に踏み込まない

### 実装アンチパターンの回避 (必守)

- スコープ厳守: spec / 本 prompt に明記された機能のみ実装する。未要求の機能追加・先回りの抽象化・無関係なリファクタ・暗黙の互換維持をしない。
- 既存規約への整合: 命名・エラー処理・ログ・テスト・API 連携方式は、対象コードベースの既存パターンに合わせる。新方式を持ち込む場合は理由を述べて確認を取る。
- 観測可能な契約の保持: UI 文言・イベント名・戻り値・エラーメッセージ・ログ形式・API を要求なく変更しない。変更が必要なら理由と影響を明記する。
- 推測の排除: 要件・業務ルール・API 仕様が不明なら停止して確認する。それらしいが誤った実装 (存在しない API 呼び出し / 非互換な引数) を避け、import と API の実在を確認する。
- fallback の最小化: `??` / `||` / 既定引数 / 多段 fallback / 暗黙のエラー握り潰しは「任意データ」に限定する。必須データの欠落は隠さず明示的に失敗させる。
- 過剰実装の排除: 単純な条件分岐を strategy / handler map に置換しない。要求も計測もない caching / memoization を入れない。
- dead code を残さない: 到達不能コード・未使用の変数 / 関数 / import / export・変更後に不要化した型定義を削除する。
- 判断の記録: 非自明な設計判断は理由 (or spec / ADR へのリンク) を残す。
- **完了条件のタスク化**: 作業開始前に「完了条件」セクションの各項目を todo として登録し、各ステップ完了時に状態を更新すること。タスク化せずに作業を開始することは禁止。

## 作業ステップ (この順序で実行する)

### ステップ 0: ブランチ準備

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` に従う。issue #22 の作業ブランチ (`feature/22`) を継続利用し、P1-P4 の commit を含む状態から開始する。

### ステップ 1: 探索クエリの CLI プロセス E2E (S1/S2/S3・exit code)

1. テストを先に書く (TDD): `core/e2e/` に探索クエリの CLI プロセス E2E を追加する。#24 整備の harness (`core/e2e/gradle_multiproject_cli_test.go` の `buildCoreCLI` / `runCLI` helper) を再利用し、新設しない。検証内容: (a) S1 — 既知の呼び出し関係を持つ Java/Spring fixture に対し `--method` + `--direction caller` の console / json 出力が golden file と完全一致すること、(b) S2 — `--direction callee` で同様、(c) S3 — json 出力が golden 一致に加えて `json.Unmarshal` に成功すること、(d) exit code — 成功 (0)、Analyzer fatal (1)、不正 flag 値・selector 不一致 (2) の 3 区分、(e) `--max-depth` 指定時の cutoff 注釈が出力に反映されること。
2. golden file は既存 fixture 規約 (`testdata/` 配下) に置く。配置の詳細 (repo root `testdata/` か既存 E2E fixture 隣接か) は既存 E2E の golden の置き方に合わせ、迷ったら停止して確認する。
3. `## 検証コマンド` (E2E 含む) を実行し、diff レビューを回す。

### ステップ 2: SLO 数値目標の計測と feature doc への記録

1. 実プロジェクト相当 fixture (既存 E2E fixture) で解析時間・最大 RSS を複数回計測する。計測経路は #24 (D8) の経路別計測 (single 明示 / single discovery / multi discovery の初回値・warm 中央値) を入力・比較対象とする。
2. 確定した数値目標を `design/features/java-analyzer/DesignDoc_java-analyzer.md` の性能方針節へ追記する (同 doc の最終更新ヘッダも同期する)。
3. spec の `## 上位資料からの変更点` に反映を記録する。

### ステップ最終: 最終確認

1. 全テスト / E2E がパスすることを確認する。
2. issue #22 の完了条件 (下記「設計仕様」の受け入れ基準) をすべて満たしていることを確認し、PR を Ready にする準備を整える。

## 実装コンテキスト

- spec: `specs/22-cli-interface/index.md` (D9、テスト / 評価方針、Performance / Security 設計の SLO 委譲)
- durable 正本: `design/features/cli/DesignDoc_cli.md` (テスト節)、`context/testing.md` (E2E 2 層構造)
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `core/e2e/` (`gradle_multiproject_cli_test.go` の harness を再利用)
  - `testdata/fixtures/java/` (既存 fixture と golden 規約)
  - `design/features/java-analyzer/DesignDoc_java-analyzer.md` (性能方針節 — SLO 記録先)

## 前提条件

- 依存 prompt: `P4_01_core_cli-flags-exit-codes.md` (完了済みであること — E2E は完成した CLI を検証するため)
- 完了後に着手可能になる後続 prompt: なし (最終 prompt)
- 必要な repo 状態: JDK 25 と Java Analyzer fat jar が build 可能なこと (E2E 要件)

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- golden の配置・SLO の合否ラインの置き方に迷ったら、選択肢を整理して停止・確認する

## タスク境界

### 実装する範囲

- 探索クエリの CLI プロセス E2E (os/exec、golden 照合、exit code 3 区分)
- golden fixture の追加
- SLO 計測と `design/features/java-analyzer/DesignDoc_java-analyzer.md` 性能方針節への数値記録

### 実装しない範囲

- CLI / use case / graph / output 本体の変更 (P1-P4 の責務。E2E で不具合を見つけたら停止して報告する)
- 既存のグラフレベル E2E (`analyze.Run` 直接呼び出し) の変更 (2 層構成を維持)
- 解析時間・RSS の機械的な合否判定のテストへの組み込み (spec の計測指標で「組み込まない」と確定済み)

## 設計仕様

spec #22 (抜粋):

- **D9**: E2E の CLI 出力照合は os/exec によるバイナリ起動で行う。`go build` した depwalk バイナリを実プロセスとして起動し、stdout / stderr / exit code を検証する (flag パースや D8 の exit code 制御も検証範囲)。照合粒度は console / json とも golden file との完全一致とし、json は加えて Unmarshal 成功を検証して S3 を直接担保する。既存のグラフレベル E2E は残し、CLI プロセス E2E を追加する 2 層構成とする。harness は #24 整備の `buildCoreCLI`/`runCLI` を再利用する。
- **SLO (Performance 設計)**: 実プロジェクト相当の fixture による複数回計測 (解析時間・最大 RSS) を行い、確定した数値目標は `design/features/java-analyzer/DesignDoc_java-analyzer.md` の性能方針節へ追記する。#24 (D8) の経路別計測を入力として使う。テストへの機械的な合否判定は組み込まない (リリース判定は S1-S3 の E2E 照合)。

受け入れ基準 (spec `## 要件の解釈` より抜粋):

- [S1] WHEN 開発者が CLI で対象メソッド (method selector) と caller 方向・深さ上限を指定して実行したとき、システムは到達した caller 集合を指定した出力形式で出力する
- [S2] WHEN 開発者が CLI で対象メソッドと callee 方向・深さ上限を指定して実行したとき、システムは到達した callee 集合を指定した出力形式で出力する
- [S3] WHEN 利用者または CI が JSON 出力形式を指定して実行したとき、システムは機械的にパース可能な構造化出力を stdout に返す

issue #22 完了条件: 実プロジェクト相当の fixture に対し CLI だけで caller / callee 影響調査が完結する (S1/S2)。出力形式が機械的にパース可能である (S3)。E2E が CLI 出力レベルで期待値と照合される。

## テスト観点

spec `## テスト / 評価方針` の E2E 節 (抜粋): #24 harness の再利用、S1/S2 の golden 完全一致、S3 の golden + `json.Unmarshal`、exit code 3 区分 (成功 0 / Analyzer fatal 1 / 不正 flag 値・selector 不一致 2)、既存グラフレベル E2E の無変更 (2 層構成の維持)。

## 検証コマンド

- `cd core && go build ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `cd core && go test ./...`
- E2E: `(cd analyzers/java && ./gradlew shadowJar) && ./analyzers/java/gradlew --no-daemon -p testdata/fixtures/java/spring-project clean writeDepwalkClasspath && (cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -count=1)` (要 JDK 25)

## 完了条件

- [ ] ステップ 0 でブランチを準備した
- [ ] S1/S2/S3 と exit code 3 区分の CLI プロセス E2E を golden 込みで追加した
- [ ] 既存グラフレベル E2E が無変更でパスする (2 層構成維持)
- [ ] SLO を計測し feature doc `java-analyzer` の性能方針節へ記録した
- [ ] spec の `## 上位資料からの変更点` に SLO 反映を記録した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` (E2E 含む) がすべてパスする
- [ ] 未解決の仕様質問が残っていない
