# Phase: clarify

spec (`specs/<...>/index.md`) の `## 設計時の論点` テーブルから論点を 1 件選び、選択肢を提示し、ユーザーの決定を spec 内全箇所に同期する phase。**1 件ずつ**確定させ、全件で必ず停止する。

## 先に読むもの

- `AGENTS.md` の `Spec Workflow Contract`
- 対象 spec の `index.md` 全体
- 必要に応じて `reconcile-upstream.md` (同 dir)

## 手順

### 1. 上位文書整合の前提チェック

論点が上位文書 (PRD / Design Doc / feature doc / context / ADR) のスコープを超えるか確認する。
超える場合は **その論点だけ先に phase: sync (`phase-sync.md`) を提案する**。
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
4. 既に生成済の Mermaid 図があれば、矛盾しないよう更新フラグを残す (再生成は phase: diagram に任せる)
5. 解決済みなら `## 解決済みの論点` に移動

### 5. 差分セクションに追記

`## 上位資料からの変更点` のうち、決定に伴う変更を該当テーブル (PRD / Design Doc / feature doc / context / ADR) に追記する。
phase: track での二重追記を避けるため、本 phase で追記したものは「source: clarify」とコメントを残す。

### 6. 残り論点の報告

- 未決定論点数を報告
- 「次の論点」候補を 1 件提案

## 停止条件

- 論点が上位文書のスコープを超えており、phase: sync 未実行
- ユーザーが決定していないのに進めようとしている
- 既に確定済の論点を上書きしようとしている (`解決済みの論点` を再度書き換えない)
- 該当論点の選択肢が出せない (情報不足) — ユーザーに情報補足を求める
