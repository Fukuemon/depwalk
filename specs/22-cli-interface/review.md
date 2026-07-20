## Review 2026-07-12 14:00 (scaffold phase gate)

Verdict: NEEDS_WORK

### 観点別評価

- **上位文書整合: PASS** — 整合テーブル (index.md:44-52) の引用先を実地検証し一致を確認。Design Doc S1-S3 (DesignDoc.md:41-43)・モジュール責務 (:134)・Phase 計画 (:233-238)、feature doc analysisMode 意味論 (DesignDoc_java-analyzer.md:102)、context/architecture.md:21-30・testing.md:16,20,26、ADR-0002/0003 (0003:24 の前段は論点 D10 として管理) と整合。
- **未解決論点: PASS** — D1-D10 (index.md:115-126) は「未決」明示、未確定事項 (index.md:134-138) で clarify へ委譲。下流節はすべて placeholder で、未決のまま下流記述が進んだ箇所なし。
- **実装対象明示: PASS** — target 5 件は context/project.md:68 の対象ドメインと一致。変更は core のみ、traversal/output は「既存 API 利用」と境界が読める。
- **template 必須節: PASS** — template の全 23 節が存在。メタ情報 / フェーズ状況 / 変更履歴も同期済み。
- **EARS acceptance: NEEDS_WORK** — `## 要件の解釈` の EARS 風記述 (index.md:107-109) が placeholder のまま 0 件。scaffold の手順 5 は「要件の解釈」を埋める対象としており placeholder 免除の範囲外。成功条件 S1/S2/S3 を素材に最低 1 件以上 (推奨 3 件) の EARS 記述を追加すること。あわせて「実現したいユーザー価値」(index.md:92)・「対象ユーザー」(index.md:105) も DesignDoc L102-104 (開発者 / CI) から埋められる。
- **prompts 自己完結性: N/A** — prompts 未生成。
- **正本境界: N/A** — sync phase 未実行、scaffold 段階で spec が作業正本 (契約どおり)。

### 指摘

1. index.md:107-109 — EARS 記述 0 件。S1 (caller 探索) / S2 (callee 探索) / S3 (パース可能出力) の 3 件を追加してから scaffold 完了とすること。ユーザー価値・対象ユーザーも同時に記入可。
2. (軽微) index.md:26 — フェーズ表の「上位文書突合」が未着手表示だが整合テーブルは実質記入済み。状態と実態を同期させること。

## Review 2026-07-12 (scaffold phase gate 再レビュー)

Verdict: PASS

- 上位文書整合: PASS — 整合テーブル全引用先を実地照合し一致 (Design Doc S1-S3 / feature doc analysisMode 意味論 / architecture.md / testing.md / ADR-0002・0003)
- 未解決論点: PASS — D1-D10 すべて「未決」明示、下流節は placeholder のまま (scaffold 期待どおり)
- 実装対象明示: PASS — 5 target が context/project.md の対象ドメインと一致、変更は core のみで境界と逸脱時手続きが読める
- template 必須節: PASS — 全 23 節存在、フェーズ表同期済み (前回指摘 2 対応確認)
- EARS acceptance: PASS — S1/S2/S3 の WHEN 形式 3 件、観測可能な記述 (前回指摘 1 対応確認)
- prompts 自己完結性 / 正本境界: N/A (scaffold 段階)
- 補足 (非ブロッキング): レビュー表の「対応方針をユーザー確認中」を「対応済」へ同期すること → 本記録で対応

## Review 2026-07-12 (clarify phase gate)

Verdict: NEEDS_WORK

- 上位文書整合: PASS — D1-D11 の全決定を feature doc / context / ADR-0003 / 実装現状 (traversal.go / output types.go / cli analyze.go) と実地照合し一致
- 未解決論点: PASS — 論点テーブル空、D1-D11 解決済み、未確定事項なし、下流節は placeholder (期待どおり)
- 実装対象明示: PASS — 5 target 一致、output の ◯ 昇格経緯と traversal の逸脱時手続きが読める
- template 必須節: NEEDS_WORK — メタ情報同期 3 件 (下記指摘 1-3)
- EARS acceptance: PASS — S1/S2/S3 の観測手段が D8/D9 で裏付け済み
- prompts 自己完結性 / 正本境界: N/A

指摘:

1. フェーズ表「論点解決」が未着手のまま (実態: 全解決) → 完了へ同期
2. 整合ヘッダ「ADR 起票要否: 要」が D10 確定後も残存 → 不要へ同期
3. D11 の graph/output Metadata 追加が「feature doc への影響」に未記載 → 反映予定行を追記
4. (非ブロッキング) specs/21 参照が本ブランチで未解決な旨を関連資料に注記
5. (非ブロッキング) D1 の <qualifiedName> 用語が feature doc の定義と揺れ → <型の binary name> へ統一

総評: D1-D11 は相互矛盾なく上位文書・実装と整合。残りはメタ情報同期のみで修正後 PASS 可能な水準。

## Review 2026-07-12 (clarify phase gate 再レビュー)

Verdict: PASS

- 上位文書整合: PASS — D1-D11 全決定の引用先を実地照合し一致 (D6=feature doc L102 の責務充足、D10=ADR-0003 L24 の範囲内、D8=traversal 正常 status の CLI 層再解釈として理由明記、D4/D5/D11=実装現状と一致)
- 未解決論点: PASS — 論点テーブル空、D1-D11 解決済み、未確定事項なし、下流節 placeholder (期待どおり)
- 実装対象明示: PASS — 5 target 一致、変更範囲と D 番号の紐付け・逸脱時手続きが読める
- template 必須節: PASS — 全 23 節存在、前回指摘のメタ情報同期 3 件を解消確認
- EARS acceptance: PASS — S1/S2/S3 の観測手段が D8/D9 で裏付け
- prompts 自己完結性 / 正本境界: N/A — sync 前段として「反映予定」行の準備が整っている
- 前回指摘 5 件: すべて解消を確認

補足 (非ブロッキング、後続 phase で扱う):

- D5 の「registry 登録だけで CLI 自動露出」には、登録済み Format 列挙 API の output 側公開が必要 → Interface 設計 phase で明示すること
- specs/21 の参照は #21 merge 後に解決すること

## Review 2026-07-15 12:00 (D11 拡張分 clarify 再オープン、develop rebase 後)

Verdict: NEEDS_WORK

### 観点別評価

- 上位文書整合: PASS — D11 拡張本文の実装コード上の前提 (`core/internal/graph/convert.go:6-25` が Metadata をコピーしていないこと、`graph/graph.go:26-44` の Symbol/Edge に Metadata フィールドが無いこと、`CallGraphBuilder.java:534-541,567-571` が LIFTED 時に declaringType/inherited を実際に metadata へ設定していること) はすべて実コードと一致。feature doc (`DesignDoc_java-analyzer.md:265`)・context (`testing.md:16,20`) とも整合。
- 未解決論点: PASS — 論点テーブル空、未確定事項なし、下流節は placeholder のまま
- 実装対象明示: PASS — target 名が `context/project.md:68` と一致、D11 拡張の責務変更 (core/output を Edge/Symbol 双方に拡大) が明記
- template 必須節: NEEDS_WORK — (1) `index.md:28` フェーズ5が「レビュー済」を自称する一方、本レビュー記録が `## レビュー` 表に無かった、(2) `specs/21-java-dispatch-spring-di/index.md:24` フェーズ5備考が D9 override (`:218`, `:552`) 前の要約のまま同期されていなかった
- EARS acceptance: PASS — S1-S3 とも観測可能な記述
- prompts 自己完結性 / 正本境界: N/A (phase 10 未着手 / sync phase 未実行)

### 指摘

1. `index.md:28` / `## レビュー` 表 — 本レビュー結果を追記してからフェーズ状態を確定させること → 対応済み (本レビュー行を追記)
2. `specs/21-java-dispatch-spring-di/index.md:24` — フェーズ5備考を D9 override 後の内容に同期すること → 対応済み

### 補足 (非ブロッキング)

- `design/DesignDoc_java-analyzer.md:336` は現状 edge 側の CLI 表出境界のみ言及。sync phase で node 側 (`graph.Symbol`/`methodSymbol.metadata`) の境界記述追加を忘れないこと。

## Review 2026-07-15 14:30 (phase 6 Interface/Routing 設計)

Verdict: NEEDS_WORK

- 上位文書整合: NEEDS_WORK — `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md:113,231` (durable 正本、#21 の 2026-07-14 sync で反映済み) が「`methodSymbol.metadata` は #21/#22 D11 双方の対象外」という override 前の記述のまま残っており、`specs/22-cli-interface/index.md:139-142` の D11 拡張 (2026-07-15、#21 D9 を override) と矛盾していた。sync phase を待たず二重正本状態が生じていた。
- 未解決論点: PASS — 論点テーブル空、下流節は D1-D11 の決定を参照するのみで未決前提の記述なし
- 実装対象明示: PASS — target 名・責務境界とも `context/project.md` と一致
- template 必須節: PASS — 全節存在
- 正本境界: NEEDS_WORK — 同一 durable 事実について design (analyzer-protocol feature doc) が古い内容を、spec (#22) が新しい決定を独立に保持する二重正本状態
- 指摘: (1) feature doc (analyzer-protocol) の override 未反映、(2) フェーズ6状態「完了」と備考「レビュー待ち」の不一致
- 内容面 (CLI flag 体系・exit code・Request/Response 変換・`output.RegisteredFormats()` 提案) は D1-D11・実装コードと矛盾なし

対応: `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md` (L3/L113/L231) と `specs/21-java-dispatch-spring-di/index.md:470` を override 後の内容へ更新、フェーズ6状態を「進行中」に修正。

## Review 2026-07-15 (phase 6 再レビュー)

Verdict: PASS

- 前回指摘 3 件 (feature doc override 反映・フェーズ6状態不一致・#21 反映済み行同期) すべて解消を確認
- 3 文書 (spec #22 / spec #21 / feature doc analyzer-protocol) が同一の override 事実を矛盾なく記述していることをクロスチェックで確認
- 新たな不整合なし
- 補足 (非ブロッキング、対応済み): #21 index.md の変更履歴に今回の再同期を追記

## Review 2026-07-15 (phase 7 Content/Data 設計)

Verdict: PASS

- 上位文書整合: PASS — 「永続ストアなし」判断は `context/architecture.md:47-48` と一致。package 配置方針は `context/architecture.md:19-30` の package 一覧および実コード (`core/internal/cli/analyze.go`, `core/internal/graph/convert.go`, `core/internal/output/registry.go`) と矛盾なし。D11拡張の Metadata 透過方針は override 反映済みの feature doc と一致。
- 未解決論点: PASS — 論点テーブル空、下流節は D1/D5/D6/D7/D9/D11 のみ参照し未決前提の記述なし
- 実装対象明示: PASS — target が `context/project.md` と一致、`context/architecture.md` の依存方向規約とも矛盾なし
- template 必須節: PASS — 全節存在、フェーズ表11行、メタ情報・変更履歴とも同期済み
- EARS acceptance: PASS
- prompts 自己完結性: N/A (phase 10 未着手)
- 正本境界: PASS — feature doc への影響テーブルで analyzer-protocol 行のみ「反映済」、他は「未反映」のまま (sync phase 前の契約どおり)

指摘: なし

## Review 2026-07-15 15:30 (phase 8 Performance/Security 設計)

Verdict: NEEDS_WORK

- 上位文書整合: PASS — Performance 節は feature doc (java-analyzer 性能方針節) の「SLO は #22 完了時に確定」という委譲を、数値を捏造せず実装フェーズへ引き継いでいる。Security 節も context/architecture.md の読み取り専用方針と整合。D11 Fallback も #21 index.md D6 の観測責務境界と整合。
- 未解決論点: PASS
- 実装対象明示: PASS
- template 必須節: NEEDS_WORK — フェーズ8の状態が「完了」・備考「レビュー待ち」で不一致。フェーズ6で既に自己是正した同一パターンが再発
- EARS acceptance: PASS
- prompts 自己完結性 / 正本境界: N/A

指摘: フェーズ8の状態表記を実態 (レビュー未了) に合わせて「進行中」に修正すること → 対応済み

## Review 2026-07-15 (phase 8 再レビュー 2回目)

Verdict: NEEDS_WORK

- フェーズ8の状態修正自体 (完了→進行中) は解消を確認
- ただし `## 変更履歴` にこの修正を記録する行がなく、フェーズ6の同種修正時の運用 (変更履歴にも記録) から外れていた
- 指摘: 変更履歴への追記漏れを解消すること → 対応済み

## Review 2026-07-15 (phase 8 再レビュー 3回目)

Verdict: PASS

- 変更履歴への記録漏れが解消
- フェーズ表・レビュー表・変更履歴の3節 (状態・日付) が矛盾なく整合していることを確認
- 新たな不整合なし。phase 8 (Performance/Security 設計) 完了

## Review 2026-07-20 (fresh-context evaluator) — develop (#24) rebase 後の再検証更新分

Verdict: PASS

- 上位文書整合: D12 の出自 (spec #24 変更履歴 2026-07-18・E2E 引き継ぎコメント・feature doc analyzer-protocol の include/exclude 契約) と一致。実装コード現状 (graph.Symbol.Metadata #24 実装済み / EdgeFromCallEdge 未保持 / 既存 flag 4 つ / AnalyzerFailure 経路 / request.Validate() の検証実在) と D8 拡張・D11 進捗注記・D12 の記述が一致
- 未解決論点: なし (D1-D12 全解決、phase 9-11 未着手と整合)
- 実装対象明示・template 必須節・EARS・正本境界: いずれも PASS。メタ情報同期 (更新日・phase 5-8 降格・変更履歴) も確認
- 非ブロッキング補足: 上位文書整合テーブルの行番号参照 2 件が #24 マージ後の実体とずれ (java-analyzer analysisMode 節は L141-152 へ移動、DesignDoc Phase 計画は L234-241)。次回更新時に現状化を推奨 → 同日対応済み

## Review 2026-07-20 (fresh-context evaluator) — phase 9 Test / Metrics 設計

Verdict: PASS

- 上位文書整合: テスト観点が context/testing.md の検証境界 (mock 方針・golden 配置・E2E 2 層・S1-S3 リリース判定) と一致。SLO 引き継ぎは spec #24 D8 と正確に対応。参照した実装コード (fakeAnalyzerCommand / echo-source-roots / RejectsInvalidSourceRoot / buildCoreCLI / runCLI / NodeFromMethodSymbol deep copy test) すべて実在確認
- 未解決論点・実装対象明示・template 必須節・EARS・正本境界: いずれも PASS。S1-S3 が golden 照合 / Unmarshal / exit code 3 区分の観測可能なテストに落ちていることを確認
- 非ブロッキング補足 2 件 (sync phase 対応推奨): (1) context/testing.md L87 の「既存 Output schema は表出しない」を #22 後の状態へ現状化、(2) context への影響テーブルへ context/testing.md の更新予定行を追加 → (2) は同日対応済み、(1) は影響テーブルの追跡行として登録済み (sync phase で反映)

## Review 2026-07-20 (fresh-context evaluator) — diagram phase

Verdict: NEEDS_WORK → 対応済み

- Mermaid 構文は両図とも有効。エラーケース 1-8 と exit code 0/1/2 の分岐網羅、D5/D6/D7/D8/D11/D12 注記は本文と整合
- 指摘 1: Sequence 図が traversal.Traverse / output.Write の呼び出しを CLI 層に置いており、スコープ「analyze use case から traversal / output への結合」(index.md やること) と context/architecture.md の use case orchestration 責務に矛盾 → use case 側 orchestration へ図を修正
- 指摘 2: Content/Data の「method selector 照合結果の受け渡し」が図と噛み合わず曖昧 → use case が照合〜出力を orchestrate し、曖昧・不一致は種別付きエラーで CLI 層へ返す (CLI 層は表示と exit code 判別のみ) と明確化

## Review 2026-07-20 (fresh-context evaluator) — diagram 再レビュー

Verdict: PASS

- 前回指摘 2 件の対応を確認: Sequence の selector 照合・Traverse・Write の呼び出し元が analyze use case へ修正され、context/architecture.md の use case orchestration 責務およびスコープ宣言と整合。Content/Data の責務記述も図と完全一致
- 波及矛盾なし: Interface 設計の exit code 判別 (CLI 層)・テスト観点の配置・「本 spec の追加分」への呼称統一を確認
