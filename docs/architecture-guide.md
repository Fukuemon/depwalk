# depwalk アーキテクチャガイドライン

このドキュメントは、depwalk の開発者向けにアーキテクチャの概要と開発ガイドラインを提供します。

## 目次

1. [アーキテクチャ概要](#アーキテクチャ概要)
2. [ディレクトリ構成](#ディレクトリ構成)
3. [データフロー](#データフロー)
4. [パッケージ詳細](#パッケージ詳細)
5. [開発ガイドライン](#開発ガイドライン)
6. [新機能の追加方法](#新機能の追加方法)

---

## アーキテクチャ概要

depwalk は**ハイブリッド・パイプラインアーキテクチャ**を採用しています。

- **パイプライン型**: データが `selector → parse → resolve → traverse → render` と一方向に流れる
- **ヘキサゴナル（Ports & Adapters）**: 外部依存をインターフェースで隔離

### なぜこの構成か？

1. **データフローの明確性**: 解析処理のステージが視覚的に理解しやすい
2. **テスタビリティ**: 各ステージを独立してテスト可能
3. **拡張性**: 新しい言語（Kotlin 等）のサポートが容易

詳細は [ADR 0001](./adr/0001-hybrid-pipeline-architecture.md) を参照。

---

## ディレクトリ構成

```
depwalk/
├── cmd/depwalk/           # CLI エントリポイント
├── config/                # アプリケーション設定
│   ├── config.go          # 設定読み込みロジック
│   └── config.yaml        # デフォルト設定ファイル
├── internal/
│   ├── model/             # ドメインモデル（純粋）
│   ├── pipeline/          # パイプライン定義
│   ├── infra/             # 外部依存の実装
│   │   ├── treesitter/
│   │   ├── javahelper/
│   │   ├── gradle/
│   │   ├── cache/
│   │   └── output/
│   └── driver/            # 言語ドライバ
├── pkg/                   # 再利用可能ユーティリティ
│   ├── execx/             # コマンド実行
│   ├── jsonl/             # JSON Lines
│   ├── logger/            # 構造化ログ（slog）
│   └── pathx/             # パス操作
├── java/                  # Java ヘルパー
│   └── depwalk-helper/
└── docs/                  # ドキュメント
    └── adr/               # Architecture Decision Records
```

### 依存関係図

```mermaid
graph TD
    subgraph "cmd"
        CMD[cmd/depwalk]
    end

    CONFIG[config<br/>設定管理]

    subgraph "internal"
        PIPELINE[pipeline<br/>インターフェース定義]
        MODEL[model<br/>純粋データ構造]
        DRIVER[driver<br/>言語ドライバ]

        subgraph "infra"
            TS[treesitter]
            JH[javahelper]
            GR[gradle]
            CA[cache]
            OUT[output]
        end
    end

    subgraph "pkg"
        PKG[execx / jsonl / pathx / logger]
    end

    CMD --> PIPELINE
    CMD --> DRIVER
    PIPELINE --> MODEL
    DRIVER --> PIPELINE
    DRIVER --> TS & JH & GR & CA & OUT
    TS & JH & GR & CA & OUT --> MODEL
    TS & JH & GR & CA & OUT -.->|implements| PIPELINE
    TS & JH & GR --> PKG

    style PIPELINE fill:#e1f5fe
    style MODEL fill:#fff3e0
    style DRIVER fill:#f3e5f5
```

**ルール**:

- `model` は他の internal パッケージに依存しない
- `infra` は `pipeline` のインターフェースを実装
- 循環依存は禁止

---

## データフロー

### callees コマンド

```mermaid
flowchart LR
    subgraph Input
        SEL[Selector<br/>'file:42']
    end

    subgraph "Stage 1: Parse"
        PARSE[Parse<br/>tree-sitter]
    end

    subgraph "Stage 2: Resolve"
        RESOLVE[Resolve<br/>JavaParser]
    end

    subgraph "Stage 3: Traverse"
        TRAVERSE[Traverse<br/>BFS]
    end

    subgraph "Stage 4: Output"
        RENDER[Render<br/>tree/mermaid]
    end

    SEL --> PARSE --> RESOLVE --> TRAVERSE --> RENDER

    style SEL fill:#e8f5e9
    style RENDER fill:#fff3e0
```

### callers コマンド

```mermaid
flowchart LR
    subgraph Input
        SEL[Selector<br/>'file#method']
    end

    subgraph "Stage 1: Parse"
        PARSE[Parse<br/>tree-sitter]
    end

    subgraph "Stage 2: Resolve"
        RESOLVE[Resolve<br/>JavaParser]
    end

    subgraph "Stage 3: Traverse"
        INDEX[Index Lookup<br/>CallNameIndex]
        TRAVERSE[Traverse<br/>Reverse BFS]
    end

    subgraph "Stage 4: Output"
        RENDER[Render<br/>tree/mermaid]
    end

    SEL --> PARSE --> RESOLVE --> INDEX --> TRAVERSE --> RENDER

    style SEL fill:#e8f5e9
    style INDEX fill:#e1f5fe
    style RENDER fill:#fff3e0
```

### 詳細フロー（callees）

```mermaid
sequenceDiagram
    participant CLI as cmd/depwalk
    participant PL as pipeline
    participant TS as treesitter
    participant JH as javahelper
    participant OUT as output

    CLI->>PL: Run(selector)

    Note over PL: Stage 1: Parse Selector
    PL->>PL: ParseSelector("file:42")

    Note over PL,TS: Stage 2: Find Declaration
    PL->>TS: FindEnclosingMethod(file, line, col)
    TS-->>PL: DeclRange

    Note over PL,JH: Stage 3: Resolve Root
    PL->>JH: ResolveDecl(DeclRange)
    JH-->>PL: MethodID

    Note over PL,JH: Stage 4: Traverse (repeat for depth)
    loop depth iterations
        PL->>TS: ListCallsInMethod(decl)
        TS-->>PL: []CallSite
        PL->>JH: ResolveCalls(calls)
        JH-->>PL: []ResolvedCall
        PL->>PL: AddEdges to Graph
    end

    Note over PL,OUT: Stage 5: Render
    PL->>OUT: Render(graph, format)
    OUT-->>PL: string

    PL-->>CLI: output string
```

---

## パッケージ詳細

### パッケージ関係の詳細図

```mermaid
graph TB
    subgraph "cmd/depwalk"
        MAIN[main.go]
        ROOT[root.go]
        CALLEES_CMD[callees.go]
        CALLERS_CMD[callers.go]
    end

    subgraph "internal/pipeline"
        STAGE[stage.go<br/>Parser, Resolver,<br/>Index, Cache]
        CONFIG[config.go]
        DEPS[deps.go]
        CALLEES_PL[callees.go]
        CALLERS_PL[callers.go]
    end

    subgraph "internal/model"
        MID[methodid.go]
        CS[callsite.go]
        GR[graph.go]
        SL[selector.go]
        ERR[errors.go]
        RNG[range.go]
    end

    subgraph "internal/driver"
        DRV[driver.go]
        REG[registry.go]
        JAVA[java.go]
    end

    subgraph "internal/infra"
        TS_IMPL[treesitter/parser.go]
        JH_IMPL[javahelper/resolver.go]
        GR_IMPL[gradle/classpath.go]
        CA_IMPL[cache/cache.go]
        OUT_IMPL[output/renderer.go]
    end

    subgraph "pkg"
        EXECX[execx/exec.go]
        JSONL[jsonl/jsonl.go]
        PATHX[pathx/path.go]
    end

    %% cmd dependencies
    MAIN --> ROOT
    ROOT --> CALLEES_CMD & CALLERS_CMD
    CALLEES_CMD & CALLERS_CMD --> STAGE & CONFIG & DEPS
    CALLEES_CMD & CALLERS_CMD --> DRV

    %% pipeline dependencies
    STAGE --> MID & CS & GR & RNG
    CALLEES_PL & CALLERS_PL --> CONFIG & DEPS & STAGE
    CALLEES_PL & CALLERS_PL --> MID & CS & GR & SL & ERR

    %% driver dependencies
    DRV --> STAGE
    JAVA --> TS_IMPL & JH_IMPL & GR_IMPL & CA_IMPL

    %% infra dependencies
    TS_IMPL -.->|implements| STAGE
    JH_IMPL -.->|implements| STAGE
    GR_IMPL -.->|implements| STAGE
    CA_IMPL -.->|implements| STAGE
    OUT_IMPL --> MID & GR

    %% pkg usage
    GR_IMPL --> EXECX
    JH_IMPL --> JSONL

    style STAGE fill:#e1f5fe
    style MID fill:#fff3e0
    style DRV fill:#f3e5f5
```

### `internal/model/`

純粋なデータ構造を定義。**外部依存なし**。

```go
// MethodID - 安定したメソッド識別子
type MethodID string  // "com.example.Foo#bar(int,String)"

// CallSite - 呼び出し箇所（未解決）
type CallSite struct {
    File      string
    StartByte uint32
    EndByte   uint32
    CalleeName string
    // ...
}

// Graph - 呼び出しグラフ
type Graph struct {
    Nodes map[MethodID]struct{}
    Edges map[MethodID]map[MethodID]struct{}
}
```

### `internal/pipeline/`

パイプラインのインターフェース定義とオーケストレーション。

```go
// stage.go - インターフェース
type Parser interface {
    FindEnclosingMethod(ctx, file, line, col) (DeclRange, error)
    FindMethodCandidatesByName(ctx, file, name) ([]DeclRange, error)
    ListCallsInMethod(ctx, decl) ([]CallSite, error)
}

type Resolver interface {
    ResolveDecl(ctx, decl) (MethodID, error)
    ResolveCalls(ctx, calls) ([]ResolvedCall, error)
}

// callees.go - パイプライン実装
type CalleesPipeline struct {
    deps Dependencies
    cfg  Config
}

func (p *CalleesPipeline) Run(ctx, selector) (string, error) {
    // 1. Parse selector
    // 2. Find declaration
    // 3. Resolve root
    // 4. Traverse callees
    // 5. Render output
}
```

### `internal/infra/`

外部システムとの接続。`pipeline` のインターフェースを実装。

| パッケージ    | 責務                            |
| ------------- | ------------------------------- |
| `treesitter/` | tree-sitter による高速 AST 解析 |
| `javahelper/` | JavaParser による厳密な型解決   |
| `gradle/`     | Gradle からの classpath 取得    |
| `cache/`      | 解決結果のキャッシュ            |
| `output/`     | tree/mermaid 出力フォーマット   |

### `internal/driver/`

言語固有のコンポーネントをバンドル。

```go
type Driver struct {
    Name      string
    Parser    pipeline.Parser
    Resolver  pipeline.Resolver
    Index     pipeline.Index
    Cache     pipeline.Cache
    Classpath pipeline.ClasspathProvider
}

// Dependencies() で pipeline.Dependencies に変換
func (d Driver) Dependencies() pipeline.Dependencies { ... }
```

### `pkg/`

再利用可能なユーティリティ。depwalk 固有でない汎用コード。

| パッケージ | 内容                            |
| ---------- | ------------------------------- |
| `execx/`   | `exec.Command` のラッパー       |
| `jsonl/`   | JSON Lines エンコード/デコード  |
| `logger/`  | 構造化ログ（`log/slog` ベース） |
| `pathx/`   | プロジェクトルート探索          |

### `config/`

アプリケーション設定の管理。**YAML ファイル + 環境変数** からの設定読み込みを提供。

```go
// config/config.go
type Config struct {
    Logger logger.Config `yaml:"logger"`
}

// 使用例
cfg, err := config.NewConfig[config.Config]()
```

設定の優先順位:

1. 環境変数（最優先）
2. `config/config.yaml`
3. デフォルト値

環境変数の例:

| 環境変数          | 説明                  | デフォルト |
| ----------------- | --------------------- | ---------- |
| `LOG_LEVEL`       | ログレベル            | `info`     |
| `LOG_ADD_SOURCE`  | ソース情報を含めるか  | `true`     |
| `LOG_JSON_FORMAT` | JSON 形式で出力するか | `true`     |

---

## 開発ガイドライン

### 1. 依存の方向を守る

```mermaid
graph LR
    subgraph "✅ 正しい依存方向"
        A1[pipeline] --> B1[model]
        A2[infra] --> B2[model]
        A3[infra] -.->|implements| B3[pipeline]
        A4[driver] --> B4[pipeline]
        A5[driver] --> B5[infra]
    end
```

```mermaid
graph LR
    subgraph "❌ 禁止される依存"
        X1[model] -->|禁止| Y1[pipeline]
        X2[model] -->|禁止| Y2[infra]
        X3[pipeline] -->|禁止| Y3[infra具体実装]
    end

    style X1 fill:#ffcdd2
    style X2 fill:#ffcdd2
    style X3 fill:#ffcdd2
    style Y1 fill:#ffcdd2
    style Y2 fill:#ffcdd2
    style Y3 fill:#ffcdd2
```

### 2. インターフェースは pipeline に定義

外部依存を抽象化するインターフェースは `pipeline/stage.go` に集約。

```go
// ❌ 悪い例: infra にインターフェースを定義
package treesitter
type Parser interface { ... }

// ✅ 良い例: pipeline にインターフェースを定義
package pipeline
type Parser interface { ... }
```

### 3. model は純粋に保つ

`model` パッケージには IO やフレームワーク依存を入れない。

```go
// ❌ 悪い例: model で IO
package model
func (g *Graph) SaveToFile(path string) error { ... }

// ✅ 良い例: IO は infra で
package cache
func SaveGraph(path string, g *model.Graph) error { ... }
```

### 4. エラーは適切な粒度で

```go
// ❌ 悪い例: 汎用的すぎる
return errors.New("failed")

// ✅ 良い例: 構造化エラー
return &model.SelectorError{
    Kind:     model.SelectorErrorNotFound,
    Selector: raw,
    Message:  "method not found",
}
```

### 5. Context を伝播する

すべての外部呼び出しに `context.Context` を渡す。

```go
func (p *Parser) FindEnclosingMethod(ctx context.Context, ...) { ... }
```

---

## 新機能の追加方法

### 新しい出力フォーマットを追加

```mermaid
flowchart TD
    A[1. output/renderer.go に Format 追加] --> B[2. Render に分岐追加]
    B --> C[3. renderXxx メソッド実装]

    subgraph "コード変更"
        D["const FormatJSON Format = 'json'"]
        E["case FormatJSON:<br/>  return r.renderJSON(...)"]
    end

    A --> D
    B --> E
```

```go
const FormatJSON Format = "json"

func (r *Renderer) Render(...) {
    switch format {
    case FormatJSON:
        return r.renderJSON(root, g, direction), nil
    // ...
    }
}
```

### 新しい言語をサポート

```mermaid
flowchart TD
    A[1. infra/ に言語固有実装追加] --> B[2. driver/ にドライバ追加]
    B --> C[3. RegisterDefaults で登録]

    subgraph "追加するファイル"
        D["infra/kotlin/parser.go"]
        E["infra/kotlinhelper/resolver.go"]
        F["driver/kotlin.go"]
    end

    A --> D & E
    B --> F
```

```go
// internal/driver/kotlin.go
func RegisterKotlin() {
    d := Driver{
        Name:     "kotlin",
        Parser:   kotlin.NewParser(),
        Resolver: kotlinhelper.NewResolver(""),
        // ...
    }
    Register("kotlin", d)
}
```

### 新しいパイプラインステージを追加

```mermaid
flowchart TD
    A[1. pipeline/stage.go に<br/>インターフェース追加] --> B[2. infra/ に実装追加]
    B --> C[3. パイプラインに統合]

    subgraph "例: Validator ステージ"
        D["type Validator interface {<br/>  Validate(ctx, graph) error<br/>}"]
        E["infra/validator/validator.go"]
        F["callees.go の traverse 後に<br/>validation 呼び出し"]
    end

    A --> D
    B --> E
    C --> F
```

---

## 参照ドキュメント

- [ADR 0001: ハイブリッド・パイプラインアーキテクチャ](./adr/0001-hybrid-pipeline-architecture.md)
- [ADR 0002: パッケージ構造の設計](./adr/0002-package-structure.md)
- [Design Doc](../design/)
