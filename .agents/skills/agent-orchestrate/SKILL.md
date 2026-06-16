---
name: agent-orchestrate
description: >-
  Fans out a single prompt to multiple non-interactive CLI agents (e.g. Codex /
  Claude / Cursor; the actual set lives in context/ai-agents.md) in parallel,
  collects per-agent output, and tolerates token / rate-limit failures with
  retry-then-skip. Use as the base layer when delegating review or
  implementation work to several AI agents at once.
---
# Agent Orchestrate

複数の CLI エージェントを **非対話・並列** に呼び出すための基盤 skill。1 つのプロンプトを各エージェントへ投げ、出力を収集し、token / rate 上限の失敗を「1 回リトライ後スキップ・部分成功許容」で扱う。Rv (`multi-agent-review`) や実装委譲など、上位 skill / ワークフローの実行エンジンとして使う。

## いつ使うか

- 「複数エージェントに並列で投げて」「クロス実行」「並列で委譲」と要求された
- 実装やレビューを特定の 1 エージェントに非対話で委譲したい (どのエージェントを使うかはレジストリの用途別ルーティングで決める)
- 一部エージェントが上限に達しても、残りの結果で前に進めたい
- 上位 skill (`multi-agent-review` 等) から実行エンジンとして呼ばれた

## 先に読むもの

- `context/ai-agents.md` — 利用可能エージェント / invocation / limit_patterns / timeout の正本
- `references/agent-registry-schema.md` — 上記レジストリのスキーマと読み方
- `references/parallel-execution.md` — 並列起動と出力収集の正準シェルパターン
- `references/failure-handling.md` — 失敗分類とリトライ/スキップ方針、status テーブル様式
- `references/aggregation.md` — 収集結果の汎用提示フォーマット
- root rule の `Skill 共通契約` (正本 `.rulesync/rules/CLAUDE.md`、各 provider へ `AGENTS.md` / `CLAUDE.md` として生成) — 固有値はレジストリを読む

## 入力

- 投げるプロンプト本文 (または生成元: diff / spec / 指示)
- 対象エージェント集合 (省略時は `context/ai-agents.md` の `enabled: yes` 全て)
- 出力先 dir (省略時はレジストリの「出力先 dir 既定」)
- 用途 (`review` / `implement` / 汎用) — レジストリの用途別ルーティングを参照

## 中核原則

- **非対話のみ**: 対話/TUI を起動しない。各 invocation は `$PROMPT` を 1 回で受け切る。
- **固有値を直書きしない**: CLI 名・モデル・flag は必ず `context/ai-agents.md` から解決する。
- **部分成功を許容**: 1 エージェントの失敗で全体を止めない。何が成功/失敗したかを必ず明示する。
- **プロンプトは固定**: 投げる本文を一時ファイルに確定し、全エージェントへ同一内容を渡す。
- **不可逆操作の確認**: implement 用途でエージェントがファイルを書き換える場合は、対象範囲を事前にユーザーへ確認する。

## 実行フロー

### 1. レジストリ解決

- `context/ai-agents.md` を読み、対象エージェントの `invocation` / `limit_patterns` / `timeout` / `model` を取得する。
- 対象集合の決定順: ①起動時に明示指定があればそれを優先 → ②`用途` (`review` / `implement`) 指定時は用途別ルーティング表で絞る → ③どちらも無ければ `enabled: yes` 全て。いずれの場合も `enabled: no` のエージェントは **除外** する。
- `verified: no` のエージェントは初回に `<cli> --help` / `<cli> <subcmd> --help` で各 flag が現行 CLI に存在するか点検してから使う (スキーマは `references/agent-registry-schema.md`)。
- 対象が 0 件なら `停止条件` へ。

### 2. プロンプトの固定

- 投げる本文を出力先 dir の `prompt.txt` に書き出す。この dir (`OUT`) を step3 の並列起動へそのまま渡す。
- 各 `invocation` の `$PROMPT` は、シェル変数経由 (`PROMPT="$(cat "$OUT/prompt.txt")"`) で渡し、起動時は必ず `"$PROMPT"` と引用したまま argv として展開する (`agent-registry-schema.md` の `$PROMPT` 展開規則)。`eval` でレジストリ文字列を再評価しない (本文中の特殊文字・改行が壊れる/誤実行する)。

### 3. 並列起動と収集

- `references/parallel-execution.md` のパターンで各エージェントを background 起動する。
- 各エージェントは `<出力先>/<id>.out` (stdout+stderr) と `<id>.exit` (終了コード) を残す。
- `timeout` を各エージェントに適用し、ハングを防ぐ。

### 4. 失敗分類とリトライ

- 各 `<id>.exit` と `<id>.out` を `references/failure-handling.md` の規則で分類する (status は `timeout` / `auth` / `limit` / `error` / `ok`。これは評価順で、判定は `failure-handling.md` を正とする)。
- `limit` または一過性 `error` は **1 回だけ** 短いバックオフ後に再実行する。
- 再試行後もダメなら当該エージェントを `status=limit/error` でスキップし、残りで続行する。

### 5. 集約と報告

- `references/aggregation.md` / `failure-handling.md` の様式 (`agent / status / retries / output` の 4 列) で status テーブルと各出力サマリを提示する。
- 上位 skill から呼ばれた場合は、成功エージェントの出力 path 一覧を返す。
- 全エージェントが失敗した場合は `停止条件` に該当。

## 停止条件

- `context/ai-agents.md` が読めない、または対象 `enabled` エージェントが 0 件
- 必須フィールドや必須の共通既定 (timeout / 出力先 / `max_input_tokens` 等) が解決できない (レジストリ不備。`agent-registry-schema.md` の解決順を参照)
- 投げるプロンプト本文が確定できない (生成元が空)
- timeout runner も background runner も無く、各エージェントのハングを打ち切る手段が無い (`parallel-execution.md`)
- 全エージェントが上限/失敗でスキップされ、成功結果が 1 件も無い
- implement 用途で書き換え対象がユーザーに確認されていない

## 禁止事項

- 対話/TUI モードでエージェントを起動する
- CLI 名・モデル・flag を skill に直書きする (レジストリを読む)
- 1 エージェントの失敗を理由に他エージェントの結果を破棄する
- `verified: no` のまま flag 未確認でリトライを繰り返し token を浪費する
