# analysis/pipeline

解析段階の**実行順を知る唯一の場所**。`AnalysisRunner` が各 sub-package の段階を以下の順で調停する。
他 package は自段階の責務のみを持ち、段階間の順序・依存を知らない。

正本: `design/features/java-analyzer/DesignDoc_java-analyzer.md` 「内部 package 構成と依存境界」 /
`adr/0007-layered-architecture-refactor.md`

## 実行順

| 順  | 段階                                | package                                 |
| --- | ----------------------------------- | --------------------------------------- |
| 1   | scope 列挙                          | `analysis/scope`                        |
| 2   | context 構築 — JavaParser + augment | `analysis/context` / `analysis/augment` |
| 3   | attribution 準備                    | `analysis/attribution`                  |
| 4   | SootUp 型階層 index                 | `analysis/sootup`                       |
| 5   | Spring DI index                     | `analysis/spring`                       |
| 6   | call graph 構築                     | `analysis/graph`                        |
| 7   | completeness 検査                   | `analysis/completeness`                 |
| 8   | io 出力                             | `io`                                    |

- `analysis/normalize` は段階横断の naming util (実行順に位置を持たない)
- 補足: SootUp index の instance は段階 2 (TypeSolver 構築) が bytecode member 合成 (spec #24 D31) で
  index を要するため context 構築より先に全 context 分を生成するが、索引化は lazy のため
  段階としては 4 に位置づける
