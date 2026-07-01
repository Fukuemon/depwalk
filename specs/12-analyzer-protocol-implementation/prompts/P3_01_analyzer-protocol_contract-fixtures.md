# Protocol contract fixtures

## 絶対ルール

- spec に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない。
- `testdata/analyzer-protocol` の record type fixture と scenario fixture、および Core 側 contract test だけを実装する。
- DTO / validation / parser は P1 / P2 の成果を前提にし、この prompt で再設計しない。
- Analyzer process runner、Java Analyzer、Graph、Traversal、Output、CLI `depwalk analyze` は実装しない。
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
3. P1 / P2 prompt の完了状態を確認する。
4. 検証: 作業開始前の branch と差分を記録する。

### ステップ 1: record type fixture を追加する

1. `testdata/analyzer-protocol` に record type ごとの fixture を追加する。
2. valid `analysisRequest`、`methodSymbol`、`callEdge`、`diagnostic`、`error` を含める。
3. invalid fixture として、不正 JSONL、duplicate key、invalid UTF-8、必須 field 欠落、型不一致、field 名大小文字違い、未対応 `schemaVersion` を含める。
4. fixture の file naming は、record type と valid / invalid が分かる形にする。
5. 検証: `cd core && go test ./...` を実行する。
6. diff レビューを行い、scenario fixture と混同していないことを確認する。

### ステップ 2: scenario fixture を追加する

1. `testdata/analyzer-protocol` に scenario ごとの input-output fixture を追加する。
2. scenario fixture は request、stdout JSONL、stderr、exit code の組み合わせを表現する。
3. success、diagnostic-only、error-record、non-zero-exit、invalid-stdout の最小 scenario を用意する。
4. timeout、stderr 上限、record size 上限の fixture は追加しない。
5. 検証: `cd core && go test ./...` を実行する。
6. diff レビューを行い、Analyzer implementation や Java fixture に踏み込んでいないことを確認する。

### ステップ 3: Core 側 contract test を追加する

1. `core/internal/protocol` の parser / validation test で record type fixture を読む。
2. `core/internal/analyzer` の runner test はこの prompt では実装しない。scenario fixture は後続 runner prompt の入力として保存する。
3. fixture から期待される pass / fail が test 名や期待値で分かるようにする。
4. 検証: `cd core && go test ./...`、`cd core && go vet ./...`、`cd core && test -z "$(gofmt -l .)"` を実行する。
5. diff レビューを行い、contract test が Protocol 契約の検証に閉じていることを確認する。

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
  - `testdata/analyzer-protocol`
  - `core/internal/protocol`
- 参照する path:
  - `testdata/analyzer-protocol`
  - `core/internal/protocol`
- 参照しない path:
  - `analyzers/java`
  - `core/internal/graph`
  - `core/internal/traversal`
  - `core/internal/output`

## 前提条件

- 完了しているべき phase / 依存 prompt:
  - `P1_01_analyzer-protocol_protocol-model-validation.md`
  - `P2_01_analyzer-protocol_strict-jsonl-parser.md`
- 完了後に着手可能になる後続 prompt:
  - `P4_01_core_analyzer-process-runner.md`
- 必要な repo 状態: P1 / P2 の DTO / validation / parser が実装済み。

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める。
- 推測で実装を進めない。
- 質問するときは、止まっている作業単位、判断が必要な論点、選択肢を整理する。
- fixture naming、expected result file の形式、invalid UTF-8 fixture の表現方法で判断不能になった場合は、P3 の完了条件を満たす最小案を提示して確認する。

## タスク境界

### 実装する範囲

- `testdata/analyzer-protocol` の record type fixture。
- `testdata/analyzer-protocol` の scenario input-output fixture。
- Core 側 Protocol contract test。

### 実装しない範囲

- DTO / validation / parser の再設計。
- Analyzer process runner の実装。
- Java Analyzer、Graph、Traversal、Output、CLI interface。
- timeout、stderr 上限、record size 上限。

## 設計仕様

- contract fixture は record type fixture と scenario fixture の両方を持つ。
- record type fixture は `core/internal/protocol` の parse / validation / strict JSONL test に使う。
- scenario fixture は `core/internal/analyzer` の process runner test に使う。
- valid response record は `methodSymbol` / `callEdge` / `diagnostic` / `error`。
- unknown field は対応済み major version では受け入れる。
- duplicate key、invalid UTF-8、Protocol field 名大小文字違い、未対応 major `schemaVersion` は invalid fixture とする。
- `diagnostic` だけを理由に fatal failure としない。
- `error` record と Analyzer 非ゼロ exit は fatal failure とする。

## テスト観点

- record type ごとの fixture で valid / invalid record を個別に検証できること。
- scenario fixture で request、stdout JSONL、stderr、exit code の組み合わせを検証できること。
- valid `analysisRequest` を Analyzer が受け取れることを fixture で表現できること。
- valid `methodSymbol` / `callEdge` / embedded `SourceLocation` を Core が parse / validate できること。
- valid `diagnostic` record を Core が利用者へ伝播し、fatal failure としないことを fixture で表現できること。
- valid `error` record を Core が fatal failure として扱うことを fixture で表現できること。
- 未解決 symbol が `diagnostic` として表現され、未解決 callee を参照する `callEdge` が valid edge として扱われないことを fixture で表現できること。

## 検証コマンド

- `cd core && go mod tidy`
- `cd core && go test ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `git diff --check`

## 完了条件

- [ ] ステップ 0 で branch と差分状態を確認した。
- [ ] `testdata/analyzer-protocol` に record type fixture がある。
- [ ] `testdata/analyzer-protocol` に scenario input-output fixture がある。
- [ ] Core 側 Protocol contract test が fixture を読んでいる。
- [ ] valid / invalid record を個別に検証できる。
- [ ] request、stdout JSONL、stderr、exit code の scenario を表現できる。
- [ ] Analyzer process runner、Java Analyzer、Graph、Traversal、Output、CLI interface を実装していない。
- [ ] `cd core && go mod tidy` 後に意図しない差分がない。
- [ ] `cd core && go test ./...` がパスする。
- [ ] `cd core && go vet ./...` がパスする。
- [ ] `cd core && test -z "$(gofmt -l .)"` がパスする。
- [ ] `git diff --check` がパスする。
- [ ] 各ステップで diff レビューを実施し、指摘を対応した。
- [ ] 未解決の仕様質問が残っていない。
