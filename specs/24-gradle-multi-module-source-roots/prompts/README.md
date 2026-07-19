# Implementation Prompt Dependency Map

この一覧は Issue #24 の実装 prompt 群の phase、target、依存関係、並列可否を示す。
各 prompt 本文が実装指示であり、この README は実行順序と依存関係の索引である。

| Prompt                                                   | Phase | Target              | Scope                                                                    | 依存先              | 同一 phase 並列可否 | 理由                                                              |
| -------------------------------------------------------- | ----- | ------------------- | ------------------------------------------------------------------------ | ------------------- | ------------------- | ----------------------------------------------------------------- |
| `P1_01_analyzer-protocol_multi-root-failure-contract.md` | P1    | `analyzer-protocol` | `sourceRoots`、`FailureDetail`、symbol metadata の Protocol 契約         | なし                | -                   | Core と Java Analyzer が共有する wire 契約を先に固定する          |
| `P2_01_core_request-staging-failure.md`                  | P2    | `core`              | CLI request、staging Graph、opaque metadata、構造化 failure の汎用表示   | P1_01               | P2_02 と並列可      | Java 固有実装を参照せず、確定済み Protocol だけを消費する         |
| `P2_02_java-analyzer_gradle-model-provider.md`           | P2    | `java-analyzer`     | Tooling API、custom model provider、output 隔離、互換性ガード            | P1_01               | P2_01 と並列可      | Core と変更 path が分離され、共通の P1 契約だけに依存する         |
| `P3_01_java-analyzer_source-context-preflight.md`        | P3    | `java-analyzer`     | root 解決、context、language level、parse pre-flight                     | P2_02               | -                   | Gradle model provider の型付き出力を解析入力へ変換する            |
| `P4_01_java-analyzer_call-completeness-bytecode.md`      | P4    | `java-analyzer`     | source 帰属、bytecode-only member、inventory / ledger、完全性 gate       | P1_01, P3_01        | -                   | Protocol failure 契約と全解析 context の確定後に call を解決する  |
| `P5_01_java-analyzer_fixture-compatibility-security.md`  | P5    | `java-analyzer`     | multi-module fixture、cross-version matrix、security negative test、計測 | P2_02, P3_01, P4_01 | -                   | production 契約を実 jar と複数 Gradle / JVM 組合せで統合検証する  |
| `P6_01_core_required-cli-e2e.md`                         | P6    | `core`              | test-only 透過 proxy、実 Core CLI / 実 Analyzer required E2E             | P2_01, P5_01        | -                   | 両 process の production wiring と request 原子性を最後に検証する |

## 実行順序

1. `P1_01_analyzer-protocol_multi-root-failure-contract.md`
2. P2 は次の2件を並列実行できる。
   - `P2_01_core_request-staging-failure.md`
   - `P2_02_java-analyzer_gradle-model-provider.md`
3. `P3_01_java-analyzer_source-context-preflight.md`
4. `P4_01_java-analyzer_call-completeness-bytecode.md`
5. `P5_01_java-analyzer_fixture-compatibility-security.md`
6. `P6_01_core_required-cli-e2e.md`

P2 の2件以外は、同じ target または同じ fixture を更新するため直列実行する。
