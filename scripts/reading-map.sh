#!/usr/bin/env bash
# 文書の frontmatter から読み取りマップと索引を生成する。
#
#   context/reading-map.yaml  「コードパス → 読むべき文書」の逆引き (全面生成)
#   context/README.md          ファイル一覧 (生成マーカー区間だけ置換)
#
# 手書きの索引は育たない (impact-index.yaml が実例) ため生成物にする。
# 判断の正本は adr/0008-doc-freshness-and-reading-map.md。
#
# 冪等: frontmatter が変わっていなければ再実行しても差分は出ない。
# その性質を使って pre-commit / CI が drift を検査する。
#
# 索引は markdown テーブルではなく箇条書きで出力する。prettier がテーブルの
# 列幅を揃え直すため、テーブルだと生成 -> 整形 -> 再生成の ping-pong になり
# drift 検査が恒久的に FAIL する (ADR-0008「生成区間を含む文書の整形」)。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# sort の locale 差で並びが変わると drift を誤検出するため固定する。
export LC_ALL=C

python3 - "$ROOT" <<'PY'
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(sys.argv[1]) / "scripts"))
from docmeta import DocMetaError, load_docs  # noqa: E402

BEGIN = "<!-- BEGIN GENERATED: context-index (scripts/reading-map.sh が更新する。手編集しない) -->"
END = "<!-- END GENERATED: context-index -->"

root = pathlib.Path(sys.argv[1])
try:
    docs = load_docs(root)
except DocMetaError as e:
    sys.exit(f"reading-map: {e}")

if not docs:
    sys.exit("reading-map: frontmatter を持つ文書が 1 件も見つかりませんでした")

# --- context/reading-map.yaml (全面生成) ---------------------------------
# governs を逆引きして「コードパス → 読むべき文書」にする。1 つのパスを
# 複数の文書が語ることはある (例: protocol は graph と analyzer-protocol の
# 両方が契約を持つ) ので、値は配列にする。
by_path: dict[str, list[dict]] = {}
for d in docs:
    for p in d["governs"]:
        by_path.setdefault(p, []).append(d)

lines = [
    "# 読み取りマップ — 「何を読めば足りるか」のルーティング表",
    "#",
    "# scripts/reading-map.sh が各文書の frontmatter から生成する。手編集しない。",
    "# 判断の正本は adr/0008-doc-freshness-and-reading-map.md。",
    "#",
    "# 使い方: 触るコードパスで前方一致するエントリを引き、docs のファイルだけを読む。",
    "# エントリが無い場合は repo 全体の探索へ逃げず、対象文書に governs を足す。",
    "",
    "paths:",
]
for p in sorted(by_path):
    entry = by_path[p]
    lines.append(f"  {p}:")
    lines.append("    docs:")
    for d in sorted(entry, key=lambda x: x["path"]):
        lines.append(f"      - {d['path']}")
    keywords = sorted({k for d in entry for k in d["keywords"]})
    if keywords:
        lines.append("    keywords: [" + ", ".join(keywords) + "]")

# 鮮度検査の対象外 (governs を持たない) 文書も、読み取りの入口としては有用なので残す。
exempt = [d for d in docs if not d["governs"]]
if exempt:
    lines.append("")
    lines.append("# governs を持たない文書 (鮮度検査の対象外。索引としてのみ載せる)")
    lines.append("unscoped:")
    for d in sorted(exempt, key=lambda x: x["path"]):
        lines.append(f"  - {d['path']}")

(root / "context" / "reading-map.yaml").write_text("\n".join(lines) + "\n", encoding="utf-8")

# --- context/README.md のマーカー区間 -------------------------------------
readme = root / "context" / "README.md"
text = readme.read_text(encoding="utf-8")
if text.count(BEGIN) != 1 or text.count(END) != 1:
    sys.exit(f"reading-map: {readme} の生成マーカーが 1 組ではありません")

index_lines = [BEGIN, ""]
for d in sorted((x for x in docs if x["path"].startswith("context/")), key=lambda x: x["path"]):
    name = d["path"].split("/", 1)[1]
    if name == "README.md":
        continue  # 索引自身は載せない
    desc = d["description"] or d["title"] or ""
    index_lines.append(f"- [{name}]({name}) — {desc}" if desc else f"- [{name}]({name})")
index_lines += ["", END]

start = text.index(BEGIN)
stop = text.index(END) + len(END)
readme.write_text(text[:start] + "\n".join(index_lines) + text[stop:], encoding="utf-8")

print(f"reading-map: {len(by_path)} パス / {len(docs)} 文書から生成しました")
PY
