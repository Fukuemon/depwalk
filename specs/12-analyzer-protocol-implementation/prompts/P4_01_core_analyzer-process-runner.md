# Analyzer process runner

## 絶対ルール

- spec に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない。
- `core/internal/analyzer` の `os/exec` による最小 process runner だけを実装する。
- Protocol DTO / parser / fixture は P1 / P2 / P3_01 の成果を前提にし、この prompt で再設計しない。
- Java Analyzer、Graph、Traversal、Output、CLI `depwalk analyze` は実装しない。
- timeout、stderr 上限、record size 上限、parallel execution、session reuse、capability handshake は実装しない。
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
3. P1 / P2 / P3_01 prompt の完了状態を確認する。
4. 検証: 作業開始前の branch と差分を記録する。

### ステップ 1: 最小 runner interface を定義する

1. `core/internal/analyzer` に Analyzer process runner の最小 API を追加する。
2. runner は 1 request ごとに Analyzer process を 1 つ起動する。
3. request は Protocol 側の `analysisRequest` DTO / marshal 済み JSONL を受け取れる形にする。
4. runner result は parsed records、diagnostics、Analyzer error record、exit code、stderr を区別できる形にする。
5. timeout、stderr 上限、record size 上限は API に固定値として入れない。
6. 検証: `cd core && go test ./...` を実行する。
7. diff レビューを行い、CLI interface や Java Analyzer 起動設定に踏み込んでいないことを確認する。

### ステップ 2: `os/exec` runner を実装する

1. `os/exec` で Analyzer process を起動する。
2. stdin に `analysisRequest` JSONL を 1 件送信し、その後 stdin を close する。
3. stdout を Protocol parser へ streaming で渡す。
4. stderr は protocol record として parse しない。
5. exit code `0` は成功、非ゼロは fatal failure として result に含める。
6. Analyzer が `error` record を出力した場合、Analyzer 側 fatal failure として扱う。
7. 検証: `cd core && go test ./...`、`cd core && go vet ./...` を実行する。
8. diff レビューを行い、timeout / stderr 上限 / record size 上限を実装していないことを確認する。

### ステップ 3: scenario fixture を使った test を追加する

1. `testdata/analyzer-protocol` の scenario fixture を runner test で使う。
2. success、diagnostic-only、error-record、non-zero-exit、invalid-stdout を検証する。
3. test 用 fake Analyzer process が必要な場合は、test scope に閉じる。
4. Java Analyzer 実装や runtime には依存しない。
5. 検証: `cd core && go test ./...`、`cd core && go vet ./...`、`cd core && test -z "$(gofmt -l .)"` を実行する。
6. diff レビューを行い、process SPI の最小契約だけを検証していることを確認する。

### ステップ最終: 最終確認

1. `## 検証コマンド` の全コマンドがパスすることを確認する。
2. `git diff --check` を実行する。
3. `cd core && go list -deps ./...` と `cd core && go list -f '{{.ImportPath}} {{.Imports}}' ./...` を実行し、Analyzer runtime / implementation への直接 import がないことを確認する。
4. spec の `## 上位資料からの変更点` に追記が必要ないことを確認する。

## 実装コンテキスト

- spec: `specs/12-analyzer-protocol-implementation/index.md`
- review: `specs/12-analyzer-protocol-implementation/review.md`
- Issue: `#12`
- Branch: `feature/12`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 実装対象:
  - `core/internal/analyzer`
  - `core/internal/protocol` の parser API 呼び出し
  - `testdata/analyzer-protocol` の scenario fixture
- 参照する path:
  - `core/internal/analyzer`
  - `core/internal/protocol`
  - `testdata/analyzer-protocol`
- 参照しない path:
  - `analyzers/java`
  - `core/internal/graph`
  - `core/internal/traversal`
  - `core/internal/output`

## 前提条件

- 完了しているべき phase / 依存 prompt:
  - `P1_01_analyzer-protocol_protocol-model-validation.md`
  - `P2_01_analyzer-protocol_strict-jsonl-parser.md`
  - `P3_01_analyzer-protocol_contract-fixtures.md`
- 完了後に着手可能になる後続 prompt: なし。#12 の実装 prompt 群は完了に向かう。
- 必要な repo 状態: Protocol DTO / parser / scenario fixture が実装済み。

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める。
- 推測で実装を進めない。
- 質問するときは、止まっている作業単位、判断が必要な論点、選択肢を整理する。
- command path、argv、working directory、stderr 要約形式で判断不能になった場合は、P4_01 の完了条件を満たす最小案を提示して確認する。

## タスク境界

### 実装する範囲

- `core/internal/analyzer` の `os/exec` による最小 process runner。
- stdin に 1 件の `analysisRequest` JSONL を送信して close する処理。
- stdout streaming parse の接続。
- stderr と exit code の分離。
- scenario fixture を使った runner test。

### 実装しない範囲

- Protocol DTO / parser / fixture の再設計。
- Java Analyzer 実装。
- Graph、Traversal、Output、CLI interface。
- timeout、stderr 上限、record size 上限。
- parallel execution、session reuse、capability handshake。

## 設計仕様

- Core は 1 `analysisRequest` ごとに Analyzer process を 1 つ起動する。
- Core は Analyzer の stdin に `analysisRequest` record を 1 件送信し、その後 stdin を close する。
- Analyzer は stdout に `methodSymbol` / `callEdge` / `diagnostic` / `error` record を JSONL で逐次出力する。
- stderr は人間向け diagnostics とし、Core は protocol record として parse しない。
- exit code `0` は成功、非ゼロは fatal failure とする。
- Analyzer process reuse、session mode、interactive mode、capability handshake は Phase1 の対象外。
- timeout、最大 stderr サイズ、最大 record サイズは Core 実装時の runtime config とし、JSONL protocol field には含めない。本 promptでは固定しない。

## テスト観点

- Core が `analysisRequest` 送信後に stdin を close すること。
- Analyzer stdout の JSONL record が逐次 parse / validate されること。
- Analyzer stderr が protocol record として parse されないこと。
- exit code `0` を成功、非ゼロを fatal failure として扱うこと。
- `core/internal/analyzer` の最小 runner が stdin に 1 件の `analysisRequest` を送信して close すること。
- `core/internal/analyzer` の最小 runner が stdout を protocol parser へ streaming で渡すこと。
- `core/internal/analyzer` の最小 runner が exit code と stderr を protocol record と分離して扱うこと。
- scenario fixture で request、stdout JSONL、stderr、exit code の組み合わせを検証できること。
- Core が `analyzers/<language>/` や Analyzer runtime library に直接依存していないこと。

## 検証コマンド

- `cd core && go mod tidy`
- `cd core && go test ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `cd core && go list -deps ./...`
- `cd core && go list -f '{{.ImportPath}} {{.Imports}}' ./...`
- `git diff --check`

## 完了条件

- [ ] ステップ 0 で branch と差分状態を確認した。
- [ ] `core/internal/analyzer` に `os/exec` による最小 process runner がある。
- [ ] runner が stdin に 1 件の `analysisRequest` JSONL を送信して close する。
- [ ] runner が stdout を Protocol parser へ streaming で渡す。
- [ ] runner が stderr を protocol record として parse しない。
- [ ] runner が exit code `0` と非ゼロを区別する。
- [ ] scenario fixture を使った runner test がある。
- [ ] Java Analyzer、Graph、Traversal、Output、CLI interface を実装していない。
- [ ] timeout、stderr 上限、record size 上限、parallel execution、session reuse、capability handshake を実装していない。
- [ ] `cd core && go mod tidy` 後に意図しない差分がない。
- [ ] `cd core && go test ./...` がパスする。
- [ ] `cd core && go vet ./...` がパスする。
- [ ] `cd core && test -z "$(gofmt -l .)"` がパスする。
- [ ] `go list` で Analyzer runtime / implementation への直接 import がないことを確認した。
- [ ] `git diff --check` がパスする。
- [ ] 各ステップで diff レビューを実施し、指摘を対応した。
- [ ] 未解決の仕様質問が残っていない。
