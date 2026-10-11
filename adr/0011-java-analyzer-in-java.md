# ADR-0011: Java Analyzer を Java で実装し、Kotlin を採らない

## Status

承認

## Context

- Java Analyzer は、Java/Spring のソースを読み、メソッドの呼び出し関係を抽出する言語別の Analyzer である。
  - 設計は [Java Analyzer の Design Doc](../analyzers/java/DesignDoc.md) が持つ。
- Java Analyzer は、JDK 25 で動き、JavaParser と SymbolSolver を使う。
- 将来、Kotlin のソースを解析する Analyzer を足す可能性がある。

## Decision

Java Analyzer の実装の言語は Java にし、Kotlin を採らない。  
理由は次の 3 つ。

- JDK 25 の sealed interface、record、pattern matching で、Kotlin の主な利点（代数的データ型、網羅性のチェック）が Java だけで得られる。
- JavaParser との interop では型が platform type になり、Kotlin の null 安全が働かない。
- Kotlin で書くと、将来の「Kotlin Analyzer」と名前が紛れる。

## Considered Options

| 選択肢         | 強み                                                                                                | 弱み                                                                                                              |
| -------------- | --------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| **採用:** Java | JDK 25 の sealed interface、record、pattern matching で、代数的データ型と網羅性のチェックが得られる | —                                                                                                                 |
| Kotlin         | 代数的データ型と網羅性のチェックを持つ                                                              | JavaParser との interop では型が platform type になり、null 安全が働かない。将来の Kotlin Analyzer と名前が紛れる |

## Consequences

### Positive

- 代数的データ型と網羅性のチェックを、Java だけで使える。
- 将来の Kotlin Analyzer と名前が紛れない。

### Negative

- —
