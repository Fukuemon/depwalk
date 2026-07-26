#!/usr/bin/env bash
# core/ に対して golangci-lint (depguard による依存方向検査) を実行する。
# lefthook pre-commit と CI が同じ経路を通るための唯一の入口。
#
# バージョンは pin する (@latest だと上流リリースで検査結果が変わり、repo 側の
# 変更なしに gate が壊れる)。更新は動作確認のうえ本行の bump commit で行う。
#
# 固定手段に `go run <pkg>@<version>` を使うのは、core/go.mod を汚さずに
# 再現性を得るため (tool directive にすると linter の依存木が production
# module の go.mod / go.sum へ入り、ADR-0002 の依存最小方針とぶつかる)。
# 初回はビルドが走るが、以後は Go のモジュールキャッシュから再利用される。
set -euo pipefail

GOLANGCI_LINT_VERSION="v2.12.2"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/core"

exec go run "github.com/golangci/golangci-lint/v2/cmd/golangci-lint@${GOLANGCI_LINT_VERSION}" run "$@"
