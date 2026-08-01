#!/usr/bin/env python3
"""文書 frontmatter の収集と検証。

reading-map.sh / doc-freshness.sh が共有する読み取り層。判断の正本は
adr/0008-doc-freshness-and-reading-map.md。

PyYAML に依存しない。frontmatter の schema は「フラットなキー + 文字列 /
インライン配列 / ブロック配列」に限られており、そのぶんだけを解釈する。
CI で追加の pip install を要求しないことを優先している。
"""

from __future__ import annotations

import pathlib
import re
import sys

# 鮮度検査・索引生成の対象。ADR-0008 決定 2 の割り当て表に対応する。
# adr/ は決定時点の不変記録、specs/ は issue close 時に削除されるため対象外。
TARGET_GLOBS = ("design/*.md", "design/features/*/*.md", "context/*.md")

FRONTMATTER_RE = re.compile(r"\A---\n(.*?)\n---\n", re.S)


class DocMetaError(Exception):
    """frontmatter の設定ミス。呼び出し側は FAIL させる。"""


def _strip_quotes(value: str) -> str:
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
        return value[1:-1]
    return value


def _is_sha(value: object) -> bool:
    """不変の commit 参照か。短縮 SHA を許すため 7 文字以上とする。"""
    return isinstance(value, str) and bool(re.fullmatch(r"[0-9a-f]{7,40}", value))


def _parse_frontmatter(text: str) -> dict | None:
    """先頭の frontmatter を dict で返す。無ければ None。"""
    m = FRONTMATTER_RE.match(text)
    if not m:
        return None

    data: dict = {}
    current_list_key: str | None = None
    # 「値が空のキー」は、ブロック配列の開始とも「値の書き忘れ」とも読める。
    # 後続に要素が 1 つも来なければ後者とみなすため、候補を覚えておく。
    empty_keys: set[str] = set()
    for raw in m.group(1).splitlines():
        line = raw.rstrip()
        if not line.strip() or line.lstrip().startswith("#"):
            continue

        # ブロック配列の要素 ("  - value")
        if current_list_key and line.lstrip().startswith("- "):
            data[current_list_key].append(_strip_quotes(line.lstrip()[2:].strip()))
            empty_keys.discard(current_list_key)
            continue

        if ":" not in line:
            raise DocMetaError(f"解釈できない行: {raw!r}")

        key, _, rest = line.partition(":")
        key = key.strip()
        # 値に含まれる末尾コメントを落とす (" # ..." の形のみ。URL の # は残す)
        rest = re.split(r"\s+#\s", rest, maxsplit=1)[0].strip()

        if rest == "":
            data[key] = []
            empty_keys.add(key)
            current_list_key = key
        elif rest.startswith("[") and rest.endswith("]"):
            inner = rest[1:-1].strip()
            data[key] = [_strip_quotes(v.strip()) for v in inner.split(",") if v.strip()]
            current_list_key = None
        else:
            data[key] = _strip_quotes(rest)
            current_list_key = None

    # 要素の来なかった空キーは「書き忘れ」として未設定に倒す。空配列のまま残すと、
    # `verified_commit:` (値なし) が「設定済み」に見えて片欠け検査をすり抜ける。
    for key in empty_keys:
        data[key] = None
    return data


def load_docs(root: pathlib.Path) -> list[dict]:
    """対象文書のメタ情報を path 昇順で返す。

    frontmatter を持たない文書は移行途中とみなして黙って除外する
    (#40 の展開が終われば全対象文書が frontmatter を持つ)。
    """
    paths: list[pathlib.Path] = []
    for pattern in TARGET_GLOBS:
        paths.extend(root.glob(pattern))

    docs = []
    for path in sorted(set(paths)):
        rel = path.relative_to(root).as_posix()
        meta = _parse_frontmatter(path.read_text(encoding="utf-8"))
        if meta is None:
            continue

        governs = meta.get("governs")
        verified = meta.get("verified_commit")
        # 片欠けは設定ミス (キー名の typo / 移行時の書き漏れ)。黙って対象外に
        # すると governed な文書が stale 一覧から静かに消えるため error にする。
        if (governs is None) != (verified is None):
            missing = "governs" if governs is None else "verified_commit"
            raise DocMetaError(
                f"{rel}: {missing} が欠けています。"
                "鮮度検査の対象外にするなら governs と verified_commit の両方を外してください"
            )

        if governs is not None:
            # governs: [] や governs: core/foo (スカラー) は「対象だが実質空」に
            # なる。前者は文書が黙って一覧から消え、後者は文字列が 1 文字ずつ
            # パスとして走査される。どちらも設定ミスなので error にする。
            if not isinstance(governs, list) or not governs:
                raise DocMetaError(
                    f"{rel}: governs は 1 件以上のリストで書いてください "
                    "(スカラーや空リストは不可)"
                )
            # verified_commit が HEAD / ブランチ名だと git が毎回解決し直すため、
            # HEAD..HEAD が常に空になって永久に「一致」と報告される。
            if verified != "unverified" and not _is_sha(verified):
                raise DocMetaError(
                    f"{rel}: verified_commit には commit SHA か unverified を書いてください "
                    f"(可変の参照は使えません): {verified!r}"
                )

        # description は索引生成の入力。title で代替すると索引が文書名の羅列に
        # なり「何を読めば足りるか」の判断に使えない (ADR-0008 決定 1)。
        if not meta.get("description"):
            raise DocMetaError(f"{rel}: description が必要です (索引の 1 行説明)")

        docs.append(
            {
                "path": rel,
                "type": meta.get("type", ""),
                "title": meta.get("title", ""),
                "description": meta.get("description", ""),
                "keywords": meta.get("keywords") or [],
                "governs": governs or [],
                "verified_commit": verified,
            }
        )
    return docs


def repo_root() -> pathlib.Path:
    return pathlib.Path(__file__).resolve().parent.parent


def main() -> int:
    """単体実行時は収集結果を人が読める形で出す (デバッグ用)。"""
    try:
        docs = load_docs(repo_root())
    except DocMetaError as e:
        print(f"NG: {e}", file=sys.stderr)
        return 1
    for d in docs:
        scope = ", ".join(d["governs"]) if d["governs"] else "(鮮度検査の対象外)"
        print(f"{d['path']}\n  type={d['type']} verified={d['verified_commit']}\n  governs={scope}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
