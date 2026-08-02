#!/usr/bin/env python3
"""追跡ファイルの Markdown 相対リンクが実在するかを検査する。

判断の正本は adr/0011-doc-layout-and-quality-gates.md。

コードフェンスとコードスパンの中は見ない。索引の出力例のようにリンクの書式を
示すために書かれたものを、切れリンクとして数えないため。

templates/ も見ない。テンプレ内のリンクはテンプレの配置先 (context/*.md 等)
からの相対 path であり、テンプレ自身の位置からは解決しないため。
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys

EXCLUDE_PREFIXES = ("templates/",)

FENCE_RE = re.compile(r"```.*?```", re.S)
SPAN_RE = re.compile(r"`[^`\n]*`")
LINK_RE = re.compile(r"\[([^\]]*)\]\(([^)\s]+)\)")


def repo_root() -> pathlib.Path:
    return pathlib.Path(__file__).resolve().parent.parent


def tracked_markdown(root: pathlib.Path) -> list[str]:
    out = subprocess.run(
        ["git", "-C", str(root), "ls-files", "*.md"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    return [f for f in out.splitlines() if not f.startswith(EXCLUDE_PREFIXES)]


def broken_links(root: pathlib.Path, rel: str) -> list[tuple[str, str]]:
    path = root / rel
    text = path.read_text(encoding="utf-8")
    text = FENCE_RE.sub("", text)
    text = SPAN_RE.sub("", text)

    found = []
    for m in LINK_RE.finditer(text):
        # anchor は検査しない。見出しの表記ゆれまで追うと誤検出が増える。
        target = m.group(2).split("#")[0].strip()
        if not target or target.startswith(("http://", "https://", "mailto:")):
            continue
        if not (path.parent / target).exists():
            found.append((m.group(1), target))
    return found


def main() -> int:
    root = repo_root()
    total = 0
    for rel in sorted(tracked_markdown(root)):
        for label, target in broken_links(root, rel):
            print(f"NG {rel}: [{label}]({target})")
            total += 1

    if total:
        print(f"\ncheck-links: 切れリンク {total} 件", file=sys.stderr)
        return 1
    print("check-links: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
