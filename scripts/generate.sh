#!/usr/bin/env bash
# .rulesync/ から各 provider 設定を生成し、provider 別 normalizer を適用する。
# 唯一の生成シーケンス。Makefile / templates/consumer/sdd-template.mk /
# scripts/check-generated.sh はすべて本スクリプトを呼ぶ (手動列挙による同期漏れを防ぐ)。
# 冪等なので何度実行してもよい。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# バージョンは pin する (@latest だと上流リリースで生成物が変わり、repo 側の変更なしに
# drift 検査が壊れる)。更新は動作確認のうえ本行の bump commit で行う。
npx rulesync@14.2.0 generate
bash "$ROOT/scripts/fix-cursor-cli.sh"
bash "$ROOT/scripts/fix-codex-config.sh"
bash "$ROOT/scripts/fix-claude-settings.sh"
