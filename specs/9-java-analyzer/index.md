# Java Analyzer feature spec

> issue #9 の spec。Java/Spring ソースの AST 解析・型解決・CallGraph 生成を担う言語別 Analyzer の設計と実装分割を管理する。
> 共通契約 (SPI / JSONL Protocol / Model schema) の正本は [Analyzer Protocol / SPI feature doc](../../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md) と [ADR-0001](../../adr/0001-analyzer-protocol-jsonl-spi.md)。本 spec は契約を変更せず、Java 側の実装方式を決める。
> durable な設計成果は phase: sync で `design/features/java-analyzer/DesignDoc_java-analyzer.md` へ正本ハンドオフする。

## メタ情報

- Issue: `#9`
- ステータス: `In Progress`
- 作成日: 2026-07-11
- 更新日: 2026-07-11
- Branch: `feature/9`
- Owner: Fukuemon

## 設計フェーズ状況

状態は `未着手 / 進行中 / 完了 / レビュー済 / 保留` のいずれか。保留の場合は理由を備考に残す。

| #   | フェーズ                    | 状態       | 最終更新   | 備考                                                                                                                              |
| --- | --------------------------- | ---------- | ---------- | --------------------------------------------------------------------------------------------------------------------------------- |
| 1   | 起票                        | 完了       | 2026-07-11 | GitHub issue #9 / requirements.md を確認済み                                                                                      |
| 2   | 下書き                      | レビュー済 | 2026-07-11 | scaffold 完了。spec-review PASS (4 回目)                                                                                          |
| 3   | 上位文書突合                | 完了       | 2026-07-11 | S5 / P4 の測定方法に齟齬を検出し Design Doc への変更提案として登録 (phase: sync で反映)。feature doc / context / ADR とは矛盾なし |
| 4   | 論点整理                    | 進行中     | 2026-07-11 | D1-D10 を初期論点として列挙                                                                                                       |
| 5   | 論点解決                    | 未着手     |            |                                                                                                                                   |
| 6   | Interface / Routing 設計    | 未着手     |            |                                                                                                                                   |
| 7   | Content / Data 設計         | 未着手     |            |                                                                                                                                   |
| 8   | Performance / Security 設計 | 未着手     |            |                                                                                                                                   |
| 9   | Test / Metrics 設計         | 未着手     |            |                                                                                                                                   |
| 10  | 実装分割                    | 未着手     |            |                                                                                                                                   |
| 11  | レビュー済                  | 未着手     |            |                                                                                                                                   |

## 上位文書整合

正本 ([PRD](../../PRD.md) / [Design Doc](../../design/DesignDoc.md) / [feature doc](../../design/features/) / [context](../../context/) / ADR) のどの節と、どう整合させたかを記録する。

- PRD 更新要否: 不要 (本プロダクトは統合モード。Why / What は Design Doc に統合)
- Design Doc 更新要否: 要 (① 「詳細の所在 → Feature 設計」の Java Analyzer 行を feature doc へリンクする ② 成功条件 S5 / 設計原則 P4 の測定方法を「2 つ目以降の Analyzer 追加時に Core 無変更」と明確化する。いずれも phase: sync で実施)
- ADR 起票要否: phase: clarify で `要 / 不要` に確定する (判断条件: D1 (build tool / JDK) または D2 (Analyzer 起動コマンドの解決方式) が Core の toolchain / 実行境界に長期影響を与える場合は起票する)

| 上位文書                       | 節 / 該当箇所                                                 | 整合方針 (継承 / 補足 / 変更提案)                                                                                                                                   |
| ------------------------------ | ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| PRD                            | 統合モードのため非該当                                        | 継承                                                                                                                                                                |
| Design Doc                     | モジュール責務 (Java Analyzer) / 設計原則 P2・P3 / S4         | 継承 (Analyzer は独立プロセス、Protocol のみで Core と結合)                                                                                                         |
| Design Doc                     | 成功条件 S5 / 設計原則 P4                                     | 変更提案 (測定方法を「**2 つ目以降**の言語 Analyzer 追加時に Core へ差分が出ないこと」と明確化する。初号機導入時の言語非依存な初回配線は対象外。phase: sync で反映) |
| Design Doc                     | Future Work Phase1〜3 / Open Questions Q2                     | 補足 (Phase1 の範囲を確定し、Phase2/3 の段階導入境界を宣言する)                                                                                                     |
| Design Doc                     | 詳細の所在 → Feature 設計 (Java Analyzer = 未作成)            | 変更提案 (phase: sync で feature doc を作成しリンクする)                                                                                                            |
| feature doc: analyzer-protocol | Model schema / process contract / versioning                  | 継承 (契約は変更しない。Java 側は準拠側として実装する)                                                                                                              |
| context: architecture          | Package Boundary (`analyzers/<language>/`) / Runtime Boundary | 継承 (Java 実装は `analyzers/java/` に置き、Core の internal に入れない)                                                                                            |
| context: toolchain             | 標準スタック (Java Analyzer 行) / Scaffold Policy             | 補足 (JavaParser / SymbolSolver / SootUp は確定済。build tool と JDK version を本 spec で確定する)                                                                  |
| context: testing               | Protocol contract test / テスト責務の分担                     | 補足 (Java Analyzer 側の contract test 実行方式を確定する)                                                                                                          |
| context: engineering           | quality gate / Analyzer build を束ねる wrapper の要否         | 補足 (D1 / D10 の決定に伴い、Java build を quality gate に含めるかを確定する)                                                                                       |
| ADR-0001                       | JSONL over STDIN/STDOUT / process SPI                         | 継承                                                                                                                                                                |
| ADR-0002                       | Core 実装基盤 (Go)                                            | 継承 (Core に JVM 依存を持ち込まない)                                                                                                                               |

> 矛盾を検出した場合は phase: sync で PRD / Design Doc / feature doc / context / ADR への back-propagation を提案する。

## 関連資料

- `design/DesignDoc.md`: モジュール責務 (Java Analyzer)、設計原則 P1〜P4、Communication Protocol、Future Work Phase1〜3、Open Questions Q2
- `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md`: Model schema / process contract / versioning (本 spec が準拠する契約の正本)
- `adr/0001-analyzer-protocol-jsonl-spi.md`: JSONL process SPI の判断
- `adr/0002-core-implementation-foundation.md`: Core 実装基盤 (Go)
- `context/architecture.md` / `context/toolchain.md` / `context/testing.md`
- `specs/9-java-analyzer/requirements.md`: 要求定義 (本 issue の起点)
- `specs/8-analyzer-protocol/`: 契約決定の経緯 (issue 単位の作業記録)
- `specs/12-analyzer-protocol-implementation/`: Core 側 Protocol / Analyzer process 実装の作業記録
- 関連 issue: #8 (Protocol 設計, closed) / #12 (Protocol 実装, closed) / #7 (出力形式, open)

## 背景

Phase1 の対象は Java/Spring Boot であり、Java Analyzer は `analyzer-protocol` の SPI / JSONL スキーマを実装する **最初の言語別 Analyzer** である。Core 側は #12 で Protocol parser / validator (`core/internal/protocol`) と Analyzer process 起動 (`core/internal/analyzer`) を実装済みで、契約の受け側は揃っている。本 spec は、その契約に対して JSONL を出力する Java 側の実装方式を確定する。

本 spec が関わる成功条件は Design Doc の S1 / S2 (caller / callee 探索の網羅性 — graph の入力を供給する)、S4 (Spring DI 経由の呼び出し先解決、Phase2 以降)、S5 (Analyzer 追加時に Core を変更しない) である。Phase1 では JavaParser ベースの静的呼び出し抽出を達成し、DI 解決 (Phase2) と Interface Dispatch / Override 解決 (Phase3, SootUp) は段階導入とする。

解析ロジックの影響範囲は `analyzers/java/` に閉じる。ただし Core 側には「どの Analyzer をどう起動するか」を決める配線 (analyze command / Analyzer 起動コマンド解決) がまだ無く (`core/internal/cli/root.go` に analyze command は無く、`core/internal/analyzer/runner.go` は起動コマンドを呼び出し側から受け取るだけ)、Java Analyzer を初号機として Core から実行するにはこの初回配線が必要になる。S5 が求めるのは **2 つ目以降**の言語 Analyzer 追加時に Core へ差分を出さないことなので、初回配線は S5 に反しない。本 spec はこの配線を「言語非依存」に作ることで S5 を担保する。

## スコープ

### やること

- Java ソースの AST 解析 (JavaParser) と型解決 (SymbolSolver) による静的呼び出し抽出
- 抽出結果を `analyzer-protocol` の JSONL スキーマ (`methodSymbol` / `callEdge` / `diagnostic` / `error`) で stdout へ出力
- `analysisRequest` の受領 (stdin) と process contract (exit code / stderr) の遵守
- Java Analyzer の build / 配布形態と、Core からの起動方法の確定
- Core 側の初回配線の実装: `depwalk analyze` から Analyzer 起動コマンドを解決し、Protocol 経由で graph を受け取るまで (言語非依存に作り、2 つ目以降の Analyzer 追加で差分が出ない構造にする)
- 未解決 symbol / 部分解析の `diagnostic` 表現
- Phase2 (Spring Bean / DI 解決) / Phase3 (Interface Dispatch / Override 解決, SootUp) の段階導入境界の宣言
- Phase1 実装のタスク分割と実装 prompts の生成

### やらないこと

- 共通契約 (SPI / Protocol / Model schema) の定義・変更 (→ `analyzer-protocol` feature doc が正本)
- グラフ探索 (→ `traversal`)、出力整形 (→ `output`)
- Phase2 / Phase3 の実装 (本 spec では段階導入の境界宣言のみ)
- SootUp 統合範囲 (Q2) の決定 (DesignDoc Open Question として Phase3 着手前まで保留)
- Reflection / AspectJ Runtime / 実行時 Proxy の動的解析 (Design Doc Non Goals)
- CLI 引数の完全仕様の確定 (出力形式指定 / 探索方向 / 深さ上限などの全 flag 体系 → 後続の CLI interface spec)。本 spec は Analyzer を起動して graph を得るために必要な最小 flag のみ扱い、後から拡張できる形に留める

## 要件の解釈

### 実現したいユーザー価値

- Java/Spring プロジェクトのメソッド呼び出し関係を、実行せずに (静的解析だけで) 網羅的に抽出できる。
- 型解決できない呼び出しが残っても解析全体は止まらず、どこが未解決かを利用者が観測できる。

### 成功条件

- Phase1: サンプル Java/Spring プロジェクトに対し、JavaParser ベースで抽出した `methodSymbol` / `callEdge` が Protocol contract test を通過する。
- S5: Core 側の配線を言語非依存に保ち、**2 つ目以降**の言語 Analyzer (Kotlin / TypeScript 等) を追加するとき Core に差分が出ない構造にする。Java 導入に伴う初回配線 (`depwalk analyze` / Analyzer 起動コマンド解決) は S5 の対象外であり、Java 固有の分岐を Core に入れないことで担保する。
- S4 (Phase2 以降): interface 注入を含むサンプルで、実装クラスのメソッドが呼び出し先として現れる。

### 対象ユーザー / 操作主体

- 直接の呼び出し元は Core (depwalk CLI)。Java Analyzer は独立プロセスとして起動され、人間が直接叩くことは想定しない (debug 用途を除く)。

EARS 風で振る舞いを記述する。

- WHEN Core が Analyzer process を起動し stdin へ `analysisRequest` を 1 件送信して close したとき、システムは対象 Java ソースを read-only で解析し、結果を stdout へ JSONL で逐次出力する。
- WHEN 呼び出し先の型が解決できたとき、システムは `methodSymbol` (caller / callee 双方) と、両者を参照する `callEdge` を出力する。
- IF 呼び出し先の型が解決できないとき、システムは `callEdge` を出力せず `diagnostic` (`severity: warning` または `partialFailure`) として未解決を報告し、解析を継続する。
- IF 個別ファイルがパース不能なとき、システムは該当ファイルを `diagnostic` で報告し、他ファイルの解析を継続する (部分解析を許容する)。
- IF 解析を継続できない致命的な問題が起きたとき、システムは `error` record を出力し、非ゼロ exit code で終了する。
- THE SYSTEM SHALL すべての出力 record に `schemaVersion` (Phase1 は `"1"`) と `recordType` を含める。
- THE SYSTEM SHALL 解析対象リポジトリを書き換えず、外部への送信を行わない。

## 設計時の論点

設計 / 実装フェーズへ持ち越す残課題を 1 件ずつ管理する。確定したものは「解決済みの論点」へ移す。

| #   | 論点                                                                                                         | 決定候補                                                                                                                                                                                                                                           | 決定 |
| --- | ------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- |
| D1  | Java Analyzer の build tool / JDK version / 配布形態                                                         | A) Gradle + shadow (fat) jar / B) Maven + shade plugin。JDK は 17 LTS / 21 LTS                                                                                                                                                                     | 未決 |
| D2  | Core が Analyzer をどう発見・起動するか (実行コマンドの解決)                                                 | A) CLI flag で jar path 指定 / B) 環境変数 / C) `java -jar` 固定 + 既定 path / D) Core に同梱                                                                                                                                                      | 未決 |
| D3  | SymbolSolver の型解決範囲 (source root のみか、依存 jar を classpath に含めるか)                             | A) source root のみ (JDK 標準型は解決) / B) 依存 jar を classpath 指定で受け取る / C) build tool から自動解決                                                                                                                                      | 未決 |
| D4  | Phase1 で対応する `analysisMode` の範囲                                                                      | A) `fullGraph` のみ / B) `fullGraph` + `reachableFromEntrypoints` 両方                                                                                                                                                                             | 未決 |
| D5  | `methodId` / `signature` の正規化規則 (generics / varargs / inner class / lambda / 匿名クラス)               | 完全修飾名 + erasure ベースの parameter type list。lambda / 匿名クラスの命名規則を決める                                                                                                                                                           | 未決 |
| D6  | Phase1 で node 化する `symbolKind` の範囲                                                                    | A) `method` / `constructor` のみ / B) `initializer` も含める / C) lambda も含める (※ protocol の `symbolKind` enum は `method` / `constructor` / `function` / `initializer` のみ。lambda を独立 node にするなら契約変更 = major bump の判断が要る) | 未決 |
| D7  | interface / 抽象メソッド呼び出しの Phase1 での扱い (宣言型のメソッドを callee にするか、diagnostic にするか) | A) 宣言型のメソッドを callee として edge を張る / B) 未解決 `diagnostic` にする / C) 両方 (metadata で dispatch 種別を標識)                                                                                                                        | 未決 |
| D8  | `diagnostic.code` 体系と、未解決/部分解析の分類粒度                                                          | prefix 付き code (例: `JAVA_UNRESOLVED_SYMBOL`) と severity の対応表を定める                                                                                                                                                                       | 未決 |
| D9  | 大規模プロジェクトの性能 / メモリ方針 (streaming 出力、目標値)                                               | record を逐次 flush する streaming 出力。目標値 (対象規模 / 実行時間) を定める                                                                                                                                                                     | 未決 |
| D10 | テスト戦略 (Java 側 fixture プロジェクト、contract test の実行方式、CI に JVM を要求するか)                  | A) Java 側 unit test + Go 側 contract test を fixture jar 経由で結合 / B) Java 側で contract test を完結                                                                                                                                           | 未決 |

## 解決済みの論点

(phase: clarify で確定したものをここに移動する)

-

## 未確定事項

(決定できない項目を理由とともに残す。1 件でも残っていれば下流 phase は止める)

- **Q2 (SootUp 統合範囲)**: DesignDoc Open Questions Q2。決定者 Fukuemon / 期限 Phase3 着手前。本 spec では Phase3 のスコープ宣言のみを行い、統合範囲は決定しない。本 spec の下流 phase を止める対象には含めない (Phase1 実装に影響しないため)。

## 実装対象

正規 target は `context/project.md` の対象ドメイン一覧を正本とする。

| モジュール          | 実装有無 | 主な責務                                                                                                                                                                                                                                         |
| ------------------- | :------: | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `java-analyzer`     |    ◯     | Java/Spring の AST 解析・型解決・CallGraph 生成・JSONL 出力 (`analyzers/java/`)                                                                                                                                                                  |
| `analyzer-protocol` |    -     | 契約は #8 / #12 で確定済み。本 spec は準拠側。契約変更が必要になったら phase: sync で提案                                                                                                                                                        |
| `core`              |    ◯     | 初回配線のみ: `depwalk analyze` command と Analyzer 起動コマンド解決 (D2) を実装し、既存の `core/internal/analyzer` / `core/internal/protocol` (#12 実装済み) と結合する。Java 固有の分岐は入れない。CLI 引数の完全仕様は後続 CLI interface spec |
| `traversal`         |    -     | 対象外 (#6 で実装済み)                                                                                                                                                                                                                           |
| `output`            |    -     | 対象外 (#7)                                                                                                                                                                                                                                      |

## 機能仕様

### User Flow

(phase: clarify / diagram で確定する)

### Reuse Policy

- Java Analyzer の実装は `analyzers/java/` に閉じる。Core (`core/`) と Go package / Java code を共有しない ([context/architecture.md](../../context/architecture.md))。
- Core と Analyzer の共有境界は Protocol doc / ADR / JSONL fixture / contract test 観点に限定する。

### Performance

- (D9 で確定する)

### Routing / URL State

- 非該当 (CLI ツール / 独立プロセス)。

### Content / Assets

- 非該当。

### UI Reuse

- 非該当 (Design Doc Non Goals: IDE Plugin / Web UI を提供しない)。

### Testing

- (D10 で確定する。横断規約は [context/testing.md](../../context/testing.md))

## Interface 設計

### UI / API / Event Interface

(phase 6 で確定する)

### Props / Request / Response

(phase 6 で確定する)

## Content / Data 設計

### 保存・管理するデータ

(phase 7 で確定する)

### コンテンツ配置 / package / route

(phase 7 で確定する)

## Performance / Security 設計

### Performance

(phase 8 で確定する)

### Security / Privacy

- 解析対象ソースは read-only。外部送信を行わない ([context/architecture.md](../../context/architecture.md) State Boundary)。

## Error / Fallback 設計

### エラーケース

| #   | ケース                          | ユーザーへの見せ方              | リカバリ                         |
| --- | ------------------------------- | ------------------------------- | -------------------------------- |
| 1   | 型解決できないシンボル          | `diagnostic` で未解決を報告     | 解析継続 (部分結果を返す)        |
| 2   | DI 経由で実体が一意に定まらない | (Phase2 以降で確定)             | Phase2/3 で解決度を上げる        |
| 3   | パース不能なソースファイル      | `diagnostic` で該当ファイル報告 | 他ファイルの解析を継続           |
| 4   | 解析継続不能な致命的エラー      | `error` record + 非ゼロ exit    | Core が fatal failure として扱う |

### Fallback

(phase 8 で確定する)

## テスト / 評価方針

### テスト観点

(phase 9 で確定する。[context/testing.md](../../context/testing.md) の Protocol contract test を正本として継承する)

### 計測指標

(phase 9 で確定する)

## フロー / シーケンス

(phase: diagram で生成する)

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

(phase 10 で確定する)

| Phase | 対象 | 概要 | 依存 |
| ----- | ---- | ---- | ---- |
| P1    |      |      |      |

### prompts 生成方針

(phase 10 で確定する)

## 上位資料からの変更点

本 spec で PRD / Design Doc / feature doc / context / 既存 ADR から変更・追加した内容を、反映先別に記録する。phase: track / phase: sync で更新する。

### PRD への影響

| 対象節 | 変更内容               | 理由                            |
| ------ | ---------------------- | ------------------------------- |
| (なし) | 統合モードのため非該当 | Why / What は Design Doc に統合 |

### Design Doc への影響

| 対象節                    | 変更内容                                                                                                                                                                         | 理由                                                                                                                                                  |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| 詳細の所在 → Feature 設計 | Java Analyzer 行を feature doc へリンクし、状態を `未着手` → `完了` に更新する (phase: sync で反映)                                                                              | phase: sync で feature doc を作成するため                                                                                                             |
| 成功条件 S5 / 設計原則 P4 | 測定方法を「**2 つ目以降**の言語 Analyzer を追加するとき Core モジュールに差分が発生しないこと」と明確化する。初号機 (Java) 導入時の言語非依存な初回配線は S5 の対象外と明記する | Core に `depwalk analyze` / Analyzer 起動コマンド解決が未実装のため、初号機導入時のみ言語非依存の配線が必要。現行の文言では初回配線が S5 違反に読める |

### feature doc への影響

| 対象 doc / 節                                              | 変更内容               | 理由                     |
| ---------------------------------------------------------- | ---------------------- | ------------------------ |
| `design/features/java-analyzer/DesignDoc_java-analyzer.md` | 新規作成 (phase: sync) | durable な設計成果の正本 |

### context への影響

| 対象 doc / 節                       | 変更内容                                                                                                                                                                                                       | 理由                                                                                                                                                        |
| ----------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `context/project.md` Quick Commands | Java Analyzer の build / test コマンドを追加 (D1 / D10 の決定後)。「開発起動」「E2E」行 (現在「後続の CLI interface spec で確定」) に、本 spec が実装する最小 `depwalk analyze` の起動例を暫定値として記入する | 現状 Go (`cd core && ...`) のみで Java 側のコマンド契約を持たない。最小 analyze を実装するため起動手段が確定する (全 flag 体系は CLI interface spec が正本) |
| `context/toolchain.md` 標準スタック | build tool / JDK version を確定値に更新 (D1)                                                                                                                                                                   | 現在「JavaParser / SymbolSolver / SootUp を利用」までしか固定されていない                                                                                   |
| `context/engineering.md`            | Analyzer build を束ねる wrapper (make-like) 導入要否の判断を反映                                                                                                                                               | 「Analyzer build を束ねる必要が出た時点で検討」と保留されている                                                                                             |
| `context/testing.md`                | Java Analyzer 側の contract test 実行方式 / CI の JVM 要求を追記 (D10)。S5 の再掲箇所 (「新 Analyzer 追加時は Protocol contract test の通過を必須」) が Design Doc 側の明確化に追随しているか確認する          | Protocol contract test の実行主体が Go 側のみを前提にしている                                                                                               |
| `context/architecture.md`           | Package Boundary の S5 再掲 (「Analyzer 追加で Core に差分が出ないこと (S5)」) を Design Doc 側の明確化に追随させる                                                                                            | S5 の測定方法を変更提案するため、再掲箇所に drift が残らないようにする                                                                                      |

### ADR の新規 / 更新

| ADR ID                  | 変更内容 | 理由 |
| ----------------------- | -------- | ---- |
| (phase: clarify で判断) |          |      |

## レビュー

`spec-review` (fresh-context evaluator) の最新結果。完全な記録は `review.md` を参照。

| 日付       | 結果 (PASS / NEEDS_WORK) | 指摘要点                                                                                  | 対応                                                                                                                                    |
| ---------- | ------------------------ | ----------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-07-11 | NEEDS_WORK               | `core` の責務境界が矛盾 (D2 で起動方法を決めるのに実装対象は `-` / S5 解釈が上位より強い) | `core` を実装対象 ◯ (初回配線のみ) に変更、S5 の表現を DesignDoc 定義に合わせた。advisory (D6 の protocol 影響 / context 波及) も反映   |
| 2026-07-11 | NEEDS_WORK (2 回目)      | S5 の再定義が「継承」扱いのままで Design Doc へ back-propagation 登録されていない         | 上位文書整合表の Design Doc 行を分割し S5 / P4 を `変更提案` として登録。「Design Doc への影響」表に明確化行を追加 (phase: sync で反映) |
| 2026-07-11 | NEEDS_WORK (3 回目)      | メタ情報の追随漏れ (phase 3 備考 / phase 2 状態 / review 記録)。設計内容の変更は不要      | phase 3 備考を齟齬検出に更新、phase 2 を `進行中` に戻し、`review.md` と本表に 2・3 回目の記録を追記                                    |
| 2026-07-11 | **PASS** (4 回目)        | 全観点 PASS (prompts / 正本境界は N/A)。phase: scaffold の gate 通過                      | phase: clarify (D1-D10 の決定) へ進む                                                                                                   |

## 変更履歴

| 日付       | 変更者   | 変更内容                                                                                                                      |
| ---------- | -------- | ----------------------------------------------------------------------------------------------------------------------------- |
| 2026-07-11 | Fukuemon | phase: scaffold — index.md を作成、D1-D10 を初期論点として列挙                                                                |
| 2026-07-11 | Fukuemon | spec-review NEEDS_WORK 対応 — `core` を実装対象 ◯ (初回配線のみ) に、S5 の表現を修正、context への影響表と D6 の注記を追加    |
| 2026-07-11 | Fukuemon | spec-review 再指摘対応 — S5 / P4 の明確化を Design Doc への変更提案として登録 (整合表・影響表)、context: engineering 行を追加 |
| 2026-07-11 | Fukuemon | spec-review 3 回目対応 — phase 3 備考 / phase 2 状態 / レビュー記録のメタ情報を実態に同期                                     |

## 備考

- appendix (api / database / authorization / screen-spec / testid) は本 spec のスコープに該当しないため取り込まない。Java Analyzer は CLI 配下の独立プロセスであり、HTTP API / 永続層 / 画面 / ロールを持たない。
