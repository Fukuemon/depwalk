# depwalk

ソースコードの静的解析でメソッド間の **呼び出し関係 (caller / callee)** を抽出し、**変更影響調査**を支援する CLI ツール。「あるメソッドを直したいが、どこから呼ばれ・どこを呼んでいるか」を手作業で追う負荷を自動化し、CI 上でも実行できる形で提供する。

> **Status: 設計フェーズ (Draft)** — 設計の正本は [design/DesignDoc.md](design/DesignDoc.md)。実装は未着手で、ビルド/テストのコマンドは未確定 ([context/project.md](context/project.md) 参照)。

## なぜ作るか (Why)

大規模・業務システムではメソッド変更時の影響範囲調査が大きなコストになっている。

- 呼び出し元が Controller / Batch / Facade / Test に散らばり、手作業の洗い出しに時間がかかる
- IDE の Call Hierarchy はファイル/モジュール単位に強い一方、プロジェクト全体の横断的・網羅的な抽出には向かない
- Spring の DI (interface 経由・Bean 注入) で、コード上の呼び出し先と実体が一致せず静的に追いづらい
- IDE 依存の調査は人手前提で、CI 上で影響範囲を自動レポートできない

depwalk はこの調査を静的解析で自動化し、CLI として CI に組み込める形で提供する。

## できること (What)

- 指定メソッドの **呼び出し元 (caller) を再帰探索** / **呼び出し先 (callee) を探索**
- 呼び出しグラフを **Console / JSON / DOT / Mermaid** で出力 (グラフのビューワ自体は提供しない)
- 言語別 **Analyzer をプラグインとして追加**できる Core / Protocol

Phase1 は **Java / Spring Boot** を対象 (JavaParser ベース)。Kotlin / TypeScript / Vue / Go は将来対象。

### 対象外 (Non Goals)

Runtime Trace / APM などの実行時計測、Reflection / AspectJ Runtime / 実行時 Proxy の動的解析、IDE Plugin / Web UI の提供。本ツールは CLI に限定する。

## アーキテクチャ

呼び出しグラフの構築・探索・出力を担う **Core を言語非依存**に保ち、言語ごとの解析差異を独立プロセスの **Analyzer** に閉じ込める。両者は共通データモデル (`MethodSymbol` / `CallEdge` / `SourceLocation`) を **JSONL (STDIN/STDOUT)** で受け渡すのみで結合するため、新しい言語の Analyzer を Core 変更なしに追加できる。

```text
ユーザー / CI
    └─ CLI ── Core (言語非依存: Graph / Traversal / Output)
                 └─ Analyzer SPI ⇄ JSONL ⇄ Java Analyzer (独立プロセス: JavaParser / SymbolSolver / SootUp)
                                                              └─ Java / Spring ソース (read-only)
```

詳細は [design/DesignDoc.md](design/DesignDoc.md) (C4 L1/L2・モジュール責務・Communication Protocol) を参照。

## 設計フェーズの進行

feature 単位で設計を issue 駆動で進めている。

| ドメイン            | 内容                                       | 設計 issue |
| ------------------- | ------------------------------------------ | ---------- |
| `traversal`         | Caller / Callee 探索 (Traversal Engine)    | #6         |
| `output`            | 出力形式 (Console / JSON / DOT / Mermaid)  | #7         |
| `analyzer-protocol` | Analyzer SPI + Protocol + Model (共通契約) | #8         |
| `java-analyzer`     | Java/Spring 解析の言語別実装               | #9         |

各 issue の要求は [specs/](specs/)、未決の論点は DesignDoc の Open Questions (Q1〜Q4) で管理する。

## ドキュメント構成

本リポジトリは Spec Driven Development (SDD) テンプレートを土台に運用する。正本は層ごとに分かれる。

| 層                | 文書                                       | 役割                                             |
| ----------------- | ------------------------------------------ | ------------------------------------------------ |
| Why / What + How  | [design/DesignDoc.md](design/DesignDoc.md) | 統合モード: Why/What を内包した system landscape |
| How (feature)     | [design/features/](design/features/)       | feature 単位の設計 (未着手)                      |
| How (規約 / 契約) | [context/](context/)                       | 技術規約・codebase architecture・運用契約        |
| 固有値            | [context/project.md](context/project.md)   | repo / 命名 / コマンド / 対象ドメイン / ラベル   |
| 意思決定          | [adr/](adr/)                               | 長期参照する技術選定・境界 (未着手)              |
| 作業文書          | [specs/](specs/)                           | issue / 機能単位の要求・設計・テスト観点         |

> 統合モードのため独立した `PRD.md` は作らず、Why/What は DesignDoc の「## Why / What」節を正本とする。
> AI エージェントの操作契約は [CLAUDE.md](CLAUDE.md) / [AGENTS.md](AGENTS.md) (正本は [.rulesync/](.rulesync/)、`rulesync` で各 provider へ生成)。

## ディレクトリ

```text
design/      # Design Doc (landscape) と feature 設計
context/     # 技術規約・運用契約 + project.md (固有値・Label Policy)
specs/       # 設計フェーズの requirements / spec (issue 単位)
adr/         # Architecture Decision Records
templates/   # 各文書のテンプレート
.rulesync/   # AI 設定の単一情報源 (rules / skills / hooks / permissions / mcp)
hooks/       # protected branch / 文書検証 / markdown 整形などの hook
```
