---
type: context
title: <文書名>
description: <索引に出す 1 行説明。何が書いてあるかを具体的に>
keywords: [<検索の手掛かり>, <API 名>, <用語>]
governs:
  # この文書が語る契約の実装場所。コードだけでなく設定ファイルでもよい。
  # 鮮度検査の対象外にするなら governs と verified_commit の両方を消す。
  - <path/to/impl>
# 最後に実装と突き合わせた commit。未確認なら unverified のままにする。
verified_commit: unverified
---

# Engineering Conventions

shared config / root task / repository quality gate の境界規約。toolchain 一覧は [toolchain.md](toolchain.md)、プロジェクト固有コマンドは [context/project.md](project.md)。

## Shared Config Boundary

- 共有設定 (tsconfig / lint / test 等) をどこで export し、どう参照するか。

## Root Task Boundary

- どのタスクを root から束ねるか、どれを直実行するか。
- commit 前に通す自動検査 (pre-commit hook 等)。

## Repository Quality Gate

- repository 全体に対する検査 (依存境界 / dead code / 型) の正本 config と実行点。
- false positive を避けるための除外方針。
