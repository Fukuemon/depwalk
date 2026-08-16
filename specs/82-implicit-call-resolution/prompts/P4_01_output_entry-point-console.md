---
phase: 4
seq: 1
target: output
issue: 82
depends_on: [P1_01_java-analyzer_entry-point-classification.md]
---

# Console tree への entry point 標識表示の実装

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
2. `feature/82` を最新化して使う (P1 の `entryPoint` metadata 出力が取り込み済みであること)
3. Draft PR が無ければ作成して push する

### ステップ 1: Console formatter への entry point 標識追加

1. 既存 console テストの golden file 様式に合わせ、標識表示の unit テストを先に書く (key あり / なし / 複数 FQN / `(cycle)`・`(既出)` 標識との共存)
2. `console.go` の行構築 (`formatNode` / `formatMarker` 系) に `entryPoint` 標識を追加する。解釈は `NodeView.Metadata` の `entryPoint` key のみ (string 配列。FQN → simple 名の表示変換以外に解釈しない)
3. `## 検証コマンド` を実行する
4. diff レビューを回し、指摘を対応してから次へ

### ステップ 2: CLI E2E の golden 更新

1. entry point fixture (P1 で追加済み) を通す CLI E2E で、console 出力の golden に標識行が現れる期待を追加する
2. json 出力の golden が `nodes[].metadata.entryPoint` を透過表出していることを確認する (実装変更は不要のはず。変わる場合は停止して確認)
3. `## 検証コマンド` をすべて実行する
4. diff レビューを回し、指摘を対応してから次へ

### ステップ最終: 最終確認

1. 全テスト / lint がパスすることを確認
2. spec の `## 上位資料からの変更点` に追記が必要な差分が出ていないか確認し、あれば記録する
3. commit する (規約: `workflow-git` の commit-format、AI attribution 禁止)

## 実装コンテキスト

- spec: `specs/82-implicit-call-resolution/index.md` (解決済みの論点 D6、実装分割 P4)
- 設計正本: `design/features/output/DesignDoc_output.md` の「行の書式」(entry point 標識) と「JSON 出力」の metadata 節
- 参照する path:
  - `core/internal/output/console.go` (行の書式の実装)
  - `core/internal/output/view.go` / `core/internal/output/types.go` (NodeView.Metadata)
  - `core/internal/output/json.go` (透過表出の確認のみ。変更しない)
  - `core/e2e/` (CLI E2E golden)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P1_01_java-analyzer_entry-point-classification.md` (E2E で実データの `entryPoint` を使うため)
- 完了後に着手可能になる後続 prompt: なし
- 必要な repo 状態: P1 の変更が `feature/82` に取り込み済み

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- Console の node 行末への entry point 標識表示
- console unit テスト (golden) と CLI E2E の期待追加

### 実装しない範囲

- `entryPoint` 以外の metadata key の Console 表示 (dispatch / viaLambda 等は Future Work)
- JSON 出力の変更 (既存透過のまま)
- traversal / graph / analyzer 側の変更

## 設計仕様

- node の `Metadata` に `entryPoint` key (string 配列。値は検出アノテーションの FQN) が存在するとき、行末に `  (entry point: <simple 名をカンマ区切り>)` を付す。例: `com.example.Batch#run()  [Batch.java:10]  (entry point: @Scheduled)`
- Output が意味解釈する metadata key は `entryPoint` のみ。配列の中身は FQN → simple 名 (`@` + 最終 segment) の表示変換以外に解釈しない
- key が無い node には何も出さない。既存の `(cycle)` / `(既出)` 標識・位置表記との並び順は既存書式を崩さない (標識は既存 marker の後ろ)
- View 境界の全数対応 (Formatter は View field 以外から情報を得ない) を維持する

## テスト観点

- WHEN `entryPoint` を持つ node を console 出力したとき、行末に標識が付く (単一 / 複数 FQN)
- key が無い node の出力が従来と byte 一致する (非回帰)
- `(cycle)` / `(既出)` と標識が共存するときの並び順が仕様どおり
- CLI E2E: console golden に標識、json golden に `nodes[].metadata.entryPoint` が現れる

## 検証コマンド

- `cd core && go build ./...` / `cd core && go vet ./...` / `cd core && go test ./...`
- `cd analyzers/java && ./gradlew shadowJar` (E2E が analyzer jar を使う場合の事前 build)
- `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 でブランチと Draft PR を準備した (status 遷移済みを確認)
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] console unit (golden) と CLI E2E が「テスト観点」を網羅している
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (無ければ「なし」を確認した)
- [ ] 未解決の仕様質問が残っていない
