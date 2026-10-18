# ADR-0007: Core と Java Analyzer の依存境界を ACL と機械のチェックで固定し、Core はフラットな package 構成のまま保つ

## Status

承認

## Context

depwalk は、Core（Go）と言語ごとの Analyzer を、プロセスと JSONL の Protocol で分けている。  
この外側の境界は機能している。  
ADR-0001: [Core と Analyzer を別プロセスにし、JSONL over STDIN/STDOUT の process SPI で結合する](0001-analyzer-protocol-jsonl-spi.md)

一方で、それぞれの内部は機能ごとの package を並べただけになっている。  
Core の最初の package 構成は、ADR-0002 で決めた。  
ADR-0002: [Core を Go と Go modules で実装し、依存を Cobra と標準ライブラリに絞る](0002-core-implementation-foundation.md)

import を測ると、内部に次の 4 つの問題があった。

- `core/internal/protocol` に依存が集まっている。
  - `graph`、`output`、`cli`、`analyze`、`analyzer` の 5 つの package が直接依存している。
  - wire の表現（JSONL の DTO）がドメインの側へ漏れ、`graph -> protocol` の import がある。
- `core/internal/cli` が、use case の `analyze` だけでなく、`graph`、`output`、`protocol` にも直接依存し、エントリポイントが内側の層を迂回して参照している。
- Java Analyzer は `analysis` の下に 9 つの sub-package を並べている。
  - 解析のパイプラインの段階と、外部ライブラリへの依存の境界を、構造から読み取れない。
  - SootUp は `SootUpTypeHierarchyIndex` の 1 ファイルに閉じ込めてあるが、機械のチェックがないため、隔離が保たれることを保証できない。
- 層の区別を、ディレクトリの構造からも import の規約からも読み取れず、開発者とコーディングエージェントが依存の向きを誤りやすい。

再編は、外部の挙動（CLI、JSONL の Protocol、出力の形式、exit code）を変えないリファクタリングとして行い、次の 5 つの規則を満たす必要がある。

- 依存の向きは内向きの一方向（platform → app → domain）にし、domain は他の層に依存しない。
  - クリーンアーキテクチャの基本の原則で、Analyzer を独立したプロセスにして共通の Protocol で結ぶ [Design Doc](../design/DesignDoc.md) の設計原則と整合する。
- wire の表現（Protocol の DTO）は境界の変換層でドメインのモデルへ写し、内側の層に持ち込まない。
  - graph が wire の DTO を持たないという [codebase.md の State Ownership](../context/codebase.md#state-ownership) の規約を、実装の水準で保証するためである。
- 外部ライブラリ（Cobra、SootUp、Gradle Tooling API）への依存は、境界の側に隔離する。
  - 将来のライブラリの差し替えと、テストのしやすさのためである。
- 再編は外部の挙動を変えず、E2E と golden のテストが変更なしで通る。
  - リファクタリングが安全であることを確かめるためである。
- 層をまたぐ禁止の import は機械のチェックで検出し、CI で通すべきチェックに組み込む。
  - 人のレビューに頼ると regression するためである。

新しい機能、既存のロジックのアルゴリズムの変更、Core と Analyzer の間のプロセスの境界の変更、`analyzers/java` の Gradle の build の構成の変更は、この再編の対象にしない。  
package の移動に伴う機械的な追随だけは行う。

## Decision

### 1. Core の `core/internal` はフラットな責務名の package のまま保ち、層のディレクトリを作らない

問題は、package の間の実際の依存の辺が見えず、強制もされないことにある。  
層のディレクトリは、この問題に対して粗すぎる。  
層は 3 つの分類の順序しか示さず、`graph` と `traversal` の関係のような実際の辺は見えないままだからである。  
代わりに、次の 3 つで依存を見えるようにし、強制する。

- package ごとの依存の規則を depguard の deny のルールで宣言し、CI と pre-commit で強制する。
  - 層の glob より細かく規則を書ける。
  - 規則は [codebase.md の Dependency Rules](../context/codebase.md#dependency-rules) にある。
- `go list` の実際の import から package の依存図（mermaid）を生成するスクリプトを置く。
  - 手で描く図と違って古くならず、再生成の diff を調べれば実態とのずれも検出できる。
  - 生成した図は [Design Doc の Core の package の依存](../design/DesignDoc.md#core-の-package-の依存) にある。
- 配線（手動の DI と、`var _` による interface を満たすことの検証）を `cli` の合成ルートに集め、実際の依存のグラフを 1 か所で読めるようにする。

層（domain、app、platform に当たるもの）は概念として保ち、package との対応を [codebase.md の Module Boundaries](../context/codebase.md#module-boundaries) に示す。  
ディレクトリには焼き付けない。  
`output` は presenter の層として独立させない。

### 2. wire の変換を `protocol` の腐敗防止層（ACL）に閉じ、`analyze` は port で受ける

- `graph` は自前の `Symbol` と `SourceLocation` の値型を持ち、`protocol` への import をなくす。
  - wire の型と定義が重複することは、境界を隔離するコストとして許す。
  - 「protocol の型を再利用する」という以前の決定は、[Graph の Design Doc](../core/internal/graph/DesignDoc.md) で改めた。
- `analyze` は、domain の型（graph の値型）を返す port の interface を、利用する側のファイルの中に小さく定義する。
  - `port/` 専用の package は作らない。
  - `analyze` 自身は struct として公開し、先回りの interface を作らない。
  - 出典: [Go の設計、どこまでやる？](https://zenn.dev/135yshr/books/go-service-design)
- `protocol` は ACL として wire の DTO を内側に閉じ、Translator（wire から domain への変換）と Adapter（port の実装）を担う。

### 3. 配線は `cli` でのコンストラクタ注入による手動の DI にする

- `google/wire` などの DI のライブラリとコードの生成は入れない。
- `var _ Interface = (*Impl)(nil)` による interface を満たすことの検証も、`cli` に集める。

### 4. Java Analyzer は `analysis` を段階ごとの package にし、外部ライブラリを 3 段で隔離する

- `javaanalyzer` の直下（`protocol`、`io`、`preflight`、`discovery`）はそのままにする。
- `analysis` の下は段階ごとの package で作り、実行の順は `analysis/pipeline` だけが知る。
- 外部ライブラリの隔離は 3 段にする。
  - SootUp は `analysis/sootup`（自前の型で公開する adapter の facade）へ完全に閉じ込める。
  - Gradle Tooling API は `discovery` へ完全に隔離する。
  - JavaParser と SymbolSolver は、解析エンジンの中心として `analysis` の下では使ってよく、外へ漏れることだけを禁じる。
  - package の構成は [Java Analyzer の Design Doc](../analyzers/java/DesignDoc.md) にある。

### 5. 依存の向きを、Go は depguard、Java は ArchUnit で機械的にチェックする

- Go: golangci-lint と depguard を入れ、package ごとの禁止の import をチェックし、lefthook の pre-commit と CI に組み込む。
- Java: ArchUnit を test の依存に足し、外部ライブラリの隔離のルールを JUnit のテストとして書き、既存の `./gradlew test` で実行する。

## Considered Options

### Core の package の構成

| 選択肢                                                                                                          | 強み                                                                                                                                                                                                                                   | 弱み                                                                                                                                                                                                                                                                                              |
| --------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **採用:** フラットな責務名の package のまま、depguard、生成した依存図、`cli` の合成ルートで依存を見せて強制する | 生成した依存図、depguard の理由つきのエラー、`cli` の配線のコードの 3 つから実際の依存を判別できる。物理的な移動がなく、既存の path と既存の branch に影響しない。depguard は package ごとのルールで、層の glob と同等以上に強制できる | パスを見るだけでは層の粗い順序を読めない。依存図を生成するスクリプトという保守の対象が 1 つ増える。golangci-lint という dev の依存が増え、バージョンの固定の保守が要る                                                                                                                            |
| 機械的な層の名前（`domain`、`usecase`、`infra`）を使う                                                          | —                                                                                                                                                                                                                                      | クリーンアーキテクチャで唯一絶対なのは依存の内向きの方向で、層の名前と数は自由である。Go のコミュニティは機械的な層の名前を避ける傾向があり、`app` と `platform` で同じ構造の読みやすさが得られる                                                                                                 |
| 3 つの層のディレクトリ（`domain/`、`app/`、`platform/`）へ物理的に移す                                          | 層の判別が文書に依存しない                                                                                                                                                                                                             | 知りたいのは実際の依存の辺で、層のディレクトリは 3 分類の粗い順序しか示さない。package の名前は責務名のままなので、import path が深くなること、既存の branch との conflict、文書の 17 か所の追随に見合う情報が増えない。layer-first を避けて責務名の package を保つ Go の設計の慣行とも整合しない |
| `output` を presenter として独立した層にする                                                                    | —                                                                                                                                                                                                                                      | package 1 つのために 4 つ目の層を作るのは、先回りした共通化である。依存のルールは platform → domain の既存の規則で表せる                                                                                                                                                                          |

### wire の変換の置き場

| 選択肢                                                                          | 強み                                                                                                                  | 弱み                                                                                                                   |
| ------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| **採用:** `analyze` の port と `protocol` の ACL で変換する                     | 層のルールに例外なしで成り立つ。wire の表現の変更（Protocol のバージョンの更新）が ACL で止まり、ドメインへ波及しない | `SourceLocation` に当たる型の定義が wire 用と domain 用で重複し、フィールドを足すときに 2 か所の更新と変換の追随が要る |
| 変換を `analyze` に置き、app → platform（`protocol`）の import を例外として許す | —                                                                                                                     | 層のルールに最初から例外が入り、lint のルールと説明の文書が複雑になる                                                  |

### 配線

| 選択肢                                       | 強み                                                                                              | 弱み                                                                                                            |
| -------------------------------------------- | ------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| **採用:** `cli` での手動の DI                | Core の配線は数個の struct の組み立てで、手動の DI で足りる。依存を最小に保つ既存の方針と整合する | —                                                                                                               |
| `google/wire` などの DI のライブラリを入れる | —                                                                                                 | 依存を最小に保つ既存の方針と合わない。interface を利用する側で小さく定義するスタイルと `wire.Bind` の相性が悪い |

### Java Analyzer の外部ライブラリの隔離とチェック

| 選択肢                                                      | 強み                                                                                                 | 弱み                                                                                                                                                                                 |
| ----------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **採用:** 3 段で隔離し、ArchUnit でチェックする             | ArchUnit は既存の `./gradlew test` に乗るため、導入のコストが小さい。Go の側（depguard）と対称になる | ArchUnit という test の依存が増え、バージョンの固定の保守が要る                                                                                                                      |
| JavaParser と SymbolSolver も adapter へ全面的に隔離する    | —                                                                                                    | この Analyzer は JavaParser の AST の上に作った解析器そのもので（35 クラスのうち 16 クラスが使う）、全面的な隔離は実質的な書き換えになり、外部の挙動を変えない再編という範囲を超える |
| Java の側の機械のチェックを見送り、規約の文書だけで運用する | —                                                                                                    | 再編の直後が最も regression しやすく、Go の側（depguard）と非対称になる                                                                                                              |

## Consequences

### Positive

- 生成した依存図、depguard の理由つきのエラー、`cli` の配線のコードの 3 つから、実際の依存を判別できる。
  - 新しく加わる人とコーディングエージェントが、置き場と依存を誤りにくい。
  - 物理的な移動がないため、既存の path と既存の branch に影響しない。
- wire の表現の変更（Protocol のバージョンの更新）が ACL で止まり、ドメインへ波及しない。
- 禁止の import を CI と pre-commit で機械的に検出し、人のレビューに頼った regression を防げる。
- 参照実装（Java Analyzer）の構造から、2 つ目の言語の Analyzer の作り方を読み取れる。

### Negative

- `SourceLocation` に当たる型の定義が wire 用と domain 用で重複し、フィールドを足すときに 2 か所の更新と変換の追随が要る。
- golangci-lint と ArchUnit という dev と test の依存が増え、バージョンの固定の保守が要る。
- パスを見るだけでは、層の粗い順序を読めない。
  - 生成した図、depguard の `desc` のメッセージ、codebase.md の層と package の対応で補う。
- 依存図を生成するスクリプトという保守の対象が 1 つ増える。
  - 再生成の diff のチェックで、図が古くなるのを防ぐ。
