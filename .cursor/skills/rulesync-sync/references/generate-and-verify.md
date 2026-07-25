# Generate and Verify

`.rulesync/` 編集後、各 provider 向け設定へ展開する手順と検証方法。

## 1. 生成コマンド

```sh
make generate                      # sdd-template repo
make -f sdd-template.mk generate   # 消費 repo
```

- 実体は `scripts/generate.sh` (両 target が共通で呼ぶ唯一の生成シーケンス)
- 引数なしで全 provider に展開する
- rulesync のバージョンは `scripts/generate.sh` で **pin** している (上流リリースによる生成物の
  無断変化を防ぐ)。更新は動作確認のうえ generate.sh の bump commit で行う
- 生成先は `AGENTS.md` / `CLAUDE.md` / `.codex/` / `.claude/` / `.cursor/`
- `scripts/generate.sh` は rulesync の後に Cursor / Claude 用 normalizer を必ず実行する

### cursor cli.json の正規化が必要な理由

`rulesync generate` (>=8.x) は `.cursor/cli.json` に top-level の `version` / `editor` を
**常時出力**し、`permissions.deny` は deny エントリが 1 件以上ある時だけ出力する。一方
現行 cursor-agent CLI は `version` / `editor` を未知キーとして拒否し、`permissions.deny`
を必須配列として要求する。rulesync 側にこれらを抑止する設定が無いため、生成直後に
`scripts/fix-cursor-cli.sh` (jq で `version`/`editor` を除去し `deny` 配列を保証) を通す。
冪等なので何度実行してもよい。`.rulesync/permissions.json` に deny エントリを持たせると
deny 配列は rulesync 側でも出力される (スクリプトは欠落時の保険)。

### codex config.toml の正規化が必要な理由

`rulesync generate` (14.x 現在も) は `.codex/config.toml` に `default_permissions = "rulesync"` と
`":minimal" = "read"` を含む `[permissions.rulesync.filesystem]` block を出力する。この profile は
Codex Desktop で repository path が cwd として見えていても、`specs/` / `context/` / `design/` /
`.rulesync/` など通常の project file read が `Operation not permitted` になることがある
(14.x で `extends = ":workspace"` が併記されるようになったが、阻害が解消されたことを実機で
確認できるまでは normalizer を維持する)。生成直後に `scripts/fix-codex-config.sh` で
`default_permissions = "rulesync"` と `[permissions.rulesync.filesystem]` block を除去する。
MCP / hooks / network permissions は残す。冪等なので何度実行してもよい。

### Claude settings の正規化が必要な理由

Claude Code は commit と PR に attribution を既定で付ける。公式設定の `attribution.commit` と
`attribution.pr` を空文字にすると両方を無効化できるため、`scripts/fix-claude-settings.sh` が
生成後の `.claude/settings.json` にこの値を設定する。冪等なので何度実行してもよい。

## 2. 差分確認チェックリスト

```
- [ ] git status で生成先のみが変更されているか
- [ ] .rulesync/ 編集分と生成先の差分が論理的に一致するか
- [ ] AGENTS.md と CLAUDE.md の差分が同じ意図になっているか
- [ ] skill 追加時、.claude/skills/<name>/ が生成されているか
- [ ] skill 削除時、.claude/skills/<name>/ も消えているか
```

確認コマンド:

```sh
git status
git diff -- AGENTS.md CLAUDE.md
git diff -- .claude/ .codex/ .cursor/
```

## 3. 失敗時の切り分け

| 症状                       | 原因の候補                                              | 対処                                                                                            |
| -------------------------- | ------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `npx rulesync` がエラー    | frontmatter の YAML 不正、未対応フィールド              | エラーメッセージの該当ファイルを開き、frontmatter (`name` / `description` / `targets`) を見直す |
| 生成先に差分が出ない       | 編集ファイルが `.rulesync/` 外、または rulesync 対象外  | `references/rulesync-layout.md` で対象パスを再確認                                              |
| 不要なファイルが生成される | `targets` が広すぎる、あるいは過去の skill が残っている | `targets` を絞る、もしくは旧 skill を `.rulesync/skills/` から削除して再生成                    |
| 同じ内容が複数箇所に重複   | rule と skill に同じ内容を書いている                    | rule (`rules/CLAUDE.md`) は短く、詳細は skill 側へ寄せる                                        |

## 4. アンチパターン

- 生成先 (`AGENTS.md` 等) を直接編集 → 次回 `generate` で消える
- 生成失敗を回避するため `.rulesync/` ではなく生成先で帳尻合わせ → 不可逆な不整合
- skill の `name` を `claude-*` / `anthropic-*` で作る → Anthropic 規約違反
- SKILL.md 本体に長大な手順を直書き → 200 行超は `references/` へ分割
