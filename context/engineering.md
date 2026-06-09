# Engineering Conventions

> 最終更新: 2026-06-10

shared config / root task / repository quality gate の境界規約。toolchain 一覧は [toolchain.md](toolchain.md)、プロジェクト固有コマンドは [context/project.md](project.md)。

> 本プロダクトは設計フェーズで、アプリの shared config / build task は未確定。現状は **ドキュメント repo としての quality gate** のみ確定している。

## Shared Config Boundary

- 共有設定 (lint / test / build config) の配置は実装スタック確定後に定める。
- 現状の共有契約はドキュメント正本パス ([project.md](project.md) Source of Truth) と AI 設定 (`.rulesync/` → 各 provider 生成)。

## Root Task Boundary

- commit 前検査は `lefthook` (pre-commit hook) が束ねる。設定は repo root の `lefthook.yml`。
- アプリ実装の root task (build / test の束ね) は実装着手時に定義する。

## Repository Quality Gate

- 現状の gate: Markdown / ドキュメント整合 (lefthook 経由)。AI 設定は `.rulesync/` を正本とし、生成物 (`AGENTS.md` / `CLAUDE.md` / `.codex/` / `.claude/` / `.cursor/`) の直接編集を禁止する。
- 実装着手後: 依存境界 (Core → Analyzer は Protocol のみ / [architecture.md](architecture.md) の禁止経路) の自動検査を quality gate に追加する。
