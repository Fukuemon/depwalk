#!/usr/bin/env bash
# Normalize .codex/config.toml so Codex uses the user's global sandbox policy
# instead of a Rulesync-generated project permissions profile.
#
# Why: rulesync writes `default_permissions = "rulesync"` and
# `[permissions.rulesync.*]` entries for codexcli. With rulesync 9.x this can
# generate a project profile such as `:minimal = "read"`, which overrides the
# user's global `sandbox_mode = "workspace-write"` and makes sessions read-only.
#
# Run this AFTER `npx rulesync@latest generate`. Idempotent.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG="$ROOT/.codex/config.toml"

[ -f "$CONFIG" ] || { echo "no $CONFIG; skip"; exit 0; }

tmp="$(mktemp)"
python3 - "$CONFIG" > "$tmp" <<'PY'
import sys

path = sys.argv[1]
with open(path, "r", encoding="utf-8") as f:
    lines = f.readlines()

out = []
skip_permissions_rulesync = False

for line in lines:
    stripped = line.strip()

    if stripped.startswith("[") and stripped.endswith("]"):
        section = stripped.strip("[]")
        skip_permissions_rulesync = section == "permissions.rulesync" or section.startswith("permissions.rulesync.")
        if skip_permissions_rulesync:
            continue

    if skip_permissions_rulesync:
        continue

    if stripped.startswith("default_permissions = "):
        continue

    out.append(line)

while out and out[0].strip() == "":
    out.pop(0)

while out and out[-1].strip() == "":
    out.pop()

sys.stdout.write("".join(out))
if out and not out[-1].endswith("\n"):
    sys.stdout.write("\n")
PY

mv "$tmp" "$CONFIG"
echo "normalized $CONFIG for Codex permissions"
