#!/usr/bin/env bash
# Normalize .codex/config.toml so Codex can read the checked-out workspace.
#
# Why: `rulesync generate` currently emits `default_permissions = "rulesync"`
# and a generated `[permissions.rulesync.filesystem]` profile with only
# `":minimal" = "read"`. In Codex Desktop this can make the repository path
# visible as the cwd while blocking reads for normal project files such as
# specs/, context/, design/, and .rulesync/.
#
# Run this AFTER `npx rulesync@latest generate`. Idempotent.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG="$ROOT/.codex/config.toml"

[ -f "$CONFIG" ] || { echo "no $CONFIG; skip"; exit 0; }

tmp="$(mktemp)"
awk '
  /^default_permissions = "rulesync"$/ { next }
  /^\[permissions\.rulesync\.filesystem\]$/ {
    skip = 1
    next
  }
  /^\[/ {
    skip = 0
  }
  skip {
    next
  }
  {
    if (!started && $0 == "") {
      next
    }
    started = 1
    print
  }
' "$CONFIG" > "$tmp"

mv "$tmp" "$CONFIG"
echo "normalized $CONFIG for Codex workspace access"
