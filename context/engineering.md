# Engineering Conventions

> 最終更新: 2026-07-26

shared config / root task / repository quality gate の境界規約。toolchain 一覧は [toolchain.md](toolchain.md)、プロジェクト固有コマンドは [context/project.yml](project.yml)。

Core 実装基盤の正本は [ADR-0002](../adr/0002-core-implementation-foundation.md)。

## Shared Config Boundary

- Core の初期 shared config は Go 標準 command を優先し、専用 config を増やさない。
- `golangci-lint` は依存方向検査 (depguard) の要件化 (spec #32) に伴い導入済み。設定は `core/.golangci.yml` (有効な linter は depguard のみ)、実行の入口は `scripts/golangci-lint.sh` に一本化し、lefthook と CI は同じスクリプトを呼ぶ。バージョンはこのスクリプト内で pin する: 固定手段に `go run <pkg>@<version>` を使い、`go.mod` の tool directive にはしない (linter の依存木が production module の `go.mod` / `go.sum` へ入るのを避けるため。ADR-0002 の依存最小方針)。`govulncheck`、release automation の設定は、CI gate または release 手順が要件化した時点で追加する。
- 現状の共有契約はドキュメント正本パス ([project.yml](project.yml) Source of Truth) と AI 設定 (`.rulesync/` → 各 provider 生成)。

## Root Task Boundary

- commit 前検査は `lefthook` (pre-commit hook) が束ねる。設定は repo root の `lefthook.yml`。
- Core の初期 root task は repository-level wrapper ではなく、`core/` 配下で実行する Go 標準 command とする。
- make-like wrapper は、複数 module、Analyzer build、CI matrix、release command を 1 command に束ねる必要が出た時点で導入を検討する。Java Analyzer は `analyzers/java/` の `gradlew` で完結し、現時点で repository-level wrapper は導入しない (再評価条件は上記のまま)。

## Repository Quality Gate

- 現状の gate: Markdown / ドキュメント整合 (lefthook 経由)。AI 設定は `.rulesync/` を正本とし、生成物 (`AGENTS.md` / `CLAUDE.md` / `.codex/` / `.claude/` / `.cursor/`) の直接編集を禁止する。
- Core 実装後の最小 gate: `cd core && go test ./...`、`cd core && go vet ./...`、`cd core && test -z "$(gofmt -l .)"`、`cd core && go mod tidy` 後の差分確認。
- 依存境界 gate: Core から `analyzers/<language>/` や特定 Analyzer runtime へ直接依存しないことを CI の Go job で検査する (`go list -deps ./...` に `analyzers/` が含まれないこと。`.github/workflows/ci.yml`)。
- 依存方向 gate (Go): `core/internal` の package 単位の禁止 import (例: `graph` / `traversal` / `analyze` / `output` → `protocol` / `cli` 等) を golangci-lint + depguard で検査し、lefthook pre-commit / CI に組み込む (#34 で実装済み)。ルールは `files` (package の glob) + `deny` + `desc` (違反理由) の宣言形式で書く。違反時は `desc` の理由が表示されるので、開発者・AI エージェントはメッセージだけで是正できる。依存規則の正本は [architecture.md](architecture.md) の Package Boundary (導入判断は [ADR-0007](../adr/0007-layered-architecture-refactor.md)、フラット構成維持は spec #32 D8)。あわせて依存図の生成 (`scripts/depgraph.sh` → architecture.md の生成マーカー区間) と再生成 drift 検査を同 gate に含める。
- 依存方向 gate (Java): 外部ライブラリ隔離 (`sootup.*` は `analysis/sootup` のみ / `org.gradle.tooling.*` は `discovery` のみ / `com.github.javaparser.*` は `analysis` 配下のみ) を ArchUnit の JUnit テストで検査する。既存の `./gradlew test` (quality gate 組み込み済み) で実行され、新しい gate 配線は不要。
