# Toolchain

> 最終更新: 2026-06-27

採用する標準 toolchain。採否の根拠は [adr/](../adr/) を参照する。プロジェクト固有のコマンドは [context/project.md](project.md) の Quick Commands を正本とする。

Core 実装基盤の技術選定は [ADR-0002](../adr/0002-core-implementation-foundation.md) を正本とする。
本書は、実装者が参照する標準 stack と導入境界だけを保持する。

## 標準スタック

| 区分                     | ツール                                        | 備考                                                       |
| ------------------------ | --------------------------------------------- | ---------------------------------------------------------- |
| Package manager          | Go modules                                    | `core/go.mod` を module manifest とする                     |
| Task runner              | Go 標準 command                               | 初期は make-like wrapper を導入しない                       |
| Language (Core)          | Go                                            | single binary 配布、JSONL streaming、process 制御を重視     |
| Language (Java Analyzer) | Java (JVM)                                    | JavaParser / SymbolSolver / SootUp を利用 (DesignDoc 確定) |
| CLI framework            | `github.com/spf13/cobra`                      | 初期 runtime dependency は Cobra のみに抑える               |
| Linter                   | `go vet` / `golangci-lint`                    | `golangci-lint` は開発ツール候補として扱う                  |
| Formatter                | `gofmt` / `go fmt`                            | Go 標準 formatter を正とする                                |
| Unit test                | Go 標準 `testing`                             | 手書き fake / golden fixture / contract test で開始する     |
| E2E                      | Go 標準 `testing` から CLI fixture を実行      | 具体 CLI 引数は後続の CLI interface spec で確定             |

## 採用方針

- **Java Analyzer の解析ライブラリは先行固定**: JavaParser (AST) / SymbolSolver (型解決) / SootUp (Interface Dispatch・Override 解決)。確定範囲は `java-analyzer` feature と Open Question Q2 (SootUp 統合範囲) で詰める。
- **Core 実装言語**は Go に固定する。判断根拠は [ADR-0002](../adr/0002-core-implementation-foundation.md)。
- Analyzer との通信は **JSONL over STDIN/STDOUT** に固定 (言語非依存・実装/デバッグ容易)。判断根拠は [ADR-0001](../adr/0001-analyzer-protocol-jsonl-spi.md)、Protocol / SPI / Model schema は [Analyzer Protocol / SPI feature doc](../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md) を正本とする。
- Go 側 Core は標準ライブラリを優先する。JSONL、外部 process 実行、graph 表現、text / JSON / Mermaid 出力、test は標準ライブラリと内部 package で開始する。
- Runtime dependency は初期状態で `github.com/spf13/cobra` のみに限定する。設定ファイル / env binding が要件化されるまでは `viper` を導入しない。
- 開発ツールの version 固定方法は CI 設計時に決める。`golangci-lint` と `govulncheck` は runtime dependency ではなく、quality gate 用の候補として扱う。

## Scaffold Policy

- 新規 Analyzer は `analyzer-protocol` の SPI / JSONL スキーマに準拠する形で scaffold する。対象言語の公式ツール (パーサ等) を優先採用する。
- 生成後はプロジェクトの命名・Protocol 契約 ([analyzer-protocol](../design/features/)) へ寄せる。
- Core scaffold は `core/` 配下に閉じる。Go 側 Protocol 実装は `core/internal/protocol`、Analyzer process 境界は `core/internal/analyzer` に置く。
