# Feature 設計 doc 一覧

`design/features/` は **feature 単位の設計 (How)** を置く層。全体像 (system landscape / モジュール責務 / 横断方針) は [design/DesignDoc.md](../DesignDoc.md)、技術スタック規約・運用契約は [context/](../../context/) が定める。

各 feature doc は「仕様 (What) をどう実現するか」を、データ構造・画面・主要シナリオ / フロー単位で記述する。責務・範囲・方針の層に留め、ファイル配置・directive・テスト手順などの実装レベルの手順は issue 単位の spec (作業文書。issue close 後に削除する) が持つ。

## 一覧

各 feature doc の frontmatter (`description`) から生成する。手で編集しても次回の生成で消える。
frontmatter を持たない feature doc はまだ載らない ([issue #40](https://github.com/Fukuemon/depwalk/issues/40) で解消する)。

<!-- BEGIN GENERATED: features-index (scripts/reading-map.sh が更新する。手編集しない) -->

- [analyzer-protocol/DesignDoc_analyzer-protocol.md](analyzer-protocol/DesignDoc_analyzer-protocol.md) — Core と Analyzer をつなぐ JSONL wire schema・SPI・失敗時の契約
- [cli/DesignDoc_cli.md](cli/DesignDoc_cli.md) — analyze コマンドの flag 体系と、Core への配線・入力検証の契約
- [graph/DesignDoc_graph.md](graph/DesignDoc_graph.md) — node / edge が持つ属性と、wire record → graph 値型の変換契約・公開の原子性
- [java-analyzer/DesignDoc_java-analyzer.md](java-analyzer/DesignDoc_java-analyzer.md) — Java/Spring 解析の全体構成と、外部ライブラリの隔離境界・起動契約・性能方針
- [java-analyzer/analysis.md](java-analyzer/analysis.md) — 型解決・Spring DI 解決・解析完全性の判定規則
- [java-analyzer/discovery.md](java-analyzer/discovery.md) — Gradle build model からの source root / classpath 取得と、その安全境界
- [java-analyzer/protocol-mapping.md](java-analyzer/protocol-mapping.md) — Java の構文要素を JSONL record へ写す規則 (methodId / signature / 帰属型 / metadata / diagnostic)
- [output/DesignDoc_output.md](output/DesignDoc_output.md) — 出力形式ごとの表示規則と、graph / traversal から View への変換契約
- [traversal/DesignDoc_traversal.md](traversal/DesignDoc_traversal.md) — 呼び出しグラフの探索意味論と、深さ・訪問順・結果構造の契約

<!-- END GENERATED: features-index -->

各 feature は `design/features/<feature>/` ディレクトリに集約する。design doc は `DesignDoc_<feature>.md`、付随するコンテンツは `<feature>/reference/` に置く。

## 新規追加

`templates/features/template.md` を `design/features/<feature>/DesignDoc_<feature>.md` にコピーして起票する。`templates/` は sdd-template から symlink で繋がっており本 repo では追跡しない (未接続なら `bash scripts/doctor.sh`)。
