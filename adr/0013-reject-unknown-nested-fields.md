# ADR-0013: Core は record の最上位の未知の field を無視し、入れ子の object の未知の field を invalid にする

## Status

承認

## Context

ADR-0001 は、互換性の判定について次の 2 つを決めた。

- record の受信者は、対応済みの major バージョンの未知の field を無視する。
- 任意の field の追加は、互換の変更とする。

ADR-0001 は、record の最上位の field と、record に埋め込む object の field を区別していない。  
ADR-0001: [Core と Analyzer を別プロセスにし、JSONL over STDIN/STDOUT の process SPI で結合する](0001-analyzer-protocol-jsonl-spi.md)

Core の parser は、record の最上位について、record の種類ごとに決めた field 名と完全に一致する key だけを取り出して decode する。  
一致しない key は無視する。  
Go の `encoding/json` は、JSON の object を struct へ decode するとき、key と field 名を大文字と小文字を区別せずに対応づける。  
出典: [encoding/json の Unmarshal](https://pkg.go.dev/encoding/json#Unmarshal)

入れ子の object を struct へそのまま decode すると、Core は `sourceLocation` の `Path` という key を `path` として受け付ける。  
その結果、field 名を誤った Analyzer の出力を、Core は成功の解析として扱う。  
Core はコミット 3980273 から、この問題を止めるために入れ子の object の未知の field を invalid にしている。  
この振る舞いは ADR-0001 の「未知の field を無視する」と食い違うので、決定として残す必要がある。

## Decision

Core の parser は、record の最上位の未知の field を、ADR-0001 のとおり無視する。  
Core の parser は、次の object の中の未知の field を、invalid record として拒否する。

- `sourceLocation`
- `callSite`
- `entrypoints[]` の各要素
- `details[]` の各要素と、その要素の `sourceLocation`

field 名の大文字と小文字だけが違う key も、未知の field として拒否する。  
`metadata` の中身は opaque な値なので、Core は中の field を確かめない。  
入れ子の object で未知の field を無視すると、Analyzer の field 名の誤りを Core が成功として扱うためである。

この ADR は、ADR-0001 の決定のうち次の 2 つを狭める。

- 「受信者は未知の field を無視する」は、Core が受信者のときは record の最上位の field に限る。
- 「任意の field の追加は互換の変更とする」は、record の最上位への追加と `metadata` の中への追加に限る。
  - `sourceLocation`、`callSite`、`entrypoints[]`、`details[]` へ field を足す変更は、互換性を壊す変更として扱い、major バージョンを上げる。

Analyzer が `analysisRequest` を受け取るときの扱いは、ADR-0001 のまま変えない。

## Considered Options

| 選択肢                                                                                             | 強み                                                                                                                               | 弱み                                                                                                      |
| -------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| **採用:** record の最上位の未知の field は無視し、入れ子の object の未知の field は invalid にする | 入れ子の object の field 名の誤りを、Core が invalid record として検出する。record の最上位への任意の field の追加は互換のまま残る | 入れ子の object に任意の field を足すときも、major バージョンを上げる                                     |
| 入れ子の object でも未知の field を無視する                                                        | 入れ子の object への任意の field の追加が互換の変更になる                                                                          | `encoding/json` が大文字と小文字を区別しないので、`Path` を `path` として受け付け、誤った出力が成功になる |
| 入れ子の object でも、完全に一致する key だけを取り出し、残りを無視する                            | 大文字と小文字だけが違う key を、既知の field として受け付けない。入れ子の object への任意の field の追加が互換の変更になる        | 任意の field の名前の誤り（`startcolumn` など）を利用者に伝えずに捨て、位置の情報が欠けたまま成功になる   |

## Consequences

### Positive

- Analyzer が入れ子の object の field 名を誤ったとき、Core は request を invalid record による致命的な失敗にする。
- record の最上位への任意の field の追加は、ADR-0001 のとおり互換の変更のまま残る。

### Negative

- `SourceLocation` のような入れ子の object に任意の field を足すときも、major バージョンを上げる必要がある。
- record の最上位の任意の field の名前の誤りは、未知の field として無視されるので、Core は検出できない。
