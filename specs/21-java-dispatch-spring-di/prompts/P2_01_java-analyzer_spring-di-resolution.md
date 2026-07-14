# Spring Bean 定義・DI 解決による dispatch 候補の絞り込み

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- **完了条件のタスク化**: 作業開始前に「完了条件」セクションの各項目を todo として登録し、各ステップ完了時に状態を更新すること。タスク化せずに作業を開始することは禁止

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

P1 と同じ `feature/21` branch / PR を継続利用する。P1 の完了を前提にし、prompt ごとに branch / PR を作り直さない。

1. 現在の branch が `feature/21` であることを確認する
2. P1 の完了条件とテストが満たされていることを確認する
3. 既存 Draft PR の description に本 prompt の完了条件を追記する

### ステップ 1: Spring stereotype / `@Bean` による Bean 候補の収集

1. `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/spring/` パッケージを新設する。
2. Spring stereotype アノテーション (`@Component` / `@Service` / `@Repository` / `@Controller` / `@RestController` 等) と `@Configuration` クラス内の `@Bean` メソッドから Bean 定義を収集するコンポーネントを実装する。
3. アノテーションの判定は **完全修飾名 (FQN) の文字列一致** で行う (`org.springframework.stereotype.Service` 等)。Spring 本体を実行時依存として追加しない (Analyzer は Spring アプリケーションを実行しない、静的検出のみ)。
4. Bean 名を次で導出する: stereotype class は annotation `value` が非空ならその値、未指定なら simple class name に `java.beans.Introspector.decapitalize` と同じ規則を適用する。`@Bean` method は `name` / `value` の全要素を Bean 名・alias として保持し、未指定なら method name を Bean 名とする。
5. Bean class または `@Bean` method に直接付与された `@Qualifier("value")` を qualifier value として保持する。custom qualifier meta-annotation は本 prompt の対象外とする。
6. テストを先に書く (TDD)。stereotype の明示名・既定名、`@Bean` の明示名・alias・既定名を含む fixture を追加する。
7. `cd analyzers/java && ./gradlew test` を実行する。
8. diff レビューを回し、指摘を対応してから次へ。

### ステップ 2: DI 注入解決 (constructor / field / setter)

1. constructor injection、field injection (`@Autowired` フィールド)、setter injection (`@Autowired` setter メソッド) それぞれの注入点を検出し、注入される型と Bean 候補を突合するコンポーネントを実装する。
2. constructor injection は P1 で構築した SootUp 型階層照会 (自プロジェクトのコンパイル済み class 含む、D7) を使い、Lombok 生成コンストラクタも解決対象に含める。P1 の成果物をそのまま呼び出す (再実装しない)。
3. テストを先に書く (TDD)。constructor / field / setter それぞれの fixture を追加する (Lombok 生成コンストラクタでの constructor injection を含む)。
4. `cd analyzers/java && ./gradlew test` を実行する。
5. diff レビューを回し、指摘を対応してから次へ。

### ステップ 3: `@Qualifier` / `@Primary` による Bean 選択と曖昧候補の扱い (D2 の入力生成)

1. 注入型へ代入可能な候補を列挙する。
2. 注入点に直接の `@Qualifier("value")` がある場合は、Bean 側 qualifier value、Bean 名、alias のいずれかが `value` と一致する候補だけを残す。custom qualifier meta-annotation、generics qualifier、`@Resource` は対象外とする。
3. 残った候補が 1 件なら `resolution: unique` とする。候補が複数件なら、`@Primary` がちょうど 1 件の場合だけその候補を `unique` とする。`@Primary` が 0 件または複数件なら全候補を保持して `resolution: ambiguous` とする。候補 0 件は unresolved とする。
4. 条件付き候補を含む場合は、上記で 1 件に絞れても `ambiguous` とする (D3/E4)。
5. 中間結果は候補リスト、`resolution`、候補ごとの provenance、条件種別を持ち、P3 が `callEdge.metadata` へ変換する。本 prompt では Protocol record へ変換しない。
6. テストを先に書く (TDD)。Qualifier の qualifier value / Bean 名 / alias 一致、Qualifier 不一致 0 件、Primary 1 件、Primary 複数件、指定なし複数候補を検証する。
7. `cd analyzers/java && ./gradlew test` を実行する。
8. diff レビューを回し、指摘を対応してから次へ。

### ステップ 4: Spring 条件アノテーションの検出・記録 (D3、評価はしない)

1. `@Profile` / `@ConditionalOnProperty` 等の条件アノテーションが付いた Bean を検出し、「条件付きである」事実と条件種別を中間結果に記録するロジックを実装する。条件の**評価は行わない** (active profile やプロパティ値を読みに行かない)。
2. 条件付き Bean を候補に含める場合、候補が 1 件でも `resolution: unique` とはせず曖昧候補として扱うルールを適用する (R1/E4 整合)。
3. テストを先に書く (TDD)。`@Profile` 付き Bean が 1 件だけのケースで `unique` にならないことを検証する fixture を追加する。
4. `cd analyzers/java && ./gradlew test` を実行する。
5. diff レビューを回し、指摘を対応してから次へ。

### ステップ 5: 実行時生成実装の runtime-provided マーカー判定 (D4/D8)

1. 実装候補が 0 件の interface / 抽象型に対して、既知の runtime-provided マーカーに合致するかを判定するロジックを実装する。
2. 初期マーカー対象は 2 種: (a) Spring Data の `Repository` 型階層 (継承関係で判定)、(b) MyBatis の `@Mapper` アノテーション (D8、`org.apache.ibatis.annotations.Mapper` の FQN 文字列一致で判定)。
3. マーカーに合致する場合は「未解決」ではなく「runtime-provided」として理由を区別する中間結果を生成する。疑似実装ノードは合成しない。
4. `@FeignClient` 等その他フレームワークへの拡張はこの prompt では実装しない (D8 決定のトレードオフとして明示的に対象外)。
5. テストを先に書く (TDD)。Spring Data `Repository` を継承する interface、MyBatis `@Mapper` インターフェース、どちらにも該当しない未解決 interface の 3 パターンの fixture を追加する。
6. `cd analyzers/java && ./gradlew test` を実行する。
7. diff レビューを回し、指摘を対応してから次へ。

### ステップ最終: 最終確認

1. 全テスト / lint / typecheck がパスすることを確認 (`cd analyzers/java && ./gradlew test`)
2. spec の `## 上位資料からの変更点` に必要な追記がないかを phase: track に渡す
3. PR / MR を Ready に変更しレビュアーを指名する

## 実装コンテキスト

- spec: `specs/21-java-dispatch-spring-di/index.md`
- 参照する appendix: なし
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/spring/` (新設)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/sootup/` (P1 成果物、参照のみ)
  - `analyzers/java/src/test/resources/fixtures/` (新規 fixture 追加)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P1_01_java-analyzer_sootup-type-hierarchy.md` (SootUp 型階層照会が完了していること)
- 完了後に着手可能になる後続 prompt: `P3_01_java-analyzer_candidate-merge-fixture-e2e.md`
- 必要な repo 状態: 同じ `feature/21` branch 上で P1 の変更とテストが完了していること

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない (Spring アノテーションの FQN や属性名が不明な場合は、公式ドキュメントで実在を確認してから実装する)
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- Spring stereotype / `@Bean` による Bean 候補の収集
- constructor / field / setter injection の解決
- `@Qualifier` / `@Primary` による Bean 選択、曖昧候補の判定
- Spring 条件アノテーションの検出・記録 (評価はしない)
- runtime-provided マーカー判定 (Spring Data `Repository` + MyBatis `@Mapper`)

### 実装しない範囲

- SootUp の型階層照会そのもの (P1 の成果物を利用するのみ)
- 中間結果を `callEdge` / `diagnostic` の Protocol record へ変換する処理、候補の重複排除 (P3 の責務)
- Spring Boot fixture (E2E 用) の新規作成 (P3 の責務)
- Spring の条件評価 (profile / property の実値判定)、`@FeignClient` 等その他フレームワークへの拡張
- Core / Traversal / Output / Analyzer Protocol の変更

## 設計仕様

spec (`specs/21-java-dispatch-spring-di/index.md`) より抜粋:

> **D2 (解決済み)**: 複数 dispatch 候補は call site ごとの複数 CallEdge とする。実装候補 edge の metadata は `resolution` (`unique` / `ambiguous`)、`provenance` (重複なし・辞書順の `sootup` / `spring-di` 配列)、必要に応じて `conditional` / `conditionTypes` を持つ。本 prompt はこの値へ変換可能な中間結果までを生成し、CallEdge 化は P3 が担う。
>
> **D3 (解決済み)**: Spring の条件評価 (profile / property / `@Conditional`) は一切行わない。条件アノテーションの検出と記録のみ行う。条件付き Bean も無条件に候補として列挙する。「条件付きである」事実と条件種別を metadata / diagnostic に記録し、絞り込みの判断材料には使わない。条件付き Bean が候補に含まれる場合、候補 1 件でも「静的に一意」とは扱わず曖昧候補とする。
>
> **D4 (解決済み、マーカー対象は D8 で拡張)**: Spring Data 等の実行時生成実装は宣言メソッドへの edge のみを保持し、疑似実装ノードは合成しない。既知の runtime-provided マーカーは Spring Data `Repository` 型階層に加え、MyBatis `@Mapper` インターフェース (D8、フレームワークによるランタイムプロキシ生成でソースに実装クラスが存在しない点で Spring Data と同構造) を対象とする。マーカーに合致する場合は diagnostic の理由を「未解決」ではなく「runtime-provided」として区別する。`@FeignClient` 等その他フレームワークへの拡張は引き続き後続とする。
>
> **EARS (要件の解釈より)**:
>
> - WHEN Spring Bean が constructor、field または setter で注入されるとき、Java Analyzer は Bean 定義と選択規則に従って実装候補への call edge を出力する。
> - WHERE `@Qualifier` または `@Primary` で候補が一意になる場合、Java Analyzer は選択された Bean の実装メソッドを解決結果として出力する。
> - IF 複数の実装候補が残る場合、Java Analyzer は解析を失敗させず、候補と曖昧性を diagnostic または metadata に出力する。
>
> **エラーケース**:
>
> - E1 (Bean 候補が0件): 未解決 diagnostic を出力し解析継続。ただし既知の runtime-provided マーカーに合致する場合は理由を区別 (D4/D8)。宣言型の edge を保持。
> - E2 (Bean 候補が複数件で絞り込めない): 候補一覧と曖昧性を出力。
> - E4 (条件付き Bean を静的に確定できない): 条件付きであることと条件種別を記録し、候補を無条件に列挙。候補 1 件でも一意扱いせず曖昧候補とする。

## テスト観点

spec の `### Testing` より抜粋:

- SootUp / Spring 解析固有の観点として、Spring Data `Repository` および MyBatis `@Mapper` 経由呼び出しの runtime-provided マーカー検出テスト (D4/D8) を追加する。
- 条件アノテーション検出・記録 (D3) のテストケースを追加する。
- `@Qualifier` / `@Primary` による一意解決、複数候補が残るケースのテストを追加する。

## 検証コマンド

- ビルド: `cd analyzers/java && ./gradlew shadowJar`
- Unit test: `cd analyzers/java && ./gradlew test`

## 完了条件

- [ ] ステップ 0 で P1 と同じ `feature/21` / Draft PR を継続していることを確認した
- [ ] Spring stereotype / `@Bean` による Bean 候補収集を実装した (FQN 文字列一致、Spring 本体への実行時依存なし)
- [ ] constructor (Lombok 生成含む) / field / setter injection の解決を実装した
- [ ] Bean 名・alias・qualifier value の導出と、`@Qualifier` → `@Primary` の順序、複数 Primary の曖昧判定を実装した
- [ ] Spring 条件アノテーションの検出・記録 (評価はしない) を実装し、条件付き候補が 1 件でも `unique` にならないことを確認した
- [ ] runtime-provided マーカー判定 (Spring Data `Repository` + MyBatis `@Mapper`) を実装した
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った
- [ ] PR / MR を Ready に変更しレビュアーを指名した
- [ ] 未解決の仕様質問が残っていない
