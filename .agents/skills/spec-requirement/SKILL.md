---
name: spec-requirement
description: >-
  対話でユーザー要求を整理し、requirements テンプレートから要求文書を起案、必要ならトラッカー issue を起票する。"要求を整理して" /
  "issue にして" / "requirements 作って" で起動する。
---
# Spec Requirement

要求を対話で構造化し、requirements template に沿った draft を作成し、必要に応じて issue を起票する。

## いつ使うか

- 要求はあるが、まだ requirements doc / issue になっていない
- spec を起こす前に「誰が / なぜ / 完了条件」を確定したい

## 先に読むもの

- `spec-lifecycle` の `references/spec-contract.md` (Spec Workflow Contract)
- `context/project.yml` の `tracker` (起票先の CLI / project)
- `templates/requirements/template.md`
- `references/intake-checklist.md`

## 禁止事項 (起票ゲート)

以下は、ユーザーが draft を確認し「起票してよい」等の明示承認を返すまで禁止する。
ユーザーが「そのまま進めて」と言ってもゲートは緩めない:

- tracker への issue 新規作成 / 本文更新 / ラベル付与・削除
- tracker 上の公開・共有状態に影響する操作

承認前に行ってよいのは、ローカル draft の作成・更新とレビュー依頼の提示までとする。

## 実行フロー

> 流れ: 要求のヒアリング → 構造化 → ドラフト提示 → ユーザー承認 → (任意) Issue 起票

### 1. 要求のヒアリング

`$ARGUMENTS` またはユーザー発言から要求を把握し、不足を質問する:

- 誰がどう使うか
- なぜ必要か (背景 / 課題)
- 完了の条件は何か (受け入れ基準)
- 関連する `PRD.md` / `design/DesignDoc.md` の節 (統合モードでは DesignDoc の Why / What 節)

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

ユーザーが起票を承認した場合のみ、`context/project.yml` の `tracker` (CLI / project) を使って起票する:

```sh
<tracker.cli> issue create --repo <tracker.project> --title "<title>" --body-file <requirements.md path>
```

### 5. 完了時の案内 (必須)

起票 (または起票なしでの draft 確定) の直後、必ず以下を報告する:

```text
🎉 要求の整理が完了しました。

【requirements doc】 specs/<...>/requirements.md
【Issue】 {番号 / URL、起票しなかった場合は「未起票」}

次工程:
  `spec-lifecycle {issue 番号 or spec path}` で設計を開始します。
```

spec-dir のリネームが必要であればあわせてユーザーに確認する。

## 停止条件

- 受け入れ基準が EARS 風に書けない (誰が / いつ / 何をするか不明)
- 関連 PRD / Design Doc 節が特定できず、上位文書と矛盾する可能性が残る
- ユーザーが draft を承認していない状態で起票しようとしている
- `Spec Workflow Contract` の必須値 (Tracker CLI / Repo) が欠落している
