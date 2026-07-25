# Java call完全性、source帰属、bytecode-only member救済

## 絶対ルール

- spec と上位正本に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止する。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証と diff レビューをスキップしない。
- scope内callの欠落をexternal扱い、file skip、broad catch、partial modeで隠さない。
- generator名やannotation名別の救済分岐を追加しない。
- Core、Protocol schema、Gradle model provider、Traversal / Outputを変更しない。
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
3. P1と`P3_01_java-analyzer_source-context-preflight.md`が完了していることを確認する。
4. Issue #24の既存Draft PRを流用し、本promptの完了条件をdescriptionへ追記する。
5. P3で確定したcontext / origin / parse pre-flightを再設計しない。

### ステップ 1: source宣言索引とorigin制約付き再対応付けを実装する

1. TDDでsource / project classes併存、external衝突、非依存module衝突、synthetic contextを検証する。
2. include / exclude後の全sourceから、所有context、binary name、正規化method signature、workspace相対locationを持つ軽量`WorkspaceSourceDeclarationIndex`を作る。AST全件を保持しない。
3. `projectClasses(targetContext)`由来のbytecode宣言だけを、呼出元から到達可能な同じtarget contextの一意source宣言へ再対応付けする。
4. external artifact、JDK、origin不明、依存到達不能contextをworkspace全体の名前一致でsourceへ戻さない。
5. 明示overrideは同じsynthetic context内の一意sourceだけを候補にする。
6. classes outputの所有context不明は`JAVA_GRADLE_MODEL_ERROR`、解析中のorigin欠落 /到達性違反は`JAVA_INTERNAL_ERROR`でfatalにする。
7. testを実行し、scope内sourceが帰属の正であることをdiffレビューする。

### ステップ 2: call-site inventoryとoutcome ledgerを実装する

1. TDDで全既存call kind、通常method、constructor、lambda、initializerを含むfixtureを追加する。
2. parse成功後、resolverとは独立したAST visitorで解析対象call kindを全件`CallSiteInventory`へ登録する。
3. lexical site keyはworkspace相対path、start / end line・column、AST call kindで構成し、semantic caller method IDと組み合わせて決定的な内部`CallSiteId`を作る。Protocolへ出力しない。
4. instance initializer / field initializerの1 lexical callを各constructorへ展開する。constructorなしはcanonical default constructor、static initializer / field initializerは`<clinit>()`をsemantic callerにする。
5. inventory entryごとに`emitted`、理由付き`excluded`、code / reason付きprimary `diagnostic`のいずれか1件だけを`CallSiteOutcomeLedger`へcommitする。
6. `excluded`は`external-target`と`lift-excluded-package`だけを許可する。edgeと補助diagnosticが併存するentryはprimary `emitted`とする。
7. identity欠落、ID重複、未登録ID、二重commit、outcome欠落を`JAVA_INTERNAL_ERROR`でfatalにする。
8. Java unit / integration testはentry別snapshotを固定し、production stderrは総数と理由別集計、`silentOmission`だけを出す。
9. testを実行し、resolver自身からinventoryを逆算していないことをdiffレビューする。

### ステップ 3: generator非依存のbytecode-only member救済を実装する

1. context別`ProjectBytecodeMemberIndex`を、source call siteからの照会に対してlazyに使う。
2. 所有source typeがscope内、memberがsource ASTに不在、到達可能な同一contextのproject classesに一意な正規化signatureで存在するmethod / constructor / receiver fieldだけを対象にする。
3. method / constructorはbytecode-only `methodSymbol`とcall edgeを決定的に生成する。
4. bytecode-only symbolの`sourceLocation`は省略し、metadataへ`declarationOrigin: project-bytecode`、`sourceAnchor: owner-type`、`ownerSourceLocation`を入れる。edge metadataへ`calleeOrigin: project-bytecode-member`を入れる。
5. owner位置を構築できない採用済みmemberは`JAVA_INTERNAL_ERROR`にし、call siteやtype位置をmember定義位置として偽装しない。
6. bytecode-only fieldはreceiver type解決だけに使い、field nodeを作らない。
7. bridge method、compiler accessor、`lambda$...`、class initializerをclasses outputから一括node化しない。source callからsource-level signatureへ一意に対応する場合だけ救済する。
8. Lombok getter / setter / builder / constructor / logging fieldとgenerator名なしfixtureが同じ索引経路を通るtestを追加する。
9. testを実行し、annotation名別resolverがないことをdiffレビューする。

### ステップ 4: resolution failure containmentと完全性gateを実装する

1. JavaParser / SymbolSolver / SootUp adapter境界で、要素単位に隔離可能と確認済みのresolution failureだけを専用`ResolutionFailure`へ変換する。
2. `RuntimeException`や`LinkageError`をclass単位で一括diagnostic化しない。allowlist外例外、内部不変条件違反、binary非互換は`JAVA_INTERNAL_ERROR`と非ゼロexitにする。
3. node / edge / DI index / source indexのmutationは要素の必要resolution成功後だけcommitし、失敗要素の途中stateを残さない。
4. 全resolverとbytecode救済後にledgerを検査する。exit 0を許可するprimary outcomeは`emitted`または根拠付き`excluded`だけとする。
5. primary diagnosticが1件でも残れば`JAVA_INCOMPLETE_ANALYSIS`と非ゼロexitにし、v1 partial / strict modeを追加しない。
6. call edge欠落を伴わないdeclaration / DI補助diagnosticだけはexit 0と両立できる。
7. Analyzerの既存fullGraph streamingを維持し、全graph bufferを追加しない。fatal前のrecordはCore側で無効化されるP2_01契約を前提にする。
8. testを実行し、broad catchとscope内callの黙示除外がないことをdiffレビューする。

### ステップ 5: 全未解決callを構造化fatal detailへ集約する

1. primary diagnosticに残った全entryを内部`CallSiteId`決定順で`error.details`へ変換する。件数制限でtruncateしない。
2. top-level metadataへ`total`とcode / reason別`reasonCounts`を同じledgerから生成する。
3. 各detailはworkspace相対`sourceLocation`、元diagnostic `code`、安定reasonの`message`を必須とする。
4. detail metadataは`callKind`と安定`reason`を必須とし、判明時だけself-containedな`target` / `candidates`を決定順で持つ。discarded graphのmethod IDだけを識別子にしない。
5. source本文、絶対path、classpath entry、credential、raw exception、内部`CallSiteId`をdetailへ出さない。
6. details 0件、ledger / total / reasonCounts不一致、self-containedでないcandidateは出力前`JAVA_INTERNAL_ERROR`にする。
7. testで複数reason / candidate、全件、順序、serialized byte数metric、先行diagnostic不要性を固定する。
8. testを実行し、Protocol共通fieldとopaque metadataの境界をdiffレビューする。

### ステップ最終: 最終確認

1. `## 検証コマンド`をすべて実行する。
2. source member、bytecode-only member、明確なexternal target、解決不能scope内targetを同一fixtureで検証する。
3. Core、Protocol schema、Gradle provider、Traversal / Outputを変更していないことを確認する。
4. durable設計との差分が必要なら実装せずspec-lifecycleのtrackへ戻す。
5. 最終diffレビューを行い、指摘を対応する。

## 実装コンテキスト

- spec: `specs/24-gradle-multi-module-source-roots/index.md`
- Java Analyzer正本: `design/features/java-analyzer/DesignDoc_java-analyzer.md`
- Protocol正本: `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md`
- ADR: `adr/0001-analyzer-protocol-jsonl-spi.md`、`adr/0004-defer-runtime-call-tracing.md`、`adr/0005-adopt-sootup-and-spring-di-resolution.md`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照・変更するpath:
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/Main.java`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/AnalysisRunner.java`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/TypeSolverFactory.java`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/attribution/`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/graph/`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/scope/`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/sootup/`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/spring/`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/protocol/`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/JavaErrorCode.java`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/JavaDiagnosticCode.java`
  - 対応する`analyzers/java/src/test/`配下のtest / fixture。
- P3のcontext / origin型を参照し、意味を変更しない。

## 前提条件

- 完了しているべきphase / 依存prompt:
  - `P1_01_analyzer-protocol_multi-root-failure-contract.md`
  - `P3_01_java-analyzer_source-context-preflight.md`
- 完了後に着手可能になる後続prompt: `P5_01_java-analyzer_fixture-compatibility-security.md`。
- 必要なrepo状態: Protocol details、context別solver / origin、parse pre-flightが実装済みであること。

## 不明点ハンドリング

- 矛盾、欠落、未定義を見つけたら作業を止める。
- allowlistに入れるlibrary failureは、adapter操作と要素単位隔離可能性を説明できる場合だけ候補にする。
- source-level signature正規化、initializer caller展開、bytecode memberの一意性が決められない場合は推測しない。
- 質問時は、止まっている作業単位、判断論点、選択肢、call完全性と誤帰属への影響を整理する。

## タスク境界

### 実装する範囲

- source宣言索引とorigin /到達性制約付き再対応付け。
- 独立call-site inventory、initializer展開、outcome ledger。
- generator非依存`ProjectBytecodeMemberIndex`とowner metadata。
- allowlist済みresolution failure containmentと`JAVA_INTERNAL_ERROR`境界。
- `JAVA_INCOMPLETE_ANALYSIS`完全性gateと全件`error.details`。
- Java unit / integration / process test。

### 実装しない範囲

- Protocol schema、Core staging Graph / renderer。
- Gradle Tooling API / provider / version guard。
- root / context / language / parse pre-flightの再設計。
- primary multi-module fixture、cross-version matrix、credential negative E2E。
- Traversalと成功時Output schema。

## 設計仕様

- scope内sourceを帰属の正とし、project classesからsourceへ戻すのはorigin付き・依存到達可能な同一contextだけである。
- inventoryはresolver前の独立AST走査で作る。initializerはlexical siteとsemantic callerの組へ展開し、各entryがprimary outcomeを1件だけ持つ。
- 成功時primary outcomeは`emitted`または`external-target` / `lift-excluded-package`根拠付き`excluded`だけである。
- source ASTにないscope内typeのcallable memberは、到達可能project classesの一意memberをgenerator非依存に救済する。
- bytecode-only memberの`sourceLocation`は省略し、owner type位置はopaque metadataへ分離する。
- allowlist済みresolution failureだけを要素単位に隔離し、未知例外 / LinkageError /不変条件違反はrequest fatalにする。
- primary diagnosticが残るrequestは全件details付き`JAVA_INCOMPLETE_ANALYSIS`でfatalにし、partial / strict modeを設けない。
- detailsは自己完結し、内部ID、source本文、absolute path、classpath、credential、raw例外を含めない。

## テスト観点

- source / project classes併存時のsource帰属とowner context制約。
- external / JDK /非依存module / synthetic context衝突で誤再対応付けしない。
- 全call kindのinventory、決定ID、initializer展開、ledger 1対1。
- 未登録、重複、未分類、二重分類をinternal fatalにする。
- Lombokとgenerator名なしbytecode memberが同じ索引で解決される。
- bytecode-only symbolのlocation省略、owner metadata、receiver field補完、JVM内部member非列挙。
- allowlist failureの要素単位commitとunknown failure / LinkageError fatal。
- success時`silentOmission = 0`、primary diagnostic残存時request fatal。
- details全件・順序・reasonCounts・self-contained性・secret /内部ID非出力。
- fullGraph streamingを維持し、Analyzer全graph bufferを追加しない。

## 検証コマンド

- `cd analyzers/java && ./gradlew test`
- `cd analyzers/java && ./gradlew shadowJar`
- `git diff --check`

## 完了条件

- [ ] ステップ0でbranch、差分、P1 / P3完了を確認した。
- [ ] source宣言索引とorigin /依存到達性制約付き再対応付けを実装した。
- [ ] 独立inventoryとinitializer caller展開、1 entry 1 outcome ledgerを実装した。
- [ ] 未登録 /重複 /未分類 /二重分類をinternal fatalにした。
- [ ] generator非依存のbytecode-only method / constructor / receiver field救済を実装した。
- [ ] bytecode-only memberの定義位置を偽装せずowner metadataへ分離した。
- [ ] broad catchを避け、allowlist failureだけを要素単位に隔離した。
- [ ] primary diagnostic残存を全件details付きrequest fatalにした。
- [ ] partial / strict mode、scope内callの黙示external化、details truncateを追加していない。
- [ ] source本文、absolute path、classpath、credential、raw例外、内部CallSiteIdをdetailsへ出していない。
- [ ] Core、Protocol schema、Gradle provider、Traversal / Outputへ踏み込んでいない。
- [ ] 全作業ステップとdiffレビューを完了した。
- [ ] `## 検証コマンド`がすべてパスした。
- [ ] 未解決の仕様質問が残っていない。
