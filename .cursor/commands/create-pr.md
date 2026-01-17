---
alwaysApply: false
---

# PR 自動生成コマンド

## 概要

現在のブランチの変更内容から、Pull Request を自動生成するためのコマンドです。
GitHub MCP サーバーを使用して、差分とコミット履歴を分析し、`.cursor/rules/pr-message-format.mdc` に沿った PR を作成します。

## 前提条件

- 現在のブランチがリモートにプッシュ済みであること
- main/develop ブランチ以外の作業ブランチにいること
- GitHub MCP サーバーが設定済みであること
- PR メッセージの書き方は、`.cursor/rules/pr-message-format.mdc` で定義された規約に従うこと

## 引数

| 引数 | 位置 | 必須 | 説明 |
|---|---|---|---|
| `base` | 1 | - | マージ先ブランチ（省略時: `develop`） |
| `draft` | 2 | - | ドラフト PR として作成するか（省略時: `false`） |

## 実行手順

1. 現在のブランチを確認（main/develop の場合はエラー）
2. リモートへのプッシュ状況を確認
3. ベースブランチとの差分を取得
   - `git diff --name-status` で変更ファイル一覧
   - `git log` でコミット履歴
4. 差分とコミット履歴から PR タイトルと本文を生成
   - タイトル: ブランチ名と変更内容から推測
   - 本文: `.cursor/rules/pr-message-format.mdc` のテンプレートに沿って構造化
5. GitHub MCP サーバーの `create_pull_request` ツールで PR を作成

## GitHub MCP サーバーの使用

### create_pull_request ツール

PR 作成には以下のパラメータを使用します：

| パラメータ | 必須 | 説明 |
|---|---|---|
| `owner` | ○ | リポジトリオーナー |
| `repo` | ○ | リポジトリ名 |
| `title` | ○ | PR タイトル |
| `body` | - | PR 本文（Markdown対応） |
| `head` | ○ | 変更を含むブランチ（現在のブランチ） |
| `base` | ○ | マージ先ブランチ |
| `draft` | - | ドラフト PR として作成（デフォルト: false） |

### 呼び出し例

```json
{
  "tool": "create_pull_request",
  "arguments": {
    "owner": "Fukuemon",
    "repo": "depwalk-wt",
    "title": "feat: ユーザー認証機能を追加",
    "body": "## 概要\n\nこのPRでは、JWTを使用したユーザー認証機能を実装します。\n\n## 関連：issue\n\n- #123\n\n## 対応内容\n\n- ログインエンドポイントの追加\n- ログアウトエンドポイントの追加\n- 認証ミドルウェアの追加",
    "head": "feature/user-auth",
    "base": "develop",
    "draft": false
  }
}
```

## PR 情報の収集コマンド

AI が PR を自動生成する際に使用する情報：

```bash
# 現在のブランチ名を取得
git branch --show-current

# リポジトリ情報を取得（owner/repo）
git remote get-url origin

# ベースブランチとのマージベースを取得
git merge-base origin/develop HEAD

# 変更ファイルの一覧
git diff --name-status $(git merge-base origin/develop HEAD)...HEAD

# 変更の統計情報
git diff --stat $(git merge-base origin/develop HEAD)...HEAD

# コミット履歴
git log origin/develop..HEAD --oneline

# 詳細な差分（必要に応じて）
git diff $(git merge-base origin/develop HEAD)...HEAD
```

## PR タイトルの決定ルール

### ブランチ名から Prefix を推測

| ブランチ接頭辞 | Prefix |
|---|---|
| `feature/` | `feat` |
| `fix/` | `fix` |
| `refactor/` | `refactor` |
| `perf/` | `perf` |
| `test/` | `test` |
| `docs/` | `docs` |
| `build/` | `build` |
| `ci/` | `ci` |
| `chore/` | `chore` |

### タイトル生成の優先順位

1. コミットメッセージの Prefix とサマリを参照
2. ブランチ名から目的を推測
3. 変更ファイルの内容から推測

## PR 本文のテンプレート

`.cursor/rules/pr-message-format.mdc` に従い、以下の構造で生成：

```markdown
## 概要
> このPRで実装・修正した内容の要約を記載

## 関連するissue
> このPRが関連しているissueをリンクさせる

## 対応内容
> このPRで対応した内容を記載する

- 対応内容1の説明
- 対応内容2の説明
- 対応内容3の説明

## 技術的な詳細（任意）
> 実装の詳細や設計上の意図を記載
> 必要に応じてシーケンス図やフローチャート図などのUML図を追加する（mermaid記法）

## テスト内容
> 実施したテストの種類（ユニットテスト、E2Eテスト、手動確認など）
> 主要な動作確認の結果

## チェックリスト
> 下記のチェックリストを確認し、問題ないことを確かめる

- [ ] 実装タスクの場合、単体テストを作成し、成功しているか。
- [ ] 仕様・設計・実装に相違はないか（差異がある場合、正しい方に合わせて更新すること）

## 残作業
> 残作業がある場合は下記に記載し、対応したissueを作成してリンクさせる

- [ ]
- [ ]
- [ ]

## 参考
> 参考になった文献やがあれば貼り付ける
```

## フォーマット例

### 使用例 1: デフォルト（develop へマージ）

```bash
# main ブランチへの PR を作成
@create-pr
```

### 使用例 2: ベースブランチを指定

```bash
# develop ブランチへの PR を作成
@create-pr develop
```

### 使用例 3: ドラフト PR として作成

```bash
# ドラフト PR として作成
@create-pr develop true
```

## フォールバック（MCP が使用できない場合）

GitHub CLI (`gh`) を使用して PR を作成：

```bash
# ブランチ名とベースブランチを取得
BRANCH=$(git branch --show-current)
BASE="develop"

# PR 作成（タイトルとメッセージは手動で指定）
gh pr create --title "<Prefix>: <サマリ>" --body "<本文>" --base "$BASE"

# または対話的に作成
gh pr create --base "$BASE"
```

## ノート

- PR タイトルおよび本文のフォーマットは、`.cursor/rules/pr-message-format.mdc` のルールに従ってください
- コミットメッセージのフォーマットは、`.cursor/rules/commit-message-format.mdc` の規約に従ってください
- PR 作成前に `git push` でリモートにプッシュ済みであることを確認してください
- MCP サーバーが利用できない場合は、GitHub CLI (`gh`) をフォールバックとして使用できます
