#!/usr/bin/env bash
# Disable Claude Code attribution in generated project settings.
# Run after `npx rulesync@latest generate`. Idempotent.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SETTINGS_FILE="${1:-$ROOT/.claude/settings.json}"
[ -f "$SETTINGS_FILE" ] || { echo "no $SETTINGS_FILE; skip"; exit 0; }

python3 - "$SETTINGS_FILE" <<'PY'
import json
import os
import sys
import tempfile

path = sys.argv[1]
with open(path, encoding="utf-8") as source:
    settings = json.load(source)

settings["attribution"] = {"commit": "", "pr": ""}

directory = os.path.dirname(path) or "."
fd, temporary = tempfile.mkstemp(prefix=".claude-settings.", dir=directory, text=True)
try:
    with os.fdopen(fd, "w", encoding="utf-8") as target:
        json.dump(settings, target, ensure_ascii=False, indent=2)
        target.write("\n")
    os.replace(temporary, path)
except BaseException:
    try:
        os.unlink(temporary)
    except FileNotFoundError:
        pass
    raise
PY
