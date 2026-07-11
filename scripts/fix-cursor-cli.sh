#!/usr/bin/env bash
# Normalize .cursor/cli.json so the cursor-agent CLI accepts it.
#
# Why: `rulesync generate` (>=8.x) always writes top-level `version` / `editor`
# keys and only emits `permissions.deny` when at least one deny entry exists.
# The current cursor-agent CLI rejects `version` / `editor` as unrecognized keys
# and requires `permissions.deny` to be an array. rulesync has no option to
# suppress those keys, so we normalize the generated file here.
#
# Run this AFTER `npx rulesync@latest generate`. Idempotent.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLI_JSON="$ROOT/.cursor/cli.json"

[ -f "$CLI_JSON" ] || { echo "no $CLI_JSON; skip"; exit 0; }

tmp="$(mktemp)"
# - drop unrecognized top-level keys (version, editor)
# - guarantee permissions.deny is an array (cursor-agent requires it)
jq '
  del(.version, .editor)
  | .permissions = (.permissions // {})
  | .permissions.allow = (.permissions.allow // [])
  | .permissions.deny  = (.permissions.deny  // [])
' "$CLI_JSON" > "$tmp"

mv "$tmp" "$CLI_JSON"
echo "normalized $CLI_JSON for cursor-agent"
