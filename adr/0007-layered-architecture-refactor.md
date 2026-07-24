# ADR-0007: Core / Java Analyzer の内部を層別構造へ再編し依存方向を機械検査する

## 状態

承認

## 決定日

2026-07-24

## 背景

depwalk は Core (Go) と Analyzer (言語別) をプロセス + JSONL Protocol で分離しており (ADR-0001 / ADR-0002)、この外側の境界は明確に機能している。一方でそれぞれの内部は feature 単位の package 並列に留まり、import 実測 (2026-07-23) で次の問題が確認された。

- `core/internal/protocol` がハブ化し、`graph` / `output` / `cli` / `analyze` / `analyzer` の 5 package が直接依存している。wire 表現 (JSONL DTO) がドメイン側へ漏れている (`graph -> protocol` の import が実在)。
- `core/internal/cli` が use case (`analyze`) だけでなく `graph` / `output` / `protocol` にも直接依存し、エントリポイントが内層を迂回参照している。
- Java Analyzer は `analysis` 配下に 9 sub-package が並列し、解析パイプラインの段階と外部ライブラリ (SootUp 等) への依存境界が構造から読み取れない。SootUp 型は 7 クラスへ漏れている。
- 層の区別がディレクトリ構造からも import 規約からも読み取れず、開発者・AI エージェントが依存方向を誤りやすい。

外部挙動 (CLI / JSONL Protocol / 出力形式 / exit code) は一切変えないリファクタリングとして、層を明示し依存方向を機械検査で固定する。要求の正本は [specs/32-architecture-refactor/requirements.md](../specs/32-architecture-refactor/requirements.md)、決定経緯 (論点 D1〜D7) は [specs/32-architecture-refactor/index.md](../specs/32-architecture-refactor/index.md)。

## 決定

### Core (Go) の層構造

`core/internal` 配下を 3 層ディレクトリでグルーピングする。層名は `domain` / `usecase` / `infra` のような機械的層名を避け、Go コミュニティで通用する語彙を使う (D1)。

- `domain/` (graph / traversal): ドメイン層。他層に依存しない
- `app/` (analyze): アプリケーションサービス層 (usecase 相当)。`domain` のみに依存する
- `platform/` (protocol / analyzer / output / cli): 技術基盤層 (infrastructure 相当)。外部ライブラリを隔離する

package 名 (import 末尾) は従来の責務名を維持する。output は presenter 層を新設せず `platform` に含める (D2)。

### wire 変換層 (ACL) と port

- `domain/graph` は自前の `Symbol` / `SourceLocation` 値型を持ち、protocol import をゼロにする。wire 型との重複定義は境界隔離のコストとして許容する (D6。graph feature doc の「protocol 型を再利用する」旧決定を改訂)
- `app/analyze` は domain 型を返す port interface を利用側のファイル内に小さく定義する (`port/` 専用 package は作らない)。app 自身は struct として公開し、先回り interface を作らない
- `platform/protocol` は腐敗防止層 (ACL) として wire DTO を内部に閉じ、Translator (wire → domain 変換) + Adapter (port 実装) を担う
- 配線は `platform/cli` (コンポジションルート) でのコンストラクタ注入による手動 DI とし、`google/wire` 等の DI ライブラリ・コード生成は導入しない。`var _ Interface = (*Impl)(nil)` の interface 満足検証も cli に集約する

### Java Analyzer の構造

- `javaanalyzer` 直下 (`protocol` / `io` / `preflight` / `discovery`) は現状維持
- `analysis` 配下は段階別 package で構成し、実行順は `analysis/pipeline` (AnalysisRunner を移動) だけが知る
- 外部ライブラリ隔離は 3 段階 (D7): SootUp は `analysis/sootup` (adapter facade、自前型公開) に完全封じ込め、Gradle Tooling API は `discovery` に完全隔離、JavaParser / SymbolSolver は解析エンジンの中核として `analysis` 配下では許容し外への漏れのみ禁止

### 依存方向の機械検査

- Go: golangci-lint + depguard を導入し、層をまたぐ禁止 import (`domain` → `app`/`platform`、`app` → `platform`) を `files` + `deny` + `desc` の宣言形式で検査する。lefthook pre-commit / CI に組み込む (D5)
- Java: ArchUnit を test 依存として追加し、外部ライブラリ隔離ルールを JUnit テストとして記述する。既存の `./gradlew test` で実行される (D3)

### 実装の段階分割

#32 を epic とし、実装は子 issue 2 件 (① Core 再編 + depguard、② Java 再編 + ArchUnit) に分割する。各段階で既存テスト (unit / E2E / golden) が無変更で PASS する状態を保つ (D4)。

## 代替案

- 機械的層名 (`domain` / `usecase` / `infra`) を採用する。
  - 却下理由: クリーンアーキテクチャで唯一絶対なのは依存の内向き方向であり、層名・層数は自由。Go コミュニティは機械的層名を避ける傾向があり、`app` / `platform` で同じ構造的可読性が得られる。
- フラット構成を維持し、層は README / lint のみで表現する。
  - 却下理由: 「ディレクトリ構造を見るだけで層と依存方向が判別できる」という要求の成功条件を構造で満たせず、文書依存になる。
- output を presenter として独立層にする。
  - 却下理由: package 1 つのために 4 層目を作るのは先回りした共通化。依存ルールは platform → domain の既存規則で表現できる。
- 変換を `app/analyze` に置き、app → platform/protocol の import を例外許可する。
  - 却下理由: 層ルールに初回から例外が入り、lint ルールと README の説明が複雑化する。port + ACL なら例外なしで成立する。
- `google/wire` 等の DI ライブラリを導入する。
  - 却下理由: Core の配線は数個の struct の組み立てで手動 DI で十分。ADR-0002 の依存最小方針と整合し、interface を利用側で小さく定義するスタイルとも wire.Bind の相性が悪い。
- JavaParser / SymbolSolver も adapter に全面隔離する。
  - 却下理由: 本 Analyzer は JavaParser の AST 上に構築された解析器そのもので (35 クラス中 16 クラスが使用)、全面隔離は実質的な書き換えとなり「外部挙動不変の再編」スコープを超える。
- Java 側の機械検査を見送り規約文書のみで運用する。
  - 却下理由: 再編直後が最も regression しやすく、Go 側 (depguard) と非対称になる。ArchUnit は既存 `./gradlew test` に乗り導入コストが小さい。

## 影響

### 良い影響

- ディレクトリ構造だけで層と依存方向が判別でき、新規参加者・AI エージェントが配置と依存を誤りにくい
- wire 表現の変更 (Protocol 版更新) がドメインへ波及しない (ACL で遮断)
- 禁止 import が CI / pre-commit で機械検出され、人力レビュー頼みの regression を防げる
- 参照実装 (Java Analyzer) の構造から第 2 言語 Analyzer の作り方を読み取れる

### 悪い影響 / トレードオフ

- `SourceLocation` 相当の型定義が wire 用と domain 用で重複し、フィールド追加時に 2 箇所の更新と変換の追随が必要になる
- golangci-lint / ArchUnit という dev / test 依存が増える (バージョン固定の保守が必要)
- import path が 1 階層深くなり、既存 branch との conflict・path 参照 doc の追随コストが一時的に発生する

### 影響範囲

- 対象モジュール / package: `core`, `traversal`, `output`, `analyzer-protocol`, `java-analyzer`

## 実装・運用への反映

- spec 更新要否: 要。spec #32 が決定経緯を保持し、実装は #32 の子 issue 2 件で行う
- context / AI 向け設定更新要否: 要。`context/architecture.md` (Package Boundary の層別化 / Java 内部境界)、`context/project.md` (Naming Conventions)、`context/engineering.md` (層依存 gate) へ反映済み (2026-07-24)

## 関連ドキュメント / チケット

- [design/DesignDoc.md](../design/DesignDoc.md): 設計原則 P1〜P4 (本 ADR は P2/P3 の内部徹底であり landscape 不変)
- [adr/0002-core-implementation-foundation.md](0002-core-implementation-foundation.md): 初期 package 構成 (本 ADR で層別構造へ改訂)
- [design/features/graph/DesignDoc_graph.md](../design/features/graph/DesignDoc_graph.md): `SourceLocation` 自前型化・変換所在の改訂先
- [design/features/java-analyzer/DesignDoc_java-analyzer.md](../design/features/java-analyzer/DesignDoc_java-analyzer.md): 内部 package 構成と依存境界
- spec / PR: [specs/32-architecture-refactor](../specs/32-architecture-refactor/index.md) (issue #32)
- 外部参考資料: [Go の設計、どこまでやる？](https://zenn.dev/135yshr/books/go-service-design) (依存性ルール / interface 利用側定義 / ACL / 手動 DI / depguard)
