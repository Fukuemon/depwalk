#!/usr/bin/env bash
# sdd-template の共有プロセス層が実際に繋がっているかを検査する。
#
# 配布は symlink で、消費 repo には何も commit されない。したがって link.sh を
# 実行していない環境では **AI 設定も hook も存在しない**。存在しないこと自体は
# 想定内だが、それに気づかないまま「hook が効いている」と思い込むのが事故になる。
# 本スクリプトはその状態を明示するためにある。
#
# 終了コード: 0 全て接続済み / 1 一部が切れている / 2 未接続 (link.sh 未実行)
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

EXPECTED=(
  "CLAUDE.md" "AGENTS.md"
  ".claude/skills" ".claude/settings.json"
  "hooks" "templates/adr" "templates/features"
  ".rulesync"
)

linked=0
broken=()   # symlink だが参照先が無い
solid=()    # symlink でない実体がある
absent=()   # 何も無い
for rel in "${EXPECTED[@]}"; do
  if [ -L "$rel" ]; then
    if [ -e "$rel" ]; then
      linked=$((linked + 1))
    else
      broken+=("$rel")
    fi
  elif [ -e "$rel" ]; then
    solid+=("$rel")
  else
    absent+=("$rel")
  fi
done

if [ "$linked" -eq 0 ] && [ "${#broken[@]}" -eq 0 ]; then
  echo "doctor: 未接続 — 共有プロセス層が 1 つも繋がっていません。" >&2
  echo "  AI 設定 / skill / hook はこの環境に存在しません。" >&2
  echo "  接続: sdd-template 側で bash scripts/link.sh $ROOT" >&2
  exit 2
fi

status=0
if [ "${#broken[@]}" -gt 0 ]; then
  echo "doctor: symlink が切れています (${#broken[@]} 件):" >&2
  printf '  %s\n' "${broken[@]}" >&2
  echo "  復旧: sdd-template 側で make sync、または bash scripts/link.sh $ROOT" >&2
  status=1
fi
if [ "${#absent[@]}" -gt 0 ]; then
  echo "doctor: 接続されていない項目があります (${#absent[@]} 件):" >&2
  printf '  %s\n' "${absent[@]}" >&2
  echo "  接続: sdd-template 側で bash scripts/link.sh $ROOT" >&2
  status=1
fi
if [ "${#solid[@]}" -gt 0 ]; then
  echo "doctor: symlink でない実体があります (${#solid[@]} 件):" >&2
  printf '  %s\n' "${solid[@]}" >&2
  echo "  消費 repo 固有の実体なら問題ありません。テンプレ由来なら link.sh を再実行してください。" >&2
  status=1
fi
[ "$status" -eq 0 ] || exit "$status"

echo "doctor: OK (${linked} entries)"
