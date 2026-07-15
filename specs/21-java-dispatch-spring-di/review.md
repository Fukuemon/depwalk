# Review Log

## Review 2026-07-12 — Phase 2 (scaffold)

Verdict: PASS

- 上位文書整合: PASS — S4/S5 継承、Design Doc Q2 → D1 継承、ADR-0004/0005 踏襲、architecture.md Package Boundary 遵守。矛盾なし。
- 未解決論点: PASS — D1〜D5 が「未決」で明示管理され、下流 phase は未着手のまま。
- 実装対象明示: PASS — 対象ドメイン一覧と完全一致、java-analyzer のみ ◯、波及可能性は備考に明示。
- template 必須節: PASS — REQUIRED_SECTIONS 22 節すべて存在、メタ情報同期良好。
- EARS acceptance: PASS — WHEN/WHERE/IF 7 件、観測可能な出力で終端。
- prompts 自己完結性 / 正本境界: N/A (scaffold 段階として正常)

非 blocker 指摘 (対応済み):

1. index.md:48 「実測値未確定」→ 実態は「数値目標未確定 (実測 baseline は記録済み、確定期限 #22)」に修正
2. index.md:109 「DI 情報からから」typo 修正

## Review 2026-07-12 — Phase 3 (clarify)

Verdict: NEEDS_WORK → 指摘対応後に再レビュー予定

- 上位文書整合: PASS (D1〜D5 とも ADR-0004/0005・feature doc・Design Doc の枠内、矛盾なし)
- 未解決論点: PASS (D1〜D6 全件解決済み、requirements Q1〜Q4 同期済み、下流先行なし)
- 実装対象明示: NEEDS_WORK (traversal / analyzer-protocol 備考が未解決前提のまま)
- template 必須節: NEEDS_WORK (設計フェーズ状況の備考が stale)
- EARS acceptance: PASS / prompts・正本境界: N/A

指摘 8 件 (いずれも決定確定後の反映漏れ。対応: 本 commit で修正):

1. フェーズ状況 #6 備考「Q1/Q2 解決待ち」→ 解決済みに更新
2. フェーズ状況 #8 備考「比較方針は論点解決後に確定」→ D5 で確定済みに更新
3. 実装対象 traversal 備考 → 「変更なし (D2 により変更不要が確定)」
4. 実装対象 analyzer-protocol 備考 → 「schema 変更なし (既存 metadata / diagnostic への値追加のみ、非破壊)」
5. User Flow step 4 の「D2 の結論次第」→ D2 確定を反映
6. 備考の appendix 再検討 → 結論を明記
7. (minor) 上位文書整合の「Design Doc 更新要否: 不要」ラベルの自己矛盾解消
8. (minor) フェーズ #3 (上位文書突合) の状態整理

## Review 2026-07-12 — Phase 3 (clarify) 再レビュー

Verdict: PASS

- 前回指摘 8 件すべて修正反映を確認 (フェーズ状況備考 / 実装対象 traversal・analyzer-protocol 備考 / User Flow step 4 / appendix 結論 / Design Doc 更新要否ラベル / フェーズ #3 状態)
- D1〜D6 の決定が Interface 設計 / Content・Data 設計 / Performance / Error・Fallback / Testing / 上位資料からの変更点に整合的に伝播、stale 残存なし
- 観点別: 上位文書整合 PASS / 未解決論点 PASS (D1〜D6 全解決) / 実装対象明示 PASS / template 必須節 PASS / EARS PASS / prompts・正本境界 N/A

非 blocker 所見:

1. requirements.md のスコープに「Gradle マルチモジュール非対応 (#24 切り出し) / 単一 source root 前提」が未反映 → 本 commit で対応
2. Interface 設計節に clarify 決定の伝播記述が既にあるため、phase 6 (diagram 以降) 着手時に「clarify 継承部分」と「当該 phase 確定部分」の区別に留意

## Review 2026-07-12 — Phase 4 (diagram)

Verdict: PASS

- 図と D1〜D6 / E1〜E4 / 機能仕様の整合: 良好 (D1: SootUp は照会のみ、D2: 複数 CallEdge + 宣言型 edge、D3/E4: 条件は記録のみ、D4/E1: runtime-provided 分岐、E2/E3 分岐、D6: JSONL までが Analyzer 責務 — すべて図に忠実に反映)
- 推測 (未確定事項) の混入なし。diagram-rules 準拠 (ノード ID / ラベルのクォート / participants 選定)
- 上位文書整合 / 未解決論点 / 実装対象 / template 必須節 / EARS: すべて PASS

非 blocker 所見 3 件 (実装時の留意点として記録):

1. flowchart の BeanCount 分岐で「条件付き Bean (E4)」が件数分岐と同列の排他分岐に見えるが、実際は件数と直交する属性 (複数件かつ条件付きが起こり得る)。実装分割時に判定順序を明確化する
2. sequence で diagnostic と callEdge の出力タイミングが二段に読める。実装時に「出力は統合後に一括か逐次か」を確定する
3. 図に JavaParser / SootUp / Spring の固有名が登場するが、本 feature では設計対象そのものであり Design Doc C4 L2 と同名のため許容

## Review 2026-07-12 — Phase 5 (track)

Verdict: NEEDS_WORK → 指摘対応後に再レビュー予定

- 上位文書整合: NEEDS_WORK (context への影響テーブルが空 — D1 の context/toolchain.md 反映記録漏れ)
- 未解決論点 / 実装対象 / template 必須節 / EARS: PASS。prompts / 正本境界: N/A
- 二重追記: 検出なし (D1 の複数テーブル出現は反映先が異なる正当な多重記録)

指摘 3 件 (対応: 本 commit で修正):

1. context への影響テーブルに D1 → context/toolchain.md の反映行を追加 (toolchain.md は「確定範囲は Q2 で詰める」と解決待ちを明記、ADR-0005:70 も toolchain/testing への反映を指示)
2. (minor) context/testing.md は更新不要の根拠 (既に「サンプル Java/Spring プロジェクト」前提) を 1 行記録
3. (minor) ADR テーブル D1 行の「sync phase で反映」文言を他行と統一

## Review 2026-07-12 — Phase 5 (track) 再レビュー

Verdict: PASS

- 前回指摘 3 件すべて反映確認 (context/toolchain.md 反映行の追加、testing.md 更新不要根拠の記録、ADR D1 行の文言統一)
- 上位文書整合 / 未解決論点 / 実装対象 / template 必須節 / EARS: すべて PASS。prompts / 正本境界: N/A
- 非 blocker: sync phase で context/toolchain.md 更新時に冒頭の「最終更新」日付も同時更新すること (文書メタ情報の同期)

## Review 2026-07-14 — Phase 3 (clarify) 再オープン分 (D7/D8)

Verdict: PASS

- 上位文書整合: PASS — D7/D8 とも ADR-0005 の責務境界・D1/D4 の枠内。D4 決定文自体は書き換えず (index.md:201 に明記)。feature doc `design/features/java-analyzer/DesignDoc_java-analyzer.md:249` (Spring Data のみ、他フレームワークは後続) との差分は index.md:449-450 で「未反映 (次回 sync 待ち)」として正しく管理された計画的差分であり矛盾ではない。
- 未解決論点: PASS — D1〜D8 すべて「解決済み」、未確定事項 (index.md:210) と一致。
- 実装対象明示: PASS — java-analyzer 行 (index.md:222) が D7 (自プロジェクトのコンパイル済み class を含む) を明示。他 target は変更なしで整合。
- template 必須節: PASS — 見出し欠落なし、メタ情報 (更新日 2026-07-14) と変更履歴が同期。
- EARS acceptance: PASS — 既存 EARS (index.md:128, 131) が D7/D8 を一般化して包含。Testing / Error 節で具体化。
- prompts 自己完結性: N/A (tasks phase 未着手)
- 正本境界: PASS (部分的) — Interface/Content/Performance のスナップショット節への D7/D8 追記はスナップショットへの追記であり正本主張なし。feature doc 未反映は sync phase 待ちとして管理済み。

非 blocker 所見 2 件 (対応不要、既に spec 内で追跡済み):

1. flowchart (index.md:347 RuntimeCheck) / sequence (index.md:390 マーカー判定) のラベルが D4 時点 (Spring Data のみ) のまま、D8 (MyBatis `@Mapper`) 未反映。分岐構造は変わらないため矛盾ではない。備考の【更新フラグ】(index.md:521) で次回 diagram phase での再生成対象として既に記録済み。
2. D7 (Lombok) に対応する専用 EARS 行はなく、既存の一般的な EARS で包含する設計。blocker ではないが、実装分割時に「Lombok 生成コンストラクタ解決」の観測可能な受け入れ基準を prompts / タスク記述で明示することを推奨。

## Review 2026-07-14 — Phase 6 (diagram) 再実行分 (D7/D8 ラベル反映)

Verdict: PASS

- 上位文書整合: PASS — D7/D8 とも ADR-0005 の SootUp 責務定義 (bytecode/依存jar を含む型階層・override・interface実装候補の補完) の枠内。feature doc への反映も確認済み。
- 未解決論点: PASS — D1〜D8 すべて解決済み、下流の図はすべて確定済み決定の反映のみ。
- 実装対象明示 / EARS acceptance / 正本境界: PASS。prompts: N/A (tasks phase 未着手)。template 必須節: N/A (今回の焦点は図のみ、他節の構造変更なし)。
- Mermaid 構文チェック: flowchart / sequence とも構文エラーなし。D7 (`SootUpQuery`/`Candidates` ラベルに自プロジェクト bytecode・Lombok コンストラクタ反映) / D8 (`RuntimeCheck`/マーカー判定ステップに MyBatis `@Mapper` 反映) がラベルに正しく反映され、flowchart と sequence で一致。分岐構造 (E1〜E4) の変更なし。

指摘: なし (blocker 相当なし)。

非 blocker 所見:

1. E3 分岐 (SootUp bytecode 読み込み失敗) のラベルに D7 の前提制約 (未ビルド時は degrade) への言及がないが、E3 は既存の一般規則をそのまま踏襲しており D7 決定理由 (index.md:195「E3 の一般規則でカバーする」) と整合。図の修正は不要、実装時の留意事項として扱う。

## Review 2026-07-14 — フェーズ7-9 クローズレビュー (1回目)

Verdict: NEEDS_WORK → 指摘対応後に再レビュー予定

- 上位文書整合: NEEDS_WORK — D7 で SootUp の照会対象に追加された自プロジェクトのコンパイル済み class の read-only 性を spec の Security/Privacy 節にのみ追記し、「ハンドオフ対象外」と断定していたが、durable な設計情報のため feature doc への反映が必要だった。
- 未解決論点 / 実装対象明示 / template 必須節 / EARS acceptance: PASS。prompts: N/A。
- 正本境界: NEEDS_WORK — 上記と同じ理由 (durable 成果が spec 側にのみ残存)。

指摘 1 件 (対応: 本 commit で修正):

1. feature doc (段階導入節 D7 行) へ read-only 拡張を追記し、spec の Security/Privacy 節見出しの断定を訂正、「上位資料からの変更点」テーブルに反映行を追加。

## Review 2026-07-14 — フェーズ7-9 クローズレビュー (再レビュー)

Verdict: PASS

- 上位文書整合: PASS — feature doc (`design/features/java-analyzer/DesignDoc_java-analyzer.md:240`) に read-only 拡張の記述を確認、spec の D7 決定と整合。
- 未解決論点 / 実装対象明示 / template 必須節 / EARS acceptance: PASS。prompts: N/A (tasks phase 未着手)。
- 正本境界: PASS — spec 側の見出し注記が用語規約どおり「決定経緯の記録」に訂正され、feature doc 側に正本記述が実在。二重正本主張なし。

指摘: なし。フェーズ7〜9 (Content/Data・Performance/Security・Test/Metrics 設計) を「完了」として扱ってよいと判定。

非 blocker 所見: feature doc ヘッダー (最終更新日) は read-only 追記への明示的言及がないが、日付は一致しており blocker ではない。

## Review 2026-07-14 — Phase 7 (tasks) prompts 生成分 (1回目)

Verdict: NEEDS_WORK → 指摘対応後に再レビュー予定

- 上位文書整合 / 未解決論点 / 実装対象明示 / EARS acceptance / 正本境界: PASS。template 必須節: N/A (prompts 対象のため)。
- prompts 自己完結性: NEEDS_WORK — P3_01 ステップ2「severity は spec のエラーケース表に従う」が、spec のエラーケース表に severity 列が存在せず自己完結性に反する。

指摘 1 件 (対応: 本 commit で修正):

1. P3_01 ステップ2に新規 diagnostic code 3件 (`JAVA_RUNTIME_PROVIDED`=info、`JAVA_AMBIGUOUS_CANDIDATE`=warning、`JAVA_CONDITIONAL_BEAN`=info) と severity を確定値として直書き。

## Review 2026-07-14 — Phase 7 (tasks) prompts 生成分 (2回目)

Verdict: NEEDS_WORK → 指摘対応後に再レビュー予定

- 前回指摘 (severity の自己完結性) は解消を確認。
- prompts 自己完結性 / 正本境界: NEEDS_WORK (新規指摘) — P3 が追加する新規 diagnostic code 3件は feature doc の diagnostic/error code 体系表 (正本) への反映経路が prompt 内になかった。

指摘 1 件 (対応: 本 commit で修正):

1. P3_01 ステップ2に「この doc への追記は phase: sync として扱う」を追記 (ステップ5の性能計測と同一パターン)。完了条件・spec の上位資料からの変更点テーブルにも予告行を追加。

## Review 2026-07-14 — Phase 7 (tasks) prompts 生成分 (3回目)

Verdict: NEEDS_WORK → 指摘対応後に再レビュー予定

- 2回目指摘 (P3 の新規 diagnostic code の feature doc 反映経路) は P3 側で解消を確認。
- prompts 自己完結性: NEEDS_WORK (見落とし) — 同種の新規 diagnostic code (E3 用、P1_01 ステップ3) にも同じ課題が残存しており、指摘パターンが P1 に波及していなかった。
- template 必須節: NEEDS_WORK — review.md / index.md の `## レビュー` / `## 変更履歴` に今回の NEEDS_WORK ラウンドの記録が欠落 (文書メタ情報の同期違反)。

指摘 2 件 (対応: 本 commit で修正):

1. P1_01 ステップ3に新規 diagnostic code 名 `JAVA_SOOTUP_UNAVAILABLE` (severity: warning) を確定し、P3 と同様の feature doc sync ルーティング注記・完了条件更新を追加。
2. spec の予告行を P1/P3 合計 4 code に拡張。review.md / index.md のレビュー記録・変更履歴を同期。

## Review 2026-07-14 — Phase 7 (tasks) prompts 生成分 (最終レビュー)

Verdict: PASS

- 上位文書整合 / 未解決論点 / 実装対象明示 / EARS acceptance / 正本境界: PASS。template 必須節: N/A (prompts 対象)。
- prompts 自己完結性: PASS — P1 (`JAVA_SOOTUP_UNAVAILABLE`) / P3 (3 code) とも severity 確定値を直書き、feature doc sync ルーティング注記も両者に整合的に付与。P2 は中間結果生成のみで診断コード追加を P3 に明示的に分離しており、同種の見落としは検出されず。

指摘: なし。tasks phase (P1/P2/P3) を最終 PASS として確定。

## Review 2026-07-14 — D9 (Core metadata passthrough) 追加分

Verdict: PASS

- 上位文書整合: PASS — D9 の事実根拠 (`core/internal/graph/graph.go` の `Edge`/`Symbol` に `Metadata` フィールドなし、`convert.go` がコピーしていない) を実コードで確認。D2/D6 への訂正注記は決定文を書き換えず追記のみ、S5 (非破壊) とも整合。feature doc (analyzer-protocol) への sync も確認。
- 未解決論点 / 実装対象明示 / template 必須節 / EARS acceptance / 正本境界: PASS。
- prompts 自己完結性: PASS — 新規 `P1_02_core_metadata-passthrough.md` は必須10節を具備、命名規約準拠、既存 P1_01/P2_01/P3_01 との依存関係 (P1_01 と並列可、P3 が P1_02 に依存) が実装タスク案テーブルと一致。

指摘: なし。D9 を解決済み論点として確定。フェーズ5・10 を PASS として最終化する。

## Review 2026-07-14 — D9 訂正分 (案 B、#22 D11 へ委譲) 1回目

Verdict: NEEDS_WORK → 指摘対応後に再レビュー予定

- 上位文書整合 / 未解決論点 / 実装対象明示 / EARS acceptance / prompts 自己完結性 / 正本境界: PASS — #22 D11 (`specs/22-cli-interface/index.md`) の内容・決定日 (2026-07-12) を実ファイルで確認し #21 側の記述と完全一致。実装対象表・実装タスク案・prompts (`P1_02` 削除、P3 依存除去) が D9 追加前の状態に正しく復元されていることを確認。
- template 必須節: NEEDS_WORK — フェーズ状況表 (#10/#11) が「spec-review PASS」「レビュー済」と記述しているが、対応する review.md エントリ・`## レビュー` テーブルの verdict が未記録のまま (訂正内容に対するレビュー自体が本ラウンドであるため、記述が先行していた)。

指摘 1 件 (対応: 本 commit で修正):

1. `## レビュー` テーブルの該当行に本ラウンドの PASS verdict を記録し、review.md にも同内容を追記して同期する。

## Review 2026-07-14 — D9 訂正分 (案 B、#22 D11 へ委譲) 最終レビュー

Verdict: PASS

- 前回指摘 (レビュー記録の同期漏れ) を解消: `index.md` の `## レビュー` テーブル最終行を PASS verdict に更新、本エントリで review.md にも記録。
- 内容面 (D9 の委譲判断、実装対象表・実装タスク案・prompts の復元、feature doc の二階建て参照) はいずれも前回ラウンドで PASS 済みで変更なし。

指摘: なし。D9 (案 B、#22 D11 へ委譲) を最終確定。フェーズ10・11 をレビュー済/完了として最終化する。

## Review 2026-07-14 — fresh-context 実装前監査

Verdict: NEEDS_WORK → 指摘対応後に再レビュー予定

- 上位文書整合: NEEDS_WORK — P1 の classpath 欠落時 fallback が、feature doc の明示 classpath entry pre-flight fatal 契約と衝突していた。
- 未解決論点 / prompts 自己完結性: NEEDS_WORK — 自プロジェクト classes directory の入力経路、`callEdge.metadata` の key/value、Spring の Bean 名・`@Qualifier`・`@Primary` 選択規則が実装可能な粒度まで確定していなかった。
- 実装対象明示: NEEDS_WORK — spec の `core: ×` と P3 の Go E2E 追加が不一致だった。
- 検証契約 / メタ情報 / 正本境界: NEEDS_WORK — 強制 E2E の具体コマンド、実装前 status、durable な dispatch/DI フローの feature doc 反映が不足していた。
- #22 D11 への Core metadata passthrough 委譲は妥当であり、変更不要。

対応:

1. 明示 classpath entry の欠落・読取不能は `JAVA_MISSING_JAR` fatal、自プロジェクト classes directory 未指定または pre-flight 後の SootUp 解釈失敗だけを `JAVA_SOOTUP_UNAVAILABLE` fallback として分離した。
2. classes directory は既存 `analysisRequest.metadata.classpath` で渡すこと、metadata の型・値、Bean 選択順序を feature doc と prompts に確定値として反映した。
3. `core/e2e` は test-only 変更として実装対象表・P3 を統一し、強制 E2E コマンドと同一 `feature/21` branch での直列実装を明記した。
4. durable な dispatch/DI フローを feature doc へハンドオフし、spec の図を決定時スナップショットへ降格した。

## Review 2026-07-14 — fresh-context 実装前再監査

Verdict: NEEDS_WORK → 指摘対応後に再レビュー予定

- 上位文書整合: NEEDS_WORK — java-analyzer feature doc の「やらないこと」に Phase2/3 実装と SootUp 範囲決定が残り、後段の #21 正本仕様と衝突していた。
- requirements 同期: NEEDS_WORK — #22 を graph E2E の前提とする古い受け入れ基準、未着手の要求フェーズ、D7〜D9 の欠落が残っていた。
- prompts 自己完結性: NEEDS_WORK — Spring fixture の build、compiled classes、依存 jar、classpath manifest の生成・投入方法と、SootUp artifact/version が未確定だった。
- cross-spec 境界: NEEDS_WORK — #22 D11 は `callEdge.metadata` だけを扱い `methodSymbol.metadata` を対象外としているのに、#21/analyzer-protocol は両方を委譲したように記述していた。

対応:

1. feature doc の scope を #21 の実装・決定済み範囲へ更新し、SootUp 2.0.0 の最小3 moduleを固定した。
2. requirements のフェーズ、E3、#22 非依存 graph E2E、D7〜D9 を最終 spec と同期した。
3. standalone Gradle fixture、固定依存、Java 21 bytecode、`writeDepwalkClasspath`、Go E2E への classpath 投入契約を P3 と正本へ追加した。
4. #22 D11 への委譲を `callEdge.metadata` だけに限定し、`methodSymbol.metadata` の既存 gap は #21/#22 D11 の対象外と明記した。

## Review 2026-07-14 — fresh-context 実装前最終再レビュー

Verdict: PASS

- 上位文書整合 / requirements 同期: PASS — feature scope、D7〜D9、#22 非依存 graph E2E が同期済み。
- 未解決論点 / 実装対象: PASS — D1〜D9 は解決済み。production code は java-analyzer、Core は `core/e2e` の test code のみ。
- E3 / Spring / metadata: PASS — fatal/fallback、Bean 選択、edge metadata の型・値が確定済み。
- prompts 自己完結性: PASS — SootUp 2.0.0 の座標、standalone fixture build、classes/runtime classpath manifest、全検証コマンドが明示済み。
- cross-spec / 正本境界: PASS — #22 D11 へは `callEdge.metadata` だけを委譲し、`methodSymbol.metadata` は対象外。durable flow は feature doc を正本として参照。

指摘: なし。実装を安全に開始できる。
