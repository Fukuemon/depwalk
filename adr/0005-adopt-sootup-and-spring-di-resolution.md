# ADR-0005: Java Analyzer に SootUp による型階層の補完と Spring DI の解決を 1 つの feature として導入する

## Status

承認

## Context

Phase 1 は JavaParser / SymbolSolver により、ソース上の宣言と静的な呼び出しを抽出する。  
この方式では、interface または基底型を receiver とする呼び出しは宣言メソッドまで追跡できる。  
しかし、実行時に選択され得る override 先や、Spring DI で注入される実装 Bean までは解決しない。

Java / Spring Boot では、interface 越しのサービス・repository の呼び出しが一般的である。  
宣言メソッドだけを出力すると、「どの実装を変更すると呼び出し元へ影響するか」という成功条件「Spring DI 経由の呼び出し先を実体まで解決できる」を満たせず、変更影響調査の主要な呼び出しが実装クラスへ接続されない。  
Design Doc: [design/DesignDoc.md](../design/DesignDoc.md)

また、ソースだけでは、依存 jar にある型階層、override、default method などの情報が足りない。  
JavaParser の型解決を無理に拡張するだけでは、source と bytecode をまたぐ dispatch 解析の責務が複雑になる。

## Decision

Java Analyzer へ、次の 2 つを 1 つの後続 feature として段階的に導入する。

1. SootUp を用いて、source / bytecode / 依存 jar をまたぐ型階層と、Interface Dispatch / Override の候補を補う。
2. Spring の Bean 定義と注入規則を解析し、注入点の静的型から得た dispatch 候補を、実際の Bean の候補へ絞り込む。

Spring DI と Interface Dispatch / SootUp は別の Issue に分けず、1 つの feature Issue で設計する。  
Spring DI 側が dispatch 候補の表現と正規化に依存し、両者で symbol の正規化、edge の重複排除、曖昧性の表現、E2E fixture を共有するためである。

### 責務境界

- JavaParser / SymbolSolver は、ソースの AST、呼び出し式、annotation、source symbol を抽出する。
- SootUp は、bytecode と依存 jar を含む型階層、override、interface の実装候補を補い、bytecode の member と型の情報を取得する。
- Spring DI 解析は、stereotype、`@Bean`、constructor / field / setter injection、`@Qualifier`、`@Primary`、一意候補の規則で Bean の候補を絞り込む。
- Core は、Spring / JVM / SootUp の意味を解釈せず、Analyzer Protocol で受け取った graph を処理する。

### SootUp に call graph の生成は任せない

SootUp は、型階層・override・interface の実装候補の索引としてのみ使う。  
call graph の生成と call inventory の完全性の判定は、Java Analyzer が担う。  
理由は次の 4 つ。

- 責務境界と整合する。
  - JavaParser が source AST・呼び出し式・symbol の抽出を担い、SootUp が型階層・override・実装候補の補完を担う分担を保つ。
- 既存の資産をそのまま延長できる。
  - call graph の構築、帰属型の解決、methodId の正規化を作り直さずに済み、callSite の行番号の精度が source 由来で保たれる。
- SootUp の call graph (CHA / RTA) が持つ追加の能力が、現行の仕様では活きない。
  - 追加で得られるのは依存 jar の内部の呼び出しの連鎖だが、帰属型の決定規則が scope 外の edge を出力しないため、結果に現れない。
  - Spring DI の絞り込みはどちらの案でも自前の実装になるため、最終的な精度は同等である。
- 解析時間と最大 RSS の増分を抑えられる。

### 候補 edge と曖昧性の Protocol 表現

一意に解決できない場合は候補を保持し、根拠なく 1 つへ確定しない。  
call site ごとに、caller から各実装候補への複数の `CallEdge` を出力し、宣言型への既存の edge も保持する。  
各 edge の `metadata` に解決の根拠を付ける。

### project bytecode member index

source に現れない生成 member は、call site から要求された signature だけを、自 project の compile classes output から索引化して解決する。  
索引は、Lombok などの generator 固有の annotation に依存しない。

## Considered Options

| 選択肢                                                                                 | 強み                                                                                                                                                    | 弱み                                                                                                                                                                                              |
| -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **採用:** SootUp による型階層の補完と Spring DI の解決を 1 つの feature で段階導入する | 静的に導ける実装候補まで変更影響を追跡できる。source と依存 jar の型情報を統合できる。型階層の候補と Bean の選択を同じ symbol / edge の規則で統合できる | build 時間、解析時間、最大 RSS が増える。JavaParser と SootUp の symbol 表現の差を正規化し、同一 edge を重複排除する必要がある                                                                    |
| JavaParser / SymbolSolver だけで Interface Dispatch と依存 jar の解析を実装する        | —                                                                                                                                                       | source 中心の AST 解析と bytecode の call graph 解析の責務が混在し、依存 jar や複雑な override の補完を独自に実装する負担が大きい                                                                 |
| SootUp だけで Spring DI まで解決する                                                   | 型階層と bytecode から候補は列挙できる                                                                                                                  | Spring の Bean 定義、`@Qualifier`、`@Primary` などの container の規則による選択は表現できない                                                                                                     |
| Spring DI の解決と Interface Dispatch / SootUp を別々の feature Issue にする           | —                                                                                                                                                       | Spring DI 側が dispatch 候補の表現と正規化に依存し、境界をまたぐ暫定の schema、fixture、重複排除を二重に設計することになる。1 つの spec の中で段階分割した方が、end-to-end の成功条件を保ちやすい |
| 宣言メソッドまでの Phase 1 の出力を最終の仕様とする                                    | —                                                                                                                                                       | Spring Boot の主要な呼び出しが実装クラスへ接続されず、Spring DI 経由の呼び出し先を実体まで解決する成功条件を満たさない                                                                            |

## Consequences

### Positive

- interface、継承、override、Spring DI を通る呼び出しについて、静的に導ける実装候補まで変更影響を追跡できる。
- source と依存 jar の型情報を統合し、JavaParser 単独より高い解析精度を得られる。
- 型階層の候補と Spring の Bean の選択を、同じ symbol / edge の規則で統合できる。

### Negative

- SootUp と Spring 解析の依存を足すことで、Java Analyzer の build 時間、解析時間、最大 RSS が増える。
- JavaParser と SootUp の symbol 表現の差を正規化し、同一 edge を重複排除する必要がある。
- 条件付き Bean や実行時 Proxy など、一意に確定できないケースでは、候補または未解決が残る。
- SootUp が対応する class file version を、JDK の更新時に検証する必要がある。
