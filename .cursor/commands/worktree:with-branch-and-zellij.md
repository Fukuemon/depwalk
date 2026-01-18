# Worktree with Branch and Zellij (git-wt)

タスク内容からブランチ名とタブ名（3-10文字、日本語可）を生成し、git worktreeを作成して、zellijで新しいタブを開き、Claude Codeを自動起動してタスクを開始する。

## 引数

ユーザーが指定した引数: $ARGUMENTS

## 実行手順

### Step 1: 引数の解析

引数 `$ARGUMENTS` を解析して入力タイプを判定する:
- `https://` または `http://` で始まる → Issue URLとして処理
- `.md` を含む、または `/` で始まる → ファイルパスとして処理
- それ以外 → タスク説明として処理

### Step 2: タスク内容の取得

入力タイプに応じてタスク内容を取得する:

**Issue URLの場合:**
`gh issue view <url>` または `gh pr view <url>` でタイトルと本文を取得

**ファイルパスの場合:**
Readツールでファイル内容を読み込む

**タスク説明の場合:**
引数をそのままタスク内容として使用

### Step 3: ブランチ名の生成

タスク内容から適切なブランチ名を生成する:
- 命名規則: `feature/xxx`, `fix/xxx`, `chore/xxx`, `docs/xxx`, `refactor/xxx`
- 日本語がある場合は英語に変換
- スペースはハイフンに変換
- 小文字で統一
- 例: "ユーザー認証機能を追加" → `feature/add-user-authentication`

### Step 3.5: zellijタブ名の生成

タスク内容から短いタブ名を生成する:
- **長さ: 3-10文字**
- **日本語も使用可能**
- タスクの要点を表す短い名前
- 例:
  - "ユーザー認証機能を追加" → `認証` または `auth`
  - "バグ修正: ログイン失敗" → `ログイン` または `login-fix`
  - "ドキュメント更新" → `docs`
  - "リファクタリング: API層" → `API` または `refactor`

### Step 4: worktree作成（git-wt）

`git-wt`（`git wt`）を使って worktree を作成/切替する。

- `git wt <branch|worktree>` は、対象が無ければ **ブランチとworktreeを作成**して切り替える
- `--nocd` を付けると **ディレクトリ移動せずに worktree パスを出力**する（このパスを zellij の `--cwd` に使う）:contentReference[oaicite:1]{index=1}

以下を実行して worktree のパスを取得する:

```bash
worktree_path="$(git wt --nocd "<generated-branch-name>" | tail -n 1)"
