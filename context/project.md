# Project Profile

> 最終更新: 2026-06-10

このプロダクト固有の値 (リポジトリ / 命名 / コマンド / 対象ドメイン / トラッカー / 正本パス) を集約する **唯一の正本**。`.rulesync/rules/CLAUDE.md` と `spec-*` / `dev-commands` 等の skill は、固有値をハードコードせず本ファイルを読む。

<!--
記入ガイド:
- 新規プロダクトでは `<...>` のプレースホルダをすべて埋める。
- DesignDoc から導ける値は context-bootstrap skill が下書きする。
- 値を変えたら先頭の「最終更新」を更新し、依存する context/*.md との整合を確認する。
- 本プロダクトは設計フェーズ。実装スタック (Core 言語 / build) は未確定で、確定タイミングを各項目に明示する。
-->

## Repository Map

- リポジトリ管理: `ghq` 規約。`$(ghq root)/github.com/Fukuemon/depwalk`
- app repo: `github.com/Fukuemon/depwalk` (単一リポジトリ)
- infra repo (別管理の場合): なし

```text
depwalk/
├── design/        # 全体像 (landscape) と feature 設計
│   ├── DesignDoc.md       # system landscape (正本)
│   └── features/          # feature 単位の設計
├── context/       # 技術規約 / codebase architecture / 運用契約 (本ライブラリ)
├── specs/         # 機能単位の作業文書 (issue 駆動)
├── adr/           # 個別の意思決定
├── templates/     # 各文書のテンプレート
└── .rulesync/     # AI 設定の正本 (→ AGENTS.md / CLAUDE.md / .codex / .claude / .cursor を生成)
```

> 実装コード (Core / Analyzer) のディレクトリ構成は未定。Core 実装言語の確定後 (ADR 化予定) に本マップへ追記する。

## Naming Conventions

- パッケージ名: 未定 (Core 実装言語の確定後に規約を定める)
- コンポーネント / ファイル: 未定 (同上)
- ブランチ: `feature/<issue-id>`

## Quick Commands

| やりたいこと     | コマンド                                             |
| ---------------- | ---------------------------------------------------- |
| 開発起動         | 未定 (実装着手前)                                    |
| ビルド           | 未定 (実装着手前)                                    |
| Lint / typecheck | 未定 (実装着手前)                                    |
| Format (確認)    | 未定 (実装着手前)                                    |
| Unit test        | 未定 (実装着手前)                                    |
| E2E              | 未定 (実装着手前)                                    |
| 健全性検査       | `lefthook run pre-commit` (現状の repo quality gate) |
| 依存追加         | 未定 (実装着手前)                                    |

> 現状はドキュメント駆動の設計フェーズで、アプリのビルド/テストコマンドは未確定。実装スタック確定時に [toolchain.md](toolchain.md) と本表を同時更新する。

## 対象ドメイン (spec「実装対象」テーブル用)

`traversal`, `output`, `analyzer-protocol`, `java-analyzer`

- `traversal` — Caller / Callee 探索 (Traversal Engine)
- `output` — 出力形式 (Console / JSON / DOT / Mermaid; Output Engine)
- `analyzer-protocol` — Analyzer SPI + Communication Protocol (JSONL) + Model (言語非依存の共通契約)
- `java-analyzer` — Java/Spring 解析の言語別実装 (analyzer-protocol を実装)

## Issue Tracker

- Tracker: `GitHub`
- CLI: `gh`
- Repo: `Fukuemon/depwalk`
- Branch pattern: `feature/<issue-id>`

## Label Policy

Issue / PR のラベル体系の **唯一の正本**。`workflow-git` skill はラベル付与時に本節を読む。命名は **名前空間付き** (`<axis>:<value>`) とし、軸ごとに色を揃える。

### 軸とラベル

| 軸         | ラベル                                                                                                     | 付与ルール                                                     | 色        |
| ---------- | ---------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- | --------- |
| `type:*`   | `type:feature` / `type:bug` / `type:research` / `type:task` / `type:chore`                                 | **必須・1 件**。issue の種類 (issue-format と一致)             | `#1d76db` |
| `phase:*`  | `phase:design` / `phase:implementation`                                                                    | SDD ライフサイクル。設計 issue は `phase:design`               | `#5319e7` |
| `domain:*` | `domain:traversal` / `domain:output` / `domain:analyzer-protocol` / `domain:java-analyzer` / `domain:core` | **1 件以上**。上記「対象ドメイン」に対応。横断は `domain:core` | `#0e8a16` |
| (flag)     | `epic`                                                                                                     | 複数 phase / spec にまたがる大きい要件 (親)                    | `#fbca04` |

> `domain:*` は「対象ドメイン」節と一対一。ドメインを増やすときは両方を更新する。`type:*` は [workflow-git の issue-format](../.rulesync/skills/workflow-git/references/issue-format.md) の type と一致させる。

### 付与の最低ライン

- すべての issue に `type:*` を 1 件付ける。
- 実装対象が定まる issue には該当する `domain:*` を付ける。
- SDD の設計フェーズ issue には `phase:design`、実装フェーズには `phase:implementation`。
- 1 つの要件が複数 phase / spec に展開される親 issue には `epic` を付ける。

## Source of Truth (正本ドキュメントのパス契約)

`spec-*` skill が参照するパス。`Spec Workflow Contract` はこの表を読む。

- PRD: `PRD.md` (統合モードでは `design/DesignDoc.md` の Why/What 節 — 本プロダクトは統合モード)
- Design Doc (landscape): `design/DesignDoc.md`
- feature doc dir: `design/features/<feature>/`
- feature doc: `design/features/<feature>/DesignDoc_<feature>.md`
- context library: `context/<topic>.md`
- ADR: `adr/`
- spec dir: `specs/<issue-id>-<slug>/`
- spec body: `specs/<issue-id>-<slug>/index.md`
- spec prompts: `specs/<issue-id>-<slug>/prompts/`
- spec requirements (任意): `specs/<issue-id>-<slug>/requirements.md`
- spec review report (任意): `specs/<issue-id>-<slug>/review.md`

## Templates

- PRD: `templates/prd/template.md`
- Design Doc: `templates/design-doc/template.md`
- context: `templates/context/<topic>.md`
- spec core: `templates/specs/template.md`
- spec appendices: `templates/specs/appendices/<topic>.md`
- feature doc: `templates/features/template.md`
- requirements: `templates/requirements/template.md`
- adr: `templates/adr/template.md`

## Decision Priority

迷ったときの判断優先度 (DesignDoc 設計原則 P1〜P4 から導出):

1. 将来拡張性 (マルチ言語化 / Analyzer 追加で Core 無変更 — S5, P1/P4)
2. Core の言語非依存性 / 結合点の最小化 (Protocol のみで結合 — P2/P3)
3. デバッグ容易性 (JSONL でテキスト観測可能 — Communication Protocol)
4. 開発体験

技術選定の根拠は [Design Doc](../design/DesignDoc.md) と [ADR](../adr/)。
