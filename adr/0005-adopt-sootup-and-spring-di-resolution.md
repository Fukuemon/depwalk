# ADR-0005: Java Analyzer に SootUp と Spring DI 解決を段階導入する

## 状態

承認

## 決定日

2026-07-11

## 背景

Phase 1 は JavaParser / SymbolSolver によりソース上の宣言と静的な呼び出しを抽出する。この方式では、interface または基底型を receiver とする呼び出しは宣言メソッドまで追跡できるが、実行時に選択され得る override 先や、Spring DI で注入される実装 Bean までは解決しない。

Java / Spring Boot では interface 越しのサービス・repository 呼び出しが一般的である。宣言メソッドだけを出力すると「どの実装を変更すると呼び出し元へ影響するか」という成功条件 S4 を満たせず、変更影響調査の主要経路が実装クラスへ接続されない。

また、ソースだけでは依存 jar にある型階層、override、default method 等の情報が不足する。JavaParser の型解決を無理に拡張するだけでは、source と bytecode をまたぐ dispatch 解析の責務が複雑になる。

## 決定

Java Analyzer へ次の 2 つを 1 つの後続 feature として段階導入する。

1. SootUp を用いて source / bytecode / 依存 jar をまたぐ型階層と Interface Dispatch / Override 候補を補完する。
2. Spring の Bean 定義と注入規則を解析し、注入点の静的型から得た dispatch 候補を実際の Bean 候補へ絞り込む。

Spring DI と Interface Dispatch / SootUp は別 Issue に分けず、1 つの feature Issue で設計する。Spring DI 側が dispatch 候補の表現と正規化に依存し、両者で symbol 正規化、edge 重複排除、曖昧性表現、E2E fixture を共有するためである。実装は型階層補完、Spring 候補絞り込み、統合 E2E の順に分割する。

### 責務境界

| 担い手                    | 責務                                                                                                                          |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| JavaParser / SymbolSolver | ソース AST、呼び出し式、annotation、source symbol の抽出                                                                      |
| SootUp                    | bytecode と依存 jar を含む型階層、override、interface 実装候補の補完。bytecode member / 型情報の取得                          |
| Spring DI 解析            | stereotype、`@Bean`、constructor / field / setter injection、`@Qualifier`、`@Primary`、一意候補規則による Bean 候補の絞り込み |
| Core                      | Spring / JVM / SootUp の意味を解釈せず、Analyzer Protocol で受け取った graph を処理する                                       |

### SootUp に call graph 生成は任せない

SootUp は型階層・override・interface 実装候補の索引としてのみ使用する。call graph 生成と call inventory の完全性判定は Java Analyzer が担う。理由は 4 つある。

- 本 ADR の責務境界と整合する。JavaParser が source AST・呼び出し式・symbol の抽出を担い、SootUp は型階層・override・実装候補の補完を担う、という分担を崩さない
- 既存資産をそのまま延長できる。CallGraphBuilder / AttributionResolver / methodId 正規化を作り直さずに済み、callSite の行番号精度が source 由来で保たれる
- SootUp の call graph (CHA / RTA) が持つ追加能力が、現行仕様では活きない。追加で得られるのは依存 jar 内部の呼び出し連鎖だが、帰属型の決定規則が scope 外 edge を出力しないため結果に現れない。Spring DI の絞り込みはどちらの案でも自前実装になるため、最終的な精度は同等
- 解析時間と最大 RSS の増分を抑えられる

### 候補 edge と曖昧性の Protocol 表現

一意に解決できない場合は候補を保持し、根拠なく 1 つへ確定しない。call site ごとに caller → 各実装候補への複数 `CallEdge` を出力し、宣言型への既存 edge も保持する。各 edge の `metadata` に解決根拠 (`resolution` / `provenance` 等) を付与する。

### project bytecode member index

source に現れない生成 member は、call site から要求された signature だけを自 project の compile classes output から索引化して解決する。索引は Lombok 等の generator 固有 annotation に依存しない。bytecode-only member は Protocol 上の `sourceLocation` を省略し、owner の source anchor を opaque metadata で保持する。

## 代替案

- JavaParser / SymbolSolver だけで Interface Dispatch と依存 jar 解析を実装する。
  - 却下理由: source 中心の AST 解析と bytecode call graph 解析の責務が混在し、依存 jar や複雑な override の補完を独自実装する負担が大きい。
- SootUp だけで Spring DI まで解決する。
  - 却下理由: 型階層と bytecode から候補は列挙できるが、Spring の Bean 定義、`@Qualifier`、`@Primary` 等の container 規則による選択は表現できない。
- Spring DI 解決と Interface Dispatch / SootUp を別々の feature Issue にする。
  - 却下理由: Spring DI 側が dispatch 候補の表現と正規化に依存し、境界をまたぐ暫定 schema、fixture、重複排除を二重に設計することになる。1 つの spec 内で段階分割した方が end-to-end の成功条件を保ちやすい。
- 宣言メソッドまでの Phase 1 出力を最終仕様とする。
  - 却下理由: Spring Boot の主要な呼び出しが実装クラスへ接続されず、成功条件 S4 を満たさない。

## 影響

### 良い影響

- interface、継承、override、Spring DI を通る呼び出しについて、静的に導ける実装候補まで変更影響を追跡できる。
- source と依存 jar の型情報を統合し、JavaParser 単独より高い解析精度を得られる。
- 型階層候補と Spring の Bean 選択を同じ symbol / edge 規則で統合できる。

### 悪い影響 / トレードオフ

- SootUp と Spring 解析の依存追加により、Java Analyzer の build 時間、解析時間、最大 RSS が増える。
- JavaParser と SootUp の symbol 表現差を正規化し、同一 edge を重複排除する必要がある。
- 条件付き Bean や実行時 Proxy 等、一意に確定できないケースでは候補または未解決が残る。
- SootUp の対応 class file version を JDK 更新時に検証する必要がある。

### 影響範囲

- 対象モジュール / package: `java-analyzer`, `analyzer-protocol`

## 実装・運用への反映

- Java Analyzer feature doc と toolchain へ、依存・テスト・性能契約を反映する。
- SootUp を更新するときは、対応 class file version と型階層 API の挙動を先に確認する。解析結果の edge 数が変わるため、E2E fixture で前後を比較する。

## 関連ドキュメント / チケット

- [design/DesignDoc.md](../design/DesignDoc.md): 成功条件 S4、Java Analyzer 責務
- [design/features/java-analyzer/DesignDoc_java-analyzer.md](../design/features/java-analyzer/DesignDoc_java-analyzer.md): 段階導入、dispatch の既知の制約
- [design/features/java-analyzer/analysis.md](../design/features/java-analyzer/analysis.md): 型解決・Spring DI 解決・解析完全性の判定規則
- [ADR-0004](0004-defer-runtime-call-tracing.md): 動的呼び出しの完全追跡との境界
- [ADR-0012](0012-implicit-call-resolution-and-type-propagation-rescue.md): framework 由来の暗黙呼び出し解決と型伝播救済
- issue / PR:
  - [#9](https://github.com/Fukuemon/depwalk/issues/9): Phase 1 の設計と実装分割
  - [#21](https://github.com/Fukuemon/depwalk/issues/21): SootUp 統合範囲・dispatch 候補表現・Spring 条件評価・性能受け入れ基準の決定記録
  - [#24](https://github.com/Fukuemon/depwalk/issues/24): generator 非依存の project bytecode member index と完全性 gate の決定経緯
