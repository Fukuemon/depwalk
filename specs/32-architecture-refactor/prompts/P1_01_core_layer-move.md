# Core を層別ディレクトリ (domain/app/platform) へ物理移動する

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止。移動対象の列挙は本 prompt に記載済み)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- **本 prompt は「物理移動と import path の機械的更新」のみ**。依存関係の是正 (protocol 依存の除去・port 導入) は P2_01 の責務であり、本 prompt では行わない
- **完了条件のタスク化**: 作業開始前に「完了条件」セクションの各項目を todo として登録し、各ステップ完了時に状態を更新すること。タスク化せずに作業を開始することは禁止

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

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` に従う。

1. 最新の base branch (`main`) を取得する (#32 の設計 PR がマージ済みであることを確認する)
2. 作業ブランチ `feature/34` を作成する
3. PR テンプレートを確認し、issue #34 の完了条件を description に転記する
4. Draft PR を作成して push する

### ステップ 1: ディレクトリ移動と import path 更新

1. 下記「設計仕様」の対応表どおりに `git mv` で package を移動する
2. `core/` 配下全体の import path を旧 → 新へ機械的に置換する (テストファイルの import も含む。テスト本体のロジックは変更しない)
3. `## 検証コマンド` をすべて実行する
4. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す
5. 指摘を対応してから次へ

### ステップ最終: 最終確認

1. 全テスト / vet / fmt がパスすることを確認する
2. spec #32 の `## 上位資料からの変更点` に追記が必要な差分が出ていないか確認する (出た場合は停止して報告)
3. commit する (PR は P3_01 完了まで Draft のまま)

## 実装コンテキスト

- spec: `specs/32-architecture-refactor/index.md`
- 正本: `context/architecture.md` (Package Boundary「Core の層構造 (Go)」)、`adr/0007-layered-architecture-refactor.md`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path: `core/internal/`、`core/cmd/depwalk/`、`core/e2e/`

## 前提条件

- 完了しているべき phase / 依存 prompt: なし (#32 設計 PR の main マージのみ)
- 完了後に着手可能になる後続 prompt: `P2_01_core_wire-acl-port.md`
- 必要な repo 状態: main に `adr/0007` / 改訂済み `context/architecture.md` が存在する

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- `core/internal` 配下 7 package の層別ディレクトリへの物理移動 (`git mv`)
- `core/` 配下全体 (cmd / internal / e2e、テスト含む) の import path の機械的更新

### 実装しない範囲

- 依存関係の是正 (`graph`/`output`/`analyze`/`cli` の `protocol` 依存除去、port 導入、手動 DI 整理) — P2_01 の責務
- lint (depguard) 導入 — P3_01 の責務
- doc の path 追随 — P3_01 の責務
- Java Analyzer 側 — issue #35 の責務
- ロジック・シグネチャ・外部挙動の一切の変更

## 設計仕様

移動対応表 (spec #32 D1 / D2 確定。package 名 = import 末尾は従来のまま):

| 旧 path                   | 新 path                           | 層       |
| ------------------------- | --------------------------------- | -------- |
| `core/internal/graph`     | `core/internal/domain/graph`      | domain   |
| `core/internal/traversal` | `core/internal/domain/traversal`  | domain   |
| `core/internal/analyze`   | `core/internal/app/analyze`       | app      |
| `core/internal/protocol`  | `core/internal/platform/protocol` | platform |
| `core/internal/analyzer`  | `core/internal/platform/analyzer` | platform |
| `core/internal/output`    | `core/internal/platform/output`   | platform |
| `core/internal/cli`       | `core/internal/platform/cli`      | platform |

- `core/cmd/depwalk` は移動しない (import path の更新のみ)
- module path は `github.com/Fukuemon/depwalk/core` のまま変更しない
- この時点では旧来の依存 (`graph` → `protocol` 等) が残っていてよい (P2_01 で是正)

## テスト観点

- 既存テストスイート (Go unit / E2E / golden) がテスト本体のロジック変更なし (import path の機械的修正のみ) で全件 PASS する (受け入れ基準 4)
- 外部挙動 (CLI フラグ / JSONL Protocol / 出力形式 / exit code) が一切変わらない

## 検証コマンド

- `cd core && go build ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `cd core && go test ./...`
- `(cd analyzers/java && ./gradlew shadowJar) && ./analyzers/java/gradlew --no-daemon -p testdata/fixtures/java/spring-project clean writeDepwalkClasspath && (cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -count=1)` (要 JDK 25)
- `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 でブランチ `feature/34` と Draft PR を作成した
- [ ] 移動対応表どおりに 7 package を移動し、import path を全更新した
- [ ] テスト本体のロジック変更が diff に含まれていない (import path 変更のみ)
- [ ] `## 検証コマンド` がすべてパスする
- [ ] diff レビューを実施し、指摘を対応した
- [ ] 未解決の仕様質問が残っていない
