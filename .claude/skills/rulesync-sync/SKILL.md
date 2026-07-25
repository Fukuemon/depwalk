---
name: rulesync-sync
description: >-
  AI 設定の正本 `.rulesync/` を編集し、AGENTS.md / CLAUDE.md / .codex / .claude / .cursor
  を全 provider 向けに再生成する。AI rule / skill / subagent の追加・変更、"AGENTS.md 更新" /
  "CLAUDE.md 変えて" / "rulesync" で起動する。
---

# Rulesync Sync

本リポジトリの AI 設定 (`AGENTS.md` / `CLAUDE.md` / `.codex/` / `.claude/` / `.cursor/` 等) は `.rulesync/` から生成される。本 skill は **編集対象を `.rulesync/` に正しく寄せ、生成結果を検証する** ための workflow。

## いつ使うか

- `AGENTS.md` や `CLAUDE.md` を書き換えたい
- 新しい skill / rule を追加したい
- 既存 skill (`SKILL.md` / `references/`) を更新したい
- 生成先と `.rulesync/` の差分を確認したい

## 先に読むもの

- `AGENTS.md` の `Documents (正本)` 節 — `.rulesync/rules/CLAUDE.md` が正本で生成先は触らない原則
- `references/rulesync-layout.md` — `.rulesync/` 配下の構造と編集先判断
- `references/generate-and-verify.md` — `npx rulesync generate` 実行と差分確認手順

## 入力

- 変更内容 (rule の追加 / skill の更新 / 既存セクションの修正 など)
- 影響範囲 (rule なのか skill なのか、対象 provider)

## 実行フロー

0. **repo 種別の判定**: `.rulesync/rules/CLAUDE.md` が symlink なら消費 repo で作業している。
   - 編集は symlink 経由で **sdd-template 側の実体** に入る (消費 repo では未追跡のため commit できない)
   - 変更の commit は sdd-template repo 側で行い、消費 repo では生成物のみ commit する (`AGENTS.md` の「共有プロセス層の正本」)
1. **編集対象の決定** (`references/rulesync-layout.md`):
   - リポジトリ横断のルール → `.rulesync/rules/CLAUDE.md` (root rule)
   - 新規 skill → `.rulesync/skills/<skill-name>/SKILL.md` + `references/`
   - 既存 skill の修正 → 該当 `.rulesync/skills/<skill-name>/` 配下
   - ❌ `AGENTS.md` / `CLAUDE.md` / `.claude/` / `.codex/` / `.cursor/` を直接編集してはいけない
2. **編集**: `.rulesync/` 配下の対象ファイルだけを編集する
3. **生成**: 次を実行する (生成後に cursor cli.json の正規化を必ず通す。理由は `references/generate-and-verify.md`)
   ```sh
   npx rulesync@latest generate
   bash scripts/fix-cursor-cli.sh
   ```
4. **差分確認** (`references/generate-and-verify.md`):
   - `git status` で生成先 (`AGENTS.md`, `CLAUDE.md`, `.codex/`, `.claude/`, `.cursor/`) に意図どおりの差分が出ているか
   - `git diff -- AGENTS.md CLAUDE.md` で内容を確認する
   - `.rulesync/` 側の編集と生成先の差分が一致していなければ、`.rulesync/` を修正して再実行する
5. **検証**: `make check` を実行する (skill 契約の機械検査 + 生成物 drift 検査)。通らない状態で終わらない
6. **生成失敗時**: 生成先を手作業で合わせず、`.rulesync/` 側の失敗理由を解消して再実行する
7. 次アクションを提案: skill を追加した場合は `dev-commands` / `workflow-git` 経由でテスト・コミットを促す

## 停止条件

- `.rulesync/` 外を直接編集しようとしている
- 消費 repo で `.rulesync/` 配下を commit しようとしている (正本は sdd-template repo)
- `npx rulesync@latest generate` が失敗し、エラーメッセージから原因を切り分けられない
- 生成先の差分が `.rulesync/` の編集意図と一致せず、矛盾の原因が特定できない
- 既存 skill の `name` / `description` を書き換えようとしているが、参照側 (他 skill / docs) の影響を確認していない
- root rule (`.rulesync/rules/CLAUDE.md`) を編集しようとしているが、内容が Design Doc / PRD と矛盾している (先に `spec-lifecycle` の sync phase 提案を検討)
