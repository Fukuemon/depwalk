# Implementation Prompt Dependency Map

この一覧は #12 の実装 prompt 群の phase、target、依存関係、並列可否を示す。
各 prompt 本文が実装指示の正本であり、この README は実行順序と依存関係の索引である。

| Prompt                                                 | Phase | Target              | Scope                                                           | 依存先              | 同一 phase 並列可否 | 理由                                                              |
| ------------------------------------------------------ | ----- | ------------------- | --------------------------------------------------------------- | ------------------- | ------------------- | ----------------------------------------------------------------- |
| `P1_01_analyzer-protocol_protocol-model-validation.md` | P1    | `analyzer-protocol` | Protocol DTO / record validation                                | なし                | -                   | 後続 parser / fixture / runner が依存する基盤                     |
| `P2_01_analyzer-protocol_strict-jsonl-parser.md`       | P2    | `analyzer-protocol` | strict JSONL parser                                             | P1_01               | -                   | P1 の DTO / validation に接続する                                 |
| `P3_01_analyzer-protocol_contract-fixtures.md`         | P3    | `analyzer-protocol` | record type fixture / scenario fixture / Protocol contract test | P1_01, P2_01        | -                   | P1 / P2 の parser と validation を fixture で検証する             |
| `P4_01_core_analyzer-process-runner.md`                | P4    | `core`              | minimal Analyzer process runner                                 | P1_01, P2_01, P3_01 | -                   | scenario fixture を runner test に使うため P3_01 完了後に実施する |

## 実行順序

1. `P1_01_analyzer-protocol_protocol-model-validation.md`
2. `P2_01_analyzer-protocol_strict-jsonl-parser.md`
3. `P3_01_analyzer-protocol_contract-fixtures.md`
4. `P4_01_core_analyzer-process-runner.md`

同一 phase に複数 prompt はないため、現時点で並列実行可能な prompt はない。
