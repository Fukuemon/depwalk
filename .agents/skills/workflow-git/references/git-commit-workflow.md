---
name: local.git-commit
description: git commit を作成する。branch 確認、staging、メッセージ生成、hook 失敗時対応まで扱う。
disable-model-invocation: true
---

# Git Commit Workflow

## 手順

### ステップ1: 現状把握

以下のコマンドを並列で実行する。

- `git status`
- `git diff --staged`
- `git diff`
- `git log --oneline -5`
- `git branch --show-current`

### ステップ2: ブランチ判定

- `main` / `master` / `develop` にいる場合は新規ブランチ作成を提案する
- それ以外のブランチにいる場合は、そのまま続行する

ブランチ名は [branch-naming.md](branch-naming.md) の規約に従う。

### ステップ3: ステージング判定

ステージ済みファイルがない場合:

- 会話の文脈や実装状況から、ステージすべきファイルを判断する
- `.env`、認証情報、シークレットを含むファイルがあれば警告する

### ステップ4: コミットメッセージ生成

変更理由が不明な場合だけ、必要最小限の確認を行う。

#### 基本原則

- `what` は差分から、`why` は会話文脈から引く
- typo 修正などの自明な変更を除き、本文に理由を書く
- 1 行目は `<type>(<scope>): <サマリ> #<issue/ticket番号>` の形式に合わせる

#### 主観的な判断には根拠を添える

- 検索結果: 呼び出し元がない、参照箇所が 0 件
- 意思決定の経緯: Issue や設計文書で廃止が合意された
- 技術的事実: 置き換え済み、EOL、互換条件
- エラーや不具合の証拠: CI 失敗、実行エラー

### ステップ5: ユーザー確認

以下を 1 回でまとめて確認する。

- branch: 新規作成が必要なら候補名、既存なら現在の branch 名
- staging 対象: `git add` するファイル一覧
- commit message: 生成した全文

ユーザー承認後、必要な順で `git switch -c`、`git add`、`git commit` を実行する。

### ステップ6: hook 失敗時の対応

pre-commit hook が失敗した場合:

1. エラー内容を分析する
2. 問題を修正する
3. 修正ファイルを再ステージする
4. 新規 commit を作成する

`--amend` は使わない。hook 失敗時に `--amend` を使うと直前の commit を書き換える危険がある。

## 禁止事項

- `git push` はユーザーが明示的に依頼した場合のみ行う
- `--amend` は使わない
- `--no-verify` は使わない
- `.env`、認証情報、シークレットファイルの commit は警告する
