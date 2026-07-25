# Phase: diagram

spec の `## フロー / シーケンス` セクションに Mermaid 図を生成する phase。
ユーザー操作起点の flowchart と、システム内部処理の sequence diagram を組み合わせる。

## 先に読むもの

- `AGENTS.md` の `Spec Workflow Contract`
- 対象 spec の `index.md` (要件解釈 / 機能仕様 / Appendix)
- `diagram-rules.md` (同 dir)

## 手順

1. spec の `## 設計時の論点` / `## 未確定事項` を確認し、関連論点が残っていれば停止
2. 機能仕様 / Appendix から「描く対象」を列挙
3. 各対象について **flowchart** (ユーザー操作起点) と **sequence diagram** (システム内部処理) を生成
4. `diagram-rules.md` の participants / 必須要素ルールに従う
5. spec の `## フロー / シーケンス` セクションに挿入
6. 既存図がある場合は差分のみ更新

## 停止条件

- 未確定論点が残っている (推測で図を作らない)
- 描く対象 (Op / Flow) が spec から特定できない
- Mermaid 構文エラーが発生した状態で書き戻そうとしている
