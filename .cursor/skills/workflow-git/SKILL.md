---
name: workflow-git
description: 本リポジトリの Git / GitHub 運用 workflow。ブランチ作成、コミット、Issue (ラベル含む)、PR の順序と停止条件を扱う。固有値は context/project.yml を読む。"branch 切って" / "コミットして" / "PR 作って" / "issue 起票" で起動する。
---
# Workflow Git

本リポジトリの Git / GitHub 操作を進めるときの入口スキル。プロジェクト固有値 (tracker / domains / labels) は [context/project.yml](../../../context/project.yml) を読む。

## いつ使うか

- 作業ブランチを切る
- コミットメッセージを作る
- GitHub Issue を起票する
- PR を作る、更新する

## 先に読むもの

- `context/project.yml` の `repos` (パス規約と repo 定義) / `labels`
- `references/operation-order.md` — 標準順序と保護ブランチガード
- issue 追跡 (`#<issue>` 記法、関連 link) は `references/commit-format.md` / `references/pr-format.md` / `references/issue-format.md` に内包

## 実行フロー

1. 現在の branch、差分、関連 Issue を確認する
2. 保護ブランチにいる場合は `feature/<issue-number>` 形式の作業ブランチへ切り替える
3. 実施する操作に応じて詳細 reference を読む
   - branch: `references/branch-naming.md`
   - commit: `references/commit-format.md`, `references/git-commit-workflow.md`
   - issue: `references/issue-format.md`, `references/issue-workflow.md`
   - 前段 phase の取りこぼし (要求漏れ / 設計漏れ) の起票: `references/derived-issue.md`
   - status ラベル遷移 + 状態遷移コメント: `references/issue-status.md`
   - pr: `references/pr-format.md`, `references/pr-workflow.md`
4. 実際の差分と会話文脈に基づいて本文を作成する
5. push / PR は明示依頼がある場合だけ実行する

## 停止条件

- 変更理由や関連 Issue が不明で、差分からも判断できない
- `main` / `master` / `develop` に直接コミットしようとしている
- app / package をまたぐ関連付けが必要なのに、リンク先や対象範囲が確定していない
- hook や validation が失敗し、原因を説明できない
