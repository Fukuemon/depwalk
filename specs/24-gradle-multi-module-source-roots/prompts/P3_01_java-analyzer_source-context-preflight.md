# Java解析context、複数root、language level、parse pre-flight

## 絶対ルール

- spec と上位正本に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止する。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証と diff レビューをスキップしない。
- discovery失敗時にfilesystem規約、別model、別language levelへfallbackしない。
- parseできないfileをskipまたはdiagnosticへ降格しない。
- CoreとProtocol schema、call-site completeness、Graph / Outputを変更しない。
- **完了条件のタスク化**: 作業開始前に「完了条件」セクションの各項目を todo として登録し、各ステップ完了時に状態を更新すること。タスク化せずに作業を開始することは禁止。

### 実装アンチパターンの回避 (必守)

- スコープ厳守: spec / 本 prompt に明記された機能のみ実装する。未要求の機能追加・
  先回りの抽象化・無関係なリファクタ・暗黙の互換維持をしない。
- 既存規約への整合: 命名・エラー処理・ログ・テスト・API 連携方式は、対象コードベースの
  既存パターンに合わせる。新方式を持ち込む場合は理由を述べて確認を取る。
- 観測可能な契約の保持: UI 文言・イベント名・戻り値・エラーメッセージ・ログ形式・API を
  要求なく変更しない。変更が必要なら理由と影響を明記する。
- 推測の排除: 要件・業務ルール・API 仕様が不明なら停止して確認する。それらしいが
  誤った実装 (存在しない API 呼び出し / 非互換な引数) を避け、import と API の実在を確認する。
- fallback の最小化: `??` / `||` / 既定引数 / 多段 fallback / 暗黙のエラー握り潰しは
  「任意データ」に限定する。必須データの欠落は隠さず明示的に失敗させる。
- 過剰実装の排除: 単純な条件分岐を strategy / handler map に置換しない。
  要求も計測もない caching / memoization を入れない。
- dead code を残さない: 到達不能コード・未使用の変数 / 関数 / import / export・
  変更後に不要化した型定義を削除する。
- 判断の記録: 非自明な設計判断は理由 (or spec / ADR へのリンク) を残す。

## 作業ステップ (この順序で実行する)

### ステップ 0: ブランチ準備

1. 現在のbranchが`feature/24`であることを確認する。
2. `git status --short`で意図しない差分がないことを確認する。
3. `P2_02_java-analyzer_gradle-model-provider.md`が完了していることを確認する。
4. Issue #24の既存Draft PRを流用し、本promptの完了条件をdescriptionへ追記する。
5. P2_01のCore変更を取り込んでいても、Core pathへ追加変更しない。

### ステップ 1: 明示rootとdiscovery結果を正規化する

1. TDDで明示・discovery両経路のroot validation testを追加する。
2. 明示`sourceRoots`は存在する読取可能なdirectoryに限定する。欠落、broken symlink、非directory、読取不能、workspace外real pathをfatalにする。
3. discovery rootはworkspace相対へ正規化する。未作成directoryは空rootとしてwarning付き除外し、存在する異常rootとin-scope projectのworkspace外source参照はfatalにする。
4. external included buildはin-scope rootの検証前に識別し、1 build 1 warningで除外する。modelが返す解決済みartifactは外部依存として利用できる。
5. real path正規化後の完全重複rootは先頭だけを残し、異なるrootの親子包含をTypeSolver構築とfile列挙前にfatalにする。directory symlinkは再帰追跡しない。
6. fileは正規化済み絶対pathで重複排除し、workspace相対pathへinclude / excludeを適用する。全SourceLocationの基準はworkspaceRootのままにする。
7. 有効rootが0件なら`JAVA_NO_SOURCE_ROOTS`、include / exclude後のJava fileが0件なら空graph成功候補とする。
8. testを実行し、filesystem推測fallbackがないことをdiffレビューする。

### ステップ 2: `SourceSetAnalysisContext`を構築する

1. TDDでproject依存、同一root複数所有、binary name衝突、explicit synthetic contextを検証する。
2. discovery時はproject / `main` source setごとに、root、compile classpath、classes output、project依存、language level、previewを持つ解析contextを構築する。
3. 各fileは所有contextのsolverを使用する。owning contextとGradle project依存で到達可能なcontextのsource rootをproject classes outputより優先する。
4. solver entryと解決結果へ内部`ResolvedDeclarationOrigin`として`source(contextId)`、`projectClasses(contextId)`、`externalArtifact(identity)`、`jdk`を付与できる境界を作る。source再対応付け自体はP4で実装する。
5. contextごとに外部jar / classes directoryとlazy SootUp indexを分離し、非依存moduleや異なるdependency versionをglobal classpathへ混在させない。
6. 同じrootが異なるcontextへ属するmodelと、異なるcontextの同一source binary nameをrecord出力前fatalにする。
7. 明示rootは全root、global `metadata.classpath`、1つのlanguage levelを共有するsynthetic contextとする。
8. 自動discovery時の`metadata.classpath`は任意の共通追加entry、明示root時はkey必須で空配列を許可する。明示entryとmodelの解決済み外部entryの欠落・読取不能はfatalにする。
9. model由来classes output欠落、または明示経路で自project classes output未指定ならcontext単位の`JAVA_SOOTUP_UNAVAILABLE` warningでsource-onlyを許可する。Analyzerからbuild taskを実行しない。
10. testを実行し、context分離とsource-only境界をdiffレビューする。

### ステップ 3: context別language levelを適用する

1. discovery modelの`compileJava.options.release`を優先し、未指定なら実効`sourceCompatibility`を使う。
2. `targetCompatibility`、Analyzer JDK、daemon JVM、project toolchainからsource grammarを推測しない。
3. contextごとのmain parserと`JavaParserTypeSolver`内部parserへ同じlanguage level / previewを設定する。
4. 明示rootでは`metadata.javaLanguageLevel`をcanonicalな10進major versionの1要素文字列配列として必須にする。`1.8`、空、複数、非文字列をinvalidにする。
5. 明示rootのoptional `metadata.javaPreview`は`["true"]` / `["false"]`だけを許可し、省略時falseとする。
6. 自動discovery時にlanguage metadataが指定されたらinvalidにする。
7. level欠落・曖昧・未対応、preview未対応をgraph record出力前fatalにし、別levelへfallbackしない。
8. mixed-version context testを実行し、4つのtoolchain軸を混同していないことをdiffレビューする。

### ステップ 4: 全source fileのparse pre-flightを実装する

1. include / exclude後の全fileをworkspace相対path順に、contextのlanguage levelでparse検証する。
2. 最初の失敗についてworkspace相対path、line / column、language level、parser messageを持つ`JAVA_PARSE_ERROR`を出し、非ゼロexitにする。
3. parse failure時はmethod / edge / diagnosticを1件も出力しない。file skipとpartial modeを追加しない。
4. pre-flightではfileごとにASTを破棄する。全成功後の通常解析で再parseし、全ASTを保持しない。
5. parse pre-flight時間と通常解析時間をstderr metricsで分離できる値を返す。
6. parser未対応の有効構文と構文不正を同じrequest-level fatal境界でtestする。
7. testを実行し、既存のparse diagnostic継続経路を残していないことをdiffレビューする。

### ステップ最終: 最終確認

1. `## 検証コマンド`をすべて実行する。
2. root / context / language / parse異常がstreaming前fatalであることを確認する。
3. call-site ledger、bytecode member、Core / Protocolへ変更していないことを確認する。
4. durable設計との差分が必要なら実装せずspec-lifecycleのtrackへ戻す。
5. 最終diffレビューを行い、指摘を対応する。

## 実装コンテキスト

- spec: `specs/24-gradle-multi-module-source-roots/index.md`
- Java Analyzer正本: `design/features/java-analyzer/DesignDoc_java-analyzer.md`
- testing: `context/testing.md`
- toolchain: `context/toolchain.md`
- ADR: `adr/0005-adopt-sootup-and-spring-di-resolution.md`、`adr/0006-adopt-gradle-tooling-api-discovery.md`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照・変更するpath:
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/Main.java`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/preflight/`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/AnalysisRunner.java`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/TypeSolverFactory.java`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/scope/`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/sootup/`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/normalize/`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/JavaErrorCode.java`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/JavaDiagnosticCode.java`
  - 対応する`analyzers/java/src/test/`配下のtest / fixture。
- P2_02で追加したmodel / discovery adapterは参照・接続してよい。

## 前提条件

- 完了しているべきphase / 依存prompt: `P2_02_java-analyzer_gradle-model-provider.md`。
- 完了後に着手可能になる後続prompt: `P4_01_java-analyzer_call-completeness-bytecode.md`。
- 必要なrepo状態: P2_02のcustom modelと明示bypass入口が実装済みであること。

## 不明点ハンドリング

- 矛盾、欠落、未定義を見つけたら作業を止める。
- root所有context、Gradle依存到達性、source levelのmodel値が一意でない場合は推測しない。
- JavaParserのlevel / preview対応APIは実在を確認し、未対応を通常levelへ降格しない。
- 質問時は、止まっている作業単位、判断論点、選択肢、解析完全性とcontext分離への影響を整理する。

## タスク境界

### 実装する範囲

- 明示 / discovery rootのworkspace境界、重複、包含、file一意化。
- project / main source set別contextとexplicit synthetic context。
- classpath / classes output / project依存 / originのcontext境界。
- context別language level / preview validationとparser設定。
- 全file parse pre-flightと`JAVA_PARSE_ERROR` fatal。
- Java unit / process test。

### 実装しない範囲

- Gradle provider artifact、Tooling API output隔離、version guardの再設計。
- source-bytecode再対応付け、ProjectBytecodeMemberIndex、call-site inventory / ledger。
- `JAVA_INCOMPLETE_ANALYSIS` details。
- Core、Protocol schema、Graph / Traversal / Output。
- primary multi-module fixture、cross-version matrix、required CLI E2E。

## 設計仕様

- `workspaceRoot`はroot、glob、SourceLocationの唯一の座標系である。
- 明示rootと既存discovery rootの異常、workspace escape、包含root、context ambiguityはfatalである。未作成discovery rootとexternal included buildだけはwarning付き除外できる。
- discoveryは各projectの`main`だけをcontext化する。明示rootはglobal classpath / language levelを持つ1 synthetic contextである。
- owning / 到達可能sourceをproject classesより優先し、solver originを保持する。external / JDK /非依存contextを名前一致でsourceへ戻さない。
- model classes output欠落または明示経路の自project classes未指定はsource-only warning、明示entry /解決済み外部entry欠落はfatalである。
- source grammarは`release`優先、次に実効`sourceCompatibility`で決め、contextごとにmain parserとTypeSolver parserへ適用する。
- 全file parse成功前にgraph recordを出さない。1件でもparse不能ならrequest全体fatalで、partial modeはない。

## テスト観点

- workspace相対root、`.`、real path、symlink、重複、包含、file重複排除。
- 未作成root / external buildの除外と、既存異常root / workspace外in-scope sourceのfatal。
- project依存方向のcontext分離、非依存moduleとdependency version衝突の隔離。
- 同一root複数所有、source binary name重複のstreaming前fatal。
- 明示synthetic contextとglobal classpath、discoveryの共通追加classpath。
- classes output欠落時source-onlyと、明示 / model外部entry欠落fatalの境界。
- mixed source level、release優先、preview、invalid / unsupported level。
- parse失敗の決定順、詳細、method / edge未出力、非ゼロexit。
- parse pre-flight後にASTを全件保持しない。

## 検証コマンド

- `cd analyzers/java && ./gradlew test`
- `cd analyzers/java && ./gradlew shadowJar`
- `git diff --check`

## 完了条件

- [ ] ステップ0でbranch、差分、P2_02完了を確認した。
- [ ] 明示 / discovery rootのworkspace / real-path境界とfatal / warning規則を実装した。
- [ ] 重複root、包含root、file重複、external build、有効root 0件を確定契約どおり処理した。
- [ ] project / main source set別contextと明示synthetic contextを実装した。
- [ ] project依存、classpath、classes output、originをcontext別に分離した。
- [ ] classes output欠落時source-onlyとentry欠落fatalの境界を実装した。
- [ ] context別language level / previewを両parserへ同じ値で設定した。
- [ ] 全file parse pre-flightと決定的`JAVA_PARSE_ERROR` fatalを実装した。
- [ ] file skip、partial mode、filesystem / level fallbackを追加していない。
- [ ] Core、Protocol schema、call completenessへ踏み込んでいない。
- [ ] 全作業ステップとdiffレビューを完了した。
- [ ] `## 検証コマンド`がすべてパスした。
- [ ] 未解決の仕様質問が残っていない。
