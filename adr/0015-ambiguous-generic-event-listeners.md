# ADR-0015: イベントの edge で、generics で過剰に一致しうる listener も曖昧として扱う

## Status

承認

## Context

ADR-0009 の決定 3 は、イベントの edge を broadcast の意味論の専用の規則で表すと決めた。  
合致した listener への edge は、複数でも、どれも確定（`resolution: unique`）にする。  
曖昧として扱うのは、条件つきの listener だけにする。  
ADR-0009: [framework 由来の暗黙の呼び出しを Protocol の schema を変えずに metadata で表し、receiver の型は型根拠を保つ救済層で導く](0009-implicit-call-resolution-and-type-propagation-rescue.md)

Java Analyzer は、listener と publish の引数を raw type の一致で突合する。

- listener の索引は、listener の parameter の型を erasure した raw type で引けるようにする。
- `publishEvent` の call site では、引数の静的な型とその祖先の raw type で、索引から listener を引く。
- 出典: [EventListenerIndex.java の accept](https://github.com/Fukuemon/depwalk/blob/8e5f43e8bb2fb2ea267bd5eeb27d691cfc255333/analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/spring/EventListenerIndex.java#L54-L85)
- 出典: [EventEdgeEmitter.java の emit](https://github.com/Fukuemon/depwalk/blob/8e5f43e8bb2fb2ea267bd5eeb27d691cfc255333/analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/graph/EventEdgeEmitter.java#L74-L141)

Spring は、generics を使うイベントでは、型引数まで見て listener を選ぶ。  
出典: [Spring Framework の Standard and Custom Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html)

listener の parameter の型が型変数か、型引数の付いた型のとき、raw type の一致は、実行時に呼ばれない listener にも一致しうる。  
この listener への edge を確定として出すと、利用者は、実行されない listener を確定の callee と読み違える。  
実装は、この listener への edge を、条件がなくても `ambiguous` にしている。

## Decision

ADR-0009 の決定 3 で曖昧として扱う listener を、次の 2 つに広げる。  
broadcast の意味論の専用の規則を使うこと、Spring DI の規則を流用しないことを含め、ADR-0009 のほかの決定は変えない。

1. 条件つきの listener。ADR-0009 の決定 3 のとおりである。
2. generics で過剰に一致しうる listener。listener の parameter の型が型変数か、型引数の付いた型のものを指す。

- 2 つ目の listener への edge は、`resolution: ambiguous` にする。
  - 実行時の条件ではないため、`conditional` と `conditionTypes` は付けない。
- どちらにも当たらない listener への edge は、ADR-0009 のとおり、複数でもどれも `resolution: unique` にする。
- 出典: [EventListenerIndex.java の rawApproximation の判定](https://github.com/Fukuemon/depwalk/blob/8e5f43e8bb2fb2ea267bd5eeb27d691cfc255333/analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/spring/EventListenerIndex.java#L68-L70)
- 出典: [EventEdgeEmitter.java の resolution の決定](https://github.com/Fukuemon/depwalk/blob/8e5f43e8bb2fb2ea267bd5eeb27d691cfc255333/analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/graph/EventEdgeEmitter.java#L130-L138)

理由は、raw type の一致による近似で一致した listener を確定として示さないためである。  
edge は残すため、影響の伝播は途切れない。

## Considered Options

| 選択肢                                                                         | 強み                                                                                  | 弱み                                                                                                             |
| ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| **採用:** generics で過剰に一致しうる listener への edge を `ambiguous` にする | 近似で一致した listener を確定として示さない。edge を残すため、影響の伝播が途切れない | 実行時に呼ばれる generics の listener も `ambiguous` と表示される                                                |
| ADR-0009 の決定 3 のまま、条件のない listener はすべて `unique` にする         | 規則が単純                                                                            | 実行時に呼ばれない listener への edge が確定と表示され、利用者が確度を読み違える                                 |
| 型引数まで突合する                                                             | Spring と同じ基準で listener を選べる                                                 | 型引数の代入可能性の判定を新たに実装する必要がある。引数の静的な型の型引数を導けない場合の扱いも決める必要がある |
| generics の listener を突合の対象から外す                                      | 誤った edge を出さない                                                                | 実行時に呼ばれる listener への edge も消え、影響の伝播が途切れる                                                 |

## Consequences

### Positive

- raw type の一致による近似で一致した listener を、利用者が確定の callee と読み違えない。
- generics の listener への edge を残すため、caller と callee の探索が途切れない。

### Negative

- 実行時に必ず呼ばれる generics の listener への edge も `ambiguous` と表示され、確度が実際より低く見える。
- `ambiguous` の理由が条件か generics かは、`conditional` の有無からしか読み取れない。
