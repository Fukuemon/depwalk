#!/bin/bash

# commit 時にステージ済ファイルを Prettier で整形する pre-commit 用スクリプト。
# 消費プロダクト側の git hook マネージャ (lefthook / husky 等) から呼び出す。
# 編集時 (Claude Code Edit/Write) の md 整形は hooks/markdown/format_markdown.sh が担う。

set -euo pipefail

STAGED_FILES=()

while IFS= read -r file; do
  case "$file" in
    *.js|*.cjs|*.mjs|*.jsx|*.ts|*.tsx|*.json|*.md|*.css|*.yml|*.yaml)
      STAGED_FILES+=("$file")
      ;;
  esac
done < <(git diff --cached --name-only --diff-filter=ACMR)

if [ "${#STAGED_FILES[@]}" -eq 0 ]; then
  exit 0
fi

npx prettier --write "${STAGED_FILES[@]}"
git add -- "${STAGED_FILES[@]}"
