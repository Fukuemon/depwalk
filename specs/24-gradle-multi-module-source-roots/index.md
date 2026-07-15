# Gradle マルチモジュールの複数 source root を解析する

> 本文書は Issue #24 の spec-lifecycle における作業記録である。
> durable な Protocol、Java Analyzer、テスト契約は sync phase で feature doc / context へハンドオフする。

## メタ情報

- Issue: `#24`
- ステータス: `Draft`
- 作成日: 2026-07-15
- 更新日: 2026-07-15
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
| 5   | 論点解決                    | 未着手     |            | clarify phase で D1〜D9 を確定する                                    |
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
- ADR 起票要否: 現時点では不要。任意 field の追加として Protocol を拡張できない判断になった場合は、clarify phase で再判定する。

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
- 単一 source root の既存 request と fixture の後方互換性を維持する。
- Gradle マルチモジュールの Spring Boot fixture を追加し、module 間の型解決・帰属・DI 解決を検証する。

### やらないこと

- Interface Dispatch、Spring Bean 選択、帰属型決定の意味論自体は変更しない。Issue #21 と Java Analyzer feature doc の既存契約を継承する。
- Gradle Tooling API を用いた build model の自動取得や、Gradle task の自動実行は扱わない。
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
- 単一 source root の既存 request を変更せずに解析できる。
- マルチモジュール fixture で、型解決・帰属・Spring DI 解決の期待集合を自動テストできる。

### 対象ユーザー / 操作主体

- Gradle マルチモジュールの Java / Spring Boot プロジェクトを解析する開発者
- depwalk を CI から実行する開発者
- Analyzer Protocol、Core CLI、Java Analyzer を保守する開発者

EARS 風で振る舞いを記述する。

- WHEN 利用者が 1 つの workspace と複数の source root を指定したとき、システムは全 source root の対象 Java ファイルを 1 つの解析 scope として解析する。
- WHEN ある module の source が別 module の source type を参照するとき、Java Analyzer は対象 type を解決し、既存の帰属型決定規則に従う `methodSymbol` と `callEdge` を出力する。
- WHEN Spring の注入点と Bean 実装が異なる module にあるとき、Java Analyzer は Issue #21 で確定した Bean 選択規則に従って実装候補を出力する。
- IF 複数 source root の入力が省略されたとき、システムは既存の単一 `workspaceRoot` request と同じ対象範囲を解析する。
- THE SYSTEM SHALL `SourceLocation.path` を workspace 全体で一意に解釈できる相対 path として出力する。
- THE SYSTEM SHALL Core に Gradle、JavaParser、JVM 固有の module discovery または型解決ロジックを追加しない。

## 設計時の論点

設計・実装フェーズへ持ち越す残課題を 1 件ずつ管理する。
確定したものは「解決済みの論点」へ移す。

| #   | 論点                                                                                        | 決定候補                                                                                                                                                                   | 決定 |
| --- | ------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- |
| D1  | Protocol で複数 source root をどう表現し、既存 `workspaceRoot` request と互換にするか       | A: optional `sourceRoots` を追加し、省略時は `workspaceRoot` 1 件として扱う / B: Analyzer metadata に格納する / C: `workspaceRoot` を配列へ変更して major version を上げる | 未決 |
| D2  | `sourceRoots` の path 基準と validation をどう定義するか                                    | A: `workspaceRoot` 相対のみ / B: 絶対 path のみ / C: 相対・絶対の両方                                                                                                      | 未決 |
| D3  | Core CLI で source root をどう指定するか                                                    | A: repeatable な言語非依存 flag / B:既存`--analyzer-meta` / C: Analyzer側でbuild systemを自動検出                                                                          | 未決 |
| D4  | `workspaceRoot`、source 列挙、include / exclude、`SourceLocation.path` の責務をどう分けるか | A: `workspaceRoot` を全体の基準に固定し、列挙起点だけを `sourceRoots` にする / B: root ごとに相対基準を持つ                                                                | 未決 |
| D5  | root の重複・包含関係・同一ファイルの重複列挙をどう扱うか                                   | A: 正規化後に root と file を重複排除 / B: 重複・包含 root を request error とする / C: 指定順のまま解析する                                                               | 未決 |
| D6  | module ごとの classes output / dependency classpath をどう渡して型解決するか                | A: 既存の global `metadata.classpath` を全 root で共有 / B: source root と classpath の対応を追加 / C: source の型解決と bytecode 補完で別契約にする                       | 未決 |
| D7  | source root の欠落・読取不能・workspace 外指定を fatal と部分解析のどちらで扱うか           | A: pre-flight fatal / B: diagnostic を出して有効 root の解析を継続 / C: 条件別に分ける                                                                                     | 未決 |
| D8  | 複数 root 追加による解析時間・最大 RSS をどう評価するか                                     | A: 単一 / 複数 fixture の before / after を記録し、SLO は既存方針どおり別途確定 / B: 本 Issue で数値上限を設定                                                             | 未決 |
| D9  | E2E fixture の module 構成と合格条件をどこまで含めるか                                      | A: app / service / repository の3 moduleとmodule間DI / B: 依存方向だけを検証する2 module / C: 実プロジェクト相当の追加構成を含める                                         | 未決 |

## 解決済みの論点

現時点ではなし。

## 未確定事項

設計時の論点 D1〜D9 は未決である。
決定者は Fukuemon、決定期限は clarify phase 完了時とする。
未決のまま Interface / Data / Performance / Error / Test の詳細設計へ進まない。

## 実装対象

正規 target は `context/project.md` の対象ドメイン一覧を正本とする。

| モジュール          | 実装有無 | 主な責務                                                                  |
| ------------------- | :------: | ------------------------------------------------------------------------- |
| `core`              |    ◯     | CLI入力、request組み立て、言語非依存な複数rootの受け渡し                  |
| `traversal`         |    -     | graphに入ったedgeを既存規則で探索する。変更しない                         |
| `output`            |    -     | 既存のgraph出力を継承する。変更しない                                     |
| `analyzer-protocol` |    ◯     | 複数 source root のwire schema、validation、互換性                        |
| `java-analyzer`     |    ◯     | 複数root列挙、TypeSolver登録、scope membership、pre-flight、Java unit/E2E |

責務境界は Core → Analyzer の Protocol 接続を維持する。
Core は path の正規化と共通 request schema だけを扱い、Gradle module や Java の package hierarchy を解釈しない。

## 機能仕様

### User Flow

1. 利用者が解析対象 workspace、複数 source root、Analyzer 起動情報、classpath を指定する。
2. Core が入力を言語非依存な `analysisRequest` に正規化して Analyzer process へ送る。
3. Java Analyzer が全 source root を検証し、対象 Java ファイルを列挙する。
4. Java Analyzer が全 source root と classpath を使って型解決し、graph record と diagnostic を出力する。
5. Core が既存の Graph / Traversal / Output 処理へ結果を渡す。

### Reuse Policy

- Protocol DTOとvalidationは`core/internal/protocol`の既存`AnalysisRequest`を拡張する。
- Java固有のsource root解釈とTypeSolver構築は`analyzers/java/`に閉じる。
- Gradle build modelの共通 abstractionは本Issueで追加しない。

### Performance

- 複数rootでも解析済みファイルのASTを保持し続けない既存方針を継承する。
- root数、解析ファイル数、所要時間、最大RSSの評価方法はD8で決める。

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
- 実jar E2E: Gradleマルチモジュールfixtureの既知caller / callee集合とgraphを照合する。
- 具体的なfixture構成と性能計測はD8 / D9で決める。

## Interface 設計

### UI / API / Event Interface

外部interfaceはCLIとAnalyzer Protocolの`analysisRequest`である。
field名、必須性、既定値、CLI flagはD1〜D4で確定する。

### Props / Request / Response

- Request: `workspaceRoot`、複数source root候補、`include` / `exclude`、`metadata.classpath`。
- Response: 既存の`methodSymbol` / `callEdge` / `diagnostic` / `error`を変更しない。
- `SourceLocation.path`: request全体で一意なworkspace相対pathを維持する方針をD4で確定する。

## Content / Data 設計

### 保存・管理するデータ

永続データは追加しない。
source root、scope file集合、TypeSolver、graph構築用の状態はAnalyzer process内だけに保持する。

### コンテンツ配置 / package / route

- Core DTO / validation / CLI: `core/internal/protocol`、`core/internal/analyze`、`core/internal/cli`
- Java Analyzer: `analyzers/java/`
- Protocol fixture: `testdata/analyzer-protocol/`
- 実jar E2E fixture: `testdata/fixtures/java/`

詳細なclass配置はclarify後の実装分割で確定する。

## Performance / Security 設計

### Performance

既存のAST逐次破棄とmode別streaming方針を継承する。
複数`JavaParserTypeSolver`、root横断index、classpathの構築コストはD8の計測対象とする。

### Security / Privacy

解析対象source、classes directory、依存jarはread-onlyで扱い、外部送信しない。
workspace外pathの許可範囲とsymlink評価はD2 / D7で確定する。

## Error / Fallback 設計

### エラーケース

| #   | ケース                                       | ユーザーへの見せ方                               | リカバリ                              |
| --- | -------------------------------------------- | ------------------------------------------------ | ------------------------------------- |
| E1  | source rootが存在しない                      | `error`または`diagnostic`。D7で確定              | 入力修正または有効rootで継続          |
| E2  | source rootがdirectoryでない、または読めない | `error`または`diagnostic`。D7で確定              | 入力・権限を修正                      |
| E3  | rootが重複または包含関係にある               | validation errorまたは重複排除。D5で確定         | 指定修正または正規化                  |
| E4  | module間typeを解決できない                   | 既存`JAVA_UNRESOLVED_SYMBOL` diagnostic          | classpath / source rootを修正し再実行 |
| E5  | moduleのclasses outputが欠落する             | 既存fatal / SootUp fallback境界をD6 / D7で具体化 | 対象projectをbuildして再実行          |

### Fallback

単一source root requestの既存動作を互換fallbackとして維持する。
複数rootの一部が無効な場合に部分解析を許すかはD7で決定する。

## テスト / 評価方針

### テスト観点

- optional field追加後も既存`analysisRequest` fixtureをparse / validateできる。
- 複数rootのvalidation、path正規化、重複・包含関係をD2 / D5 / D7の決定どおり検証できる。
- 各source rootを`JavaParserTypeSolver`へ登録し、module間のsource typeを解決できる。
- 全rootのscope file集合によってscope内宣言を正しく判定できる。
- moduleをまたぐSpring DI候補とcaller / calleeの期待集合が一致する。
- 既存の単一root unit / E2E testが変更後も通る。

### 計測指標

- 解析root数
- 解析Javaファイル数
- 所要時間
- 最大RSS
- 未解決symbol件数
- 期待caller / callee集合との差分

合否基準はD8 / D9で確定する。

## フロー / シーケンス

diagram phaseで、CLI入力から複数root列挙・型解決・graph出力までのflowchartと、Core / Protocol / Java Analyzerのsequenceを生成する。
未決論点D1〜D9が残っているため、このphaseでは確定図を作成しない。

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

| Phase | 対象                         | 概要                                   | 依存                   |
| ----- | ---------------------------- | -------------------------------------- | ---------------------- |
| P1    | `analyzer-protocol` / `core` | 複数rootのrequest契約とCLI入力         | D1〜D4の確定後に分割   |
| P2    | `java-analyzer`              | 列挙、型解決、scope、pre-flight        | P1、D5〜D7の確定       |
| P3    | `java-analyzer` / `core`     | fixture、contract、実jar E2E、性能計測 | P1 / P2、D8 / D9の確定 |

### prompts 生成方針

- Protocol / CoreとJava Analyzerの責務境界でpromptを分ける。
- wire schema確定後にJava側request modelとTypeSolverを実装する。
- fixtureと実jar E2Eはproduction contractの実装後に行う。
- 詳細な並列可否はtasks phaseで決める。

## 上位資料からの変更点

clarifyで確定したdurableな追加だけをtrack phaseで分類し、sync phaseで上位文書へ反映する。
現時点ではD1〜D9が未決のため、反映内容を確定しない。

### PRD への影響

| 対象節 | 変更内容                                  | 理由             |
| ------ | ----------------------------------------- | ---------------- |
| なし   | 独立PRDなし。Design DocのWhy / Whatを継承 | 統合モードのため |

### Design Doc への影響

| 対象節       | 変更内容                        | 理由                         |
| ------------ | ------------------------------- | ---------------------------- |
| 現時点でなし | landscapeの責務境界を変更しない | 既存P1〜P4の範囲内であるため |

### feature doc への影響

| 対象 doc / 節                     | 変更内容                     | 理由                                       |
| --------------------------------- | ---------------------------- | ------------------------------------------ |
| analyzer-protocol / Java Analyzer | 未確定。D1〜D9の決定後に分類 | 複数source rootのdurable契約を反映するため |

### context への影響

| 対象 doc / 節 | 変更内容                      | 理由                                         |
| ------------- | ----------------------------- | -------------------------------------------- |
| testing.md    | 未確定。D8 / D9の決定後に分類 | contract / E2E観点を更新する可能性があるため |

### ADR の新規 / 更新

| ADR ID       | 変更内容                         | 理由                                 |
| ------------ | -------------------------------- | ------------------------------------ |
| 現時点でなし | D1で非互換変更を選ぶ場合は再判定 | ADR-0001の互換性契約内を優先するため |

## レビュー

`spec-review` (fresh-context evaluator) の最新結果。完全な記録は `review.md` を参照する。

| 日付       | 結果 (PASS / NEEDS_WORK) | 指摘要点                                                                               | 対応                              |
| ---------- | ------------------------ | -------------------------------------------------------------------------------------- | --------------------------------- |
| 2026-07-15 | PASS                     | scaffoldの上位文書整合、未決論点管理、対象境界、必須節、EARSを根拠付きで確認。指摘なし | Phase 2 gate完了。clarify開始待ち |

## 変更履歴

| 日付       | 変更者 | 変更内容                                                                                      |
| ---------- | ------ | --------------------------------------------------------------------------------------------- |
| 2026-07-15 | Codex  | Issue #24からscaffoldを作成し、上位文書整合と未決論点D1〜D9を整理                             |
| 2026-07-15 | Codex  | scaffoldのfresh-context review PASSを記録し、下書き・上位文書突合・論点整理をレビュー済へ更新 |

## 備考

API endpoint、永続データ、認可、画面コンポーネント、UI E2Eは対象外である。
このため、spec appendixは追加しない。
