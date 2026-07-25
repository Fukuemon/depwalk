# Java Analyzer 解析本体: AST 解析・型解決・record 生成

## 絶対ルール

- spec に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない。
- 実装は `analyzers/java/` に閉じる。Core (`core/`) の変更は一切行わない。
- P1_02 の成果 (scaffold / request 受領 / pre-flight / JSONL writer) を前提にし、再設計しない。
- protocol の schema を変更・拡張しない。言語固有情報は `metadata` にのみ載せる。
- 後続 feature #21 の範囲 (SootUp による型階層 / Interface Dispatch / Override 候補補完、Spring Bean / DI 解決 — ADR-0005) を実装しない。virtual dispatch の解決は #21 の担当であり、本 prompt は静的な帰属型止まりが仕様。
- Reflection / AspectJ Runtime / 実行時 Proxy の動的解析を実装しない。
- 解析対象ソースは read-only。対象リポジトリを書き換えない。
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

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` に従う。Issue は `#9`。

1. 現在の branch が `feature/9` (または派生の実装 branch) であることを確認する。
2. P1_02 (`analyzers/java/` scaffold / protocol I/O 基盤) が完了していることを確認する。
3. 検証: `cd analyzers/java && ./gradlew test` がパスする状態から始める。

### ステップ 1: TypeSolver 構成と AST 走査を実装する

1. テストを先に書く (TDD): 小さな Java ソース (テスト resource) で型解決が通ることを検証する。
2. `ReflectionTypeSolver` (JDK 標準型) + `JavaParserTypeSolver` (対象プロジェクトの source root) + `JarTypeSolver` (classpath の依存 jar) の 3 TypeSolver を構成する。
3. scope (`include` / `exclude` 適用後) の Java ファイルを列挙し、逐次 parse する。解析済みファイルの AST は逐次破棄する (保持するのは SymbolSolver の型解決キャッシュと record 出力に必要な最小限)。
4. パース不能ファイルは `JAVA_PARSE_ERROR` (`partialFailure`) の `diagnostic` で報告し、他ファイルの解析を継続する。
5. 検証: `cd analyzers/java && ./gradlew test` を実行する。
6. diff レビュー (`spec-review` または repo の標準レビュー手段) を回す。

### ステップ 2: 正規化規則 (signature / methodId) と symbolKind を実装する

1. テストを先に書く: 下記「設計仕様」の D5 表 (overload / erasure / varargs / nested `$` / `<init>` / `<clinit>` / 匿名クラス採番) と D6 表を網羅する。
2. 型表記 = erasure + JVM binary name、`signature` = `<帰属型>#<メソッド名>(<引数型>)`、`methodId` = `java:` + `signature` の生成を実装する。
3. `symbolKind` の割り当て (method / constructor / initializer、初期化子の constructor への畳み込み、lambda の囲みメソッド帰属 + `viaLambda: true`) を実装する。
4. 検証: `cd analyzers/java && ./gradlew test` を実行する。
5. diff レビューを回す。

### ステップ 3: 帰属型の決定規則と callEdge / dispatch 標識を実装する

1. テストを先に書く: 下記「設計仕様」の帰属型 4 分岐 + static / `this` / `super` / `new` の各形 + 除外 package (既定値と `liftExcludePackages` 置き換え、segment 単位 prefix 一致) を網羅する。テスト名で「出力しない」条が「出力する」条に優先することを明示する。
2. 呼び出し式ごとに帰属型を決定し、`methodSymbol` / `callEdge` を出力する。引き上げ node は `metadata` に `declaringType` / `inherited: true` を保持する。
3. `callEdge.metadata.dispatch` (static / virtual / interface / abstract) を標識する。
4. 型解決に失敗した呼び出しは `JAVA_UNRESOLVED_SYMBOL` (`warning`) の `diagnostic` を出し、`callEdge` は出さずに継続する。scope 外呼び出しの省略 (仕様) では `diagnostic` を出さない。
5. 検証: `cd analyzers/java && ./gradlew test` を実行する。
6. diff レビューを回す。

### ステップ 4: analysisMode と node 母集合を実装する

1. テストを先に書く: `fullGraph` (宣言列挙 ∪ call site 由来) / `reachableFromEntrypoints` (callee 方向の推移閉包) / entrypoints 空 = 全体扱い / `JAVA_ENTRYPOINT_NOT_FOUND` を検証する。
2. `fullGraph`: scope 内で宣言された全 method / constructor / static initializer (呼ばれていないものも含む) + 引き上げで生じた call site 由来 node を出力する。
3. `reachableFromEntrypoints`: 母集合のうち entrypoints から callee 方向に推移的に到達するものに限る。entrypoints の selector に一致する method が無ければ `JAVA_ENTRYPOINT_NOT_FOUND` (`warning`)。
4. stderr 計測 (解析ファイル数 / 所要時間 / 未解決件数) を P1_02 の枠に集計値として接続する。
5. 検証: `cd analyzers/java && ./gradlew test`、`./gradlew shadowJar` を実行する。
6. diff レビューを回す。

### ステップ最終: 最終確認

1. `## 検証コマンド` の全コマンドがパスすることを確認する。
2. `core/` に差分がないことを `git status --short` で確認する。
3. spec の `## 上位資料からの変更点` に必要な追記がないかを phase: track に渡す。

## 実装コンテキスト

- spec: `specs/9-java-analyzer/index.md` (D4-D8 / D11 は決定経緯。EARS 受け入れ基準は `## 要件の解釈`)
- 設計の正本: `design/features/java-analyzer/DesignDoc_java-analyzer.md` (型解決 / analysisMode / 正規化規則 / symbolKind / dispatch 標識 / diagnostic / 帰属型の決定規則 / 性能方針)
- protocol 契約の正本: `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md`
- Issue: `#9` / Branch: `feature/9`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 実装対象:
  - `analyzers/java/` (P1_02 の scaffold 上に解析本体を追加)
- 参照しない path:
  - `core/` (一切変更しない)
  - `testdata/fixtures/java/` (E2E fixture は P2_02 の責務。本 prompt の unit test resource は `analyzers/java/` 内に置く)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P1_02_java-analyzer_scaffold-protocol-io.md`
- 完了後に着手可能になる後続 prompt: `P2_02_core_e2e-fixture-baseline.md`
- 必要な repo 状態: `analyzers/java/` の scaffold / request 受領 / pre-flight / JSONL writer が実装済み

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める。
- 推測で実装を進めない。
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する。
- SymbolSolver の API 上、宣言サイト (override 解決後) が本 prompt の規則どおりに取得できないケースが見つかった場合は、該当ケースと候補の扱いを整理して停止する (規則の側を無断で変えない)。

## タスク境界

### 実装する範囲

- JavaParser + SymbolSolver (3 TypeSolver) による AST 解析・型解決。
- 帰属型の決定規則 (宣言サイト優先 / 引き上げ / 除外 prefix / 出力しない) と node 母集合。
- D5 正規化 (`signature` / `methodId`) と D6 `symbolKind` (lambda 帰属 + `viaLambda`)。
- `callEdge.metadata.dispatch` 標識と `metadata.declaringType` / `inherited`。
- `diagnostic` 3 種 (`JAVA_UNRESOLVED_SYMBOL` / `JAVA_PARSE_ERROR` / `JAVA_ENTRYPOINT_NOT_FOUND`)。
- `fullGraph` / `reachableFromEntrypoints` の両 analysisMode。
- stderr 計測値の集計 (P1_02 の枠への接続)。
- 上記の JUnit test (テスト resource は `analyzers/java/` 内)。

### 実装しない範囲

- scaffold / request 受領 / pre-flight / JSONL writer の再設計 → P1_02。
- Core 側の変更 → P1_01。
- E2E fixture (`testdata/fixtures/java/`) / baseline 計測 → P2_02。
- Spring Bean / DI 解決、SootUp / virtual dispatch 解決 (後続 feature #21 — ADR-0005)。
- lambda の独立 node 化、`Stream.forEach` 内部から lambda 本体への辺 (Phase1 の既知の制約。virtual dispatch の解決は後続 feature #21 (SootUp / Interface Dispatch) の担当)。

## 設計仕様

feature doc (正本: `design/features/java-analyzer/DesignDoc_java-analyzer.md`) からの抜粋:

**型解決**: `ReflectionTypeSolver` + `JavaParserTypeSolver` + `JarTypeSolver` の 3 TypeSolver。classpath は pre-flight 済み (P1_02)。

**正規化規則 (D5)**:

| 項目           | 規則                                                                         | 例                                                           |
| -------------- | ---------------------------------------------------------------------------- | ------------------------------------------------------------ |
| 型名           | JVM binary name (nested class は `$` 区切り)                                 | `com.example.Outer$Inner`                                    |
| generics       | erasure で消去 (型引数を保持しない)                                          | `List<String>` → `java.util.List`                            |
| 配列 / varargs | erasure の配列表記に正規化 (varargs は配列)                                  | `String...` → `java.lang.String[]`                           |
| `signature`    | `<帰属型の binary name>#<メソッド名>(<引数型の binary name をカンマ区切り>)` | `com.example.UserService#findById(java.lang.Long)`           |
| constructor    | メソッド名 token は `<init>`                                                 | `com.example.UserService#<init>(com.example.UserRepository)` |
| `methodId`     | `java:` prefix + `signature` (hash しない)                                   | `java:com.example.UserService#findById(java.lang.Long)`      |

匿名クラスのメソッドは、宣言型をソース出現順で採番した binary name (`com.example.Outer$1`) とする (採番はソース内容が同じなら決定的)。

**symbolKind (D6)**: method / constructor / static initializer (`initializer`、signature は `#<clinit>()`) を node 化。インスタンス初期化ブロック / フィールド初期化子は各 constructor に畳み込む。lambda は独立 node にせず、字句的に囲むメソッドに帰属させ、lambda 内の呼び出しの `callEdge.metadata` に `viaLambda: true` を立てる。

**dispatch 標識 (D7)**: `callEdge.metadata.dispatch` = `static` (static メソッド) / `virtual` (具象クラスの instance メソッド) / `interface` (interface 経由) / `abstract` (抽象クラスの抽象メソッド経由)。interface / 抽象メソッド呼び出しは帰属型のメソッドへ辺を張る (未解決 diagnostic に倒さない)。

**帰属型の決定規則**: 「宣言型」= SymbolSolver が override 解決まで済ませた後に返すメソッド宣言の所在型 (本体を持つかどうかは問わない)。

| 条件                                                                                          | 帰属型                                                |
| --------------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| 宣言サイトが scope 内                                                                         | 宣言型 (宣言の所在型)                                 |
| 宣言サイトが scope 外で、レシーバの静的型が scope 内、かつ宣言型が引き上げ除外 package でない | レシーバの静的型へ引き上げる                          |
| 宣言サイトが scope 外で、宣言型が引き上げ除外 package                                         | 出力しない (`diagnostic` も出さない)                  |
| 宣言サイトが scope 外で、レシーバの静的型も scope 外                                          | 出力しない (`methodSymbol` / `callEdge` とも出さない) |

- 引き上げ除外 package: 既定 `java` / `javax` / `jakarta`。`liftExcludePackages` (metadata) 指定時は既定値を**置き換える**。判定は宣言型の binary name に対する `.` 区切り segment 単位の prefix 一致 (`java` は `java.lang` に一致、`javafx` に不一致)。
- static メソッド: 「レシーバ」を参照した型とみなして同一規則を適用。`this.foo()` / `super.foo()`: 宣言サイトが scope 内なら宣言型に帰属。`new Foo()`: 引き上げなし。`Foo` が scope 内なら `#<init>(...)`、scope 外なら出力しない。
- 引き上げ node は継承元を `methodSymbol.metadata` に保持する (例: `declaringType: "org.springframework.data.repository.CrudRepository"`, `inherited: true`)。
- 出力する `callEdge` の caller / callee はいずれも出力済み `methodSymbol` を参照する (protocol 契約)。

**node 母集合**: `fullGraph` = ① 宣言列挙 (scope 内で宣言された method / constructor / static initializer のすべて。呼ばれていないものも含む) ∪ ② call site 由来 (引き上げで生じた node。実際に呼び出された箇所からのみ生成)。`reachableFromEntrypoints` = 母集合のうち entrypoints から callee 方向に推移的に到達するもの。entrypoints 未指定 / 空配列は analysisMode によらず scope 全体の要求。

**diagnostic (解析継続)**:

| code                        | severity         | 出る場面                                                          |
| --------------------------- | ---------------- | ----------------------------------------------------------------- |
| `JAVA_UNRESOLVED_SYMBOL`    | `warning`        | 呼び出し先の型が解決できず `callEdge` を張れない                  |
| `JAVA_PARSE_ERROR`          | `partialFailure` | ファイル単位で構文解析に失敗し、そのファイルを飛ばした            |
| `JAVA_ENTRYPOINT_NOT_FOUND` | `warning`        | `entrypoints` の method selector に一致する method が見つからない |

`diagnostic.sourceLocation` と `relatedMethodId` を可能な範囲で埋める。

**受け入れ基準 (spec `## 要件の解釈` の EARS 風記述から抜粋)**:

- WHEN 呼び出し先の型が解決できたとき、システムは `methodSymbol` (caller / callee 双方) と、両者を参照する `callEdge` を出力する。
- WHERE 呼び出し先が interface / 抽象メソッドであるとき、システムは帰属型のメソッドを callee として `callEdge` を出力し、`metadata.dispatch` に dispatch 種別を標識する。
- IF 宣言サイトが scope 外で宣言型が引き上げ除外 package に属するとき、システムは `methodSymbol` / `callEdge` を出力しない (`diagnostic` も出さない)。
- IF 宣言サイトもレシーバの静的型も scope 外であるとき、システムは出力しない (同上)。
- IF 呼び出し先の型が解決できないとき、システムは `callEdge` を出力せず `diagnostic` として報告し、解析を継続する。
- IF 個別ファイルがパース不能なとき、システムは該当ファイルを `diagnostic` で報告し、他ファイルの解析を継続する。

## テスト観点

spec「テスト / 評価方針 — Java unit test」からの抜粋:

- D5: signature / `methodId` の正規化 — overload / generics erasure / varargs / nested class (`$`) / constructor (`<init>`) / static initializer (`<clinit>`) / 匿名クラス採番の決定性。
- D6: `symbolKind` の割り当て — 初期化子の constructor への畳み込み、lambda 内呼び出しの囲みメソッド帰属と `viaLambda: true`。
- D7 / D11: 帰属型の決定規則 — 宣言サイト scope 内 (override あり / なし)、scope 外宣言の引き上げ、除外 package (既定値と `liftExcludePackages` による置き換え、segment 単位 prefix 一致)、`this` / `super` / static / `new` の各形、`metadata.dispatch` の値。
- D8: `diagnostic` の code と severity の対応。
- D4: `fullGraph` / `reachableFromEntrypoints` の出力範囲 (宣言列挙 ∪ call site 由来、entrypoints 空は全体扱い)。
- EARS の優先順位: 「出力する」条 (WHEN / WHERE) に対し「出力しない」条 (IF) が例外として優先されることをテスト名で明示する。

## 検証コマンド

- `cd analyzers/java && ./gradlew test`
- `cd analyzers/java && ./gradlew shadowJar`
- `git diff --check`
- (Core 非変更の確認) `git status --short` で `core/` 配下に差分がないこと

## 完了条件

- [ ] ステップ 0 で branch / P1_02 完了状態を確認した。
- [ ] 3 TypeSolver 構成で型解決が動き、AST が逐次破棄される。
- [ ] D5 正規化 (erasure + binary name / `<init>` / `<clinit>` / 匿名クラス採番 / `java:` prefix) がテストで網羅されている。
- [ ] D6 symbolKind (初期化子の畳み込み / lambda の囲みメソッド帰属 + `viaLambda`) が実装されている。
- [ ] 帰属型の決定規則 4 分岐 + static / `this` / `super` / `new` + 除外 prefix (既定値 / 置き換え / segment 一致) がテストで網羅されている。
- [ ] `callEdge.metadata.dispatch` と `metadata.declaringType` / `inherited` が出力される。
- [ ] `diagnostic` 3 種が仕様どおりの code / severity で出て、解析が継続する。
- [ ] `fullGraph` / `reachableFromEntrypoints` の両モードが実装され、node 母集合が仕様どおり。
- [ ] 出力する `callEdge` の caller / callee がいずれも出力済み `methodSymbol` を参照する。
- [ ] stderr 計測 (解析ファイル数 / 所要時間 / 未解決件数) が集計値を出力する。
- [ ] `core/` に差分がない。
- [ ] `## 検証コマンド` がすべてパスする。
- [ ] 各ステップで diff レビューを実施し、指摘を対応した。
- [ ] 未解決の仕様質問が残っていない。
