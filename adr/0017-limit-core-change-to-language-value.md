# ADR-0017: 2 つ目以降の言語の Analyzer を足すとき、Core の変更を protocol の許す language の値の追加に限る

## Status

承認

## Context

ADR-0001 と ADR-0003 は、2 つ目以降の言語の Analyzer を足しても Core を変えずに済むことを、成功条件にした。

- ADR-0001: [Core と Analyzer を別プロセスにし、JSONL over STDIN/STDOUT の process SPI で結合する](0001-analyzer-protocol-jsonl-spi.md)
- ADR-0003: [Analyzer の起動コマンドを、Core が解釈しない言語非依存の文字列として解決する](0003-analyzer-command-resolution.md)

Core の `core/internal/protocol` は、`analysisRequest.language` と `methodSymbol.language` を検証する。  
Core が受け付ける値は `supportedLanguages` の一覧にあり、今は `java` だけである。  
一覧にない値の record は invalid になり、CLI は `--language` の誤りとして終了コード 2 を返す。  
このため、2 つ目の言語の Analyzer を足すには、Core の一覧に値を 1 つ足す必要がある。

## Decision

2 つ目以降の言語の Analyzer を足すとき、Core の変更は `core/internal/protocol` の `supportedLanguages` に値を 1 つ足すことに限る。  
Core のほかの package は変えない。

ADR-0001 と ADR-0003 の「Core を変更しない」は、この 1 か所の追加を除いた範囲で読む。  
ほかの決定は変えない。

Core が言語の値を検証するのは、利用者が `--language` を誤ったときに、Analyzer を起動する前に入力の誤りとして返すためである。  
値を検証しないと、Core は誤った言語のまま Analyzer を起動し、Analyzer ごとに違う形の失敗が返る。

## Considered Options

| 選択肢                                                       | 強み                                                             | 弱み                                                             |
| ------------------------------------------------------------ | ---------------------------------------------------------------- | ---------------------------------------------------------------- |
| **採用:** Core が許す値の一覧を持ち、言語ごとに値を 1 つ足す | `--language` の誤りを Analyzer の起動の前に終了コード 2 で返せる | 言語を足すたびに Core の 1 か所を変える                          |
| Core は language を検証せず、Analyzer へそのまま渡す         | 言語を足しても Core を変えない                                   | `--language` の誤りが Analyzer ごとに違う形の失敗になる          |
| 許す値を Analyzer から問い合わせる                           | Core の一覧を持たずに検証できる                                  | Analyzer を起動する前に検証できず、Protocol に問い合わせが増える |

## Consequences

### Positive

- 利用者が `--language` を誤ったとき、Analyzer の種類によらず同じ入力の誤りとして返る。
- 言語を足すときの Core の変更が、1 か所の値の追加に決まる。

### Negative

- 2 つ目以降の言語の Analyzer を足すとき、Core のリリースが要る。
