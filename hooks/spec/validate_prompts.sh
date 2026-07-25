#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "${SCRIPT_DIR}/../lib/tool_use_input.sh"

FILE_PATH="$(resolve_tool_use_file_path || true)"

if [ -z "$FILE_PATH" ]; then
  exit 0
fi

if ! echo "$FILE_PATH" | grep -qE 'specs/[^/]+/prompts/.*\.md$'; then
  exit 0
fi

ERRORS=()

REQUIRED_SECTIONS=(
  "## 絶対ルール"
  "## 作業ステップ"
  "## 実装コンテキスト"
  "## 前提条件"
  "## 不明点ハンドリング"
  "## タスク境界"
  "## 設計仕様"
  "## テスト観点"
  "## 検証コマンド"
  "## 完了条件"
)

for section in "${REQUIRED_SECTIONS[@]}"; do
  if ! grep -qF "$section" "$FILE_PATH" 2>/dev/null; then
    ERRORS+=("必須セクション欠落: $section")
  fi
done

if grep -qF "## タスク境界" "$FILE_PATH" 2>/dev/null; then
  if ! grep -qF '### 実装する範囲' "$FILE_PATH" 2>/dev/null; then
    ERRORS+=("タスク境界に「実装する範囲」がありません")
  fi
  if ! grep -qF '### 実装しない範囲' "$FILE_PATH" 2>/dev/null; then
    ERRORS+=("タスク境界に「実装しない範囲」がありません")
  fi
fi

if grep -qF "## 完了条件" "$FILE_PATH" 2>/dev/null; then
  if ! grep -qE '^\s*- \[ \]' "$FILE_PATH" 2>/dev/null; then
    ERRORS+=("完了条件がチェックリスト形式ではありません")
  fi
fi

if grep -qF "## 絶対ルール" "$FILE_PATH" 2>/dev/null; then
  if ! grep -qF '実装アンチパターンの回避' "$FILE_PATH" 2>/dev/null; then
    ERRORS+=("絶対ルールに実装アンチパターンの回避ブロックがありません (spec-prompts/references/antipatterns.md を注入)")
  fi
fi


if [ ${#ERRORS[@]} -gt 0 ]; then
  echo "" >&2
  echo "============================================================" >&2
  echo "[BLOCKED] spec prompt 品質ゲート不合格" >&2
  echo "ファイル: $FILE_PATH" >&2
  echo "============================================================" >&2
  for err in "${ERRORS[@]}"; do
    echo "- $err" >&2
  done
  echo ".rulesync/skills/spec-prompts/references/prompt-template.md に沿って補完してください。" >&2
  echo "============================================================" >&2
  exit 2
fi

exit 0
