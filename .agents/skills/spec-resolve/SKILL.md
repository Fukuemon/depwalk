---
name: spec-resolve
description: >-
  Resolves one open question at a time from the spec's 論点 table, syncing every
  dependent section in the spec. Use when the user references "論点", asks "next
  open question", or invokes spec-resolve.
---
# Spec Resolve

spec (`specs/<...>/index.md`) の `## 設計時の論点` テーブルから論点を 1 件選び、選択肢を提示し、ユーザーの決定を spec 内全箇所に同期する。

## いつ使うか

- spec の論点を 1 件ずつ確定させたい
- 「設計時の論点」テーブルに未決定行がある

## 先に読むもの

- `AGENTS.md` の `Spec Workflow Contract`
- 対象 spec の `index.md` 全体
- 必要に応じて [`spec-draft/references/reconcile-upstream.md`](../spec-draft/references/reconcile-upstream.md)

## 入力

- `$ARGUMENTS` から対象論点番号または内容を特定
- 引数なし → 未決定論点一覧を提示し、ユーザーに選択を促す

## 実行フロー

### 1. 上位文書整合の前提チェック

論点が上位文書 (PRD / Design Doc / feature doc / context / ADR) のスコープを超えるか確認する。
超える場合は **その論点だけ先に `spec-sync` を提案する**。
spec 単独で上位文書を書き換えてはならない。

### 2. 論点の詳細説明

対象論点について以下を提示する:

- 何が決まっていないか
- 選択肢とそれぞれのメリット / デメリット
- 他の論点 / 機能仕様セクションとの依存
- 推奨案 (ある場合)

### 3. ユーザーの決定を待つ

ユーザー回答を待つ。追加質問があれば answer する。**推測で決めない**。

### 4. spec 内全箇所に反映

決定内容を以下の **すべての関連箇所** に同期する:

1. `## 設計時の論点` テーブルの「決定」列を更新
2. 該当する機能仕様サブセクション (Performance / Routing-URL State / Content-Assets / UI Reuse / Testing) を更新
3. 取り込み済 appendix (API / Database / Authorization / Screen / testid) のうち関連箇所を更新
4. 既に生成済の Mermaid 図があれば、矛盾しないよう更新フラグを残す (再生成は `spec-diagrams` に任せる)
5. 解決済みなら `## 解決済みの論点` に移動

### 5. 差分セクションに追記

`## 上位資料からの変更点` のうち、決定に伴う変更を該当テーブル (PRD / Design Doc / feature doc / context / ADR) に追記する。
`spec-track` での二重追記を避けるため、本 skill で追記したものは「source: spec-resolve」とコメントを残す。

### 6. 残り論点の報告

- 未決定論点数を報告
- 「次の論点」候補を 1 件提案

## 停止条件

- 論点が上位文書のスコープを超えており、`spec-sync` 未実行
- ユーザーが決定していないのに進めようとしている
- 既に確定済の論点を上書きしようとしている (`解決済みの論点` を再度書き換えない)
- 該当論点の選択肢が出せない (情報不足) — ユーザーに情報補足を求める
