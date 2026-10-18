---
type: feature-design
title: "Java Analyzer: Protocol への写像"
description: Java の構文要素を JSONL の record へ写す規則（methodId、帰属型、dispatch、metadata、code）
status: stable
governs:
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/normalize
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/attribution
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/graph
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/protocol
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/io
verified_commit: d44c349300a64279c33deec4daf393184b19e42c
---

# Java Analyzer: Protocol への写像

## Overview

Protocol への写像は、**解析の結果を Analyzer Protocol の record へどう写すか**を担う。  
同じメソッドが常に同じ `methodId` になること（正規化）、呼び出しをどの型に帰属させるか、どの metadata を載せるか、失敗をどの code で報告するかを決める。

Java Analyzer の全体と、この文書で使う語（adjacency、provenance）は [Java Analyzer の Design Doc](../../../../../../../../DesignDoc.md) にある。  
JSONL の wire schema そのものは [Analyzer Protocol の Design Doc](../../../../../../../../../../core/internal/protocol/DesignDoc.md) にある。

## Scope

持つものは次のとおり。

- 同じメソッドが常に同じ ID になるための、名前の正規化
- 継承や interface があるときに、呼び出しを**どの型のメソッド**として記録するか（帰属型）
- どこまでを node として出し、どこから先を切るか
- 解析の中で起きた問題を報告する code
- Java に固有の request の metadata の key

持たないものは次のとおり。

- JSONL の wire schema、SPI、process contract。
  - [Analyzer Protocol の Design Doc](../../../../../../../../../../core/internal/protocol/DesignDoc.md) が持つ。
- どの呼び出しを解決するか（型の解決、Spring DI、暗黙の呼び出し）。
  - [解析エンジンの文書](../analysis/DesignDoc.md) が持つ。

## Design

### methodId と signature の正規化

型の表記は erasure と JVM の binary name にし、`methodId` は可読な文字列そのものにする（hash しない）。

| 項目            | 規則                                                                                                                   | 例                                                           |
| --------------- | ---------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| 型名            | JVM の binary name（nested class は `$` で区切る）                                                                     | `com.example.Outer$Inner`                                    |
| generics        | erasure で消す（型引数を持たない）                                                                                     | `List<String>` → `java.util.List`                            |
| 配列と varargs  | erasure の配列の表記に正規化する（varargs は配列として扱う）                                                           | `String...` → `java.lang.String[]`                           |
| `signature`     | `<帰属型の binary name>#<メソッド名>(<引数の型の binary name をカンマで区切る>)`。帰属型は「帰属型の決定規則」で決める | `com.example.UserService#findById(java.lang.Long)`           |
| `qualifiedName` | 表示と debug のための完全修飾名                                                                                        | `com.example.UserService.findById`                           |
| constructor     | メソッド名の token は JVM の表記の `<init>` を使う                                                                     | `com.example.UserService#<init>(com.example.UserRepository)` |
| `methodId`      | `java:` の prefix と `signature`                                                                                       | `java:com.example.UserService#findById(java.lang.Long)`      |

Java の overload の解決は erasure に基づき、erasure だけで overload を区別できる。  
そのため generics を持たない。  
`methodId` を hash しないのは、デバッグのしやすさのために JSONL を選んだ判断と揃えるためである。  
決定性は、文字列を作る規則が決定的であることで満たす。  
ADR-0001: [Analyzer Protocol を JSONL の process SPI にする](../../../../../../../../../../adr/0001-analyzer-protocol-jsonl-spi.md)

匿名クラスのメソッドは、宣言型に binary name を採番して扱う。  
採番は、直近の enclosing class ごとに、1 から始まるソースの出現の順で行う（`com.example.Outer$1`。JVM の binary name と互換がある）。  
`signature` と `methodId` は、ふつうのメソッドと同じ規則で作る。  
ローカルクラスは `Outer$nLocal` の形にし、`n` は同じ名前のローカルクラスの enclosing class の中での出現の順にする。  
lambda は独立した node にしないため、専用の ID を持たない。

### 帰属型の決定規則

帰属型（メソッドが属する型）は、「宣言型を優先し、宣言が scope の外のときだけ、レシーバの静的な型へ引き上げる」。  
ADR-0012: [呼び出しの帰属型を宣言サイトで決め、宣言が scope の外のときだけレシーバの型へ引き上げる](../../../../../../../../../../adr/0012-attribution-by-declaration-site.md)

「宣言型」は、SymbolSolver が override の解決まで済ませた後に返す、そのメソッドの宣言の所在の型を指す。  
本体を持つかは問わず、interface と抽象メソッドの宣言も含む。  
override されていれば override した先の型、されていなければ継承元の型になる。

| 条件                                                                                                  | 帰属型                                                        | 例                                                                                                                                                                                      |
| ----------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 宣言サイトが scope の中                                                                               | 宣言型（宣言の所在の型）                                      | `UserService extends BaseService` で `save` を override していない → `userService.save` の callee は `com.example.BaseService#save`。override していれば `com.example.UserService#save` |
| 宣言サイトが scope の外で、レシーバの静的な型が scope の中で、宣言型が引き上げから外す package でない | レシーバの静的な型へ引き上げる                                | `UserRepository extends JpaRepository` → `userRepository.findById` の callee は `com.example.UserRepository#findById(java.lang.Long)`                                                   |
| 宣言サイトが scope の外で、宣言型が引き上げから外す package                                           | 出力しない                                                    | `userService.toString`（`java.lang.Object#toString`）、`equals`、`hashCode`                                                                                                             |
| 宣言サイトが scope の外で、レシーバの静的な型も scope の外                                            | 出力しない（`methodSymbol` と `callEdge` のどちらも出さない） | `String#equals`、`List#add`                                                                                                                                                             |

規則の各部の理由は次のとおり。

- 引き上げを scope の外に限るのは、引き上げを「宣言が jar の中にあって node にできない」問題を解くためだけに使うためである。
  - 継承元が scope の外であることは、`methodSymbol.metadata` に持つ。
  - 例: `declaringType: "org.springframework.data.repository.CrudRepository"`、`inherited: true`
- scope の外の呼び出しを落とすのは、JDK とライブラリの内部のメソッドをすべて node にすると、影響の調査に価値のないノイズでグラフが埋まるためである。
  - depwalk の用途は自分のコードへの影響の調査で、ライブラリの内部の呼び出し関係は対象の外である。
- scope の外の呼び出しの省略は「解析できなかった」ではなく「仕様として出力しない」である。
  - そのため `JAVA_UNRESOLVED_SYMBOL` の `diagnostic` は出さない。
  - 型の解決そのものに失敗した場合だけ `diagnostic` にする。
- 出力する `callEdge` の caller と callee は、どちらも出力済みの `methodSymbol` を参照する。
  - そのため「valid な `callEdge` は解決済みの `methodSymbol` を参照する」という Protocol の契約を満たす。

#### 引き上げから外す package

何も指定しないときは `java`、`javax`、`jakarta` の下を、引き上げの対象から外す。  
`analysisRequest.metadata` の `liftExcludePackages` を指定すると、外す package を置き換えられる。  
渡す値は、wildcard を含まない package の prefix にする。  
外すかの判定は、宣言型の binary name に対する、`.` で区切った segment の単位の prefix の一致で行う。  
`java` は `java.lang` と `java.util` に一致し、`javafx` には一致しない。

#### その他の呼び出しの形

- static メソッド: 参照した型を「レシーバ」とみなして、同じ規則を当てる。
- `this.foo` と `super.foo`: 宣言サイトが scope の中なら宣言型に帰属するため、揺れない。
- `new Foo`（constructor）: constructor は継承されないため、引き上げは起きない。
  - `Foo` が scope の中なら `com.example.Foo#<init>(...)` を callee にし、scope の外（`new ArrayList<>` など）なら出力しない。

#### 既知の制約: override は追えない

静的な解決のため、基底の型の変数を経由する呼び出しは、基底の型のメソッドに帰属する。  
実行時に呼ばれる override した先には、辺が張られない。  
virtual dispatch の解決の範囲は ADR-0005 が定める。  
SootUp は、型の階層、override、interface の実装の候補の索引にだけ使い、call graph の生成そのものは任せない。  
ADR-0005: [SootUp と Spring DI の解決を導入する](../../../../../../../../../../adr/0005-adopt-sootup-and-spring-di-resolution.md)

#### node として出すメソッド

`fullGraph` の `methodSymbol` は、次の和集合にする。

1. **宣言の列挙**: scope の中で宣言された method、constructor、static initializer のすべて。
   - 呼ばれていないメソッドも node として出力する。
     - caller が 0 件であることを示す必要があり、これが呼び出し元の探索の用途に当たる。
2. **call site 由来**: 引き上げで生じた node（scope の中の型に帰属する、宣言が scope の外のメソッド）。
   - これらは scope の中に宣言がないため、宣言の列挙では出せず、実際に呼び出された箇所からだけ作る。
   - 呼ばれていない継承したライブラリのメソッドは、node にしない。

`reachableFromEntrypoints` は、この母集合のうち、entrypoints から callee の方向に推移的に到達するものに限る。

### symbolKind の割り当て

`method`、`constructor`、static initializer（`initializer`）を node にする。  
lambda は独立した node にしない。  
Protocol の `symbolKind` の enum は変えない。

| Java の構文                                    | 扱い                                                                                     |
| ---------------------------------------------- | ---------------------------------------------------------------------------------------- |
| インスタンスと static のメソッド               | `symbolKind: method`                                                                     |
| コンストラクタ                                 | `symbolKind: constructor`                                                                |
| 匿名クラスのメソッド                           | `symbolKind: method`（宣言型が `Outer$1` になるだけで、実体はふつうのメソッド）          |
| static 初期化ブロック                          | `symbolKind: initializer`（`signature` は `com.example.Foo#<clinit>`）                   |
| インスタンス初期化ブロックとフィールド初期化子 | 独立した node にせず、各 `constructor` に畳み込む（Java のコンパイラの意味論に合わせる） |
| lambda の本体                                  | 独立した node にせず、lambda を字句として囲むメソッドに帰属させる                        |

lambda の本体の中の呼び出しは、囲むメソッドを caller とする `callEdge` として出力する。  
遅れて実行される呼び出しであることは、`callEdge.metadata` に `viaLambda: true` を立てて標識する。  
Core の graph の構築は `metadata` に依存しないため、この追加は契約の上で害がない。

method reference（`this::toDto`、`Foo::bar`、`Foo::new`）も lambda と同じ原則で扱い、独立した node にしない。

- method reference を字句として囲むメソッドを caller とする `callEdge` を出力し、参照先のメソッド（帰属型の決定規則を当てる）を callee にする。
- 遅れて実行されることは、`callEdge.metadata` に `viaMethodReference: true` を立てて標識する。
  - これは `viaLambda` と独立した flag で、method reference が lambda の本体の中に現れた場合は両方が立つ。
- constructor reference（`Foo::new`）は、帰属型の決定規則の `new` の規則を当て、callee を `Foo` の canonical constructor（`<init>`）にする。
  - constructor は継承されないため、引き上げは起きない。
    - `Foo` が scope の外なら出力しない。

### dispatch の標識

DI の解決を行わない場合では、interface と抽象メソッドの呼び出しの callee は、帰属型の決定規則で決まる帰属型（interface と抽象クラスを含む）のメソッドになる。  
実装クラスのメソッドへの辺は、Spring DI の解決が足す。  
interface を越える呼び出しも、宣言型への辺として出す。  
Spring のプロジェクトでは呼び出しの大半が interface を越えるため、辺を落とすと、呼び出し元と呼び出し先の探索の網羅性が実用にならないためである。  
ADR-0012: [呼び出しの帰属型を宣言サイトで決め、宣言が scope の外のときだけレシーバの型へ引き上げる](../../../../../../../../../../adr/0012-attribution-by-declaration-site.md)

`callEdge.metadata.dispatch` に、呼び出しの種別を持たせる。  
値は次の 4 つ。

- `static`: static メソッドの呼び出し
- `virtual`: 具象クラスの instance メソッド
- `interface`: interface を経由する
- `abstract`: 抽象クラスの抽象メソッドを経由する

この標識があると、利用者は「この辺は宣言型で止まっていて、実体ではない」と判別できる。  
実装の候補の辺を足すときの出発点にもなる。

Spring の DI を経由する呼び出し先を実体まで解決することは、ADR-0005 の範囲で満たす。  
DI の解決を行わない場合では、宣言型で止まるのが仕様である。

#### 実装の候補が複数あるとき

複数の dispatch の候補は、call site ごとに、caller から各実装の候補への複数の `CallEdge` として表す。  
宣言型（interface か基底の型）への edge も残し、その metadata は変えない。  
足す実装の候補の edge の metadata は、次で固定する。

| key              | 型       | 値と規則                                                                                                                      |
| ---------------- | -------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `resolution`     | string   | `unique` か `ambiguous`。条件付きの候補を含む場合は、候補が 1 件でも `ambiguous`                                              |
| `provenance`     | string[] | `sootup` と `spring-di` の、重複のない辞書順の配列。両方から同じ candidate の edge が得られた場合は `["sootup", "spring-di"]` |
| `conditional`    | boolean  | 条件アノテーションの付いた候補なら `true`。条件がなければ key を省略する                                                      |
| `conditionTypes` | string[] | 検出した条件アノテーションの FQN を、重複なしの辞書順で入れる。`conditional: true` のときは必須                               |

1 つの call site の実装の候補は、候補のメソッドの signature（宣言型、メソッド名、parameter の型）を key にして 1 件にまとめる。  
SootUp と Spring DI の両方が同じ候補を挙げた場合は、`provenance` を和集合にする。  
`edgeId` は解析の実行の中で振る連番（`java-edge-<連番>`）で、edge の重複の判定には使わない。

#### 暗黙の呼び出しの標識

framework 由来の暗黙の呼び出しは、次の opaque な metadata で標識する。  
Protocol の schema は変えない。  
どの呼び出しを暗黙の呼び出しとして解決するかは、[解析エンジンの文書](../analysis/DesignDoc.md#framework-由来の暗黙の呼び出しの解決) にある。  
ADR-0009: [framework 由来の暗黙の呼び出しと型伝播救済](../../../../../../../../../../adr/0009-implicit-call-resolution-and-type-propagation-rescue.md)

| key                     | 載る record             | 型       | 意味                                                                                             |
| ----------------------- | ----------------------- | -------- | ------------------------------------------------------------------------------------------------ |
| `entryPoint`            | `methodSymbol.metadata` | string[] | framework の entry point として検出したアノテーションの FQN（重複なしの辞書順）。edge は作らない |
| `viaCallableInvocation` | `callEdge.metadata`     | boolean  | functional interface の invocation site から callable の実体へ張った edge。`true` だけを設定する |
| `provenance` の値の追加 | `callEdge.metadata`     | string[] | イベントの edge は `spring-event` を積む（既存の `sootup` と `spring-di` と同列）                |

## Interface

### metadata 契約

`--analyzer-meta key=value` の合成の規則（Core が metadata の JSON を組み立てる規則）は次のとおり。

- 値は常に JSON の配列に積む。
  - 1 回だけ指定した場合も、要素 1 の配列になる（`--analyzer-meta classpath=/a.jar` → `{"classpath": ["/a.jar"]}`）。
- 同じ key の繰り返しは、指定の順に配列へ足す。
- 値が空の文字列の場合は、その key を空の配列として登録する（`--analyzer-meta classpath=` → `{"classpath": []}`）。
- 同じ key の繰り返しと空の値（`key=`）が混在する場合、空の値の指定は、その key がまだ登録されていないときだけ空の配列として登録する。
  - 既に登録された値をリセットしない。
  - よって `classpath=/a.jar` → `classpath=` は `["/a.jar"]`、`classpath=` → `classpath=/a.jar` も `["/a.jar"]` になる。
- 分割は最初の `=` で行う（value の側に `=` を含んでよい）。
  - `=` を含まない指定は、validation error として実行の前に拒否する。

Java に固有の `metadata` の key は次のとおり。

| key                       | 型          | 必須か任意か                                                                                                                                                                                                                     | 意味                                                                                                                                                                                                                                                                                                                    |
| ------------------------- | ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `classpath`               | string 配列 | 明示の `sourceRoots` のときは**必須**（空の配列でもよい）。自動 discovery のときは任意の共通の extra                                                                                                                             | 依存の jar と classes dir の path。自動 discovery では model の compile classpath と classes output を使う                                                                                                                                                                                                              |
| `javaLanguageLevel`       | string 配列 | 明示の `sourceRoots` のときは**必須**（要素 1）。自動 discovery のときは指定を禁止する                                                                                                                                           | parser に渡す canonical な source language level。Analyzer と daemon の JVM から推測しない                                                                                                                                                                                                                              |
| `javaPreview`             | string 配列 | 明示の `sourceRoots` のときだけ任意（要素 1 の `true` か `false`）。自動 discovery のときは指定を禁止する                                                                                                                        | preview の構文を有効にする。parser が対応する language level だけで許す                                                                                                                                                                                                                                                 |
| `liftExcludePackages`     | string 配列 | 任意                                                                                                                                                                                                                             | 引き上げから外す package（帰属型の決定規則）。指定すると既定値（`java`、`javax`、`jakarta`）を置き換える。segment の単位の prefix の一致                                                                                                                                                                                |
| `allowIncompleteAnalysis` | string 配列 | 任意（要素 1 の `true` か `false`、既定は `false`）                                                                                                                                                                              | `true` のとき、すべての救済の後も残る primary diagnostic があっても request を fatal にせず、解決済みの graph（edge と明示の除外）と診断を公開する。完全性の gate そのもの、診断の見え方、`silentOmission == 0` は変えない。規則は [解析エンジンの文書](../analysis/DesignDoc.md#未解決を許して結果を返すopt-in) にある |
| `gradleJavaHome`          | string 配列 | 自動 discovery のときだけ任意（要素 1 の path）。明示の `sourceRoots` のときは使わない。要素の数の違反、空の値、実在しない path、`bin/java`（Windows は `bin/java.exe`）が実行できない path は `JAVA_INVALID_REQUEST` で拒否する | 自動 discovery の Gradle の daemon の JVM を明示して指定する。daemon の JVM が対象の Gradle の互換の範囲を外れる場合に避ける手段で、規則は [discovery の文書](../discovery/DesignDoc.md#daemon-の-jvm-の明示の指定) にある                                                                                              |

未知の key は、Protocol の規則どおり無視する。  
Core はこの表を知らず、Analyzer の側だけが解釈する。

### diagnostic / error code 体系

code は、`JAVA_` の prefix と大文字のスネークケースにする。  
Core は `code` を不透明な文字列として扱うため、code を増やしても Protocol の契約は変わらない。

`diagnostic`（解析を続ける）の code は次のとおり。

| code                        | severity  | 出る場面                                                                                                                                                                                             |
| --------------------------- | --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `JAVA_UNRESOLVED_SYMBOL`    | `warning` | 呼び出し先の型を解決できず、`callEdge` を張れない。stream の中の warning は成功したときだけ有効で、ledger の primary outcome として残れば、終端で `JAVA_INCOMPLETE_ANALYSIS` の request fatal になる |
| `JAVA_ENTRYPOINT_NOT_FOUND` | `warning` | `entrypoints` の method selector に一致する method が見つからない                                                                                                                                    |
| `JAVA_SOOTUP_UNAVAILABLE`   | `warning` | pre-flight を通った後に SootUp が class file を解釈できないか索引にできない、または自分のプロジェクトの bytecode が classpath にない                                                                 |
| `JAVA_RUNTIME_PROVIDED`     | `info`    | Spring Data か MyBatis が実行時に実装を提供するため、意図して解決しない                                                                                                                              |
| `JAVA_AMBIGUOUS_CANDIDATE`  | `warning` | `@Qualifier` と `@Primary` を当てた後も、候補が複数残る                                                                                                                                              |
| `JAVA_CONDITIONAL_BEAN`     | `info`    | 条件付きの Bean を評価せず、候補として残す                                                                                                                                                           |
| `JAVA_SOURCE_ROOT_EXCLUDED` | `warning` | まだ作られていない discovery の source directory、workspace の外の project、または included build を除いた                                                                                           |
| `JAVA_EVENT_UNRESOLVED`     | `warning` | `publishEvent()` の引数の型を静的に解決できず、イベントの edge を張れない                                                                                                                            |
| `JAVA_CALLABLE_UNRESOLVED`  | `info`    | callable が静的に追う範囲（同じメソッドの中、引数渡し 1 段）の外にあり、invocation の edge を張れない。設計の上の制約による対象外を表す                                                              |

`error`（fatal、非ゼロの exit）の code は次のとおり。

| code                        | 出る場面                                                                                                                                            |
| --------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `JAVA_MISSING_CLASSPATH`    | 明示の `sourceRoots` の request の `metadata` に classpath の key がない（空の配列は正当な入力）                                                    |
| `JAVA_MISSING_JAR`          | classpath に指定された jar か classes directory が存在しないか、読めない                                                                            |
| `JAVA_INVALID_REQUEST`      | `analysisRequest` を Java Analyzer として処理できない（対応しない `language` など）                                                                 |
| `JAVA_INTERNAL_ERROR`       | 上のどれでもない、続けられない内部のエラー                                                                                                          |
| `JAVA_PARSE_ERROR`          | parse の pre-flight で 1 件以上の file が失敗した                                                                                                   |
| `JAVA_INCOMPLETE_ANALYSIS`  | すべての救済の後も primary diagnostic outcome が残り、完全な成功の graph を保証できない                                                             |
| `JAVA_INVALID_SOURCE_ROOTS` | 明示か discovery の root の欠落、directory でない、読み取れない、root の包含の関係の曖昧さ、realpath の workspace の外への脱出、binary name の重複  |
| `JAVA_NO_SOURCE_ROOTS`      | discovery と除外の後に、有効な source root が 0 件。Gradle の model が main の source root を 1 件も返さないときは `JAVA_GRADLE_MODEL_ERROR` になる |
| `JAVA_GRADLE_MODEL_ERROR`   | model の非互換、必須の field の欠落、classpath の解決、context の対応、build の評価に失敗した                                                       |

language level の欠落、invalid、曖昧、JavaParser が対応しない（preview を含む）ものは、`JAVA_INVALID_REQUEST` として拒否し、専用の code は設けない。

jar の欠落を fatal にするのは、jar が 1 つ欠けるだけで、広い範囲の型の解決が失敗するためである。  
そのまま続けると「未解決だらけの、一見成功した結果」が出て、利用者が不完全なグラフを正しいと誤認する。  
`diagnostic.sourceLocation` と `relatedMethodId` はできる範囲で埋め、未解決が起きた箇所を追えるようにする。

#### 未解決の呼び出しに付ける診断の情報

`JAVA_INCOMPLETE_ANALYSIS` の `error.details.metadata` には、既存の reason、callKind、target、candidate に加えて、sanitize した診断の 4 項目を含める。

- `resolutionPhase`: 失敗した解決の段階
- `exceptionClass`: resolver の例外のクラスの名前だけ。
  - message は含めない。
- `receiverKind`: receiver の式の種別。
  - AST の node の種別の名前か、実装で定めた固定の表記。
- `receiverTypeResolved`: receiver の型を取得できたか（真偽値）

診断の metadata は、解決に失敗した時点で内部に記録し、その call site が primary diagnostic として終端した場合だけ Protocol へ出力する。  
救済に成功した場合は出力しない。  
`metadata.allowIncompleteAnalysis` で、primary diagnostic が exit 0 のまま残ることがある。  
**この場合も、4 項目を同じ内容で含める。**  
その出力の先は、成功したときに逐次出力される `diagnostic` record である。  
metadata は opaque な key-value で、Protocol の schema は変えない。  
sanitize の制約（ソースの本文、絶対 path、classpath の entry、credential、raw な例外の message の禁止）は保つ。
