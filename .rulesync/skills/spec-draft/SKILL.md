---
name: spec-draft
description: Scaffolds a spec directory and index.md from the spec template after reconciling PRD, Design Doc, feature doc, and context. Use when starting a new feature spec or when the user asks for "spec 雛形" / "spec-draft" / "下書き".
targets:
  - "*"
---

# Spec Draft

issue から spec dir と `index.md` を template ベースで生成する。
PRD / Design Doc / feature doc / context / ADR と整合をとった上でスキャフォルディングし、矛盾を検知したら `spec-sync` を提案する。

## いつ使うか

- 新しい feature / issue 単位の spec を起こす
- requirements doc はあるが、設計用 spec がまだない

## 先に読むもの

- `AGENTS.md` の `Spec Workflow Contract` — 正本 path / templates / target 一覧
- `references/reconcile-upstream.md` — 上位文書整合の手順
- 対象 issue (なければ `spec-issue-read` を先に呼ぶ)

## 入力

- `$ARGUMENTS` から issue 番号または slug を特定
- 引数なし → ユーザーに対象 issue を確認する

## 実行フロー

1. **issue 把握**: `spec-issue-read` を呼んで対象 issue 内容を取得 (既に把握済みならスキップ可)
2. **上位文書読み込み**: `Spec Workflow Contract` の PRD / Design Doc / feature doc / context / ADR path を読み、対象機能に関係する節だけを抽出する
3. **spec dir 決定**:
   - 形式: `<spec dir base>/<issue-id>-<slug>/`
   - 既存ディレクトリがあれば上書きせず、差分提案のみ
4. **整合チェック** (`references/reconcile-upstream.md`):
   - PRD のスコープ / Non Goals と矛盾していないか
   - Design Doc のモジュール責務 / Phase / 全体像と矛盾していないか
   - feature doc の設計方針 / context の architecture・規約・運用契約と矛盾していないか
   - 関連 ADR があれば該当 ID を控える
   - 矛盾を検出した場合は **`spec-sync` を提案して停止**
5. **`index.md` 生成**: `templates/specs/template.md` (minimal core) をコピーし、以下を埋める:
   - メタ情報 (Issue 番号 / Branch / Owner / Status)
   - 設計フェーズ状況 (intake = 完了, scaffold = 進行中)
   - 上位文書整合 (PRD / Design Doc / feature doc / context / ADR の節と整合方針)
   - 関連資料 / 背景 / スコープ / 要件の解釈
   - 実装対象 (target 一覧から該当を ◯)
   - 機能仕様の各サブセクション (該当しない場合は空のまま残す)
6. **Appendix 取り込み判定**: 機能種別に応じて `templates/specs/appendices/<topic>.md` を取り込むか確認:
   - API endpoint がある → `appendices/api.md`
   - 永続データ層がある → `appendices/database.md`
   - ロール / 権限がある → `appendices/authorization.md`
   - 画面コンポーネントツリーがある → `appendices/screen-spec.md`
   - E2E 対象 UI がある → `appendices/testid.md`
   - **ユーザーに確認してから挿入する** (該当する appendix が無いスコープなら何も追加しない)
7. **初期論点の洗い出し**: issue / 上位文書から未確定事項を `論点一覧` に列挙
8. 次アクションを提案: `spec-resolve` で論点解決へ

> 正本境界 (`Spec Workflow Contract`): 作成段階では spec が自身の決定の作業正本でよい。durable な設計成果 (IA / フロー / データモデル等) を design へ反映し正本を移すのは `spec-sync` の正本ハンドオフで行う。draft 段階で design 側を勝手に書き換えない。

## 停止条件

- 上位文書 (PRD / Design Doc / feature doc / context / 既存 ADR) と矛盾しており、解決方針が決まらない
- 対象 issue または spec dir が決まらない
- 実装対象 app / package が `Spec Workflow Contract` の target 一覧から特定できない
- `templates/specs/template.md` が見つからない
- 既存 spec を上書きしようとしている
