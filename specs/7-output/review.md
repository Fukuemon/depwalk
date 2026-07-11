# spec-review 記録 (#7 Output)

`spec-review` (fresh-context evaluator `spec-reviewer`) の完全な記録。最新結果の要約は [index.md](index.md) の `## レビュー` を参照。

## Review 2026-07-11 (phase: scaffold gate)

Verdict: **PASS**

### 観点別評価

- **上位文書整合: PASS** — 整合テーブル (`index.md:42-58`) の全行を実文書で検証。Output Engine の依存 (`Graph Engine` / `Model`) は `design/DesignDoc.md:137` / `context/architecture.md:13,29` と一致。「tree 構築は Output 側」の分界は `design/features/traversal/DesignDoc_traversal.md:129` と一致。Q3 は `design/DesignDoc.md:245` で未決のままであり、D2 で引き取る宣言と矛盾しない。DOT / Mermaid = Phase4 / ビューワ非提供は `design/DesignDoc.md:79,236` と一致。`graph.Node` が methodId しか持たない件 (`core/internal/graph/graph.go:22-25` vs `analyzer-protocol` feature doc の `qualifiedName` / `signature` 必須) は差分として実在し、「矛盾ではなく未定義」という切り分けも妥当。ADR-0001 / 0002 を覆す決定なし。
- **未解決論点: PASS** — D1-D7 が 1 件 1 行で列挙され、各行の決定候補が推測でなく選択肢の形で具体化されている。未確定事項は D1-D7 + CLI interface spec 未起票の 2 系統に整理され、解決先が明記。下流 phase (Flowchart / Sequence / 実装分割) は空のプレースホルダのままで、未決定のまま先行記述していない。
- **実装対象明示: PASS** — target 一覧は `context/project.md:66` の対象ドメインと完全一致。`output` = 主対象、`core` = D1 従属の条件付き、他は非実装と責務境界が読める。Reuse Policy が `context/architecture.md:13` の依存規約と整合し、越境なし。
- **template 必須節: PASS** — `hooks/spec/validate_document.sh` の必須 22 節、`やること` / `やらないこと`、機能仕様の必須サブ節 5 件をすべて充足。メタ情報 / phase 表 / 変更履歴が本文と同期。
- **EARS acceptance: PASS** — 7 件 (WHEN 3 / IF 3 / THE SYSTEM SHALL 1)。「schemaVersion を含む」「決定的な要素順序」「常に同一のバイト列」等はテスト可能。曖昧動詞のみで終わる行なし。E1-E5 と 1:1 対応。
- **prompts 自己完結性: N/A** — phase: scaffold のため `prompts/` 未生成。
- **正本境界: N/A** — 「上位資料からの変更点」は全行が `(予定)` / `(なし)` で sync 未実行。現段階は spec が作業正本でよい。

### 参考 (非ブロッキングの改善提案 — 次 phase で対応)

1. Console 向け EARS (`index.md:120`) は D2 未決のため観測述語が弱い。D2 確定時に「cycle edge に `(cycle)` 標識が含まれる」等のテスト可能な述語へ具体化すると、golden test (D7) の受け入れ基準と直結する。
2. 未確定事項に決定者 / 期限を明記すると、rubric の「期限 / 決定者付きで管理」を明示的に満たせる。→ **本 review 後に対応済み** (`index.md` 未確定事項に決定者 / 期限を追記)。
3. D1 の選択肢 (b) 「Output に symbol table を別入力で渡す」を採る場合、symbol table の供給元 (Analyze Use Case か Graph Engine か) が `context/architecture.md:13` の依存方向に触れる。clarify 時に併せて記録すると sync が楽になる。
