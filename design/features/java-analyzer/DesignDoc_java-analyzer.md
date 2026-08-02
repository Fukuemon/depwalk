---
type: feature-design
title: "Java Analyzer"
description: Java/Spring 解析の全体構成と、外部ライブラリの隔離境界・起動契約・性能方針
status: 完了
keywords: [Java, Spring, SootUp, JavaParser, Gradle Tooling API, discovery]
governs:
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/pipeline
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/scope
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/preflight
verified_commit: 2d82ed3
---

# Feature 設計: Java Analyzer

Java/Spring のソースを読み、メソッドの呼び出し関係を抽出する言語別 Analyzer の設計を定める。

共通契約 (SPI / JSONL Protocol / Model schema) は [Analyzer Protocol / SPI feature doc](../analyzer-protocol/DesignDoc_analyzer-protocol.md) と [ADR-0001](../../../adr/0001-analyzer-protocol-jsonl-spi.md) が定める。本 doc は Java 固有の部分だけを扱う。

## 前提: この doc を読むのに必要な語

Java の静的解析に固有の語が多いため、先に整理する。

### 解析の 3 段

Java のソースから「どのメソッドがどのメソッドを呼んでいるか」を出すには、3 段階を踏む。

| 段                | やること                                                                               | 使うもの                                                                    |
| ----------------- | -------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| 1. 構文を読む     | ソースを構文木 (AST) にする。「ここにメソッド呼び出しがある」まで分かる                | **[JavaParser](https://javaparser.org/)**                                   |
| 2. 型を決める     | その呼び出しが**どの型の**どのメソッドか決める。`user.save()` の `user` が何型かを解く | **SymbolSolver** (JavaParser の `javaparser-symbol-solver-core`)            |
| 3. 実装候補を絞る | interface 越しの呼び出しについて、実装クラスの候補を挙げる                             | **[SootUp](https://soot-oss.github.io/SootUp/latest/)** / Spring の DI 情報 |

2 が解けないと呼び出し先が特定できず、edge を出せない。**この doc の大半は「2 と 3 をどこまで、どう解くか」の規則**である。

SootUp は JVM の bytecode を読んで型階層や call graph を扱う静的解析基盤だが、depwalk は**型階層と override / interface 実装候補の索引としてのみ使う**。call graph の生成は任せない (判断を定めるのは [ADR-0005](../../../adr/0005-adopt-sootup-and-spring-di-resolution.md))。

### 型解決に必要な入力

| 語                    | 意味                                                                                                                                                                                                                         |
| --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **classpath**         | 型を解決するために参照する場所の一覧。依存ライブラリの jar と、コンパイル済みクラスの置き場からなる ([JDK の `--class-path`](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html))                            |
| **classes directory** | コンパイル済みの `.class` ファイルが置かれるディレクトリ (Gradle なら `build/classes/java/main`)。**classes output** も同じものを指す                                                                                        |
| **bytecode member**   | ソースには現れないが `.class` には存在するメソッドやコンストラクタ。[Lombok が生成する](https://projectlombok.org/features/constructor) getter / コンストラクタが典型。ソースだけ見ても分からないため、`.class` を読んで補う |
| **source root**       | 解析対象のソースが置かれたディレクトリ (`src/main/java` 等)                                                                                                                                                                  |

依存ライブラリのソースは手元にないため、型解決は `.class` を読んで行う。classpath が欠けると「型が分からない」呼び出しが増え、edge が落ちる。

### 結果の扱いに使う語

| 語              | 意味                                                                                                                                       |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| **adjacency**   | 隣接関係。「どの node からどの node へ edge が伸びているか」の対応表                                                                       |
| **provenance**  | その edge が**どういう根拠で出たか** (ソースの呼び出しから直接か、Spring の DI 解決を経たか等)。同じ edge が複数の経路で出たときに統合する |
| **完全性 gate** | 解析しきれなかった呼び出しが残ったまま結果を返さないための検査。未解決が残れば失敗として扱う                                               |
| **救済**        | 一次的に解決できなかった呼び出しを、別の手段 (`.class` を読む等) で解き直すこと                                                            |

### 範囲と失敗の扱い

| 語                     | 意味                                                                                                                                                       |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **workspace**          | 解析の起点となるディレクトリ。利用者が `depwalk analyze <path>` で渡したもの                                                                               |
| **scope (解析 scope)** | node として出す対象の範囲。**scope 内**は自分のコード、**scope 外**は依存ライブラリや JDK。scope 外のメソッドは原則 node にしない (影響調査に使わないため) |
| **record**             | Analyzer が stdout へ 1 行ずつ出す JSONL の 1 件。`methodSymbol` / `callEdge` / `diagnostic` / `error` の 4 種                                             |
| **diagnostic**         | 解析を続けられる問題の報告。出しても結果は返る                                                                                                             |
| **fatal**              | 解析を続けられない問題。`error` record を出して非ゼロ exit し、**それまでに出した record ごと破棄される**                                                  |

## 背景・要件解釈

対象は Java/Spring Boot であり、Java Analyzer は `analyzer-protocol` の SPI / JSONL スキーマを実装する最初の言語別 Analyzer である。Core 側は Protocol parser / validator (`core/internal/protocol`) と Analyzer process 起動 (`core/internal/analyzer`) を実装済みで、契約の受け側は揃っている。本 feature は、その契約に対して JSONL を出力する Java 側の実装方式 (build 基盤、起動契約、型解決、正規化規則、帰属型決定、段階導入) を確定する。

本 feature が関わる成功条件は [DesignDoc](../../DesignDoc.md) の 3 つである。

- **S1 / S2**: caller / callee 探索の網羅性 — graph の入力を供給する
- **S4**: Spring DI 経由の呼び出し先解決
- **S5**: 2 つ目以降の言語 Analyzer を追加するとき Core を変更せずに済む

## スコープ

### やること

- Java ソースの AST 解析 (JavaParser) と型解決 (SymbolSolver) による静的呼び出し抽出
- 抽出結果を `analyzer-protocol` の JSONL スキーマ (`methodSymbol` / `callEdge` / `diagnostic` / `error`) で stdout へ出力
- `analysisRequest` の受領 (stdin) と process contract (exit code / stderr) の遵守
- Java Analyzer の build / 配布形態 (Gradle + Shadow plugin、単一 fat jar)
- Core からの起動方法 (CLI flag / 環境変数による起動コマンド解決) の確定
- 未解決 symbol / 部分解析の `diagnostic` 表現
- SootUp 型階層補完、Interface Dispatch / Override 解決、Spring Bean / DI 解決、候補 edge 統合の契約
- single / multi-project を同じ request で扱う Gradle build model discovery と明示 source root override
- parse・resolution・生成 member を含む call inventory の完全性 gate

### やらないこと

- 共通契約 (SPI / Protocol / Model schema) の定義・変更 (→ analyzer-protocol feature doc が定める)
- グラフ探索 (→ traversal)、出力整形 (→ output)
- SootUp への call graph 生成の委任 (型階層・override・interface 実装候補の索引としてのみ使う)
- Reflection / AspectJ Runtime / 実行時 Proxy の動的解析 (Design Doc Non Goals)
- CLI 引数の完全仕様の確定 (出力形式指定 / 探索方向 / 深さ上限などの全 flag 体系 → 後続の CLI interface spec)

## 設計

### 詳細の所在

本 doc は Java Analyzer 全体の骨格 (実装基盤・package 境界・起動契約・性能) を持つ。個別の規則は次が持つ。

- [discovery.md](discovery.md) — 解析対象のソースと classpath をどう決めるか (Gradle build model と安全境界)
- [analysis.md](analysis.md) — ソースから呼び出し関係をどう解決するか (型解決 / Spring DI / 完全性)
- [protocol-mapping.md](protocol-mapping.md) — 解析結果を Protocol の record へどう写すか (正規化 / 帰属型 / metadata / diagnostic)

### 実装基盤

- **build tool**: Gradle (Kotlin DSL)。`gradlew` wrapper を同梱し、CI に Gradle 本体の事前インストールを要求しない。
- **JDK**: 25 LTS。Gradle toolchain で固定する (Analyzer process 自身が動く JVM の version。解析対象ソースの言語レベルとは独立して扱う)。
- **配布形態**: 単一 fat jar (Gradle Shadow plugin)。Core は `java -jar <path>` の 1 コマンドで起動できる。
- **実装言語**: Java を維持する (Kotlin を検討した上での判断)。JDK 25 の sealed interface + record + pattern matching で Kotlin を採用した場合の主利点 (代数的データ型、網羅性検査) が Java 単体で得られること、JavaParser との interop では Kotlin の null 安全が platform type で効かず利点が薄れること、将来の「Kotlin Analyzer」との命名混乱を避けられることが理由。

### 内部 package 構成と依存境界

`javaanalyzer` 配下の内部構成を定める (判断を定めるのは [ADR-0007](../../../adr/0007-layered-architecture-refactor.md))。

- 直下の `protocol` (wire DTO) / `io` (JSONL 入出力) / `preflight` (入力検証) / `discovery` (Gradle Tooling API 隔離) は入出力・起動系として維持する。
- `analysis` 配下は解析パイプラインの段階別 package で構成し、**段階の実行順は `analysis/pipeline` (AnalysisRunner) だけが知る**。実行順: scope 列挙 → context 構築 (JavaParser + augment) → attribution 準備 → sootup 型階層 index → spring DI index → graph 構築 → completeness 検査 → io 出力。`normalize` は段階横断の naming util。
- 外部ライブラリの隔離は 3 段階: **SootUp** は `analysis/sootup` (adapter) に完全封じ込め (facade が自前型で公開し、他 package から `sootup.*` を import しない)。**Gradle Tooling API** は `discovery` に完全隔離。**JavaParser / SymbolSolver** は解析エンジンの中核として `analysis` 配下では自由に使い、`analysis` の外へは漏らさない。
- 依存境界は ArchUnit の JUnit テストで機械検査する (quality gate は [engineering.md](../../../context/engineering.md))。

### 起動契約

Core は `--analyzer-cmd` (CLI flag) → `DEPWALK_ANALYZER_CMD` (環境変数) の順で Analyzer 起動コマンド文字列を解決する。どちらも指定が無い場合は実行前に validation error で拒否する。Core は解決した文字列を **shell を介さず shell-word 分割して exec** する (shell injection を避ける)。Core は `java` / jar / JVM の存在を知らず、言語固有の分岐と path 解決規約を持ち込まない。判断は [ADR-0003](../../../adr/0003-analyzer-command-resolution.md)。

metadata passthrough も同様の言語非依存原則に従う。Core は `--analyzer-meta key=value` で `analysisRequest.metadata` へ素通しするだけで、key / value の意味を解釈しない。

### analysisMode の意味論

`fullGraph` と `reachableFromEntrypoints` の両方を実装する。

| モード                     | 出力範囲                                                                                                     |
| -------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `fullGraph`                | scope (`include` / `exclude` 適用後) 内の全 `methodSymbol` と、その間の全 `callEdge`                         |
| `reachableFromEntrypoints` | `entrypoints` から呼び出し先 (callee) 方向に推移的に到達する `methodSymbol` と、それらの間の `callEdge` のみ |

- `entrypoints` が未指定または空配列の場合は、`analysisMode` の値によらず scope 全体の call graph 生成要求として扱う。
- node 母集合 (どのメソッドを `methodSymbol` として出すか) の列挙方法は「帰属型の決定規則」節が定める。
- caller 探索 (S1) の入力としては `reachableFromEntrypoints` は不完全であるため、caller 方向の問い合わせでは Core が `fullGraph` を選ぶ責務を持つ (本 doc は Java Analyzer 側の意味論を定めるものであり、Core の振る舞いは参照)。`reachableFromEntrypoints` は callee 方向の調査で出力量を削るための最適化と位置づける。

### 性能方針

- **モード別の streaming 方針**: `reachableFromEntrypoints` は entrypoints からの到達判定に解析完了までの adjacency 全体が必要であり、streaming と両立しない。このためモードごとに挙動を分ける。
  - `fullGraph`: ファイル単位で `methodSymbol` / `callEdge` を逐次 stdout へ flush し、解析済みファイルの中間状態 (AST 等) を保持しない。出力済み `methodId` 集合の保持は許容する。
  - `reachableFromEntrypoints`: 到達判定のため、解析完了まで adjacency (呼び出し関係) を保持したうえで到達集合を確定し、その後に出力する二段階処理を **モード別の例外** として許容する。
  - `diagnostic` は両モードとも検出時に即時 flush する (中間保持しない)。
- **AST の逐次破棄**: 解析済みファイルの AST を保持し続けない。保持するのは SymbolSolver の型解決キャッシュと、`callEdge` 出力に必要な最小限の情報 (`fullGraph` は逐次 flush 用、`reachableFromEntrypoints` は到達判定用の adjacency)。
- **計測の観測性**: 解析ファイル数 / 所要時間 / 未解決件数を stderr に出力する (protocol record としては出さない)。
- **メモリ特性の扱い**: `fullGraph` と `reachableFromEntrypoints` は adjacency 保持の有無でメモリ特性が異なるため、数値目標はモード別に扱う。
- **SootUp の view 構築は lazy に行う**。型階層解決に必要なクラスだけを読み込み、eager な全クラス読み込みをしない。

#### 計測の手順

明示 single-root、自動 single-project、自動 multi-project の 3 経路を、初回 1 回と warm 3 回の中央値で測る。discovery / model / parse / resolution / graph の phase 別時間を stderr へ記録する。

数値目標は実測から次の規則で導く。

- latency: warm wall 中央値の 1.5 倍を 0.5 秒単位で切り上げ
- 最大 RSS: warm 3 回の最大値の 1.25 倍を 64 MiB 単位で切り上げ

小規模 fixture では JVM 起動と Gradle daemon の寄与が大きいため、値は「小規模プロジェクトでの下限に近い値」として扱う。

#### 経路別 SLO

| 経路                      | 初回 wall | warm wall 3 回        | warm 中央値 | warm 最大 RSS                    | latency SLO | 最大 RSS SLO |
| ------------------------- | --------- | --------------------- | ----------- | -------------------------------- | ----------- | ------------ |
| 明示 single-root (8 file) | 1.78s     | 1.71s / 1.81s / 1.69s | 1.71s       | 418,873,344 bytes (約 399.5 MiB) | 3.0s 以下   | 512 MiB 以下 |
| single-project discovery  | 3.52s     | 3.00s / 2.52s / 2.73s | 2.73s       | 445,415,424 bytes (約 424.8 MiB) | 4.5s 以下   | 576 MiB 以下 |
| multi-module discovery    | 3.11s     | 2.51s / 2.64s / 2.49s | 2.51s       | 399,687,680 bytes (約 381.2 MiB) | 4.0s 以下   | 512 MiB 以下 |

この SLO は特定の fixture と計測環境に対する手動のリリース目標であり、**時間や RSS の機械的なテスト gate にはしない**。S1 から S3 の CLI E2E を required gate とする方針は維持する。fixture の規模、Analyzer runtime JDK、Gradle major が変わったら測り直す。

### 段階導入

| 段階                 | 範囲                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 静的呼び出し抽出     | JavaParser + SymbolSolver による静的呼び出し抽出 (実装済み)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| SootUp / Spring 解析 | SootUp による型階層 / Interface Dispatch / Override 候補の補完と、Spring Bean / DI 解決による候補絞り込み (S4 の達成)。実装は型階層補完 → Spring 候補絞り込み → 統合 E2E の順に分割する ([ADR-0005](../../../adr/0005-adopt-sootup-and-spring-di-resolution.md))。**SootUp の統合範囲は決定済み (2026-07-12)**: 型階層・override・interface 実装候補の索引としてのみ使用し、call graph 生成は任せない。SootUp の view 構築は lazy に行い、型階層解決に必要なクラスのみ読み込む (性能方針節を参照)。**Lombok 生成コンストラクタの解決**: `@AllArgsConstructor` / `@RequiredArgsConstructor` 等 Lombok が生成する constructor は source (JavaParser) からは見えないため、SootUp の bytecode 型階層照会対象に自プロジェクトのコンパイル済み class を含めて解決する。これに伴い、解析対象プロジェクトは解析時点でコンパイル済み (`.class` 生成済み) であることを前提とする (未ビルド時は E3 の一般規則で degrade する)。この自プロジェクトのコンパイル済み class も、既存の解析対象ソース・依存 jar と同様に読み取り専用として扱う (書き込み・実行はしない)。 |

Reflection / AspectJ Runtime / 実行時 Proxy 等、実行時状態で初めて確定する呼び出しの完全追跡は初期スコープに含めない ([ADR-0004](../../../adr/0004-defer-runtime-call-tracing.md))。静的に候補を導ける場合は候補と根拠を出力し、確定できない場合は候補・未解決理由を観測可能にする。

- **Spring 条件アノテーション**: `@Profile` / `@ConditionalOnProperty` 等の条件アノテーションは条件評価を行わず、検出・記録のみを行う。条件付き Bean も無条件に候補として列挙し、「条件付きである」事実と条件種別を metadata / diagnostic に記録する。条件付き候補を含む場合は候補が 1 件でも `resolution: unique` とはせず曖昧候補として扱う。
- **実行時生成実装 (マーカー対象は後続 issue で拡張)**: Spring Data 等の実行時生成実装は宣言メソッドへの edge のみを保持し、疑似実装ノードは合成しない。既知の runtime-provided マーカーは Spring Data `Repository` 型階層に加え、MyBatis `@Mapper` インターフェース (フレームワークによるランタイムプロキシ生成でソースに実装クラスが存在しない点で Spring Data と同構造、決定済み 2026-07-14) を対象とする。マーカーに合致する場合は diagnostic の理由を「未解決」ではなく「runtime-provided」として区別する。`@FeignClient` 等その他フレームワークへの拡張は引き続き後続とする。

## 主要シナリオ / フロー

### 起動から結果を返すまで

Core が Analyzer を 1 回呼ぶと何が起きるかの全体像。各段の詳細は子 doc が持つ。

```mermaid
sequenceDiagram
    participant Core
    participant Analyzer as Java Analyzer
    participant Gradle as Gradle (build model)
    participant FS as 解析対象のソース / .class

    Core->>Analyzer: process 起動 + stdin へ analysisRequest を 1 件送って close
    Analyzer->>Analyzer: 入力検証 (preflight)

    alt sourceRoots の明示あり
        Note over Analyzer: Gradle を呼ばない (discovery を bypass)
    else 明示なし
        Analyzer->>Gradle: build model を問い合わせ
        Gradle-->>Analyzer: source root / classpath / classes output
        Note over Analyzer,Gradle: 対象の build logic が評価される<br/>(discovery.md の安全境界)
    end

    Analyzer->>FS: ソースを読む (read-only)
    Analyzer->>Analyzer: 1. JavaParser で AST 化
    Analyzer->>FS: .class を読む (型解決 / bytecode member)
    Analyzer->>Analyzer: 2. SymbolSolver で型を解決
    Analyzer->>Analyzer: 3. SootUp と Spring で実装候補を絞る

    loop 解析できたものから
        Analyzer-->>Core: stdout へ methodSymbol / callEdge を JSONL で逐次出力
    end
    Analyzer-->>Core: 非致命の問題は diagnostic を逐次出力

    alt 未解決が残らない
        Analyzer-->>Core: exit 0
    else 未解決が残る / 解析不能
        Analyzer-->>Core: error record を出力して非ゼロ exit
        Note over Core: Core は先行 record ごと破棄する
    end
```

出力は**逐次**である。解析が全部終わってからまとめて返すのではなく、確定したものから流す。ただし `reachableFromEntrypoints` だけは到達判定に全体が要るため例外で、詳細は「analysisMode の意味論」節に置く。

### 個別の規則

- Core が Analyzer process を起動し stdin へ `analysisRequest` を 1 件送信して close する。Java Analyzer は対象 Java ソースを read-only で解析し、結果を stdout へ JSONL で逐次出力する。
- 呼び出し先の型が解決できたとき、`methodSymbol` (caller / callee 双方) と、両者を参照する `callEdge` を出力する。
- 呼び出し先が interface / 抽象メソッドであるとき、帰属型の決定規則で決まる帰属型のメソッドを callee として `callEdge` を出力し、`callEdge.metadata.dispatch` に dispatch 種別を標識する。
- 呼び出し先メソッドの宣言サイトが scope 外で、その宣言型が引き上げ除外 package に属するとき、`methodSymbol` / `callEdge` を出力しない (解析失敗ではないため `diagnostic` も出さない)。
- allowlist された resolution failure は call outcome ledger に候補・理由を記録して解析を継続するが、全救済後も primary diagnostic が残る request は `JAVA_INCOMPLETE_ANALYSIS` で fatal にする。
- 個別ファイルがパース不能なときは graph record を1件も確定せず、決定順で最初の parse failure を `JAVA_PARSE_ERROR` の location / message として返して fatal にする。
- 解析を継続できない致命的な問題が起きたとき、`error` record を出力し、非ゼロ exit code で終了する。

### Interface Dispatch / Spring DI 解決フロー

```mermaid
flowchart TD
    Parse["JavaParser: call site と宣言型 edge を生成"] --> Hierarchy["SootUp: 型階層と override 候補を索引化"]
    Hierarchy --> Beans["Spring: 型で Bean 候補を列挙"]
    Beans --> Qualifier{"@Qualifier 指定あり?"}
    Qualifier -->|あり| Filter["qualifier value / Bean 名 / alias で絞り込み"]
    Qualifier -->|なし| Count
    Filter --> Count{"候補数は?"}
    Count -->|0件| Unresolved["unresolved または runtime-provided diagnostic"]
    Count -->|1件| Unique["resolution=unique"]
    Count -->|複数件| Primary{"@Primary の候補数は?"}
    Primary -->|1件| PrimaryCondition{"唯一の @Primary は<br/>条件付き?"}
    PrimaryCondition -->|はい| Ambiguous
    PrimaryCondition -->|いいえ| Unique
    Primary -->|0件または複数件| Ambiguous["resolution=ambiguous"]
    Unique --> Conditional{"条件付き候補を含む?"}
    Conditional -->|はい| Ambiguous
    Conditional -->|いいえ| Merge
    Ambiguous --> Merge["宣言型 edge を保持し候補 edge を edgeId 単位で統合"]
    Unresolved --> Merge
    Merge --> Output["methodSymbol / callEdge / diagnostic を JSONL 出力"]
```

SootUp は edge を直接生成せず候補索引だけを提供する。Spring の絞り込み後に JavaParser 側で候補 edge を生成し、同一 edge の provenance を統合する。

## テスト観点

横断規約は [context/testing.md](../../../context/testing.md)。本 feature は三層 (Java unit / Go fake analyzer / 実 jar E2E) で保証する。

**観測責務の境界**: 曖昧性・解決根拠の観測は Analyzer JSONL (`callEdge.metadata` / `diagnostic`) までを本 feature の責務とする。CLI 出力への edge 単位 metadata 表出は [CLI feature doc](../cli/DesignDoc_cli.md) が管轄する。本 doc が定める。

**Java unit test (JUnit / `analyzers/java/`)**

- signature / `methodId` の正規化 (overload / generics erasure / varargs / nested class (`$`) / constructor (`<init>`) / static initializer (`<clinit>`) / 匿名クラス採番の決定性)
- `symbolKind` の割り当て (インスタンス初期化子・フィールド初期化子が constructor に畳み込まれること、lambda 内の呼び出しが囲みメソッドに帰属し `viaLambda: true` が立つこと)
- 帰属型の決定規則 (宣言サイト scope 内 (override あり / なし)、scope 外宣言の引き上げ、除外 package (既定値と `liftExcludePackages` による置き換え、segment 単位 prefix 一致)、`this` / `super` / static / `new` の各形、`metadata.dispatch` の値)
- `diagnostic` / `error` の code と severity の対応、pre-flight 検査 (classpath key 不在 / 明示 classpath entry 欠落・読取不能 / `language != "java"`) が解析開始前に fatal になること
- explicit / auto の排他 validation、root 正規化・重複・包含・realpath 境界、custom model、main source set、project dependency 到達性、context 別 language level / preview
- parse pre-flight、allowlist 外 resolver fatal、atomic mutation、call inventory / outcome ledger、initializer caller 展開、`silentOmission == 0`、共通 failure details
- call-site driven project bytecode member index、bytecode-only member の location 省略と owner metadata、Graph deep copy
- `fullGraph` / `reachableFromEntrypoints` の出力範囲 (宣言列挙 ∪ call site 由来、entrypoints 空は全体扱い)

**Go 側 process contract (fake analyzer / JVM 不要)**

- `--analyzer-cmd` / `DEPWALK_ANALYZER_CMD` の解決順序と、どちらも無い場合の実行前拒否
- `--analyzer-meta key=value` の合成規則 (1 回指定 → 要素 1 の配列、繰り返し → 指定順の配列、空値 (`key=`) → 空配列、`=` なし → validation error、value に `=` を含む指定 → 最初の `=` で分割)
- shell を介さない shell-word 分割で exec されること
- 既存の contract test 観点 (stdin close / 逐次 parse / stderr 非 parse / exit code) を再利用する

**E2E (実 jar / `testdata/fixtures/java/`)**

- 既知の caller / callee 集合と解析結果 graph の照合 (S1 / S2 の入力層)。CLI 出力レベルの照合は CLI 層が担う
- interface 注入を含むサンプルで、宣言型 (interface) のメソッドが callee に現れ `dispatch: interface` が立つこと (S4 の前段)
- パース不能ファイルを混ぜた fixture で、`JAVA_PARSE_ERROR` が決定順で最初のfailure detailを返し graph / diagnostic を公開しないこと
- 未解決 symbol を含む fixture で、救済できない primary outcome が `JAVA_INCOMPLETE_ANALYSIS` の全 detail を返し、不完全 graph を成功させないこと
- **完全性 gate の opt-in 緩和**: `metadata.allowIncompleteAnalysis=["true"]` のとき、primary diagnostic が残っていても exit 0 で成功し、解決済み edge / node と診断 (`diagnostic` record) が公開されること。`callSiteSummary` の `diagnostic[...]` 集計が残存件数と一致し `silentOmission=0` を維持すること。不正な値 (`["true"]` / `["false"]` 以外) は `JAVA_INVALID_REQUEST` で fatal になること
- app / service / repository の3 project、変更した `projectDir`、custom source dir、project 間 call / DI を含む fixture で、自動 discovery と明示 override の graph が一致すること
- test-only 透過 proxy を介して実 Core CLI と実 Analyzer jarを接続し、request、raw graph、CLI終了状態を required gate で照合すること
- Gradle `7.6.5`〜`9.6.x` と daemon JVM anchor matrix、Gradle output discard、credential / URL / absolute path を含む negative fixture の非漏洩を検証すること
- **Spring Boot fixture**: `testdata/fixtures/java/spring-project/` に単一 source root の Spring fixture を配置した。DI (constructor / field / setter injection)、stereotype、`@Qualifier`、`@Primary`、条件付き Bean (`@Profile` / `@ConditionalOnProperty`)、Spring Data Repository を含む。
- **Lombok / MyBatis Mapper 拡張**: 上記 fixture に、コンストラクタを明示せず Lombok (`@AllArgsConstructor` / `@RequiredArgsConstructor` 等) で生成するクラス と、MyBatis `@Mapper` インターフェース を含める。前者は自プロジェクトのコンパイル済み class を通じた constructor injection 解決を、後者は runtime-provided マーカー検出を検証する。
- **未解決 call パターン fixture**: `testdata/fixtures/java/multi-module-spring-project/` へ、実環境実測の上位未解決パターン (lambda / generic を含む fluent chain、`var` + generic メソッド戻り値、method reference、explicit `super(...)`、cross-module の Lombok 生成 member 呼び出し) の最小再現ケースを追加する。救済修正後の回帰検証と、`JAVA_INCOMPLETE_ANALYSIS` 時の診断 metadata 4 項目 (sanitize 制約含む) の期待値検証に使う。
- **fixture build / classpath 契約**: `testdata/fixtures/java/spring-project/` は独立した Gradle project とし、repository の `analyzers/java/gradlew -p` で build する。fixture の `build.gradle.kts` は Java toolchain 25、`options.release=21`、Spring Boot Autoconfigure 4.1.0、Spring Data Commons 4.1.0、MyBatis 3.5.19、Lombok 1.18.46 を固定する。`writeDepwalkClasspath` task が `build/classes/java/main` と `runtimeClasspath` の jar を絶対 path・辞書順・1 行 1 entry で `build/depwalk-classpath.txt` へ書き、Go E2E は全行を `analysisRequest.metadata.classpath` に渡す。Lombok の生成 constructor は `classes` task 後の `.class` で検証する。
