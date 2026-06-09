---
name: technical-writing
description: Defines the writing quality bar for all design / context / spec documents. Use when authoring or reviewing PRD, Design Doc, context library, requirements, or spec documents to keep them unambiguous and implementable.
targets:
  - "*"
---

# Technical Writing

設計・文書を書くときの **共通の品質基準**。doc を生成・更新する skill (`design-doc` / `context-bootstrap` / `spec-requirement` / `spec-draft` 等) は本 skill を「先に読むもの」で参照する。

## いつ使うか

- PRD / Design Doc / context / requirements / spec を新規作成・更新するとき
- 既存文書をレビューし、後続実装が可能な粒度か確認するとき

## 5 原則

1. **曖昧さを排除する** — 主語・対象・条件を明示する。「適切に」「必要に応じて」など解釈余地のある語は、判断基準・閾値・具体例に置き換える。
2. **意思決定理由を残す** — 「何を決めたか」だけでなく「なぜそう決めたか」「却下した代替案」を併記する。確定した長期判断は [adr/](../../../adr/) へ、issue 固有の経緯は spec へ。
3. **AI が後続実装できる粒度** — 入出力・境界・受け入れ基準を、実装者 (人/AI) が追加質問なしに着手できる具体度で書く。曖昧さが残る点は「未決事項」として担当者・期限付きで明示する。
4. **人間が読んで理解しやすい構造** — 見出しで Why → What → How の順に流す。表・箇条書き・図 (Mermaid) を使い、1 節 1 主題に保つ。正本へのリンクで重複を避ける。
5. **過剰な装飾を避ける** — 強調・絵文字・冗長な前置きを使わない。情報密度を優先し、テンプレートの見出し構造を保つ。

## 図のはしご (C4)

文書階層ごとに描く図の粒度を揃える。同じ対象を別の層で重複させない。

| 文書 | 描く図 (Mermaid) |
| ---- | ---------------- |
| Design Doc | C4 **L1 System Context** + **L2 Container** (全体像) |
| feature doc | C4 **L3 Component** + 主要フロー (feature 内部構造) |
| spec | **Sequence** / **Flowchart** (操作・内部処理。`spec-diagrams`) |

未確定論点が残る対象は図にしない (推測で描かない)。

## 適用チェック

- [ ] 各記述の主語・対象が一意に定まる
- [ ] 主要な判断に理由 (or ADR / spec へのリンク) がある
- [ ] 受け入れ基準 / 境界が実装着手可能な具体度
- [ ] 未確定点が「未決事項」として明示されている
- [ ] Why / What / How の所在が文書間で重複・矛盾していない

## 停止条件

- 曖昧さを解消できる情報が不足している (ユーザーに確認するか「未決事項」に記録する)
