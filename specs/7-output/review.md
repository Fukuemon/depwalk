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

## Review 2026-07-11 (phase: clarify gate / 2 回目)

Verdict: **NEEDS_WORK**

1 回目の blocking 3 件はいずれも**解消を確認**。D1-D7 の相互整合も良好と評価された (D1 の symbol 保持 → D2/D3/D4 の表示語彙 → D6 の `View` 集約 → D7 の golden 検証が一貫)。新規 blocking が 1 件。

### blocking 指摘と対応

6. **`depthCutoffs[]` の dangling 参照が探索方向で逆になる** — spec は「`calleeMethodId` が `nodes[]` に存在しない」と方向非依存に断定していたが、`nextNode(e, dir)` は `DirectionCaller` で `e.CallerID` を返す (`core/internal/traversal/search.go`)。caller 方向 (depwalk の主用途 S1) では深さ上限の外側にあるのは **`callerMethodId` 側**であり、`calleeMethodId` は `nodes[]` に存在する。従来の記述は callee 方向でしか成立せず、JSON 利用者に誤った契約を与えていた。
   - **対応**: schema に **`targetMethodId`** (探索方向の接続先 = dangling する側) を追加。既存の `targetMinDepth` と対になり、利用者は `direction` を見て分岐せずに dangling 参照を特定できる。D7 の検証観点も caller / callee の両方向で書き分けた。

### minor 指摘と対応

7. cutoff ラベルの表記ゆれ (`… (depth limit: N)` が D2 決定文と Interface 設計に残存) → `… (depth limit: N edges cut)` に統一。

## Review 2026-07-11 (phase: clarify gate / 3 回目)

Verdict: **PASS** — clarify gate 通過。

### 観点別評価

- **上位文書整合: PASS** — 3 件の変更提案 (traversal `minDepth` / `Output → Traversal` 依存 / S3 の 2 層照合) がいずれも上位文書の宣言との差分として正しく捕捉され、spec 単独で上位文書を書き換えていない。ADR-0001 / 0002 を覆す決定なし。
- **未解決論点: PASS** — D1-D7 の決定欄がすべて埋まり、未確定事項は「sync 未反映の変更提案 3 件」と「CLI spec 依存 (本 spec では解決不能・境界を D5 で明文化)」に整理済み。下流 phase は空プレースホルダのまま。
- **実装対象明示: PASS** — target 一覧が `context/project.md` の対象ドメインと完全一致。越境 (`output → traversal`) を隠さず変更提案として宣言。
- **template 必須節 / EARS acceptance: PASS** — 必須節・サブ節をすべて充足。EARS 7 件が golden 検証観点と 1:1 対応。
- **prompts 自己完結性 / 正本境界: N/A** — prompts 未生成、sync 未実行 (現段階は spec が作業正本でよい)。

### 実装照合による確認 (2 回目 blocking #6 の修正検証)

`result.go` の cutoff 記録 (`for id := range nodes` → `g.Neighbors(id, dir)` → `next := nextNode(e, dir)` が到達集合外なら記録、`TargetMinDepth = depths[next]`)、`search.go` の `nextNode` (`DirectionCaller` → `e.CallerID`)、`graph.go` の `Neighbors(id, DirectionCaller) = incoming[id]` を突き合わせ、**caller 方向では dangling するのが `callerMethodId` 側**であることを確認。spec の `targetMethodId` の定義と D7 の両方向検証観点はこの構造と厳密に一致する。

### 参考 (非ブロッキング)

1. golden の置き場所 (`core/internal/output/testdata/golden/`) は Go の package-local `testdata/` 慣習として妥当だが、`context/testing.md` の他の行は repo root の `testdata/` を指す文脈が多い。→ **phase: sync で `context/testing.md` に 1 行補足する変更提案として記録済み**。
2. D2 規則 7 の「cutoff edge を持つ node」がどちらの endpoint か暗黙 → **反映済み** (到達側 endpoint = `targetMethodId` ではない方、と明記)。
3. E1 行の「閉路の先を `(cycle)`」が D2 の back edge 規則より緩い → **反映済み** (「経路上の祖先に戻る edge の先」に統一)。

### 次アクション

phase: diagram / track / sync / tasks へ進んでよい。sync では 3 件の変更提案の反映、Design Doc Q3 の「解決済み」更新、`design/features/output/DesignDoc_output.md` への正本ハンドオフを行う。
