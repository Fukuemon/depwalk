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
if command -v jq >/dev/null 2>&1; then
  jq '
    del(.version, .editor)
    | .permissions = (.permissions // {})
    | .permissions.allow = (.permissions.allow // [])
    | .permissions.deny  = (.permissions.deny  // [])
  ' "$CLI_JSON" > "$tmp"
else
  python3 - "$CLI_JSON" > "$tmp" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)

data.pop("version", None)
data.pop("editor", None)
permissions = data.setdefault("permissions", {})
permissions.setdefault("allow", [])
permissions.setdefault("deny", [])
json.dump(data, sys.stdout, ensure_ascii=False, indent=2)
sys.stdout.write("\n")
PY
fi

mv "$tmp" "$CLI_JSON"
echo "normalized $CLI_JSON for cursor-agent"
