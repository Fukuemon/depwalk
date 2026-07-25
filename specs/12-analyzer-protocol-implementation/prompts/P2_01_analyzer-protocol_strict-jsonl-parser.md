# Strict JSONL parser

## 絶対ルール

- spec に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない。
- `core/internal/protocol` の strict JSONL parser だけを実装する。
- record DTO / validation は P1 の成果を前提にし、この prompt で再設計しない。
- contract fixture、Analyzer process runner、Java Analyzer、Graph、Traversal、Output、CLI `depwalk analyze` は実装しない。
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

## 作業ステップ

### ステップ 0: ブランチ準備

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` に従う。Issue は `#12`。

1. 現在の branch が `feature/12` であることを確認する。
2. `git status --short` で意図しない差分がないことを確認する。
3. P1 prompt の完了状態を確認する。
4. 検証: 作業開始前の branch と差分を記録する。

### ステップ 1: 1 行 1 record parser を実装する

1. `core/internal/protocol` に JSONL の 1 行を 1 record として parse する関数を追加する。
2. `recordType` に応じて P1 の DTO / validation へ接続する。
3. Analyzer stdout の streaming 処理から呼び出せる API にする。
4. 空行の扱いが未定義なら停止して確認する。
5. 検証: `cd core && go test ./...` を実行する。
6. diff レビューを行い、process runner や fixture 追加に踏み込んでいないことを確認する。

### ステップ 2: `encoding/json` v1 の permissive 挙動を拒否する

1. duplicate key を invalid record として拒否する。
2. invalid UTF-8 を invalid record として拒否する。
3. Protocol field 名の大小文字違いを別 field として扱い、必須 field 欠落として拒否する。
4. 未知 field は対応済み major version では無視し、既知 field だけで処理を継続する。
5. 未対応 major `schemaVersion` は schema version mismatch として拒否する。
6. 検証: `cd core && go test ./...`、`cd core && go vet ./...` を実行する。
7. diff レビューを行い、`encoding/json/v2` や experimental API を導入していないことを確認する。

### ステップ 3: parser unit test を追加する

1. valid JSONL line を parse / validate できる test を追加する。
2. 不正 JSONL、duplicate key、invalid UTF-8、field 名大小文字違い、未対応 `schemaVersion` の test を追加する。
3. 未知 field を含む対応済み major version の record を受け入れる test を追加する。
4. fixture file はこの prompt では追加しない。test 内の最小入力で検証する。
5. 検証: `cd core && go test ./...`、`cd core && go vet ./...`、`cd core && test -z "$(gofmt -l .)"` を実行する。
6. diff レビューを行い、P2 の責務だけを検証していることを確認する。

### ステップ最終: 最終確認

1. `## 検証コマンド` の全コマンドがパスすることを確認する。
2. `git diff --check` を実行する。
3. `go mod tidy` 後に `core/go.mod` / `core/go.sum` の意図しない差分がないことを確認する。
4. spec の `## 上位資料からの変更点` に追記が必要ないことを確認する。

## 実装コンテキスト

- spec: `specs/12-analyzer-protocol-implementation/index.md`
- review: `specs/12-analyzer-protocol-implementation/review.md`
- Issue: `#12`
- Branch: `feature/12`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 実装対象:
  - `core/internal/protocol`
- 参照する path:
  - `core/internal/protocol`
  - `core/go.mod`
  - `core/go.sum`
- 参照しない path:
  - `core/internal/analyzer`
  - `testdata/analyzer-protocol`
  - `analyzers/java`

## 前提条件

- 完了しているべき phase / 依存 prompt: `P1_01_analyzer-protocol_protocol-model-validation.md`
- 完了後に着手可能になる後続 prompt:
  - `P3_01_analyzer-protocol_contract-fixtures.md`
  - `P4_01_core_analyzer-process-runner.md`
- 必要な repo 状態: P1 の DTO / validation が `core/internal/protocol` に実装済み。

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める。
- 推測で実装を進めない。
- 質問するときは、止まっている作業単位、判断が必要な論点、選択肢を整理する。
- 空行、最大 record size、error 型の粒度で判断不能になった場合は、P2 の完了条件を満たす最小案を提示して確認する。

## タスク境界

### 実装する範囲

- `core/internal/protocol` の strict JSONL parser。
- duplicate key / invalid UTF-8 / field 名大小文字違いの拒否。
- unknown field の許容と unsupported major version の拒否。
- P2 範囲の unit test。

### 実装しない範囲

- DTO / validation の再設計。
- `testdata/analyzer-protocol` の fixture。
- `core/internal/analyzer` の process runner。
- Java Analyzer、Graph、Traversal、Output、CLI interface。
- timeout、stderr 上限、record size 上限。

## 設計仕様

- Analyzer stdout は JSONL で、1 行を 1 record として扱う。
- Core は stdout の各行を逐次 parse / validate する。
- `encoding/json` v1 を使ってよいが、v1 の duplicate key 許容、invalid UTF-8 置換、struct field の case-insensitive matching を Protocol contract として採用しない。
- duplicate key、invalid UTF-8、Protocol field 名の大小文字違い、必須 field 欠落、未対応 major `schemaVersion` は invalid record として拒否する。
- 対応済み major version の未知 field は無視する。
- 不正 JSONL、schema 不準拠、未対応 `schemaVersion` は Analyzer が表現する `error` record ではなく、Core validation error として扱う。

## テスト観点

- Analyzer stdout の JSONL record が逐次 parse / validate されること。
- Analyzer stdout に invalid UTF-8 を含む JSONL record が出た場合、Core が invalid record として拒否すること。
- Analyzer stdout に duplicate key を含む JSON object が出た場合、Core が invalid record として拒否すること。
- Protocol field 名の大小文字違いを別 field として扱い、`schemaVersion` の代わりに `schemaversion` が出た場合は必須 field 欠落として拒否すること。
- 未知 field を含む対応済み major version の record を受け入れられること。
- 未対応 major `schemaVersion` を拒否できること。
- 不正 JSONL、必須 field 欠落、型不一致を拒否できること。

## 検証コマンド

- `cd core && go mod tidy`
- `cd core && go test ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `git diff --check`

## 完了条件

- [ ] ステップ 0 で branch と差分状態を確認した。
- [ ] `core/internal/protocol` に strict JSONL parser がある。
- [ ] parser が P1 の DTO / validation に接続している。
- [ ] duplicate key を拒否できる。
- [ ] invalid UTF-8 を拒否できる。
- [ ] field 名大小文字違いを必須 field 欠落として拒否できる。
- [ ] 未知 field を含む対応済み major version の record を受け入れられる。
- [ ] 未対応 major `schemaVersion` を拒否できる。
- [ ] P2 範囲の unit test がある。
- [ ] fixture、Analyzer process runner、Java Analyzer、Graph、Traversal、Output、CLI interface を実装していない。
- [ ] `cd core && go mod tidy` 後に意図しない差分がない。
- [ ] `cd core && go test ./...` がパスする。
- [ ] `cd core && go vet ./...` がパスする。
- [ ] `cd core && test -z "$(gofmt -l .)"` がパスする。
- [ ] `git diff --check` がパスする。
- [ ] 各ステップで diff レビューを実施し、指摘を対応した。
- [ ] 未解決の仕様質問が残っていない。
