# Phase: track

spec (`specs/<...>/index.md`) の `## 上位資料からの変更点` を最新にする phase。
PRD / Design Doc / feature doc / context / ADR 別のテーブルに差分を分類して追記する。

## 先に読むもの

- `spec-contract.md` (Spec Workflow Contract)
- 対象 spec の `## 上位資料からの変更点` セクション (現状)
- 対象 spec の他セクション (会話中の変更を拾うため)

## 手順

### 1. 現状把握

`## 上位資料からの変更点` 配下のテーブル (PRD / Design Doc / feature doc / context / ADR) を読み、既に記録済の行を把握する。

### 2. 直近の変更を特定

会話コンテキストから、前回の差分追記以降に行った変更を拾う:

- phase: clarify で確定した決定
- Appendix の追加 / 削除
- 機能仕様の変更
- 図の更新による意味変化

### 3. 二重追記の防止

phase: clarify が既に追記したものには `source: clarify` コメントが付く。
本 phase は **コメントが付いていない変更のみ** を追記する。
同一決定の二重追記を見つけたら 1 件に統合する。

### 4. テーブル別追記

| 反映先      | 例                                          |
| ----------- | ------------------------------------------- |
| PRD         | スコープ / 成功条件 / Persona 変更          |
| Design Doc  | モジュール責務 / Phase 方針 / 全体像 変更   |
| feature doc | feature の設計方針 / データ構造 / UC 変更   |
| context     | architecture / toolchain / 規約 / 運用 変更 |
| ADR         | 新規 ADR が必要な技術判断                   |

各行に「対象 / 変更内容 / 理由」を埋める。

関連 docs へ追記する場合は本文だけで止めず、`Spec Workflow Contract` の「文書メタ情報の同期」と正本境界「用語規約」に従う。

### 5. ユーザー報告

- 追記件数と概要
- 「変更提案」行 (上位文書側の更新が要るもの) が残っているなら **phase: sync (`phase-sync.md`) を提案**

## 停止条件

- どの上位資料に分類するか不明な変更がある (推測で振り分けない)
- 既存行と矛盾する内容を上書きしようとしている
- 確定していない論点に関する記述を追記しようとしている
