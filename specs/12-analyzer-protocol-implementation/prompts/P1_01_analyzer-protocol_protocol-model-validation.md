# Protocol model + validation

## 絶対ルール

- spec に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない。
- `core/internal/protocol` の DTO と record 単位 validation だけを実装する。
- strict JSONL parser、duplicate key / invalid UTF-8 / field 名大小文字違いの検出、fixture 追加、Analyzer process runner は実装しない。
- Java Analyzer、Graph、Traversal、Output、CLI `depwalk analyze` は実装しない。
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
3. PR が未作成の場合は、PR テンプレートを確認し、完了条件を description に転記する。
4. 検証: 作業開始前の branch と差分を記録する。

### ステップ 1: Protocol DTO を定義する

1. `core/internal/protocol` に record DTO と embedded object を追加する。
2. `analysisRequest`、method selector、`methodSymbol`、`callEdge`、`diagnostic`、`error`、`SourceLocation` を表現する。
3. `schemaVersion` は protocol 全体 version とし、Phase1 は `"1"` を扱う。
4. `recordType` は `analysisRequest` / `methodSymbol` / `callEdge` / `diagnostic` / `error` を扱う。
5. `metadata` は任意 object として保持できるが、validation の必須条件にしない。
6. 検証: `cd core && go test ./...` を実行する。
7. diff レビューを行い、DTO 以外の責務が混入していないことを確認する。

### ステップ 2: record 単位 validation を実装する

1. 各 DTO に必須 field、enum、相対 path、line / column の validation を追加する。
2. 対応済み `schemaVersion` は `"1"` のみとし、未対応 major version は validation error にする。
3. `include` / `exclude` は `workspaceRoot` からの相対 path glob 配列として扱い、絶対 path、空文字、`..` を含む path を拒否する。
4. `entrypoints[].qualifiedName` は必須、`signature` は任意にする。
5. `analysisMode` は未指定時に `fullGraph` として扱えるようにする。ただし CLI interface は実装しない。
6. `diagnostic.severity` は `info` / `warning` / `partialFailure` のみ許可する。
7. `SourceLocation.path` は相対 path、`startLine` は 1-based 必須、column / end は任意にする。
8. 検証: `cd core && go test ./...`、`cd core && go vet ./...` を実行する。
9. diff レビューを行い、parser や fixture 追加に踏み込んでいないことを確認する。

### ステップ 3: Unit test を追加する

1. `core/internal/protocol` に DTO / validation の unit test を追加する。
2. valid `analysisRequest`、`methodSymbol`、`callEdge`、`diagnostic`、`error`、`SourceLocation` を検証する。
3. 必須 field 欠落、未対応 `schemaVersion`、invalid enum、invalid path、invalid line を検証する。
4. 未知 field、duplicate key、invalid UTF-8、field 名大小文字違いはこの prompt では検証しない。
5. 検証: `cd core && go test ./...`、`cd core && go vet ./...`、`cd core && test -z "$(gofmt -l .)"` を実行する。
6. diff レビューを行い、test が P1 の責務だけを検証していることを確認する。

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
  - `core/internal/protocol/protocol.go`
  - `core/go.mod`
  - `core/go.sum`
- 参照しない path:
  - `core/internal/analyzer`
  - `testdata/analyzer-protocol`
  - `analyzers/java`

## 前提条件

- 完了しているべき phase / 依存 prompt: なし。#11 の Core scaffold と #12 spec-review PASS は完了済み。
- 完了後に着手可能になる後続 prompt:
  - `P2_01_analyzer-protocol_strict-jsonl-parser.md`
  - `P3_01_analyzer-protocol_contract-fixtures.md`
  - `P4_01_core_analyzer-process-runner.md`
- 必要な repo 状態: `core/internal/protocol` は stub package の状態、または本 prompt の範囲内で整合可能な状態。

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める。
- 推測で実装を進めない。
- 質問するときは、止まっている作業単位、判断が必要な論点、選択肢を整理する。
- Go の public API 名、validation error 型、metadata の具体型で判断不能になった場合は、P1 の完了条件を満たす最小案を提示して確認する。

## タスク境界

### 実装する範囲

- `core/internal/protocol` の record DTO / wire model。
- `analysisRequest` / method selector / `methodSymbol` / `callEdge` / `diagnostic` / `error` / `SourceLocation` の validation。
- P1 範囲の unit test。

### 実装しない範囲

- strict JSONL parser。
- duplicate key / invalid UTF-8 / field 名大小文字違いの検出。
- `testdata/analyzer-protocol` の fixture。
- `core/internal/analyzer` の process runner。
- Java Analyzer、Graph、Traversal、Output、CLI interface。
- timeout、stderr 上限、record size 上限。

## 設計仕様

- 全 record は `schemaVersion` と `recordType` を必須 field に持つ。
- Phase1 の `schemaVersion` は `"1"`。
- `analysisRequest` 必須 field: `schemaVersion`, `recordType`, `requestId`, `workspaceRoot`, `language`。
- `analysisRequest` 任意 field: `include`, `exclude`, `entrypoints`, `analysisMode`, `metadata`。
- `include` / `exclude` は `workspaceRoot` からの相対 path glob 配列。絶対 path、空文字、`..` を含む path は schema 不準拠。
- `entrypoints[]` は method selector object。`qualifiedName` 必須、`signature` 任意。
- `entrypoints` 未指定または空配列は scope 全体の call graph 生成要求。
- `analysisMode` は `fullGraph` または `reachableFromEntrypoints`。未指定時は `fullGraph`。
- `methodSymbol` 必須 field: `schemaVersion`, `recordType`, `methodId`, `language`, `symbolKind`, `qualifiedName`, `signature`。
- `methodSymbol` 任意 field: `sourceLocation`, `metadata`。
- `callEdge` 必須 field: `schemaVersion`, `recordType`, `edgeId`, `callerMethodId`, `calleeMethodId`。
- `callEdge` 任意 field: `callSite`, `metadata`。
- `SourceLocation` は独立 record ではなく embedded value object。
- `SourceLocation` 必須 field: `path`, `startLine`。
- `SourceLocation` 任意 field: `startColumn`, `endLine`, `endColumn`。
- `diagnostic` 必須 field: `schemaVersion`, `recordType`, `severity`, `code`, `message`。
- `diagnostic.severity` は `info` / `warning` / `partialFailure`。
- `error` 必須 field: `schemaVersion`, `recordType`, `code`, `message`。
- Core validation error と Analyzer が出力する `error` record は別概念として扱う。

## テスト観点

- valid `analysisRequest` を marshal / validate できること。
- valid `methodSymbol` / `callEdge` / embedded `SourceLocation` を validate できること。
- valid `diagnostic` を fatal failure とせず扱えること。
- valid `error` を Analyzer fatal failure として表現できること。
- 未対応 major `schemaVersion` を拒否できること。
- 必須 field 欠落、型不一致、invalid enum、invalid path、invalid line を拒否できること。
- `include` / `exclude` の絶対 path、空文字、`..` を拒否できること。
- `entrypoints.qualifiedName` 必須、`signature` 任意を検証できること。

## 検証コマンド

- `cd core && go mod tidy`
- `cd core && go test ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `git diff --check`

## 完了条件

- [ ] ステップ 0 で branch と差分状態を確認した。
- [ ] `core/internal/protocol` に Protocol DTO / wire model がある。
- [ ] `analysisRequest` / method selector / `methodSymbol` / `callEdge` / `diagnostic` / `error` / `SourceLocation` の validation がある。
- [ ] P1 範囲の unit test がある。
- [ ] strict JSONL parser、fixture、Analyzer process runner を実装していない。
- [ ] Java Analyzer、Graph、Traversal、Output、CLI interface を実装していない。
- [ ] `cd core && go mod tidy` 後に意図しない差分がない。
- [ ] `cd core && go test ./...` がパスする。
- [ ] `cd core && go vet ./...` がパスする。
- [ ] `cd core && test -z "$(gofmt -l .)"` がパスする。
- [ ] `git diff --check` がパスする。
- [ ] 各ステップで diff レビューを実施し、指摘を対応した。
- [ ] 未解決の仕様質問が残っていない。
