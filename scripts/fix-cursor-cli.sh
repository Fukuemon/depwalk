#!/usr/bin/env bash
# cursor-agent CLI が受け付ける形へ .cursor/cli.json を正規化する。
#
# `rulesync generate` (>=8.x) は top-level の `version` / `editor` を必ず書き、
# `permissions.deny` は deny が 1 件以上あるときしか出さない。一方で現行の
# cursor-agent CLI は `version` / `editor` を未知キーとして拒否し、
# `permissions.deny` が配列であることを要求する。rulesync 側にこれらを抑止する
# option が無いため、生成後にここで整える。
#
# `npx rulesync@latest generate` の**後**に実行する。冪等。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLI_JSON="$ROOT/.cursor/cli.json"

[ -f "$CLI_JSON" ] || { echo "no $CLI_JSON; skip"; exit 0; }

tmp="$(mktemp)"
# - 未知の top-level キー (version、editor) を落とす
# - permissions.deny を必ず配列にする (cursor-agent の要求)
jq '
  del(.version, .editor)
  | .permissions = (.permissions // {})
  | .permissions.allow = (.permissions.allow // [])
  | .permissions.deny  = (.permissions.deny  // [])
' "$CLI_JSON" > "$tmp"

mv "$tmp" "$CLI_JSON"
echo "normalized $CLI_JSON for cursor-agent"
