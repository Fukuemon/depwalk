# P3_01: analyze use case の探索クエリ orchestration (D1/D6/D7/D12)

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

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` に従う。issue #22 の作業ブランチ (`feature/22`) を継続利用し、P1_01 / P2_01 の commit を含む状態から開始する。

### ステップ 1: AnalysisRequest の拡張 (fullGraph 明示・include/exclude 透過)

1. テストを先に書く (TDD): `core/internal/analyze/analyze_test.go` に、(a) 組み立てられた `AnalysisRequest` の `AnalysisMode` が常に `fullGraph` で明示設定されること (protocol の暗黙既定に依存しない)、(b) `Entrypoints` が空のままなこと、(c) `Options.Include` / `Options.Exclude` が指定順のまま request の `Include` / `Exclude` へ透過されること・未指定時に request へ載らないこと、を追加する。fake analyzer で request 内容を検証する既存パターン (`--source-root` の透過テストと同型) を使う。
2. 実装する: `analyze.Options` に `Include []string` / `Exclude []string` を追加し (既存 `SourceRoots` と同型)、`Run` の request 組み立てで `AnalysisModeFullGraph` を明示設定、include/exclude を透過する。検証は既存の `request.Validate()` に委ねる (CLI 層は glob を解釈しない)。
3. `## 検証コマンド` を実行し、diff レビューを回す。

### ステップ 2: method selector 照合と traversal / output の orchestration

1. テストを先に書く: (a) selector の完全 signature 指定で一致 1 件、(b) 括弧省略で 1 件一致、(c) 複数一致 (オーバーロード) で候補の完全 signature 一覧を含む種別付きエラー、(d) 一致 0 件で種別付きエラー、(e) 照合が `Node.Symbol` の `QualifiedName`/`Signature` 走査であり methodId 文字列形式に依存しないこと、(f) 1 件一致時に `traversal.Traverse` → `output.Write` まで到達し指定 format で出力されること、(g) `--method` 相当が未指定なら従来のサマリ動作のままなこと。
2. 実装する: `analyze.Options` に探索クエリ (method selector / direction / max-depth / format / 出力先 writer) を追加し、`Run` (または use case 内の後続処理) で graph 構築後に selector 照合 → `traversal.Request{StartID, Direction, MaxDepth}` で `Traverse` → `output.Write(w, format, output.Input{Graph, Result, Request})` を orchestrate する。`Order` は指定せず既定 (`OrderBFS`) のまま。照合の曖昧 (複数一致)・不一致 (0 件) は候補一覧を含む **種別判定可能なエラー型** で返す (呼び出し側 = CLI 層が `errors.As` で判別できる形。既存 `AnalyzerFailure` と同型のパターン)。selector 書式のパース: `<型の binary name>#<メソッド名>[(<引数型リスト>)]`、`#` 区切り、括弧があれば signature 完全指定として扱う。
3. `## 検証コマンド` を実行し、diff レビューを回す。

### ステップ最終: 最終確認

1. 全テスト / vet / gofmt がパスすることを確認する。
2. 公開エラー型・Options の追加が spec の設計 (D1/D6/D7/D12・責務配置) と一致していることを見直す。

## 実装コンテキスト

- spec: `specs/22-cli-interface/index.md` (D1 / D6 / D7 / D12、Interface 設計の変換手順 1-5、Content / Data 設計の責務配置)
- durable 正本: `design/features/cli/DesignDoc_cli.md` (責務配置・selector 書式)
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `core/internal/analyze/analyze.go` / `analyze_test.go`
  - `core/internal/traversal/` (既存 API `Traverse` / `Request` を利用のみ、変更しない)
  - `core/internal/output/` (既存 API `Write` / P2_01 の `RegisteredFormats()` を利用のみ、変更しない)

## 前提条件

- 依存 prompt: `P1_01_core_graph-edge-metadata.md`、`P2_01_output_metadata-registered-formats.md` (完了済みであること)
- 完了後に着手可能になる後続 prompt: `P4_01_core_cli-flags-exit-codes.md`
- 必要な repo 状態: traversal (#6) / output (#7) が実装済みで、`graph.Edge.Metadata` / `NodeView.Metadata` が存在すること

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- `analyze.Options` の拡張 (Include/Exclude・探索クエリ・出力先 writer)
- `AnalysisMode` の fullGraph 明示設定、`Entrypoints` 空維持
- method selector のパース・graph node 走査照合・種別付きエラー型
- `traversal.Traverse` / `output.Write` の orchestration

### 実装しない範囲

- CLI flag の定義・stderr 表示・exit code 判別 (P4_01。本 prompt は種別判定可能なエラーを返すところまで)
- traversal / output package 自体の変更 (既存 API を利用のみ。公開 API 変更が必要になった場合は停止して spec の論点に戻す)
- E2E (P5_01)

## 設計仕様

spec #22 (抜粋):

- **D1**: method selector は 1 引数の統合書式 `<型の binary name>#<メソッド名>[(<引数型リスト>)]`。括弧付きで signature 完全指定 (例: `com.example.UserService#findById(java.lang.Long)`)、括弧省略時はメソッド名のみ。照合は node が保持する symbol 情報 (`QualifiedName` / `Signature`) の走査で行い、Core は methodId の文字列形式 (`java:` prefix 等) に依存しない。signature 省略時に同名メソッドが複数一致した場合、候補の完全 signature を一覧表示してエラー終了する (自動選択しない)。一致 1 件ならそれを採用する。
- **D6**: 探索方向に関わらず Core は常に fullGraph で解析する。実装位置は analyze use case — AnalysisRequest 組み立て時に AnalysisMode を明示的に fullGraph に設定する (protocol の暗黙既定に依存しない)。
- **D7**: method selector を `AnalysisRequest.Entrypoints` には渡さない (空のまま)。照合は graph 構築後に node 走査で行う。
- **D12**: `--include` / `--exclude` の値を指定順のまま `AnalysisRequest.Include`/`Exclude` へ透過する (`--source-root` → `SourceRoots` と同一パターン)。CLI 層・use case は glob の意味解釈・展開を行わず、値の検証は既存の `request.Validate()` に委ねる。既定値なし (未指定時は request に載せない)。
- **責務配置 (Content / Data 設計)**: graph 構築後の method selector 照合 (D1)・`traversal.Traverse`・`output.Write` の orchestration は use case が担う。照合の曖昧・不一致 (エラーケース 3-4) は候補一覧を含む種別付きエラーで CLI 層へ返し、CLI 層は stderr 表示と exit code 判別のみを担う。
- **変換手順 (Interface 設計)**: (2) 一致 1 件なら `Node.ID` を `traversal.Request.StartID` に使う。(3) `Direction` は `--direction` を `graph.Direction` にマップ、`MaxDepth` は未指定なら nil。`Order` は既定 (`OrderBFS`) のまま CLI に露出しない。(4) `output.Write(w, format, output.Input{Graph, Result, Request})`。
- **後方互換 (D2)**: `--method` 相当が未指定なら現行のサマリ動作 (件数 1 行 + diagnostics) を維持する。

## テスト観点

spec `## テスト / 評価方針` の該当行 (抜粋):

- analyze use case: `AnalysisRequest.AnalysisMode` が常に fullGraph で明示されること、`Entrypoints` が空のままなこと、`--include`/`--exclude` が指定順のまま request へ透過されること・未指定時に request へ載らないこと (echo 系 fake analyzer で request 内容を検証)。
- method selector 照合: 完全 signature 指定の一致、括弧省略で 1 件一致、複数一致で候補の完全 signature 一覧、一致 0 件。照合が qualifiedName/signature 走査で methodId 文字列形式に依存しないこと。

## 検証コマンド

- `cd core && go build ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `cd core && go test ./...`

## 完了条件

- [ ] ステップ 0 でブランチを準備した
- [ ] request 拡張 (fullGraph 明示 / Entrypoints 空 / include・exclude 透過) をテスト先行で実装した
- [ ] selector 照合と orchestration を種別付きエラー込みでテスト先行で実装した
- [ ] `--method` 相当未指定時の後方互換 (サマリ動作) を確認した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] 未解決の仕様質問が残っていない
