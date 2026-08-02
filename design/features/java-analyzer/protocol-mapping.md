---
type: feature-design
title: "Java Analyzer: Protocol への写像"
description: Java の構文要素を JSONL record へ写す規則 (methodId / signature / 帰属型 / metadata / diagnostic)
status: 完了
keywords:
  [methodId, signature, 帰属型, dispatch, symbolKind, metadata, diagnostic]
governs:
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/normalize
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/attribution
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/graph
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/protocol
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/io
verified_commit: 2d82ed3
---

# Java Analyzer: Protocol への写像

**解析結果を Analyzer Protocol の record へどう写すか**の正本。

同じメソッドが常に同じ `methodId` になること (正規化)、呼び出しをどの型に帰属させるか、どの metadata を載せるか、失敗をどの code で報告するかを定める。

wire schema そのものは [analyzer-protocol feature doc](../analyzer-protocol/DesignDoc_analyzer-protocol.md) が正本であり、本 doc は Java 固有の写像規則だけを扱う。親 doc は [DesignDoc_java-analyzer.md](DesignDoc_java-analyzer.md)。用語 (adjacency / provenance / dispatch) は親 doc の「前提」節を参照する。

## この doc が答えること

- 同じメソッドが常に同じ ID になるようにするには、名前をどう正規化するか
- 継承や interface があるとき、呼び出しを**どの型のメソッド**として記録するか (帰属型)
- どこまでを node として出し、どこから先を切るか
- 解析中に起きた問題を、どの code で報告するか

## 正規化規則 (methodId / signature)

型表記は erasure + JVM binary name とし、`methodId` は可読な文字列そのものとする (hash しない)。

| 項目            | 規則                                                                                                                                | 例                                                           |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| 型名            | JVM binary name (nested class は `$` 区切り)                                                                                        | `com.example.Outer$Inner`                                    |
| generics        | erasure で消去する (型引数を保持しない)                                                                                             | `List<String>` → `java.util.List`                            |
| 配列 / varargs  | erasure の配列表記に正規化する (varargs は配列として扱う)                                                                           | `String...` → `java.lang.String[]`                           |
| `signature`     | `<帰属型の binary name>#<メソッド名>(<引数型の binary name をカンマ区切り>)` (帰属型の決定規則は「帰属型の決定規則」節を正本とする) | `com.example.UserService#findById(java.lang.Long)`           |
| `qualifiedName` | 表示・debug 用の完全修飾名                                                                                                          | `com.example.UserService.findById`                           |
| constructor     | メソッド名 token は JVM 表記の `<init>` を用いる                                                                                    | `com.example.UserService#<init>(com.example.UserRepository)` |
| `methodId`      | `java:` prefix + `signature`                                                                                                        | `java:com.example.UserService#findById(java.lang.Long)`      |

Java の overload 解決は erasure ベースであり、erasure だけで overload の区別に十分であるため generics を保持する必要はない。`methodId` を hash しないのは、JSONL がデバッグ容易性のために選ばれた性質と一貫させるためであり、決定性は文字列生成規則が決定的であることで満たす。

匿名クラスのメソッドは、宣言型に binary name を採番して扱う。採番は直近の enclosing class ごとに 1 始まりのソース出現順で行う (`com.example.Outer$1`、JVM binary name 互換)。`signature` / `methodId` は通常のメソッドと同じ規則で作る。ローカルクラスは `Outer$1Local` 形式 (n は同名ローカルクラスの enclosing class 内出現順)。lambda は独立 node にしないため専用の ID を持たない。

## 帰属型の決定規則

帰属型 (メソッドが属する型) は「宣言型を優先し、宣言が scope 外のときだけレシーバの静的型へ引き上げる」。

「宣言型」は SymbolSolver が override 解決まで済ませた後に返す、そのメソッド宣言の所在型を指す (本体を持つかどうかは問わない — interface / 抽象メソッドの宣言もここに含む)。override されていれば override 先の型、されていなければ継承元の型になる。

| 条件                                                                                          | 帰属型                                                | 例                                                                                                                                                                                      |
| --------------------------------------------------------------------------------------------- | ----------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 宣言サイトが scope 内                                                                         | 宣言型 (宣言の所在型)                                 | `UserService extends BaseService` で `save` を override していない → `userService.save` の callee は `com.example.BaseService#save`。override していれば `com.example.UserService#save` |
| 宣言サイトが scope 外で、レシーバの静的型が scope 内、かつ宣言型が引き上げ除外 package でない | レシーバの静的型へ引き上げる                          | `UserRepository extends JpaRepository` → `userRepository.findById` の callee は `com.example.UserRepository#findById(java.lang.Long)`                                                   |
| 宣言サイトが scope 外で、宣言型が引き上げ除外 package                                         | 出力しない                                            | `userService.toString` (`java.lang.Object#toString`) / `equals` / `hashCode`                                                                                                            |
| 宣言サイトが scope 外で、レシーバの静的型も scope 外                                          | 出力しない (`methodSymbol` / `callEdge` とも出さない) | `String#equals` / `List#add`                                                                                                                                                            |

### 引き上げから外す package

既定で `java` / `javax` / `jakarta` 配下を引き上げ対象から除外する (`liftExcludePackages` に渡す正規値は wildcard を含まない package prefix)。`analysisRequest.metadata` の `liftExcludePackages` で除外 package を上書き (置き換え) 可能にする。除外判定は宣言型の binary name に対する `.` 区切り segment 単位の prefix 一致で行う (`java` は `java.lang` / `java.util` に一致し、`javafx` には一致しない)。

### その他の呼び出し形

- static メソッド: 「レシーバ」を参照した型とみなして同一規則を適用する。
- `this.foo` / `super.foo`: 宣言サイトが scope 内なら宣言型に帰属するため揺れない。
- `new Foo` (constructor): constructor は継承されないため引き上げは発生しない。`Foo` が scope 内なら `com.example.Foo#<init>(...)` を callee とし、scope 外 (`new ArrayList<>` 等) なら出力しない。

### なぜこの規則にするか

- 宣言サイト基準の根拠: 「常にレシーバ型へ帰属」だと scope 内継承 (override なし) で実在しない node が合成され node 分裂を招く。「常に根の基底へ集約」だと override した node が dead node になり影響調査ができない。実際の宣言サイトを使えばどちらの病理も起きない。
- 引き上げを scope 外に限る根拠: 引き上げは「宣言が jar の中にあって node にできない」問題を解くためだけに使う。継承元が scope 外であることは `methodSymbol.metadata` に保持する (例: `declaringType: "org.springframework.data.repository.CrudRepository"`, `inherited: true`)。
- scope 外呼び出しを落とす根拠: JDK / library 内部メソッドをすべて node 化すると影響調査に無価値なノイズでグラフが埋まる。depwalk の用途は自分のコードへの影響調査であり、library 内部の呼び出し関係は対象外。
- 未解決との区別: scope 外呼び出しの省略は「解析できなかった」ではなく「仕様として出力しない」ため、`JAVA_UNRESOLVED_SYMBOL` の `diagnostic` は出さない。型解決自体に失敗した場合のみ `diagnostic` とする。
- protocol 整合: 出力する `callEdge` の caller / callee はいずれも出力済み `methodSymbol` を参照するため、「valid な `callEdge` は解決済み `methodSymbol` を参照する」という契約を満たす。

### 既知の制約: override は追えない

静的解決のため、基底型の変数経由の呼び出しは基底型のメソッドに帰属し、実行時に呼ばれる override 先には辺が張られない。virtual dispatch の解決は [ADR-0005](../../../adr/0005-adopt-sootup-and-spring-di-resolution.md) の範囲とする。SootUp は型階層・override・interface 実装候補の索引としてのみ使用し、call graph 生成そのものは委譲しない (。、2026-07-12)。本 doc を正本とする。

### どのメソッドを node として出すか

`fullGraph` の `methodSymbol` は次の和集合とする。

1. **宣言列挙**: scope 内で宣言された method / constructor / static initializer のすべて。呼ばれていないメソッドも node として出力する (caller が 0 件であることを示せる = S1 の用途に必要)。
2. **call site 由来**: 引き上げで生じた node (scope 内型に帰属する、宣言が scope 外のメソッド)。これらは scope 内に宣言が存在しないため宣言列挙では出せず、実際に呼び出された箇所からのみ生成する。呼ばれていない継承 library メソッドは node 化しない。

`reachableFromEntrypoints` は上記母集合のうち、entrypoints から callee 方向に推移的に到達するものに限る。

## symbolKind の割り当て

`method` + `constructor` + static initializer (`initializer`) を node 化する。lambda は独立 node にしない。protocol の `symbolKind` enum は変更しない。

| Java の構文                                     | 扱い                                                                              |
| ----------------------------------------------- | --------------------------------------------------------------------------------- |
| インスタンス / static メソッド                  | `symbolKind: method`                                                              |
| コンストラクタ                                  | `symbolKind: constructor`                                                         |
| 匿名クラスのメソッド                            | `symbolKind: method` (宣言型が `Outer$1` になるだけで実体は通常のメソッド)        |
| static 初期化ブロック                           | `symbolKind: initializer` (`signature` は `com.example.Foo#<clinit>`)             |
| インスタンス初期化ブロック / フィールド初期化子 | 独立 node にせず、各 `constructor` に畳み込む (Java コンパイラの意味論に合わせる) |
| lambda 本体                                     | 独立 node にせず、lambda を字句的に囲むメソッドに帰属させる                       |

lambda 本体内の呼び出しは、囲みメソッドを caller とする `callEdge` として出力する。遅延実行される呼び出しであることは `callEdge.metadata` に `viaLambda: true` を立てて標識する (Core の graph 構築は `metadata` に依存しないため契約上は無害)。

method reference (`this::toDto` / `Foo::bar` / `Foo::new`) も lambda と同じ原則で扱う。独立した node にはしない。method reference を字句的に囲むメソッドを caller とする `callEdge` を出力し、参照先メソッド (「帰属型の決定規則」節を適用) を callee とする。遅延実行であることは `callEdge.metadata` に `viaMethodReference: true` を立てて標識する (`viaLambda` とは独立した flag。method reference が lambda 本体の中に現れた場合は両方が立つ)。constructor reference (`Foo::new`) は「帰属型の決定規則」節の `new` 規則を適用し、callee を `Foo` の canonical constructor (`<init>`) とする。constructor は継承されないため引き上げは発生しない。`Foo` が scope 外なら出力しない。

## dispatch 標識

DI 解決を行わない経路では、interface / 抽象メソッド呼び出しの callee は「帰属型の決定規則」で決まる帰属型 (interface / 抽象クラスを含む) のメソッドになる。実装クラスのメソッドへの辺は Spring DI 解決 ([ADR-0005](../../../adr/0005-adopt-sootup-and-spring-di-resolution.md)) が追加する。

`callEdge.metadata.dispatch` に呼び出しの種別を持たせる。値は 4 つ。

- `static`: static メソッド呼び出し
- `virtual`: 具象クラスの instance メソッド
- `interface`: interface 経由
- `abstract`: 抽象クラスの抽象メソッド経由利用者は「この辺は宣言型止まりで実体ではない」と判別でき、実装候補の辺を足すときの土台にもなる。

未解決 `diagnostic` に倒す案は採らない。Spring プロジェクトでは呼び出しの大半が interface 越しであり、辺を落とすと S1 / S2 (網羅性) が実用にならないため。

成功条件 S4「Spring DI 経由の呼び出し先を実体まで解決できる」は [ADR-0005](../../../adr/0005-adopt-sootup-and-spring-di-resolution.md) の範囲で満たす。DI 解決を行わない経路では宣言型止まりになるのが仕様である。

### 実装候補が複数あるとき

複数の dispatch 候補は call site ごとに caller → 各実装候補への複数 `CallEdge` として表現し、宣言型 (interface / 基底型) への既存 edge も保持する。宣言型 edge の既存 metadata は変更しない。追加する実装候補 edge の metadata は次で固定する。本 doc を正本とする。

| key              | 型       | 値 / 規則                                                                                                             |
| ---------------- | -------- | --------------------------------------------------------------------------------------------------------------------- |
| `resolution`     | string   | `unique` または `ambiguous`。条件付き候補を含む場合は候補が 1 件でも `ambiguous`                                      |
| `provenance`     | string[] | `sootup` / `spring-di` の重複なし・辞書順配列。両方から同じ candidate edge が得られた場合は `["sootup", "spring-di"]` |
| `conditional`    | boolean  | 条件アノテーション付き候補なら `true`。条件なしでは key を省略                                                        |
| `conditionTypes` | string[] | 検出した条件アノテーションの FQN を重複なし・辞書順で格納。`conditional: true` のとき必須                             |

edge の重複判定は caller / callee / call site から生成する既存 `edgeId` 単位で行う。同一 edge を複数解析器が報告した場合は edge を 1 件に統合し、`provenance` を和集合にする。

## metadata 契約

`--analyzer-meta key=value` の合成規則 (Core が metadata の JSON を組み立てる規則):

- 値は常に JSON 配列に積む。1 回だけ指定した場合も要素 1 の配列になる (`--analyzer-meta classpath=/a.jar` → `{"classpath": ["/a.jar"]}`)。
- 同一 key の繰り返しは、指定順に配列へ追加する。
- 値が空文字列の場合、その key を空配列として登録する (`--analyzer-meta classpath=` → `{"classpath": []}`)。
- 同一 key の繰り返しと空値 (`key=`) が混在する場合: 空値指定はその key を (未登録の場合のみ) 空配列として登録する。既登録の値をリセットしない。よって `classpath=/a.jar` → `classpath=` は `["/a.jar"]`、`classpath=` → `classpath=/a.jar` も `["/a.jar"]`。
- 分割は最初の `=` で行う (value 側に `=` を含んでよい)。`=` を含まない指定は validation error として実行前に拒否する。

Java 固有の `metadata` key:

| key                       | 型          | 必須/任意                                                                               | 意味                                                                                                                                                                                                                                |
| ------------------------- | ----------- | --------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `classpath`               | string 配列 | 明示 `sourceRoots` 時は **必須** (空配列可)。自動 discovery 時は任意の共通 extra        | 依存 jar / classes dir の path。自動 discovery では model の compile classpath / classes output を使用する                                                                                                                          |
| `javaLanguageLevel`       | string 配列 | 明示 `sourceRoots` 時は **必須** (要素 1)。自動 discovery 時は指定禁止                  | parser に渡す canonical source language level。Analyzer / daemon JVM から推測しない                                                                                                                                                 |
| `javaPreview`             | string 配列 | 明示 `sourceRoots` 時のみ任意 (要素 1 の `true` / `false`)。自動 discovery 時は指定禁止 | preview 構文の有効化。parser が対応する language level のみ許可                                                                                                                                                                     |
| `liftExcludePackages`     | string 配列 | 任意                                                                                    | 引き上げ除外 package (帰属型決定規則)。指定時は既定値 (`java` / `javax` / `jakarta`) を置き換える。segment 単位 prefix 一致                                                                                                         |
| `allowIncompleteAnalysis` | string 配列 | 任意 (要素 1 の `true` / `false`、既定 `false`)                                         | `true` のとき、全救済後も残る primary diagnostic があっても request を fatal にせず、解決済み graph (edge / 明示除外) と診断を公開する。完全性 gate 自体・診断の可視性・`silentOmission == 0` は変更しない (詳細は完全性 gate の節) |

未知 key は protocol の規則どおり無視する。Core は本表を知らない (Analyzer 側のみが解釈する)。

## diagnostic / error code 体系

`JAVA_` prefix + 大文字スネークケースとする。Core は `code` を不透明な文字列として扱うため契約変更は発生しない。

`diagnostic` (解析継続):

| code                        | severity  | 出る場面                                                                                                                                                                                  |
| --------------------------- | --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `JAVA_UNRESOLVED_SYMBOL`    | `warning` | 呼び出し先の型が解決できず `callEdge` を張れない。stream 中の warning は成功時のみ有効で、ledger の primary outcome として残れば終端で `JAVA_INCOMPLETE_ANALYSIS` の request fatal になる |
| `JAVA_ENTRYPOINT_NOT_FOUND` | `warning` | `entrypoints` の method selector に一致する method が見つからない                                                                                                                         |
| `JAVA_SOOTUP_UNAVAILABLE`   | `warning` | pre-flight 通過後に SootUp が class file を解釈・索引化できない、または自プロジェクト bytecode が classpath にない                                                                        |
| `JAVA_RUNTIME_PROVIDED`     | `info`    | Spring Data / MyBatis が実行時に実装を提供するため意図的に解決しない                                                                                                                      |
| `JAVA_AMBIGUOUS_CANDIDATE`  | `warning` | `@Qualifier` / `@Primary` 適用後も候補が複数残る                                                                                                                                          |
| `JAVA_CONDITIONAL_BEAN`     | `info`    | 条件付き Bean を評価せず候補として保持する                                                                                                                                                |
| `JAVA_SOURCE_ROOT_EXCLUDED` | `warning` | 未作成のdiscovery source directory、external included buildのproject、またはcomposite / included buildを除外した                                                                          |

`error` (fatal / 非ゼロ exit):

| code                        | 出る場面                                                                                                                |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `JAVA_MISSING_CLASSPATH`    | 明示 `sourceRoots` request の `metadata` に classpath key が無い (空配列は正当な入力)                                   |
| `JAVA_MISSING_JAR`          | classpath に指定された jar または classes directory が存在しない / 読めない (fatal、既存 code を再利用)                 |
| `JAVA_INVALID_REQUEST`      | `analysisRequest` が Java Analyzer として処理できない (未対応 `language` 等)                                            |
| `JAVA_INTERNAL_ERROR`       | 上記以外の継続不能な内部エラー                                                                                          |
| `JAVA_PARSE_ERROR`          | parse pre-flight で 1 件以上の file が失敗した                                                                          |
| `JAVA_INCOMPLETE_ANALYSIS`  | 全救済後も primary diagnostic outcome が残り、完全な成功 graph を保証できない                                           |
| `JAVA_INVALID_SOURCE_ROOTS` | 明示 / discovery rootの欠落・非directory・読取不能、root包含関係のambiguity、realpathのworkspace外脱出、binary name重複 |
| `JAVA_NO_SOURCE_ROOTS`      | discoveryと除外後に有効なsource rootが0件                                                                               |
| `JAVA_GRADLE_MODEL_ERROR`   | model非互換、必須field欠落、classpath解決、context対応、build評価に失敗した                                             |

language level の欠落・invalid・曖昧・JavaParser 非対応 (preview を含む) は `JAVA_INVALID_REQUEST` として拒否する。専用の code は設けない。

jar 欠落を fatal にするのは、jar が 1 つ欠けるだけで広範囲の型解決が失敗し、継続すると「未解決だらけの、一見成功した結果」が出て利用者が不完全なグラフを正と誤認するリスクが高いため。`diagnostic.sourceLocation` と `relatedMethodId` を可能な範囲で埋め、未解決の発生箇所を追跡できるようにする。

### 未解決の呼び出しに付ける診断情報

`JAVA_INCOMPLETE_ANALYSIS` の `error.details.metadata` には、既存の reason / callKind / target / candidate に加えて、sanitize 済みの診断 4 項目を含める。

- `resolutionPhase` — 失敗した解決段階
- `exceptionClass` — resolver 例外のクラス名のみ (message は含めない)
- `receiverKind` — receiver 式種別 (AST ノード種別名、または実装で定義した固定表記)
- `receiverTypeResolved` — receiver 型を取得できたか (真偽値)
  診断 metadata は解決失敗時点で内部記録し、その call site が primary diagnostic として終端した場合のみ Protocol へ出力する (救済成功時は出力しない)。**`metadata.allowIncompleteAnalysis` で primary diagnostic が exit 0 のまま残る場合も、この 4 項目は同じ内容で含める。** 出力先は成功時に逐次出力される `diagnostic` record になる。 metadata は opaque な key-value であり Protocol schema は変更しない。sanitize 制約 (source 本文・絶対 path・classpath entry・credential・raw exception message の禁止) を維持する。本 doc を正本とする。
