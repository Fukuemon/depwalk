# Rulesync ドキュメント

## 概要

Rulesync は、複数の AI コーディングアシスタントツール間で、ルールや設定を一元管理・同期するための CLI ツールです。統一された設定ファイルから、各ツール固有の設定ファイルを自動生成することで、チーム全体で一貫性のある AI アシスタント環境を構築できます。

## このプロジェクトの設定

このプロジェクトでは、以下の `rulesync.jsonc` 設定を使用しています：

```jsonc
{
  "targets": [
    "copilot",      // GitHub Copilot
    "cursor",       // Cursor
    "claudecode",   // Claude Code
    "codexcli",     // Codex CLI
    "antigravity"   // Antigravity
  ],
  "features": [
    "rules",        // ルール定義
    "ignore",       // 除外対象
    "mcp",          // Model Context Protocol 設定
    "commands",     // コマンド定義
    "subagents",    // サブエージェント
    "skills"        // スキル定義
  ],
  "baseDirs": ["."],
  "delete": true,           // 既存ファイルを削除
  "verbose": false,         // 詳細ログ出力
  "global": false,          // プロジェクトスコープ
  "simulateCommands": false,
  "simulateSubagents": false,
  "modularMcp": false
}
```

### 設定の詳細

#### targets（対象ツール）
このプロジェクトでは、5つの AI アシスタントツールをサポート対象としています：

- **copilot**: GitHub Copilot
- **cursor**: Cursor エディタ
- **claudecode**: Claude Code
- **codexcli**: Codex CLI
- **antigravity**: Antigravity

#### features（同期する機能）
以下の機能すべてを同期対象としています：

1. **rules**: コーディング規約、プロジェクトルール
2. **ignore**: 各ツールの除外パターン設定
3. **mcp**: Model Context Protocol 関連の設定
4. **commands**: 定義済みコマンド
5. **subagents**: サブエージェント機能
6. **skills**: スキル定義

#### その他の設定

- **baseDirs**: `["."]` - プロジェクトルートを基準ディレクトリとして使用
- **delete**: `true` - 生成時に既存の生成済みファイルを削除
- **verbose**: `false` - 簡潔なログ出力
- **global**: `false` - グローバルモードではなく、プロジェクトスコープで動作
- **simulateCommands/Subagents**: `false` - シミュレーションモードは無効
- **modularMcp**: `false` - モジュラー MCP 機能は無効（実験的機能）

## Rulesync の主な機能

### 1. 初期化（init）

プロジェクトに Rulesync を導入する際の初期設定を行います。

```bash
npx rulesync init
```

これにより `.rulesync/` ディレクトリが作成され、統一ルールファイルの構造が整備されます。

### 2. インポート（import）

既存の各ツール用設定を統一形式に取り込みます。

```bash
npx rulesync import
```

各 AI ツールが個別に持っている設定を `.rulesync/` 形式に変換して取り込むことができます。

### 3. 生成（generate）

統一ルールから各ターゲットツール向けの設定ファイルを生成します。

```bash
npx rulesync generate
```

または、特定のターゲットと機能のみを指定：

```bash
npx rulesync generate --targets cursor,copilot --features rules,mcp
```

### 4. gitignore 設定

生成されるファイルを `.gitignore` に追加します。

```bash
npx rulesync gitignore
```

## 使用方法の流れ

### 基本的なワークフロー

1. **初期化**
   ```bash
   npx rulesync init
   ```

2. **ルールファイルの作成・編集**
   - `.rulesync/rules/` ディレクトリ内に Markdown 形式でルールを記述
   - コーディング規約、テスト基準、命名規則など

3. **設定ファイルの生成**
   ```bash
   npx rulesync generate
   ```

4. **生成結果の確認**
   - 各ツール固有のディレクトリに設定ファイルが生成される
   - `.cursor/rules/`, `.github/copilot/`, etc.

5. **ルール更新時の再同期**
   - ルールを変更したら再度 `generate` コマンドを実行
   - すべてのツールで一貫性が保たれる

### チーム開発での活用

1. **統一ルールの共有**
   - `.rulesync/` ディレクトリを Git で管理
   - チーム全体で同じルールを使用

2. **個別ツールの設定は Git 管理しない**
   - 生成されたツール固有の設定ファイルは `.gitignore` に追加
   - `rulesync.jsonc` と `.rulesync/` のみを管理

3. **メンバーごとの同期**
   ```bash
   git pull
   npx rulesync generate
   ```

## MCP サーバー機能

Rulesync は MCP（Model Context Protocol）サーバー機能を提供しており、AI エージェントから直接 Rulesync のファイル操作が可能です。

提供されるツール：
- `listRules` / `getRule` / `putRule` / `deleteRule`
- `listCommands` / `getCommand` / `putCommand` / `deleteCommand`
- `listSubagents` / `getSubagent` / `putSubagent` / `deleteSubagent`
- `listSkills` / `getSkill` / `putSkill` / `deleteSkill`
- ignore ファイルや MCP 設定の操作


## 注意点

### 1. 実験的機能
- `modularMcp`, `skills`, シミュレーション機能などは実験的
- 本番環境での使用には注意が必要

### 2. ツールのバージョン依存
- 各 AI ツールのバージョンによって動作が異なる可能性
- 定期的な動作確認が推奨される

### 3. グローバルモードの制限
- グローバルモードでは対応機能が限定的
- すべての機能を使うにはプロジェクトスコープを推奨

## トラブルシューティング

### 生成されたファイルが反映されない
1. 対象ツールを再起動
2. `--verbose` フラグで詳細ログを確認
   ```bash
   npx rulesync generate --verbose
   ```

### ルールの内容が期待通りでない
1. `.rulesync/` 内のルールファイルを確認
2. `rulesync.jsonc` の `features` 設定を確認
3. `delete: true` が設定されているか確認

### 既存設定が消えてしまう
- `delete: true` の場合、生成済みファイルは削除される
- 重要な既存設定は事前に `import` でバックアップ

## リファレンス

### インストール

```bash
# npm でグローバルインストール
npm install -g rulesync

# または npx で直接実行
npx rulesync <command>
```

### 主要コマンド

| コマンド | 説明 |
|---------|------|
| `init` | プロジェクトの初期化 |
| `import` | 既存設定のインポート |
| `generate` | 設定ファイルの生成 |
| `gitignore` | .gitignore への追加 |

### オプション

| オプション | 説明 |
|-----------|------|
| `--targets <tools>` | 対象ツールを指定 |
| `--features <features>` | 同期する機能を指定 |
| `--verbose` | 詳細ログを出力 |
| `--global` | グローバルモードで実行 |

## 関連リンク

- [Rulesync GitHub リポジトリ](https://github.com/dyoshikawa/rulesync)
- 対応ツール
  - [GitHub Copilot](https://github.com/features/copilot)
  - [Cursor](https://cursor.sh/)
  - [Claude Code](https://www.anthropic.com/)
  - [Codex](https://developers.openai.com/codex/cli/)
  - [Antigravity](https://antigravity.google/docs/get-started)
