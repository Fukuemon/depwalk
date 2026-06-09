# SDD Template

Spec Driven Development (SDD) のための **汎用 AI ワークフローテンプレート**。PRD / Design Doc / context library / spec を一貫した正本構造で管理し、AI エージェントが後続実装できる粒度の設計文書を作る。

設定は `.rulesync/` を単一情報源とし、`rulesync` で Cursor / Claude Code / Codex CLI 等の各 provider へ展開する。

## ドキュメント階層 (Why / What / How)

| 層                | 文書                                          | 役割                                               |
| ----------------- | --------------------------------------------- | -------------------------------------------------- |
| Why / What        | [PRD.md](PRD.md) (統合時は Design Doc に内包) | 誰のどの課題を、なぜ・何で解決するか               |
| How (全体像)      | [design/DesignDoc.md](design/DesignDoc.md)    | system landscape / モジュール責務 / 横断方針       |
| How (feature)     | [design/features/](design/features/)          | feature 単位の設計                                 |
| How (規約 / 契約) | [context/](context/)                          | 技術規約・codebase architecture・運用契約          |
| 固有値            | [context/project.md](context/project.md)      | repo / 命名 / コマンド / 対象ドメイン / トラッカー |
| 意思決定          | [adr/](adr/)                                  | 長期参照する技術選定・境界                         |
| 作業文書          | [specs/](specs/)                              | issue / 機能単位の要求・設計・テスト観点           |

操作契約の正本は [.rulesync/rules/CLAUDE.md](.rulesync/rules/CLAUDE.md) (生成元)。

## はじめかた (新規プロダクトへの適用)

1. **PRD / Design Doc を作る** — `design-doc` skill。要件規模から PRD の要否を判定し、分離モード (PRD.md + DesignDoc.md) か統合モード (DesignDoc に Why/What を内包) を選ぶ。
2. **context を初期化する** — `context-bootstrap` skill。Design Doc と短い対話から [context/project.md](context/project.md) と `context/*.md` を生成する。`<PLACEHOLDER>` を埋める。
3. **AI 設定を同期する** — `rulesync-sync` skill。`.rulesync/` から各 provider 設定 (`AGENTS.md` / `CLAUDE.md` / `.cursor/` / `.codex/`) を生成する。
4. **issue 駆動で進める** — `spec-*` skill 群 (intake → specify → clarify → design → sync → tasks → review)。`spec-lifecycle` が phase を orchestration する。

## 文書品質

すべての設計文書は `technical-writing` skill の 5 原則 (曖昧さ排除 / 意思決定理由 / AI 実装可能な粒度 / 可読構造 / 過剰装飾の回避) に従う。

## ディレクトリ

```text
.rulesync/   # AI 設定の単一情報源 (rules / skills / hooks / permissions / mcp)
context/     # 技術規約・運用契約 + project.md (固有値)
design/      # Design Doc (landscape) と feature 設計
templates/   # PRD / Design Doc / context / spec / feature / requirements / adr のテンプレート
adr/         # Architecture Decision Records
hooks/       # protected branch / 文書検証 / markdown 整形などの hook
```
