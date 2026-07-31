# Engineering Conventions

> 最終更新: 2026-07-26

shared config / root task / repository quality gate の境界規約。toolchain 一覧は [toolchain.md](toolchain.md)、プロジェクト固有コマンドは [context/project.yml](project.yml)。

Core 実装基盤の正本は [ADR-0002](../adr/0002-core-implementation-foundation.md)。

## Code Comment Boundary

- **コード内コメントの言語は現状 module ごとに異なる**。Core (Go) は英語 (godoc 規約に合わせた)、Java Analyzer は日本語 (設計文書と同じ語彙で解析の意味論を書けるため)。日本語への統一を [issue #40](https://github.com/Fukuemon/depwalk/issues/40) で検討中で、決まり次第この項を改訂する。ドキュメント・commit・PR は日本語。ユーザーに見える文字列リテラル (CLI 出力等) は言語を変えない — 観測可能な契約であり golden test が固定している。
- **コメントから spec / issue を引用しない** (`spec #32 D6` / `P2_01` / `D21` 等)。spec は issue close 時に削除される作業文書なので、コードから参照すると宙に浮いたリンクが残る。決定の経緯は git history と PR で辿る。
- 理由をコメントに残すときのリンク先は **ADR と durable な正本ドキュメント** (`adr/*.md`、`context/*.md`、`design/features/*/DesignDoc_*.md`) に限る。
- `(S5)` のような符号だけの参照はしない。読み手に伝わる言葉で書き、必要なら正本へのリンクを添える。

## Error Boundary

- **エラーメッセージの package prefix はユーザーへの到達可否で決める**。`traversal:` / `output:` / `analyze:` のような prefix は「呼び出し側の実装ミス」を表す内部不変条件の違反にだけ付ける (これらは cli が事前検証しており、利用者には到達しない)。利用者の stderr に出るメッセージには内部 package 名を出さない。
- **exit code への分類は cli が決める**。内層は「利用者の入力が原因」であることを型で表明するだけにし、exit code の値を知らない (`cli.ExitCode` が `analyze.InputError` と cli 自身の入力エラーの双方を 2 へ写す)。
- **プロセスの結果を struct フィールドの `error` で運ぶときは、判定を型のメソッドへ畳む**。`analyze.Outcome` は fatal record / 非ゼロ exit / stdout 検証エラーの 3 つを持つが、優先順位 (fatal の理由を検証エラーで上書きしない) を呼び出し側に再実装させないよう `Outcome.Err()` に集約する。
- 型で分類する必要があるエラーは struct として定義し、`errors.As` で検査できる状態を保つ (例: `protocol.ValidationError` は contract test が型として検証している)。単に文言を組み立てるだけなら `fmt.Errorf` でよい。

## Shared Config Boundary

- Core の初期 shared config は Go 標準 command を優先し、専用 config を増やさない。
- `golangci-lint` は依存方向検査 (depguard) の要件化 ([issue #32](https://github.com/Fukuemon/depwalk/issues/32)) に伴い導入済み。設定は `core/.golangci.yml` (有効な linter は depguard のみ)、実行の入口は `scripts/golangci-lint.sh` に一本化し、lefthook と CI は同じスクリプトを呼ぶ。バージョンはこのスクリプト内で pin する: 固定手段に `go run <pkg>@<version>` を使い、`go.mod` の tool directive にはしない (linter の依存木が production module の `go.mod` / `go.sum` へ入るのを避けるため。ADR-0002 の依存最小方針)。`govulncheck`、release automation の設定は、CI gate または release 手順が要件化した時点で追加する。
- 現状の共有契約はドキュメント正本パス ([project.yml](project.yml) Source of Truth) と AI 設定 (`.rulesync/` → 各 provider 生成)。

## Root Task Boundary

- commit 前検査は `lefthook` (pre-commit hook) が束ねる。設定は repo root の `lefthook.yml`。
- Core の初期 root task は repository-level wrapper ではなく、`core/` 配下で実行する Go 標準 command とする。
- make-like wrapper は、複数 module、Analyzer build、CI matrix、release command を 1 command に束ねる必要が出た時点で導入を検討する。Java Analyzer は `analyzers/java/` の `gradlew` で完結し、現時点で repository-level wrapper は導入しない (再評価条件は上記のまま)。

## Repository Quality Gate

- 現状の gate: Markdown / ドキュメント整合 (lefthook 経由)。AI 設定は `.rulesync/` を正本とし、生成物 (`AGENTS.md` / `CLAUDE.md` / `.codex/` / `.claude/` / `.cursor/`) の直接編集を禁止する。
- Core 実装後の最小 gate: `cd core && go test ./...`、`cd core && go vet ./...`、`cd core && test -z "$(gofmt -l .)"`、`cd core && go mod tidy` 後の差分確認。
- 依存境界 gate: Core から `analyzers/<language>/` や特定 Analyzer runtime へ直接依存しないことを CI の Go job で検査する (`go list -deps ./...` に `analyzers/` が含まれないこと。`.github/workflows/ci.yml`)。
- 依存方向 gate (Go): `core/internal` の package 単位の禁止 import (例: `graph` / `traversal` / `analyze` / `output` → `protocol` / `cli` 等) を golangci-lint + depguard で検査し、lefthook pre-commit / CI に組み込む (#34 で実装済み)。ルールは `files` (package の glob) + `deny` + `desc` (違反理由) の宣言形式で書く。違反時は `desc` の理由が表示されるので、開発者・AI エージェントはメッセージだけで是正できる。依存規則の正本は [architecture.md](architecture.md) の Package Boundary (導入判断は [ADR-0007](../adr/0007-layered-architecture-refactor.md)、フラット構成維持は [issue #32](https://github.com/Fukuemon/depwalk/issues/32))。あわせて依存図の生成 (`scripts/depgraph.sh` → architecture.md の生成マーカー区間) と再生成 drift 検査を同 gate に含める。
- 依存方向 gate (Java): 外部ライブラリ隔離 (`sootup.*` は `analysis/sootup` のみ / `org.gradle.*` は `discovery` のみ / `com.github.javaparser.*` は `analysis` 配下のみ) を ArchUnit の JUnit テストで検査する (`ArchitectureTest`)。既存の `./gradlew test` (quality gate 組み込み済み) で実行され、新しい gate 配線は不要。Gradle は `org.gradle.tooling.*` ではなく `org.gradle.*` 全体を禁止する: gradle-tooling-api の jar は `org.gradle.api` / `util` / `internal` も同梱するため、tooling 配下だけの禁止では隔離をすり抜ける (#35 実測)。
