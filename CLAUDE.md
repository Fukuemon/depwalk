# Platform Agent Operating Contract

エージェントが本リポジトリで作業するときの **最短の操作ガイド**。詳細はそれぞれの正本へリンクする。本 contract は `.rulesync/rules/CLAUDE.md` が正本で、`AGENTS.md` / `CLAUDE.md` / `.codex/` / `.claude/` / `.cursor/` は生成物 (編集は `rulesync-sync` skill 経由)。

プロジェクト固有値 (リポジトリ / 命名 / コマンド / 対象ドメイン / トラッカー / 正本パス) は **本 contract に直書きせず** [context/project.md](context/project.md) を読む。

## Documents (正本)

- プロダクト要求 (Why / What): [PRD.md](PRD.md) — PRD を作らない統合モードでは [design/DesignDoc.md](design/DesignDoc.md) の Why/What 節
- 全体像 / モジュール責務 (How: landscape): [design/DesignDoc.md](design/DesignDoc.md)
- feature 単位の設計 (How: feature): [design/features/](design/features/)
- 技術規約 / codebase architecture / 運用契約 (How: 横断): [context/](context/)
- プロジェクト固有値: [context/project.md](context/project.md)
- 個別の意思決定: [adr/](adr/)
- 機能単位の作業文書: [specs/](specs/)
- 開発プロセス (skill 連鎖) の俯瞰図: [architecture.md](architecture.md)
- skill / rule 著作の非自明な判断記録: [decisions.md](decisions.md)

迷ったら **PRD (or 統合 DesignDoc の Why/What) → Design Doc → feature doc / context → ADR → spec** の順に読む。AGENTS.md と内容が衝突したら **Design Doc が正**。

## Setup (新規プロダクトでテンプレートを使い始めるとき)

1. `design-doc` skill で PRD / Design Doc を作る (PRD 要否を判定)。
2. `context-bootstrap` skill で [context/project.md](context/project.md) と `context/*.md` を生成する。
3. `rulesync-sync` skill で各 provider 設定を生成する。
4. 以降は `spec-*` skill で issue 駆動の設計・実装を進める。

> 共有テンプレートとして使う場合: 本テンプレ repo で `scripts/link.sh <消費 repo>` (または `make link ARGS=<消費 repo>`) を実行すると、共有プロセス層を消費 repo へ接続できる。固有値 (`context/` 等) は消費 repo 側で実ファイルとして記入する。詳細は [README.md](README.md)。

### 共有プロセス層の正本 (消費 repo で作業するとき)

- **skills / rules / subagents / hooks の正本は sdd-template repo**。消費 repo の `.rulesync/*` は sdd-template への symlink であり、編集すると **テンプレ側の実体が変わる** (消費 repo では未追跡のため commit できない)。
- したがって共有プロセス層の変更は: ① 編集 (symlink 経由でもテンプレ repo 直接でもよい) → ② **sdd-template repo 側で commit** → ③ 消費 repo で `rulesync generate` を再実行し **生成物だけを commit**、の順で行う。
- 消費 repo に commit するのは生成物 (`AGENTS.md` / `.claude/` 等)・`hooks/` (実ファイルコピー)・`lefthook.yml`・`.template-version` のみ。`.rulesync/` 配下を消費 repo に commit しない。
- 今いる repo がテンプレか消費側かは `.rulesync/rules/CLAUDE.md` が symlink かどうかで判定できる (symlink なら消費側)。

## Repository / Commands / Naming

- リポジトリマップ・Quick Commands・命名規約・対象ドメインは [context/project.md](context/project.md) を正本とする。
- コマンドのスコープ判断・E2E env contract・selector の使い分けは `dev-commands` skill。

## Skills (workflow 入口)

| Skill                  | 用途                                                                     |
| ---------------------- | ------------------------------------------------------------------------ |
| `design-doc`           | PRD / Design Doc を作る (Why/What/How、PRD 要否判定)                     |
| `context-bootstrap`    | `context/project.md` と context library を初期生成                       |
| `styleguide-documents` | 文書の品質基準・分割粒度 (doc を書く skill が参照)                       |
| `dev-commands`         | コマンド / スコープ / E2E env を解決 (`context/project.md` 基準)         |
| `rulesync-sync`        | AI 設定 (`.rulesync/` → 各 provider) の編集と生成                        |
| `workflow-git`         | branch / commit / issue / PR の Git 運用                                 |
| `agent-orchestrate`    | 複数 CLI エージェントを非対話・並列に呼ぶ基盤 (`context/ai-agents.md`)   |
| `multi-agent-review`   | diff / PR / spec を複数エージェントで並列 Rv し指摘を統合                |
| `skill-feedback`       | 利用中に見つけた skill / rule の不具合・改善点を `.rulesync/` に書き戻す |
| `context-harvest`      | 作業で得た横断的な知見を `context/` / feature doc / ADR に書き戻す       |
| `spec-*`               | Spec Driven Development 一式 (下記 `Spec Workflow Contract`)             |

skill は `.rulesync/skills/<name>/SKILL.md` が正本。直接呼び出すか slash command で起動する。

## Decision Priority

判断の優先度は [context/project.md](context/project.md) の Decision Priority を正本とする。技術選定の根拠は [Design Doc](design/DesignDoc.md) と [ADR](adr/)。

## Forbidden Patterns

- 生成物 (`AGENTS.md` / `CLAUDE.md` / `.codex/` / `.claude/` / `.cursor/`) の直接編集 (`rulesync-sync` 経由で `.rulesync/` を編集する)
- 消費 repo への `.rulesync/` 配下の commit (正本は sdd-template repo。上記「共有プロセス層の正本」)
- protected branch (例: `main` / `develop`) への直接コミット
- skill / rule へのプロジェクト固有名の直書き (`context/project.md` を読む)
- プロダクト固有の禁止事項は [context/project.md](context/project.md) / [context/](context/) に追記する

## Output Format Rules

コード調査結果は次の形式:

```text
- {説明}
  - `<path>:<line-range>`
```

repo path の記法 (ghq 等) は [context/project.md](context/project.md) の Repository Map に従う。

## Spec Workflow Contract

Spec Driven Development (SDD) skill 群 (`.rulesync/skills/spec-*/SKILL.md`) が参照する **唯一の contract**。skill 側に project 固有名 (path / CLI / framework) を直書きせず、固有値は [context/project.md](context/project.md)、運用ルールは本節を読む。

### 正本ドキュメント / Templates / Issue Tracker / 対象ドメイン

[context/project.md](context/project.md) の `Source of Truth` / `Templates` / `Issue Tracker` / `対象ドメイン` を参照する。

### 正本境界 (Source-of-Truth Boundary)

spec は **issue 単位** の文書、design (PRD / Design Doc / feature doc / context / ADR) は **製本となる正本**。両者の正本関係はライフサイクルで遷移する:

- **spec 作成〜clarify (`spec-lifecycle` の scaffold / clarify phase)**: spec が自身の決定の作業正本でよい。design へはまだ反映しない。
- **sync phase 実行時 (正本ハンドオフ)**: durable な設計成果 (IA / データモデル / フロー / アーキ判断) を design 側へ反映したら、以後 **design 側が正本**。同時に spec の該当節を「決定時スナップショット」に降格し、design への正本リンクを張る。
- **ハンドオフ後**: spec は論点 / 受け入れ基準 / レビュー / 実装分割 / 決定経緯のみを保持し、durable 成果の正本を二重に持たない (drift 防止)。

判定の目安: 「issue が閉じても残り続ける設計情報」は design 正本、「この issue の意思決定の記録」は spec。

**用語規約 (呼称)**: 「正本」の語は feature doc / context / ADR など durable な文書にのみ使う。handoff 後の spec を「正本」と呼ばない。他 spec への参照は「決定経緯」「決定時スナップショット」「issue 単位の作業記録」など位置づけが分かる語を使う。

### 文書メタ情報の同期

spec / ADR / context の本文を更新したら、対象文書の `更新日` / `ステータス` / `## 設計フェーズ状況` / `## レビュー` / `## 変更履歴` (存在するもの) を同時に更新する。他 issue / 他 spec への参照を追加した場合は、関連資料・未確定事項の状態も同期する。本文だけ更新してメタ情報が古いまま、を残さない。

### Phase Gate ルール

- 各 phase 完了時に `spec-review` (fresh-context evaluator) を通す
- PRD / Design Doc / feature doc / context / ADR と矛盾を検出したら sync phase を先に提案し、未解決のまま下流 phase に進まない
- spec 内「設計フェーズ状況」表が状態 (`未着手 / 進行中 / 完了 / レビュー済 / 保留`) を管理する
- 確定済みの判断 (解決済みの論点) を skill から上書きしない

### Skill 共通契約

- skill 内に project 固有名を直書きしない (本 contract と `context/project.md` を読む)
- skill 本体 (SKILL.md) は **200 行未満**、詳細は `references/<topic>.md` へ 1 階層深さで分離。100 行を超える reference は冒頭に目次を置く
- description は日本語・third-person で「何をする + いつ起動」を含め、発動トリガー語を引用符で列挙する。`いつ使うか` は description の言い換えにせず、追加のトリガー語 / 文脈のみ書く
- 必須セクション (正規名のみ使用): `いつ使うか` / `先に読むもの` / `実行フロー` / `停止条件`。`実行手順` / `生成手順` / `終了条件` 等の同義異名を使わない
- 任意セクション: `入力` / `中核原則` / `禁止事項` 等は追加してよい。手順の全体像は ASCII 図でなく `実行フロー` 内の番号付きステップ (複雑なら冒頭にコピー可能なチェックリスト) で示し、`ワークフロー` 節を重複して置かない
- **例外**: 品質基準のみを提供する reference 型 skill (例: `styleguide-documents`) は `先に読むもの` / `実行フロー` / `停止条件` を省略してよい
- 文書を書く skill は `styleguide-documents` skill を「先に読むもの」で参照する
- 同じ規範を複数ファイルに再掲しない。正本を 1 箇所に置き、他は 1 行で参照する
- 本契約の機械検査は `make check` (`scripts/check-skills.sh` + 生成物 drift 検査)。skill / rule / subagent を変更したら `make check` を通す
- skill の連鎖挙動 (呼び出し関係 / phase gate / 状態遷移) を変えたら、同 PR で [architecture.md](architecture.md) のシーケンスを更新する
- 非自明な著作判断 (token コスト / ツール制約 / スコープの線引き) は [decisions.md](decisions.md) に 1 判断 1 セクション (`背景 / 判断 / 理由 / 今後`) で記録し、逆戻しの前に読む

### SDD Skill 一覧

| Skill              | 役割                                                | 対応 phase           |
| ------------------ | --------------------------------------------------- | -------------------- |
| `spec-issue-read`  | Issue 取得 + 要約                                   | intake               |
| `spec-requirement` | 要求の対話整理 → requirements doc / Issue 起票      | intake → specify     |
| `spec-lifecycle`   | 設計プロセスを集約した半自律 orchestrator           | scaffold 〜 tasks    |
| `spec-review`      | fresh-context evaluator が PASS / NEEDS_WORK を返す | review gate (複数回) |

設計プロセスの各 phase は `spec-lifecycle` に集約され、手順は `references/phase-*.md` で持つ:
scaffold (`phase-scaffold.md`) / clarify (`phase-clarify.md`) / diagram (`phase-diagram.md`) /
track (`phase-track.md`) / sync (`phase-sync.md`) / prompts (`phase-prompts.md`)。
個別 skill (旧 `spec-draft` 等) としては起動せず、`spec-lifecycle` の単一コンテキストから phase を進める。

レビュー観点の正本は subagent 定義 [.rulesync/subagents/spec-reviewer.md](.rulesync/subagents/spec-reviewer.md)。
`spec-review` skill は委譲と集約のみを担い、観点を skill 側で再記述しない。

orthogonal な Git / GitHub 操作は `workflow-git` を使う。
