# ADR-0002: Core 実装基盤に Go と Go modules を採用する

## 状態

承認

> 追補 (2026-07-24): 本 ADR の「初期 directory / package 構成」(`core/internal/` 直下の 7 package 並列) は [ADR-0007](0007-layered-architecture-refactor.md) で層別構造 (`domain` / `app` / `platform`) へ改訂した。実装言語 / Go modules / 標準 command / JSONL parser 方針など他の決定は引き続き有効。

## 決定日

2026-06-27

## 背景

Spec #8 で Analyzer Protocol / SPI / Model schema を定義し、ADR-0001 で Core と Analyzer を JSONL over STDIN/STDOUT の process SPI で結合すると決めた。
次に Core 実装へ進むには、Core 実装言語、dependency 管理、task runner、test framework、初期 directory / package 構成を固定する必要がある。

Core は呼び出しグラフの構築、探索、出力を担う。
Java Analyzer は独立プロセスとして Java / Spring の解析を担う。
そのため Core の実装基盤は、特定 Analyzer runtime へ直接依存せず、local / CI で配布しやすく、JSONL streaming と外部 process 制御を扱える必要がある。

## 決定

Core 実装言語は Go とする。
Dependency 管理は Go modules とする。
初期の runtime dependency は CLI framework の `github.com/spf13/cobra` のみに抑える。

初期 task runner は導入しない。
Core の標準 command は `core/` を working directory として、`go test ./...`、`go vet ./...`、`go fmt ./...`、`go mod tidy` を使う。
Repository-level の make-like wrapper は、command 数や CI matrix が増えた時点で再検討する。

Test framework は Go 標準の `testing` を採用する。
Protocol contract test、golden fixture、手書き fake、interface stub で開始する。
`testify`、mock generator、`github.com/google/go-cmp/cmp` は初期導入しない。

JSONL parser / validator は安定版の `encoding/json` を使って開始する。
ただし、`encoding/json` v1 の permissive な挙動を Protocol contract として採用しない。
`core/internal/protocol` は、duplicate key、invalid UTF-8、Protocol field 名の大小文字違い、必須 field 欠落、未対応 major `schemaVersion` を invalid record として拒否する。
出力側は、Protocol 上 array / object と定義する field を nil slice / nil map から `null` として marshal しない。

`encoding/json/v2` と `encoding/json/jsontext` は初期実装では採用しない。
Go 1.25 時点では experimental であり、`GOEXPERIMENT=jsonv2` と Go 1 compatibility promise 外の API に依存するためである。
experimental が外れ、CI / 配布 toolchain で標準利用できる状態になった時点で、strict JSONL parser の実装候補として再評価する。

初期 directory / package 構成は、Core と Analyzer を top-level で分ける。
Go 側の Analyzer Protocol 実装は `core/internal/protocol` に置く。
Analyzer process の起動、stdin / stdout / stderr、exit code handling は `core/internal/analyzer` に分ける。
Java などの言語別 Analyzer 実装は `analyzers/<language>/` に置き、Core の `internal` package には入れない。

```text
depwalk/
├── core/
│   ├── go.mod
│   ├── cmd/
│   │   └── depwalk/
│   └── internal/
│       ├── cli/
│       ├── analyze/
│       ├── protocol/
│       ├── analyzer/
│       ├── graph/
│       ├── traversal/
│       └── output/
├── analyzers/
│   └── java/
└── testdata/
    ├── analyzer-protocol/
    └── fixtures/
```

Core と Analyzer が共有する正本は、Analyzer Protocol feature doc、ADR、JSONL fixture、contract test 観点に限定する。
Go package や Java 実装 code を共有境界にしない。

## 代替案

- Rust を Core 実装言語にする。
  - 却下理由: single binary 配布と性能面は適している。一方で、初期実装で JSONL process SPI、CLI、graph / traversal、contract test を素早く揃える観点では、開発者の導入負荷と実装速度が Go より重い。
- TypeScript(Node.js) を Core 実装言語にする。
  - 却下理由: JSONL と CLI は実装しやすい。一方で、Core 配布時に Node.js runtime 前提が残り、local / CI で単体 binary として扱う方針に合わない。
- Kotlin または JVM 言語で Core を実装する。
  - 却下理由: Phase1 の Java Analyzer との統合は容易になる。一方で、Core が JVM 前提になり、Analyzer runtime から独立させる設計原則と CLI 配布の軽さに反する。
- Protocol schema から各言語の型を生成する。
  - 却下理由: 初期段階では schema generator と generated code の保守が増える。Phase1 は Go struct と `Validate()`、JSONL fixture、contract test で互換性を検証する。
- `encoding/json/v2` または `encoding/json/jsontext` を初期採用する。
  - 却下理由: duplicate key や invalid UTF-8 を拒否する default は Protocol 境界に合う。一方で、Go 1.25 時点では experimental であり、Core の実装基盤 ADR が experimental API と `GOEXPERIMENT=jsonv2` に依存することになる。初期実装は安定版 `encoding/json` に strict validation を重ね、v2 は experimental が外れた時点で再評価する。
- `testify`、mock generator、`go-cmp` を初期導入する。
  - 却下理由: 初期の test 対象は小さい package 境界と fixture 照合で表現できる。Assertion DSL、generated mock、deep diff library は、失敗差分や fake 重複が実害になった時点で追加判断する。

## 影響

### 良い影響

- Core を single binary として配布しやすい。
- JSONL streaming、外部 process 制御、structured logging、unit test を標準ライブラリ中心で実装できる。
- Core が Java Analyzer の JVM runtime や Java 解析 library に依存しない。
- 初期 dependency を Cobra に限定し、restore 時間と supply chain risk を小さく保てる。
- `go test`、`go vet`、`go fmt`、`go mod tidy` を local / CI の共通 command として使える。
- `encoding/json/v2` を待たずに実装へ進める。Protocol の厳格性は contract test と `core/internal/protocol` の validation で担保する。

### 悪い影響 / トレードオフ

- Go と Java Analyzer の間で型定義を code として共有できない。
- Protocol 互換性は JSONL fixture と contract test で担保する必要がある。
- `encoding/json` v1 は duplicate key、invalid UTF-8、struct field の大小文字違いなどを permissive に扱う。Protocol parser は `json.Unmarshal` へ直接流すだけでは contract を満たせない。
- `encoding/json/v2` を初期採用しないため、v2 の strict default を利用できない。v2 が安定化した時点で、strict validation の実装を置き換えるかを再評価する。
- make-like wrapper を初期導入しないため、複数 module や CI matrix が増えた場合は root task の再設計が必要になる。
- Assertion library や diff library を初期導入しないため、複雑な graph 差分が読みにくくなった場合は `go-cmp` などの追加判断が必要になる。

### 影響範囲

- 主対象モジュール / package: `core`
- scaffold 境界作成の影響先: `traversal`, `output`, `analyzer-protocol`, `java-analyzer`

## 実装・運用への反映

- spec 更新要否: 要。spec #11 は本 ADR と context への handoff を記録し、durable な判断の正本を ADR / context に移す。
- context / AI 向け設定更新要否: 要。`context/project.yml`、`context/architecture.md`、`context/toolchain.md`、`context/testing.md`、`context/engineering.md` を本 ADR 参照として更新する。

## 関連ドキュメント / チケット

- [design/DesignDoc.md](../design/DesignDoc.md): Core 言語非依存、Analyzer 独立プロセス、Alternatives Considered
- [adr/0001-analyzer-protocol-jsonl-spi.md](0001-analyzer-protocol-jsonl-spi.md): JSONL over STDIN/STDOUT の process SPI
- [design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md](../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md): Protocol / SPI / Model schema の正本
- [specs/11-core-implementation-foundation](../specs/11-core-implementation-foundation/): Core 実装基盤の決定経緯と issue 単位の作業記録
