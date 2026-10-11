# ADR-0001: Core と Analyzer を別プロセスにし、JSONL over STDIN/STDOUT の process SPI で結合する

## Status

承認

## Context

depwalk は Core を言語非依存に保ち、言語ごとの差異を独立プロセスの Analyzer に閉じ込める。  
Phase1 は Java Analyzer を対象にするが、将来 Kotlin / TypeScript / Vue / Go の Analyzer を追加しても Core を変更しないことを成功条件にする。

Core が Analyzer の内部ライブラリや言語ランタイムに依存すると、Analyzer を追加するたびに Core の build / runtime / release の境界が膨らむ。  
Core と Analyzer の結合点を protocol に限定する必要がある。

## Decision

Core と Analyzer は別プロセスとし、Analyzer SPI は JSONL over STDIN/STDOUT を共通の protocol とする。

### プロセスと入出力

- Core は 1 `analysisRequest` ごとに Analyzer process を 1 つ起動する。
- Core は Analyzer の stdin へ `analysisRequest` record を 1 件送信し、その後 stdin を close する。
- Analyzer は stdout へ `methodSymbol` / `callEdge` / `diagnostic` / `error` record を JSONL で逐次出力する。
- stderr は人間向けの diagnostics とし、Core は protocol record として parse しない。
- exit code `0` は成功、非ゼロは fatal failure とする。

### schemaVersion と互換性の判定

- 全 record の `schemaVersion` は protocol 全体の major version を表す。
  - Phase1 は `"1"` とする。
- record の受信者は、対応済み major version の未知 field を無視し、未対応 major version を拒否する。
- 任意 field の追加は互換変更とする。
- 必須 field の追加と削除、field の型変更、field の意味論変更、record type の削除は breaking change とする。

### streaming と成功結果の公開単位

JSONL の transport streaming と、request 単位での成功結果の公開を分離する。

- Analyzer は graph の全件を buffer せず、stdout へ逐次出力する。
- Core は valid な graph record を受け取った時点で graph の値型へ変換し、request 専用の非公開 staging Graph へ 1-pass で登録する。
  - wire DTO の全件は保持しない。
- exit code `0`、fatal なし、stream 全体の参照完全性の 3 つを満たした場合だけ、Graph と diagnostic を公開する。
- valid な `error` record、非ゼロ exit code、stdout の parse / schema error は、それ以前の graph record と diagnostic をすべて無効にする。
  - fatal な stream には参照完全性を要求せず、staging Graph を破棄する。
- request が fatal でも観測可能にする情報は、Protocol 共通の `error.details` へ正規化する。
  - Core と CLI は Analyzer 固有の code で分岐せず、汎用の表示に留める。

Protocol と Model の具体的な schema は、この ADR では定めない。  
record type ごとの field の定義と互換性の規則は、Analyzer Protocol の Design Doc が持つ。  
Analyzer Protocol / SPI: [core/internal/protocol/DesignDoc.md](../core/internal/protocol/DesignDoc.md)

## Considered Options

| 選択肢                                                                                        | 強み                                                                                                                                   | 弱み                                                                                                                 |
| --------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| **採用:** 別プロセスにし、JSONL over STDIN/STDOUT で結合する。Phase1 は 1 request = 1 process | Core は Analyzer の内部ライブラリや言語ランタイムを知らずに済む。JSONL はテキストで観測でき、debug と contract test の入力に使いやすい | process 起動と IPC の overhead が発生する。timeout、stderr 上限、record サイズ上限などの runtime config が要る       |
| Analyzer を Core にライブラリとして組み込む                                                   | —                                                                                                                                      | Analyzer ごとの言語ランタイム依存が Core に混在し、将来の Analyzer 追加で Core の変更が必要になる                    |
| Core と Analyzer を同一プロセスにする                                                         | 初期実装は単純になる                                                                                                                   | TypeScript / Vue / Go など異なる runtime の Analyzer を追加しづらい                                                  |
| capability handshake / session reuse / incremental analysis を Phase1 の protocol に含める    | —                                                                                                                                      | Analyzer の実装と contract test が複雑になる。Phase1 は 1 request = 1 process に限定し、必要性を測定した後に拡張する |

## Consequences

### Positive

- Core は Analyzer の内部ライブラリや言語ランタイムを知らずに済む。
- 新しい Analyzer を追加しても、Core の内部実装の差分を避けやすい。
- JSONL はテキストで観測でき、debug と contract test の入力に使いやすい。
- stdout の streaming により、大規模な解析結果を一括で読み込まずに処理できる。
- streaming のメモリ特性を保ったまま、利用者へ部分 Graph を成功結果として渡さずに済む。

### Negative

- process 起動と IPC の overhead が発生する。
- Analyzer process の timeout、stderr 上限、record サイズ上限などの runtime config が必要になる。
- session reuse と incremental analysis を初期の protocol に含めないため、短時間に多数の request を投げる用途では後続の拡張が必要になる可能性がある。
