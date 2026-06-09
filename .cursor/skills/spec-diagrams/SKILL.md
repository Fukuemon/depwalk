---
name: spec-diagrams
description: Generates Mermaid flowcharts and sequence diagrams from a spec's operations. Use when the user asks for "flowchart", "sequence diagram", "図を追加", or invokes spec-diagrams.
---
# Spec Diagrams

spec の `## フロー / シーケンス` セクションに Mermaid 図を生成する。
ユーザー操作起点の flowchart と、システム内部処理の sequence diagram を組み合わせる。

## いつ使うか

- spec の操作 / フローを可視化したい
- 未確定論点ゼロの spec に対し、設計を図に落とす段階

## 先に読むもの

- `AGENTS.md` の `Spec Workflow Contract`
- 対象 spec の `index.md` (要件解釈 / 機能仕様 / Appendix)
- `references/diagram-rules.md`

## 入力

- `$ARGUMENTS` から対象 spec path または操作名を特定
- 引数なし → spec 内のすべての主要操作に対して生成

## 実行フロー

1. spec の `## 設計時の論点` / `## 未確定事項` を確認し、関連論点が残っていれば停止
2. 機能仕様 / Appendix から「描く対象」を列挙
3. 各対象について **flowchart** (ユーザー操作起点) と **sequence diagram** (システム内部処理) を生成
4. `references/diagram-rules.md` の participants / 必須要素ルールに従う
5. spec の `## フロー / シーケンス` セクションに挿入
6. 既存図がある場合は差分のみ更新

## 停止条件

- 未確定論点が残っている (推測で図を作らない)
- 描く対象 (Op / Flow) が spec から特定できない
- Mermaid 構文エラーが発生した状態で書き戻そうとしている
