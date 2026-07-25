# Phase Guide

`spec-lifecycle` の各 phase の完了条件と、本プロジェクトで毎回確認する論点。
project 共通の SDD 流れと、プロジェクト固有の論点を分けて記述する。プロジェクト固有の論点は `context/` を正本に列挙する。

## Phase 一覧 (project 共通)

各 phase (intake / review を除く) は `spec-lifecycle` の単一コンテキストから
`references/phase-*.md` の手順で進める。個別 skill (旧 `spec-draft` 等) としては起動しない。
phase の採否・順序は `context/project.yml` の `spec.phases` が正本 (下表は既定列)。

| Phase            | 作業                                                                                | 完了条件                                                         | 次アクション      |
| ---------------- | ----------------------------------------------------------------------------------- | ---------------------------------------------------------------- | ----------------- |
| 1. intake        | issue / requirements doc を読み、対象機能を確認                                     | 要求の起点が明確                                                 | scaffold phase    |
| 2. scaffold      | `phase-scaffold.md`: `templates/specs/template.md` を埋める / 上位文書整合          | 背景 / スコープ / 論点 / 実装対象がある                          | draft review      |
| 3. clarify       | `phase-clarify.md` で未確定論点を 1 件ずつ確定                                      | 未解決論点ゼロ                                                   | diagram phase     |
| 4. diagram       | `phase-diagram.md` で flow / sequence を描く                                        | 図と機能仕様が整合する                                           | track phase       |
| 5. track         | `phase-track.md` で `## 上位資料からの変更点` を最新化                              | 差分が PRD / Design Doc / feature doc / context / ADR 別に整理済 | sync phase (任意) |
| 6. upstream sync | `phase-sync.md` で PRD / Design Doc / feature doc / context / ADR に back-propagate | 変更提案行がゼロ                                                 | prompts phase     |
| 7. prompts       | `phase-prompts.md` で実装 prompt を生成 (最終 gate)                                 | 各 prompt が自己完結 / phase 依存表 / gate レビュー PASS         | closeout (実装後) |

## Phase Gate (review が必須なタイミング)

gate の正本は `spec-lifecycle` SKILL.md の phase レジストリ。gate phase の完了時に
前の gate 以降の未レビュー分を累積して `spec-review` に渡す (既定列での観点):

- clarify gate (scaffold〜clarify を累積): draft として背景 / スコープ / 論点を追えるか、重要論点が未解決のまま残っていないか
- track gate (diagram〜track を累積): 図と機能仕様が整合するか、差分が上位文書側と矛盾していないか
- prompts gate = 最終 (sync〜prompts を累積): 正本ハンドオフが完了しているか、prompts が自己完結かつ実装可能か (`PASS` で終端)

## プロジェクトで毎回確認する論点

スコープに応じて該当する論点だけを clarify phase (`phase-clarify.md`) で確定する。各論点の正本は `context/`。

- performance / runtime budget をどう抑えるか ([context/architecture.md](../../../../context/architecture.md))
- 共有コードへ昇格する範囲と module / feature 固有に閉じる範囲の境界 ([context/architecture.md](../../../../context/architecture.md))
- 先回りした共通化をしていないか (colocation を崩していないか)
- secret を client / build-time / runtime のどこに置くか ([context/infrastructure.md](../../../../context/infrastructure.md))
- state / routing をどこで表現するか
- テストの検証境界 (unit / e2e の責務) ([context/testing.md](../../../../context/testing.md))
- 依存境界 / dead code のルール違反を生まないか ([context/engineering.md](../../../../context/engineering.md))
- 将来拡張を初期スコープに含めるか、ADR に送るか

## Phase を止めるべき条件

- 実装対象 module が特定できない (`context/project.yml` の対象ドメインから外れる)
- 変更が `PRD.md` (Why/What) か `design/DesignDoc.md` / feature doc / context (How) のどちらで扱うべきか逆転している
- spec 自体が module をまたいだ要求追跡に使えない
- secret / runtime boundary が `context/infrastructure.md` / `context/architecture.md` と矛盾
- performance / 一貫性に直接影響する判断を独断で進めようとしている
