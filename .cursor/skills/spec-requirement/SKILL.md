---
name: spec-requirement
description: Captures user requests through dialog, drafts a requirements doc from the requirements template, and optionally opens a tracker issue. Use when the user asks to turn a request into a requirements doc or an issue.
---
# Spec Requirement

要求を対話で構造化し、requirements template に沿った draft を作成し、必要に応じて issue を起票する。

## いつ使うか

- 要求はあるが、まだ requirements doc / issue になっていない
- spec を起こす前に「誰が / なぜ / 完了条件」を確定したい

## 先に読むもの

- `AGENTS.md` の `Spec Workflow Contract`
- `templates/requirements/template.md`
- `references/intake-checklist.md`

## 実行フロー

> 流れ: 要求のヒアリング → 構造化 → ドラフト提示 → ユーザー承認 → (任意) Issue 起票

### 1. 要求のヒアリング

`$ARGUMENTS` またはユーザー発言から要求を把握し、不足を質問する:

- 誰がどう使うか
- なぜ必要か (背景 / 課題)
- 完了の条件は何か (受け入れ基準)
- 関連する [PRD](../../PRD.md) / [Design Doc](../../design/DesignDoc.md) の節

### 2. ドラフト作成 (必須 — スキップ禁止)

`templates/requirements/template.md` のセクションに沿って draft を作る。
受け入れ基準は `references/intake-checklist.md` の EARS 風記法に沿って書く。

> **ガード**: draft をユーザーに提示せずに起票してはならない。
> ユーザーが「そのまま起票して」と言った場合でも、draft を表示してから起票する。

### 3. 保存先

- spec-dir を決める: `specs/<issue-id>-<slug>/` (issue 起票前の場合は `specs/draft-<slug>/`)
- draft を `specs/<...>/requirements.md` に保存する
- 既に存在する場合は上書きせず差分案を提示する

### 4. Issue 起票 (任意)

ユーザーが起票を承認した場合のみ、`Spec Workflow Contract` の CLI / Repo を使って起票する:

```sh
<ISSUE_CLI> issue create --repo <REPO> --title "<title>" --body-file <requirements.md path>
```

起票後、Issue 番号と URL を報告し、spec-dir のリネームが必要であればユーザーに確認する。

## 停止条件

- 受け入れ基準が EARS 風に書けない (誰が / いつ / 何をするか不明)
- 関連 PRD / Design Doc 節が特定できず、上位文書と矛盾する可能性が残る
- ユーザーが draft を承認していない状態で起票しようとしている
- `Spec Workflow Contract` の必須値 (Tracker CLI / Repo) が欠落している
