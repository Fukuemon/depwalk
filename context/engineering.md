# Engineering Conventions

> 最終更新: YYYY-MM-DD

shared config / root task / repository quality gate の境界規約。toolchain 一覧は [toolchain.md](toolchain.md)、プロジェクト固有コマンドは [context/project.md](project.md)。

## Shared Config Boundary

- 共有設定 (tsconfig / lint / test 等) をどこで export し、どう参照するか。

## Root Task Boundary

- どのタスクを root から束ねるか、どれを直実行するか。
- commit 前に通す自動検査 (pre-commit hook 等)。

## Repository Quality Gate

- repository 全体に対する検査 (依存境界 / dead code / 型) の正本 config と実行点。
- false positive を避けるための除外方針。
