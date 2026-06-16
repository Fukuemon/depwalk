# Phase Guide

`spec-lifecycle` の各 phase の完了条件と、本プロジェクトで毎回確認する論点。
project 共通の SDD 流れと、プロジェクト固有の論点を分けて記述する。プロジェクト固有の論点は `context/` を正本に列挙する。

## Phase 一覧 (project 共通)

| Phase            | 作業                                                                            | 完了条件                                                         | 次アクション    |
| ---------------- | ------------------------------------------------------------------------------- | ---------------------------------------------------------------- | --------------- |
| 1. intake        | issue / requirements doc を読み、対象機能を確認                                 | 要求の起点が明確                                                 | `spec-draft`    |
| 2. scaffold      | `templates/specs/template.md` を埋める / 上位文書整合                           | 背景 / スコープ / 論点 / 実装対象がある                          | draft review    |
| 3. clarify       | `spec-resolve` で未確定論点を 1 件ずつ確定                                      | 未解決論点ゼロ                                                   | diagram         |
| 4. diagram       | `spec-diagrams` で flow / sequence を描く                                       | 図と機能仕様が整合する                                           | track           |
| 5. track         | `spec-track` で `## 上位資料からの変更点` を最新化                              | 差分が PRD / Design Doc / feature doc / context / ADR 別に整理済 | sync (任意)     |
| 6. upstream sync | `spec-sync` で PRD / Design Doc / feature doc / context / ADR に back-propagate | 変更提案行がゼロ                                                 | tasks           |
| 7. tasks         | `spec-prompts` で実装 prompt を生成                                             | 各 prompt が自己完結 / phase 依存表                              | review          |
| 8. review        | `spec-review` で fresh-context evaluator を通す                                 | PASS                                                             | 実装 or handoff |

## Phase Gate (review が必須なタイミング)

- Phase 2 完了後: draft として読み手が背景 / スコープ / 論点を追えるか
- Phase 3 完了後: 重要論点が未解決のまま残っていないか
- Phase 5 完了後: 差分が上位文書側と矛盾していないか
- Phase 7 完了後: prompts が自己完結かつ実装可能か
- Phase 8: 最終 review (`PASS` で終端)

## プロジェクトで毎回確認する論点

スコープに応じて該当する論点だけを `spec-resolve` で確定する。各論点の正本は `context/`。

- performance / runtime budget をどう抑えるか ([context/architecture.md](../../../../context/architecture.md))
- 共有コードへ昇格する範囲と module / feature 固有に閉じる範囲の境界 ([context/architecture.md](../../../../context/architecture.md))
- 先回りした共通化をしていないか (colocation を崩していないか)
- secret を client / build-time / runtime のどこに置くか ([context/infrastructure.md](../../../../context/infrastructure.md))
- state / routing をどこで表現するか
- テストの検証境界 (unit / e2e の責務) ([context/testing.md](../../../../context/testing.md))
- 依存境界 / dead code のルール違反を生まないか ([context/engineering.md](../../../../context/engineering.md))
- 将来拡張を初期スコープに含めるか、ADR に送るか

## Phase を止めるべき条件

- 実装対象 module が特定できない (`context/project.md` の対象ドメインから外れる)
- 変更が `PRD.md` (Why/What) か `design/DesignDoc.md` / feature doc / context (How) のどちらで扱うべきか逆転している
- spec 自体が module をまたいだ要求追跡に使えない
- secret / runtime boundary が `context/infrastructure.md` / `context/architecture.md` と矛盾
- performance / 一貫性に直接影響する判断を独断で進めようとしている
