# depwalk

ソースコードの静的解析でメソッド間の **呼び出し関係 (caller / callee)** を抽出し、**変更影響調査**を支援する CLI ツール。「あるメソッドを直したいが、どこから呼ばれ・どこを呼んでいるか」を手作業で追う負荷を自動化し、CI 上でも実行できる形で提供する。

> **Status: Java / Spring Boot 向けの中核機能まで実装済み。** Core (Go) と Java Analyzer が動作し、CI で unit / E2E テストが回っている。次に進める対象は GitHub の issue で管理する。

## なぜ作るか (Why)

大規模・業務システムではメソッド変更時の影響範囲調査が大きなコストになっている。

- 呼び出し元が Controller / Batch / Facade / Test に散らばり、手作業の洗い出しに時間がかかる
- IDE の Call Hierarchy はファイル/モジュール単位に強い一方、プロジェクト全体の横断的・網羅的な抽出には向かない
- Spring の DI (interface 経由・Bean 注入) で、コード上の呼び出し先と実体が一致せず静的に追いづらい
- IDE 依存の調査は人手前提で、CI 上で影響範囲を自動レポートできない

depwalk はこの調査を静的解析で自動化し、CLI として CI に組み込める形で提供する。

## できること (What)

- 指定メソッドの **呼び出し元 (caller) を再帰探索** / **呼び出し先 (callee) を探索**
- 呼び出しグラフを **Console / JSON** で出力
- 言語別 **Analyzer をプラグインとして追加**できる Core / Protocol

対象は **Java / Spring Boot**。interface 越しの呼び出しは SootUp の型階層と Spring の DI 情報で実装候補まで解決する。Kotlin / TypeScript / Vue / Go は将来対象。

### 対象外 (Non Goals)

Runtime Trace / APM などの実行時計測、Reflection / AspectJ Runtime / 実行時 Proxy の動的解析、IDE Plugin / Web UI の提供。本ツールは CLI に限定する。

グラフを図として描く形式 (DOT / Mermaid 等) は現時点で対象外とし、形式を決めないまま将来の課題として残す。

- [ADR-0008](adr/0008-defer-graph-visualization.md) — 可視化出力をスコープから外し、解析精度と永続化を優先した決定

## 使い方

Java Analyzer の jar と Core CLI をビルドしてから、Core CLI に解析させる。コマンドはリポジトリのルートで実行する。

```sh
# Analyzer の jar と Core CLI をビルドする
(cd analyzers/java && ./gradlew shadowJar)
(cd core && go build -o ../dist/depwalk ./cmd/depwalk)

# 呼び出し元を探索する
./dist/depwalk analyze <workspace-root> \
  --language java \
  --analyzer-cmd "java -jar analyzers/java/build/libs/java-analyzer.jar" \
  --method "com.example.UserService#findById(java.lang.Long)" \
  --direction caller \
  --format json
```

`--source-root` を省略すると Gradle の build model から source root と classpath を自動で取得する。明示すると自動の取得を完全に bypass する。

- [context/tech-stack.md](context/tech-stack.md#toolchain) — 開発、ビルド、チェックの主なコマンド

## アーキテクチャ

呼び出しグラフの構築・探索・出力を担う **Core を言語非依存**に保ち、言語ごとの解析差異を独立プロセスの **Analyzer** に閉じ込める。両者は共通データモデル (`MethodSymbol` / `CallEdge` / `SourceLocation`) を **JSONL (STDIN/STDOUT)** で受け渡すのみで結合する。このため、新しい言語の Analyzer を Core 変更なしに追加できる。

```text
ユーザー / CI
    └─ CLI ── Core (言語非依存: Graph / Traversal / Output)
                 └─ Analyzer SPI ⇄ JSONL ⇄ Java Analyzer (独立プロセス: JavaParser / SymbolSolver / SootUp)
                                                              └─ Java / Spring ソース (read-only)
```

C4 の L2 の図、モジュールの責務、Core の package の依存図は Design Doc にある。

- [design/DesignDoc.md](design/DesignDoc.md) — 全体の構成とモジュールの責務

## ドキュメント構成

文書は種類ごとに分かれる。

| 文書           | 置き場                                                                    | 役割                                                        |
| -------------- | ------------------------------------------------------------------------- | ----------------------------------------------------------- |
| PRD            | [PRD.md](PRD.md)                                                          | 誰のどの課題を何で解決するか                                |
| Design Doc     | [design/DesignDoc.md](design/DesignDoc.md)                                | 全体の構成、モジュールの責務と依存                          |
| 機能ごとの設計 | 対象のコードの隣の `DesignDoc.md`（例: `core/internal/cli/DesignDoc.md`） | 機能の内部の構成と処理の流れ                                |
| context        | [context/](context/)                                                      | 取り決めと契約。目次は [context/index.md](context/index.md) |
| ADR            | [adr/](adr/)                                                              | 選択肢を比べて決めた判断                                    |
| spec           | [specs/](specs/)                                                          | issue ごとの要求と設計。issue を閉じるときに削除する        |

どこから読むかが分からないときは [context/index.md](context/index.md) を読む。

Design Doc と context は、frontmatter に `governs`（その文書が語る実装の範囲）と `verified_commit`（最後に実装と読み比べたコミット）を持つ。
書き方の規則は [context/conventions.md](context/conventions.md#documents) にあり、文書のチェックの実行方法は [CONTRIBUTING.md](CONTRIBUTING.md) にある。

> AI エージェントの規約、スキル、playbook は、agent-harness と agent-playbooks を apm で導入する（`apm.yml`）。配置先は Git で追跡しない。手順は [CONTRIBUTING.md](CONTRIBUTING.md) にある。

## ディレクトリ

```text
PRD.md       # PRD
core/        # Core CLI (Go) — graph / traversal / output / protocol / analyzer / cli
analyzers/   # 言語別 Analyzer — java/ (JavaParser / SymbolSolver / SootUp)
design/      # 全体の Design Doc
context/     # 取り決めと契約
adr/         # Architecture Decision Records
specs/       # issue 単位の作業文書 (close 時に削除する)
testdata/    # JSONL contract fixture / E2E fixture
scripts/     # 依存図の生成、golangci-lint の起動、specs の残りの検査
hooks/       # protected branch と commit message の Git hook
```
