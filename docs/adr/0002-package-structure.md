# ADR 0002: パッケージ構造の設計

## ステータス

承認済み（Accepted）

## 日付

2026-01-03

## コンテキスト

Go プロジェクトのパッケージ構造には複数のパターンがある：

1. **Standard Go Project Layout**: `cmd/`, `internal/`, `pkg/` の分離
2. **フラット構造**: 最小限のディレクトリ
3. **DDD スタイル**: domain/, application/, infrastructure/

depwalk は以下の要件を持つ：

- CLI ツールとしてのエントリポイント
- 外部依存（tree-sitter, JavaParser）の隔離
- 将来の言語拡張（Kotlin）への対応
- 再利用可能なユーティリティの分離

### パターン比較

```mermaid
graph TB
    subgraph "Standard Layout"
        S_CMD[cmd/]
        S_INT[internal/]
        S_PKG[pkg/]
    end

    subgraph "フラット構造"
        F_ALL[main.go<br/>handler.go<br/>model.go]
    end

    subgraph "DDD スタイル"
        D_DOM[domain/]
        D_APP[application/]
        D_INF[infrastructure/]
    end

    CHOICE[depwalk の選択]

    S_CMD --> CHOICE
    S_INT --> CHOICE
    S_PKG --> CHOICE

    style CHOICE fill:#c8e6c9
```

## 決定

**Standard Go Project Layout をベースに、内部構造をカスタマイズ**する。

### パッケージ構成図

```mermaid
graph TB
    subgraph "cmd/"
        CMD[depwalk/]
    end

    subgraph "internal/"
        MODEL[model/<br/>純粋データ構造]
        PIPELINE[pipeline/<br/>インターフェース + オーケストレーション]
        DRIVER[driver/<br/>言語ドライバ]

        subgraph "infra/"
            TS[treesitter/]
            JH[javahelper/]
            GR[gradle/]
            CA[cache/]
            OUT[output/]
        end
    end

    subgraph "pkg/"
        EXECX[execx/]
        JSONL[jsonl/]
        PATHX[pathx/]
    end

    CMD --> PIPELINE
    CMD --> DRIVER
    PIPELINE --> MODEL
    DRIVER --> PIPELINE
    DRIVER --> TS & JH & GR & CA
    TS & JH & GR & CA & OUT --> MODEL
    TS & JH & GR --> EXECX
    JH --> JSONL

    style MODEL fill:#fff3e0
    style PIPELINE fill:#e1f5fe
    style DRIVER fill:#f3e5f5
```

### パッケージの責務

#### `cmd/depwalk/`

CLI エントリポイント。Cobra を使用したコマンド定義。

```go
// 責務: フラグ解析、ドライバ取得、パイプライン実行
func newCalleesCmd() *cobra.Command {
    // ...
    p := pipeline.NewCalleesPipeline(d.Dependencies(), cfg)
    result, err := p.Run(ctx, selectorRaw)
    // ...
}
```

#### `internal/model/`

純粋なデータ構造。**外部依存なし**。

```mermaid
classDiagram
    class MethodID {
        <<type alias>>
        string
        +String() string
    }

    class DeclRange {
        +File string
        +StartByte uint32
        +EndByte uint32
        +IsZero() bool
    }

    class CallSite {
        +File string
        +StartByte uint32
        +EndByte uint32
        +EnclosingMethodDeclRange DeclRange
        +CalleeName string
        +ArgsCount int
    }

    class ResolvedCall {
        +CalleeMethodID MethodID
        +CallerMethodID MethodID
    }

    class Graph {
        +Nodes map~MethodID~struct
        +Edges map~MethodID~map
        +AddNode(id)
        +AddEdge(from, to)
        +Successors(id) []MethodID
        +Predecessors(id) []MethodID
    }

    class Selector {
        +Raw string
        +Type SelectorType
        +File string
        +Line int
        +Col int
        +MethodName string
    }

    ResolvedCall --|> CallSite : embeds
    CallSite --> DeclRange : contains
    Graph --> MethodID : uses
```

| ファイル      | 内容                        |
| ------------- | --------------------------- |
| `methodid.go` | MethodID 型（安定識別子）   |
| `callsite.go` | CallSite, ResolvedCall      |
| `graph.go`    | Graph（ノード・エッジ）     |
| `selector.go` | Selector, ParseSelector()   |
| `errors.go`   | SelectorError, ResolveError |

#### `internal/pipeline/`

パイプラインのオーケストレーション。**インターフェース定義**を含む。

```mermaid
classDiagram
    class Parser {
        <<interface>>
        +FindEnclosingMethod(ctx, file, line, col) DeclRange, error
        +FindMethodCandidatesByName(ctx, file, name) []DeclRange, error
        +ListCallsInMethod(ctx, decl) []CallSite, error
    }

    class Resolver {
        <<interface>>
        +ResolveDecl(ctx, decl) MethodID, error
        +ResolveCalls(ctx, calls) []ResolvedCall, error
    }

    class Index {
        <<interface>>
        +Build(ctx) error
        +LookupCallSites(ctx, name) []CallSite, error
    }

    class Cache {
        <<interface>>
        +GetResolvedDecl(ctx, key) MethodID, bool, error
        +PutResolvedDecl(ctx, key, val) error
    }

    class CalleesPipeline {
        -deps Dependencies
        -cfg Config
        +Run(ctx, selector) string, error
    }

    class CallersPipeline {
        -deps Dependencies
        -cfg Config
        +Run(ctx, selector) string, error
    }

    class Dependencies {
        +Parser Parser
        +Resolver Resolver
        +Index Index
        +Cache Cache
    }

    CalleesPipeline --> Dependencies
    CallersPipeline --> Dependencies
    Dependencies --> Parser
    Dependencies --> Resolver
    Dependencies --> Index
    Dependencies --> Cache
```

| ファイル     | 内容                                            |
| ------------ | ----------------------------------------------- |
| `stage.go`   | Parser, Resolver, Index, Cache インターフェース |
| `config.go`  | Config 構造体                                   |
| `deps.go`    | Dependencies バンドル                           |
| `callees.go` | CalleesPipeline                                 |
| `callers.go` | CallersPipeline                                 |

#### `internal/infra/`

外部システムとの接続。**pipeline のインターフェースを実装**。

```mermaid
graph LR
    subgraph "pipeline (インターフェース)"
        P_PARSER[Parser]
        P_RESOLVER[Resolver]
        P_CLASSPATH[ClasspathProvider]
        P_CACHE[Cache]
    end

    subgraph "infra (実装)"
        I_TS[treesitter.Parser]
        I_JH[javahelper.Resolver]
        I_GR[gradle.ClasspathProvider]
        I_CA[cache.Cache]
        I_OUT[output.Renderer]
    end

    I_TS -.->|implements| P_PARSER
    I_JH -.->|implements| P_RESOLVER
    I_GR -.->|implements| P_CLASSPATH
    I_CA -.->|implements| P_CACHE

    style P_PARSER fill:#e1f5fe
    style P_RESOLVER fill:#e1f5fe
    style P_CLASSPATH fill:#e1f5fe
    style P_CACHE fill:#e1f5fe
```

| パッケージ    | 実装するインターフェース     |
| ------------- | ---------------------------- |
| `treesitter/` | pipeline.Parser              |
| `javahelper/` | pipeline.Resolver            |
| `gradle/`     | pipeline.ClasspathProvider   |
| `cache/`      | pipeline.Cache               |
| `output/`     | Renderer（出力フォーマット） |

#### `internal/driver/`

言語固有のコンポーネントをバンドル。

```mermaid
classDiagram
    class Driver {
        +Name string
        +Parser pipeline.Parser
        +Resolver pipeline.Resolver
        +Index pipeline.Index
        +Cache pipeline.Cache
        +Classpath pipeline.ClasspathProvider
        +Dependencies() pipeline.Dependencies
    }

    class Registry {
        -registry map~string~Driver
        +Register(name, driver)
        +Get(name) Driver, error
        +List() []string
    }

    Registry --> Driver : manages
    Driver --> Parser : has
    Driver --> Resolver : has
```

```go
type Driver struct {
    Name      string
    Parser    pipeline.Parser
    Resolver  pipeline.Resolver
    Index     pipeline.Index
    Cache     pipeline.Cache
    Classpath pipeline.ClasspathProvider
}
```

#### `pkg/`

**再利用可能**な汎用ユーティリティ。他プロジェクトからも import 可能。

| パッケージ | 内容                           |
| ---------- | ------------------------------ |
| `execx/`   | コマンド実行ヘルパー           |
| `jsonl/`   | JSON Lines エンコード/デコード |
| `pathx/`   | プロジェクトルート探索         |

### internal vs pkg の判断基準

```mermaid
flowchart TD
    START[新しいコード] --> Q1{depwalk 固有?}
    Q1 -->|Yes| INTERNAL[internal/]
    Q1 -->|No| Q2{外部依存あり?}
    Q2 -->|Yes| Q3{インターフェース実装?}
    Q2 -->|No| Q4{純粋データ構造?}
    Q3 -->|Yes| INFRA[internal/infra/]
    Q3 -->|No| INTERNAL
    Q4 -->|Yes| MODEL[internal/model/]
    Q4 -->|No| PKG[pkg/]

    style PKG fill:#c8e6c9
    style MODEL fill:#fff3e0
    style INFRA fill:#f3e5f5
    style INTERNAL fill:#e1f5fe
```

| 条件                               | 配置先            |
| ---------------------------------- | ----------------- |
| depwalk 固有のロジック             | `internal/`       |
| 汎用的で他プロジェクトで再利用可能 | `pkg/`            |
| 外部依存を含む                     | `internal/infra/` |
| 純粋なデータ構造                   | `internal/model/` |

## 結果

### 良い影響

1. **明確な責務分離**: 各パッケージの役割が明確
2. **依存方向の制御**: `internal/` 内での循環依存を防止
3. **再利用性**: `pkg/` の切り出しにより汎用コードを共有可能

### 悪い影響

1. **ディレクトリ階層**: 深いネストがナビゲーションを複雑に

## 参照

- [ADR 0001: ハイブリッド・パイプラインアーキテクチャ](./0001-hybrid-pipeline-architecture.md)
- [Standard Go Project Layout](https://github.com/golang-standards/project-layout)
