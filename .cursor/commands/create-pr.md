# PR 自動生成コマンド

## 概要

現在のブランチの変更内容から、Pull Request を自動生成するためのコマンドです。
GitHub MCP サーバーを使用して、差分とコミット履歴を分析し、`.cursor/rules/pr-message-format.mdc` に沿った PR を作成します。
また、ブランチ名から関連Issueを特定し、Issue の確認・更新、残作業がある場合の新規Issue作成も行います。

## 前提条件

- 現在のブランチがリモートにプッシュ済みであること
- main/develop ブランチ以外の作業ブランチにいること
- GitHub MCP サーバーが設定済みであること
- PR メッセージの書き方は、`.cursor/rules/pr-message-format.mdc` で定義された規約に従うこと
- ブランチ名は `<prefix>/<issue番号>` の形式であること（例: `feature/3`, `fix/42`）

## 引数

| 引数 | 位置 | 必須 | 説明 |
|---|---|---|---|
| `base` | 1 | - | マージ先ブランチ（省略時: `develop`） |
| `draft` | 2 | - | ドラフト PR として作成するか（省略時: `false`） |

## 実行手順

1. 現在のブランチを確認（main/develop の場合はエラー）
2. ブランチ名からIssue番号を抽出（例: `feature/3` → `#3`）
3. 関連Issueの内容を取得・確認（GitHub MCP `get_issue` を使用）
4. 必要に応じてIssueを更新（対話的に内容を整理）
5. リモートへのプッシュ状況を確認
6. ベースブランチとの差分を取得
   - `git diff --name-status` で変更ファイル一覧
   - `git log` でコミット履歴
7. 差分とコミット履歴から PR タイトルと本文を生成
   - タイトル: ブランチ名と変更内容から推測
   - 本文: `.cursor/rules/pr-message-format.mdc` のテンプレートに沿って構造化
8. 残作業がある場合、新しいIssueを作成（GitHub MCP `create_issue` を使用）
9. GitHub MCP サーバーの `create_pull_request` ツールで PR を作成

## ブランチ名からIssue番号を抽出

```bash
# ブランチ名からIssue番号を抽出
BRANCH=$(git branch --show-current)
ISSUE_NUMBER=$(echo "$BRANCH" | sed -E 's/^[^/]+\///')
# 例: feature/3 → 3, fix/42 → 42
```

## 関連Issueの確認・更新フロー

PR作成前に、ブランチに紐づくIssueの内容を確認し、必要に応じて更新します。

### 確認項目

1. Issueのタイトルと概要が実装内容と一致しているか
2. タスクの完了状況が最新か
3. 完了の定義を満たしているか
4. 追加で記載すべき情報がないか

### 更新が必要な場合

- コードベースの変更内容とIssueの記載内容に差異がある場合
- タスクの完了状況を更新する必要がある場合
- 実装中に判明した追加情報を記載する必要がある場合

Issue の整理・更新の詳細なフローは、`.cursor/commands/create-issue.md` を参照してください。
Issue メッセージのフォーマットは、`.cursor/rules/issue-message-format.mdc` に従ってください。

## 残作業用Issue作成フロー

PR作成時に残作業がある場合、または実装内容がブランチの目的と異なる部分がある場合は、新しいIssueを作成します。

### 残作業Issueを作成するケース

1. PR本文の「残作業」セクションに項目がある場合
2. 元のIssueのタスクで未完了の項目がある場合
3. 実装中に発見した追加の改善点がある場合
4. スコープ外だが関連する作業が発生した場合

### 作成するIssueの種類

| 残作業の内容 | Issue種類 | タイトル形式 |
|---|---|---|
| 新機能の追加実装 | 機能追加 | `✨ feature: <サマリ>` |
| バグの発見・修正 | バグ報告 | `🐛 bug: <サマリ>` |

Issue作成時は、`.cursor/rules/issue-message-format.mdc` のフォーマットに従ってください。

## GitHub MCP サーバーの使用

### get_issue ツール

関連Issueの取得には以下のパラメータを使用します：

| パラメータ | 必須 | 説明 |
|---|---|---|
| `owner` | ○ | リポジトリオーナー |
| `repo` | ○ | リポジトリ名 |
| `issue_number` | ○ | Issue番号 |

### 呼び出し例

```json
{
  "tool": "get_issue",
  "arguments": {
    "owner": "Fukuemon",
    "repo": "depwalk-wt",
    "issue_number": 3
  }
}
```

### update_issue ツール

Issueの更新には以下のパラメータを使用します：

| パラメータ | 必須 | 説明 |
|---|---|---|
| `owner` | ○ | リポジトリオーナー |
| `repo` | ○ | リポジトリ名 |
| `issue_number` | ○ | Issue番号 |
| `title` | - | 更新後のタイトル |
| `body` | - | 更新後の本文 |
| `state` | - | Issue の状態（`open` / `closed`） |

### 呼び出し例

```json
{
  "tool": "update_issue",
  "arguments": {
    "owner": "Fukuemon",
    "repo": "depwalk-wt",
    "issue_number": 3,
    "body": "## 概要\n更新された内容..."
  }
}
```

### create_issue ツール

残作業Issueの作成には以下のパラメータを使用します：

| パラメータ | 必須 | 説明 |
|---|---|---|
| `owner` | ○ | リポジトリオーナー |
| `repo` | ○ | リポジトリ名 |
| `title` | ○ | Issue タイトル |
| `body` | - | Issue 本文（Markdown対応） |
| `labels` | - | ラベルの配列 |

### 呼び出し例

```json
{
  "tool": "create_issue",
  "arguments": {
    "owner": "Fukuemon",
    "repo": "depwalk-wt",
    "title": "✨ feature: 残作業の実装",
    "body": "## 概要\n...\n\n## 完了の定義\n...\n\n## タスク\n- [ ] タスク1",
    "labels": ["enhancement"]
  }
}
```

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
    "body": "## 概要\n\nこのPRでは、JWTを使用したユーザー認証機能を実装します。\n\n## 関連するissue\n\n- Closes #3\n\n## 対応内容\n\n- ログインエンドポイントの追加\n- ログアウトエンドポイントの追加\n- 認証ミドルウェアの追加",
    "head": "feature/3",
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

# ブランチ名からIssue番号を抽出
BRANCH=$(git branch --show-current)
ISSUE_NUMBER=$(echo "$BRANCH" | sed -E 's/^[^/]+\///')

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

1. 関連Issueのタイトルを参照
2. コミットメッセージの Prefix とサマリを参照
3. ブランチ名から目的を推測
4. 変更ファイルの内容から推測

## PR 本文のテンプレート

`.cursor/rules/pr-message-format.mdc` に従い、以下の構造で生成：

```markdown
## 概要
> このPRで実装・修正した内容の要約を記載

## 関連するissue
> このPRが関連しているissueをリンクさせる
> 例: Closes #3

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
> 例: #10, #11

- [ ]
- [ ]
- [ ]

## 参考
> 参考になった文献やがあれば貼り付ける
```

## フォーマット例

### 使用例 1: デフォルト（develop へマージ）

```bash
# develop ブランチへの PR を作成
@create-pr
```

### 使用例 2: ベースブランチを指定

```bash
# main ブランチへの PR を作成
@create-pr main
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
ISSUE_NUMBER=$(echo "$BRANCH" | sed -E 's/^[^/]+\///')
BASE="develop"

# 関連Issueを確認
gh issue view "$ISSUE_NUMBER"

# PR 作成（タイトルとメッセージは手動で指定）
gh pr create --title "<Prefix>: <サマリ>" --body "<本文>" --base "$BASE"

# または対話的に作成
gh pr create --base "$BASE"

# 残作業Issueの作成（必要な場合）
gh issue create --title "✨ feature: 残作業のタイトル" --body "<本文>" --label "enhancement"
```

## ノート

- PR タイトルおよび本文のフォーマットは、`.cursor/rules/pr-message-format.mdc` のルールに従ってください
- コミットメッセージのフォーマットは、`.cursor/rules/commit-message-format.mdc` の規約に従ってください
- Issue メッセージのフォーマットは、`.cursor/rules/issue-message-format.mdc` のルールに従ってください
- Issue の整理・作成の詳細なフローは、`.cursor/commands/create-issue.md` を参照してください
- PR 作成前に `git push` でリモートにプッシュ済みであることを確認してください
- MCP サーバーが利用できない場合は、GitHub CLI (`gh`) をフォールバックとして使用できます
- 残作業がある場合は、必ず対応するIssueを作成してからPRを作成してください
