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
