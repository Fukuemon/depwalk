# Engineering Conventions

> 最終更新: 2026-07-11

shared config / root task / repository quality gate の境界規約。toolchain 一覧は [toolchain.md](toolchain.md)、プロジェクト固有コマンドは [context/project.yml](project.yml)。

Core 実装基盤の正本は [ADR-0002](../adr/0002-core-implementation-foundation.md)。

## Shared Config Boundary

- Core の初期 shared config は Go 標準 command を優先し、専用 config を増やさない。
- `golangci-lint`、`govulncheck`、release automation の設定は、CI gate または release 手順が要件化した時点で追加する。
- 現状の共有契約はドキュメント正本パス ([project.yml](project.yml) Source of Truth) と AI 設定 (`.rulesync/` → 各 provider 生成)。

## Root Task Boundary

- commit 前検査は `lefthook` (pre-commit hook) が束ねる。設定は repo root の `lefthook.yml`。
- Core の初期 root task は repository-level wrapper ではなく、`core/` 配下で実行する Go 標準 command とする。
- make-like wrapper は、複数 module、Analyzer build、CI matrix、release command を 1 command に束ねる必要が出た時点で導入を検討する。Java Analyzer は `analyzers/java/` の `gradlew` で完結し、現時点で repository-level wrapper は導入しない (再評価条件は上記のまま)。

## Repository Quality Gate

- 現状の gate: Markdown / ドキュメント整合 (lefthook 経由)。AI 設定は `.rulesync/` を正本とし、生成物 (`AGENTS.md` / `CLAUDE.md` / `.codex/` / `.claude/` / `.cursor/`) の直接編集を禁止する。
- Core 実装後の最小 gate: `cd core && go test ./...`、`cd core && go vet ./...`、`cd core && test -z "$(gofmt -l .)"`、`cd core && go mod tidy` 後の差分確認。
- 依存境界 gate: Core から `analyzers/<language>/` や特定 Analyzer runtime へ直接依存しないことを CI の Go job で検査する (`go list -deps ./...` に `analyzers/` が含まれないこと。`.github/workflows/ci.yml`)。
