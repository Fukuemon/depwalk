# Gradle マルチモジュールの複数 source root を解析する

> 本文書は Issue #24 の spec-lifecycle における作業記録である。
> durable な Protocol、Java Analyzer、テスト契約は sync phase で feature doc / context へハンドオフする。

## メタ情報

- Issue: `#24`
- ステータス: `In Progress`
- 作成日: 2026-07-15
- 更新日: 2026-07-16
- Branch: `feature/24`
- Owner: Fukuemon

## 設計フェーズ状況

状態は `未着手 / 進行中 / 完了 / レビュー済 / 保留` のいずれか。保留の場合は理由を備考に残す。

| #   | フェーズ                    | 状態       | 最終更新   | 備考                                                                  |
| --- | --------------------------- | ---------- | ---------- | --------------------------------------------------------------------- |
| 1   | 起票                        | 完了       | 2026-07-15 | GitHub Issue #24 を確認済み                                           |
| 2   | 下書き                      | レビュー済 | 2026-07-15 | 本 index.md をテンプレートから新規作成。spec-review PASS              |
| 3   | 上位文書突合                | レビュー済 | 2026-07-15 | Design Doc / feature doc / context / ADR と矛盾なし。spec-review PASS |
| 4   | 論点整理                    | レビュー済 | 2026-07-15 | D1〜D9 を未決論点として抽出。spec-review PASS                         |
| 5   | 論点解決                    | レビュー済 | 2026-07-16 | D1〜D9解決済み。fresh-context spec-review PASS                        |
| 6   | Interface / Routing 設計    | 未着手     |            |                                                                       |
| 7   | Content / Data 設計         | 未着手     |            |                                                                       |
| 8   | Performance / Security 設計 | 未着手     |            |                                                                       |
| 9   | Test / Metrics 設計         | 未着手     |            |                                                                       |
| 10  | 実装分割                    | 未着手     |            |                                                                       |
| 11  | レビュー済                  | 未着手     |            |                                                                       |

## 上位文書整合

正本 ([Design Doc](../../design/DesignDoc.md) / [feature doc](../../design/features/) / [context](../../context/) / ADR) のどの節と、どう整合させるかを記録する。

- PRD 更新要否: 不要。本プロジェクトは統合モードであり、Why / What は Design Doc に統合されている。
- Design Doc 更新要否: 不要。Core と Analyzer の責務境界および成功条件 S1 / S2 / S4 / S5 の範囲内である。
- ADR 起票要否: 要。D3のGradle Tooling API採用とbuild script評価のruntime / security boundaryを、sync phaseでADR-0006として起票する。既存ADRの廃止は不要。

| 上位文書                    | 節 / 該当箇所                                                                 | 整合方針 (継承 / 補足 / 変更提案)                                         |
| --------------------------- | ----------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| Design Doc                  | Why / What、成功条件 S1 / S2 / S4                                             | 継承。Java / Spring Boot の変更影響解析を実プロジェクト構成へ広げる       |
| Design Doc                  | 成功条件 S5、設計原則 P1〜P4、モジュール責務                                  | 継承。Core に Gradle / Java 固有の探索規則を持たせない                    |
| feature doc (protocol)      | `analysisRequest.workspaceRoot`、`include` / `exclude`、`SourceLocation.path` | 補足。既存の相対 path 基準を保ちながら複数 source root の入力契約を詰める |
| feature doc (protocol)      | Versioning / compatibility                                                    | 継承。任意 field 追加を優先し、既存 request の意味を維持する              |
| feature doc (java-analyzer) | TypeSolver、帰属型決定、pre-flight、性能方針                                  | 補足。複数 root の列挙・型解決・scope membership を具体化する             |
| feature doc (java-analyzer) | Java unit / Go process contract / 実 jar E2E                                  | 継承。既存三層へマルチモジュールの検証を追加する                          |
| context (architecture.md)   | Package Boundary / Runtime Boundary / State Boundary                          | 継承。Core → Analyzer は Protocol のみ、対象ソースは read-only            |
| context (testing.md)        | Protocol contract test / Java Analyzer 三層                                   | 継承。Protocol、Java unit、実 jar E2E の責務を分ける                      |
| context (engineering.md)    | Repository Quality Gate / 依存境界 gate                                       | 継承。Go / Java の既存 gate を維持する                                    |
| ADR-0001                    | 任意 field の追加は互換変更、field 型・意味論変更は非互換                     | 継承。非破壊的な Protocol 拡張を優先する                                  |
| ADR-0003                    | Core は Analyzer 固有の意味を解釈しない                                       | 継承。Core に Gradle 固有の module discovery を入れない                   |
| ADR-0005                    | JavaParser / SymbolSolver、SootUp、Spring DI、Core の責務境界                 | 継承。複数 root 対応で Interface Dispatch / Spring DI の規則を変えない    |

上位文書との矛盾は検出していない。
Protocol と Java Analyzer の durable な追記内容は、clarify で決定後に track / sync phase で反映する。

## 関連資料

- [Issue #24](https://github.com/Fukuemon/depwalk/issues/24): 本 spec の要求起点
- [Design Doc](../../design/DesignDoc.md): Why / What、成功条件 S1 / S2 / S4 / S5、設計原則 P1〜P4
- [Analyzer Protocol feature doc](../../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md): `analysisRequest`、相対 path、互換性契約
- [Java Analyzer feature doc](../../design/features/java-analyzer/DesignDoc_java-analyzer.md): source 解析、型解決、帰属型、性能、三層テスト
- [context/architecture.md](../../context/architecture.md): Core / Analyzer の package・runtime・state boundary
- [context/testing.md](../../context/testing.md): Protocol contract test と Java Analyzer 三層
- [ADR-0001](../../adr/0001-analyzer-protocol-jsonl-spi.md): JSONL process SPI と versioning
- [ADR-0003](../../adr/0003-analyzer-command-resolution.md): 言語非依存な Analyzer 起動・metadata passthrough
- [ADR-0005](../../adr/0005-adopt-sootup-and-spring-di-resolution.md): JavaParser / SootUp / Spring DI の責務境界
- [spec #21](../21-java-dispatch-spring-di/index.md): 単一 source root 制約から本 Issue を切り出した決定経緯

## 背景

現行の `analysisRequest` は単一の `workspaceRoot` を持つ。
Java Analyzer はその値を、対象ファイルの列挙、`JavaParserTypeSolver` の source root、`SourceLocation.path` の相対化基準として兼用している。

この構成では、`module-a/src/main/java` と `module-b/src/main/java` のように複数の package hierarchy 起点を持つ Gradle マルチモジュールを一度に型解決できない。
repository root を `workspaceRoot` にすると `JavaParserTypeSolver` が package hierarchy を認識できず、単一 module の source root を渡すと他 module が解析 scope から外れる。

Issue #21 で追加した Interface Dispatch と Spring DI 解決を標準的なマルチモジュール構成へ適用するため、repository 基準の path と Java の source root を区別し、複数 module を単一の解析要求で扱える契約が必要である。

## スコープ

### やること

- Analyzer Protocol で複数 source root を表す非破壊的な入力契約を設計する。
- Core CLI から複数 source root を言語非依存な値として受け取り、`analysisRequest` へ渡す契約を設計する。
- Java Analyzer が複数 source root の Java ファイルを列挙し、各 root を型解決へ登録できるようにする。
- 全 source root の対象ファイルを同一 scope membership として扱い、既存の帰属型決定規則を維持する。
- module 間の source / bytecode / dependency classpath を使った型解決境界を決める。
- 単一・複数source rootを同じv1契約で扱い、既存の内部request / fixtureを公開前の確定schemaへ移行する。
- Gradle マルチモジュールの Spring Boot fixture を追加し、module 間の型解決・帰属・DI 解決を検証する。

### やらないこと

- Interface Dispatch、Spring Bean 選択、帰属型決定の意味論自体は変更しない。Issue #21 と Java Analyzer feature doc の既存契約を継承する。
- Gradle Tooling APIのmodel取得は行うが、Gradle taskの自動実行、source生成、対象projectのbuildは行わない。
- Maven、BazelなどGradle以外のbuild system固有の自動検出は扱わない。
- KotlinなどJava以外の言語解析は扱わない。
- Runtime Trace、Reflection、実行時Proxyの完全追跡は扱わない。
- CLIの出力形式やTraversal / Output Engineの仕様は変更しない。
- 解析対象repositoryへの書き込みは行わない。

## 要件の解釈

### 実現したいユーザー価値

Gradle マルチモジュールで構成された Java / Spring Boot プロジェクトを保守する開発者が、module 境界をまたぐ caller / callee と Spring DI 経由の実装候補を、一度の解析要求で調査できる。

### 成功条件

- 複数 source root の各 package hierarchy を型解決へ登録し、module 間参照の caller / callee を graph に含められる。
- 全 source root の Java ファイルが同一解析 scope に含まれ、scope 内宣言を scope 外として誤帰属しない。
- `SourceLocation.path` と include / exclude の基準が request 全体で一意になり、異なる module の同名相対 path を区別できる。
- 単一source rootのbuildも、build model discoveryまたは1件の明示overrideで解析できる。
- マルチモジュール fixture で、型解決・帰属・Spring DI 解決の期待集合を自動テストできる。

### 対象ユーザー / 操作主体

- Gradle マルチモジュールの Java / Spring Boot プロジェクトを解析する開発者
- depwalk を CI から実行する開発者
- Analyzer Protocol、Core CLI、Java Analyzer を保守する開発者

EARS 風で振る舞いを記述する。

- WHEN 利用者が 1 つの workspace と複数の source root を指定したとき、システムは全 source root の対象 Java ファイルを 1 つの解析 scope として解析する。
- WHEN ある module の source が別 module の source type を参照するとき、Java Analyzer は対象 type を解決し、既存の帰属型決定規則に従う `methodSymbol` と `callEdge` を出力する。
- WHEN Spring の注入点と Bean 実装が異なる module にあるとき、Java Analyzer は Issue #21 で確定した Bean 選択規則に従って実装候補を出力する。
- IF `sourceRoots` が省略されたとき、Java Analyzer はGradle Tooling APIで`workspaceRoot`のbuild modelを取得し、Java source rootの検出結果を解析入力として使用する。
- IF 1件以上の `sourceRoots` が指定されたとき、Java Analyzer はbuild model discoveryを行わず、明示されたrootだけを解析入力として使用する。
- IF `sourceRoots` が空配列のとき、Java Analyzerは解析開始前にinvalid requestとして拒否する。
- IF build model discoveryがsource rootを確定できないとき、Java Analyzerはdirectory走査で推測せず、明示指定を案内するfatal errorを返す。
- IF 明示された`sourceRoots`が絶対path、空文字、または`..` segmentを含むとき、システムは解析開始前にinvalid requestとして拒否する。
- WHEN workspace自体をsource rootとして指定するとき、利用者は`sourceRoots`の要素に`.`を指定できる。
- WHEN 複数のsource rootが正規化後に同一となるとき、Java Analyzerは先頭のrootだけを採用する。
- IF 異なるsource rootの間に親子の包含関係があるとき、Java Analyzerは解析開始前にinvalid source root configurationとして拒否する。
- THE SYSTEM SHALL 同一の正規化済み絶対pathを持つsource fileを1回だけ解析する。
- WHEN Gradle build modelからsource rootを自動検出したとき、Java Analyzerは各source fileを所属project / source setのcompile classpathとproject依存関係を持つ解析contextで型解決する。
- WHEN `sourceRoots`を明示overrideしたとき、Java Analyzerは全明示rootと`metadata.classpath`を共有する1つの解析contextとして扱う。
- IF 異なる解析contextが同じsource binary nameを宣言するとき、Java Analyzerはgraph recordを出力する前に曖昧な入力として拒否する。
- IF 明示source rootが欠落、非directory、読取不能、またはsymlink解決後にworkspace外となるとき、Java Analyzerは解析開始前にfatal errorとして拒否する。
- WHEN build model上のsource directoryがまだ存在しないとき、Java Analyzerは空のsource directoryとして除外し、他の有効rootを解析する。
- IF discoveryされた既存source rootが読取不能、またはin-scope projectからworkspace外を参照するとき、Java Analyzerは不完全解析へ降格せずfatal errorとして拒否する。
- WHEN model由来のproject classes outputだけが存在しないとき、Java Analyzerは`JAVA_SOOTUP_UNAVAILABLE`を出力し、source解析を継続する。
- THE SYSTEM SHALL single-rootの明示・自動discoveryとmulti-moduleの自動discoveryについて、初回値とwarm run 3回の中央値を計測し、解析時間と最大RSSの増分を記録する。
- WHEN 3 module fixtureをworkspace rootから自動discoveryまたは3 rootの明示overrideで解析したとき、システムは同じ期待method / edge集合とworkspace相対locationを出力する。
- THE SYSTEM SHALL `SourceLocation.path` を workspace 全体で一意に解釈できる相対 path として出力する。
- THE SYSTEM SHALL Core に Gradle、JavaParser、JVM 固有の module discovery または型解決ロジックを追加しない。

## 設計時の論点

設計・実装フェーズへ持ち越す残課題を 1 件ずつ管理する。
確定したものは「解決済みの論点」へ移す。

| #   | 論点                                                                                        | 決定候補                                                                                                                   | 決定     |
| --- | ------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- | -------- |
| D1  | Protocol で複数 source root をどう表現し、既存 `workspaceRoot` request と互換にするか       | D: optional `sourceRoots` を明示overrideとして追加し、省略時はAnalyzer側のbuild model discoveryへ委譲する。空配列はinvalid | 解決済み |
| D2  | `sourceRoots` の path 基準と validation をどう定義するか                                    | A: `workspaceRoot`相対のみ。`/`区切り、`.`を許可し、絶対path・空文字・`..` segmentを拒否                                   | 解決済み |
| D3  | source root discoveryの実現方式とCLIの明示override / opt-inをどう設計するか                 | A: Java AnalyzerがGradle Tooling APIで自動取得し、repeatableな共通`--source-root`指定時はdiscoveryを完全bypass             | 解決済み |
| D4  | `workspaceRoot`、source 列挙、include / exclude、`SourceLocation.path` の責務をどう分けるか | A: `workspaceRoot`を唯一の座標系とし、`sourceRoots`はsource列挙とTypeSolver登録の起点だけを担う                            | 解決済み |
| D5  | root の重複・包含関係・同一ファイルの重複列挙をどう扱うか                                   | A: 完全重複rootは先頭を残して除去し、異なるrootの包含関係はerror、fileは絶対pathで重複排除                                 | 解決済み |
| D6  | module ごとの classes output / dependency classpath をどう渡して型解決するか                | B: discovery時はGradle project / source set別の解析context、明示root時はglobal `metadata.classpath`を持つ単一context       | 解決済み |
| D7  | source root の欠落・読取不能・workspace 外指定を fatal と部分解析のどちらで扱うか           | C: 明示rootと既存rootの異常はfatal、未作成discovery rootは除外、model由来classes output欠落はsource-only継続               | 解決済み |
| D8  | 複数 root 追加による解析時間・最大 RSS をどう評価するか                                     | A: single明示 / single discovery / multi discoveryの初回・warm中央値を記録し、数値SLOは#22で確定                           | 解決済み |
| D9  | E2E fixture の module 構成と合格条件をどこまで含めるか                                      | A: app → service → repositoryの3 module、変更projectDir・custom source dir、module間call / DIを固定期待集合で検証          | 解決済み |

## 解決済みの論点

- **D1: `sourceRoots`をoptionalな明示overrideとしてv1 `analysisRequest`へ追加する。**
  - `workspaceRoot`はbuild全体と`include` / `exclude` / `SourceLocation.path`の共通基準として必須のまま維持する。
  - `sourceRoots`が省略された場合、Analyzerは対象言語・build systemに対応するbuild modelから1件以上のsource rootを解決する。Java AnalyzerはD3で確定したGradle Tooling APIの自動discoveryを使用する。
  - `sourceRoots`が1件以上指定された場合、指定値を正としbuild model discoveryを行わない。単一rootと複数rootは同じ配列schemaで表現する。
  - 空配列は「自動検出」または「解析対象なし」と解釈せず、invalid requestとして解析開始前に拒否する。
  - build model discoveryに失敗した場合、directory走査や`src/main/java`の推測へfallbackせず、明示的な`sourceRoots`指定を案内するfatal errorを返す。
  - 決定理由: 標準的なGradle multi-project buildはroot settingsからproject階層を解決できる一方、source set、`projectDir`、composite build等によりfilesystem規約だけではsource rootを確定できない。通常利用はworkspace rootだけで開始でき、非標準構成・Gradle modelを取得できない環境では明示overrideに切り替えられる境界とする。
  - トレードオフ: requestの意味がAnalyzerのbuild model discovery能力に依存する。Coreはdiscoveryを解釈せず、Analyzerが検出結果と失敗理由を観測可能にする必要がある。
  - 移行方針: 公開済みv1 consumerは存在しないため、現行実装でsource rootとして渡している`workspaceRoot`と内部fixtureを、build root + optional `sourceRoots`の確定schemaへ一括移行する。旧実装挙動の互換分岐は残さない。
  - ADR判断: ADR-0001のoptional field互換性、ADR-0003のCore言語非依存、ADR-0005のJava Analyzer責務と整合するため、D1単独では新規ADR・既存ADR廃止は不要。D3のGradle Tooling API採用判断は新規ADRへ記録する。
  - 決定日: 2026-07-16
  - 決定者: Fukuemon

- **D2: `sourceRoots`は`workspaceRoot`からの相対pathだけを許可する。**
  - Protocol上のpath separatorは`/`へ正規化する。絶対path、空文字、`..` segmentを含む値はinvalid requestとして解析開始前に拒否する。
  - `.`はworkspace directory自体がpackage hierarchyの起点となる単一root構成を表す正当な値として許可する。
  - build model discoveryが返すsource directoryはAnalyzer内部で絶対・正規化pathとして解決した後、`workspaceRoot`配下であることを検証し、同じworkspace相対表現へ正規化する。
  - workspace外のproject directoryやcomposite included buildはv1の解析scopeへ含めない。D7の規則に従いexternal included buildはwarning付きで除外し、in-scope projectのworkspace外source参照はfatalとする。
  - rootとfileの重複・包含関係は、D7でsymlinkの実体pathとworkspace境界を検証した後にD5の規則を適用する。
  - 決定理由: requestとfixtureをlocal / CI間で再利用でき、既存の`include` / `exclude`および`SourceLocation.path`と同じ`workspaceRoot`基準に揃えられる。絶対pathと相対pathの混在による重複・境界判定を避ける。
  - トレードオフ: workspace外に配置されたGradle projectやcomposite buildは単一requestで解析できない。必要性が具体化した時点で、複数workspaceまたはbuild treeを表す後続Protocolを設計する。
  - ADR判断: 既存のAnalyzer Protocol path規則の補足であり、新規ADR・既存ADR更新・廃止は不要。
  - 決定日: 2026-07-16
  - 決定者: Fukuemon

- **D3: `sourceRoots`省略時、Java AnalyzerがGradle Tooling APIでbuild modelを自動取得する。**
  - Java AnalyzerはGradle wrapperを認識するTooling APIからproject階層とJava source setを取得し、D2のworkspace相対`sourceRoots`へ正規化する。classpathとclasses outputはD6のproject / source set別解析contextへ対応付ける。
  - Core CLIは言語非依存なrepeatable flag `--source-root <path>`を提供し、指定順を`analysisRequest.sourceRoots`へ渡す。CoreはGradle、SourceSet、Tooling APIの意味を解釈しない。
  - `--source-root`が1件以上指定された場合、Java AnalyzerはGradle Tooling APIを起動せず、明示rootだけを使用する。明示rootはGradle discoveryを避ける安全・再現性のための完全overrideとする。
  - Gradle model取得ではbuild task、compile、source生成を自動実行しない。ただしsettings / build scriptとpluginのconfigurationは評価され、Gradle wrapper取得、plugin / dependency解決、`.gradle` cache等のnetwork・filesystem副作用が起こり得ることを利用者へ明記する。
  - discovery開始・終了、使用したGradle version、検出project数・source root数、失敗理由をstderrへ出力し、Tooling API実行を観測可能にする。D8の計測ではmodel discovery時間とcontext構築時間も分離する。
  - Tooling APIが利用できない、build model評価が失敗する、またはJava source rootを1件も検出できない場合は、filesystem走査へfallbackせず、`--source-root`による明示overrideを案内するfatal errorを返す。
  - 決定理由: root projectだけを入力する通常経路で、`settings.gradle(.kts)`のproject階層、変更された`projectDir`、custom Java source setをGradle自身のmodelに従って解決できる。明示overrideによりGradleを評価できない環境と信頼できないCIにも対応する。
  - トレードオフ: rootだけの解析はbuild scriptを評価するため、静的なsource読取だけより起動コストと安全上の注意が増える。Tooling API・Gradle wrapperのversion compatibilityとmodel取得失敗を保守対象に追加する。
  - ADR判断: Gradle Tooling APIという依存選定とbuild script評価のruntime / security boundaryはfeature docの詳細を超える横断判断である。sync phaseで新規ADRを作成する。ADR-0001 / ADR-0003 / ADR-0005は廃止せず、本決定との関係を新規ADRから参照する。
  - 決定日: 2026-07-16
  - 決定者: Fukuemon

- **D4: `workspaceRoot`をrequest全体の唯一のpath座標系とし、`sourceRoots`は列挙・型解決の起点だけを担う。**
  - `workspaceRoot`はGradle build root、`sourceRoots`の相対基準、`include` / `exclude` globの評価基準、全`SourceLocation.path`の相対基準を兼ねる。
  - `sourceRoots`はJava source file列挙と各`JavaParserTypeSolver`登録の起点に限定する。rootごとのpath namespaceやroot IDはProtocolへ追加しない。
  - 各rootから列挙したfileは絶対・正規化pathへ変換し、`workspaceRoot`からの相対pathに統一してから`include` / `exclude`を評価する。globは`app/src/**/*.java`のようにmodule directoryを含むworkspace相対pathへ一致させる。
  - `methodSymbol.sourceLocation`、`callEdge.callSite`、`diagnostic.sourceLocation`はすべてworkspace相対pathを出力する。同じpackage / file名が複数moduleにあってもmodule directoryを含むpathで区別する。
  - scope membershipはinclude / exclude適用後の全rootの正規化済み絶対file集合を和集合として構築する。`AttributionResolver`のscope内判定規則自体は変更しない。
  - 同一fileが複数rootから列挙される場合はD5の規則で1件へ重複排除し、異なるrootの包含関係は解析前に拒否する。
  - 決定理由: D2のworkspace相対契約と既存Protocolの`include` / `exclude`、`SourceLocation.path`を同じ基準へ揃え、module間で同名pathが存在してもrecordを一意に解釈できる。root別基準に必要なroot ID追加を避けられる。
  - トレードオフ: module内だけを基準にした短いglobは使えず、利用者はworkspaceからmodule directoryを含むglobを指定する。
  - ADR判断: 既存Analyzer Protocolのpath意味論を複数rootへ適用する補足であり、新規ADR・既存ADR更新・廃止は不要。
  - 決定日: 2026-07-16
  - 決定者: Fukuemon

- **D5: 完全重複rootは除去し、異なるrootの包含関係は拒否し、同一fileは1回だけ解析する。**
  - D2の規則でworkspace相対rootを絶対・正規化pathへ解決した後、同じpathとなるrootは先頭の1件だけを残す。残ったrootの順序は明示指定またはbuild model discoveryの順序を維持する。
  - 正規化後も異なるrootの一方が他方の祖先directoryとなる場合、Java AnalyzerはTypeSolver構築やsource列挙より前のpre-flightでfatal errorとして拒否する。`.`と`module/src/main/java`の同時指定も包含関係として扱う。
  - 各rootは`JavaParserTypeSolver`へ1回だけ登録する。包含rootを自動的に短縮・統合して、どちらかのpackage hierarchyの意味を暗黙に変更しない。
  - include / exclude適用後のsource fileは正規化済み絶対pathをkeyとする集合へ統合し、parse、symbol出力、call edge抽出を1回だけ行う。
  - D7のpre-flightでworkspaceと既存rootの実体pathを解決した後、その実体pathをroot / fileの同一性と包含判定に使用する。directory列挙ではsymlinkを再帰追跡しない。
  - 決定理由: 完全重複は解析意味を変えずに吸収できる。一方、親子rootは同じfileを異なるpackage hierarchy起点として解釈し得るため、fileだけを重複排除してもTypeSolverの曖昧さが残る。意味を推測せず設定修正を求める。
  - トレードオフ: 意図的に親子source directoryを構成するbuildは自動解析できず、重ならないsource rootへ構成を直す必要がある。完全重複を自動除去するため、入力ミスとしてはfatalにならない。
  - ADR判断: Java Analyzerの入力正規化とpre-flightの詳細であり、新規ADR・既存ADR更新・廃止は不要。
  - 決定日: 2026-07-16
  - 決定者: Fukuemon

- **D6: 自動discoveryではGradle project / source setごとの解析contextで型解決する。**
  - Tooling APIから得るproject pathとsource set名をAnalyzer内部のcontext識別子とし、source root、compile classpath、classes output、project依存関係を`SourceSetAnalysisContext`へ対応付ける。この識別子はProtocol recordへ出力せず、root IDも追加しない。
  - 各source fileは所有元contextの`CombinedTypeSolver`で解析する。solverには自身のsource rootと、Gradleのcompile classpath関係から到達できる解析対象project / source setのsource root、当該contextの外部jar / classes directory、JDK標準型だけを登録する。
  - 解析対象projectのclasses outputと外部依存はcontextごとのlazyなSootUp indexへ登録する。moduleごとに異なる依存versionや非依存moduleの型をglobal classpathへ混在させない。
  - 同じ正規化rootが複数の異なるproject / source setへ所属する場合は所有contextを推測せず、model ambiguityとしてfatal errorにする。同一context内の完全重複rootはD5どおり除去する。
  - 全contextのsource fileはD4のworkspace scopeへ統合し、method symbolとcall edgeは既存graphへ集約する。ただし異なるcontextが同じsource binary nameを宣言すると、現行Protocolの`methodId`ではmodule別に区別できないため、record出力前にfatal errorとする。
  - `sourceRoots`を明示した場合はD3どおりTooling APIを完全bypassし、全明示rootを1つのsynthetic contextとして扱う。この経路では`metadata.classpath` keyを従来どおり必須とし、空配列を許可する。
  - `sourceRoots`を省略した自動discovery経路では、context classpathをGradle modelから取得するため`metadata.classpath`を任意とする。指定されたentryは利用者による共通追加classpathとして全contextへ追加し、明示entryのpre-flight規則を維持する。
  - model由来のclasses output欠落時はD7どおりsource解析を継続し、SootUp補完だけをwarning付きで無効化する。Java Analyzerはclasses生成やGradle taskを自動実行しない。
  - 決定理由: 全moduleのclasspathを単純結合すると、本来依存していない型やmoduleごとに異なる依存versionを誤って解決し得る。Gradleのproject / source set境界に従いながら、最終graphだけをworkspace単位へ統合する。
  - トレードオフ: contextごとにTypeSolverとSootUp indexを構築するため、global solverより実装量とmemory使用量が増える。明示override経路ではGradle依存関係を利用できず、利用者が与えたglobal classpathの正確性に依存する。
  - ADR判断: D3と同じGradle Tooling API採用判断の一部として、project / source set別contextと明示override時のsynthetic contextをADR-0006へ記録する。追加ADRおよび既存ADRの更新・廃止は不要。
  - 決定日: 2026-07-16
  - 決定者: Fukuemon

- **D7: 明示入力と既存rootの異常はfatal、未作成discovery rootとmodel由来classes output欠落は条件付きで継続する。**
  - pre-flightは`workspaceRoot`のreal pathを境界とし、既存source rootと列挙されたsource fileを`toRealPath`相当で検証する。directory walkではdirectory symlinkを再帰追跡せず、rootまたはfileのsymlink実体がworkspace外ならfatalにする。
  - 明示`sourceRoots`の各要素は存在する読取可能なdirectoryでなければならない。欠落、broken symlink、非directory、読取不能、workspace外実体pathは`JAVA_INVALID_SOURCE_ROOT`または`JAVA_SOURCE_ROOT_OUTSIDE_WORKSPACE`のfatal errorとする。
  - build modelが返すsource directoryが存在しない場合は、未作成の空source directoryとして解析対象から除外し、件数とpathをstderrのdiscovery summaryへ出力する。Protocol diagnosticにはせず、既存の有効rootへ影響させない。
  - discoveryされたsource rootが存在するにもかかわらず非directory・読取不能の場合、またはin-scope projectのsource setがworkspace外pathを参照する場合は、同じfatal error境界を適用する。不完全なmoduleだけを飛ばして成功扱いにしない。
  - Tooling APIがworkspace外のexternal composite / included buildを識別した場合、そのbuildのsource rootはscopeへ含めず、`JAVA_EXTERNAL_BUILD_EXCLUDED` warningを1 buildにつき1件出力する。Gradle modelが解決済みartifactをclasspathとして返す場合は外部依存として利用できる。
  - symlink実体pathの完全重複と包含関係にはD5を適用する。同一実体root / fileは1件へ除去し、異なる実体rootの包含関係はfatalにする。record pathはD4どおり利用者が指定したworkspaceからの相対pathを維持する。
  - model由来の解析対象project classes outputが未作成の場合は、該当contextに`JAVA_SOOTUP_UNAVAILABLE` warningを1件出し、JavaParserによるsource解析を継続する。自動buildやtask実行は行わない。
  - 明示`metadata.classpath` entryと、modelが解決済みcompile classpathとして返した外部jar / classes directoryの欠落・読取不能は`JAVA_MISSING_JAR`のfatalを維持する。広範な型解決欠落を成功に見せないためである。
  - 未作成directoryとexternal buildを除外した結果、build全体で有効なsource rootが0件なら`JAVA_NO_SOURCE_ROOTS`のfatal errorとする。有効rootが存在するがinclude / exclude後のJava fileが0件である場合は、正当な空graphとして成功できる。
  - 決定理由: Gradleでは設定上のsource directoryが未作成でも異常とは限らない。一方、利用者が明示した入力や既存directoryの読取失敗、workspace escape、外部classpath欠落は解析完全性またはsecurity boundaryを壊すためfatalに分ける。
  - トレードオフ: sourceだけで解析を継続したcontextではSootUpが担うbytecode由来のdispatch / Lombok補完が欠ける。ただし既存の`JAVA_SOOTUP_UNAVAILABLE`で明示し、一見完全な結果として扱わない。
  - ADR判断: Tooling API discoveryの失敗・除外・source-only fallbackとworkspace real-path境界を、D3 / D6と合わせてADR-0006へ記録する。追加ADRおよび既存ADRの更新・廃止は不要。
  - 決定日: 2026-07-16
  - 決定者: Fukuemon

- **D8: 性能の数値上限は設けず、3経路の初回値・warm中央値と増分を記録する。**
  - 既存`testdata/fixtures/java/project`を明示single-rootのsynthetic contextで実行し、feature docに記録済みの#21実装後baselineと、Issue #24実装後の解析時間・最大RSSを比較する。
  - 同じsingle-project fixtureを`sourceRoots`省略の自動discoveryで実行し、明示経路との差分としてTooling API model discoveryとcontext構築の増分を記録する。
- D9の`app → service → repository` fixtureを自動discoveryで実行し、複数project / source set contextを含む実運用経路の絶対値を記録する。対応する実装前値は存在しないため、single discoveryとの差を参考値とする。
  - 各経路は同一checkout・Gradle user home・daemon / cache状態で、初回runを1回、その後のwarm runを3回実行する。初回値とwarm 3回の中央値を分け、cache状態を混ぜた単一平均値にしない。
  - 実jar E2Eでtotal wall time、Analyzer stderrのmodel discovery時間・context構築時間・source解析時間、`os.ProcessState.SysUsage()`の最大RSSを取得する。project数、source set context数、root数、解析file数、未解決symbol数も併記する。
  - 計測日、実行command、commit、fixture、JDK / Gradle version、OS / architectureを記録し、結果をJava Analyzer feature docの性能方針へsyncする。
  - correctness testの期待集合一致とfatal / diagnostic境界は必須gateとするが、Issue #24では解析時間・最大RSSの数値による機械的な合否判定を設けない。SLOは既定どおりIssue #22完了時に実プロジェクト規模の測定を含めて確定する。
  - 決定理由: 小規模fixtureではJVM起動、Gradle daemon、cache状態の寄与が大きく、現時点で妥当な上限を設定する材料が不足する。経路別の増分を残せば、#22でSLOを決める入力にできる。
  - トレードオフ: Issue #24の実装中に性能悪化を機械的にrejectするgateはなく、計測結果の妥当性はreviewで判断する。初回とwarm中央値を分離し、少なくともmodel discoveryと複数contextのコストを観測可能にする。
  - ADR判断: 既存feature docの性能方針と#21 D5を継承する計測計画であり、新規ADR・既存ADR更新・廃止は不要。
  - 決定日: 2026-07-16
  - 決定者: Fukuemon

- **D9: `app → service → repository`の3 module fixtureで自動discoveryと明示overrideの同値性を検証する。**
  - fixtureは`testdata/fixtures/java/multi-module-spring-project/`へ配置し、root `settings.gradle.kts`から`:app`、`:service`、`:repository`をincludeする。root project自体にはJava sourceを置かない。
  - `:app`は標準`app/src/main/java`にSpring Boot entrypointとcontrollerを持ち、`:service`へproject dependencyを張る。`:service`は`projectDir = file("modules/service")`で移動し、service interface / `@Service`実装とconstructor injectionを持ち、`:repository`へ依存する。
  - `:repository`はmain Java source directoryを`repository/src/domain/java`へ変更し、repository interface / `@Repository`実装を持つ。これによりroot include、変更`projectDir`、custom source directoryを1つのhappy-path fixtureで検証する。
  - controllerからservice interface、service実装からrepository interfaceへの通常callと、module境界をまたぐconstructor injection / interface実装候補を含める。期待`methodSymbol`、`callEdge`、dispatch / DI provenance、diagnostic集合を固定値としてE2Eで照合する。
  - 自動discovery E2Eはworkspace rootだけを渡し、`sourceRoots`と`metadata.classpath`を省略する。3つの解析対象project / main source set contextと3 source rootを検出し、project依存方向に従って型解決することを確認する。
  - 明示override E2Eは3 source rootとfixture buildが生成するglobal classpath manifestを渡し、Tooling APIを起動しない。自動discovery経路と同じmethod / edge / diagnostic集合を出力することを確認する。discovery固有のstderr metricsは同値比較から除外する。
  - 全`SourceLocation.path`が`app/...`、`modules/service/...`、`repository/...`のworkspace相対pathとなること、module directoryを含むinclude / excludeが対象method集合へ反映されることを検証する。
  - fixtureはE2E前にbuildしてclasses outputを用意し、D8のmulti discovery性能計測にも使用する。classes output欠落、root欠落、symlink escape、重複・包含root、model ambiguity、同一binary name等は小さなJava unit / process contract fixtureへ分離する。
  - 既存single-root fixtureのunit / E2Eを残し、単一rootの明示overrideとsingle-project discoveryが退行しないこともrequired gateとする。
  - 決定理由: 3 moduleの2段project依存で通常callとSpring DIを検証しつつ、変更`projectDir`とcustom source directoryでfilesystem規約ではなくGradle modelを使う価値を確認できる。異常系を分離し、happy-path失敗の原因を限定する。
  - トレードオフ: external included buildやmoduleごとのdependency version衝突はprimary E2Eへ含めない。D6 / D7の専用unit / process contract testで責務境界を検証する。
  - ADR判断: Java AnalyzerのE2E fixtureと受け入れ基準の詳細であり、新規ADR・既存ADR更新・廃止は不要。
  - 決定日: 2026-07-16
  - 決定者: Fukuemon

## 未確定事項

なし。D1〜D9はすべて解決済みである。

## 実装対象

正規 target は `context/project.md` の対象ドメイン一覧を正本とする。

| モジュール          | 実装有無 | 主な責務                                                                     |
| ------------------- | :------: | ---------------------------------------------------------------------------- |
| `core`              |    ◯     | `--source-root`入力、request組み立て、言語非依存な複数rootの受け渡し         |
| `traversal`         |    -     | graphに入ったedgeを既存規則で探索する。変更しない                            |
| `output`            |    -     | 既存のgraph出力を継承する。変更しない                                        |
| `analyzer-protocol` |    ◯     | 複数 source root のwire schema、validation、互換性                           |
| `java-analyzer`     |    ◯     | Gradle Tooling API discovery、複数root列挙、TypeSolver、pre-flight、unit/E2E |

責務境界は Core → Analyzer の Protocol 接続を維持する。
Core は path の正規化と共通 request schema だけを扱い、Gradle module や Java の package hierarchy を解釈しない。

## 機能仕様

### User Flow

1. 利用者が解析対象workspaceとAnalyzer起動情報を指定し、必要ならrepeatableな`--source-root`で1件以上を明示overrideする。
2. Core が`workspaceRoot`とoptionalな`sourceRoots`を言語非依存な`analysisRequest`に正規化してAnalyzer processへ送る。
3. Java Analyzerは明示rootがあればその値を採用し、省略時はGradle Tooling APIでbuild model discoveryを行う。real pathでworkspace境界を検査し、未作成discovery rootを除外してから、重複・包含関係をpre-flightし、各rootのfileへworkspace相対のinclude / excludeを適用する。
4. Java Analyzerが各fileを所属project / source setの解析contextで型解決し、結果をworkspace全体のgraph recordとdiagnosticへ統合する。明示rootの場合は全rootを1つのsynthetic contextとして扱う。
5. Core が既存の Graph / Traversal / Output 処理へ結果を渡す。

### Reuse Policy

- Protocol DTOとvalidationは`core/internal/protocol`の既存`AnalysisRequest`を拡張する。
- Gradle Tooling API、Java source setの解釈、TypeSolver構築は`analyzers/java/`に閉じる。
- Gradle build modelの共通 abstractionは本Issueで追加しない。

### Performance

- 複数rootでも解析済みファイルのASTを保持し続けない既存方針を継承する。
- Gradle model discoveryの所要時間と検出project / root数を観測可能にする。
- 明示single-root、single-project自動discovery、multi-module自動discoveryの3経路で、初回値とwarm run 3回の中央値を記録する。
- 数値SLOは本Issueで設けず、Issue #22で実プロジェクト規模の計測を含めて確定する。

### Routing / URL State

非該当。CLIツールであり、画面routingとURL stateを持たない。

### Content / Assets

非該当。外部配信コンテンツと静的assetを持たない。
解析対象sourceとbuild成果物はread-onlyで扱う。

### UI Reuse

非該当。Web UI / IDE Pluginは対象外である。

### Testing

- `analyzer-protocol`: wire schema、validation、後方互換性をcontract testで検証する。
- `java-analyzer`: root列挙、TypeSolver、scope membership、pre-flightをJUnitで検証する。
- 実jar E2E: D9の3 module fixtureについて、自動discoveryと明示overrideのmethod / edge / diagnostic固定期待集合、workspace相対location、module globを照合する。
- 性能はD8の3経路を計測・記録し、multi discoveryにはD9 fixtureを使用する。

## Interface 設計

### UI / API / Event Interface

外部interfaceはCLIとAnalyzer Protocolの`analysisRequest`である。
`workspaceRoot`は必須、`sourceRoots`はoptionalな明示override、空配列はinvalidとする。
Core CLIはrepeatableな`--source-root <path>`を`sourceRoots`へ写像する。省略時はJava AnalyzerがGradle Tooling APIで自動discoveryする。
`workspaceRoot`をsource列挙後のglob評価と全`SourceLocation.path`の唯一の相対基準とする。
Java Analyzerは正規化後の完全重複rootを先頭優先で除去するが、異なるrootの包含関係はinvalid configurationとして拒否する。
自動discovery時のproject / source setとclasspathの対応はJava Analyzer内部状態とし、Protocolへmodule IDやroot IDを追加しない。

### Props / Request / Response

- Request: 必須の`workspaceRoot`、optionalかつ指定時は1件以上のworkspace相対`sourceRoots`、`include` / `exclude`、条件付きの`metadata.classpath`。`sourceRoots`は`/`区切りで、`.`を許可し、絶対path・空文字・`..` segmentを拒否する。
- `metadata.classpath`: `sourceRoots`明示時はkey必須かつ空配列を許可する。自動discovery時は任意の共通追加classpathとして全contextへ適用する。
- Response: 既存の`methodSymbol` / `callEdge` / `diagnostic` / `error`を変更しない。
- `SourceLocation.path`: `methodSymbol`、`callEdge`、`diagnostic`の全recordでworkspace相対pathを使用し、module directoryを含めて一意にする。

## Content / Data 設計

### 保存・管理するデータ

永続データは追加しない。
重複排除済みsource root、workspace相対path、scope fileの絶対path集合、project / source set別の`SourceSetAnalysisContext`、TypeSolver、SootUp index、graph構築用の状態はAnalyzer process内だけに保持する。

### コンテンツ配置 / package / route

- Core DTO / validation / CLI: `core/internal/protocol`、`core/internal/analyze`、`core/internal/cli`
- Java Analyzer: `analyzers/java/`
- Protocol fixture: `testdata/analyzer-protocol/`
- 実jar E2E fixture: `testdata/fixtures/java/`

詳細なclass配置はclarify後の実装分割で確定する。

## Performance / Security 設計

### Performance

既存のAST逐次破棄とmode別streaming方針を継承する。
Gradle model discovery、context別`CombinedTypeSolver` / lazy SootUp index、root横断index、classpathの構築コストはD8の計測対象とする。
既存single-root baselineとの差分、single自動discoveryのTooling API増分、multi-module実運用経路を、初回値とwarm中央値に分けてfeature docへ記録する。数値による合否判定は行わない。

### Security / Privacy

解析対象source、classes directory、依存jarはread-onlyで扱い、外部送信しない。
`sourceRoots`と列挙fileはsymlink解決後の実体pathもworkspace配下に限定する。directory symlinkは再帰追跡せず、workspace外実体pathはfatalにする。
Tooling APIによるdiscoveryはGradle taskやbuildを実行しないが、settings / build scriptとplugin configurationを評価する。Gradle評価を避ける場合は`--source-root`を明示する。

## Error / Fallback 設計

### エラーケース

| #   | ケース                                       | ユーザーへの見せ方                         | リカバリ                               |
| --- | -------------------------------------------- | ------------------------------------------ | -------------------------------------- |
| E1  | 明示source rootが存在しない                  | `JAVA_INVALID_SOURCE_ROOT` fatal           | 入力修正                               |
| E2  | 既存source rootがdirectoryでない・読めない   | `JAVA_INVALID_SOURCE_ROOT` fatal           | 入力・権限を修正                       |
| E3  | 異なるrootが親子の包含関係にある             | pre-flightのfatal error                    | 重ならないsource rootへ指定を修正      |
| E4  | module間typeを解決できない                   | 既存`JAVA_UNRESOLVED_SYMBOL` diagnostic    | classpath / source rootを修正し再実行  |
| E5  | model由来のproject classes outputが欠落する  | `JAVA_SOOTUP_UNAVAILABLE` warning          | source-only継続。必要ならbuild後再実行 |
| E6  | `sourceRoots`が空配列                        | 解析開始前のinvalid request                | fieldを省略するか1件以上指定する       |
| E7  | build model discoveryでrootを確定できない    | 明示指定を案内するfatal error              | `sourceRoots`を明示して再実行          |
| E8  | 明示rootが絶対path・空文字・`..`を含む       | 解析開始前のinvalid request                | workspace相対pathへ修正する            |
| E9  | in-scope sourceまたはsymlinkがworkspace外    | `JAVA_SOURCE_ROOT_OUTSIDE_WORKSPACE` fatal | workspace内rootだけを指定する          |
| E10 | Gradle wrapper / Tooling API / model評価失敗 | 明示overrideを案内するfatal error          | `--source-root`を指定して再実行        |
| E11 | 1つのrootが複数の解析contextへ所属する       | model ambiguityのfatal error               | Gradle source set構成を修正            |
| E12 | 異なるcontextに同じsource binary nameがある  | record出力前のfatal error                  | package名または解析範囲を分離          |
| E13 | workspace外のexternal included buildを検出   | `JAVA_EXTERNAL_BUILD_EXCLUDED` warning     | artifact利用または別workspaceで解析    |
| E14 | 除外後に有効source rootが0件                 | `JAVA_NO_SOURCE_ROOTS` fatal               | root明示またはbuild構成を修正          |

### Fallback

`sourceRoots`省略時はbuild model discovery、1件以上の明示時は明示値のみを使用する。
discovery失敗時にdirectory走査や規約pathの推測へfallbackしない。
未作成discovery rootとexternal build sourceだけを明示的に除外でき、既存rootの異常やworkspace escapeは部分解析へfallbackしない。
model由来project classes output欠落時だけSootUpを無効化してsource解析を継続する。

## テスト / 評価方針

### テスト観点

- optional field追加後も`sourceRoots`を省略した`analysisRequest`をparse / validateできる。
- 1件・複数件の`sourceRoots`を同じschemaで受理し、空配列を解析開始前に拒否できる。
- workspace相対rootと`.`を受理し、絶対path、空文字、`..` segmentを解析開始前に拒否できる。
- discovery結果をworkspace相対へ正規化し、workspace外rootを解析scopeへ混入させない。
- include / excludeをroot別相対pathではなくmodule directoryを含むworkspace相対pathへ適用できる。
- 異なるmoduleに同じpackage / file名を配置しても、全SourceLocationをworkspace相対pathで区別できる。
- 全rootのscope fileを1つの正規化済み絶対path集合へ統合し、既存のscope membership規則を維持できる。
- `sourceRoots`明示時にbuild model discoveryを行わず、省略時だけdiscoveryを行うことをJava unit / 実jar E2Eの境界で検証できる。
- repeatableな`--source-root`が指定順でrequestへ渡り、1件以上の指定時はTooling APIを起動しない。
- 正規化後に同一となるrootは先頭だけがTypeSolverへ登録され、残ったrootの順序が維持される。
- 異なるrootの親子関係をTypeSolver構築・source列挙前に拒否できる。
- 複数の列挙経路から同じ正規化済み絶対pathへ到達しても、fileを1回だけ解析・出力できる。
- 自動discovery時、各fileが所有project / source setのclasspathとproject依存sourceだけを使って解決され、非依存moduleの型を参照しない。
- moduleごとに異なるdependency versionを持つfixtureで、contextごとのTypeSolver / SootUp indexが分離される。
- 明示root時は全rootが1つのsynthetic contextとglobal `metadata.classpath`を共有し、classpath key不在を既存errorとして拒否する。
- 自動discovery時は`metadata.classpath`を省略でき、指定時は全contextの共通追加entryとなる。
- 同一rootの複数context所属と、異なるcontextのsource binary name重複をrecord出力前にfatalにできる。
- 明示rootの欠落・非directory・読取不能・workspace外symlinkをpre-flightでfatalにできる。
- 未作成discovery rootをstderrへ記録して除外し、他の有効rootを解析できる。
- existing discovery rootの読取不能とin-scope projectのworkspace外source参照をfatalにできる。
- workspace外external included buildをwarning付きでscopeから除外し、解決済みartifactだけを外部依存として利用できる。
- symlink実体pathでD5の重複・包含判定を行い、directory symlinkを再帰追跡しない。
- model由来classes output欠落時にcontext単位の`JAVA_SOOTUP_UNAVAILABLE`を出し、source解析を継続できる。
- 除外後の有効root 0件をfatalとし、include / exclude後のfile 0件は空graphとして成功できる。
- root settingsから`app`、変更`projectDir`の`service`、custom source directoryの`repository`を検出し、3つのmain解析contextへ対応付けられる。
- `app → service → repository`の通常call、constructor injection、interface実装候補について、method / edge / provenance / diagnosticの固定期待集合が一致する。
- 自動discoveryと明示3-root overrideがdiscovery metricsを除いて同じgraphを出力し、全locationがmodule directoryを含むworkspace相対pathになる。
- module directoryを含むinclude / excludeが3 module fixtureの対象method集合へ反映される。
- Gradle modelから変更済み`projectDir`とcustom Java source setを検出できる。
- discoveryの開始・終了、Gradle version、project / source root数、失敗理由をstderrで観測できる。
- discovery失敗時にfilesystem推測へfallbackせず、明示指定を案内するfatal errorを検証できる。
- 複数rootのvalidation、path正規化、symlink境界をD2 / D5 / D7の決定どおり検証できる。
- 各source rootを`JavaParserTypeSolver`へ登録し、module間のsource typeを解決できる。
- 全rootのscope file集合によってscope内宣言を正しく判定できる。
- moduleをまたぐSpring DI候補とcaller / calleeの期待集合が一致する。
- 既存の単一root unit / E2E testが変更後も通る。
- 明示single-root、single-project discovery、multi-module discoveryを同一環境で初回1回・warm 3回計測し、warm中央値を算出できる。
- correctnessの期待集合を性能値より先に検証し、計測結果を数値gateにせずfeature docへ記録できる。

### 計測指標

- project数 / source set context数 / 解析root数
- 解析Javaファイル数
- total wall time / model discovery時間 / context構築時間 / source解析時間
- 最大RSS (`os.ProcessState.SysUsage()`)
- 未解決symbol件数
- 期待caller / callee集合との差分

期待caller / callee集合、dispatch / DI provenance、error / diagnostic境界は合否判定する。性能値は初回値・warm中央値・既存baselineとの差分を記録し、数値上限による合否判定はしない。

## フロー / シーケンス

diagram phaseで、CLI入力から複数root列挙・型解決・graph出力までのflowchartと、Core / Protocol / Java Analyzerのsequenceを生成する。
D1〜D9は解決済みである。clarify review gate通過後にdiagram phaseで確定図を生成する。

### Flowchart (ユーザー操作起点)

```mermaid
flowchart TD
```

### Sequence

```mermaid
sequenceDiagram
```

## 実装分割

### 実装タスク案

| Phase | 対象                         | 概要                                   | 依存                 |
| ----- | ---------------------------- | -------------------------------------- | -------------------- |
| P1    | `analyzer-protocol` / `core` | 複数rootのrequest契約とCLI入力         | D1〜D4の確定後に分割 |
| P2    | `java-analyzer`              | 列挙、型解決、scope、pre-flight        | P1、D5〜D7の確定     |
| P3    | `java-analyzer` / `core`     | fixture、contract、実jar E2E、性能計測 | P1 / P2              |

### prompts 生成方針

- Protocol / CoreとJava Analyzerの責務境界でpromptを分ける。
- wire schema確定後にJava側request modelとTypeSolverを実装する。
- fixtureと実jar E2Eはproduction contractの実装後に行う。
- 詳細な並列可否はtasks phaseで決める。

## 上位資料からの変更点

clarifyで確定したdurableな追加だけをtrack phaseで分類し、sync phaseで上位文書へ反映する。
D1〜D9のdurableな変更候補を記録した。track / sync phaseで反映先と正本ハンドオフを確定する。

### PRD への影響

| 対象節 | 変更内容                                  | 理由             |
| ------ | ----------------------------------------- | ---------------- |
| なし   | 独立PRDなし。Design DocのWhy / Whatを継承 | 統合モードのため |

### Design Doc への影響

| 対象節       | 変更内容                        | 理由                         |
| ------------ | ------------------------------- | ---------------------------- |
| 現時点でなし | landscapeの責務境界を変更しない | 既存P1〜P4の範囲内であるため |

### feature doc への影響

| 対象 doc / 節                          | 変更内容                                                                                                                        | 理由                                                                                       |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| analyzer-protocol / `analysisRequest`  | (source: clarify, D1) optional `sourceRoots`を明示overrideとして追加し、省略時はAnalyzer discovery、空配列はinvalidとする       | 単一・複数rootを同じv1 schemaで扱い、通常利用と明示overrideを両立するため                  |
| analyzer-protocol / path contract      | (source: clarify, D2) `sourceRoots`をworkspace相対・`/`区切りとし、`.`を許可、絶対path・空文字・`..`を拒否する                  | `include` / `exclude` / `SourceLocation.path`と基準を統一し、requestを環境非依存にするため |
| analyzer-protocol / path contract      | (source: clarify, D4) `workspaceRoot`をinclude / excludeと全`SourceLocation`の唯一の座標系とし、root IDを追加しない             | module directoryを含むpathで一意性を保ち、既存のpath意味論を維持するため                   |
| analyzer-protocol / Java metadata      | (source: clarify, D6) `metadata.classpath`を明示root時は必須、自動discovery時は任意の共通追加classpathとする                    | 自動経路ではGradle modelを正とし、明示経路では従来の入力責任を維持するため                 |
| Java Analyzer / 解析入力解決           | (source: clarify, D1) 明示root優先、build model discovery、推測fallback禁止の責務を追加                                         | Gradle modelで非標準source setを解決し、失敗を不完全解析へ降格しないため                   |
| Java Analyzer / root正規化             | (source: clarify, D2) discovery結果をworkspace相対へ正規化し、workspace外rootをscopeへ含めない                                  | 明示入力とdiscovery結果へ同じpath境界を適用するため                                        |
| Java Analyzer / root正規化・pre-flight | (source: clarify, D5) 完全重複rootは先頭を残して除去し、異なるrootの包含関係を拒否し、fileを絶対pathで重複排除する              | package hierarchyの曖昧化と二重解析を防ぐため                                              |
| Java Analyzer / root pre-flight        | (source: clarify, D7) 明示・既存rootの異常とworkspace escapeはfatal、未作成discovery rootは除外、real pathで境界判定する        | 空directoryを許容しつつ不完全解析とworkspace外読取を防ぐため                               |
| Java Analyzer / 型解決context          | (source: clarify, D6) discovery時はproject / source set別context、明示root時はglobal classpathのsynthetic contextを使用する     | Gradle moduleの依存境界とdependency versionを維持するため                                  |
| Java Analyzer / scope・location        | (source: clarify, D4) 全rootのfileへworkspace相対globを適用し、絶対path集合でscope判定し、locationをworkspace相対で出力する     | 複数rootを単一scopeとして扱い、module間の同名pathを区別するため                            |
| Java Analyzer / build model discovery  | (source: clarify, D3) `sourceRoots`省略時にGradle Tooling APIでproject階層・Java source setを自動取得し、明示時は完全bypassする | rootだけで非標準layoutを解決しつつ、安全・再現性が必要な環境では明示入力へ切り替えるため   |
| Java Analyzer / 性能方針               | (source: clarify, D8) single明示・single discovery・multi discoveryの初回値とwarm中央値を記録し、SLOは#22で確定する             | Tooling APIと複数contextの増分を分離し、将来の数値目標の入力にするため                     |
| Java Analyzer / E2E fixture            | (source: clarify, D9) app / service / repository、変更projectDir、custom source dir、module間call / DIの固定期待集合を追加      | 標準・非標準Gradle構成を実jarで検証し、自動・明示経路の同値性を保証するため                |

### context への影響

| 対象 doc / 節                      | 変更内容                                                                                                                         | 理由                                                                                |
| ---------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| architecture.md / Runtime Boundary | (source: clarify, D3/D6/D7) Java AnalyzerがTooling APIを評価し、project / source set別contextとreal-path workspace境界を管理する | CoreへGradle依存を持ち込まず、build評価・型解決・filesystem境界の所在を明示するため |
| toolchain.md / 標準スタック        | (source: clarify, D3) Java AnalyzerのGradle Tooling API依存を追記                                                                | wrapper-awareなmodel discoveryを標準toolchainとして固定するため                     |
| testing.md                         | (source: clarify, D3/D7/D8/D9) discovery、override、pre-flight、fallback、3経路性能、3 module固定期待集合を追記                  | build model評価・filesystem境界・graph正確性・性能を観測可能にするため              |

### ADR の新規 / 更新

| ADR ID                         | 変更内容                                                                                                              | 理由                                                                                                 |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| ADR-0001 / ADR-0003 / ADR-0005 | (source: clarify, D1/D3) 更新・廃止なし。新規ADRから互換性・Core言語非依存・Java Analyzer責務を参照                   | 既存判断を置換せず、Gradle model discoveryを補足するため                                             |
| ADR-0006 (新規予定)            | (source: clarify, D3/D6/D7) Tooling API自動取得、明示時bypass、source set別context、失敗fallback、workspace境界を記録 | 新規依存、build評価、副作用、型解決、filesystem安全性、version compatibilityを横断判断として残すため |

## レビュー

`spec-review` (fresh-context evaluator) の最新結果。完全な記録は `review.md` を参照する。

| 日付       | 結果 (PASS / NEEDS_WORK) | 指摘要点                                                                               | 対応                              |
| ---------- | ------------------------ | -------------------------------------------------------------------------------------- | --------------------------------- |
| 2026-07-15 | PASS                     | scaffoldの上位文書整合、未決論点管理、対象境界、必須節、EARSを根拠付きで確認。指摘なし | Phase 2 gate完了。clarify開始待ち |
| 2026-07-16 | PASS                     | clarifyのD1〜D9、上位文書整合、対象境界、必須節、EARSを根拠付きで確認。指摘なし        | Phase 3 gate完了。diagram開始待ち |

## 変更履歴

| 日付       | 変更者 | 変更内容                                                                                             |
| ---------- | ------ | ---------------------------------------------------------------------------------------------------- |
| 2026-07-15 | Codex  | Issue #24からscaffoldを作成し、上位文書整合と未決論点D1〜D9を整理                                    |
| 2026-07-15 | Codex  | scaffoldのfresh-context review PASSを記録し、下書き・上位文書突合・論点整理をレビュー済へ更新        |
| 2026-07-15 | Codex  | clarify phaseを開始し、D1から1件ずつ判断する状態へ更新                                               |
| 2026-07-16 | Codex  | D1を解決し、optional sourceRoots、Analyzer discovery、明示override、推測fallback禁止を関連節へ同期   |
| 2026-07-16 | Codex  | D2を解決し、sourceRootsをworkspace相対へ統一するpath・validation契約を関連節へ同期                   |
| 2026-07-16 | Codex  | D3を解決し、Gradle Tooling API自動discovery、明示override、観測・安全境界、新規ADR予定を関連節へ同期 |
| 2026-07-16 | Codex  | D4を解決し、workspaceRootをglob・locationの唯一の座標系とするscope・path契約を関連節へ同期           |
| 2026-07-16 | Codex  | D5を解決し、完全重複rootの除去、包含rootの拒否、source fileの一意化を関連節へ同期                    |
| 2026-07-16 | Codex  | D6を解決し、Gradle project / source set別contextと明示override時のglobal classpath境界を関連節へ同期 |
| 2026-07-16 | Codex  | D7を解決し、rootのfatal / 除外境界、symlink検査、classes outputのsource-only fallbackを関連節へ同期  |
| 2026-07-16 | Codex  | D8を解決し、single / discovery / multiの初回・warm性能計測とSLO非判定境界を関連節へ同期              |
| 2026-07-16 | Codex  | D9を解決し、3 module fixture、非標準layout、自動・明示経路の固定期待集合を関連節へ同期               |
| 2026-07-16 | Codex  | clarify phaseのfresh-context review PASSを記録し、論点解決をレビュー済へ更新                         |

## 備考

API endpoint、永続データ、認可、画面コンポーネント、UI E2Eは対象外である。
このため、spec appendixは追加しない。
