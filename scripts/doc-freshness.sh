#!/usr/bin/env bash
# 文書の鮮度検査: governs 配下に verified_commit 以降の変更があるものを stale として一覧する。
#
# **常に exit 0 で終わる。** stale の存在を理由に FAIL させない。
# 「その文書が今も正しいか」は機械的に判定できず、governs 配下が変わっても文書が
# 正しいままのケースは日常的にある。それを FAIL 扱いにすると「内容を読まずに
# verified_commit だけ進めて通す」ことが唯一の現実的な運用になり、gate が形骸化する。
# それは検査が存在しないのと同じである (ADR-0008 業務ルール 2)。
#
# frontmatter の設定ミス (governs / verified_commit の片欠け) だけは exit 1 にする。
# こちらは機械的に判定できる誤りであり、放置すると文書が一覧から静かに消えるため。
#
# GITHUB_STEP_SUMMARY があればそこへも書く (CI の job summary)。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
export LC_ALL=C

python3 - "$ROOT" <<'PY'
import os
import pathlib
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(sys.argv[1]) / "scripts"))
from docmeta import DocMetaError, load_docs  # noqa: E402

root = pathlib.Path(sys.argv[1])
try:
    docs = load_docs(root)
except DocMetaError as e:
    print(f"NG: {e}", file=sys.stderr)
    sys.exit(1)


def changed_since(commit: str, paths: list[str]) -> list[str]:
    """commit..HEAD で paths 配下に変更があった commit の短縮 SHA を返す。"""
    # rebase / squash 後、commit が解決はできるが HEAD の祖先ではない状態が起きる。
    # このとき git log commit..HEAD は成功し、多くの場合 0 件を返すため、実際には
    # 一度も突き合わせていない文書が「一致」と報告されてしまう。範囲クエリの前に
    # 祖先であることを確かめ、そうでなければ参照不能として扱う。
    anc = subprocess.run(
        ["git", "merge-base", "--is-ancestor", commit, "HEAD"],
        cwd=root, capture_output=True, text=True,
    )
    if anc.returncode != 0:
        raise RuntimeError(
            f"HEAD の祖先ではありません (rebase / squash で履歴から外れた可能性): {commit}"
        )

    r = subprocess.run(
        ["git", "log", "--format=%h", f"{commit}..HEAD", "--", *paths],
        cwd=root, capture_output=True, text=True,
    )
    if r.returncode != 0:
        raise RuntimeError(r.stderr.strip())
    return [line for line in r.stdout.splitlines() if line]


unverified: list[dict] = []
stale: list[tuple[dict, list[str]]] = []
broken: list[tuple[dict, str]] = []
fresh = 0

for d in docs:
    if not d["governs"]:
        continue  # 鮮度検査の対象外 (両方欠落。ADR-0008 決定 1)
    if d["verified_commit"] == "unverified":
        unverified.append(d)
        continue
    try:
        commits = changed_since(d["verified_commit"], d["governs"])
    except RuntimeError as e:
        # rebase / squash で verified_commit が消えた場合など。停止させず可視化する。
        broken.append((d, str(e)))
        continue
    if commits:
        stale.append((d, commits))
    else:
        fresh += 1

out: list[str] = []
out.append("## 文書の鮮度")
out.append("")
# 分母は governs を持つ文書だけにする。対象外の文書を足すと内訳の合計と
# 食い違い、全件一致のときに「検査していない文書まで一致した」と読めてしまう。
checked = fresh + len(stale) + len(unverified) + len(broken)
exempt_count = len(docs) - checked
out.append(
    f"検査対象 {checked} 文書 / 一致 {fresh} / 要確認 {len(stale)} / "
    f"未検証 {len(unverified)} / 参照不能 {len(broken)}"
    + (f" (ほかに governs を持たない対象外が {exempt_count} 文書)" if exempt_count else "")
)

if stale:
    out.append("")
    out.append("### 要確認 (governs 配下が verified_commit より進んでいる)")
    out.append("")
    for d, commits in sorted(stale, key=lambda x: x[0]["path"]):
        out.append(f"- `{d['path']}` — {d['verified_commit']} 以降に {len(commits)} commit")
        out.append(f"  - governs: {', '.join(d['governs'])}")

if unverified:
    out.append("")
    out.append("### 未検証 (まだ実装と突き合わせていない)")
    out.append("")
    for d in sorted(unverified, key=lambda x: x["path"]):
        out.append(f"- `{d['path']}`")

if broken:
    out.append("")
    out.append("### 参照不能 (verified_commit が解決できない)")
    out.append("")
    for d, err in sorted(broken, key=lambda x: x[0]["path"]):
        out.append(f"- `{d['path']}` — `{d['verified_commit']}` / {err}")

if not stale and not unverified and not broken:
    out.append("")
    out.append("すべての対象文書が実装と一致しています。")

out.append("")
out.append("確認したら該当文書の `verified_commit` を現在の HEAD へ進めてください。")
out.append("日付ではなく commit で表す理由と運用は [ADR-0008](../adr/0008-doc-freshness-and-reading-map.md) を参照。")

report = "\n".join(out)
print(report)

summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a", encoding="utf-8") as f:
        f.write(report + "\n")
PY
