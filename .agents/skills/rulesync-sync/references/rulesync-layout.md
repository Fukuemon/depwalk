# `.rulesync/` Layout

`.rulesync/` は本リポジトリの AI 設定の **単一情報源 (Single Source of Truth)**。`npx rulesync generate` により各 provider 向けファイルへ展開される。

## 構造

```
.rulesync/
├── .aiignore               # AI 入力対象外パターン
├── hooks.json              # Claude Code hooks 設定
├── mcp.json                # MCP server 設定
├── permissions.json        # 権限プリセット
├── rules/
│   └── CLAUDE.md           # root rule (AGENTS / CLAUDE の本体)
├── skills/
│   ├── <skill-name>/
│   │   ├── SKILL.md
│   │   ├── references/<topic>.md
│   │   └── assets/<file>
│   └── ...
└── subagents/              # subagent 定義 (未使用)
```

## 編集対象の判断軸

| 変更したいもの                           | 編集先                                              |
| ---------------------------------------- | --------------------------------------------------- |
| リポジトリ横断のルール / 規約            | `rules/CLAUDE.md`                                   |
| 新しい workflow / capability を skill 化 | `skills/<new-skill>/SKILL.md` を新規作成            |
| 既存 skill の手順を更新                  | 既存 `skills/<skill>/SKILL.md` または `references/` |
| skill 内の詳細手順を分割                 | `skills/<skill>/references/<topic>.md` を追加       |
| hooks / MCP / permissions                | `hooks.json` / `mcp.json` / `permissions.json`      |

## 直接編集禁止

次のファイル / ディレクトリは生成物。手で触ると `rulesync generate` で上書きされる:

- `AGENTS.md`
- `CLAUDE.md`
- `.codex/`
- `.claude/` (※ `.claude/skills/` も含む — skill の正本は `.rulesync/skills/`)
- `.cursor/`

## skill 作成時のフォーマット契約

正本は `skill-contract.md` (Skill 共通契約 — name / description / 行数 / 必須セクション / 重複禁止)。ここには再掲しない。

## 常時ロードされるもの / されないもの

- `rules/CLAUDE.md` (root rule) は **毎ターン全文ロードされる**。規範の本文を足さず、正本へのポインタ 1 行に畳む
- 規範の本文は skill の `references/<topic>.md` に置き、必要になった phase / トピックで Read させる (現行の 2 大契約は `spec-lifecycle` の `references/spec-contract.md` と `rulesync-sync` の `references/skill-contract.md`)
