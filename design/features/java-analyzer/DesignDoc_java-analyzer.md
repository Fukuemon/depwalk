# Feature 設計: Java Analyzer

> 最終更新: 2026-07-12 / Status: 完了

Java/Spring ソースの AST 解析・型解決・CallGraph 生成を担う言語別 Analyzer の durable な feature 設計正本。本 doc が Java Analyzer 設計の正本。決定経緯と issue 単位の作業記録は [spec #9](../../../specs/9-java-analyzer/) を参照する。共通契約 (SPI / JSONL Protocol / Model schema) は [Analyzer Protocol / SPI feature doc](../analyzer-protocol/DesignDoc_analyzer-protocol.md) と [ADR-0001](../../../adr/0001-analyzer-protocol-jsonl-spi.md) が正本であり、本 doc は契約を変更せず Java 側の実装方式を定める。

## メタ

| 項目           | 値                                                                                                                                                                                                                                                                                                                               |
| -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 関連 PRD 要求  | 統合モードのため [DesignDoc の Why / What](../../DesignDoc.md#提供価値--成功条件-what)                                                                                                                                                                                                                                           |
| 関連 DesignDoc | [成功条件 S1/S2/S4/S5](../../DesignDoc.md#提供価値--成功条件-what)、[モジュール責務 Java Analyzer](../../DesignDoc.md#モジュール責務)、[設計原則 P1-P4](../../DesignDoc.md#設計原則-design-principles)、[Future Work Phase1-3 / Open Questions Q2](../../DesignDoc.md#open-questions-未決事項)                                   |
| 関連 context   | [architecture](../../../context/architecture.md)、[testing](../../../context/testing.md)、[toolchain](../../../context/toolchain.md)、[engineering](../../../context/engineering.md)                                                                                                                                             |
| 関連 ADR       | [ADR-0001](../../../adr/0001-analyzer-protocol-jsonl-spi.md)、[ADR-0002](../../../adr/0002-core-implementation-foundation.md)、[ADR-0003](../../../adr/0003-analyzer-command-resolution.md)、[ADR-0004](../../../adr/0004-defer-runtime-call-tracing.md)、[ADR-0005](../../../adr/0005-adopt-sootup-and-spring-di-resolution.md) |
| 関連 spec      | [specs/9-java-analyzer](../../../specs/9-java-analyzer/)                                                                                                                                                                                                                                                                         |
| 対象モジュール | `java-analyzer` (Core 初回配線として `core` にも一部影響)                                                                                                                                                                                                                                                                        |

## 背景・要件解釈

Phase1 の対象は Java/Spring Boot であり、Java Analyzer は `analyzer-protocol` の SPI / JSONL スキーマを実装する最初の言語別 Analyzer である。Core 側は Protocol parser / validator (`core/internal/protocol`) と Analyzer process 起動 (`core/internal/analyzer`) を実装済みで、契約の受け側は揃っている。本 feature は、その契約に対して JSONL を出力する Java 側の実装方式 (build 基盤、起動契約、型解決、正規化規則、帰属型決定、段階導入) を確定する。

本 feature が関わる成功条件は Design Doc の S1 / S2 (caller / callee 探索の網羅性 — graph の入力を供給する)、S4 (Spring DI 経由の呼び出し先解決、Phase2 以降)、S5 (Analyzer 追加時に Core を変更しない) である。Phase1 では JavaParser ベースの静的呼び出し抽出を達成し、DI 解決 (Phase2) と Interface Dispatch / Override 解決 (Phase3, SootUp) は段階導入とする。

## スコープ

### やること

- Java ソースの AST 解析 (JavaParser) と型解決 (SymbolSolver) による静的呼び出し抽出
- 抽出結果を `analyzer-protocol` の JSONL スキーマ (`methodSymbol` / `callEdge` / `diagnostic` / `error`) で stdout へ出力
- `analysisRequest` の受領 (stdin) と process contract (exit code / stderr) の遵守
- Java Analyzer の build / 配布形態 (Gradle + Shadow plugin、単一 fat jar)
- Core からの起動方法 (CLI flag / 環境変数による起動コマンド解決) の確定
- 未解決 symbol / 部分解析の `diagnostic` 表現
- Phase2 (Spring Bean / DI 解決) / Phase3 (Interface Dispatch / Override 解決, SootUp) の段階導入境界の宣言

### やらないこと

- 共通契約 (SPI / Protocol / Model schema) の定義・変更 (→ analyzer-protocol feature doc が正本)
- グラフ探索 (→ traversal)、出力整形 (→ output)
- Phase2 / Phase3 の実装 (本 feature では段階導入の境界宣言のみ)
- SootUp 統合範囲 (Q2) の決定 (Phase3 着手前まで保留)
- Reflection / AspectJ Runtime / 実行時 Proxy の動的解析 (Design Doc Non Goals)
- CLI 引数の完全仕様の確定 (出力形式指定 / 探索方向 / 深さ上限などの全 flag 体系 → 後続の CLI interface spec)

## 設計

### 実装基盤

- **build tool**: Gradle (Kotlin DSL)。`gradlew` wrapper を同梱し、CI に Gradle 本体の事前インストールを要求しない。
- **JDK**: 25 LTS。Gradle toolchain で固定する (Analyzer process 自身が動く JVM の version。解析対象ソースの言語レベルとは独立して扱う)。
- **配布形態**: 単一 fat jar (Gradle Shadow plugin)。Core は `java -jar <path>` の 1 コマンドで起動できる。
- **実装言語**: Java を維持する (Kotlin を検討した上での判断)。JDK 25 の sealed interface + record + pattern matching で Kotlin を採用した場合の主利点 (代数的データ型、網羅性検査) が Java 単体で得られること、JavaParser との interop では Kotlin の null 安全が platform type で効かず利点が薄れること、将来の「Kotlin Analyzer」との命名混乱を避けられることが理由。

### 起動契約

Core は `--analyzer-cmd` (CLI flag) → `DEPWALK_ANALYZER_CMD` (環境変数) の順で Analyzer 起動コマンド文字列を解決する。どちらも指定が無い場合は実行前に validation error で拒否する。Core は解決した文字列を **shell を介さず shell-word 分割して exec** する (shell injection を避ける)。Core は `java` / jar / JVM の存在を知らず、言語固有の分岐と path 解決規約を持ち込まない。正本判断は [ADR-0003](../../../adr/0003-analyzer-command-resolution.md)。

metadata passthrough も同様の言語非依存原則に従う。Core は `--analyzer-meta key=value` で `analysisRequest.metadata` へ素通しするだけで、key / value の意味を解釈しない。

### metadata 契約

`--analyzer-meta key=value` の合成規則 (Core が metadata の JSON を組み立てる規則):

- 値は常に JSON 配列に積む。1 回だけ指定した場合も要素 1 の配列になる (`--analyzer-meta classpath=/a.jar` → `{"classpath": ["/a.jar"]}`)。
- 同一 key の繰り返しは、指定順に配列へ追加する。
- 値が空文字列の場合、その key を空配列として登録する (`--analyzer-meta classpath=` → `{"classpath": []}`)。
- 分割は最初の `=` で行う (value 側に `=` を含んでよい)。`=` を含まない指定は validation error として実行前に拒否する。

Java 固有の `metadata` key:

| key                   | 型          | 必須/任意                           | 意味                                                                                                                        |
| --------------------- | ----------- | ----------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `classpath`           | string 配列 | **必須** (key として。空配列は許容) | 依存 jar / classes dir の path。key 不在は `JAVA_MISSING_CLASSPATH` の `error`                                              |
| `liftExcludePackages` | string 配列 | 任意                                | 引き上げ除外 package (帰属型決定規則)。指定時は既定値 (`java` / `javax` / `jakarta`) を置き換える。segment 単位 prefix 一致 |

未知 key は protocol の規則どおり無視する。Core は本表を知らない (Analyzer 側のみが解釈する)。

### 型解決

JavaParser (AST 解析) + SymbolSolver (型解決) を用い、次の 3 つの `TypeSolver` を構成する。

- `ReflectionTypeSolver` (JDK 標準型)
- `JavaParserTypeSolver` (対象プロジェクトの source root)
- `JarTypeSolver` (依存 jar)

classpath は `analysisRequest.metadata` の `classpath` key として **必須**とする (値としての空配列は許容し、依存を持たない純 Java プロジェクトを扱えるようにする)。key 自体が無い場合は `JAVA_MISSING_CLASSPATH` の `error` とする。

pre-flight 検査 (classpath key の有無 / 指定 jar の存在・読み取り可否) は、解析開始前に一括で行う。型解決の途中で jar 欠落を遅延検出すると、出力済みの `methodSymbol` / `callEdge` が「一見成功した出力」として観測されうるため。fatal は `error` + 非ゼロ exit で即時停止する。

### analysisMode の意味論

`fullGraph` と `reachableFromEntrypoints` の両方を Phase1 で実装する。

| モード                     | 出力範囲                                                                                                     |
| -------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `fullGraph`                | scope (`include` / `exclude` 適用後) 内の全 `methodSymbol` と、その間の全 `callEdge`                         |
| `reachableFromEntrypoints` | `entrypoints` から呼び出し先 (callee) 方向に推移的に到達する `methodSymbol` と、それらの間の `callEdge` のみ |

- `entrypoints` が未指定または空配列の場合は、`analysisMode` の値によらず scope 全体の call graph 生成要求として扱う。
- node 母集合 (どのメソッドを `methodSymbol` として出すか) の列挙方法は「帰属型の決定規則」節を正本とする。
- caller 探索 (S1) の入力としては `reachableFromEntrypoints` は不完全であるため、caller 方向の問い合わせでは Core が `fullGraph` を選ぶ責務を持つ。`reachableFromEntrypoints` は callee 方向の調査で出力量を削るための最適化と位置づける。

### 正規化規則 (methodId / signature)

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

匿名クラスのメソッドは、宣言型を JavaParser のソース出現順で採番した binary name (`com.example.Outer$1`) とし、通常のメソッドと同じ規則で `signature` / `methodId` を作る。lambda は独立 node にしないため専用の ID を持たない。

### symbolKind の割り当て

`method` + `constructor` + static initializer (`initializer`) を node 化する。lambda は独立 node にしない。protocol の `symbolKind` enum は変更しない。

| Java の構文                                     | 扱い                                                                              |
| ----------------------------------------------- | --------------------------------------------------------------------------------- |
| インスタンス / static メソッド                  | `symbolKind: method`                                                              |
| コンストラクタ                                  | `symbolKind: constructor`                                                         |
| 匿名クラスのメソッド                            | `symbolKind: method` (宣言型が `Outer$1` になるだけで実体は通常のメソッド)        |
| static 初期化ブロック                           | `symbolKind: initializer` (`signature` は `com.example.Foo#<clinit>()`)           |
| インスタンス初期化ブロック / フィールド初期化子 | 独立 node にせず、各 `constructor` に畳み込む (Java コンパイラの意味論に合わせる) |
| lambda 本体                                     | 独立 node にせず、lambda を字句的に囲むメソッドに帰属させる                       |

lambda 本体内の呼び出しは、囲みメソッドを caller とする `callEdge` として出力する。遅延実行される呼び出しであることは `callEdge.metadata` に `viaLambda: true` を立てて標識する (Core の graph 構築は `metadata` に依存しないため契約上は無害)。

### dispatch 標識

Phase1 は DI 解決を行わないため、interface / 抽象メソッド呼び出しの callee は「帰属型の決定規則」で決まる帰属型 (interface / 抽象クラスを含む) のメソッドになる。実装クラスのメソッドへの辺は後続 feature (#21 / ADR-0005) で追加する。

`callEdge.metadata.dispatch` に呼び出しの種別を持たせる: `static` (static メソッド呼び出し) / `virtual` (具象クラスの instance メソッド) / `interface` (interface 経由) / `abstract` (抽象クラスの抽象メソッド経由)。利用者は「この辺は宣言型止まりで実体ではない」と判別でき、後続 feature (#21 / ADR-0005) で実装候補の辺を足すときの土台にもなる。

未解決 `diagnostic` に倒す案は採らない。Spring プロジェクトでは呼び出しの大半が interface 越しであり、辺を落とすと S1 / S2 (網羅性) が Phase1 で実用にならないため。

「Spring DI 経由の呼び出し先を実体まで解決できる」(S4) は後続 feature (#21 / ADR-0005) 以降の成功条件であり、Phase1 では宣言型止まりであることが仕様である。

### diagnostic / error code 体系

`JAVA_` prefix + 大文字スネークケースとする。Core は `code` を不透明な文字列として扱うため契約変更は発生しない。

`diagnostic` (解析継続):

| code                        | severity         | 出る場面                                                          |
| --------------------------- | ---------------- | ----------------------------------------------------------------- |
| `JAVA_UNRESOLVED_SYMBOL`    | `warning`        | 呼び出し先の型が解決できず `callEdge` を張れない                  |
| `JAVA_PARSE_ERROR`          | `partialFailure` | ファイル単位で構文解析に失敗し、そのファイルを飛ばした            |
| `JAVA_ENTRYPOINT_NOT_FOUND` | `warning`        | `entrypoints` の method selector に一致する method が見つからない |

`error` (fatal / 非ゼロ exit):

| code                     | 出る場面                                                                                                  |
| ------------------------ | --------------------------------------------------------------------------------------------------------- |
| `JAVA_MISSING_CLASSPATH` | `analysisRequest.metadata` に classpath の key が無い (値としての空配列は正当な入力であり error にしない) |
| `JAVA_MISSING_JAR`       | classpath に指定された jar が存在しない / 読めない (fatal)                                                |
| `JAVA_INVALID_REQUEST`   | `analysisRequest` が Java Analyzer として処理できない (未対応 `language` 等)                              |
| `JAVA_INTERNAL_ERROR`    | 上記以外の継続不能な内部エラー                                                                            |

jar 欠落を fatal にするのは、jar が 1 つ欠けるだけで広範囲の型解決が失敗し、継続すると「未解決だらけの、一見成功した結果」が出て利用者が不完全なグラフを正と誤認するリスクが高いため。`diagnostic.sourceLocation` と `relatedMethodId` を可能な範囲で埋め、未解決の発生箇所を追跡できるようにする。

### 性能方針

- **streaming 出力**: `methodSymbol` / `callEdge` を逐次 stdout へ flush し、Analyzer 側にグラフ全体をメモリ保持しない。
- **AST の逐次破棄**: 解析済みファイルの AST を保持し続けない。保持するのは SymbolSolver の型解決キャッシュと、`callEdge` 出力に必要な最小限の情報に限る。
- **計測の観測性**: 解析ファイル数 / 所要時間 / 未解決件数を stderr に出力する (protocol record としては出さない)。
- **数値目標**: 未定。Phase1 実装時に fixture プロジェクトの実測値 (ファイル数 / 所要時間 / 最大 RSS) を baseline として記録し、その後に本 doc へ確定値を記録する。現時点は方式のみを Phase1 の必須仕様として確定し、数値目標は実測 baseline 取得後に本 doc へ追記する。

### 帰属型の決定規則

帰属型 (メソッドが属する型) は「宣言型を優先し、宣言が scope 外のときだけレシーバの静的型へ引き上げる」。

「宣言型」は SymbolSolver が override 解決まで済ませた後に返す、そのメソッド宣言の所在型を指す (本体を持つかどうかは問わない — interface / 抽象メソッドの宣言もここに含む)。override されていれば override 先の型、されていなければ継承元の型になる。

| 条件                                                                                          | 帰属型                                                | 例                                                                                                                                                                                        |
| --------------------------------------------------------------------------------------------- | ----------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 宣言サイトが scope 内                                                                         | 宣言型 (宣言の所在型)                                 | `UserService extends BaseService` で `save` を override していない → `userService.save()` の callee は `com.example.BaseService#save`。override していれば `com.example.UserService#save` |
| 宣言サイトが scope 外で、レシーバの静的型が scope 内、かつ宣言型が引き上げ除外 package でない | レシーバの静的型へ引き上げる                          | `UserRepository extends JpaRepository` → `userRepository.findById()` の callee は `com.example.UserRepository#findById(java.lang.Long)`                                                   |
| 宣言サイトが scope 外で、宣言型が引き上げ除外 package                                         | 出力しない                                            | `userService.toString()` (`java.lang.Object#toString`) / `equals` / `hashCode`                                                                                                            |
| 宣言サイトが scope 外で、レシーバの静的型も scope 外                                          | 出力しない (`methodSymbol` / `callEdge` とも出さない) | `String#equals` / `List#add`                                                                                                                                                              |

**引き上げ除外 package**: 既定で `java` / `javax` / `jakarta` 配下を引き上げ対象から除外する (`liftExcludePackages` に渡す正規値は wildcard を含まない package prefix)。`analysisRequest.metadata` の `liftExcludePackages` で除外 package を上書き (置き換え) 可能にする。除外判定は宣言型の binary name に対する `.` 区切り segment 単位の prefix 一致で行う (`java` は `java.lang` / `java.util` に一致し、`javafx` には一致しない)。

**その他の呼び出し形**:

- static メソッド: 「レシーバ」を参照した型とみなして同一規則を適用する。
- `this.foo()` / `super.foo()`: 宣言サイトが scope 内なら宣言型に帰属するため揺れない。
- `new Foo()` (constructor): constructor は継承されないため引き上げは発生しない。`Foo` が scope 内なら `com.example.Foo#<init>(...)` を callee とし、scope 外 (`new ArrayList<>()` 等) なら出力しない。

**根拠**:

- 宣言サイト基準の根拠: 「常にレシーバ型へ帰属」だと scope 内継承 (override なし) で実在しない node が合成され node 分裂を招く。「常に根の基底へ集約」だと override した node が dead node になり影響調査ができない。実際の宣言サイトを使えばどちらの病理も起きない。
- 引き上げを scope 外に限る根拠: 引き上げは「宣言が jar の中にあって node にできない」問題を解くためだけに使う。継承元が scope 外であることは `methodSymbol.metadata` に保持する (例: `declaringType: "org.springframework.data.repository.CrudRepository"`, `inherited: true`)。
- scope 外呼び出しを落とす根拠: JDK / library 内部メソッドをすべて node 化すると影響調査に無価値なノイズでグラフが埋まる。depwalk の用途は自分のコードへの影響調査であり、library 内部の呼び出し関係は対象外。
- 未解決との区別: scope 外呼び出しの省略は「解析できなかった」ではなく「仕様として出力しない」ため、`JAVA_UNRESOLVED_SYMBOL` の `diagnostic` は出さない。型解決自体に失敗した場合のみ `diagnostic` とする。
- protocol 整合: 出力する `callEdge` の caller / callee はいずれも出力済み `methodSymbol` を参照するため、「valid な `callEdge` は解決済み `methodSymbol` を参照する」という契約を満たす。

**Phase1 の既知の制約 (override)**: 静的解決のため、基底型の変数経由の呼び出しは基底型のメソッドに帰属し、実行時に呼ばれる override 先には辺が張られない。virtual dispatch の解決は後続 feature (#21 / ADR-0005) の担当とする。

**node 母集合 (列挙方法)**: `fullGraph` の `methodSymbol` は次の和集合とする。

1. **宣言列挙**: scope 内で宣言された method / constructor / static initializer のすべて。呼ばれていないメソッドも node として出力する (caller が 0 件であることを示せる = S1 の用途に必要)。
2. **call site 由来**: 引き上げで生じた node (scope 内型に帰属する、宣言が scope 外のメソッド)。これらは scope 内に宣言が存在しないため宣言列挙では出せず、実際に呼び出された箇所からのみ生成する。呼ばれていない継承 library メソッドは node 化しない。

`reachableFromEntrypoints` は上記母集合のうち、entrypoints から callee 方向に推移的に到達するものに限る。

### 段階導入

| Phase              | 範囲                                                                                                                                                                                                                                                                                                                                         |
| ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Phase1             | JavaParser + SymbolSolver による静的呼び出し抽出 (本 doc の確定内容)                                                                                                                                                                                                                                                                         |
| 後続 feature (#21) | SootUp による型階層 / Interface Dispatch / Override 候補の補完と、Spring Bean / DI 解決による候補絞り込み (S4 の達成)。実装は型階層補完 → Spring 候補絞り込み → 統合 E2E の順に分割する ([ADR-0005](../../../adr/0005-adopt-sootup-and-spring-di-resolution.md))。SootUp の call graph 委譲範囲 (Q2) は #21 spec の clarify phase で決定する |

Reflection / AspectJ Runtime / 実行時 Proxy 等、実行時状態で初めて確定する呼び出しの完全追跡は初期スコープに含めない ([ADR-0004](../../../adr/0004-defer-runtime-call-tracing.md))。静的に候補を導ける場合は候補と根拠を出力し、確定できない場合は候補・未解決理由を観測可能にする。

> 本 doc 内の「Phase2」「Phase3」という旧呼称は、ADR-0005 (2026-07-11) により後続 feature (#21) に統合された。

## 主要シナリオ / フロー

- Core が Analyzer process を起動し stdin へ `analysisRequest` を 1 件送信して close する。Java Analyzer は対象 Java ソースを read-only で解析し、結果を stdout へ JSONL で逐次出力する。
- 呼び出し先の型が解決できたとき、`methodSymbol` (caller / callee 双方) と、両者を参照する `callEdge` を出力する。
- 呼び出し先が interface / 抽象メソッドであるとき、帰属型の決定規則で決まる帰属型のメソッドを callee として `callEdge` を出力し、`callEdge.metadata.dispatch` に dispatch 種別を標識する。
- 呼び出し先メソッドの宣言サイトが scope 外で、その宣言型が引き上げ除外 package に属するとき、`methodSymbol` / `callEdge` を出力しない (解析失敗ではないため `diagnostic` も出さない)。
- 呼び出し先の型が解決できないとき、`callEdge` を出力せず `diagnostic` として未解決を報告し、解析を継続する。
- 個別ファイルがパース不能なとき、該当ファイルを `diagnostic` で報告し、他ファイルの解析を継続する (部分解析を許容する)。
- 解析を継続できない致命的な問題が起きたとき、`error` record を出力し、非ゼロ exit code で終了する。

## テスト観点

横断規約は [context/testing.md](../../../context/testing.md)。本 feature は三層 (Java unit / Go fake analyzer / 実 jar E2E) で担保する。

**Java unit test (JUnit / `analyzers/java/`)**

- signature / `methodId` の正規化 (overload / generics erasure / varargs / nested class (`$`) / constructor (`<init>`) / static initializer (`<clinit>`) / 匿名クラス採番の決定性)
- `symbolKind` の割り当て (インスタンス初期化子・フィールド初期化子が constructor に畳み込まれること、lambda 内の呼び出しが囲みメソッドに帰属し `viaLambda: true` が立つこと)
- 帰属型の決定規則 (宣言サイト scope 内 (override あり / なし)、scope 外宣言の引き上げ、除外 package (既定値と `liftExcludePackages` による置き換え、segment 単位 prefix 一致)、`this` / `super` / static / `new` の各形、`metadata.dispatch` の値)
- `diagnostic` / `error` の code と severity の対応、pre-flight 検査 (classpath key 不在 / jar 欠落 / `language != "java"`) が解析開始前に fatal になること
- `fullGraph` / `reachableFromEntrypoints` の出力範囲 (宣言列挙 ∪ call site 由来、entrypoints 空は全体扱い)

**Go 側 process contract (fake analyzer / JVM 不要)**

- `--analyzer-cmd` / `DEPWALK_ANALYZER_CMD` の解決順序と、どちらも無い場合の実行前拒否
- `--analyzer-meta key=value` の合成規則 (1 回指定 → 要素 1 の配列、繰り返し → 指定順の配列、空値 (`key=`) → 空配列、`=` なし → validation error、value に `=` を含む指定 → 最初の `=` で分割)
- shell を介さない shell-word 分割で exec されること
- 既存の contract test 観点 (stdin close / 逐次 parse / stderr 非 parse / exit code) を再利用する

**E2E (実 jar / `testdata/fixtures/java/`)**

- 既知の caller / callee 集合と `depwalk analyze` の出力の照合 (S1 / S2)
- interface 注入を含むサンプルで、宣言型 (interface) のメソッドが callee に現れ `dispatch: interface` が立つこと (Phase1 の S4 前段)
- パース不能ファイルを混ぜた fixture で、`diagnostic` が出つつ他ファイルの解析が継続すること
- 未解決 symbol を含む fixture で、`JAVA_UNRESOLVED_SYMBOL` の `diagnostic` が出つつ解決済みの `callEdge` が揃うこと

## 上位資料からの変更点

| 対象資料  | 変更種別 (継承 / 追記 / 変更提案) | 内容                                                                                                                                             |
| --------- | --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| PRD       | 継承                              | 統合モードのため DesignDoc の Why / What を参照                                                                                                  |
| DesignDoc | 追記                              | Java Analyzer feature の正本を本 doc に移す。成功条件 S5 / 設計原則 P4 の測定方法明確化 (2 つ目以降の Analyzer 追加時に Core 無変更) を反映済み  |
| context   | 追記                              | `context/toolchain.md` / `context/project.md` / `context/testing.md` / `context/architecture.md` / `context/engineering.md` の該当箇所へ反映済み |
| ADR       | 追記                              | Analyzer 起動コマンド解決の判断を ADR-0003 に記録                                                                                                |
