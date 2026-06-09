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

- `name` は kebab-case、`anthropic` / `claude` を含めない (Anthropic 規約)
- `description` は third-person、「何をする + いつ起動」両方を含める (1024字以内)
- `targets: ["*"]` を frontmatter に置く (本リポジトリの skill 規約)
- SKILL.md 本体は **200 行未満**、詳細は `references/<topic>.md` へ1階層深さで分離
- 必須セクション: `いつ使うか` / `先に読むもの` / `実行フロー` / `停止条件`
- project 固有名 (CLI / path / framework) を直書きせず、`AGENTS.md` の `Spec Workflow Contract` の値を参照する
