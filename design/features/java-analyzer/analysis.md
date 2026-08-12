---
type: feature-design
title: "Java Analyzer: 解析エンジン"
description: 型解決・Spring DI 解決・解析完全性の判定規則
status: 完了
keywords: [型解決, SymbolSolver, SootUp, Spring, DI, 完全性, bytecode]
governs:
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/sootup
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/context
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/augment
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/spring
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/completeness
verified_commit: 6292e9a
---

# Java Analyzer: 解析エンジン

**ソースから呼び出し関係をどう解決するか**を定める。

JavaParser と SymbolSolver で型を解決し、SootUp で型階層・override・interface 実装候補を索引し、Spring の DI で候補を絞り込む。解決できなかった call をどう扱うか (救済するか、未解決として完全性 gate に残すか) も本 doc が定める。

解決結果を Protocol の record へ写す規則は [protocol-mapping.md](protocol-mapping.md)。親 doc は [DesignDoc_java-analyzer.md](DesignDoc_java-analyzer.md)。用語 (JavaParser / SymbolSolver / SootUp / bytecode member / 救済 / 完全性 gate) は親 doc の「前提」節を参照する。

## この doc が答えること

- `user.save()` の `user` が何型か、どう決めるか (型解決)
- ソースに現れない Lombok 生成メソッドをどう解くか (bytecode member の合成)
- interface 越しの呼び出しで、実装クラスの候補をどう絞るか (Spring DI)
- 解決できなかった呼び出しをどう扱うか。救済するか、未解決として結果を失敗にするか

## 型解決

JavaParser (AST 解析) + SymbolSolver (型解決) を用い、次の 3 つの `TypeSolver` を構成する。

- `ReflectionTypeSolver` (JDK 標準型)
- `JavaParserTypeSolver` (対象プロジェクトの source root)
- `JarTypeSolver` (依存 jar)

classpath は明示 `sourceRoots` 経路で `analysisRequest.metadata.classpath` key を **必須**とする (空配列可)。自動 discovery 経路では、custom tooling model が project ごとの compile classpath / classes output を提供する。request metadata に `classpath` があれば、共通 extra として全 context へ追加する。`javaLanguageLevel` / `javaPreview` の自動 discovery 時指定は不正とする。両 key とも要素数が 1 でない配列 (0 件・2 件以上) と非 string 要素は `JAVA_INVALID_REQUEST` として解析開始前に拒否する。

`classpath` の各要素には依存 jar またはコンパイル済み classes directory を指定できる。自プロジェクトの bytecode を照会する場合は、解析対象プロジェクトの classes output directory (例: Gradle の `build/classes/java/main`) も既存の `classpath` 配列へ追加する。新しい metadata key は導入しない。SootUp は、source から得た binary name と一致する `.class` を classpath 上で照会し、自プロジェクトの class と依存 class を区別する。

pre-flight 検査 (classpath key の有無 / 指定した jar または classes directory の存在・読み取り可否) は、解析開始前に一括で行う。明示された classpath entry の欠落・読み取り不能は `JAVA_MISSING_JAR` の fatal とし、`error` + 非ゼロ exit で即時停止する。明示された入力の欠落を部分解析へ降格すると、出力済みの `methodSymbol` / `callEdge` が「一見成功した出力」として観測されうるためである。

`JAVA_SOOTUP_UNAVAILABLE` の継続可能 fallback は、pre-flight を通過した入力について次の 4 つに限定する。

- SootUp が class file を解釈・索引化できない
- 自動 discovery の model 由来 classes output が未作成
- 明示経路で、自 project の classes output 自体が classpath に指定されていない
- model 由来 compile classpath のうち、workspace 内の project 依存 build output が未 build で存在しないこの場合は対象と原因を diagnostic に出力し、JavaParser の結果だけで source-only 解析を継続する。workspace 内の未 build entry を除外しても、依存 project の source root が solver へ入るため、型解決は依存 context の source が補完する。(model 取得は task を実行しないため、fresh checkout ではこの欠落が通常状態である)。次の 2 つの欠落・読取不能は `JAVA_MISSING_JAR` の fatal とし、fallback しない。利用者が classpath entry として明示した classes directory / jar と、model が解決済み compile classpath として返した workspace 外の external artifact である。source-only で生成 member を救済できず primary call diagnostic が残れば、終端で `JAVA_INCOMPLETE_ANALYSIS` になる。fatal は先行 warning record を無効化する。そのため SootUp を利用できなかった context 数を `JAVA_INCOMPLETE_ANALYSIS` の error metadata (`sootUpUnavailableContexts`) へ自己完結に保持し、bytecode 救済の欠如が原因の未解決を fatal 後も診断できるようにする。

SootUp 依存は `org.soot-oss:sootup.core:2.0.0`、`org.soot-oss:sootup.java.core:2.0.0`、`org.soot-oss:sootup.java.bytecode.frontend:2.0.0` に固定する。`sootup.callgraph` は本 doc「実装基盤」の責務境界に反するため追加しない。2.0.0 は実装前設計時点で Maven Central に公開されている安定版で、bytecode の `AnalysisInputLocation` / `View` に必要な最小 module を選んだ。

## solver 層の bytecode member 合成

scope 内 source 型を solver が解決するとき、同一 context の classes output にしか存在しない一意な callable member (Lombok 等の生成 member) を解決時に合成する。call-site 駆動の救済 (生成 member 索引) だけでは式の型伝播 (chained call / stream 連鎖) を辿れないための拡張で、source 宣言と source 優先の帰属規則は変更しない。合成 member の出力は bytecode-only member と同じ契約 (定義位置省略 + owner metadata) に従う。generic 戻り値は classes output の Signature 属性から実型引数を復元する。Signature が無い・読めない member と型変数は erasure (Object) へ degrade し、解析は失敗させない。

合成・救済の選択境界: 型名 scope の static call は instance member を合成・救済せず、未解決として完全性 gate に残す (偽 edge 防止)。member 候補は、owner class の classfile が project 所有の classes output に存在する場合だけ採用する。対象は自 context と、**model の project 依存関係で到達可能な依存 project の output** である。

external artifact だけに存在する同名 class の member は、project bytecode として救済しない (「solver 層の bytecode member 合成」節の origin 検証)。依存 project output は classpath の形 (Gradle model は依存 project を jar として返すことがある) に依存せず model の依存関係から解決する。SootUp の入力は project 所有 output を external jar より先に登録し、同名 class は project bytecode を優先する。

cross-module 救済: 依存 context の source 型が持つ生成 member (Lombok constructor / getter 等) の cross-module 呼び出しも救済の対象とする。採用境界は依存 project の output を含む。

## Spring Bean 候補の選択規則

Spring ApplicationContext は起動せず、次の静的規則だけを実装する。

1. 注入型へ代入可能な Bean を型階層から列挙する。
2. 注入点に直接の `@Qualifier("value")` がある場合は、Bean 側の qualifier value、Bean 名、alias のいずれかが `value` と一致する候補だけを残す。custom qualifier meta-annotation、generics qualifier、`@Resource` は対象外とする。
3. 残った候補が 1 件なら `unique` とする。ただし条件アノテーション付き候補は `ambiguous` とする。
4. 候補が複数件なら、条件アノテーションがない `@Primary` 候補がちょうど 1 件の場合だけその候補を `unique` とする。唯一の `@Primary` が条件付きの場合は、条件が偽のときに他候補が選ばれる可能性を残すため、全候補を保持して `ambiguous` とする。`@Primary` が 0 件または複数件の場合も全候補を保持して `ambiguous` とする。
5. 候補が 0 件なら unresolved とする。既知の runtime-provided マーカーに該当する場合だけ理由を `runtime-provided` に置き換える。

Bean 名は次の規則で導出する。

- stereotype class は annotation の `value` が非空ならその値を使う。省略時は simple class name に `java.beans.Introspector.decapitalize` と同じ規則を適用する。
- `@Bean` method は `name` / `value` に明示された名前を Bean 名・alias として保持する。省略時の Bean 名は method name とする。
- Bean class または `@Bean` method に直接付与された `@Qualifier("value")` を qualifier value として保持する。

## Parse・resolution・call 完全性

全対象 Java file を workspace 相対 path の決定順で graph record 出力前に parse pre-flight する。1 件でも失敗した場合は最初の失敗 file の location、適用 language level、sanitize 済み parser messageを持つ `JAVA_PARSE_ERROR` を出力して非ゼロ終了し、v1 では部分 parse mode を提供しない。pre-flight の AST は file ごとに破棄し、成功後の通常解析で再 parse する。

solver 前に resolution と独立した visitor で各 call expression / method reference / constructor invocation / initializer call を inventory 化する。`CallSiteId` は 2 つを canonical 順で連結した内部 identity であり、Protocol へは出力しない。1 つは workspace 相対 path・start / end line・column・AST call kind からなる lexical site key、もう 1 つは semantic caller method ID である。全 call は内部 outcome ledger で次のいずれか1つへ終端しなければならない。

- `emitted`: valid edge を出力した。
- `excluded`: `external-target` または `lift-excluded-package` の列挙済み理由に該当する。
- `diagnostic`: allowlist された resolution failure として候補・理由を保持した。

未知の `RuntimeException` / `LinkageError` を広く捕捉して diagnostic へ降格しない。allowlist 外の resolver failure は `JAVA_INTERNAL_ERROR` の request fatal とする。1 call の symbol / edge / ledger 更新は原子的に行い、中途半端な record を出さない。initializer の call は意味論上展開する。instance initializer / field initializer は各 constructor caller へ、static initializer は `<clinit>` caller へ展開する。展開後の各 call は独立した `CallSiteId` として数える。

source にない生成 member は、call site から要求された member だけを project bytecode member index で検索する。index は generator 固有の annotation 名に依存せず、compile classes output の signature / owner / kind を扱う。source-only member は `sourceLocation` を持つ。bytecode-only member は `sourceLocation` を省略する。代わりに `methodSymbol.metadata` へ `declarationOrigin: "project-bytecode"`、`sourceAnchor: "owner-type"`、`ownerSourceLocation` を保持する。対応する edge は `calleeOrigin: "project-bytecode-member"` を持ち、Graph は nested metadata を deep copy する。owner source type がscope内にない生成type全体と、source call siteから直接参照されないJVM内部memberは索引対象外である。

全救済後にも primary diagnostic outcome が残る場合、既定では成功 graph を返さず `JAVA_INCOMPLETE_ANALYSIS` の request fatal とする。未解決 call は内部 `CallSiteId` 順で並べるが、ID 自体は Protocol へ出力しない。各共通 `error.details` には、source location、元 diagnostic code / message、opaque metadata の reason / call kind / 判明済み target / candidate を自己完結形式で含める。内容は top-level metadata の total / reasonCounts と一致させる。`silentOmission` は常に 0 でなければならない。

### 未解決を許して結果を返す (opt-in)

`metadata.allowIncompleteAnalysis` が `true` の場合、primary diagnostic が残っても request を fatal にせず、解決済み edge / 明示除外を含む graph を成功として公開する。残存する primary diagnostic は、検出時点で通常どおり `diagnostic` record (`JAVA_UNRESOLVED_SYMBOL` warning) として streaming 済みであり、graph が部分的であることは隠さない。stderr の call-site summary (`callSiteSummary`) には、終端種別・理由別の集計 (`emitted` / `excluded[...]` / `diagnostic[...]`) を既定で出力する。`silentOmission == 0` の不変条件は緩和時も維持する。既定値は `false` (従来の fatal 挙動を維持、後方互換)。この flag は完全性 gate の判定タイミングだけを変える opt-in であり、outcome ledger の分類ロジック・帰属意味論・emit される edge の正しさ (推測による false edge の禁止) には影響しない。

### 救済と除外分類が効く範囲

bytecode 救済 (project bytecode member index) と `external-target` 除外分類は、method call だけに適用するのではない。method reference (constructor reference 含む) と explicit constructor invocation (`super(...)` / `this(...)`) の resolve 失敗にも適用する。救済・分類を試みてから diagnostic 化する。outcome ledger の 3 終端と帰属意味論は変更しない。

### 呼び出し元の型が分からないとき

receiver 型が取得できない call は、次の順で分類を試みてから diagnostic 化する。いずれも classfile / 確定 AST のみを根拠とし、推測による型付けは行わない。

1. **chain の前進解決**: receiver chain の各 link を project bytecode candidate の戻り値型 (descriptor / generic Signature 由来) で前進解決し、owner 型を復元できたら通常の救済 / external 分類を適用する。候補が一意でない link・primitive / 配列戻り値・project 外 classfile の link では前進しない。暗黙 this link は囲み型、型不明の単純名は確定 AST の initializer (囲み callable 内で同名宣言が一意の場合のみ) または囲み型の bytecode field 型で補完する。field 型補完は、囲み member 内に同名の local 宣言 (parameter / パターン変数含む) が見えない場合に限る — block 直下以外 (for ヘッダ・try-resource・catch 節等) で宣言された local が field を shadowing しているとき、field 型での誤判定 (false exclusion) を避ける。
2. **起点遡及の external 判定**: 前進解決できない場合、chain・変数 initializer・`this.field` を遡って最初に静的型が取れる起点を探し、その型が scope 外 (source 宣言索引に無い) なら `external-target` 除外へ分類する。起点が scope 内型・暗黙 this、または起点の型も取れない場合は保守的に diagnostic に残す。単純名起点の field 型補完には規則 1 と同じ同名 local 宣言の検査を適用する。
3. **lambda parameter 規則**: receiver が lambda parameter の場合、lambda が代入される変数の宣言型 (= functional interface 型そのもの) が scope 外なら `external-target` へ分類する。lambda を直接 method 引数に渡す形 (unqualified static import 経由を含む) は external 判定の根拠にしない — 受け手 method の receiver 型や static import 元の class は、lambda parameter が実際に instantiate される型と独立した情報であり、external でも in-scope 型の parameter を取り得るため diagnostic に残す。

SAM arity を推論できない method reference は救済しない。候補列挙は owner classfile の宣言 member に限られ継承 overload を検証できないため、宣言上の名前一意を参照先の一意の根拠にできない (diagnostic に残す保守側)。

### 型伝播救済層 (未実装。実装は #82 で進行中)

上記の分類規則を拡張し、solver 失敗時に receiver 式の型を段階導出して既存 bytecode 救済へ接続する (判断の正本は [ADR-0012](../../../adr/0012-implicit-call-resolution-and-type-propagation-rescue.md))。導出手段は次の 3 つで、いずれも classfile / 確定 AST を根拠とし、推測による型付けは行わない。

1. **local 変数の宣言・初期化子**: receiver が local 変数 (var 宣言含む) のとき、宣言型または初期化子式の解決型から receiver 型を導出する
2. **chain link の generic signature**: 規則 1 (chain の前進解決) の適用を拡大し、bytecode の generic Signature が型引数を保持する場合は型引数を伝播して要素型を復元する
3. **lambda parameter の functional interface 型引数**: lambda parameter の型を、lambda が渡された先の functional interface の型引数 (bytecode の generic Signature 由来) から導出する

SAM arity も functional interface の bytecode から導出する (例: `java.util.function.Function#apply` = arity 1)。これにより arity 推論失敗による救済スキップを減らすが、「宣言上の名前一意を根拠にする救済はしない」保守側の原則は変更しない。

## framework 由来の暗黙呼び出しの解決 (未実装。実装は #82 で進行中)

framework が実行時に起動する呼び出しを、ソース上の根拠 (アノテーション / 型 / AST) を伴う範囲で解決する (判断の正本は [ADR-0012](../../../adr/0012-implicit-call-resolution-and-type-propagation-rescue.md))。解決不能は diagnostic に残し、`silentOmission == 0` を維持する。

### entry point 分類

対象アノテーションを付与されたメソッドを framework entry point として分類し、`methodSymbol.metadata.entryPoint` (検出アノテーション FQN の配列) で標識する。edge は作らず、caller 探索の終端根拠のみ付与する (擬似 caller node を合成しない)。対象集合は次を既知集合として明示列挙する。

- ライフサイクル: `org.springframework.scheduling.annotation.Scheduled` / `PostConstruct` / `PreDestroy` (後者 2 つは `javax.annotation` / `jakarta.annotation` の両 FQN)
- イベント: `org.springframework.context.event.EventListener` / `org.springframework.transaction.event.TransactionalEventListener`
- Web: `org.springframework.web.bind.annotation.RequestMapping` + Spring 提供 composed (`GetMapping` / `PostMapping` / `PutMapping` / `DeleteMapping` / `PatchMapping`) / `ExceptionHandler` / `ModelAttribute`

利用者定義の合成アノテーション (meta-annotation) は 1 段だけ辿って検出する。2 段以上の入れ子は検出できず、診断も出せないため制約として扱う。

### イベント edge

`ApplicationEventPublisher#publishEvent()` の call site を起点に、引数の静的型とその型階層に合致する `@EventListener` / `@TransactionalEventListener` メソッドへの edge を生成する。caller は call site の囲みメソッド、`provenance` には `spring-event` を積む。イベントは合致 listener が全て実行される broadcast 意味論のため、無条件 listener への edge は複数でも各々確定 (`resolution: unique`) とし、条件付き listener のみ既存規則 (`conditional` / `conditionTypes`) で `ambiguous` とする。generics を使ったイベント型の突合は raw type 一致で近似する (制約)。

### callable invocation

functional interface の invocation site から、渡された callable の実体への edge を生成する。method reference は参照先メソッドへ、lambda は定義側の囲みメソッドへ張り、`viaCallableInvocation: true` で通常呼び出しと区別する (lambda 本体は独立 node にしない既存決定を維持。囲みメソッドへの edge は「invoker はそのメソッド内で定義されたコードを実行する」の意味)。静的追跡範囲は (1) 同一メソッド内の local 変数経由、(2) workspace メソッドの functional interface parameter への引数渡し 1 段、に限定する。複数 call site から異なる callable が渡る場合は各 edge を call site 根拠付きで全列挙する。field 経由・多段の受け渡し・invocation site が外部ライブラリ内にあるケースは対象外で、前 2 者は diagnostic に残す。
