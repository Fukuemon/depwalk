---
type: context
title: "AI Agents Registry"
description: 非対話 CLI エージェントの invocation / routing / timeout 契約
keywords: [AI agent, Codex, Claude, Cursor, orchestrate]
---

# AI Agents Registry

非対話で呼び出せる CLI エージェント (Codex / Claude / Cursor 等) の唯一の決まり。
`agent-orchestrate` / `multi-agent-review` skill は、CLI 名・モデル・flag をハードコードせず本ファイルを読む。
各キーの意味は、`agent-orchestrate` skill の `references/agent-registry-schema.md` が定める。

<!--
記入ガイド:
- 各エージェントは 1 つの `### <id>` ブロックで定義する。
- `invocation` はプロンプトを `$PROMPT` プレースホルダで受ける非対話コマンド。user 固有の絶対 path は書かず、PATH 上の command 名または環境ごとの wrapper を使う。実行前に各 CLI の `--help` で flag を確定し、確認できたら `verified: yes` にする。
- `limit_patterns` は exit code か stdout/stderr に現れる文字列。token / rate 上限の検知に使う。
-->

## 共通既定

- 出力先 dir 既定: `.ai-out/agent-runs/<timestamp>/` (per-agent ファイル `<id>.out` / `<id>.exit` を置く)
- timeout 既定: 600 秒 (各ブロックで上書き可)
- timeout runner: GNU `timeout`。macOS には同梱されないため `gtimeout` / coreutils へフォールバックする (`agent-orchestrate` skill の `references/parallel-execution.md`)
- stdin: 各エージェントは `</dev/null` で起動する。stdin 待ちのハングを防ぐためである
- `max_input_tokens` 既定: 120k (各ブロックで上書き可。超える差分は `multi-agent-review` 側で chunk 分割)
- 上限・失敗時のポリシー: 1 回リトライしてからスキップし、部分成功を許容する (`agent-orchestrate` skill の `references/failure-handling.md`)

## エージェント定義

### claude

- `enabled`: yes
- `model`: (CLI 既定)
- `invocation`: `claude -p "$PROMPT" --output-format text`
- `verified`: yes <!-- 2026-07-01 確認。`claude --help` 相当の PATH command 前提。環境差は PATH / wrapper 側で吸収する -->
- `limit_patterns`: `usage limit`, `rate limit`, `429`
- `auth_note`: ログイン済み or `ANTHROPIC_API_KEY`
- `timeout`: 600

### codex

- `enabled`: yes
- `model`: (CLI 既定)
- `invocation`: `codex exec "$PROMPT"`
- `verified`: yes <!-- 2026-07-01 確認。`codex exec --help` 相当の PATH command 前提。stdin パイプ誤検知でハングするため `</dev/null` 起動必須 -->
- `limit_patterns`: `rate limit`, `quota`, `usage limit`, `429`
- `auth_note`: `codex login` 済み or API key
- `timeout`: 600

### cursor

- `enabled`: yes
- `model`: `composer-2.5`
- `invocation`: `agent --print -f --model composer-2.5 --output-format text "$PROMPT"`
- `verified`: yes <!-- 2026-07-01 確認。`agent --help` 相当の PATH command 前提。`--list-models` は keychain 認証エラーのため実走確認は認証後に行う -->
- `limit_patterns`: `rate limit`, `usage limit`, `quota`, `429`
- `auth_note`: `cursor agent login` 済み or `CURSOR_API_KEY`
- `timeout`: 600

## 用途別ルーティング

| 用途        | 既定で使うエージェント  | 備考                           |
| ----------- | ----------------------- | ------------------------------ |
| review (Rv) | claude, codex, cursor   | 並列レビュー → 指摘マージ      |
| implement   | cursor (`composer-2.5`) | 実装を任せる。完了後に検証する |

- フォールバックは既定では行わない。上限に達したエージェントと失敗したエージェントはスキップし、残りで続行する。
- 用途ごとに使うエージェントを変える場合は、skill 起動時に上書き指定する。

## 既知の問題

cursor の `cli.json` は、`rulesync generate` の出力そのままでは cursor-agent CLI が起動できない。
`rulesync` (>=8.x) は top-level の `version` と `editor` を常時出力し、`permissions.deny` は deny エントリがある時だけ出力する。
一方 cursor-agent CLI は `version` と `editor` を未知キーとして拒否し、`permissions.deny` を必須配列として要求する。

`rulesync` 側に出力の抑止設定がないため、次の 2 段で解消している。

1. `.rulesync/permissions.json` に deny エントリを追加し、deny 配列を必ず出力させる。
2. 生成後に sdd-template 側の `scripts/fix-cursor-cli.sh` を通し、`version` / `editor` を除去して deny 配列を保証する。

生成は sdd-template の `make dist` が中央で行うため、本リポジトリに正規化の手順は置かない。
`rulesync` が `version` / `editor` を出力しなくなれば、手順 2 は不要になる。

## 参照

- [README.md](README.md) の `governs` の書き方: 本ファイルが `governs` / `verified_commit` を持たない理由と、鮮度の判断方法
