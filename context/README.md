# Engineering Context Library

> 最終更新: 2026-06-06

`context/` は **技術スタック別のコード規約・コードベースアーキテクチャ・運用契約** を集約する永続ナレッジ層である。Feature を問わず横断する "How" を置き、PRD / Design Doc / spec から参照する。

## 位置づけ

| 層              | 文書                                          | 役割                                              |
| --------------- | --------------------------------------------- | ------------------------------------------------- |
| Why / What      | [PRD.md](../PRD.md) (統合時は DesignDoc)       | 誰のどの課題を、なぜ・何で解決するか              |
| How (全体像)    | [design/DesignDoc.md](../design/DesignDoc.md) | system landscape / モジュール責務 / 横断方針      |
| How (feature)   | [design/features/](../design/features/)       | feature 単位の設計 (データ構造・画面・フロー)     |
| How (規約/契約) | **context/** (本ライブラリ)                   | 技術スタック規約・codebase architecture・運用契約 |
| 固有値          | [project.md](project.md)                      | repo / 命名 / コマンド / 対象ドメイン / トラッカー |
| 意思決定        | [adr/](../adr/)                               | 長期参照する技術選定・境界の確定                  |
| 作業文書        | [specs/](../specs/)                           | issue / 機能単位の要求・設計・テスト観点          |

## ファイル一覧

| ファイル                                 | 内容                                                                   |
| ---------------------------------------- | ---------------------------------------------------------------------- |
| [project.md](project.md)                 | プロジェクト固有値 (repo / 命名 / コマンド / 対象ドメイン / トラッカー) |
| [architecture.md](architecture.md)       | codebase architecture: package / runtime boundary, 依存方向            |
| [toolchain.md](toolchain.md)             | toolchain 一覧, build 構成, scaffold policy                            |
| [engineering.md](engineering.md)         | root task boundary, shared config boundary, repository quality gate    |
| [testing.md](testing.md)                 | test 責務分担, test runtime contract                                   |
| [infrastructure.md](infrastructure.md)   | infra / deployment / environment / operations / security 契約          |

## Producer / Consumer 契約

- **Producer (設計時)**: 設計判断が確定したら該当ファイルへ反映する。spec / ADR の決定が context を変える場合は本ライブラリを更新してから下流へ進む。
- **Consumer (実装時)**: 実装は context を正本として参照する。新しいパターンを発見したら該当ファイルへ追記する。
- **Freshness**: 各ファイル先頭に `> 最終更新: YYYY-MM-DD` を置き、内容変更時に更新する。

## 記載しないもの

- Why / What (→ PRD)、feature 固有の設計 (→ design/features)、確定した意思決定の経緯 (→ ADR)、issue 固有の作業ログ (→ specs)
