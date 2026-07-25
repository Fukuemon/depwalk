# ADR-0007: Core / Java Analyzer の依存境界を ACL と機械検査で固定する (Core はフラット package 構成を維持)

## 状態

承認

## 決定日

2026-07-24 (2026-07-25 改訂: Core の層ディレクトリ物理化を撤回。経緯は spec #32 の D8)

## 背景

depwalk は Core (Go) と Analyzer (言語別) をプロセス + JSONL Protocol で分離しており (ADR-0001 / ADR-0002)、この外側の境界は明確に機能している。一方でそれぞれの内部は feature 単位の package 並列に留まり、import 実測 (2026-07-23) で次の問題が確認された。

- `core/internal/protocol` がハブ化し、`graph` / `output` / `cli` / `analyze` / `analyzer` の 5 package が直接依存している。wire 表現 (JSONL DTO) がドメイン側へ漏れている (`graph -> protocol` の import が実在)。
- `core/internal/cli` が use case (`analyze`) だけでなく `graph` / `output` / `protocol` にも直接依存し、エントリポイントが内層を迂回参照している。
- Java Analyzer は `analysis` 配下に 9 sub-package が並列し、解析パイプラインの段階と外部ライブラリ (SootUp 等) への依存境界が構造から読み取れない。SootUp 型は 7 クラスへ漏れている。
- 層の区別がディレクトリ構造からも import 規約からも読み取れず、開発者・AI エージェントが依存方向を誤りやすい。

外部挙動 (CLI / JSONL Protocol / 出力形式 / exit code) は一切変えないリファクタリングとして、層を明示し依存方向を機械検査で固定する。要求の正本は [specs/32-architecture-refactor/requirements.md](../specs/32-architecture-refactor/requirements.md)、決定経緯 (論点 D1〜D7) は [specs/32-architecture-refactor/index.md](../specs/32-architecture-refactor/index.md)。

## 決定

### Core (Go) の package 構成と依存方向

`core/internal` 配下は**フラットな責務名 package 構成を維持**し、物理的な層ディレクトリは作らない (D8。2026-07-25 改訂 — 当初決定 D1 の 3 層ディレクトリ `domain/app/platform` への物理移動を撤回)。

問題の本質は「package 間の実際の依存エッジが見えない・強制されないこと」であり、層ディレクトリはこれに対して粗すぎる答えだった (層は 3 分類の順序しか示さず、`graph` と `traversal` の関係のような実エッジは依然不可視)。代わりに次の 3 点で可視性と強制を実現する:

- **依存方向の規定と機械検査**: package 単位の依存規則 (正本は `context/architecture.md` の Package Boundary) を depguard の deny ルールで宣言し、CI / pre-commit で強制する。層 glob より解像度が高い
- **生成された依存図**: `go list` から package 依存図 (mermaid) を生成するスクリプトを置き、`context/architecture.md` の生成マーカー区間を更新する。手描きの図と違い腐らず、再生成 diff を検査すれば実態との drift も検出できる
- **コンポジションルート**: 配線 (手動 DI + `var _` 検証) を `cli` に集約し、実際の依存グラフが 1 箇所で読めるようにする

層 (domain / app / platform 相当) は**概念としては維持**し、architecture.md の表で package との対応を示す。ディレクトリには焼き付けない。output は presenter 層を新設しない (D2 は不変)。

### wire 変換層 (ACL) と port

- `graph` は自前の `Symbol` / `SourceLocation` 値型を持ち、protocol import をゼロにする。wire 型との重複定義は境界隔離のコストとして許容する (D6。graph feature doc の「protocol 型を再利用する」旧決定を改訂)
- `analyze` は domain 型 (graph の値型) を返す port interface を利用側のファイル内に小さく定義する (`port/` 専用 package は作らない)。analyze 自身は struct として公開し、先回り interface を作らない
- `protocol` は腐敗防止層 (ACL) として wire DTO を内部に閉じ、Translator (wire → domain 変換) + Adapter (port 実装) を担う
- 配線は `cli` (コンポジションルート) でのコンストラクタ注入による手動 DI とし、`google/wire` 等の DI ライブラリ・コード生成は導入しない。`var _ Interface = (*Impl)(nil)` の interface 満足検証も cli に集約する

### Java Analyzer の構造

- `javaanalyzer` 直下 (`protocol` / `io` / `preflight` / `discovery`) は現状維持
- `analysis` 配下は段階別 package で構成し、実行順は `analysis/pipeline` (AnalysisRunner を移動) だけが知る
- 外部ライブラリ隔離は 3 段階 (D7): SootUp は `analysis/sootup` (adapter facade、自前型公開) に完全封じ込め、Gradle Tooling API は `discovery` に完全隔離、JavaParser / SymbolSolver は解析エンジンの中核として `analysis` 配下では許容し外への漏れのみ禁止

### 依存方向の機械検査

- Go: golangci-lint + depguard を導入し、package 単位の禁止 import (例: `graph` / `traversal` → `protocol` / `cli` / `output` を deny、`analyze` → `protocol` / `analyzer` / `output` / `cli` を deny) を `files` + `deny` + `desc` の宣言形式で検査する。lefthook pre-commit / CI に組み込む (D5 / D8)
- Java: ArchUnit を test 依存として追加し、外部ライブラリ隔離ルールを JUnit テストとして記述する。既存の `./gradlew test` で実行される (D3)

### 実装の段階分割

#32 を epic とし、実装は子 issue 2 件 (① Core 再編 + depguard、② Java 再編 + ArchUnit) に分割する。各段階で既存テスト (unit / E2E / golden) が無変更で PASS する状態を保つ (D4)。

## 代替案

- 機械的層名 (`domain` / `usecase` / `infra`) を採用する。
  - 却下理由: クリーンアーキテクチャで唯一絶対なのは依存の内向き方向であり、層名・層数は自由。Go コミュニティは機械的層名を避ける傾向があり、`app` / `platform` で同じ構造的可読性が得られる。
- 3 層ディレクトリ (`domain/` `app/` `platform/`) へ物理移動する (当初決定。2026-07-25 撤回)。
  - 撤回理由: 知りたい解像度は実際の依存エッジであり、層ディレクトリは 3 分類の粗い順序しか示さない。package 名は責務名のまま維持されるため、import path の深化・既存 branch との conflict・17 箇所の doc 追随という churn に見合う情報増がない。Go 規約 (effective-go skill) の「layer-first を避け、責務名パッケージを保つ」とも整合しない。depguard は package 単位ルールで層 glob と同等以上の強制ができ、当初「フラット + lint」案の却下理由だった「文書依存になる」は、手描き文書でなく生成された依存図 + drift 検査に置き換えることで解消した。
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

- 生成された依存図・depguard の理由付きエラー・cli の配線コードの 3 点から実際の依存関係が判別でき、新規参加者・AI エージェントが配置と依存を誤りにくい (物理移動ゼロで既存 path・既存 branch に影響しない)
- wire 表現の変更 (Protocol 版更新) がドメインへ波及しない (ACL で遮断)
- 禁止 import が CI / pre-commit で機械検出され、人力レビュー頼みの regression を防げる
- 参照実装 (Java Analyzer) の構造から第 2 言語 Analyzer の作り方を読み取れる

### 悪い影響 / トレードオフ

- `SourceLocation` 相当の型定義が wire 用と domain 用で重複し、フィールド追加時に 2 箇所の更新と変換の追随が必要になる
- golangci-lint / ArchUnit という dev / test 依存が増える (バージョン固定の保守が必要)
- パスを見るだけでは層の粗い順序が読めない (生成図と depguard の desc メッセージ、architecture.md の層対応表で代替する)
- 依存図生成スクリプトという保守対象が 1 つ増える (drift 検査で腐敗は防ぐ)

### 影響範囲

- 対象モジュール / package: `core`, `traversal`, `output`, `analyzer-protocol`, `java-analyzer`

## 実装・運用への反映

- spec 更新要否: 要。spec #32 が決定経緯を保持し、実装は #32 の子 issue 2 件で行う
- context / AI 向け設定更新要否: 要。`context/architecture.md` (Package Boundary の依存規則・生成依存図 / Java 内部境界)、`context/project.md` (Naming Conventions)、`context/engineering.md` (依存方向 gate) へ反映済み (2026-07-24、2026-07-25 D8 改訂を反映)

## 関連ドキュメント / チケット

- [design/DesignDoc.md](../design/DesignDoc.md): 設計原則 P1〜P4 (本 ADR は P2/P3 の内部徹底であり landscape 不変)
- [adr/0002-core-implementation-foundation.md](0002-core-implementation-foundation.md): 初期 package 構成 (本 ADR で層別構造へ改訂)
- [design/features/graph/DesignDoc_graph.md](../design/features/graph/DesignDoc_graph.md): `SourceLocation` 自前型化・変換所在の改訂先
- [design/features/java-analyzer/DesignDoc_java-analyzer.md](../design/features/java-analyzer/DesignDoc_java-analyzer.md): 内部 package 構成と依存境界
- spec / PR: [specs/32-architecture-refactor](../specs/32-architecture-refactor/index.md) (issue #32)
- 外部参考資料: [Go の設計、どこまでやる？](https://zenn.dev/135yshr/books/go-service-design) (依存性ルール / interface 利用側定義 / ACL / 手動 DI / depguard)
