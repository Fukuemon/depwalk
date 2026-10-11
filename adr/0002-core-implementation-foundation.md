# ADR-0002: Core を Go と Go modules で実装し、依存を Cobra と標準ライブラリに絞る

## Status

承認

## Context

Core と Analyzer は JSONL over STDIN/STDOUT の process SPI で結合すると決めてある。  
ADR-0001: [Core と Analyzer を別プロセスにし、JSONL over STDIN/STDOUT の process SPI で結合する](0001-analyzer-protocol-jsonl-spi.md)

Core の実装へ進むには、Core の実装言語、dependency 管理、task runner、test framework、初期の directory / package 構成を固定する必要がある。

Core は呼び出しグラフの構築、探索、出力を担う。  
Java Analyzer は独立プロセスとして Java / Spring の解析を担う。  
そのため Core の実装基盤は、特定の Analyzer runtime へ直接依存せず、local と CI で配布しやすく、JSONL streaming と外部 process 制御を扱える必要がある。

## Decision

### 実装言語と dependency 管理

Core の実装言語は Go とする。  
Dependency 管理は Go modules とする。  
初期の runtime dependency は、CLI framework の `github.com/spf13/cobra` だけに抑える。

### 標準 command

初期の task runner は導入しない。  
Core の標準 command は、`core/` を working directory として `go test ./...`、`go vet ./...`、`go fmt ./...`、`go mod tidy` を使う。  
repository 全体をまとめる make 相当の wrapper は、command 数や CI matrix が増えた時点で再検討する。

### test framework

Test framework は Go 標準の `testing` を採用する。  
Protocol contract test、golden fixture、手書きの fake、interface stub で開始する。  
`testify`、mock generator、`github.com/google/go-cmp/cmp` は初期に導入しない。

### JSONL parser / validator

JSONL parser / validator は、安定版の `encoding/json` を使って開始する。  
ただし `encoding/json` v1 の permissive な挙動を、Protocol contract として採用しない。  
Protocol の実装は、duplicate key、invalid UTF-8、Protocol field 名の大小文字違いを invalid record として拒否する。

`encoding/json/v2` と `encoding/json/jsontext` は初期の実装では採用しない。  
Go 1.25 の時点では experimental であり、`GOEXPERIMENT=jsonv2` と Go 1 compatibility promise の外の API に依存するためである。  
experimental が外れ、CI と配布の toolchain で標準として使える状態になった時点で、strict な JSONL parser の実装候補として再評価する。

### 初期の directory / package 構成

Core と Analyzer は top-level のディレクトリで分ける。  
Go 側の Analyzer Protocol の実装と、Analyzer process の起動と入出力の handling は、Core の `internal` の別々の package に置く。  
Java などの言語別 Analyzer の実装は `analyzers/<language>/` に置き、Core の `internal` package には入れない。

Core と Analyzer が共有する決まりは、Analyzer Protocol の Design Doc、ADR、JSONL fixture、contract test の観点に限定する。  
Go package や Java の実装 code を共有境界にしない。

package 間の依存方向と、wire 表現をドメイン側へ持ち込まないための隔離規則は、この ADR では定めない。  
ADR-0007: [フラットな責務名 package 構成を維持したまま、依存境界を ACL と機械のチェックで固定する](0007-layered-architecture-refactor.md)

## Considered Options

| 選択肢                                                                                                       | 強み                                                                                                                                               | 弱み                                                                                                                                                                                |
| ------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **採用:** Go と Go modules。依存は Cobra だけ。JSON は安定版の `encoding/json` に strict validation を重ねる | single binary として配布しやすい。JSONL streaming、外部 process 制御、unit test を標準ライブラリ中心で実装できる。Core が JVM runtime に依存しない | Go と Java Analyzer の間で型定義を code として共有できず、互換性は JSONL fixture と contract test で保証する。`encoding/json` v1 の permissive な挙動を validation で塞ぐ必要がある |
| Rust で Core を実装する                                                                                      | single binary 配布と性能面は適している                                                                                                             | JSONL process SPI、CLI、graph / traversal、contract test を素早く揃える観点では、開発者の導入負荷と実装速度が Go より重い                                                           |
| TypeScript (Node.js) で Core を実装する                                                                      | JSONL と CLI は実装しやすい                                                                                                                        | Core の配布時に Node.js runtime 前提が残り、local / CI で単体 binary として扱う方針に合わない                                                                                       |
| Kotlin または JVM 言語で Core を実装する                                                                     | Phase1 の Java Analyzer との統合は容易になる                                                                                                       | Core が JVM 前提になり、Analyzer runtime から独立させる設計原則と CLI 配布の軽さに反する                                                                                            |
| Protocol schema から各言語の型を生成する                                                                     | —                                                                                                                                                  | 初期段階では schema generator と生成 code の保守が増える。Phase1 は Go struct と `Validate()`、JSONL fixture、contract test で互換性を検証する                                      |
| `encoding/json/v2` または `encoding/json/jsontext` を初期に採用する                                          | duplicate key や invalid UTF-8 を拒否する default が Protocol 境界に合う                                                                           | Go 1.25 の時点では experimental であり、Core の実装基盤が experimental API と `GOEXPERIMENT=jsonv2` に依存することになる                                                            |
| `testify`、mock generator、`go-cmp` を初期に導入する                                                         | —                                                                                                                                                  | 初期の test 対象は、小さい package 境界と fixture との比較で表現できる。Assertion DSL、生成 mock、deep diff library は、失敗差分や fake の重複が実害になった時点で追加を判断する    |

## Consequences

### Positive

- Core を single binary として配布しやすい。
- JSONL streaming、外部 process 制御、structured logging、unit test を、標準ライブラリ中心で実装できる。
- Core が Java Analyzer の JVM runtime や Java 解析 library に依存しない。
- 初期の dependency を Cobra に限定し、restore 時間と supply chain risk を小さく保てる。
- `go test`、`go vet`、`go fmt`、`go mod tidy` を local と CI の共通 command として使える。
- `encoding/json/v2` を待たずに実装へ進める。
  - Protocol の厳格性は、contract test と Protocol の実装の validation で保証する。

### Negative

- Go と Java Analyzer の間で、型定義を code として共有できない。
- Protocol の互換性は、JSONL fixture と contract test で保証する必要がある。
- `encoding/json` v1 は duplicate key、invalid UTF-8、struct field の大小文字違いなどを permissive に扱う。
  - Protocol parser は `json.Unmarshal` へ直接流すだけでは contract を満たせない。
- `encoding/json/v2` を初期に採用しないため、v2 の strict default を利用できない。
  - v2 が安定した時点で、strict validation の実装を置き換えるかを再評価する。
- make 相当の wrapper を初期に導入しないため、複数 module や CI matrix が増えた場合は root task の再設計が必要になる。
- Assertion library や diff library を初期に導入しないため、複雑な graph 差分が読みにくくなった場合は `go-cmp` などの追加を判断する必要がある。
