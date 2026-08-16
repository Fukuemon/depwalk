---
phase: 7
seq: 1
target: core
issue: 82
depends_on: []
---

# analyzer OOM の検知と対処付きエラー報告の実装

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- **完了条件のタスク化**: 作業開始前に「完了条件」セクションの各項目を todo として登録し、各ステップ完了時に状態を更新すること。タスク化せずに作業を開始することは禁止

### 実装アンチパターンの回避 (必守)

- スコープ厳守: spec / 本 prompt に明記された機能のみ実装する。未要求の機能追加・先回りの抽象化・無関係なリファクタ・暗黙の互換維持をしない。
- 既存規約への整合: 命名・エラー処理・ログ・テスト・API 連携方式は、対象コードベースの既存パターンに合わせる。新方式を持ち込む場合は理由を述べて確認を取る。
- 観測可能な契約の保持: UI 文言・イベント名・戻り値・エラーメッセージ・ログ形式・API を要求なく変更しない。変更が必要なら理由と影響を明記する。
- 推測の排除: 要件・業務ルール・API 仕様が不明なら停止して確認する。それらしいが誤った実装 (存在しない API 呼び出し / 非互換な引数) を避け、import と API の実在を確認する。
- fallback の最小化: `??` / `||` / 既定引数 / 多段 fallback / 暗黙のエラー握り潰しは「任意データ」に限定する。必須データの欠落は隠さず明示的に失敗させる。
- 過剰実装の排除: 単純な条件分岐を strategy / handler map に置換しない。要求も計測もない caching / memoization を入れない。
- dead code を残さない: 到達不能コード・未使用の変数 / 関数 / import / export・変更後に不要化した型定義を削除する。
- 判断の記録: 非自明な設計判断は理由 (or spec / ADR へのリンク) を残す。コード内コメントは英語で書き、spec / issue を引用せず ADR 等へのリンクのみ許可。

## 作業ステップ (この順序で実行する)

### ステップ 0: ブランチ準備と着手記録

ブランチ命名と base branch は `context/project.yml` の `naming.branch` (`feature/<issue-id>`) / `naming.base_branch` (`develop`) に従う。

1. issue #82 の `status:*` が `status:implementing` でなければ付け替え、状態遷移コメントを残す (既に implementing なら何もしない)
2. `feature/82` を最新化して使う (無ければ `develop` から作成)
3. Draft PR が無ければ作成して push する

### ステップ 1: OOM パターン検知と対処付きエラー

1. unit テストを先に書く: analyzer process が valid `error` record なしの非ゼロ exit + stderr に `OutOfMemoryError` を含んで終了したとき、対処 (`--analyzer-cmd` の `-Xmx` 増加) を含むエラーが返ること。OOM を含まない異常終了は従来のエラーのままであること
2. Analyzer process 境界 (`core/internal/analyzer`) の異常終了処理に stderr の OOM パターン照合を追加し、検知時は対処文を含む種別付きエラーを返す。CLI 層は既存のエラー表示経路 (stderr / exit 1) でそのまま表示する
3. stderr の照合は「valid `error` record なしの異常終了時」に限定する。解析結果の解釈・graph 構築には一切使わない (analyzer-protocol feature doc の契約)
4. `## 検証コマンド` をすべて実行する
5. diff レビューを回し、指摘を対応してから次へ

### ステップ 2: 運用文書の同期

1. `context/toolchain.md`「実環境解析の運用指針」の heap 項が正式手順として成立していることを確認する (エラーメッセージの文言と文書の対処が一致すること)
2. `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md`「異常終了時の stderr の扱い」/ `design/features/cli/DesignDoc_cli.md` の該当箇所から「未実装、実装は #82 で進行中」注記を削除する

### ステップ最終: 最終確認

1. 全テスト / lint がパスすることを確認
2. spec の `## 上位資料からの変更点` に追記が必要な差分が出ていないか確認し、あれば記録する
3. commit する (規約: `workflow-git` の commit-format、AI attribution 禁止)

## 実装コンテキスト

- spec: `specs/82-implicit-call-resolution/index.md` (解決済みの論点 D10、実装分割 P7)
- 設計正本: `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md`「異常終了時の stderr の扱い」/ `design/features/cli/DesignDoc_cli.md`「exit code 体系」
- 参照する path:
  - `core/internal/analyzer/` (Analyzer process 境界。異常終了処理の組み込み先)
  - `core/internal/cli/` (エラー表示。`renderAnalyzerFailure` 系の既存様式)
  - `core/e2e/` (異常終了系の既存テスト様式があれば踏襲)

## 前提条件

- 完了しているべき phase / 依存 prompt: なし (他 prompt と並列可。変更は core 側のみで P1〜P6 と重ならない)
- 完了後に着手可能になる後続 prompt: なし
- 必要な repo 状態: `feature/82` が `go build ./...` を通る

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない。異常終了処理の既存構造が prompt の想定と異なる場合は停止して確認する
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- valid `error` record なしの異常終了時の stderr OOM パターン検知
- 対処 (`-Xmx` 増加) を含む種別付きエラーの返却と表示
- unit テスト、運用文書の未実装注記の削除

### 実装しない範囲

- analyzer 側での OOM 捕捉・error record 化 (ADR-0012 で却下済み)
- OOM 以外の stderr パターン検知の追加 (スコープ外)
- exit code 体系・Protocol の変更 (exit 1 の区分内で扱う)
- stderr を protocol record として parse する変更 (契約違反)

## 設計仕様

- 検知条件: analyzer process が valid `error` record を出力せずに非ゼロ exit し、stderr に `OutOfMemoryError` パターンが含まれる
- 動作: 「analyzer の heap 不足。`--analyzer-cmd` の java 起動に `-Xmx` を追加・増加する」という対処を含むエラーを stderr へ表示し exit 1 (既存の実行時エラー区分)
- stderr の照合は終了後のエラー表示の補助に限定し、解析結果の解釈・graph 構築には使わない
- 対処文言は `context/toolchain.md` の運用指針と一致させる

## テスト観点

- WHEN analyzer が error record なし・stderr に OutOfMemoryError で異常終了したとき、対処付きエラーが表示され exit 1 になる
- WHEN OOM 以外の異常終了のとき、従来のエラー表示が変わらない (非回帰)
- WHEN valid `error` record を出力して異常終了したとき、既存の構造化表示 (`renderAnalyzerFailure`) が優先される
- 再現条件 → 修正後の期待: raw stack trace のみで終わっていた OOM が、対処付きエラーとして案内される

## 検証コマンド

- `cd core && go build ./...` / `cd core && go vet ./...` / `cd core && go test ./...`
- `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 でブランチと Draft PR を準備した (status 遷移済みを確認)
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] unit テストが「テスト観点」を網羅している
- [ ] 運用文書の未実装注記を削除し、エラー文言と文書の対処が一致している
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (無ければ「なし」を確認した)
- [ ] 未解決の仕様質問が残っていない
