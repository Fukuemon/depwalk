---
name: spec-track
description: >-
  Updates the spec's "上位資料からの変更点" section with deltas the spec introduces
  against PRD, Design Doc, feature doc, context, and ADR. Use after
  spec-resolve, spec-diagrams, or when manually adding design changes that need
  recording.
---
# Spec Track

spec (`specs/<...>/index.md`) の `## 上位資料からの変更点` を最新にする。
PRD / Design Doc / feature doc / context / ADR 別のテーブルに差分を分類して追記する。

## いつ使うか

- `spec-resolve` で確定した決定が上位資料への差分を持つ
- `spec-diagrams` 後に図と矛盾する記述を整理した
- 設計セッションの終わりに変更点をまとめる

## 先に読むもの

- `AGENTS.md` の `Spec Workflow Contract`
- 対象 spec の `## 上位資料からの変更点` セクション (現状)
- 対象 spec の他セクション (会話中の変更を拾うため)

## 入力

- `$ARGUMENTS` から対象 spec path を特定
- 引数なし → 会話コンテキストから対象 spec を推定

## 実行フロー

### 1. 現状把握

`## 上位資料からの変更点` 配下のテーブル (PRD / Design Doc / feature doc / context / ADR) を読み、既に記録済の行を把握する。

### 2. 直近の変更を特定

会話コンテキストから、前回の差分追記以降に行った変更を拾う:

- spec-resolve で確定した決定
- Appendix の追加 / 削除
- 機能仕様の変更
- 図の更新による意味変化

### 3. 二重追記の防止

`spec-resolve` が既に追記したものには `source: spec-resolve` コメントが付く。
本 skill は **コメントが付いていない変更のみ** を追記する。
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

### 5. ユーザー報告

- 追記件数と概要
- 「変更提案」行 (上位文書側の更新が要るもの) が残っているなら **`spec-sync` を提案**

## 停止条件

- どの上位資料に分類するか不明な変更がある (推測で振り分けない)
- 既存行と矛盾する内容を上書きしようとしている
- 確定していない論点に関する記述を追記しようとしている
