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

| #   | フェーズ                    | 状態       | 最終更新   | 備考                                                                                                                                            |
| --- | --------------------------- | ---------- | ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | 起票                        | 完了       | 2026-07-11 | GitHub issue #9 / requirements.md を確認済み                                                                                                    |
| 2   | 下書き                      | レビュー済 | 2026-07-11 | scaffold 完了。spec-review PASS (4 回目)                                                                                                        |
| 3   | 上位文書突合                | 完了       | 2026-07-11 | S5 / P4 の測定方法に齟齬を検出し Design Doc への変更提案として登録 (phase: sync で反映)。feature doc / context / ADR とは矛盾なし               |
| 4   | 論点整理                    | 完了       | 2026-07-11 | D1-D10 を初期論点として列挙。D11 は clarify 中に spec-review が検出し追加起票                                                                   |
| 5   | 論点解決                    | レビュー済 | 2026-07-11 | D1-D11 をすべて決定 (未決ゼロ)。D11 は spec-review が検出した追加論点。Q2 と性能数値目標は決定者・期限付きで保留管理。spec-review PASS (5 回目) |
| 6   | Interface / Routing 設計    | 未着手     |            |                                                                                                                                                 |
| 7   | Content / Data 設計         | 未着手     |            |                                                                                                                                                 |
| 8   | Performance / Security 設計 | 未着手     |            |                                                                                                                                                 |
| 9   | Test / Metrics 設計         | 未着手     |            |                                                                                                                                                 |
| 10  | 実装分割                    | 未着手     |            |                                                                                                                                                 |
| 11  | レビュー済                  | 未着手     |            |                                                                                                                                                 |

## 上位文書整合

正本 ([PRD](../../PRD.md) / [Design Doc](../../design/DesignDoc.md) / [feature doc](../../design/features/) / [context](../../context/) / ADR) のどの節と、どう整合させたかを記録する。

- PRD 更新要否: 不要 (本プロダクトは統合モード。Why / What は Design Doc に統合)
- Design Doc 更新要否: 要 (① 「詳細の所在 → Feature 設計」の Java Analyzer 行を feature doc へリンクする ② 成功条件 S5 / 設計原則 P4 の測定方法を「2 つ目以降の Analyzer 追加時に Core 無変更」と明確化する。いずれも phase: sync で実施)
- ADR 起票要否: **要** (ADR-0003: Analyzer 起動コマンドを言語非依存な文字列として CLI flag + 環境変数で解決する = D2。phase: sync で起票)。D1 (build tool / JDK / 配布形態) は toolchain の確定値として `context/toolchain.md` に記録し、ADR にはしない

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
- WHERE 呼び出し先が interface / 抽象メソッドであるとき、システムは D11 の規則で決まる帰属型のメソッドを callee として `callEdge` を出力し、`callEdge.metadata.dispatch` に dispatch 種別を標識する。
- IF 呼び出し先メソッドの宣言サイトが scope 外で、その宣言型が引き上げ除外 package (既定: `java.*` / `javax.*` / `jakarta.*`) に属するとき、システムは `methodSymbol` / `callEdge` を出力しない (解析失敗ではないため `diagnostic` も出さない)。例: `userService.toString()` (`java.lang.Object#toString`)、レシーバ静的型が scope 内の `com.example.MyCollection` である場合の `myCollection.iterator()` (宣言サイトが `java.*` 側にある)。
- IF 呼び出し先メソッドの宣言サイトもレシーバの静的型もいずれも scope 外であるとき、システムは `methodSymbol` / `callEdge` を出力しない (同上)。
- IF 呼び出し先の型が解決できないとき、システムは `callEdge` を出力せず `diagnostic` (`severity: warning` または `partialFailure`) として未解決を報告し、解析を継続する。
- IF 個別ファイルがパース不能なとき、システムは該当ファイルを `diagnostic` で報告し、他ファイルの解析を継続する (部分解析を許容する)。
- IF 解析を継続できない致命的な問題が起きたとき、システムは `error` record を出力し、非ゼロ exit code で終了する。
- THE SYSTEM SHALL すべての出力 record に `schemaVersion` (Phase1 は `"1"`) と `recordType` を含める。
- THE SYSTEM SHALL 解析対象リポジトリを書き換えず、外部への送信を行わない。

## 設計時の論点

設計 / 実装フェーズへ持ち越す残課題を 1 件ずつ管理する。確定したものは「解決済みの論点」へ移す。

| #   | 論点                                                                                 | 決定候補                                                                                    | 決定     |
| --- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------- | -------- |
| D1  | ~~Java Analyzer の build tool / JDK version / 配布形態~~                             | (解決済み → `## 解決済みの論点` D1)                                                         | 解決済み |
| D2  | ~~Core が Analyzer をどう発見・起動するか (実行コマンドの解決)~~                     | (解決済み → `## 解決済みの論点` D2)                                                         | 解決済み |
| D3  | ~~SymbolSolver の型解決範囲 (source root のみか、依存 jar を classpath に含めるか)~~ | (解決済み → `## 解決済みの論点` D3)                                                         | 解決済み |
| D4  | ~~Phase1 で対応する `analysisMode` の範囲~~                                          | (解決済み → `## 解決済みの論点` D4)                                                         | 解決済み |
| D5  | ~~`methodId` / `signature` の正規化規則~~                                            | (解決済み → `## 解決済みの論点` D5。lambda / 匿名クラスの命名は D6 の結果に従って拡張する)  | 解決済み |
| D6  | ~~Phase1 で node 化する `symbolKind` の範囲~~                                        | (解決済み → `## 解決済みの論点` D6)                                                         | 解決済み |
| D7  | ~~interface / 抽象メソッド呼び出しの Phase1 での扱い~~                               | (解決済み → `## 解決済みの論点` D7)                                                         | 解決済み |
| D8  | ~~`diagnostic.code` 体系と、未解決/部分解析の分類粒度~~                              | (解決済み → `## 解決済みの論点` D8)                                                         | 解決済み |
| D9  | ~~大規模プロジェクトの性能 / メモリ方針~~                                            | (解決済み → `## 解決済みの論点` D9)                                                         | 解決済み |
| D10 | ~~テスト戦略 (fixture / contract test の実行方式 / CI の JVM 要求)~~                 | (解決済み → `## 解決済みの論点` D10)                                                        | 解決済み |
| D11 | ~~scope 外 (依存 jar / JDK) に宣言されたメソッドへの呼び出しの出力範囲~~             | (解決済み → `## 解決済みの論点` D11。spec-review が D4 / D7 の間の未決として検出し追加起票) | 解決済み |

## 解決済みの論点

### D1: build tool / JDK version / 配布形態 (2026-07-11 決定)

- **build tool: Gradle (Kotlin DSL)**。`gradlew` wrapper を同梱し、CI に Gradle 本体の事前インストールを要求しない。将来 Kotlin Analyzer を追加するときにも同じ build 基盤を使える。
- **JDK: 25 LTS** (Analyzer process の runtime)。Gradle toolchain で JDK 25 を固定する。これは Analyzer 自身が動く JVM の version であり、解析対象ソースの言語レベルとは独立して扱う (JavaParser は自身の runtime より新しい言語レベルも parse できる)。
- **配布形態: 単一 fat jar** (Gradle Shadow plugin)。Core は `java -jar <path>` の 1 コマンドで起動でき、D2 の起動契約を最も単純にできる。

**留意点 (Phase3 で確認)**: SootUp の bytecode frontend が最新の class file version に追随しているかは、Phase3 で SootUp を統合するときに確認する。Phase1 は JavaParser の source ベース解析のみのため、Phase1 のリスクにはならない。

**波及**: `context/toolchain.md` の標準スタック (Java Analyzer 行) を確定値に更新し、`context/project.md` の Quick Commands に Java の build / test コマンドを追加する (phase: sync)。CI に JDK 25 を要求する。

### D2: Analyzer 起動コマンドの解決 (2026-07-11 決定)

**CLI flag を第一とし、環境変数を fallback とする言語非依存の解決順序**を Core に持たせる。

- 解決順序: ① CLI flag (例: `--analyzer-cmd "java -jar /path/analyzer.jar"`) → ② 環境変数 (例: `DEPWALK_ANALYZER_CMD`) → ③ どちらも無ければ実行前に validation error で拒否する。
- Core は受け取った文字列を exec するだけで、`java` / jar / JVM の存在を知らない。言語固有の分岐と path 解決規約を Core に持ち込まないことで S5 (2 つ目以降の Analyzer 追加時に Core 無変更) を担保する。
- 規約 path による既定解決 (binary の隣を探す等) は Phase1 では導入せず、後続の CLI interface spec で必要になった時点で ③ の前段として足せる形にしておく。
- 解決順序 (flag 主 + 環境変数 fallback) は本 spec で確定する。**flag / 環境変数 / metadata passthrough の具体名は phase 6 (Interface 設計) で確定する** — 本 spec の実装対象に Core の初回配線が含まれ、Analyzer 側の classpath 必須検査 (D3) が key 名の合意を前提とするため。CLI 引数の**完全仕様** (出力形式 / 探索方向 / 深さ上限などの全 flag 体系) は後続の CLI interface spec が正本。

**利点**: E2E / contract test で fake analyzer (任意の実行可能ファイル) に差し替えられるため、JVM を持たない環境でも Core 側のテストが回る (D10 に影響)。

**波及**: `context/project.md` の Quick Commands に最小 `depwalk analyze` の起動例を記入する (source: clarify / D1・D2)。

### D3: SymbolSolver の型解決範囲 (2026-07-11 決定)

**依存 jar の classpath を必須入力とする。** classpath なしでの解析は許可しない。

**必須性の粒度**: `analysisRequest.metadata` の classpath **key は必須**とし、**値としての空配列は許容する** (依存を持たない純 Java プロジェクト / テスト fixture のため)。key 自体が無い場合のみ `JAVA_MISSING_CLASSPATH` の `error` とする。key 名は phase 6 (Interface 設計) で確定する。

- TypeSolver 構成: `ReflectionTypeSolver` (JDK 標準型) + `JavaParserTypeSolver` (対象プロジェクトの source root) + `JarTypeSolver` (依存 jar)。
- 理由: プロジェクト内のメソッド呼び出しであっても、レシーバの型を知るために library 型が必要になる (例: Spring Data の `JpaRepository` を継承した interface、`Optional` / `Stream` チェーン、library 由来の generics)。classpath を欠くと未解決 `diagnostic` が多発し、S1 / S2 (網羅性) が実用レベルに届かない。必須にすることで解析精度が常に一定になる。
- **classpath の受け渡し**: `analysisRequest.metadata` に載せる (protocol の `metadata` は「言語固有または Analyzer 固有の hint。Core の共通処理は依存しない」と定義済みのため契約変更は不要)。Core 側は言語固有の flag (`--java-classpath` 等) を持たず、**汎用の passthrough flag** (例: `--analyzer-metadata key=value`) で metadata へ素通しする。意味づけを知るのは Analyzer だけであり、S5 を守る。
- **必須性の検査場所**: Core は言語非依存で「Java には classpath が要る」ことを知らないため、検査は **Analyzer 側**で行う。classpath が metadata に無い場合、Analyzer は protocol の `error` record を出力し非ゼロ exit code で終了する (既存の process contract のまま成立する)。
- classpath の生成 (`./gradlew dependencies` 等) は利用者 / CI の責務とする。Analyzer が対象プロジェクトの build tool を叩いて自動取得する案は、対象を read-only に保つ原則 (`context/architecture.md` State Boundary) と衝突し、build 失敗が解析失敗に直結するため Phase1 では採らない。将来必要になれば別 spec で扱う。

**波及**:

- 要求定義のバリデーション方針 V1 (「対象パスに Java/Spring ソースが存在すること」) に classpath 必須を追加する。
- テスト fixture (サンプル Java/Spring プロジェクト) にも classpath の準備が必要になる (D10 で実行方式を決める)。
- Core の CLI に汎用 metadata passthrough flag が必要 (実装対象 `core` の初回配線に含める)。

### D4: Phase1 で対応する `analysisMode` の範囲 (2026-07-11 決定)

**`fullGraph` と `reachableFromEntrypoints` の両方を Phase1 で実装する。**

protocol は 2 モードの名前と既定値 (未指定時は `fullGraph`) しか定義していないため、Java Analyzer 側の意味論を本 spec で確定する:

| モード                     | 出力範囲                                                                                                          |
| -------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `fullGraph`                | scope (`include` / `exclude` 適用後) 内の全 `methodSymbol` と、その間の全 `callEdge`                              |
| `reachableFromEntrypoints` | `entrypoints` から **呼び出し先 (callee) 方向に推移的に到達する** `methodSymbol` と、それらの間の `callEdge` のみ |

- `entrypoints` が未指定または空配列の場合は、`analysisMode` の値によらず scope 全体の call graph 生成要求として扱う (protocol の既定義に従う)。
- **node 母集合 (どのメソッドを `methodSymbol` として出すか) の列挙方法は D11 を正本とする**。上表の「scope 内の全 `methodSymbol`」は D11 の帰属型規則で読む。
- **caller 探索 (S1) との関係**: `reachableFromEntrypoints` で得たグラフは呼び出し先方向にしか広がらないため、caller 探索の入力としては不完全になる。したがって **caller 方向の問い合わせでは Core が `fullGraph` を選ぶ**責務を持つ。`reachableFromEntrypoints` は callee 方向の調査で大規模リポジトリの出力量を削るための最適化と位置づける。
- モード選択の CLI 上の露出 (利用者が明示指定できるか、Core が問い合わせ方向から自動選択するか) は後続の CLI interface spec が正本。本 spec では Analyzer が両モードを実装することと、上記の意味論を確定する。

### D5: `methodId` / `signature` の正規化規則 (2026-07-11 決定)

**型表記は erasure + JVM binary name、`methodId` は可読な文字列そのものとする。**

| 項目            | 規則                                                                                                                   | 例                                                           |
| --------------- | ---------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| 型名            | JVM binary name (nested class は `$` 区切り)                                                                           | `com.example.Outer$Inner`                                    |
| generics        | erasure で消去する (型引数を保持しない)                                                                                | `List<String>` → `java.util.List`                            |
| 配列 / varargs  | erasure の配列表記に正規化する (varargs は配列として扱う)                                                              | `String...` → `java.lang.String[]`                           |
| `signature`     | `<帰属型の binary name>#<メソッド名>(<引数型の binary name をカンマ区切り>)` (**帰属型の決定規則は D11 を正本とする**) | `com.example.UserService#findById(java.lang.Long)`           |
| `qualifiedName` | 表示・debug 用の完全修飾名 (protocol の定義どおり)                                                                     | `com.example.UserService.findById`                           |
| constructor     | メソッド名 token は JVM 表記の `<init>` を用いる                                                                       | `com.example.UserService#<init>(com.example.UserRepository)` |
| `methodId`      | `java:` prefix + `signature` (hash しない)                                                                             | `java:com.example.UserService#findById(java.lang.Long)`      |

**根拠**:

- Java の overload 解決は erasure ベースであり、erasure が同一の overload は言語仕様上コンパイルできない。したがって erasure だけで overload の区別に十分で、generics を保持しても一意性は増えない。
- source 表記 (`com.example.Outer.Inner`) は package 区切りと nested 区切りを区別できない。binary name はこの曖昧さがなく、Phase3 で統合する SootUp の型表記とも揃う。
- `methodId` を hash しないのは、protocol が JSONL を選んだ理由の一つ「デバッグ容易 (テキストで観測可能)」と一貫させるため。JSONL のサイズは増えるが、test 失敗時の出力と JSONL の目視追跡が可能になる利点を優先する。決定性は文字列生成規則が決定的であることで満たす。

**lambda / 匿名クラスの命名** (D6 決定を受けた拡張):

- 匿名クラスのメソッド: 宣言型を JavaParser のソース出現順で採番した binary name (`com.example.Outer$1`) とし、通常のメソッドと同じ規則で `signature` / `methodId` を作る。採番はソース内容が同じなら決定的に再現する。
- lambda: 独立 node にしないため専用の ID を持たない (D6 参照)。

### D6: node 化する `symbolKind` の範囲 (2026-07-11 決定)

**`method` + `constructor` + static initializer (`initializer`) を node 化する。lambda は独立 node にしない。** protocol の `symbolKind` enum を変更しないため、契約変更 (major bump) は発生しない。

| Java の構文                                     | 扱い                                                                                  |
| ----------------------------------------------- | ------------------------------------------------------------------------------------- |
| インスタンス / static メソッド                  | `symbolKind: method`                                                                  |
| コンストラクタ                                  | `symbolKind: constructor`                                                             |
| 匿名クラスのメソッド                            | `symbolKind: method` (宣言型が `Outer$1` になるだけで実体は通常のメソッド)            |
| static 初期化ブロック                           | `symbolKind: initializer` (`signature` は `com.example.Foo#<clinit>()`)               |
| インスタンス初期化ブロック / フィールド初期化子 | 独立 node にせず、**各 `constructor` に畳み込む** (Java コンパイラの意味論に合わせる) |
| lambda 本体                                     | 独立 node にせず、**lambda を字句的に囲むメソッドに帰属**させる                       |

**lambda の扱いの詳細**: lambda 本体内の呼び出しは、囲みメソッドを caller とする `callEdge` として出力する。遅延実行される呼び出しであることは `callEdge.metadata` に `viaLambda: true` を立てて標識し、情報が完全に失われないようにする (Core の graph 構築は `metadata` に依存しないため契約上は無害)。

**根拠**: protocol の `function` は本来 非 OO 言語の関数向けであり、lambda に流用すると意味論の濫用になる。契約を拡張して `lambda` を追加する案は、#8 で確定した契約の major bump を招き、Phase1 の便益に対して代償が大きい。囲みメソッドへの帰属は「どのメソッドを直すとどこに影響するか」という depwalk の用途に対して十分な粒度を保てる。

**留意点**: lambda を独立 node にしないため、「lambda が実際に呼ばれる場所 (`Stream.forEach` の内部等)」から lambda 本体への辺は表現されない。これは Phase1 の既知の制約とし、必要になれば Phase3 (SootUp / Interface Dispatch) の文脈で再検討する。

### D7: interface / 抽象メソッド呼び出しの扱い (2026-07-11 決定)

**帰属型 (D11 の規則で決まる型。interface / 抽象クラスを含む) のメソッドを callee として `callEdge` を出力し、`callEdge.metadata` に dispatch 種別を標識する。**

- Phase1 は DI 解決を行わないため、`userRepository.findById(id)` の callee は静的に決まる帰属型のメソッド (`com.example.UserRepository#findById(java.lang.Long)`) になる。帰属型の決定規則は D11 を正本とする。実装クラスのメソッドへの辺は Phase2 (Spring Bean / DI 解決) 以降で追加する。
- `callEdge.metadata.dispatch` に呼び出しの種別を持たせる: `static` (static メソッド呼び出し) / `virtual` (具象クラスの instance メソッド) / `interface` (interface 経由) / `abstract` (抽象クラスの抽象メソッド経由)。利用者は「この辺は宣言型止まりで実体ではない」と判別でき、Phase2/3 で実装候補の辺を足すときの土台にもなる。Core の graph 構築は `metadata` に依存しないため契約上は無害。
- 未解決 `diagnostic` に倒す案は採らない。Spring プロジェクトでは呼び出しの大半が interface 越しであり、辺を落とすと S1 / S2 (網羅性) が Phase1 で実用にならないため。

**S4 との関係**: 「Spring DI 経由の呼び出し先を実体まで解決できる」(S4) は Phase2 以降の成功条件であり、Phase1 では宣言型止まりであることが仕様である。Phase1 の出力は「interface のメソッドが呼ばれている」ところまでを正確に表す。

### D8: `diagnostic` / `error` の code 体系 (2026-07-11 決定)

**`JAVA_` prefix + 大文字スネークケース**とする。Core は `code` を不透明な文字列として扱うため契約変更は発生せず、言語別 prefix により将来の Kotlin / TypeScript Analyzer と名前空間が衝突しない。

`diagnostic` (解析継続):

| code                        | severity         | 出る場面                                                          |
| --------------------------- | ---------------- | ----------------------------------------------------------------- |
| `JAVA_UNRESOLVED_SYMBOL`    | `warning`        | 呼び出し先の型が解決できず `callEdge` を張れない                  |
| `JAVA_PARSE_ERROR`          | `partialFailure` | ファイル単位で構文解析に失敗し、そのファイルを飛ばした            |
| `JAVA_ENTRYPOINT_NOT_FOUND` | `warning`        | `entrypoints` の method selector に一致する method が見つからない |

`error` (fatal / 非ゼロ exit):

| code                     | 出る場面                                                                                                          |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| `JAVA_MISSING_CLASSPATH` | `analysisRequest.metadata` に classpath の **key が無い** (D3。値としての空配列は正当な入力であり error にしない) |
| `JAVA_MISSING_JAR`       | classpath に指定された jar が存在しない / 読めない (D8-b で fatal と決定)                                         |
| `JAVA_INVALID_REQUEST`   | `analysisRequest` が Java Analyzer として処理できない (未対応 `language` 等)                                      |
| `JAVA_INTERNAL_ERROR`    | 上記以外の継続不能な内部エラー                                                                                    |

**jar 欠落を fatal にする根拠**: jar が 1 つ欠けるだけで、その jar 由来の型が絡む解決が広範囲に失敗する。継続すると「未解決だらけの、一見成功した結果」が出てしまい、利用者が不完全なグラフを正と誤認するリスクが高い。D3 で classpath を必須化した方針 (解析精度を常に一定に保つ) と一貫させ、fatal で即停止する。

**未解決 symbol の観測性**: `diagnostic.sourceLocation` と `relatedMethodId` を可能な範囲で埋め、どのファイル・どの呼び出し元で未解決が起きたかを追跡できるようにする (要求定義の監査要件「未解決シンボル・部分解析の発生を観測可能にする」に対応)。

### D9: 性能 / メモリ方針 (2026-07-11 決定)

**方式を Phase1 の必須仕様として確定し、数値目標は実測 baseline から確定する。** 現時点で実測データがなく、根拠のない数値を先に置くと「目標未達」なのか「目標が的外れ」なのかを実装時に判別できなくなるため。

Phase1 の必須仕様:

- **streaming 出力**: `methodSymbol` / `callEdge` を逐次 stdout へ flush し、Analyzer 側にグラフ全体をメモリ保持しない (protocol の「stdout に JSONL record を逐次出力」と整合)。
- **AST の逐次破棄**: 解析済みファイルの AST を保持し続けない。保持するのは SymbolSolver の型解決キャッシュと、`callEdge` 出力に必要な最小限の情報に限る。
- **計測の観測性**: 解析ファイル数 / 所要時間 / 未解決件数を **stderr** に出力する (protocol record としては出さない。stderr は protocol の parse 対象外であることが契約で定まっている)。

数値目標:

- Phase1 実装時に、テスト fixture のサンプル Java/Spring プロジェクトに対する実測値 (ファイル数 / 所要時間 / 最大 RSS) を baseline として記録する。
- baseline を得た後に、目標値 (対象規模と実行時間の上限) を feature doc 側へ記録する (phase: sync 以降 / 実装 task の成果として)。本 spec は「baseline を測る」ことを Phase1 の完了条件に含めることで、数値未定のまま放置されない状態にする。

### D10: テスト戦略 (2026-07-11 決定)

**Java 側 unit test / Go 側 fake analyzer / 実 jar を使った E2E の三層**とする。

| 層                     | 配置                                                          | 検証範囲                                                                                                                                                                                                                                                | JVM 要否               |
| ---------------------- | ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------- |
| Java unit test (JUnit) | `analyzers/java/`                                             | signature / `methodId` の正規化 (D5)、`symbolKind` の割り当て (D6)、dispatch 標識 (D7)、`diagnostic` / `error` code (D8)、**帰属型の決定規則 (D11: 宣言サイト / 引き上げ / 除外 package の既定値と metadata による上書き / override / static / `new`)** | 要 (Java job)          |
| Go process contract    | `core/internal/analyzer` (既存) + fake analyzer               | stdin close / stdout 逐次 parse / stderr 非 parse / exit code / metadata passthrough (D2・D3)                                                                                                                                                           | **不要** (fake で代替) |
| E2E (S1 / S2 照合)     | `testdata/fixtures/java/` のサンプル Java/Spring プロジェクト | `depwalk analyze` から実 jar を起動し、既知の caller / callee 集合と出力を照合する                                                                                                                                                                      | 要 (JDK 25 + jar)      |

- **Go 側が JVM に依存しない**のは D2 の設計 (起動コマンドを文字列で受ける) の直接的な帰結であり、Core の unit / contract test は fake analyzer (任意の実行可能ファイル) で回せる。CI の Go job に JVM を要求しない。
- **E2E だけが JDK 25 + build 済み jar を要求する**。CI は「Go job (JVM 不要)」と「Java + E2E job (JDK 25 / Gradle build)」に分ける。
- protocol 契約の検査ロジックを Java / Go に二重実装しない。Java 側は「自分の出力が仕様どおりか」を、Go 側は「受け取った JSONL を契約どおり扱えるか」を検証し、両者が実際に噛み合うことは E2E が担保する。

**D3 の運用細則 (fixture のための補足)**: classpath は必須入力だが、**値としての空配列を許す**。依存を持たない純 Java の fixture プロジェクトでは空 classpath で解析でき、軽量な fixture を維持できる。key 自体が無い場合は `JAVA_MISSING_CLASSPATH` の `error` とする (D3 / D8 のとおり)。

**波及**: `context/testing.md` に本三層の責務分担と「Go job は JVM 不要 / E2E job は JDK 25 を要求」を追記する (phase: sync)。`context/project.md` の Quick Commands に Java の test コマンドと E2E の実行手順を追加する。

### D11: scope 外に宣言されたメソッドへの呼び出しの扱い (2026-07-11 決定)

**帰属型 (メソッドが属する型) は「宣言型を優先し、宣言が scope 外のときだけレシーバの静的型へ引き上げる」。**

`spec-review` (phase: clarify) が D4 (「`fullGraph` = scope 内の `methodSymbol` とその間の `callEdge`」) と D7 (「interface 呼び出しは宣言型のメソッドに辺を張る」) の間の未決として検出した論点。`userRepository.findById(id)` の宣言型メソッドは継承元の `JpaRepository#findById` (jar 由来 = scope 外) であり、宣言型のみを基準にすると node にならず、classpath を必須化 (D3) してまで解決した呼び出しが出力から消えてしまう。

**帰属型の決定規則** (この規則で決まる帰属型が `signature` / `methodId` の型部分になる。D5 の「帰属型」はこれを指す):

「宣言型」は **SymbolSolver が override 解決まで済ませた後に返す、そのメソッド宣言の所在型**を指す (本体を持つかどうかは問わない — interface / 抽象メソッドの宣言もここに含む)。override されていれば override 先の型、されていなければ継承元の型になる。

| 条件                                                                                                  | 帰属型                                                | 例                                                                                                                                                                                        |
| ----------------------------------------------------------------------------------------------------- | ----------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **宣言サイトが scope 内**                                                                             | **宣言型** (実際に本体が書かれている型)               | `UserService extends BaseService` で `save` を override していない → `userService.save()` の callee は `com.example.BaseService#save`。override していれば `com.example.UserService#save` |
| **宣言サイトが scope 外**で、**レシーバの静的型が scope 内**、かつ宣言型が引き上げ除外 package でない | **レシーバの静的型** へ引き上げる                     | `UserRepository extends JpaRepository` → `userRepository.findById()` の callee は `com.example.UserRepository#findById(java.lang.Long)`                                                   |
| **宣言サイトが scope 外**で、宣言型が**引き上げ除外 package**                                         | 出力しない                                            | `userService.toString()` (`java.lang.Object#toString`) / `equals` / `hashCode`                                                                                                            |
| **宣言サイトが scope 外**で、**レシーバの静的型も scope 外**                                          | 出力しない (`methodSymbol` / `callEdge` とも出さない) | `String#equals` / `List#add`                                                                                                                                                              |

**引き上げ除外 package (B2)**: 既定で `java.*` / `javax.*` / `jakarta.*` を引き上げ対象から除外する。`analysisRequest.metadata` で除外 package を上書き可能にする (key 名は phase 6 で確定)。除外しないと `toString` / `equals` / `hashCode` が scope 内の全型ぶん node 化され、D11 自身のノイズ排除根拠と矛盾するため。除外判定は宣言型の binary name に対する **`.` 区切り segment 単位の prefix 一致**で行う (`java` は `java.lang` / `java.util` に一致し、`javafx` には一致しない)。

**その他の呼び出し形**:

- **static メソッド**: 「レシーバ」を参照した型とみなして同一規則を適用する (`Sub.staticFromLibBase()` は、宣言サイトが scope 外・参照型 `Sub` が scope 内・除外 package でないなら `Sub` へ引き上げる)。
- **`this.foo()` / `super.foo()`**: 宣言サイトが scope 内なら宣言型に帰属するため揺れない。
- **`new Foo()` (constructor)**: constructor は継承されないため引き上げは発生しない。`Foo` が scope 内なら `com.example.Foo#<init>(...)` を callee とし、scope 外 (`new ArrayList<>()` 等) なら出力しない。

**根拠**:

- **宣言サイト基準の根拠 (B1)**: 「常にレシーバ型へ帰属」だと scope 内継承 (`UserService extends BaseService`, override なし) で `UserService#save` という実在しない node が合成され、`BaseService#save` と 2 node に分裂して caller 探索が取りこぼす。逆に「常に根の基底へ集約」だと、override した `UserService#save` が誰からも参照されない dead node になり、その override を直すときの影響調査ができない。**実際の宣言サイト**を使えばどちらの病理も起きない。
- **引き上げを scope 外に限る根拠**: 引き上げは「宣言が jar の中にあって node にできない」問題を解くためだけに使う。継承元が scope 外であることは `methodSymbol.metadata` に保持する (例: `declaringType: "org.springframework.data.repository.CrudRepository"`, `inherited: true`)。情報を捨てずに、グラフの語彙を自プロジェクトに閉じる。
- **scope 外呼び出しを落とす根拠**: `String#equals` / `List#add` のような JDK / library 内部メソッドをすべて node 化すると、影響調査に無価値なノイズでグラフが埋まり、出力量も大きく増える。depwalk の用途は「自分のコードのどこが影響を受けるか」であり、library 内部の呼び出し関係は対象外 (Design Doc のスコープと整合)。
- **未解決との区別**: scope 外呼び出しの省略は「解析できなかった」ではなく「仕様として出力しない」ため、`JAVA_UNRESOLVED_SYMBOL` の `diagnostic` は出さない。型解決自体に失敗した場合のみ `diagnostic` とする (D8)。
- **protocol 整合**: 出力する `callEdge` の caller / callee はいずれも出力済み `methodSymbol` を参照するため、「valid な `callEdge` は解決済み `methodSymbol` を参照する」という契約を満たす。

**Phase1 の既知の制約 (override)**: 静的解決のため、`BaseService` 型の変数経由の呼び出しは `BaseService#save` に帰属し、実行時に呼ばれる `UserService#save` (override) には辺が張られない。virtual dispatch の解決は Phase3 (Interface Dispatch / Override 解決, SootUp) の担当とする。

**D4 の node 母集合 (列挙方法)**: `fullGraph` の `methodSymbol` は次の和集合とする。

1. **宣言列挙**: scope 内で宣言された method / constructor / static initializer (D6 の範囲) のすべて。呼ばれていないメソッドも node として出力する (caller が 0 件であることを示せる = S1 の用途に必要)。
2. **call site 由来**: 引き上げで生じた node (scope 内型に帰属する、宣言が scope 外のメソッド)。これらは scope 内に宣言が存在しないため宣言列挙では出せず、**実際に呼び出された箇所からのみ**生成する。呼ばれていない継承 library メソッドは node 化しない。

`reachableFromEntrypoints` は上記母集合のうち、entrypoints から callee 方向に推移的に到達するものに限る (D4)。

## 未確定事項

(決定できない項目を理由とともに残す。1 件でも残っていれば下流 phase は止める。ただし **決定者 / 期限が付き、Phase1 実装に影響しないことが明記された項目は除く**)

- **Q2 (SootUp 統合範囲)**: DesignDoc Open Questions Q2。決定者 Fukuemon / 期限 Phase3 着手前。本 spec では Phase3 のスコープ宣言のみを行い、統合範囲は決定しない。本 spec の下流 phase を止める対象には含めない (Phase1 実装に影響しないため)。
- **性能の数値目標 (D9 の従属項目)**: 決定者 Fukuemon / 期限 Phase1 実装完了時。方式 (streaming 出力 / AST 逐次破棄 / 計測の観測性) は D9 で確定済みで、数値目標のみ実測 baseline 取得後に確定する。baseline の取得を Phase1 の完了条件に含めるため、下流 phase を止める対象には含めない。確定値の正本は phase: sync 後の feature doc (`design/features/java-analyzer/`) に一本化し、spec 側 (phase 8) には決定時スナップショットとして残す。

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

- streaming 出力 (record を逐次 flush) / AST の逐次破棄 / 計測値を stderr に出力。数値目標は Phase1 実装時の実測 baseline から確定する (解決済みの論点 D9)。

### Routing / URL State

- 非該当 (CLI ツール / 独立プロセス)。

### Content / Assets

- 非該当。

### UI Reuse

- 非該当 (Design Doc Non Goals: IDE Plugin / Web UI を提供しない)。

### Testing

- Java unit test (JUnit) / Go 側 process contract (fake analyzer で JVM 不要) / 実 jar を使った E2E の三層で担保する (解決済みの論点 D10)。横断規約は [context/testing.md](../../context/testing.md)。

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

| #   | ケース                                       | ユーザーへの見せ方                                                                          | リカバリ                                                                                          |
| --- | -------------------------------------------- | ------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| 1   | 型解決できないシンボル                       | `diagnostic` で未解決を報告                                                                 | 解析継続 (部分結果を返す)                                                                         |
| 2   | DI / interface 経由で実体が一意に定まらない  | エラーとしない。帰属型 (D11) のメソッドへ `callEdge` を張り `metadata.dispatch` を標識 (D7) | Phase2 (DI 解決) / Phase3 (Interface Dispatch) で実装への辺を追加する                             |
| 3   | パース不能なソースファイル                   | `diagnostic` で該当ファイル報告                                                             | 他ファイルの解析を継続                                                                            |
| 4   | 解析継続不能な致命的エラー                   | `error` record + 非ゼロ exit                                                                | Core が fatal failure として扱う                                                                  |
| 5   | classpath が metadata に無い (D3 で必須化)   | Analyzer が `error` record + 非ゼロ exit                                                    | 利用者が classpath を用意して再実行する (Core は言語固有の必須性を知らないため検査は Analyzer 側) |
| 6   | classpath の一部 jar が存在しない / 読めない | `error` (`JAVA_MISSING_JAR`) + 非ゼロ exit で即停止 (D8)                                    | 利用者が classpath を修正して再実行する                                                           |

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
| `context/toolchain.md` 標準スタック | Java Analyzer 行を確定値に更新: build tool = Gradle (Kotlin DSL) + Shadow plugin、JDK = 25 LTS (Gradle toolchain で固定)、配布形態 = 単一 fat jar (source: clarify / D1)                                       | 現在「JavaParser / SymbolSolver / SootUp を利用」までしか固定されていない                                                                                   |
| `context/engineering.md`            | Analyzer build を束ねる wrapper (make-like) 導入要否の判断を反映                                                                                                                                               | 「Analyzer build を束ねる必要が出た時点で検討」と保留されている                                                                                             |
| `context/testing.md`                | Java Analyzer 側の contract test 実行方式 / CI の JVM 要求を追記 (D10)。S5 の再掲箇所 (「新 Analyzer 追加時は Protocol contract test の通過を必須」) が Design Doc 側の明確化に追随しているか確認する          | Protocol contract test の実行主体が Go 側のみを前提にしている                                                                                               |
| `context/architecture.md`           | Package Boundary の S5 再掲 (「Analyzer 追加で Core に差分が出ないこと (S5)」) を Design Doc 側の明確化に追随させる                                                                                            | S5 の測定方法を変更提案するため、再掲箇所に drift が残らないようにする                                                                                      |

### ADR の新規 / 更新

| ADR ID          | 変更内容                                                                                                                             | 理由                                                                                                                                      |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- |
| ADR-0003 (新規) | Analyzer 起動コマンドを言語非依存な文字列として解決する (CLI flag 主 + 環境変数 fallback)。Core は起動対象の言語ランタイムを知らない | S5 (2 つ目以降の Analyzer 追加時に Core 無変更) の担保方法であり、将来 Analyzer を追加するたびに参照される長期判断 (source: clarify / D2) |

## レビュー

`spec-review` (fresh-context evaluator) の最新結果。完全な記録は `review.md` を参照。

| 日付       | 結果 (PASS / NEEDS_WORK) | 指摘要点                                                                                                                                                                                         | 対応                                                                                                                                                                                         |
| ---------- | ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-07-11 | NEEDS_WORK               | `core` の責務境界が矛盾 (D2 で起動方法を決めるのに実装対象は `-` / S5 解釈が上位より強い)                                                                                                        | `core` を実装対象 ◯ (初回配線のみ) に変更、S5 の表現を DesignDoc 定義に合わせた。advisory (D6 の protocol 影響 / context 波及) も反映                                                        |
| 2026-07-11 | NEEDS_WORK (2 回目)      | S5 の再定義が「継承」扱いのままで Design Doc へ back-propagation 登録されていない                                                                                                                | 上位文書整合表の Design Doc 行を分割し S5 / P4 を `変更提案` として登録。「Design Doc への影響」表に明確化行を追加 (phase: sync で反映)                                                      |
| 2026-07-11 | NEEDS_WORK (3 回目)      | メタ情報の追随漏れ (phase 3 備考 / phase 2 状態 / review 記録)。設計内容の変更は不要                                                                                                             | phase 3 備考を齟齬検出に更新、phase 2 を `進行中` に戻し、`review.md` と本表に 2・3 回目の記録を追記                                                                                         |
| 2026-07-11 | **PASS** (4 回目)        | 全観点 PASS (prompts / 正本境界は N/A)。phase: scaffold の gate 通過                                                                                                                             | phase: clarify (D1-D10 の決定) へ進む                                                                                                                                                        |
| 2026-07-11 | NEEDS_WORK (clarify)     | D4 (scope 内 node) と D7 (宣言型に辺) の間に未決 — scope 外に宣言されたメソッド (継承した library メソッド) の node 化基準が無く、protocol の「valid edge は解決済み symbol を参照」に違反しうる | D11 を追加起票して決定。advisory 4 件 (classpath の key/空配列、metadata key 名の確定先、constructor signature、性能目標の追跡) も反映                                                       |
| 2026-07-11 | NEEDS_WORK (clarify 2)   | D11 を「常にレシーバ型に帰属」としたため scope 内継承で node が分裂し S1 の網羅性が壊れる。D5 の signature 定義と EARS が D11 反映前のまま                                                       | D11 を「宣言型優先、scope 外のときだけ引き上げ」に修正。D5 / EARS / D7 本文を帰属型基準に同期。性能目標の正本を feature doc に一本化                                                         |
| 2026-07-11 | NEEDS_WORK (clarify 3)   | D11 の「宣言型」が override 時に一意でない (dead node か取りこぼしか)。引き上げが `Object#toString` 等を巻き込みノイズ排除根拠と衝突。`fullGraph` の node 母集合の列挙方法が未定義               | 宣言型を「実際の宣言サイト」と定義。引き上げ除外 package (既定 JDK / metadata で上書き可) を導入。node 母集合を「宣言列挙 ∪ call site 由来の引き上げ node」と定義。D7 / D10 / エラー表も同期 |
| 2026-07-11 | NEEDS_WORK (clarify 4)   | EARS が D11 の第 3 分岐 (引き上げ除外 package) に追随しておらず、`myCollection.iterator()` で EARS と D11 が逆の結論を出す (決定内容の欠陥ではなく同期漏れ)                                      | EARS の WHERE 条を D11 参照に改め、除外 package の IF 条を追加。D5 の括弧書きも D11 参照に統一。advisory (D4 の相互参照 / prefix 一致の segment 粒度 / D10 の検証範囲) も反映                |
| 2026-07-11 | **PASS** (clarify 5)     | 全観点 PASS。D1-D11 が protocol 契約を変更せずに成立し、EARS / D4-D10 / エラー表が D11 を正本として一貫。未決ゼロ                                                                                | advisory 2 件 (宣言サイトの定義を abstract / interface に適用可能な表現へ、`iterator()` の例に前提を明記) を反映。phase: diagram へ進む                                                      |

## 変更履歴

| 日付       | 変更者   | 変更内容                                                                                                                                                                                                                                       |
| ---------- | -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-07-11 | Fukuemon | phase: scaffold — index.md を作成、D1-D10 を初期論点として列挙                                                                                                                                                                                 |
| 2026-07-11 | Fukuemon | spec-review NEEDS_WORK 対応 — `core` を実装対象 ◯ (初回配線のみ) に、S5 の表現を修正、context への影響表と D6 の注記を追加                                                                                                                     |
| 2026-07-11 | Fukuemon | spec-review 再指摘対応 — S5 / P4 の明確化を Design Doc への変更提案として登録 (整合表・影響表)、context: engineering 行を追加                                                                                                                  |
| 2026-07-11 | Fukuemon | spec-review 3 回目対応 — phase 3 備考 / phase 2 状態 / レビュー記録のメタ情報を実態に同期                                                                                                                                                      |
| 2026-07-11 | Fukuemon | phase: clarify — D1-D10 をすべて決定 (未決ゼロ)。ADR-0003 (D2) の起票を決定                                                                                                                                                                    |
| 2026-07-11 | Fukuemon | spec-review (clarify) 対応 — D11 (scope 外呼び出しの node 化基準) を追加起票し決定。classpath の key/空配列の粒度、constructor の signature、metadata key 名の確定先、性能目標の追跡を明記                                                     |
| 2026-07-11 | Fukuemon | spec-review (clarify 2 回目) 対応 — D11 の帰属規則を「宣言型優先、scope 外のときだけレシーバ型へ引き上げ」に修正 (scope 内継承での node 分裂を防止)。D5 の signature 定義と EARS を帰属型基準に同期                                            |
| 2026-07-11 | Fukuemon | spec-review (clarify 3 回目) 対応 — D11 の「宣言型」を実際の宣言サイトと定義 (override の dead node を回避)。引き上げ除外 package (既定 `java.*` / `javax.*` / `jakarta.*`、metadata で上書き可) と `fullGraph` の node 母集合の列挙方法を追加 |
| 2026-07-11 | Fukuemon | spec-review (clarify 4 回目) 対応 — EARS を D11 の除外 package 分岐に同期 (WHERE 条を D11 参照化 + IF 条を追加)。D5 の括弧書き / D4 の相互参照 / prefix 一致の segment 粒度 / D10 の検証範囲を整理                                             |

## 備考

- appendix (api / database / authorization / screen-spec / testid) は本 spec のスコープに該当しないため取り込まない。Java Analyzer は CLI 配下の独立プロセスであり、HTTP API / 永続層 / 画面 / ロールを持たない。
