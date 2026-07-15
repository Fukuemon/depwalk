# Toolchain

> 最終更新: 2026-07-12

採用する標準 toolchain。採否の根拠は [adr/](../adr/) を参照する。プロジェクト固有のコマンドは [context/project.md](project.md) の Quick Commands を正本とする。

Core 実装基盤の技術選定は [ADR-0002](../adr/0002-core-implementation-foundation.md) を正本とする。
本書は、実装者が参照する標準 stack と導入境界だけを保持する。

## 標準スタック

| 区分                     | ツール                                    | 備考                                                                                                                                    |
| ------------------------ | ----------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| Package manager          | Go modules                                | `core/go.mod` を module manifest とする                                                                                                 |
| Task runner              | Go 標準 command                           | 初期は make-like wrapper を導入しない                                                                                                   |
| Language (Core)          | Go                                        | single binary 配布、JSONL streaming、process 制御を重視                                                                                 |
| Language (Java Analyzer) | Java (JVM)                                | JDK 25 LTS (Gradle toolchain 固定) / Gradle (Kotlin DSL) + Shadow plugin / 単一 fat jar 配布。JavaParser / SymbolSolver / SootUp を利用 |
| CLI framework            | `github.com/spf13/cobra`                  | 初期 runtime dependency は Cobra のみに抑える                                                                                           |
| Linter                   | `go vet` / `golangci-lint`                | `golangci-lint` は開発ツール候補として扱う                                                                                              |
| Formatter                | `gofmt` / `go fmt`                        | Go 標準 formatter を正とする                                                                                                            |
| Unit test                | Go 標準 `testing`                         | 手書き fake / golden fixture / contract test で開始する                                                                                 |
| E2E                      | Go 標準 `testing` から CLI fixture を実行 | 具体 CLI 引数は後続の CLI interface spec で確定                                                                                         |

## 採用方針

- **Java Analyzer の解析ライブラリは先行固定**: JavaParser (AST) / SymbolSolver (型解決) / SootUp (Interface Dispatch・Override 解決)。SootUp の統合範囲は確定済み (2026-07-12): 型階層・override・interface 実装候補の索引としてのみ使用し、call graph 生成は委譲しない。正本は [Java Analyzer feature doc](../design/features/java-analyzer/DesignDoc_java-analyzer.md) (決定経緯: [spec #21 D1](../specs/21-java-dispatch-spring-di/index.md#解決済みの論点))。
- **Java Analyzer の実装言語は Kotlin を不採用とし Java を維持**: JDK 25 の言語機能 (sealed interface + record + pattern matching) で Kotlin の主利点が Java 単体でも得られ、JavaParser interop では Kotlin の null 安全が platform type で効かないため。判断の正本は [Java Analyzer feature doc](../design/features/java-analyzer/DesignDoc_java-analyzer.md)。
- **Core 実装言語**は Go に固定する。判断根拠は [ADR-0002](../adr/0002-core-implementation-foundation.md)。
- Analyzer との通信は **JSONL over STDIN/STDOUT** に固定 (言語非依存・実装/デバッグ容易)。判断根拠は [ADR-0001](../adr/0001-analyzer-protocol-jsonl-spi.md)、Protocol / SPI / Model schema は [Analyzer Protocol / SPI feature doc](../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md) を正本とする。
- Go 側 Core は標準ライブラリを優先する。JSONL、外部 process 実行、graph 表現、text / JSON / Mermaid 出力、test は標準ライブラリと内部 package で開始する。
- JSONL parser / validator は安定版の `encoding/json` で開始する。ただし、`encoding/json` v1 の duplicate key 許容、invalid UTF-8 置換、struct field の case-insensitive matching を Protocol contract として採用しない。
- `encoding/json/v2` と `encoding/json/jsontext` は初期採用しない。Go 1.25 時点では experimental であり、`GOEXPERIMENT=jsonv2` が不要になった時点で strict JSONL parser の実装候補として再評価する。
- Runtime dependency は初期状態で `github.com/spf13/cobra` のみに限定する。設定ファイル / env binding が要件化されるまでは `viper` を導入しない。
- 開発ツールの version 固定方法は CI 設計時に決める。`golangci-lint` と `govulncheck` は runtime dependency ではなく、quality gate 用の候補として扱う。

## Scaffold Policy

- 新規 Analyzer は `analyzer-protocol` の SPI / JSONL スキーマに準拠する形で scaffold する。対象言語の公式ツール (パーサ等) を優先採用する。
- 生成後はプロジェクトの命名・Protocol 契約 ([analyzer-protocol](../design/features/)) へ寄せる。
- Core scaffold は `core/` 配下に閉じる。Go 側 Protocol 実装は `core/internal/protocol`、Analyzer process 境界は `core/internal/analyzer` に置く。
