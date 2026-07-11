# Feature 設計 doc 一覧

`design/features/` は **feature 単位の設計 (How)** を置く層。全体像 (system landscape / モジュール責務 / 横断方針) は [design/DesignDoc.md](../DesignDoc.md)、技術スタック規約・運用契約は [context/](../../context/) を正本とする。

各 feature doc は「仕様 (What) をどう実現するか」を、データ構造・画面・主要シナリオ / フロー単位で記述する。責務・範囲・方針の層に留め、ファイル配置・directive・テスト手順などの実装レベルの手順は spec ([specs/](../../specs/)) へ委譲する。

## 一覧

| Feature                                      | 文書                                                                               | Phase                                | 状態 |
| -------------------------------------------- | ---------------------------------------------------------------------------------- | ------------------------------------ | ---- |
| Analyzer Protocol / SPI                      | [DesignDoc_analyzer-protocol.md](analyzer-protocol/DesignDoc_analyzer-protocol.md) | Phase1                               | 完了 |
| Graph (呼び出しグラフのデータモデル)         | [DesignDoc_graph.md](graph/DesignDoc_graph.md)                                     | Phase1                               | 完了 |
| Traversal (Caller / Callee 探索)             | [DesignDoc_traversal.md](traversal/DesignDoc_traversal.md)                         | Phase1                               | 完了 |
| Output (Console / JSON / DOT / Mermaid 出力) | [DesignDoc_output.md](output/DesignDoc_output.md)                                  | Phase1 (DOT / Mermaid 実装は Phase4) | 完了 |

各 feature は `design/features/<feature>/` ディレクトリに集約する。design doc は `DesignDoc_<feature>.md`、付随するコンテンツ正本は `<feature>/reference/` に置く。

## 新規追加

[templates/features/template.md](../../templates/features/template.md) を `design/features/<feature>/DesignDoc_<feature>.md` にコピーして起票する。
