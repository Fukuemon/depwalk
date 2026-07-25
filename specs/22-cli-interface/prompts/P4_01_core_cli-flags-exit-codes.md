# P4_01: CLI flag 追加と exit code 0/1/2 制御 (D2-D5/D8/D12)

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

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` に従う。issue #22 の作業ブランチ (`feature/22`) を継続利用し、P1-P3 の commit を含む状態から開始する。

### ステップ 1: flag 追加と入力 validation

1. テストを先に書く (TDD): `core/internal/cli/analyze_test.go` に、(a) `--method`/`--direction`/`--max-depth`/`--format`/`--include`/`--exclude` の各 flag が受理され use case の Options へ渡ること (include/exclude は指定順、既存の `--source-root` 透過テストと同型)、(b) `--direction`/`--format` の不正値で許容値一覧を含むエラー、(c) `--format` の許容値一覧が `output.RegisteredFormats()` 由来であること (ハードコードなし)、(d) `--max-depth` の負値でエラー、(e) `--method` 未指定時に現行サマリ動作のままなこと、を追加する。
2. 実装する: `core/internal/cli/analyze.go` の `analyzeFlags` / `newAnalyzeCommand` に flag を追加する。既存 flag (`--analyzer-cmd`/`--language`/`--analyzer-meta`/`--source-root`) の定義・`analyzeLongHelp`・`renderAnalyzerFailure` 経路は変更しない。`--include`/`--exclude` は `StringArrayVar` (既存 `--source-root` と同型)。
3. `## 検証コマンド` を実行し、diff レビューを回す。

### ステップ 2: exit code 0/1/2 の判別配線

1. テストを先に書く: (a) 探索成功 (結果あり / 結果空 / cutoff 注釈付き) → exit 0、(b) Analyzer 起動失敗・protocol 違反・Analyzer fatal・出力書き込み失敗 → exit 1 (fatal 時は既存 `renderAnalyzerFailure` の表示が変わらないこと)、(c) flag 値域エラー・selector 曖昧 (候補一覧が stderr に出ること)・selector 不一致・`request.Validate()` の利用者起因エラー (不正な `--source-root`/`--include`/`--exclude`、Analyzer 起動前に拒否) → exit 2、(d) エラー・候補一覧・diagnostics が stderr、探索結果のみ stdout に出ること。
2. 実装する: Cobra の既定 (RunE エラーを常に exit 1) に委ねず、CLI 層でエラー種別を判別して 0/1/2 を返す。P3_01 が導入した種別付きエラー型と既存 `AnalyzerFailure` を `errors.As` で判別する (既存パターンと同型)。`SilenceErrors`/`SilenceUsage` による重複表示抑止は既存挙動を維持する。
3. `## 検証コマンド` を実行し、diff レビューを回す。

### ステップ最終: 最終確認

1. 全テスト / vet / gofmt がパスすることを確認する。
2. エラーケース 1-8 (設計仕様参照) がすべてテストで覆われていることを確認する。

## 実装コンテキスト

- spec: `specs/22-cli-interface/index.md` (D2-D5 / D8 とその拡張 / D12、Error / Fallback 設計のエラーケース表)
- durable 正本: `design/features/cli/DesignDoc_cli.md` (flag 体系・exit code 体系・責務配置)
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `core/internal/cli/analyze.go` / `analyze_test.go`
  - `core/internal/analyze/` (P3_01 の Options / エラー型を利用のみ、変更しない)

## 前提条件

- 依存 prompt: `P3_01_core_analyze-query-orchestration.md` (完了済みであること — 種別付きエラー型と Options 拡張を使うため)
- 完了後に着手可能になる後続 prompt: `P5_01_core_cli-e2e-golden.md`
- 必要な repo 状態: `output.RegisteredFormats()` (P2_01) が存在すること

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- `analyze` コマンドへの 6 flag 追加 (`--method`/`--direction`/`--max-depth`/`--format`/`--include`/`--exclude`) と usage 文字列
- flag 値の入力 validation とエラー表示 (stderr、許容値一覧)
- exit code 0/1/2 の判別と `os.Exit` 制御

### 実装しない範囲

- use case 内の照合・orchestration ロジックの変更 (P3_01 の責務)
- 既存 flag・`analyzeLongHelp`・`renderAnalyzerFailure` の変更
- E2E (P5_01)
- サブコマンドの新設 (D2 で flag 追加方式を確定済み)

## 設計仕様

spec #22 (抜粋):

- **flag 体系 (D2-D5/D12)**: `--method` string (未指定なら現行のサマリ動作)。`--direction` string、値は `caller`/`callee`、既定 `caller`、不正値は許容値一覧を添えてエラー。`--max-depth` 非負整数、既定は無制限 (未指定時は traversal の `MaxDepth` に nil)、0 は起点のみ、負値はエラー。`--format` string、既定 `console`、値域は `output.RegisteredFormats()` のみ、未登録値は登録済み一覧を添えてエラー。`--include`/`--exclude` は repeatable な StringArray、既定なし (未指定時は request に載せない)。既存 flag は変更しない。
- **D8 (exit code)**: exit 0 = 探索成功 (結果空・cutoff 注釈付きも成功、結果は stdout)。exit 1 = 実行時エラー (Analyzer 起動失敗、protocol 違反、出力書き込み失敗)。exit 2 = 入力エラー (flag 値域外、selector のオーバーロード曖昧 (候補一覧を stderr へ)、対象メソッド不在、`--source-root`/`--include`/`--exclude` の不正 path/glob による `request.Validate()` エラー)。エラーメッセージ・候補一覧・diagnostics は stderr、探索結果のみ stdout。
- **D8 拡張 (#24 経路との整合)**: Analyzer fatal は #24 実装の `renderAnalyzerFailure` (summary → details 順で stderr、`SilenceErrors`/`SilenceUsage` で重複抑止) をそのまま維持し exit 1。`request.Validate()` エラーの exit 2 判別は、CLI 層が `analyze.Run` の返すエラーを `errors.As`/sentinel で種別判定する既存パターン (`AnalyzerFailure` 判定と同型) に揃える。
- **エラーケース表 (Error / Fallback 設計)**: 1 `--direction`/`--format` 不正値 → exit 2。2 `--max-depth` 負値 → exit 2。3 オーバーロード曖昧 (候補一覧) → exit 2。4 selector 不一致 → exit 2。5 Analyzer 起動失敗・protocol 違反 (fatal は構造化表示) → exit 1。6 出力書き込み失敗 → exit 1。7 結果空 / cutoff → exit 0。8 不正 path/glob (`request.Validate()`) → exit 2。

受け入れ基準 (spec `## 要件の解釈` より抜粋):

- [S1] WHEN 開発者が CLI で対象メソッド (method selector) と caller 方向・深さ上限を指定して実行したとき、システムは到達した caller 集合を指定した出力形式で出力する
- [S2] WHEN 開発者が CLI で対象メソッドと callee 方向・深さ上限を指定して実行したとき、システムは到達した callee 集合を指定した出力形式で出力する

## テスト観点

spec `## テスト / 評価方針` の該当行 (抜粋):

- CLI flag パース: `--direction`/`--format` の不正値で許容値一覧付きエラー + exit 2、`--max-depth` の負値エラー + exit 2、`--format` の値域が `output.RegisteredFormats()` 由来であること。
- exit code 判別: 0/1/2 の振り分け全経路。invalid な `--source-root`/`--include`/`--exclude` は Analyzer 起動前に拒否 (既存 `TestAnalyzeCommandRejectsInvalidSourceRootBeforeAnalyzerLaunch` パターン踏襲)。`AnalyzerFailure` 時は既存 `renderAnalyzerFailure` の表示を変えない。

## 検証コマンド

- `cd core && go build ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `cd core && go test ./...`

## 完了条件

- [ ] ステップ 0 でブランチを準備した
- [ ] 6 flag の追加と validation をテスト先行で実装した
- [ ] exit code 0/1/2 の判別をテスト先行で実装した (エラーケース 1-8 を網羅)
- [ ] 既存 flag・`renderAnalyzerFailure` 経路が無変更であることを確認した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] 未解決の仕様質問が残っていない
