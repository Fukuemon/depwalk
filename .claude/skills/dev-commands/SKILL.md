---
name: dev-commands
description: >-
  タスクに対応するプロジェクトコマンド (dev / build / lint / format / typecheck / test / e2e / 分析)
  を context/project.md のコマンド契約から解決し、単一 module へのスコープ要否を判定する。"どう動かす" / "テストの回し方"
  / "コマンド一覧" で起動する。
---

# Dev Commands

本リポジトリで **タスクを正しいコマンドで実行する** ための入口。新しいコマンドを発明せず、[context/project.md](../../../context/project.md) の Quick Commands 契約に沿って実行する。

## いつ使うか

- 開発サーバ / build / test / lint / format / e2e / 解析 を実行したい
- 特定 module だけにスコープして実行したい
- pre-commit や CI の前にローカル検証を回したい

## 先に読むもの

- [context/project.md](../../../context/project.md) — Quick Commands / 対象ドメイン (固有値の正本)
- `references/command-matrix.md` — コマンド分類と用途の解説
- `references/filter-scope.md` — module スコープの考え方 (workspace / monorepo の場合)
- `references/e2e-runtime.md` — E2E の env contract (該当する場合)

## 入力

- 何をしたいか (dev / build / test / lint / format / e2e / 解析)
- 対象 (全体 / 特定 module)

## 実行フロー

1. **意図の特定**:
   - 開発起動 → dev 系
   - 公開物の生成 → build
   - コード検査 → lint / typecheck / format(:check)
   - 動作確認 → unit test / e2e
   - 健全性検査 → dead code / 依存境界 等 (プロジェクトが持つ場合)
2. **対象スコープの決定** (`references/filter-scope.md`): 全体か特定 module か。monorepo の場合は module フィルタを使う。
3. **コマンド解決**: `context/project.md` の Quick Commands 表から該当コマンドを引く。表に無い操作は発明せず、ユーザーに確認するか project.md への追記を提案する。
4. **E2E** は `references/e2e-runtime.md` の env contract に従う (プロジェクトが E2E を持つ場合)。
5. **pre-commit ゲート** がある場合は、hook 経由で自動実行される。手動で先に流したいときは該当コマンドを直接実行する。

## 停止条件

- 対象 module 名が `context/project.md` の対象ドメインに存在しない (typo を疑う)
- `context/project.md` の Quick Commands に無いコマンドを実行しようとしている (まず project.md を確認・追記)
- `context/project.md` がまだ `<PLACEHOLDER>` のまま (先に `context-bootstrap` を案内する)
- format を破壊的 (`--write` 系) に流そうとしているが、レビュー前で破壊的な懸念がある (まず確認系を提案)
