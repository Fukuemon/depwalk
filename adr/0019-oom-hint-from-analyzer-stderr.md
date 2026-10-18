# ADR-0019: Analyzer が error の record を出さずに異常終了したとき、Core は stderr の OutOfMemoryError から heap の不足を案内する

## Status

承認

## Context

ADR-0001 は、Core と Analyzer を Protocol だけで結合し、Core は Analyzer の内部と言語のランタイムを知らない、と決めた。  
ADR-0003 は、Core は `java`、jar、JVM の存在を知らない、と決めた。

- ADR-0001: [Core と Analyzer を別プロセスにし、JSONL over STDIN/STDOUT の process SPI で結合する](0001-analyzer-protocol-jsonl-spi.md)
- ADR-0003: [Analyzer の起動コマンドを、Core が解釈しない言語非依存の文字列として解決する](0003-analyzer-command-resolution.md)

大きな workspace を解析すると、Java Analyzer の JVM が heap を使い切り、`java.lang.OutOfMemoryError` で止まることがある。  
heap を使い切った JVM は、`error` の record を書き出す前に終了することがある。  
このとき Core が受け取るのは、0 でない終了コードと、`OutOfMemoryError` を含む stderr だけである。

Core は今、`core/internal/protocol` の `heapExhausted` で、次の 3 つがそろったときに heap の不足と判定する。

- Analyzer の終了コードが 0 でない。
- 正しい `error` の record がない。
- stderr に `java.lang.OutOfMemoryError` を含む。

判定したときは、`--analyzer-cmd` の java の起動に `-Xmx` を足すか増やすよう案内する。  
この stderr の確認は ADR-0001 と ADR-0003 の原則の例外であり、決定として残す必要がある。

## Decision

Core は、上の 3 つの条件がそろったときに限り、Analyzer の stderr から `java.lang.OutOfMemoryError` を探し、heap の不足を案内する。  
stderr の確認は、終了した後のエラーの表示だけに使い、解析の結果の解釈と graph の構築には使わない。  
終了コードは、ほかの Analyzer の異常終了と同じく 1 にする。

ADR-0001 と ADR-0003 の「Core は言語のランタイムを知らない」は、この確認を除いた範囲で読む。  
ほかの決定は変えない。

例外にする理由は次の 2 つ。

- heap の不足は、Java Analyzer を大きな workspace に使うときに最も多い失敗で、利用者が自分で直せる。
- heap を使い切った JVM は自分で `error` の record を出せないことがあるので、Analyzer の側だけでは案内を出せない。

2 つ目以降の言語の Analyzer を足すときは、その言語のランタイムに固有の確認を Core に足さない。  
その Analyzer の失敗は、`error` の record で報告する。

## Considered Options

| 選択肢                                                            | 強み                                              | 弱み                                                                                   |
| ----------------------------------------------------------------- | ------------------------------------------------- | -------------------------------------------------------------------------------------- |
| **採用:** Core が stderr を確かめ、条件がそろったときだけ案内する | 利用者が heap の不足にすぐ気づき、直し方が分かる  | Core が Java のランタイムの名前を 1 つ知る                                             |
| Analyzer の側で heap を見積もるか、JVM の起動の仕組みで報告する   | Core を言語のランタイムから切り離したままにできる | 見積もりは外れうる。JVM の仕組みは起動のコマンドの形を Analyzer の側で決めることになる |
| 案内を出さない                                                    | 原則に例外を作らない                              | 利用者は Analyzer の stderr を読んで原因を探す                                         |

## Consequences

### Positive

- heap の不足で止まったとき、利用者は `-Xmx` を足せばよいと分かる。
- stderr の確認はエラーの表示に限るので、解析の結果は stderr の内容に左右されない。

### Negative

- Core に、Java のランタイムの例外の名前が 1 つ入る。
- JVM が例外の名前を変えると、案内が出なくなる。そのときも、終了コードと Analyzer の stderr は今までどおり利用者に届く。
