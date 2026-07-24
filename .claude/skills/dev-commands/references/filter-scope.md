# Filter Scope

タスクを **特定 module にスコープ** するための短いガイド。具体的な module 名・コマンドは [context/project.yml](../../../../context/project.yml) を正本とする。

## 基本

- 全体実行 → 全体向けコマンドをそのまま実行
- 単一 module → そのプロジェクトの module フィルタ機構を使う (monorepo / workspaces の場合)

## monorepo / workspaces の場合

- パッケージマネージャや task runner の filter 機構 (例: `--filter <module>`) を使う。
- 依存 graph を含めた実行 (上流 / 下流を含む) や、変更分のみの実行は、ツールの selector 構文に従う。
- selector 構文はツールやバージョンで挙動が異なるため、公式 docs を確認する。

## アンチパターン

- module 境界を無視して依存を追加し、workspace 境界を壊す
- 全体検査が遅いからと特定 module だけで済ませ、CI が境界違反を取りこぼす
