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

## Review 2026-07-11 (phase: clarify gate / 1 回目)

Verdict: **NEEDS_WORK**

### blocking 指摘と対応

1. **D2 の `(cycle)` 判定が `Result.Cycles` の意味論と矛盾する** — `Result.Cycles` は「両端が同一 SCC に属する誘導 edge すべて」(`core/internal/traversal/result.go` の `cycleEdges`) であり、SCC 内の最初の edge も注釈対象になる。「`(cycle)` = `Result.Cycles` の edge の先は展開しない」という規則では、A→B→C→A の 3 要素 SCC で最初の edge A→B が打ち切られ **C が Console tree に一度も現れない**。これは「`(既出)` の node は別の場所に完全な部分木がある = 情報は失われない」という D2 自身の論拠を破る。spec のサンプル (root と `CacheWarmer` の相互再帰 = 同一 SCC) 自体が規則と矛盾していた。
   - **対応**: 停止性は「初出のみ展開」が単独で保証する (各 node は高々 1 回しか展開されない) ことを明示し、`(cycle)` / `(既出)` を情報表示に降格。判定は Console formatter の **DFS 経路 (祖先集合)** で行い、`Result.Cycles` は使わない。`Result.Cycles` は JSON の `cycle` フラグ (D3) には正しく使えるため、JSON 側の決定は不変。3 要素 SCC の回帰テスト観点を D7 に追加。
2. **`output → traversal` の package 依存が上位文書に未宣言** — D6 の `Input` / `View` が `traversal.Result` / `Request` / `Status` を持つため `core/internal/output` は `core/internal/traversal` を import するが、`design/DesignDoc.md` のモジュール責務表と C4 図、`context/architecture.md` の Package Boundary はいずれも Output の依存先を `Graph Engine` / `Model` のみと宣言している。
   - **対応**: Design Doc (モジュール責務 + C4 図) と `context/architecture.md` への **変更提案**として `## 上位資料からの変更点` に記録。循環依存は生じない (traversal は output に依存しない)。
3. **`## Performance / Security 設計 > Performance` が placeholder のまま、phase 表は「完了」** — メタ情報同期違反。
   - **対応**: D6 の結論 (逐次書き出し / streaming 非導入 / sort は View 構築時 1 回) を反映。

### minor 指摘と対応

4. `… (depth limit: N)` の `N` が「深さ上限値」とも「省略された edge 数」とも読める → `… (depth limit: N edges cut)` に変更。
5. `View.Start` は `status = startNotFound` のとき symbol を解決できない → `NodeView` が symbol 欠落 (ID のみ) を許容する旨を D6 に明記。
