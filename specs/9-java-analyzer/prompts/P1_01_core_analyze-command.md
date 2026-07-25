# Core 初回配線: `depwalk analyze` command と Analyzer 起動コマンド解決

## 絶対ルール

- spec に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない。
- **Java 固有の分岐を Core に入れない (S5)**。Core は `java` / jar / JVM の存在を知らず、起動コマンドを不透明な文字列として扱う。`--java-classpath` のような言語別 flag、言語別の path 解決規約、`language` による分岐を Core に実装しない。
- **`viper` を導入しない** (`context/toolchain.md`)。runtime dependency は既存の `github.com/spf13/cobra` のみに保つ。環境変数の読み取りは `os.Getenv` を使う。
- 起動コマンド文字列は **shell を介さず** shell-word 分割して exec する。`sh -c` / `bash -c` の使用は禁止。
- Java Analyzer 本体 (`analyzers/java/`)、Traversal / Output の変更、CLI 引数の完全仕様 (出力形式 / 探索方向 / 深さ上限等) は実装しない。
- テストは fake analyzer (任意の実行可能ファイル) で行い、JVM に依存しない。
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

## 作業ステップ (この順序で実行する)

### ステップ 0: ブランチ準備

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` に従う。Issue は `#9`。

1. 現在の branch が `feature/9` (または派生の実装 branch) であることを確認する。
2. `git status --short` で意図しない差分がないことを確認する。
3. 検証: 作業開始前の branch と差分を記録する。

### ステップ 1: Analyzer 起動コマンドの解決を実装する

1. テストを先に書く (TDD): 解決順序 (flag → 環境変数 → validation error) と shell-word 分割のテストを `core/internal/cli` または `core/internal/analyze` に置く。
2. `--analyzer-cmd` flag → `DEPWALK_ANALYZER_CMD` 環境変数の順で起動コマンド文字列を解決する。どちらも無ければ Analyzer 起動前に validation error で拒否する (非ゼロ exit)。
3. 解決した文字列を shell を介さず shell-word 分割 (quote 処理を含む) して argv にする。
4. 検証: `cd core && go test ./...` を実行する。
5. diff レビュー (`spec-review` または repo の標準レビュー手段) を回し、言語固有の分岐が入っていないことを確認する。

### ステップ 2: `--analyzer-meta` の合成規則を実装する

1. テストを先に書く: 下記「設計仕様」の合成規則 5 分岐 (1 回指定 / 繰り返し / 空値 / `=` なし / value 内 `=`) を網羅する。
2. `--analyzer-meta key=value` (繰り返し指定可) を `analysisRequest.metadata` の JSON に合成する。Core は key / value の意味を解釈しない。
3. `=` を含まない指定は実行前に validation error として拒否する。
4. 検証: `cd core && go test ./...`、`cd core && go vet ./...` を実行する。
5. diff レビューを回す。

### ステップ 3: `depwalk analyze` command を配線する

1. テストを先に書く: fake analyzer (テスト内で build する任意の実行可能ファイル) を `--analyzer-cmd` で起動し、graph 構築まで通ることを検証する。
2. `core/internal/cli` に Cobra の `analyze` command を追加し、`core/internal/analyze` の use case orchestration から既存の `core/internal/analyzer` (process runner) / `core/internal/protocol` (parser / validator) / `core/internal/graph` と結合する。
3. `analysisRequest` を組み立て (workspaceRoot / language / metadata)、Analyzer 起動 → JSONL 逐次 parse → graph 構築までを通す。`diagnostic` は利用者へ伝播し、`error` record / 非ゼロ exit は fatal failure として扱う (既存 runner の契約を再利用し、再設計しない)。
4. 検証: `cd core && go test ./...`、`cd core && go vet ./...`、`cd core && test -z "$(gofmt -l .)"` を実行する。
5. diff レビューを回す。

### ステップ最終: 最終確認

1. `## 検証コマンド` の全コマンドがパスすることを確認する。
2. `cd core && go list -deps ./...` を実行し、`analyzers/` や JVM 関連への直接 import がないことを確認する。
3. spec の `## 上位資料からの変更点` に必要な追記がないかを phase: track に渡す。

## 実装コンテキスト

- spec: `specs/9-java-analyzer/index.md` (D2 / D3 / Interface 設計は決定経緯)
- 設計の正本: `design/features/java-analyzer/DesignDoc_java-analyzer.md` (起動契約 / metadata 契約)
- 判断の正本: `adr/0003-analyzer-command-resolution.md`
- Issue: `#9` / Branch: `feature/9`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 実装対象:
  - `core/internal/cli` (analyze command / flags)
  - `core/internal/analyze` (use case orchestration)
- 参照する path (既存成果、再設計しない):
  - `core/internal/analyzer` (process runner / #12 実装済み)
  - `core/internal/protocol` (parser / validator / #12 実装済み)
  - `core/internal/graph`
- 参照しない path:
  - `analyzers/java`
  - `core/internal/traversal`
  - `core/internal/output`

## 前提条件

- 完了しているべき phase / 依存 prompt: なし (P1_02 と並列可。変更ファイルが衝突しない)
- 完了後に着手可能になる後続 prompt: `P2_02_core_e2e-fixture-baseline.md`
- 必要な repo 状態: `core/internal/analyzer` / `core/internal/protocol` が #12 で実装済み

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める。
- 推測で実装を進めない。
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する。
- workspaceRoot の指定方法 (positional arg か flag か) 等、本 prompt に未記載の CLI 詳細で判断不能になった場合は、最小案を提示して確認する (全 flag 体系は後続の CLI interface spec で確定するため、拡張可能な最小に留める)。

## タスク境界

### 実装する範囲

- `depwalk analyze` command (Cobra) と use case orchestration。
- 起動コマンド解決 (`--analyzer-cmd` → `DEPWALK_ANALYZER_CMD` → validation error)。
- shell 非経由の shell-word 分割 exec。
- `--analyzer-meta key=value` の合成規則と metadata passthrough。
- 既存 `core/internal/analyzer` / `core/internal/protocol` / `core/internal/graph` との結合 (graph 構築まで)。
- fake analyzer による unit / contract test。

### 実装しない範囲

- Java Analyzer 本体 (`analyzers/java/`)。
- Traversal / Output との結合 (グラフ探索 / 出力整形の配線)。`core/internal/traversal` (#6) / `core/internal/output` (#7) は実装済みだが、`depwalk analyze` からの結合は後続の CLI interface spec の責務。本 prompt は graph 構築までを実装する。
- CLI 引数の完全仕様 (出力形式 / 探索方向 / 深さ上限等 → 後続の CLI interface spec)。
- 規約 path による既定解決 (binary の隣を探す等)。
- timeout / stderr 上限 / record size 上限。

## 設計仕様

feature doc「起動契約」「metadata 契約」(正本: `design/features/java-analyzer/DesignDoc_java-analyzer.md`) からの抜粋:

**起動契約** (ADR-0003):

- 解決順序: ① CLI flag `--analyzer-cmd` (例: `"java -jar analyzers/java.jar"`) → ② 環境変数 `DEPWALK_ANALYZER_CMD` → ③ どちらも無ければ実行前に validation error で拒否する。
- Core は解決した文字列を shell を介さず shell-word 分割して exec する (shell injection を避ける)。Core は `java` / jar / JVM の存在を知らない。
- `DEPWALK_` prefix は depwalk の環境変数の名前空間とする。

**`--analyzer-meta` の合成規則** (Core が metadata の JSON を組み立てる規則):

- 値は常に JSON 配列に積む。1 回だけ指定した場合も要素 1 の配列になる (`--analyzer-meta classpath=/a.jar` → `{"classpath": ["/a.jar"]}`)。
- 同一 key の繰り返しは、指定順に配列へ追加する (`--analyzer-meta classpath=/a.jar --analyzer-meta classpath=/b.jar` → `{"classpath": ["/a.jar", "/b.jar"]}`)。
- 値が空文字列の場合、その key を空配列として登録する (`--analyzer-meta classpath=` → `{"classpath": []}`)。
- 分割は最初の `=` で行う (value 側に `=` を含んでよい)。`=` を含まない指定は validation error として実行前に拒否する。
- Core は key / value の意味 (`classpath` 等) を解釈しない。意味づけを知るのは Analyzer だけである。

**process contract** (正本: analyzer-protocol feature doc / ADR-0001。#12 実装済みの runner を再利用):

- stdin へ `analysisRequest` を 1 件送信して close。stdout は JSONL 逐次 parse。stderr は protocol 対象外。exit code 0 = 成功、非ゼロ = fatal failure。

## テスト観点

spec「テスト / 評価方針 — Go 側 process contract」からの抜粋 (fake analyzer / JVM 不要):

- `--analyzer-cmd` / `DEPWALK_ANALYZER_CMD` の解決順序と、どちらも無い場合の実行前拒否。
- `--analyzer-meta key=value` の合成規則: 1 回指定 → 要素 1 の配列、繰り返し → 指定順の配列、空値 (`key=`) → 空配列、`=` なし → validation error、value に `=` を含む指定 → 最初の `=` で分割。
- shell を介さない shell-word 分割で exec されること。
- 既存の contract test 観点 (stdin close / 逐次 parse / stderr 非 parse / exit code) は #12 実装済みのものを再利用する。
- Core が `analyzers/<language>/` や Analyzer runtime library に直接依存していないこと。

## 検証コマンド

- `cd core && go build ./...`
- `cd core && go test ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `cd core && go mod tidy` (後に意図しない差分がないこと)
- `git diff --check`

## 完了条件

- [ ] ステップ 0 で branch と差分状態を確認した。
- [ ] `depwalk analyze` command が Cobra に追加され、既存 runner / parser / graph と結合されている。
- [ ] 起動コマンド解決が flag → 環境変数 → validation error の順で動く。
- [ ] 起動コマンドが shell を介さず shell-word 分割で exec される。
- [ ] `--analyzer-meta` の合成規則 5 分岐がテストで網羅されている。
- [ ] Core に Java 固有の分岐・言語別 flag が入っていない (S5)。
- [ ] `viper` を導入していない (runtime dependency は Cobra のみ)。
- [ ] テストが fake analyzer で回り、JVM に依存しない。
- [ ] `## 検証コマンド` がすべてパスする。
- [ ] 各ステップで diff レビューを実施し、指摘を対応した。
- [ ] 未解決の仕様質問が残っていない。
