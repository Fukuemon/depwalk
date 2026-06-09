# Generate and Verify

`.rulesync/` 編集後、各 provider 向け設定へ展開する手順と検証方法。

## 1. 生成コマンド

```sh
npx rulesync@latest generate
```

- 引数なしで全 provider に展開する
- `@latest` を明示するのは旧キャッシュ版を踏まないため
- 生成先は `AGENTS.md` / `CLAUDE.md` / `.codex/` / `.claude/` / `.cursor/`

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
