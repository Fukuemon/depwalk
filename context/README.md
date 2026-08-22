---
type: context
title: Engineering Context Library
description: context ライブラリの位置づけと Producer / Consumer 契約
keywords: [context, 規約, 索引]
# governs / verified_commit は持たない。索引部分は生成物、本文は各 context 文書の
# 検査が守るため、本ファイル自体は鮮度検査の対象外。
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
`context/project.yml` は YAML のため frontmatter を持てず、この一覧には載らない。

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

## 記載しないもの

- Why / What (→ PRD)、feature 固有の設計 (→ design/features)、確定した意思決定の経緯 (→ ADR)、issue 固有の作業ログ (→ specs)

## 文書メタ情報と鮮度

`design/` と `context/` の各文書は、先頭の YAML frontmatter にメタ情報を持つ。手書きの `> 最終更新: YYYY-MM-DD` ヘッダは使わない。日付は人が書くため更新漏れも食い違いも機械検出できないが、commit と `git log` の差分は書き換えられないからである。

```yaml
---
type: feature-design # design-doc | feature-design | context | prd
title: Graph Engine
description: 呼び出しグラフの node / edge が持つ属性と wire → 値型の変換契約 # 索引の 1 行説明
status: 完了 # design 系のみ
keywords: [graph, Symbol, SourceLocation]
governs:
  - core/internal/graph
verified_commit: <sha> | unverified
---
```

| キー              | 意味と書き方                                                                                      |
| ----------------- | ------------------------------------------------------------------------------------------------- |
| `type`            | 文書の層。索引 (`context/reading-map.yaml`) は生成物なので値域に含めない                          |
| `description`     | 索引の 1 行説明。索引は `- [file](link) — description` の形で出力するため、説明の出所がここになる |
| `keywords`        | 索引に載せる検索語                                                                                |
| `governs`         | その文書が語る契約の実装場所。**すべて挙げる**                                                    |
| `verified_commit` | 最後に実装と突き合わせた commit。未検証は `unverified`                                            |

### `governs` の書き方

その文書が語っている契約の実装場所をすべて挙げる。文書が置かれた package だけではない。本文が決まりとして記録している値の出所 (build 定義や wrapper の properties) も含める。コードに直接紐づかない context 文書は、その文書が語る対象の設定ファイルを監視対象にする。

`governs` と `verified_commit` の**両方が欠けている場合だけ**、その文書は鮮度検査の対象外になる。片方だけ欠けている状態は設定ミス (キー名の typo / 書き漏れ) として error になる。片欠けを黙って対象外にすると、監視対象の文書が stale 一覧から静かに消え、棚卸しという目的が崩れるためである。

`governs` を持てない文書が 2 つある。

- `context/ai-agents.md` — 語る対象の subagent 定義を持つのは sdd-template リポジトリであり、本リポジトリは symlink で接続するだけで何も追跡しない。鮮度は sdd-template 側の変更で判断する
- `context/README.md` (本ファイル) — 索引部分は生成物なので drift 検査が守り、本文は各 context 文書の検査が守る

`context/project.yml` は YAML のため frontmatter を持てず、`meta.updated` を使う。

### `verified_commit` の進め方

実装と本文を突き合わせた作業の commit を入れる。突き合わせていない文書は `unverified` を明示する。全文書へ一律に HEAD を入れてはならない。それは「全文書が実装と一致している」という宣言になり、事実に反するためである。

`unverified` の文書は常に stale として点灯する。これにより stale 一覧が「まだ突き合わせていない文書のリスト」として正しい意味を持ち、消し込みの作業リストとしてそのまま使える。

更新の責務は `spec-lifecycle` の sync phase と `context-harvest` が負う。これらの skill を定めるのは sdd-template リポジトリであり、変更は `skill-feedback` 経由で書き戻す。

### 検査の強度

| 対象           | 判定方法                                          | 強度                   | 実行箇所          |
| -------------- | ------------------------------------------------- | ---------------------- | ----------------- |
| 読み取りマップ | 再生成して diff が出るか                          | FAIL                   | lefthook + CI     |
| 文書本文の鮮度 | `git log <verified_commit>..HEAD -- <governs...>` | 通知のみ (exit code 0) | CI の job summary |

本文の鮮度を FAIL にしない。`governs` 配下が変わっても文書が正しいままのケースは日常的にあり、それを FAIL 扱いにすると「内容を読まずに `verified_commit` だけ進めて通す」ことが唯一の現実的な運用になる。それは検査が存在しないのと同じである。

### 索引と生成物

「コードパス → 読むべき文書」の逆引き表 (`context/reading-map.yaml`) は frontmatter からの生成物であり、手で編集しない。同じ生成器が本 README のファイル一覧も埋めるが、書き換えるのは `<!-- BEGIN GENERATED: ... -->` / `<!-- END GENERATED: ... -->` のマーカー区間だけで、人手の本文には触れない。

生成区間はテーブルではなく箇条書きで出力する。prettier がテーブルの列幅を揃え直すため、生成 → 整形 → 再生成の ping-pong が起き、drift 検査が恒久的に FAIL するからである。ファイル全体が生成物のものは `.prettierignore` へ入れ、整形の責務を生成器に一任する。

## 参照

- [design/DesignDoc.md](../design/DesignDoc.md): system landscape とモジュール責務
- [design/features/](../design/features/): feature 単位の設計
- [adr/](../adr/): 長期参照する技術選定・境界の確定
- [specs/](../specs/): issue / 機能単位の作業文書
- [project.yml](project.yml): repo / 命名 / コマンド / 対象ドメイン / トラッカーの固有値
- [engineering.md](engineering.md) の Repository Quality Gate: 文書検査の実行点
