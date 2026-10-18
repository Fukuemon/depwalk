# analysis/pipeline

`AnalysisRunner` は、解析の段の実行の順を持ち、各 sub-package の段を次の順に呼ぶ。  
request の読み取りから解析の context の組み立て（`AnalysisContextFactory`）までの順は、`Main` が持つ。  
段階別の package のうち、`graph` と `spring` は互いに import する。

設計の説明は次の 2 つにある。

- [Java Analyzer の Design Doc の「内部の package 構成と外部ライブラリの隔離」](../../../../../../../../../DesignDoc.md#内部の-package-構成と外部ライブラリの隔離)
- ADR-0007: [Core と Java Analyzer の依存境界を ACL と機械のチェックで固定し、Core はフラットな package 構成のまま保つ](../../../../../../../../../../../adr/0007-layered-architecture-refactor.md)

## 実行順

| 順  | 段階                                                                    | package                                                        |
| --- | ----------------------------------------------------------------------- | -------------------------------------------------------------- |
| 1   | workspaceRoot の real path 化と scope の列挙                            | `analysis/context`（`ContextScope`）/ `analysis/scope`         |
| 2   | bytecode の索引の構築（SootUp の型の階層と project の bytecode member） | `analysis/sootup` / `analysis/completeness`                    |
| 3   | parser の構築（context ごとの TypeSolver、parser、AST の injector）     | `analysis/context` / `analysis/augment`                        |
| 4   | parse の pre-flight と、成功した後の context の warning の出力          | `analysis/context`（`ParsePreflight`）/ `io`                   |
| 5   | attribution の準備                                                      | `analysis/attribution`                                         |
| 6   | first pass（索引の構築と Spring DI の diagnostic）                      | `analysis/completeness` / `analysis/spring` / `analysis/graph` |
| 7   | builder の構築（context ごとの `CallGraphBuilder`）                     | `analysis/graph`                                               |
| 8   | second pass（member の注入と呼び出しの解析）                            | `analysis/augment` / `analysis/graph` / `io`                   |
| 9   | completeness gate                                                       | `analysis/completeness`                                        |

- `analysis/scope` は glob と path の helper だけを持ち、scope の列挙は `analysis/context` の `ContextScope` が行う。
- `analysis/normalize` は段階を横断する naming の util で、実行順に位置を持たない。
- bytecode の索引は、段 3 の TypeSolver が bytecode member の合成に使うため、段 3 より先に作る。
  - 合成の規則は、[解析エンジンの Design Doc の「solver の層での bytecode member の合成」](../DesignDoc.md#solver-の層での-bytecode-member-の合成) にある。
- `fullGraph` の record は、段 8 の中で file ごとに逐次に出力する。
- `reachableFromEntrypoints` の record は、段 8 の後、段 9 の前にまとめて出力する。
