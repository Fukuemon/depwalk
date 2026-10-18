# context の目次

作業の中で参照する、このリポジトリの規約と事実。変更を取り込むまでの手順は [CONTRIBUTING.md](../CONTRIBUTING.md) にある。

- [コードベースの構造](codebase.md) — Core と Analyzer の境界、Core の package の依存の向き、Java Analyzer の import の範囲、staging Graph とエラーの分類の置き場。コードから読み取れる構造は書かない
- [コードと文書の規約](conventions.md) — Go と JUnit のテストの命名、コメントに書くこと、文書の置き場と frontmatter、golangci-lint と lefthook の設定、チェックの除外。lint で検出できる規則は書かない
- [業務の知識](domain.md) — Core、Analyzer、Protocol、Model、caller と callee、record、diagnostic と fatal、staging Graph、自動 discovery などの用語。状態と不変条件は概念ごとに domain/ に置く（draft）
- [基盤と運用](operations.md) — local と CI の環境、自動 discovery の副作用の前提、実際のプロジェクトを解析するときの設定、標準出力と stderr の観測点、depwalk が漏らさない範囲（draft）
- [技術スタック](tech-stack.md) — Go と Java のツールの役割とバージョンの所在、依存を足す条件、主なコマンド、Gradle、SootUp、JDK を上げるときの確認、Analyzer の作り始め。バージョンそのものと選んだ理由は書かない
- [テスト](testing.md) — テストの種類ごとの保証と置き場、JDK と fat jar が要るテスト、fake analyzer と fixture の作り方、macOS の real path、リリースの前に通す E2E（draft）

## PRD

- [depwalk PRD](../PRD.md) — メソッドを直す前の影響範囲の調査を、呼び出し元と呼び出し先の静的解析で自動にし、手元と CI で同じように実行できるようにする（draft）

## Design Doc

- [depwalk Design Doc](../design/DesignDoc.md) — Core と Analyzer の C4 の L2、モジュールの責務と依存、Core の package の依存図（生成）、設計原則と判断の優先順位、テストの層、ディレクトリの構成
- [CLI Interface](../core/internal/cli/DesignDoc.md) — analyze コマンドの flag の体系、method selector の書式、CLI と use case と ACL の責務の配置。起動コマンドの解決と分割、`--analyzer-meta` の合成、終了コード、出力先、E2E の CLI 層の観点
- [Graph（呼び出しグラフのデータモデル）](../core/internal/graph/DesignDoc.md) — 呼び出しグラフの node と edge が持つ属性、wire の record から graph の値型への変換、staging Graph の公開の原子性。変換と公開の実装は protocol と analyze にある
- [Output（Console と JSON）](../core/internal/output/DesignDoc.md) — Output の entry point、Formatter と View、エラーの境界、Console の tree と行の書式、JSON の schema とバージョンの管理
- [Analyzer Protocol / SPI](../core/internal/protocol/DesignDoc.md) — Core と Analyzer の間の JSONL の record、プロセスの契約、失敗の扱い、バージョンと互換性、contract test の観点
- [Traversal（Caller / Callee の探索）](../core/internal/traversal/DesignDoc.md) — 呼び出しグラフの探索の API、到達集合の定義（minDepth、誘導部分グラフ、cycle 注釈、depthLimit cutoff）、探索順序の扱い、処理の流れ
- [Java Analyzer](../analyzers/java/DesignDoc.md) — Java/Spring 解析の全体構成と、Analyzer 内部の package 構成、外部ライブラリの隔離、起動の契約、性能の方針
- [Java Analyzer: 解析エンジン](../analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/DesignDoc.md) — 型の解決、bytecode member の補完、Spring DI、完全性の gate、型伝播救済、暗黙の呼び出し
- [Java Analyzer: Source root discovery](../analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/discovery/DesignDoc.md) — Gradle の build model から source root と classpath を得る方式、互換の範囲、安全の境界
- [Java Analyzer: Protocol への写像](../analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/protocol/DesignDoc.md) — Java の構文要素を JSONL の record へ写す規則（methodId、帰属型、dispatch、metadata、code）

## ADR

- [ADR-0001](../adr/0001-analyzer-protocol-jsonl-spi.md) — Core と Analyzer を別プロセスにし、JSONL over STDIN/STDOUT の process SPI で結合する
- [ADR-0002](../adr/0002-core-implementation-foundation.md) — Core を Go と Go modules で実装し、依存を Cobra と標準ライブラリに絞る
- [ADR-0003](../adr/0003-analyzer-command-resolution.md) — Analyzer の起動コマンドを、Core が解釈しない言語非依存の文字列として解決する
- [ADR-0004](../adr/0004-defer-runtime-call-tracing.md) — 動的呼び出しの完全追跡を初期スコープに含めず、候補と未解決理由を観測可能にする
- [ADR-0005](../adr/0005-adopt-sootup-and-spring-di-resolution.md) — Java Analyzer に SootUp による型階層の補完と Spring DI の解決を 1 つの feature として導入する
- [ADR-0006](../adr/0006-adopt-gradle-tooling-api-discovery.md) — source root を指定しない request では、Gradle Tooling API で build の実効 model から解析 context を discovery する
- [ADR-0007](../adr/0007-layered-architecture-refactor.md) — Core と Java Analyzer の依存境界を ACL と機械のチェックで固定し、Core はフラットな package 構成のまま保つ
- [ADR-0008](../adr/0008-defer-graph-visualization.md) — 可視化の出力（DOT / Mermaid）をスコープから外し、解析の精度と永続化を先に進める
- [ADR-0009](../adr/0009-implicit-call-resolution-and-type-propagation-rescue.md) — framework 由来の暗黙の呼び出しを Protocol の schema を変えずに metadata で表し、receiver の型は型根拠を保つ救済層で導く
- [ADR-0010](../adr/0010-traversal-induced-subgraph.md) — Traversal の結果を誘導部分グラフと SCC の判定で定める
- [ADR-0011](../adr/0011-java-analyzer-in-java.md) — Java Analyzer を Java で実装し、Kotlin を採らない
- [ADR-0012](../adr/0012-attribution-by-declaration-site.md) — 呼び出しの帰属型を宣言サイトで決め、宣言が scope の外のときだけレシーバの型へ引き上げる
- [ADR-0013](../adr/0013-reject-unknown-nested-fields.md) — Core は record の最上位の未知の field を無視し、入れ子の object の未知の field を invalid にする
- [ADR-0014](../adr/0014-extend-discovery-model-with-build-directories.md) — discovery の custom model provider が返す値に、project の directory と included build の root を足す
- [ADR-0015](../adr/0015-ambiguous-generic-event-listeners.md) — イベントの edge で、generics で過剰に一致しうる listener も曖昧として扱う
- [ADR-0016](../adr/0016-split-execution-order-between-main-and-runner.md) — Java Analyzer の実行の順を、解析の段は AnalysisRunner に、context の組み立てまでは Main に持たせる
- [ADR-0017](../adr/0017-limit-core-change-to-language-value.md) — 2 つ目以降の言語の Analyzer を足すとき、Core の変更を protocol の許す language の値の追加に限る
- [ADR-0018](../adr/0018-query-flags-on-analyze-command.md) — 探索のクエリを、サブコマンドを足さずに analyze コマンドの flag で指定する
- [ADR-0019](../adr/0019-oom-hint-from-analyzer-stderr.md) — Analyzer が error の record を出さずに異常終了したとき、Core は stderr の OutOfMemoryError から heap の不足を案内する
