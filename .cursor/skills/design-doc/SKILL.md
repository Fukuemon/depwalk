---
name: design-doc
description: テンプレートからプロダクトの PRD とトップレベル Design Doc を作成・更新し、独立 PRD の要否 (Why/What を Design Doc に統合するか) を判定する。"PRD 書いて" / "Design Doc 作って" / "Why/What/How を整理" で起動する。
---

# Design Doc

プロダクトの **Why / What / How** を確定し、PRD と Design Doc を template から生成・更新する。要件規模に応じて、独立 PRD を作るか、Design Doc に Why/What を統合するかを判定する。

## いつ使うか

- 新規プロダクト / 大きな機能の上位設計を起こすとき
- PRD あるいは Design Doc がまだ存在しない、または見直したいとき
- Why / What / How の所在を整理したいとき

## 先に読むもの

- `styleguide-documents` skill (文書の品質基準・分割粒度)
- root rule (`.rulesync/rules/CLAUDE.md`) のドキュメント階層と `spec-lifecycle` の `references/spec-contract.md` (Spec Workflow Contract)
- `context/project.yml` (存在すれば。なければ本 skill 後に `context-bootstrap` を案内する)
- `templates/prd/template.md` / `templates/design-doc/template.md`

## 実行フロー

> 流れ: 要求ヒアリング → PRD 要否判定 → モード確定 → draft 生成 → ユーザー承認 → 保存

### 1. 要求ヒアリング

`$ARGUMENTS` とユーザー発言から Why (課題 / 背景) と What (成功条件 / スコープ) を把握し、不足を質問する。

### 2. PRD 要否判定 (必須 — 理由を明示)

次を満たすほど **分離モード** (独立 PRD) を推奨する:

- 利害関係者が複数で、プロダクト要求が技術設計と独立して長期参照される
- 要求が大きく、How と分けて合意・更新する必要がある

次に当てはまるほど **統合モード** (Why/What を Design Doc に統合) を推奨する:

- 小規模 / 技術主導で、要求と設計が同じ担当・同じ更新サイクル
- 独立 PRD を維持するコストが価値を上回る

判定結果と理由をユーザーに提示し、確定する。

### 3. draft 生成 (必須 — スキップ禁止)

> **ガード**: draft をユーザーに提示せずに保存・コミットしてはならない。

**分離モード**:

- `templates/prd/template.md` → `PRD.md` を生成 (Why / What)。
- `templates/design-doc/template.md` → `design/DesignDoc.md` を生成。`## Why / What` 節を削除し、`## Related PRD` に `PRD.md` へのリンクを残す。

**統合モード**:

- `templates/design-doc/template.md` → `design/DesignDoc.md` を生成。`## Related PRD` を削除し、`## Why / What` 節に背景・課題・提供価値・成功条件・スコープを埋める。`PRD.md` は作らない。

いずれも `styleguide-documents` の 5 原則を満たし、Why / What / How の所在が一意に定まること。未確定点は `Open Questions` / `未決事項` に担当者・期限付きで残す。

`## アーキテクチャ概観` には `styleguide-documents` の「図のはしご」に従い C4 **L1 (System Context)** と **L2 (Container)** の Mermaid を描く。L3 以下は feature doc / spec へ委譲し、ここでは描かない。確定していない構成は図にしない。

### 4. 保存と次の案内

- 承認後に `PRD.md` / `design/DesignDoc.md` を保存する。既存がある場合は上書きせず差分案を提示する。
- `context/` が未整備なら `context-bootstrap` skill を案内する。
- 統合モードを選んだ場合、下流 (CLAUDE.md / spec-\*) の「PRD」参照は「統合 Design Doc の Why/What 節」を指す旨を `Spec Workflow Contract` 側で確認する。

## 停止条件

- Why または What が確定せず、成功条件を測定可能な形で書けない
- PRD 要否のモードがユーザー承認されていない
- draft をユーザーに提示せずに保存しようとしている
- `templates/` の対象テンプレートが見つからない
