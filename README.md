# depwalk

ソースコードの静的解析でメソッド間の **呼び出し関係 (caller / callee)** を抽出し、**変更影響調査**を支援する CLI ツール。「あるメソッドを直したいが、どこから呼ばれ・どこを呼んでいるか」を手作業で追う負荷を自動化し、CI 上でも実行できる形で提供する。

> **Status: Java / Spring Boot 向けの中核機能まで実装済み。** Core (Go) と Java Analyzer が動作し、CI で unit / E2E テストが回っている。次に進める対象は [DesignDoc の Future Work](design/DesignDoc.md#future-work-rollout-plan) を参照。

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

グラフを図として描く形式 (DOT / Mermaid 等) は現時点で対象外とし、形式を決めないまま将来の課題として残す (判断を定めるのは [ADR-0010](adr/0010-defer-graph-visualization.md))。

## 使い方

Java Analyzer の jar をビルドしてから、Core CLI に解析させる。

```sh
# Analyzer の jar をビルド
cd analyzers/java && ./gradlew shadowJar

# 呼び出し元を探索する
depwalk analyze <workspace-root> \
  --language java \
  --analyzer-cmd "java -jar analyzers/java/build/libs/java-analyzer.jar" \
  --method "com.example.UserService#findById(java.lang.Long)" \
  --direction caller \
  --format json
```

`--source-root` を省略すると Gradle の build model から source root と classpath を自動で取得する。明示するとその経路を完全に bypass する。コマンドを定めるのは [context/project.yml](context/project.yml) の `commands`。

## アーキテクチャ

呼び出しグラフの構築・探索・出力を担う **Core を言語非依存**に保ち、言語ごとの解析差異を独立プロセスの **Analyzer** に閉じ込める。両者は共通データモデル (`MethodSymbol` / `CallEdge` / `SourceLocation`) を **JSONL (STDIN/STDOUT)** で受け渡すのみで結合するため、新しい言語の Analyzer を Core 変更なしに追加できる。

```text
ユーザー / CI
    └─ CLI ── Core (言語非依存: Graph / Traversal / Output)
                 └─ Analyzer SPI ⇄ JSONL ⇄ Java Analyzer (独立プロセス: JavaParser / SymbolSolver / SootUp)
                                                              └─ Java / Spring ソース (read-only)
```

詳細は [design/DesignDoc.md](design/DesignDoc.md) (C4 L1/L2・モジュール責務・Communication Protocol) を参照。

## ドキュメント構成

本リポジトリは Spec Driven Development (SDD) テンプレートを土台に運用する。決まりは層ごとに分かれる。

| 層                | 文書                                       | 役割                                             |
| ----------------- | ------------------------------------------ | ------------------------------------------------ |
| Why / What + How  | [design/DesignDoc.md](design/DesignDoc.md) | 統合モード: Why/What を内包した system landscape |
| How (feature)     | [design/features/](design/features/)       | feature 単位の設計 (6 feature)                   |
| How (規約 / 契約) | [context/](context/)                       | 技術規約・codebase architecture・運用契約        |
| 固有値            | [context/project.yml](context/project.yml) | repo / 命名 / コマンド / 対象ドメイン / ラベル   |
| 意思決定          | [adr/](adr/)                               | 長期参照する技術選定・境界 (ADR-0001〜0010)      |
| 作業文書          | [specs/](specs/)                           | issue 単位の要求・設計 (close 時に削除する)      |

**どこから読むか**が分からないときは [context/reading-map.yaml](context/reading-map.yaml) を引く。触るコードパスから「読むべき文書」を逆引きできる索引で、各文書の frontmatter から生成している。

各文書は frontmatter に `governs` (その文書が語る実装範囲) と `verified_commit` (最後に実装と突き合わせた commit) を持つ。実装が進んで文書が古くなると CI が検出する (定めるのは [ADR-0008](adr/0008-doc-freshness-and-reading-map.md))。

> 統合モードのため独立した `PRD.md` は作らず、Why/What は DesignDoc の「## Why / What」節が定める。
> AI エージェントの操作契約 (`CLAUDE.md` / `AGENTS.md` / `.claude/` など) は sdd-template リポジトリが定め、symlink で接続する。本リポジトリでは追跡しないため、clone しただけの状態では存在しない。接続は sdd-template 側で `bash scripts/link.sh <このリポジトリ>`、確認は `bash scripts/doctor.sh`。

## ディレクトリ

```text
core/        # Core CLI (Go) — graph / traversal / output / protocol / analyzer / cli
analyzers/   # 言語別 Analyzer — java/ (JavaParser / SymbolSolver / SootUp)
design/      # Design Doc (landscape) と feature 設計
context/     # 技術規約・運用契約 + project.yml (固有値・Label Policy)
adr/         # Architecture Decision Records
specs/       # issue 単位の作業文書 (close 時に削除する)
testdata/    # JSONL contract fixture / E2E fixture
templates/   # 各文書のテンプレート
scripts/     # 生成・検査スクリプト (依存図 / 読み取りマップ / 鮮度 / drift)
hooks/       # protected branch / 文書検証 / markdown 整形などの hook
```
