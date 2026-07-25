# Codebase Architecture

> 最終更新: 2026-07-26

コードベースの **package / runtime / state boundary と依存方向**。全体像 (system landscape, モジュール責務) は [design/DesignDoc.md](../design/DesignDoc.md) を正本とし、本書は境界規約を扱う。プロジェクト固有の構成は [context/project.yml](project.yml) を参照する。
Core 実装基盤の正本は [ADR-0002](../adr/0002-core-implementation-foundation.md)。

## Package Boundary

依存方向は **Core 内は単方向、Core → Analyzer は Protocol 経由のみ** とする (DesignDoc 設計原則 P1〜P4)。

- CLI → Core のみに依存する。
- Core 内: `Traversal Engine` → `Graph Engine`、`Output Engine` → `Graph Engine` / `Traversal Engine`。Output → Traversal は Traversal result / request 型の consumer としての依存であり (正本は [Output feature doc](../design/features/output/DesignDoc_output.md))、逆方向 (Traversal → Output) の依存は禁止 (循環禁止)。
- Graph Engine は node / edge の表示用属性 (`Symbol` = qualifiedName / signature / optional 宣言位置 / opaque metadata、`CallSite`) を **graph 固有の値型** (wire 非依存の自前 `SourceLocation` 型を含む) で保持する。wire record → domain 値型の変換は `platform` 層の ACL (`protocol`) が担い、Analyze Use Case は port 経由で受領した domain 値を非公開 staging Graph へ 1-pass 登録する。stream 全体の参照完全性の**検査自体は ACL** が行い (wire record を見る責務)、use case はその結果と process 成功を確認したときだけ公開する。fatal 時は Graph と先行 diagnostic を破棄し、wire DTO 全件や wire 専用フィールド (`schemaVersion` / `recordType`) を graph model に保持しない。正本は [Graph feature doc](../design/features/graph/DesignDoc_graph.md)。
- Core → Analyzer は `Analyzer SPI` (Protocol 境界) のみを介する。Core は Analyzer の内部 (使用ライブラリ・言語ランタイム) を知らない。Protocol / SPI / Model schema の正本は [Analyzer Protocol / SPI feature doc](../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md)。
- Analyzer は `Model` (`MethodSymbol` / `CallEdge` / `SourceLocation`) のスキーマにのみ依存する。Core の内部実装には依存しない。
- **禁止経路**: Core から特定言語ランタイム / Analyzer 実装への直接依存。**2 つ目以降**の言語 Analyzer 追加で Core に差分が出ないこと (S5。初号機導入時の言語非依存な初回配線は対象外)。

### Core の package 構成と依存方向 (Go)

`core/internal` 配下は**フラットな責務名 package** で構成する (判断の正本は [ADR-0007](../adr/0007-layered-architecture-refactor.md)、決定経緯は [spec #32](../specs/32-architecture-refactor/index.md) D8)。層 (domain / app / platform 相当) は概念としてのみ維持し、ディレクトリには焼き付けない。

| Package                   | 層 (概念)  | 責務                                                                                      |
| ------------------------- | ---------- | ----------------------------------------------------------------------------------------- |
| `core/cmd/depwalk`        | (cmd)      | `main`。Cobra root command の起動                                                         |
| `core/internal/graph`     | `domain`   | graph model (自前の `Symbol` / `SourceLocation` 値型)、node / edge 管理                   |
| `core/internal/traversal` | `domain`   | caller / callee traversal                                                                 |
| `core/internal/analyze`   | `app`      | `depwalk analyze` の use case orchestration + port interface 定義 (利用側・小さく)        |
| `core/internal/protocol`  | `platform` | JSONL wire DTO / parse / validate + ACL (wire → domain 変換 Translator と port 実装)      |
| `core/internal/analyzer`  | `platform` | 外部 Analyzer process の起動、stdin / stdout / stderr、exit code handling                 |
| `core/internal/output`    | `platform` | text / JSON / DOT / Mermaid formatter (依存先は graph / traversal のみ)                   |
| `core/internal/cli`       | `platform` | CLI command / flags / 入力 validation + 手動 DI 配線 (コンポジションルート、`var _` 集約) |

依存規則 (package 単位。depguard で機械検査する):

- `graph`: 他の internal package に依存しない (wire 表現 `protocol` への import 禁止を含む)
- `traversal` → `graph` のみ
- `analyze` → `graph` / `traversal` のみ。`protocol` / `analyzer` / `output` / `cli` への import 禁止 (抽象は analyze 側の port interface で表現し、`protocol` が実装する)
- `output` → `graph` / `traversal` のみ
- `protocol` → `analyze` (port 実装) / `analyzer` (process 起動に利用) / `graph`
- `analyzer`: 他の internal package に依存しない
- `cli` はコンポジションルートとして全 package を import してよい (依存性ルールの例外ではなく最外層の役割)
- DI ライブラリ (`google/wire` 等) は導入せず、`cli` でのコンストラクタ注入による手動 DI とする

依存図は手で描かず、`go list` の実 import から `scripts/depgraph.sh` で生成して下の生成マーカー区間に埋める。再生成して diff が出る状態 (図の更新漏れ) は lefthook pre-commit と CI が drift として検出する:

<!-- BEGIN GENERATED: core-depgraph (scripts/depgraph.sh が更新する。手編集しない) -->

```mermaid
graph LR
    analyze --> graph & traversal
    cli --> analyze & analyzer & graph & output & protocol
    output --> graph & traversal
    protocol --> analyze & analyzer & graph
    traversal --> graph
```

<!-- END GENERATED: core-depgraph -->

### Java Analyzer の内部境界

`analyzers/java` の `javaanalyzer` 配下は、解析パイプラインの段階別 package (`analysis/` 配下) と入出力・起動系 (`protocol` / `io` / `preflight` / `discovery`) で構成する。段階の実行順は `analysis/pipeline` (Runner) だけが知る。外部ライブラリの隔離は次の 3 段階とする (判断の正本は [ADR-0007](../adr/0007-layered-architecture-refactor.md)):

- **SootUp**: `analysis/sootup` (adapter) に完全に封じ込め、facade が自前型で公開する。他 package から `sootup.*` の import 禁止
- **Gradle Tooling API**: `discovery` に完全隔離 (`org.gradle.tooling.*` は discovery のみ)
- **JavaParser / SymbolSolver**: 解析エンジンの中核として `analysis` 配下では自由に使ってよい。`analysis` の外への import は禁止

言語別 Analyzer 実装は `analyzers/<language>/` に置く。
Java Analyzer 実装は `analyzers/java/` に置き、Core の `internal` package には入れない。
Core と Analyzer の共有境界は Protocol doc、ADR、JSONL fixture、contract test 観点に限定する。
Go package や Java 実装 code を共有しない。

> 依存境界の自動検査 (Go: golangci-lint + depguard、Java: ArchUnit) は [engineering.md](engineering.md) の quality gate で扱う。
> Core (Go) 側の依存方向は #34 で実装済み (実 import は上の生成依存図と一致)。Java Analyzer 側の内部境界は #35 で実装するため、完了までコードは旧配置の場合がある。

## Runtime Boundary

- **マルチプロセス**: Core と Analyzer は **別プロセス**。STDIN / STDOUT 上の JSONL で通信する (DesignDoc「Communication Protocol」)。
- Core は Go runtime で動く CLI binary として実装する。Analyzer は対象言語のランタイム上で動く (Java Analyzer → JVM)。
- 解析は **静的解析**のみ。実行時情報・runtime trace には依存しない (Non Goals)。
- Java Analyzer の source root 自動 discovery 時だけ、条件付き runtime として Gradle Tooling API、対象 build の Gradle daemon、workspace 外の一時 custom model provider が加わる。Gradle build logic は利用者権限で評価され network / credential provider / cache / 任意副作用を持ち得る。明示 `sourceRoots` はこの経路を完全 bypass する。詳細は [Java Analyzer feature doc](../design/features/java-analyzer/DesignDoc_java-analyzer.md)、[infrastructure](infrastructure.md)、[ADR-0006](../adr/0006-adopt-gradle-tooling-api-discovery.md)。Core / Analyzer Protocol の言語非依存境界は変えない。

## State Boundary

- Analyzer 自身は解析対象ソースを **読み取り専用**で扱い、depwalk が対象リポジトリを書き換えない。ただし自動 discovery では対象 build logic の評価に伴う Gradle runtime 全体の副作用を別境界として扱う。
- 中間状態は request 専用の非公開 staging Graph として Core process 内に保持する。成功時だけ公開し、fatal / 非ゼロ exit 時は先行 diagnostic とともに破棄する。永続ストアは現時点で持たない。
