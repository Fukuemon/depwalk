#!/usr/bin/env bash
# core/internal の package 依存図を `go list` の実 import から生成し、
# context/architecture.md の生成マーカー区間を置き換える。
#
# 手描きの依存図は実装が変わると静かに腐るため、図は必ず本スクリプトで
# 生成する (判断の正本は ADR-0007)。
# 冪等: 実 import が変わっていなければ再実行しても差分は出ない。
# その性質を使って CI / pre-commit が drift を検査する。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOC="$ROOT/context/architecture.md"
MODULE_PREFIX="github.com/Fukuemon/depwalk/core/internal/"
BEGIN_MARKER="<!-- BEGIN GENERATED: core-depgraph (scripts/depgraph.sh が更新する。手編集しない) -->"
END_MARKER="<!-- END GENERATED: core-depgraph -->"

# `<package> <依存 package>...` を core/internal 内のエッジだけに絞って出す。
#
# 図の対象は core/internal 間のエッジのみ。`cmd/depwalk` は cli を呼ぶだけの
# entrypoint で、依存規則 (architecture.md の Package Boundary) が語るのは
# internal の package 間なので含めない。
#
# `.Imports` は test ファイルの import を含まない。depguard は test も検査
# するため「test 専用の依存エッジ」は gate では捕まるが図には出ない。図は
# production の依存方向を示すもの、という非対称を意図して受け入れている。
#
# 宣言順ではなく sort して安定させる (go list の出力順に引きずられないため)。
# sort の locale 差で並びが変わると drift 検査が誤検出するので LC_ALL を固定する。
export LC_ALL=C
edges="$(
  cd "$ROOT/core"
  go list -f '{{.ImportPath}} {{range .Imports}}{{.}} {{end}}' ./internal/... |
    awk -v prefix="$MODULE_PREFIX" '
      {
        if (index($1, prefix) != 1) next
        from = substr($1, length(prefix) + 1)
        # core/internal はフラットな責務名 package を維持する前提。
        # sub-package が生えたら図の粒度の前提が崩れるので、黙って捨てずに
        # 失敗させる (drift 検査を素通りさせない)。
        if (index(from, "/") != 0) {
          printf "depgraph: 想定外の sub-package です: %s\n", $1 > "/dev/stderr"
          exit 1
        }
        deps = ""
        for (i = 2; i <= NF; i++) {
          if (index($i, prefix) != 1) continue
          to = substr($i, length(prefix) + 1)
          if (index(to, "/") != 0) continue
          deps = deps " " to
        }
        # 内部依存を持たない package (graph / analyzer) は起点行を持たない。
        # 他 package からの到達先として図に現れる。
        if (deps != "") print from deps
      }
    ' | sort
)"

if [ -z "$edges" ]; then
  echo "depgraph: core/internal の依存エッジを検出できませんでした" >&2
  exit 1
fi

# どのエッジにも現れない package (依存も被依存もない孤立 package) は
# 単独ノードとして描く。エッジ由来の行だけだと図から静かに消えてしまい、
# 「図は実装と一致する」という drift 検査の前提が崩れるため。
all_packages="$(
  cd "$ROOT/core"
  go list -f '{{.ImportPath}}' ./internal/... |
    sed "s|^${MODULE_PREFIX}||" | grep -v '/' | sort -u
)"
linked_packages="$(printf '%s\n' "$edges" | tr ' ' '\n' | grep -v '^$' | sort -u)"
isolated="$(comm -23 <(printf '%s\n' "$all_packages") <(printf '%s\n' "$linked_packages"))"

# mermaid 本体を組み立てる。1 行 1 起点で `a --> b & c` にまとめる
# (エッジ数ぶん行を作るより読みやすく、diff も起点単位で収まる)。
mermaid="$(
  printf '%s\n' "$edges" | while read -r from deps; do
    printf '    %s --> %s\n' "$from" "$(printf '%s\n' "$deps" | tr ' ' '\n' | sort -u | paste -sd '&' - | sed 's/&/ \& /g')"
  done
  if [ -n "$isolated" ]; then
    printf '%s\n' "$isolated" | while read -r pkg; do
      [ -n "$pkg" ] && printf '    %s\n' "$pkg"
    done
  fi
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

if text.count(begin) != 1 or text.count(end) != 1:
    sys.exit("depgraph: 生成マーカーが 1 組ではありません")

start = text.index(begin)
stop = text.index(end) + len(end)
if stop <= start:
    sys.exit("depgraph: 生成マーカーの順序が不正です (END が BEGIN より前にあります)")

with open(doc_path, "w", encoding="utf-8") as f:
    f.write(text[:start] + generated.rstrip("\n") + text[stop:])
PY

echo "depgraph: $DOC の依存図を更新しました"
