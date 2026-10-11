# ADR-0012: 呼び出しの帰属型を宣言サイトで決め、宣言が scope の外のときだけレシーバの型へ引き上げる

## Status

承認

## Context

- Java Analyzer は、呼び出しの callee をどの型のメソッドとして記録するか（帰属型）を決める必要がある。
  - 規則の全体は [Protocol への写像の Design Doc](../analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/protocol/DesignDoc.md) が持つ。
- 継承では、override していないメソッドの宣言は継承元の型にあり、override したメソッドの宣言はその型にある。
- 宣言が依存の jar の中にあるメソッドは、scope の外なので node にできない。
- Spring のプロジェクトでは、呼び出しの大半が interface を越える。
- depwalk の用途は、呼び出し元と呼び出し先を網羅して、自分のコードへの影響を調べることである。

## Decision

帰属型は、宣言型（SymbolSolver が override の解決まで済ませた後に返す宣言の所在の型）を優先する。  
宣言が scope の外のときだけ、レシーバの静的な型へ引き上げる。  
実際の宣言サイトを使えば、実在しない node の合成と、dead node のどちらも起きないためである。

interface と抽象メソッドの呼び出しも、宣言型（interface と抽象クラスを含む）のメソッドへの辺として出し、`callEdge.metadata.dispatch` で種別を標識する。  
辺を落とすと、呼び出し元と呼び出し先の探索の網羅性が実用にならないためである。

## Considered Options

| 選択肢                                                       | 強み                                                                   | 弱み                                                                                                     |
| ------------------------------------------------------------ | ---------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| **採用:** 宣言サイトに帰属し、scope の外のときだけ引き上げる | 実在しない node を合成せず、override した node も dead node にならない | —                                                                                                        |
| 常にレシーバの型へ帰属する                                   | —                                                                      | scope の中の継承（override なし）で、実在しない node が合成され、node が分裂する                         |
| 常に根の基底の型へ集約する                                   | —                                                                      | override した node が dead node になり、影響の調査ができない                                             |
| interface を越える呼び出しを未解決の `diagnostic` にする     | —                                                                      | Spring のプロジェクトでは呼び出しの大半が interface を越えるため、辺が落ちて探索の網羅性が実用にならない |

## Consequences

### Positive

- scope の中の継承でも、node が分裂せず、override した node から影響を調べられる。
- interface を越える呼び出しも辺になり、`dispatch` の標識で「宣言型で止まっていて実体ではない」と判別できる。

### Negative

- 静的な解決のため、基底の型の変数を経由する呼び出しは基底の型のメソッドに帰属し、実行時に呼ばれる override した先には辺が張られない。
