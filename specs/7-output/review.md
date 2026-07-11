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

## Review 2026-07-11 (phase: diagram gate / 1 回目)

Verdict: **NEEDS_WORK**

図 3 点 (出力フロー / Console tree 構築 / sequence) を追加した状態のレビュー。blocking 2 件 + moderate 1 件 + minor 2 件。

### blocking 指摘と対応

8. **Flowchart 2 で祖先集合 / 展開済みの初期化が抜けており、self-loop が D2 規則 6 と食い違う** — 図では現 node が祖先集合に加わるのが再帰時のみのため、(a) 非 root の self-loop `B→B` が `(cycle)` ではなく `(既出)` になり、(b) root の self-loop で root が再展開され、規則 5 の「各 node は高々 1 回しか展開されない」(= 停止性と O(到達 edge 数) の根拠) が破れる。
   - **対応**: D2 規則 4 に「**visit 入口で現 node を展開済みに記録し、祖先集合に加える** (root を含む)」を明記し、図にも入口ステップを追加。self-loop と root self-loop の回帰テスト観点を D7 に追加。
9. **sequence の `Format(w, Input{...})` が D6 の `Formatter.Format(w, View)` と signature 不一致** — 根本原因は、**format 検証・`View` 構築・Formatter 選択を担う package entry point が spec に定義されていなかった**こと。D5 の「未対応 format は出力前に error」は Formatter 選択より前の段階であり、`Formatter.Format(w, View)` では表現できない。
   - **対応**: D6 に公開 entry point **`output.Write(w io.Writer, f Format, in Input) error`** を追加 (ユーザー承認済み)。`Formatter` / `View` は package 内部の拡張点に降格。Interface 設計 / E4 / sequence を同期。

### moderate / minor 指摘と対応

10. Flowchart 1 で `startNotFound` / 到達なしが Formatter 選択を迂回していた → **Formatter を迂回しない**形に修正 (「該当なし」の見せ方は形式ごとに異なるため、各 Formatter が `View.Status` / 空 `Edges` を見て分岐する)。Flowchart 2 にも status 分岐と到達なしの枝を追加。
11. cutoff 行の位置を図が先取りしていた → D2 規則 7 に「**位置はその node の子の最後**」を明記し、本文と図を一致させた。
12. sequence の `minDepth` が未反映の変更提案である旨の注記が無かった → `Note over Trv` に「D3 の変更提案。sync で traversal feature doc へ反映」を追加。

## Review 2026-07-11 (phase: diagram gate / 2 回目)

Verdict: **NEEDS_WORK**

1 回目の指摘 (D2 規則 4 の visit 入口初期化 / `output.Write` entry point / Formatter 迂回なし / cutoff 行位置) はいずれも**解消を確認**。self-loop・root self-loop・3 要素 SCC の 3 ケースを図の意味論でトレースして正しさを検証済み。新規 blocking 1 件。

### blocking 指摘と対応

13. **`maxDepth=0` で Console が `(呼び出し元なし)` という誤出力になる** — spec は「到達なし」を `Edges が空` という条件だけで定義していたが、traversal の契約では **`maxDepth=0` は起点のみを到達集合に含め、起点の隣接 edge をすべて `depthLimit` cutoff にする** ([traversal feature doc](../../design/features/traversal/DesignDoc_traversal.md)、`core/internal/traversal/traversal.go` の `MaxDepth` / `result.go`)。このとき `Edges` は空だが `Cutoffs` は非空であり、呼び出し元は**存在するが深さ上限で切られただけ**。従来の定義では事実に反する `(呼び出し元なし)` を出力し、さらに D2 規則 7 が要求する `… (depth limit: N edges cut)` も出力されない (規則 7 と規則 8 が同じ入力で矛盾した出力を規定していた)。

- **対応**: 「到達なし」の条件を **`Edges` が空 かつ `Cutoffs` も空** に狭めた。`Edges` が空でも `Cutoffs` が非空なら root 行 + 規則 7 の cutoff 行を出す。EARS / D5 表 / E2・E2' / D2 規則 8 / Flowchart 2 を同じ条件で書き直し、D7 の fixture に `maxDepth=0` と `maxDepth=0 + 起点 self-loop` (誘導 edge + `cycle` が残るため別経路) を追加。JSON 側は `depthCutoffs[]` を常に出すため影響なし。

### minor 指摘と対応

14. D2 規則 8 の「tree を組まず」が D5 表・図と食い違う → 到達なしは root 行のみを出す形に文言を統一 (規則 8 / 規則 9 に分割)。
15. メタ情報のステータス行が「phase: clarify 完了」のままだった → 「phase: clarify / diagram 完了」に更新。

## Review 2026-07-11 (phase: diagram gate / 3 回目)

Verdict: **NEEDS_WORK**

2 回目の `maxDepth=0` 修正そのものは**正しい**と実装照合で確認された (4 つの境界ケース: `maxDepth=0` / `maxDepth=0` + 起点 self-loop / 起点孤立 / `startNotFound` をすべてトレースし、規則 7 と規則 8 が同一入力で衝突しないことも確認)。ただし **修正の適用漏れ**が 4 箇所あった。

### blocking 指摘と対応

16. **Formatter の分岐条件が「`Edges` 空だけ」のまま 4 箇所に残存** — D6 (`## 解決済みの論点 > D6`) / Interface 設計 / Flowchart 1 のノード K / Flowchart 1 直下の本文。規則 8・D5 表・E2'・EARS は「`Edges` 空 **かつ** `Cutoffs` 空」に修正済みだったが、**実装者が Formatter の契約を読むときに最初に当たる場所**に旧述語が残っており、同一入力に対して spec が二通りの出力を規定する状態だった。放置すると 2 回目で修正したはずの `maxDepth=0` → `(呼び出し元なし)` 誤出力がそのまま再現しうる。

- **対応**: 4 箇所すべてを `View.Status` / `View.Edges` / **`View.Cutoffs`** の 3 つを見る形に統一。grep で旧述語の残存ゼロを確認。

### minor 指摘と対応

17. D2 規則 8 の根拠文「`maxDepth=0` は起点の隣接 edge が**すべて** cutoff になる」が上位 feature doc と食い違う (feature doc と実装は「**起点自身への self-loop は誘導 edge + `cycle` 注釈として残る**」を明記) → 例外を補って上位 doc と一致させた。

## Review 2026-07-11 (phase: diagram gate / 4 回目)

Verdict: **NEEDS_WORK**

3 回目 blocking #16 (述語の統一) は**解消を確認** (全 10 箇所を全数突合し、旧述語の残存ゼロ)。残っていたのは、いずれも**同じ事実を複数箇所に列挙していたことによる適用漏れ** 2 件。

### 指摘と対応

18. **[blocking] Flowchart 2 の A4 ノードに「すべて cutoff」が残存** (3 回目 minor #17 が D2 規則 8 の本文にのみ適用され、図に漏れていた) → 図の注記を「起点 self-loop は誘導 edge として残るため A2 = Yes」に修正し、本文と同じ述語にそろえた。
19. **[moderate] `## テスト / 評価方針` の fixture 一覧が D7 より古い** — 2 回目に D7 へ追加した `maxDepth=0` / `maxDepth=0` + 起点 self-loop / 3 要素 SCC が反映されておらず、phase: tasks がこの一覧を読むと `maxDepth=0` の回帰テストが実装タスクから落ちる恐れがあった → **列挙をやめて `## 解決済みの論点 > D7` への参照 1 行に置き換え**、二重管理そのものを解消した。

### 根本原因メモ (以降の phase で再発させないために)

diagram gate で 3 回続けて出た指摘 (#16 / #18 / #19) は、いずれも「同じ規範を spec 内の複数箇所に列挙し、片方だけ直した」ことが原因。**正本を 1 箇所に置き、他は参照 1 行にする** (Spec Workflow Contract の「同じ規範を複数ファイルに再掲しない」と同じ原則を spec 内部にも適用する) ことで構造的に防ぐ。

## Review 2026-07-11 (phase: diagram gate / 5 回目)

Verdict: **PASS** — diagram gate 通過。

### 観点別評価

- **上位文書整合: PASS** — 変更提案 3 件が上位文書の宣言との差分として正しく捕捉され、spec が上位文書を勝手に書き換えていない。ADR-0001 / 0002 を覆す決定なし。
- **未解決論点 / 実装対象明示 / template 必須節 / EARS acceptance: PASS**
- **prompts 自己完結性 / 正本境界: N/A** (prompts 未生成、sync 未実行)

### 二重規範の網羅確認 (根本原因への対処の検証)

指定 6 カテゴリを全数突合し、**旧述語の残存ゼロ / 単一正本化**を確認:

| 規範                   | 判定                                                          |
| ---------------------- | ------------------------------------------------------------- |
| 「到達なし」の判定条件 | 全 10 箇所が `Edges` 空 **かつ** `Cutoffs` 空                 |
| self-loop の扱い       | 規則 4 / 8 / D7 / 図で一貫。「すべて cutoff」の残存なし       |
| cutoff 行の位置        | 規則 7 と図が一致 (子の最後)                                  |
| Formatter の分岐条件   | 全 4 箇所が `Status` / `Edges` / `Cutoffs` の 3 条件          |
| fixture ケース一覧     | 正本は D7 のみ。他は参照 1 行                                 |
| entry point signature  | 全 5 箇所が `Write(w, f, in)` で一致。旧 signature の残存なし |

### 実装との回帰照合

`targetMethodId` の方向 (`search.go` / `result.go`)、`Result.Cycles` を Console の打ち切りに使わない根拠 (`cycleEdges`)、`maxDepth=0` の意味論 (起点 self-loop のみ `edges` に残る)、`Result` が `direction` / `start` / `minDepth` を持たないこと — すべて実装と一致。

### 非ブロッキング提案と対応

20. `## 機能仕様 > Testing` の unit 概括が D7 fixture 一覧より粗く `maxDepth=0` を含まない → **D7 への参照に統一** (根本原因の残り火を除去)。
21. Flowchart 1 の Formatter 分岐ノードの配置順 (読み手の実装順との一致) → 意味的矛盾はないため据え置き。

### 次アクション

phase: track / sync / tasks へ進んでよい。

## Review 2026-07-11 (phase: track gate / 1 回目)

Verdict: **NEEDS_WORK**

### 指摘と対応

22. **[blocking] Design Doc 成功条件 S3 の測定方法への反映行が欠落** — #6 は S1/S2 の測定方法に「Traversal 層照合 (feature doc 正本) + CLI 出力照合の 2 層」注記を **Design Doc と `context/testing.md` の両方**に入れている。#7 は同じ 2 層構造を D7 で決めながら、反映先を `context/testing.md` だけに振り分け、Design Doc の S3 行を「継承」扱いにしていた。このままだと sync 後の Design Doc が「#7 で完了しない測定方法」を宣言したまま残り、S1/S2 と非対称な drift になる。
    - **対応**: Design Doc への影響テーブルに S3 の 2 層照合の行を追加し、上位文書整合の S3 行を「継承」→「**変更提案**」に改めた。
23. **[moderate] D1 (graph の symbol 値型) の durable 正本を output feature doc に置く振り分けが不適切** — `graph.Node.Symbol` / `graph.Edge.CallSite` は Graph Engine の横断データモデルで、読み手は Output だけではない (Traversal も graph を読む)。「出力形式 feature doc」を正本にすると、将来の graph model 変更が Output の doc を正本として要求することになる。加えて同じ D1 が feature doc 行と context 行の双方に載り、二重正本の芽になっていた。
    - **対応**: **`design/features/graph/DesignDoc_graph.md` を新設**して D1 の正本を持たせる (ユーザー判断)。output feature doc は D2-D7 の正本とし、D1 は graph feature doc への参照 1 行にする。`context/architecture.md` は package 境界の記述に留め、正本として graph feature doc を参照させる。Design Doc の feature 一覧にも Graph Engine 行を追加する。
24. **[minor] ラベル不整合** — `context/testing.md` が上位文書整合では「変更提案」、変更点テーブルでは「(予定)」になっていた → 変更提案リストを 4 件に統一し、3 箇所のラベルを揃えた。

### 妥当と判断された点

- **ADR 不要 (確定)** の判定は妥当と評価された。ADR-0001 は Analyzer↔Core の wire protocol、ADR-0002 は Core 実装基盤に閉じており、いずれも Core 内 package 間の依存方向や Core↔利用者の出力 schema を決めていない。D6 の依存追加は「Core 内は単方向」を保った明文化で P1-P4 を覆さない。
- 二重追記なし (D1 を除く。上記 23 で解消)。

## Review 2026-07-11 (phase: track gate / 2 回目)

Verdict: **NEEDS_WORK**

1 回目の指摘 (#22 / #23 / #24) の本体対応はいずれも実文書で確認された。正本の振り分けも「二重正本なし」と評価 (D1 = graph feature doc のみ、context/architecture.md は参照)。残ったのは **同じ規範の二重列挙** 1 件と、索引の漏れ。

### 指摘と対応

25. **[blocking] `## 上位文書整合` の要約行が旧 3 件のまま** — 「Design Doc 更新要否: 要 (①Q3 ②feature 一覧 ③依存追加)」が、#22 で追加した **④S3 の測定方法** と #23 で追加した **feature 一覧への Graph Engine 行** を反映していなかった。sync phase が最初に読む節であり、ここだけ古いと #22 が防ごうとした drift がそのまま再現する。**diagram gate で 3 回踏んだのと同じ「二重列挙 → 片側更新漏れ」パターン**。
    - **対応**: 要約行の列挙を廃止し、**`## 上位資料からの変更点 > Design Doc への影響` への参照 1 行**に置き換えた (根本対処)。
26. **[minor] ステータス語彙の 2 軸混在** — 同じ列に `(予定)` (= 未反映ステータス) と `変更提案` (= 種別) が混在し、「変更提案 4 件」の数え上げが読者から検証できなかった。
    - **対応**: 節冒頭に**ステータス規約**を明示 (「sync で反映するまで全行が未反映。反映済の行に `[反映済]` を付す」)、`(予定)` を全廃。「変更提案」は種別を表す語に統一した。
27. **[minor] `design/features/README.md` の索引更新が sync 作業として導けない** — 索引は現在 analyzer-protocol のみ登録で、traversal も未登録のまま stale。新規 2 本を作っても索引に載らない。
    - **対応**: feature doc への影響テーブルに README 索引の行を追加 (graph / output に加え、**stale な traversal の行も同時に追加**する)。

## Review 2026-07-11 (phase: track gate / 3 回目)

Verdict: **NEEDS_WORK**

上位文書との**矛盾はゼロ**、正本の振り分けも**二重正本なし**、ADR 不要の判定も妥当、sync 作業も変更点テーブルから漏れなく導ける、と確認された。残ったのは **二重列挙パターンの 5 回目**。

### 指摘と対応

28. **[blocking] `## 上位文書整合` テーブルが変更点テーブルの「第 2 の列挙」として残り、既に 2 行乖離** — 2 回目の対応で summary bullet を参照 1 行に置換したが、**テーブル本体という二重列挙の本体を温存**していた。結果、今回追加した README 索引の行と `context/testing.md` の 2 節目 (テスト runtime contract) が整合テーブルから読めない状態になっていた。
    - **対応 (根本対処)**: 節冒頭に**本節の役割**を明記し、整合テーブルを「**確認した上位文書と整合方針の記録**」に限定。**sync 作業の網羅リストは `## 上位資料からの変更点` 1 本を唯一の正本**とすると宣言した。あわせて欠落していた 2 行も追加。
29. **[moderate] `## 未確定事項` の「変更提案が 4 件」が第 3 の列挙** — 新設したステータス規約 (「変更提案」= 種別) と数え方が食い違い、件数の断定が読者から検証できなかった。
    - **対応**: 件数と内訳の再掲をやめ、**変更点テーブルへの参照**に置き換えた。

### 根本原因への対処 (通算 5 回目を受けて)

diagram gate #16 / #18 / #19、track gate #25 / #28 はすべて同一原因 (同じ規範を複数箇所に列挙 → 片方だけ更新)。今回、**各列挙箇所に「役割」と「正本はどこか」を明記する**ことで、列挙の重複そのものを構造的に禁じた:

- sync 作業の正本 = `## 上位資料からの変更点` のみ (整合テーブル / 未確定事項 / 要約行はすべて参照に降格)
- fixture ケースの正本 = D7 のみ (Testing 節は参照)
- Console tree 規則の正本 = D2 のみ (図・EARS・E1 は参照ないし同一述語)

## Review 2026-07-11 (phase: track gate / 4 回目)

Verdict: **PASS** — track gate 通過。

### 観点別評価

- **上位文書整合: PASS** — 整合テーブル全 13 行を実文書と突合し記述が事実と一致。3 回目で欠落していた 2 行 (README 索引 / testing.md runtime contract) が追加され、**整合テーブルと変更点テーブルの乖離はゼロ**。
- **未解決論点 / 実装対象明示 / template 必須節 / EARS acceptance: PASS**
- **prompts 自己完結性 / 正本境界: N/A** (prompts 未生成、sync 未実行)

### 重点確認の結果

1. **二重列挙は構造的に解消**と判断された。整合テーブル 13 行と変更点テーブル (Design Doc 4 / feature doc 4 / context 4 / ADR 1) を全数マッピングし、**片側にしかない行はゼロ**。
2. **sync 作業は変更点テーブルのみから漏れなく導ける**ことを確認 (Design Doc 4 件 / feature doc 新規 2 本 + README 索引 + traversal / context 4 件 / ADR なし / spec 側の snapshot 降格)。
3. **二重正本なし** — D1 = graph feature doc、D2-D7 = output feature doc、context/architecture.md は package 境界の記述に留め正本を graph feature doc へ委譲。

### 非ブロッキング提案と対応

30. `## 未確定事項` に「要点」として部分的な内訳の再掲が残っていた (通算 5 回踏んだパターンの残り火) → **参照 1 行に純化**して完全に閉じた。
31. `## 上位文書整合` の blockquote が箇条書きを分断していた → blockquote を先頭に移動し、3 つの bullet を連続させた。

### 次アクション

phase: sync へ進んでよい。

## Review 2026-07-11 (phase: sync gate / 1 回目)

Verdict: **NEEDS_WORK**

**上位文書への反映は全 11 行を実文書と全数突合し、意味の変質なし・反映漏れ / 過剰反映ゼロと確認された** (Design Doc の Q3 / S3 / 依存先 / C4 図 / feature 一覧、graph・output feature doc の型定義〜fixture の述語レベル一致、traversal の `minDepth`、README 索引、context 2 件)。ハンドオフの本体 (spec 冒頭の降格宣言 / snapshot blockquote / feature doc 側の決定経緯逆リンク) も正しい。残ったのは **spec 内の「正本」呼称の残存 3 件** (用語規約違反)。

### 指摘と対応

32. **[blocking] `## スコープ > やること` に「Q3 の正本」が残存** — 降格宣言は「解決済みの論点**以下**」を対象としており、それより前のスコープ節が対象外。output feature doc が「以後本 doc を正本とする」と宣言済みのため二重正本状態だった → 「Q3 の解 (durable 正本は sync で Output feature doc へハンドオフ済み)」へ言い換え。
33. **[minor] 「fixture ケースの正本は D7」が snapshot 宣言と衝突** (2 箇所) → durable 正本は Output feature doc のテスト観点、spec 内は決定経緯、と言い分け。
34. **[minor] 「CLI interface spec (…の正本)」** — 用語規約上 spec を「正本」と呼ばない → 「決定先」へ言い換え。

## Review 2026-07-11 (phase: sync gate / 2 回目)

Verdict: **NEEDS_WORK**

1 回目の言い換え 3 件のうち line 489 / 77 / 91 は確認されたが、**「fixture ケースの正本は D7」の 2 箇所目 (テスト / 評価方針節) が取り逃されて残存** (二重列挙パターンの 6 回目)。

### 指摘と対応

35. **[blocking] `## テスト / 評価方針` の fixture 行に「正本は D7」が残存** → line 489 と同じ言い分け (durable 正本 = Output feature doc テスト観点 / 決定経緯 = D7) に統一。
36. **[minor] 変更履歴の「3 件言い換え」が実態 (1 件未完) と不一致** → 履歴行を実態に合わせて更新。
37. **[参考] 「唯一の正本」(sync 作業リスト = 作業記録を指す) が用語規約の字義に触れる** → 「唯一の管理箇所」へ言い換え (3 箇所。レビュー履歴行の引用は改変せず保持)。
