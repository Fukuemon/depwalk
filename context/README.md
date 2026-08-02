---
type: context
title: Engineering Context Library
description: context ライブラリの位置づけと Producer / Consumer 契約
keywords: [context, 規約, 索引]
# governs / verified_commit は持たない。索引部分は生成物、本文は各 context 文書の
# 検査が守るため、本ファイル自体は鮮度検査の対象外 (ADR-0008 決定 4)。
---

# Engineering Context Library

`context/` は **技術スタック別のコード規約・コードベースアーキテクチャ・運用契約** を集約する永続ナレッジ層である。Feature を問わず横断する "How" を置き、PRD / Design Doc / spec から参照する。

## 位置づけ

| 層              | 文書                                                           | 役割                                               |
| --------------- | -------------------------------------------------------------- | -------------------------------------------------- |
| Why / What      | [design/DesignDoc.md](../design/DesignDoc.md) の Why / What 節 | 誰のどの課題を、なぜ・何で解決するか               |
| How (全体像)    | [design/DesignDoc.md](../design/DesignDoc.md)                  | system landscape / モジュール責務 / 横断方針       |
| How (feature)   | [design/features/](../design/features/)                        | feature 単位の設計 (データ構造・画面・フロー)      |
| How (規約/契約) | **context/** (本ライブラリ)                                    | 技術スタック規約・codebase architecture・運用契約  |
| 固有値          | [project.yml](project.yml)                                     | repo / 命名 / コマンド / 対象ドメイン / トラッカー |
| 意思決定        | [adr/](../adr/)                                                | 長期参照する技術選定・境界の確定                   |
| 作業文書        | [specs/](../specs/)                                            | issue / 機能単位の要求・設計・テスト観点           |

## ファイル一覧

各文書の frontmatter (`description`) から生成する。手で編集しても次回の生成で消える。
frontmatter を持たない文書 (`project.yml` / 移行前の `*.md`) はまだ載らない ([issue #40](https://github.com/Fukuemon/depwalk/issues/40) で解消する)。

<!-- BEGIN GENERATED: context-index (scripts/reading-map.sh が更新する。手編集しない) -->

- [ai-agents.md](ai-agents.md) — 非対話 CLI エージェントの invocation / routing / timeout 契約
- [architecture.md](architecture.md) — package / runtime / state boundary と依存方向の規約
- [engineering.md](engineering.md) — shared config / root task / repository quality gate の境界規約
- [infrastructure.md](infrastructure.md) — 公開基盤・環境・運用・セキュリティの契約
- [testing.md](testing.md) — test の責務分担と test runtime contract
- [toolchain.md](toolchain.md) — 標準 toolchain と build 構成、Gradle discovery の互換 matrix

<!-- END GENERATED: context-index -->

- [project.yml](project.yml) — プロジェクト固有値 (repo / 命名 / コマンド / 対象ドメイン / トラッカー)

## Producer / Consumer 契約

- **Producer (設計時)**: 設計判断が確定したら該当ファイルへ反映する。spec / ADR の決定が context を変える場合は本ライブラリを更新してから下流へ進む。
- **Consumer (実装時)**: 実装は context に従う。新しいパターンを発見したら該当ファイルへ追記する。
- **Freshness**: 各ファイル先頭の frontmatter に `governs` (その文書が語る実装範囲) と `verified_commit` (最後に実装と突き合わせた commit) を置く。手書きの `> 最終更新: YYYY-MM-DD` は使わない (日付は嘘をつけるが git の差分は嘘をつけないため)。実装と突き合わせていない文書は `verified_commit: unverified` を明示する。定めるのは [ADR-0008](../adr/0008-doc-freshness-and-reading-map.md)。

## 記載しないもの

- Why / What (→ PRD)、feature 固有の設計 (→ design/features)、確定した意思決定の経緯 (→ ADR)、issue 固有の作業ログ (→ specs)
