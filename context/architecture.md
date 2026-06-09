# Codebase Architecture

> 最終更新: 2026-06-10

コードベースの **package / runtime / state boundary と依存方向**。全体像 (system landscape, モジュール責務) は [design/DesignDoc.md](../design/DesignDoc.md) を正本とし、本書は境界規約を扱う。プロジェクト固有の構成は [context/project.md](project.md) を参照する。

## Package Boundary

依存方向は **Core 内は単方向、Core → Analyzer は Protocol 経由のみ** とする (DesignDoc 設計原則 P1〜P4)。

- CLI → Core のみに依存する。
- Core 内: `Traversal Engine` → `Graph Engine` → `Model`、`Output Engine` → `Graph Engine` / `Model`。Model は他に依存しない。
- Core → Analyzer は `Analyzer SPI` (Protocol 境界) のみを介する。Core は Analyzer の内部 (使用ライブラリ・言語ランタイム) を知らない。
- Analyzer は `Model` (`MethodSymbol` / `CallEdge` / `SourceLocation`) のスキーマにのみ依存する。Core の内部実装には依存しない。
- **禁止経路**: Core から特定言語ランタイム / Analyzer 実装への直接依存。Analyzer 追加で Core に差分が出ないこと (S5)。

> 共有コードの昇格条件・循環依存の検査は実装スタック確定後に [engineering.md](engineering.md) の quality gate で定める。

## Runtime Boundary

- **マルチプロセス**: Core と Analyzer は **別プロセス**。STDIN / STDOUT 上の JSONL で通信する (DesignDoc「Communication Protocol」)。
- Core は言語非依存ランタイムで動く (実装言語は未定 / A1 で Kotlin は不採用)。Analyzer は対象言語のランタイム上で動く (Java Analyzer → JVM)。
- 解析は **静的解析**のみ。実行時情報・runtime trace には依存しない (Non Goals)。

## State Boundary

- 解析対象ソースは **読み取り専用**。depwalk は対象リポジトリを書き換えない。
- 中間状態 (呼び出しグラフ) は Core のプロセス内に保持する。永続ストアは現時点で持たない。
