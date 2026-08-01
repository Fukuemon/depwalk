---
type: context
title: "AI Agents Registry"
description: 非対話 CLI エージェントの invocation / routing / timeout 契約
keywords: [AI agent, Codex, Claude, Cursor, orchestrate]
governs:
  - .claude/agents
verified_commit: 9654928
---

# AI Agents Registry

非対話で呼び出せる CLI エージェント (Codex / Claude / Cursor 等) の **唯一の正本**。`agent-orchestrate` / `multi-agent-review` skill は、CLI 名・モデル・flag をハードコードせず本ファイルを読む。スキーマの説明は `agent-orchestrate/references/agent-registry-schema.md` を参照する。

<!--
記入ガイド:
- 各エージェントは 1 つの `### <id>` ブロックで定義する。
- `invocation` はプロンプトを `$PROMPT` プレースホルダで受ける **非対話** コマンド。user 固有の絶対 path は書かず、PATH 上の command 名または環境ごとの wrapper を使う。実行前に各 CLI の `--help` で flag を確定し、確認できたら `verified: yes` にする。
- `limit_patterns` は exit code か stdout/stderr に現れる文字列。token/rate 上限を検知するために使う。
- 値を変えたら先頭の「最終更新」を更新する。
-->

## 共通既定

- 出力先 dir 既定: `.ai-out/agent-runs/<timestamp>/` (per-agent ファイル `<id>.out` / `<id>.exit` を置く)
- timeout 既定: 600 秒 (各ブロックで上書き可)
- timeout runner: GNU `timeout` (macOS では未搭載のため `gtimeout` / coreutils へフォールバック。`parallel-execution.md` 参照)
- stdin: 各エージェントは `</dev/null` で起動する (stdin 待ちハング防止)
- `max_input_tokens` 既定: 120k (各ブロックで上書き可。超える差分は `multi-agent-review` 側で chunk 分割)
- 上限/失敗時ポリシー: **1 回リトライ後スキップ・部分成功許容** (`failure-handling.md` 参照)

## エージェント定義

### claude

- `enabled`: yes
- `model`: (CLI 既定)
- `invocation`: `claude -p "$PROMPT" --output-format text`
- `verified`: yes <!-- 2026-07-01 `claude --help` 相当の PATH command 前提。環境差は PATH / wrapper 側で吸収する -->
- `limit_patterns`: `usage limit`, `rate limit`, `429`
- `auth_note`: ログイン済み or `ANTHROPIC_API_KEY`
- `timeout`: 600

### codex

- `enabled`: yes
- `model`: (CLI 既定)
- `invocation`: `codex exec "$PROMPT"`
- `verified`: yes <!-- 2026-07-01 `codex exec --help` 相当の PATH command 前提。stdin パイプ誤検知でハングするため `</dev/null` 起動必須 -->
- `limit_patterns`: `rate limit`, `quota`, `usage limit`, `429`
- `auth_note`: `codex login` 済み or API key
- `timeout`: 600

### cursor

- `enabled`: yes
- `model`: `composer-2.5`
- `invocation`: `agent --print -f --model composer-2.5 --output-format text "$PROMPT"`
- `verified`: yes <!-- 2026-07-01 `agent --help` 相当の PATH command 前提。`--list-models` は keychain 認証エラーのため実走確認は認証後に行う -->
- `limit_patterns`: `rate limit`, `usage limit`, `quota`, `429`
- `auth_note`: `cursor agent login` 済み or `CURSOR_API_KEY`
- `timeout`: 600

## 用途別ルーティング

| 用途        | 既定で使うエージェント  | 備考                       |
| ----------- | ----------------------- | -------------------------- |
| review (Rv) | claude, codex, cursor   | 並列レビュー → 指摘マージ  |
| implement   | cursor (`composer-2.5`) | 実装委譲。完了後に検証する |

- フォールバックは既定では行わず、上限/失敗エージェントはスキップして残りで続行する。
- 用途ごとに使うエージェントを変える場合は skill 起動時に上書き指定する。

## 既知の問題

- **cursor cli.json schema 非互換 (2026-06-15 解決)**: `rulesync generate` (>=8.x) は `.cursor/cli.json` に top-level `version`・`editor` を常時出力し、`permissions.deny` を deny エントリがある時だけ出力する。一方 cursor-agent CLI は `version`・`editor` を未知キーとして拒否し `permissions.deny` を必須配列として要求するため、生成直後は起動不可だった。`rulesync` 側に抑止設定が無いため、(1) `.rulesync/permissions.json` に deny エントリを追加し deny 配列を出力、(2) 生成後に `scripts/fix-cursor-cli.sh` (jq で `version`/`editor` 除去・deny 配列保証) を通す、の 2 段で解消。`rulesync-sync` skill の generate 手順に正規化を組込み済み。`rulesync` 更新で `version`/`editor` を出さなくなれば (2) は不要になる。
