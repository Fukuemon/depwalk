# Toolchain

> 最終更新: 2026-06-10

採用する標準 toolchain。採否の根拠は [adr/](../adr/) を参照する。プロジェクト固有のコマンドは [context/project.md](project.md) の Quick Commands を正本とする。

> **本プロダクトは設計フェーズ**。Core 実装言語が未確定 (A1 で Kotlin 不採用) のため、Core 側スタックは未定。確定タイミングを各行に明示する。

## 標準スタック

| 区分                     | ツール                                        | 備考                                                       |
| ------------------------ | --------------------------------------------- | ---------------------------------------------------------- |
| Package manager          | 未定                                          | Core 実装言語確定後 (ADR 化予定)                           |
| Task runner              | 未定                                          | 同上                                                       |
| Language (Core)          | 未定 (言語非依存方針 / Kotlin は A1 で不採用) | 配布が軽くマルチ言語化しやすい言語を設計フェーズで確定     |
| Language (Java Analyzer) | Java (JVM)                                    | JavaParser / SymbolSolver / SootUp を利用 (DesignDoc 確定) |
| Linter                   | 未定                                          | 実装着手前                                                 |
| Formatter                | 未定                                          | 実装着手前                                                 |
| Unit test                | 未定                                          | 実装着手前                                                 |
| E2E                      | 未定                                          | サンプル Java/Spring プロジェクトでの照合 (成功条件 S1/S2) |

## 採用方針

- **Java Analyzer の解析ライブラリは先行固定**: JavaParser (AST) / SymbolSolver (型解決) / SootUp (Interface Dispatch・Override 解決)。確定範囲は `java-analyzer` feature と Open Question Q2 (SootUp 統合範囲) で詰める。
- **Core 実装言語**は設計フェーズで確定し ADR 化する。判断軸は CLI 配布の軽さとマルチ言語化容易性 (DesignDoc Alternatives A1)。
- Analyzer との通信は **JSONL over STDIN/STDOUT** に固定 (言語非依存・実装/デバッグ容易)。判断根拠は [ADR-0001](../adr/0001-analyzer-protocol-jsonl-spi.md)、Protocol / SPI / Model schema は [Analyzer Protocol / SPI feature doc](../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md) を正本とする。

## Scaffold Policy

- 新規 Analyzer は `analyzer-protocol` の SPI / JSONL スキーマに準拠する形で scaffold する。対象言語の公式ツール (パーサ等) を優先採用する。
- 生成後はプロジェクトの命名・Protocol 契約 ([analyzer-protocol](../design/features/)) へ寄せる。
