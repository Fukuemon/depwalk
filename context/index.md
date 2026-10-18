# context の目次

作業の中で参照する、このリポジトリの規約と事実。変更を取り込むまでの手順は [CONTRIBUTING.md](../CONTRIBUTING.md) にある。

- [コードベースの構造](codebase.md) — Core と Analyzer の境界、Core の package の依存の向き、Java Analyzer の import の範囲、staging Graph とエラーの分類の置き場。コードから読み取れる構造は書かない
- [コードと文書の規約](conventions.md) — Go と JUnit のテストの命名、コメントに書くこと、文書の置き場と frontmatter、golangci-lint と lefthook の設定、チェックの除外。lint で検出できる規則は書かない（draft）
- [業務の知識](domain.md) — Core、Analyzer、Protocol、Model、caller と callee、record、diagnostic と fatal、staging Graph、自動 discovery などの用語。状態と不変条件は概念ごとに domain/ に置く（draft）
- [基盤と運用](operations.md) — local と CI の環境、自動 discovery の副作用の前提、実際のプロジェクトを解析するときの設定、標準出力と stderr の観測点、depwalk が漏らさない範囲（draft）
- [技術スタック](tech-stack.md) — Go と Java のツールの役割とバージョンの所在、依存を足す条件、主なコマンド、Gradle、SootUp、JDK を上げるときの確認、Analyzer の作り始め。バージョンそのものと選んだ理由は書かない
- [テスト](testing.md) — テストの種類ごとの保証と置き場、JDK と fat jar が要るテスト、fake analyzer と fixture の作り方、macOS の real path、リリースの前に通す E2E（draft）

## Design Doc

- [depwalk PRD](../PRD.md) — メソッドを直す前の影響範囲の調査を、呼び出し元と呼び出し先の静的解析で自動にし、手元と CI で同じように実行できるようにする（draft）
- [Java Analyzer](../analyzers/java/DesignDoc.md) — Java/Spring 解析の全体構成と、Analyzer 内部の package 構成、外部ライブラリの隔離、起動の契約、性能の方針
- [Java Analyzer: 解析エンジン](../analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/DesignDoc.md) — 型の解決、bytecode member の補完、Spring DI、完全性の gate、型伝播救済、暗黙の呼び出し
- [Java Analyzer: Source root discovery](../analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/discovery/DesignDoc.md) — Gradle の build model から source root と classpath を得る方式、互換の範囲、安全の境界
- [Java Analyzer: Protocol への写像](../analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/protocol/DesignDoc.md) — Java の構文要素を JSONL の record へ写す規則（methodId、帰属型、dispatch、metadata、code）
- [CLI Interface](../core/internal/cli/DesignDoc.md) — analyze コマンドの flag の体系、method selector の書式、CLI と use case と ACL の責務の配置。起動コマンドの解決と分割、`--analyzer-meta` の合成、終了コード、出力先、CLI のプロセスの E2E の観点
- [Graph（呼び出しグラフのデータモデル）](../core/internal/graph/DesignDoc.md) — 呼び出しグラフの node と edge が持つ属性、wire の record から graph の値型への変換、staging Graph の公開の原子性。変換と公開の実装は protocol と analyze にある
- [Output（Console と JSON）](../core/internal/output/DesignDoc.md) — Output の entry point、Formatter と View、エラーの境界、Console の tree と行の書式、JSON の schema と版の管理
- [Analyzer Protocol / SPI](../core/internal/protocol/DesignDoc.md) — Core と Analyzer の間の JSONL の record、プロセスの契約、失敗の扱い、バージョンと互換性、contract test の観点
- [Traversal（Caller / Callee の探索）](../core/internal/traversal/DesignDoc.md) — 呼び出しグラフの探索の API、到達集合の定義（minDepth、誘導部分グラフ、cycle 注釈、depthLimit cutoff）、探索順序の扱い、処理の流れ
- [depwalk Design Doc](../design/DesignDoc.md) — Core と Analyzer の C4 の L2、モジュールの責務と依存、Core の package の依存図（生成）、設計原則と判断の優先順位、テストの層、ディレクトリの構成
