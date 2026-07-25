#!/usr/bin/env bash
# core/internal の package 依存図を `go list` の実 import から生成し、
# context/architecture.md の生成マーカー区間を置き換える。
#
# 手描きの依存図は実装が変わると静かに腐るため、図は必ず本スクリプトで
# 生成する (spec #32 D8 の可視化 3 点目。判断の正本は ADR-0007)。
# 冪等: 実 import が変わっていなければ再実行しても差分は出ない。
# その性質を使って CI / pre-commit が drift を検査する。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOC="$ROOT/context/architecture.md"
MODULE_PREFIX="github.com/Fukuemon/depwalk/core/internal/"
BEGIN_MARKER="<!-- BEGIN GENERATED: core-depgraph (scripts/depgraph.sh が更新する。手編集しない) -->"
END_MARKER="<!-- END GENERATED: core-depgraph -->"

# `<package> <依存 package>...` を core/internal 内のエッジだけに絞って出す。
# 宣言順ではなく sort して安定させる (go list の出力順に引きずられないため)。
edges="$(
  cd "$ROOT/core"
  go list -f '{{.ImportPath}} {{range .Imports}}{{.}} {{end}}' ./internal/... |
    awk -v prefix="$MODULE_PREFIX" '
      {
        if (index($1, prefix) != 1) next
        from = substr($1, length(prefix) + 1)
        # sub-package を作っていない前提の再編 (spec #32 D8) なので、
        # from に "/" が残るなら想定外の階層化として気付けるようにする。
        if (index(from, "/") != 0) next
        deps = ""
        for (i = 2; i <= NF; i++) {
          if (index($i, prefix) != 1) continue
          to = substr($i, length(prefix) + 1)
          if (index(to, "/") != 0) continue
          deps = deps " " to
        }
        if (deps != "") print from deps
      }
    ' | sort
)"

if [ -z "$edges" ]; then
  echo "depgraph: core/internal の依存エッジを検出できませんでした" >&2
  exit 1
fi

# mermaid 本体を組み立てる。1 行 1 起点で `a --> b & c` にまとめる
# (エッジ数ぶん行を作るより読みやすく、diff も起点単位で収まる)。
mermaid="$(
  printf '%s\n' "$edges" | while read -r from deps; do
    printf '    %s --> %s\n' "$from" "$(printf '%s\n' "$deps" | tr ' ' '\n' | sort -u | paste -sd '&' - | sed 's/&/ \& /g')"
  done
)"

generated="$(
  printf '%s\n\n' "$BEGIN_MARKER"
  printf '```mermaid\n'
  printf 'graph LR\n'
  printf '%s\n' "$mermaid"
  printf '```\n\n'
  printf '%s\n' "$END_MARKER"
)"

if ! grep -qF "$BEGIN_MARKER" "$DOC" || ! grep -qF "$END_MARKER" "$DOC"; then
  echo "depgraph: $DOC に生成マーカーが見つかりません" >&2
  exit 1
fi

# マーカー区間だけを差し替える (前後の本文には触れない)。
python3 - "$DOC" "$BEGIN_MARKER" "$END_MARKER" <<'PY' "$generated"
import sys

doc_path, begin, end, generated = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
with open(doc_path, encoding="utf-8") as f:
    text = f.read()

start = text.index(begin)
stop = text.index(end) + len(end)
with open(doc_path, "w", encoding="utf-8") as f:
    f.write(text[:start] + generated.rstrip("\n") + text[stop:])
PY

echo "depgraph: $DOC の依存図を更新しました"
