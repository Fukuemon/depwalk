---
name: spec-issue-read
description: Fetches an issue from the repository's tracker and returns a structured summary. Use when the user references an issue by number, URL, or asks to "read issue" / "issue 確認".
targets:
  - "*"
---

# Spec Issue Read

Issue tracker から指定 issue を取得し、要約と推奨次アクションを返す。

## いつ使うか

- 指定された issue 番号 / URL の内容を確認したい
- spec を起こす前に対象 issue の現状を把握したい

## 先に読むもの

- `AGENTS.md` の `Spec Workflow Contract` — Issue Tracker / CLI / Repo の値を取得する

## 入力

- `$ARGUMENTS` から対象を特定する
  - 数字のみ → issue 番号
  - URL → 末尾の番号を抽出
  - 引数なし → ユーザーに番号を確認する

## 実行フロー

1. `context/project.md` の Issue Tracker から CLI (例: `gh`) と Repo を読む
2. Issue 詳細を取得する

   ```sh
   <ISSUE_CLI> issue view <number> --repo <REPO> \
     --json number,title,state,labels,assignees,milestone,createdAt,updatedAt,body
   ```

3. 取得結果を以下のフォーマットで整形して返す

   ```md
   ## Issue #{番号}: {タイトル}

   | 項目           | 値          |
   | -------------- | ----------- |
   | 状態           | Open/Closed |
   | ラベル         | ...         |
   | 担当者         | ...         |
   | マイルストーン | ...         |
   | 作成日         | ...         |
   | 更新日         | ...         |

   ### 概要

   (本文の要約 5〜8 行)

   ### 推奨次アクション

   - 新規 spec を起こす → `spec-draft`
   - 既存 spec を更新する → 既存 spec dir を提示
   ```

4. 既存 spec がある場合 (`specs/{番号}-*/`) はその path を併記する

## 停止条件

- issue が tracker 上に存在しない
- private で CLI が認可されていない
- `Spec Workflow Contract` が読めない / 必須値 (CLI / Repo) が欠落している
