# Spec Workflow Contract

Spec Driven Development (SDD) skill 群 (`spec-issue-read` / `spec-requirement` / `spec-lifecycle` / `spec-review`) と
`spec-reviewer` subagent が参照する **唯一の運用契約**。skill 側に project 固有名 (path / CLI / framework) を直書きせず、
固有値は `context/project.yml`、運用ルールは本ファイルを読む。

参照するときのパス: `spec-lifecycle` skill の `references/spec-contract.md`
(Claude Code では `.claude/skills/spec-lifecycle/references/spec-contract.md`)。

## 正本ドキュメント / Templates / Issue Tracker / 対象ドメイン

`context/project.yml` の `paths` (正本パス) / `templates` / `tracker` (CLI / project) / `domains` (対象ドメイン) を参照する。

## 正本境界 (Source-of-Truth Boundary)

spec は **issue 単位** の文書、design (PRD / Design Doc / feature doc / context / ADR) は **製本となる正本**。両者の正本関係はライフサイクルで遷移する:

- **spec 作成〜clarify (`spec-lifecycle` の scaffold / clarify phase)**: spec が自身の決定の作業正本でよい。design へはまだ反映しない。
- **sync phase 実行時 (正本ハンドオフ)**: durable な設計成果 (IA / データモデル / フロー / アーキ判断) を design 側へ反映したら、以後 **design 側が正本**。同時に spec の該当節を「決定時スナップショット」に降格し、design への正本リンクを張る。
- **ハンドオフ後**: spec は論点 / 受け入れ基準 / レビュー / 実装分割 / 決定経緯のみを保持し、durable 成果の正本を二重に持たない (drift 防止)。
- **issue close 後 (closeout)**: spec は **削除する** (手順と前提条件は `closeout.md`)。durable 成果は sync で design 側へ、意思決定は ADR へ反映済みであることが削除の前提。経緯は issue / PR / git history で追う。design 側に spec への索引を残さない。

判定の目安: 「issue が閉じても残り続ける設計情報」は design 正本、「この issue の意思決定の記録」は spec。

**用語規約 (呼称)**: 「正本」の語は feature doc / context / ADR など durable な文書にのみ使う。handoff 後の spec を「正本」と呼ばない。他 spec への参照は「決定経緯」「決定時スナップショット」「issue 単位の作業記録」など位置づけが分かる語を使う。

## 文書メタ情報の同期

spec / ADR / context の本文を更新したら、対象文書の `更新日` / `ステータス` / `## 設計フェーズ状況` / `## レビュー` / `## 変更履歴` (存在するもの) を同時に更新する。他 issue / 他 spec への参照を追加した場合は、関連資料・未確定事項の状態も同期する。本文だけ更新してメタ情報が古いまま、を残さない。

## 読み取り索引の解決

読み取り索引 (「何を読めば足りるか」のルーティング表) を参照・更新する skill は、場所と更新手段を次の順で解決する。**この規則の正本は本節であり、各 skill には再掲しない。**

| 状態                                       | 解決                                                          |
| ------------------------------------------ | ------------------------------------------------------------- |
| `context/project.yml` に `reading_index` 無し | `context/impact-index.yaml` を索引とみなす (後方互換の既定)   |
| `reading_index.path` が `null`             | 索引を使わない。索引に触れるステップは skip する              |
| `reading_index.path` にパス                | そのパスを索引とする                                          |
| `reading_index.generated` が `false` / 未設定 | 索引を手で更新する                                            |
| `reading_index.generated` が `true`        | 索引を**手編集せず** `reading_index.generate_command` を実行する |

キーが無いことを「索引なし」と解釈しない。キーが増える前から索引を持つ repo で、索引の更新が黙って止まるため。索引を持たないことは明示的な `path: null` で表す。`generated: true` なのに `generate_command` が未設定なら、コマンドを推測せずユーザーに確認する。

## Phase Gate ルール

- phase の集合・順序は `context/project.yml` の `spec.phases` が正本 (未設定時は `spec-lifecycle` の既定列)
- gate phase (正本は `spec-lifecycle` の phase レジストリ) の完了時に `spec-review` (fresh-context evaluator) を通す。非 gate phase は次の gate で未レビュー分を累積レビューする
- 設計開始 (scaffold)・最終 gate 通過・実装開始の節目で issue の `status:*` ラベルを遷移し、状態遷移コメントを残す (正本: `workflow-git` の `references/issue-status.md`)
- PRD / Design Doc / feature doc / context / ADR と矛盾を検出したら sync phase を先に提案し、未解決のまま下流 phase に進まない
- spec 内「設計フェーズ状況」表が状態 (`未着手 / 進行中 / 完了 / レビュー済 / 保留`) を管理する
- 確定済みの判断 (解決済みの論点) を skill から上書きしない

## SDD Skill 一覧

| Skill              | 役割                                                | 対応 phase           |
| ------------------ | --------------------------------------------------- | -------------------- |
| `spec-issue-read`  | Issue 取得 + 要約                                   | intake               |
| `spec-requirement` | 要求の対話整理 → requirements doc / Issue 起票      | intake → specify     |
| `spec-lifecycle`   | 設計プロセスを集約した半自律 orchestrator           | scaffold 〜 prompts  |
| `spec-review`      | fresh-context evaluator が PASS / NEEDS_WORK を返す | review gate (複数回) |

設計プロセスの各 phase は `spec-lifecycle` に集約され、手順は `references/phase-*.md` で持つ:
scaffold (`phase-scaffold.md`) / clarify (`phase-clarify.md`) / diagram (`phase-diagram.md`) /
track (`phase-track.md`) / sync (`phase-sync.md`) / prompts (`phase-prompts.md`)。
実行する phase の集合・順序は `context/project.yml` の `spec.phases`、識別子と gate 属性の正本は
`spec-lifecycle` の phase レジストリ。個別 skill (旧 `spec-draft` 等) としては起動せず、`spec-lifecycle` の単一コンテキストから phase を進める。

レビュー観点の正本は subagent 定義 `spec-reviewer` (正本 `.rulesync/subagents/spec-reviewer.md`)。
`spec-review` skill は委譲と集約のみを担い、観点を skill 側で再記述しない。

orthogonal な Git / GitHub 操作は `workflow-git` を使う。
