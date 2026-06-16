---
name: styleguide-documents
description: The reusable documents style guide — defines the writing quality bar and decomposition rules for all design / context / spec documents. Use when authoring or reviewing PRD, Design Doc, context library, requirements, or spec documents to keep them unambiguous, well-scoped, and implementable.
---
# Styleguide: Documents

文書 (PRD / Design Doc / context / requirements / spec) を書くときの **再利用可能な原則層**。プロジェクト横断で流用できる「考え方」だけを置き、doc 種別ごとの適用 (見出し構成・記入例) は `templates/` が、プロジェクト固有の具体は context / spec が担う。doc を生成・更新する skill (`design-doc` / `context-bootstrap` / `spec-requirement` / `spec-draft` 等) は本 skill を「先に読むもの」で参照する。

## いつ使うか

- PRD / Design Doc / context / requirements / spec を新規作成・更新するとき
- 既存文書をレビューし、後続実装が可能な粒度か確認するとき
- 文書をどの単位で分割するか (新しい feature doc / 下位 doc を起こすか) を判断するとき

## 5 原則

1. **曖昧さを排除する** — 主語・対象・条件を明示する。「適切に」「必要に応じて」など解釈余地のある語は、判断基準・閾値・具体例に置き換える。
2. **意思決定理由を残す** — 「何を決めたか」だけでなく「なぜそう決めたか」「却下した代替案」を併記する。確定した長期判断は [adr/](../../../adr/) へ、issue 固有の経緯は spec へ。
3. **AI が後続実装できる粒度** — 入出力・境界・受け入れ基準を、実装者 (人/AI) が追加質問なしに着手できる具体度で書く。曖昧さが残る点は「未決事項」として担当者・期限付きで明示する。
4. **人間が読んで理解しやすい構造** — 見出しで Why → What → How の順に流す。表・箇条書き・図 (Mermaid) を使い、1 節 1 主題に保つ。正本へのリンクで重複を避ける。
5. **過剰な装飾を避ける** — 強調・絵文字・冗長な前置きを使わない。情報密度を優先し、テンプレートの見出し構造を保つ。

## 図のはしご (C4)

文書階層ごとに描く図の粒度を揃える。同じ対象を別の層で重複させない。

| 文書        | 描く図 (Mermaid)                                               |
| ----------- | -------------------------------------------------------------- |
| Design Doc  | C4 **L1 System Context** + **L2 Container** (全体像)           |
| feature doc | C4 **L3 Component** + 主要フロー (feature 内部構造)            |
| spec        | **Sequence** / **Flowchart** (操作・内部処理。`spec-diagrams`) |

未確定論点が残る対象は図にしない (推測で描かない)。

### C4 図の規則

- `Rel(A, B, ...)` の方向はデータフロー方向 (A が B へ送信)。イベント駆動は push する側 → 受け取る側。
- tech tag は具体的に書く (例: `"WebUSB"` と略さず `"TypeScript / WebUSB API"` のように言語・API を併記)。

## 文書分割の粒度 (Granularity)

1 文書に詰め込まず、次のいずれかに当てはまったら別文書へ分割する。

- **対象読者が異なる** (例: ステークホルダー向けの Why と実装者向けの How)。
- **独立して理解できるトピック** になっている (前後の文脈なしで読める)。

分割した文書は階層で連鎖させ、同じ情報を重複させない。

- 上位文書の **Goals** は、下位文書の **Background** になりうる。
- 上位文書のアーキテクチャ (Proposed Design / Container) に登場する **コンポーネント** は、下位文書の **Goals の文脈** になりうる。

本テンプレートでは landscape を [Design Doc](../../../design/DesignDoc.md)、feature 単位の詳細を [design/features/](../../../design/features/) に分ける基準として使う。

## 文書に書かない物 (境界)

- **Non-Goals は意図的にスコープ外とするものだけ** を書く。「この文書では触れない詳細」「他文書を参照」は Non-Goal ではない。別文書が担う領域は Goals 欄のリンクか Related Documents で示す。
- **関数単位の API 仕様・生成ドキュメントを Design Doc に複製しない**。コードレベルの詳細は言語ネイティブの doc system (例: GoDoc) を正本とする。

## Markdown 記法

- 段落内改行は **行末に半角スペース 2 つ**。`<br>` タグは使わない。

## 適用チェック

- [ ] 各記述の主語・対象が一意に定まる
- [ ] 主要な判断に理由 (or ADR / spec へのリンク) がある
- [ ] 受け入れ基準 / 境界が実装着手可能な具体度
- [ ] 未確定点が「未決事項」として明示されている
- [ ] 文書の粒度が適切 (読者・独立トピック単位で分割され、上下の文書で重複していない)
- [ ] Why / What / How の所在が文書間で重複・矛盾していない

## 停止条件

- 曖昧さを解消できる情報が不足している (ユーザーに確認するか「未決事項」に記録する)
