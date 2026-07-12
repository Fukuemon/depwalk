# Codebase Architecture

> 最終更新: 2026-07-11

コードベースの **package / runtime / state boundary と依存方向**。全体像 (system landscape, モジュール責務) は [design/DesignDoc.md](../design/DesignDoc.md) を正本とし、本書は境界規約を扱う。プロジェクト固有の構成は [context/project.md](project.md) を参照する。
Core 実装基盤の正本は [ADR-0002](../adr/0002-core-implementation-foundation.md)。

## Package Boundary

依存方向は **Core 内は単方向、Core → Analyzer は Protocol 経由のみ** とする (DesignDoc 設計原則 P1〜P4)。

- CLI → Core のみに依存する。
- Core 内: `Traversal Engine` → `Graph Engine` → `Model`、`Output Engine` → `Graph Engine` / `Traversal Engine` / `Model`。Model は他に依存しない。Output → Traversal は Traversal result / request 型の consumer としての依存であり (正本は [Output feature doc](../design/features/output/DesignDoc_output.md))、逆方向 (Traversal → Output) の依存は禁止 (循環禁止)。
- Graph Engine は node / edge の表示用属性 (`Symbol` = qualifiedName / signature / 宣言位置、`CallSite`) を保持し、Protocol wire record → graph 値型の変換は graph 構築時 (Analyze Use Case 層) に 1 回だけ行う。wire 専用フィールド (`schemaVersion` / `recordType`) は graph model に持ち込まない。正本は [Graph feature doc](../design/features/graph/DesignDoc_graph.md)。
- Core → Analyzer は `Analyzer SPI` (Protocol 境界) のみを介する。Core は Analyzer の内部 (使用ライブラリ・言語ランタイム) を知らない。Protocol / SPI / Model schema の正本は [Analyzer Protocol / SPI feature doc](../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md)。
- Analyzer は `Model` (`MethodSymbol` / `CallEdge` / `SourceLocation`) のスキーマにのみ依存する。Core の内部実装には依存しない。
- **禁止経路**: Core から特定言語ランタイム / Analyzer 実装への直接依存。**2 つ目以降**の言語 Analyzer 追加で Core に差分が出ないこと (S5。初号機導入時の言語非依存な初回配線は対象外)。

Go 側 Core の初期 package 境界は次とする。

| Package                   | 責務                                                                      |
| ------------------------- | ------------------------------------------------------------------------- |
| `core/cmd/depwalk`        | `main` と Cobra root command の起動                                       |
| `core/internal/cli`       | CLI command / flags / 入力 validation                                     |
| `core/internal/analyze`   | `depwalk analyze` の use case orchestration                               |
| `core/internal/protocol`  | JSONL record type、Protocol DTO / wire model、parse、validate             |
| `core/internal/analyzer`  | 外部 Analyzer process の起動、stdin / stdout / stderr、exit code handling |
| `core/internal/graph`     | graph 内部 model、node / edge 管理                                        |
| `core/internal/traversal` | caller / callee traversal                                                 |
| `core/internal/output`    | text / JSON / DOT / Mermaid formatter                                     |

言語別 Analyzer 実装は `analyzers/<language>/` に置く。
Java Analyzer 実装は `analyzers/java/` に置き、Core の `internal` package には入れない。
Core と Analyzer の共有境界は Protocol doc、ADR、JSONL fixture、contract test 観点に限定する。
Go package や Java 実装 code を共有しない。

> 依存境界の自動検査は [engineering.md](engineering.md) の quality gate で扱う。

## Runtime Boundary

- **マルチプロセス**: Core と Analyzer は **別プロセス**。STDIN / STDOUT 上の JSONL で通信する (DesignDoc「Communication Protocol」)。
- Core は Go runtime で動く CLI binary として実装する。Analyzer は対象言語のランタイム上で動く (Java Analyzer → JVM)。
- 解析は **静的解析**のみ。実行時情報・runtime trace には依存しない (Non Goals)。

## State Boundary

- 解析対象ソースは **読み取り専用**。depwalk は対象リポジトリを書き換えない。
- 中間状態 (呼び出しグラフ) は Core のプロセス内に保持する。永続ストアは現時点で持たない。
