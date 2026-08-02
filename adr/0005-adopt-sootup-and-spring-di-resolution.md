# ADR-0005: Java AnalyzerにSootUpとSpring DI解決を段階導入する

## 状態

承認

## 決定日

2026-07-11

## 背景

Issue #9 の Phase 1 は JavaParser / SymbolSolver によりソース上の宣言と静的な呼び出しを抽出する。この方式では、interface または基底型をreceiverとする呼び出しは宣言メソッドまで追跡できるが、実行時に選択され得るoverride先や、Spring DIで注入される実装Beanまでは解決しない。

Java/Spring Bootではinterface越しのサービス・repository呼び出しが一般的である。宣言メソッドだけを出力すると「どの実装を変更すると呼び出し元へ影響するか」という成功条件S4を満たせず、変更影響調査の主要経路が実装クラスへ接続されない。

また、ソースだけでは依存jarにある型階層、override、default method等の情報が不足する。JavaParserの型解決を無理に拡張するだけでは、sourceとbytecodeをまたぐdispatch解析の責務が複雑になる。

## 決定

Java Analyzerへ次の二つを一つの後続featureとして段階導入する。

1. SootUpを用いてsource / bytecode / 依存jarをまたぐ型階層とInterface Dispatch / Override候補を補完する。
2. SpringのBean定義と注入規則を解析し、注入点の静的型から得たdispatch候補を実際のBean候補へ絞り込む。

責務境界は次のとおりとする。

- JavaParser / SymbolSolver: ソースAST、呼び出し式、annotation、source symbolの抽出を担う。
- SootUp: bytecodeと依存jarを含む型階層、override、interface実装候補の補完を担う。call graph 生成まで任せるかは後続 spec の clarify phaseで決定する。
- Spring DI解析: stereotype、`@Bean`、constructor / field / setter injection、`@Qualifier`、`@Primary`、一意候補規則によるBean候補の絞り込みを担う。
- Core: Spring / JVM / SootUpの意味を解釈せず、Analyzer Protocolで受け取ったgraphを処理する。

一意に解決できない場合は候補を保持し、根拠なく一つへ確定しない。候補edge、解決根拠、曖昧性のProtocol上の表現は後続specで既存schemaとの整合を確認して決定する。

### 状態追記 (issue #21 clarify phase での確定、2026-07-12)

上記決定時点で未決だった 2 点が issue #21 の clarify phase で確定した。決定内容自体は変更しない、状態の追記として記録する。

1. **call graph 生成をどこまで任せるか**: SootUp は型階層・override・interface 実装候補の索引としてのみ使用し、call graph 生成までは任せない。

   理由は 4 つある。
   - **本 ADR の責務境界と整合する**。JavaParser が source AST・呼び出し式・symbol の抽出を担い、SootUp は型階層・override・実装候補の補完を担う、という分担を崩さない
   - **既存資産をそのまま延長できる**。CallGraphBuilder / AttributionResolver / methodId 正規化を作り直さずに済み、callSite の行番号精度が source 由来で保たれる
   - **SootUp の call graph (CHA / RTA) が持つ追加能力が、現行仕様では活きない**。追加で得られるのは依存 jar 内部の呼び出し連鎖だが、帰属型の決定規則が scope 外 edge を出力しないため結果に現れない。Spring DI の絞り込みはどちらの案でも自前実装になるため、最終的な精度は同等
   - **解析時間と最大 RSS の増分を抑えられる**

2. **候補edge / 解決根拠 / 曖昧性のProtocol表現**: call siteごとにcaller→各実装候補への複数`CallEdge`を出力し、宣言型への既存edgeも保持する。各edgeの`metadata`に解決根拠 (`resolution` / `provenance` 等) を付与する (issue #21)。
3. **project bytecode member index (issue #24、2026-07-18)**: source に現れない生成 member は、call site から要求された signature だけを自 project の compile classes output から索引化して解決する。index は Lombok 等の generator 固有 annotation に依存しない。SootUp は bytecode member / 型情報の取得を担い、call graph 生成と call inventory の完全性判定は引き続き Java Analyzer が担う。bytecode-only member は Protocol 上の `sourceLocation` を省略し、owner の source anchor を opaque metadata で保持する。

上記の理由は issue #21 の spec で確定したものを本 ADR へ移した (2026-08-02)。spec は closeout で削除済みのため、判断を定めるのは本 ADR とする。

Spring DIとInterface Dispatch / SootUpは別Issueに分けず、一つのfeature Issueで設計する。SpringのBean選択は型階層から得る実装候補に依存し、両者でsymbol正規化、edge重複排除、曖昧性表現、E2E fixtureを共有するためである。実装promptは型階層補完、Spring候補絞り込み、統合E2Eの順に分割する。

## 代替案

- JavaParser / SymbolSolverだけでInterface Dispatchと依存jar解析を実装する。
  - 却下理由: source中心のAST解析とbytecode call graph解析の責務が混在し、依存jarや複雑なoverrideの補完を独自実装する負担が大きい。
- SootUpだけでSpring DIまで解決する。
  - 却下理由: 型階層とbytecodeから候補は列挙できるが、SpringのBean定義、`@Qualifier`、`@Primary`等のcontainer規則による選択は表現できない。
- Spring DI解決とInterface Dispatch / SootUpを別々のfeature Issueにする。
  - 却下理由: Spring DI側がdispatch候補の表現と正規化に依存し、境界をまたぐ暫定schema、fixture、重複排除を二重に設計することになる。一つのspec内で段階分割した方がend-to-endの成功条件を保ちやすい。
- 宣言メソッドまでのPhase 1出力を最終仕様とする。
  - 却下理由: Spring Bootの主要な呼び出しが実装クラスへ接続されず、Design Docの成功条件S4を満たさない。

## 影響

### 良い影響

- interface、継承、override、Spring DIを通る呼び出しについて、静的に導ける実装候補まで変更影響を追跡できる。
- sourceと依存jarの型情報を統合し、JavaParser単独より高い解析精度を得られる。
- 型階層候補とSpringのBean選択を同じsymbol / edge規則で統合できる。

### 悪い影響 / トレードオフ

- SootUpとSpring解析の依存追加により、Java Analyzerのbuild時間、解析時間、最大RSSが増える。
- JavaParserとSootUpのsymbol表現差を正規化し、同一edgeを重複排除する必要がある。
- 条件付きBeanや実行時Proxy等、一意に確定できないケースでは候補または未解決が残る。
- SootUpの対応class file versionをJDK更新時に検証する必要がある。

### 影響範囲

- 対象モジュール / package: `java-analyzer`, `analyzer-protocol`

## 実装・運用への反映

- spec 更新要否: 要。Issue #9の後続 issue #21 でSootUp適用範囲、候補表現、Spring条件評価、性能上限を決定した (2026-07-12 sync phaseで反映済み)。
- context / AI 向け設定更新要否: 要。issue #21の決定を踏まえ、Java Analyzer feature doc、toolchainへ依存・テスト・性能契約を反映済み (2026-07-12)。

## 関連ドキュメント / チケット

- [design/DesignDoc.md](../design/DesignDoc.md): 成功条件S4、Java Analyzer責務、Phase 2 / Phase 3
- [design/features/java-analyzer/DesignDoc_java-analyzer.md](../design/features/java-analyzer/DesignDoc_java-analyzer.md): 段階導入、dispatchの既知の制約
- [adr/0004-defer-runtime-call-tracing.md](0004-defer-runtime-call-tracing.md): 動的呼び出しの完全追跡との境界
- [issue #9](https://github.com/Fukuemon/depwalk/issues/9): Phase 1の設計と実装分割
- [issue #21](https://github.com/Fukuemon/depwalk/issues/21): SootUp統合範囲・dispatch候補表現・Spring条件評価・性能受け入れ基準の決定記録
- [issue #24](https://github.com/Fukuemon/depwalk/issues/24): generator 非依存の project bytecode member index と完全性 gate の決定経緯
