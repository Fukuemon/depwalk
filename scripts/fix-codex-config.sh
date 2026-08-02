#!/usr/bin/env bash
# Codex が checkout 済み workspace を読めるよう .codex/config.toml を正規化する。
#
# `rulesync generate` は現状 `default_permissions = "rulesync"` と、
# `":minimal" = "read"` だけを持つ `[permissions.rulesync.filesystem]` profile を
# 出力する。Codex Desktop ではこの組み合わせにより、repository path は cwd として
# 見えているのに specs/ / context/ / design/ / .rulesync/ といった通常の
# プロジェクトファイルの読み取りが塞がれることがある。
#
# `npx rulesync@latest generate` の**後**に実行する。冪等。
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
