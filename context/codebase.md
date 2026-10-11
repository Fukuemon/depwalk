---
type: context
title: コードベースの構造
description: Core と Analyzer の境界、Core の package の依存の向き、Java Analyzer の import の範囲、staging Graph とエラーの分類の置き場。コードから読み取れる構造は書かない
status: stable
governs:
  - core/internal
  - core/cmd
  - core/.golangci.yml
  - analyzers/java/src/main
  - analyzers/java/src/test/java/com/fukuemon/depwalk/javaanalyzer/ArchitectureTest.java
verified_commit: unverified
---

# コードベースの構造

## Module Boundaries

depwalk は、言語によらない Core と、言語ごとの Analyzer に分ける。  
Core を言語非依存に保ち、言語ごとの差を Analyzer の側に閉じ込めるためである。  
モジュールごとの責務と設計原則は [Design Doc の Components](../design/DesignDoc.md#components) にある。

- Core と Analyzer は別のプロセスで動き、STDIN と STDOUT の上の JSONL で通信する。
  - 形式は [Design Doc の Architecture](../design/DesignDoc.md#architecture) と [Analyzer Protocol の Design Doc](../core/internal/protocol/DesignDoc.md) にある。
  - ADR-0001: [Analyzer Protocol を JSONL の process SPI にする](../adr/0001-analyzer-protocol-jsonl-spi.md)
- Core は Go のランタイムで動く CLI のバイナリにする。Analyzer は対象の言語のランタイムで動く。Java Analyzer は JVM で動く。
- 解析は静的解析だけで行い、実行時の情報と runtime trace に依存しない。
  - 対象外にする範囲は [Design Doc の Non-Goals](../design/DesignDoc.md#non-goals) にある。
- Java Analyzer が source root を自動で discovery するときだけ、次の 3 つが動く。
  - Gradle Tooling API
  - 対象の build の Gradle daemon
  - workspace の外に置く一時の custom model provider
- 自動 discovery では、Gradle の build logic が利用者の権限で評価され、network、credential provider、cache、任意の副作用を持ちうる。
- source root を明示すると、Gradle Tooling API、Gradle daemon、custom model provider のどれも動かない。
- 自動 discovery のときも、Core と Analyzer の Protocol の境界は言語非依存のまま変えない。
  - 副作用の扱いは [operations.md](operations.md#environments) にある。
- Core と Analyzer が共有してよいのは、Protocol の文書、ADR、JSONL の fixture、contract テストの観点だけにする。Go の package と Java のコードは共有しない。

Core の `core/internal` は、責務の名前を付けたフラットな package で作る。  
層（domain、app、platform）は概念としてだけ持ち、ディレクトリにしない。  
ADR-0007: [層の依存境界を ACL と機械のチェックで固定する](../adr/0007-layered-architecture-refactor.md)

- domain: `graph`、`traversal`
- app: `analyze`。`depwalk analyze` の use case をまとめ、use case が使う port の interface を定義する。
- platform: `protocol`、`analyzer`、`output`、`cli`
  - `protocol` は JSONL の wire の DTO を parse と validate し、wire の record を domain の値へ写す腐敗防止層（ACL）である。`analyze` の port を実装する。
  - `analyzer` は、外部の Analyzer のプロセスを起動し、stdin、stdout、stderr、終了コードを扱う。
  - `cli` は、コマンド、flag、入力の検証と、手動の DI の配線を持つ合成ルートである。
- `core/cmd/depwalk`: `main` で、Cobra の root command を起動するだけにする。
- `core/internal/graph/graphtest`: graph のテストを支える sub-package。本番のコードから import しない。

Java Analyzer の `javaanalyzer` の下は、解析の段階ごとの package（`analysis/` の下）と、入出力と起動の package（`protocol`、`io`、`preflight`、`discovery`）で作る。  
段階の実行の順を知るのは、`analysis/pipeline` の Runner だけにする。  
package の構成は [Java Analyzer の Design Doc](../analyzers/java/DesignDoc.md#内部の-package-構成と外部ライブラリの隔離) にある。

## Dependency Rules

依存の向きは、Core の中では一方向にし、Core から Analyzer へは Protocol の境界だけを通す。

- CLI は Core だけに依存する。
- Core から Analyzer へは、Analyzer SPI（Protocol の境界）だけを通す。Core は、Analyzer が使うライブラリと言語のランタイムを知らない。
- Analyzer は Model（`MethodSymbol`、`CallEdge`、`SourceLocation`）のスキーマにだけ依存し、Core の内部の実装に依存しない。
- Core から、特定の言語のランタイムと Analyzer の実装へ直接依存しない。2 つ目以降の Analyzer を足したときに、Core を変えずに済むようにするためである。
  - 最初の Analyzer を入れたときの、言語によらない最初の配線は、この規則の対象外にする。
- Output は、traversal の request と result の型を使うために Traversal に依存する。Traversal から Output へは依存しない。循環するためである。

`core/internal` の package ごとの規則は次のとおり。

- `graph`: 他の internal の package に依存しない。wire の表現である `protocol` も import しない。
- `traversal`: `graph` だけに依存する。
- `graph/graphtest`: `graph` だけに依存する。
- `analyze`: `graph` と `traversal` だけに依存する。`protocol`、`analyzer`、`output`、`cli` を import しない。抽象は `analyze` の port の interface で表し、`protocol` が実装する。
- `output`: `graph` と `traversal` だけに依存する。
- `protocol`: `analyze`（port の実装）、`analyzer`（プロセスの起動）、`graph` に依存する。
- `analyzer`: 他の internal の package に依存しない。
- `cli`: 合成ルートとして、すべての package を import してよい。最も外の層の役割で、依存の規則の例外とは扱わない。
- `core/cmd/depwalk`: `cli` だけに依存する。内側の層を直接 import しない。
- DI のライブラリ（`google/wire` など）は入れない。`cli` でコンストラクタに渡す手動の DI にする。

Core の規則は、golangci-lint の depguard でチェックする。

- 設定は `core/.golangci.yml` にある。規則を変えるときは、先にこの節を直し、次に設定を直す。
- ルールは、package の glob（`files`）、禁止する import（`deny`）、違反の理由（`desc`）で書く。違反したときに `desc` が表示され、開発者もコーディングエージェントもメッセージから直せる。
- `deny` の `pkg` は、末尾の `$` で完全一致にする。前方一致にすると、`analyze` の規則が `analyzer` まで巻き込む。また、black-box テストの package が自分の package を import できなくなる。
- CI の Go の job は、`go list -deps ./...` に `github.com/Fukuemon/depwalk/analyzers/` が含まれないことを確かめ、Core が Analyzer に依存していないことを見る。設定は `.github/workflows/ci.yml` にある。

Java Analyzer が外部のライブラリを import してよい範囲は、次の 3 つ。  
ArchUnit のテスト `ArchitectureTest` でチェックし、`./gradlew test` で実行される。

- SootUp: `analysis/sootup` の adapter にだけ閉じ込め、facade が自前の型で公開する。他の package は `sootup.*` を import しない。
- Gradle Tooling API: `discovery` にだけ閉じ込める。`org.gradle.*` を import してよいのは `discovery` だけにする。
  - `org.gradle.tooling.*` だけに絞らず、`org.gradle.*` 全体を禁じる。gradle-tooling-api の jar は `org.gradle.api`、`util`、`internal` も同梱するため、tooling の下だけを禁じると隔離を抜けられる。
- JavaParser と SymbolSolver: 解析の中心として、`analysis` の下では自由に使ってよい。`analysis` の外では `com.github.javaparser.*` を import しない。

## State Ownership

- Analyzer は、解析の対象のソースを読み取り専用で扱う。depwalk は対象のリポジトリを書き換えない。
  - 自動 discovery では、対象の build logic の評価に伴う Gradle のランタイム全体の副作用を、depwalk の読み取り専用の契約とは別の境界として扱う。
- 解析の途中の状態は、request ごとの非公開の staging Graph として Core のプロセスの中に持つ。永続的なストアは持たない。
  - `analyze` の use case は、port から受け取った domain の値を staging Graph へ 1 回の走査で登録する。
  - stream 全体の参照の完全性は、wire の record を見る `protocol`（ACL）が確かめる。
  - use case は、参照の完全性のチェックと Analyzer のプロセスの成功を確かめたときだけ Graph を公開する。fatal か非ゼロの終了のときは、Graph と先に受け取った diagnostic をまとめて捨てる。
  - 公開までの原子性は [Graph の Design Doc](../core/internal/graph/DesignDoc.md#構築と公開の原子性) にある。
- graph は、wire の DTO と、wire にだけあるフィールド（`schemaVersion`、`recordType`）を持たない。node と edge が持つ属性は [Graph の Design Doc](../core/internal/graph/DesignDoc.md) にある。

## Placement Rules

- 言語ごとの Analyzer は `analyzers/<language>/` に置く。Java Analyzer は `analyzers/java/` に置き、Core の `internal` の package に入れない。
- Core の Protocol の実装は `core/internal/protocol` に、Analyzer のプロセスとの境界は `core/internal/analyzer` に置く。
- 新しい package を `core/internal` に足すときは、この文書の依存の規則と `core/.golangci.yml` に、その package の規則を足す。規則のない package が internal の package を import すると、depguard の catch-all のルールが拒否する。
- 複数の package のテストから使う fixture の builder は、対象の package の下のテスト支援の sub-package に置く。
  - 例は `core/internal/graph/graphtest` で、Go の標準の `net/http/httptest` と同じ形にする。
  - 本番の package の公開の範囲をテストの都合で広げないために分ける。本番のコードから import しない。depguard がチェックする。

エラーの分類は、次の場所に置く。

- 終了コードへの分類は `cli` が決める。内側の層は、利用者の入力が原因であることを型で示すだけにし、終了コードの値を知らない。
  - `cli.ExitCode` が、`analyze.InputError` と `cli` 自身の入力のエラーの両方を 2 へ写す。
- エラーメッセージの package の prefix（`traversal:`、`output:`、`analyze:` など）は、呼び出し側の実装の誤りを表す内部の不変条件の違反にだけ付ける。
  - これらの違反は `cli` が先に検証するため、利用者には届かない。利用者の stderr に出るメッセージには、内部の package の名前を出さない。
- プロセスの結果を struct のフィールドの `error` で運ぶときは、判定をその型のメソッドにまとめる。
  - `analyze.Outcome` は、fatal の record、非ゼロの終了、stdout の検証のエラーの 3 つを持つ。fatal の理由を検証のエラーで上書きしない優先順位を、`Outcome.Err()` にまとめる。呼び出し側に優先順位を書かせない。
- 型で分類する必要のあるエラーは struct で定義し、`errors.As` で調べられるようにする。
  - 例: `protocol.ValidationError` は、contract テストが型として検証する。
  - 文言を組み立てるだけのエラーは `fmt.Errorf` でよい。
