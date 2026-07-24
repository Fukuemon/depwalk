# Review Log — #32 Core / Java Analyzer アーキテクチャ再編

`spec-review` (fresh-context evaluator: spec-reviewer subagent) の完全な記録。最新結果のサマリは [index.md の ## レビュー](index.md#レビュー) を参照。

## Review 2026-07-23 (scaffold phase 完了時点)

Verdict: PASS

### 観点別評価

- **上位文書整合: PASS** — `index.md` の `## 上位文書整合` テーブルが 10 行埋まっており、継承 / 変更提案が文書別に区別されている。実測課題の根拠は `context/architecture.md:13-14` の実記述と一致。DesignDoc の Why/What / 成功条件 S1〜S5 / Non Goals は「外部挙動を変えない」スコープと矛盾しない。P2/P3 の内部徹底という位置づけも DesignDoc の設計原則と整合。ADR-0002 は追補 ADR での改訂を提案済み、ADR-0001 / 0006 は継承。scaffold 時点で sync へ分岐しない理由も明記されており妥当 (変更内容が論点解決に依存)。
- **未解決論点: PASS** — D1〜D7 は全行「未決」と明示され空欄なし。`## 未確定事項` で clarify phase での確定を宣言し、D1〜D4 は requirements.md で決定者 / 期限付き管理。下流節はすべてプレースホルダで、未決のまま下流記述が進んでいない。
- **実装対象明示: PASS** — 5 target は `context/project.md` の対象ドメイン一覧と完全一致。各 target の責務が読める。検証は既存テストスイート + lefthook / CI で Quick Commands の範囲内。
- **template 必須節: PASS** — `hooks/spec/validate_document.sh` の必須 22 セクションすべて存在。設計フェーズ状況は 11 フェーズ。メタ情報同期も本文と一致。
- **EARS acceptance: PASS** — WHEN / IF / THE SYSTEM SHALL 形式 5 件。いずれも観測可能で requirements.md と整合。
- **prompts 自己完結性: N/A** — prompts phase 未着手。
- **正本境界: N/A** — sync phase 未実行。scaffold 段階で spec が作業正本のままでよい。

### 軽微な改善提案 (判定に影響しない)

- D5〜D7 に決定者 / 期限が未明示 → 対応済み (未確定事項へ決定者 Fukuemon / 期限 clarify 内を補記)
- requirements.md:47 の markdown 崩れ → 対応済み (太字記法を修正)

## Review 2026-07-23 (clarify phase 完了時点)

Verdict: NEEDS_WORK

### 観点別評価 (要旨)

- **上位文書整合: NEEDS_WORK** — 指摘 2 件 (下記)。D1 / D3 / D4 / D5 は上位文書と整合し変更提案も記録済み
- **未解決論点: PASS** — D1〜D7 全件が決定者 / 決定日付きで「解決済みの論点」へ移動済み。未決を残した下流記述なし
- **実装対象明示: PASS** — 5 target が project.md の対象ドメインと一致し、D2/D3/D6 の決定が反映済み
- **template 必須節: PASS** — 必須 22 セクション存在、メタ情報同期良好
- **EARS acceptance: PASS** — requirements.md と整合、全件観測可能
- **prompts 自己完結性 / 正本境界: N/A** — 未着手 phase

### 指摘

1. **D6 の位置づけが不正確**: graph feature doc (`DesignDoc_graph.md:68`) は `SourceLocation` について protocol 型の再利用を明示的に決定済み。`graph -> protocol` import は規約違反の実装漏れではなく公認設計であり、D6 は「乖離の是正」ではなく「feature doc 決定を覆す変更提案」として記録すべき
2. **feature doc への影響行の欠落**: `Node.Source` / `Edge.CallSite` の型置換 (protocol 型 → domain 自前型) の影響行が「feature doc への影響」テーブルにない

### 対応 (2026-07-24)

- 指摘 1: 上位文書整合テーブルの graph 行と背景節を「feature doc の `SourceLocation` 再利用決定を D6 で改訂する変更提案」へ修正
- 指摘 2: feature doc への影響テーブルへ型置換の行を追加 (source: clarify)
- 再レビューは外部資料 (go-service-design) を踏まえた設計見直し後に実施

## Review 2026-07-24 (clarify 再レビュー)

Verdict: PASS

### 前回指摘の対応確認

1. **D6 の位置づけ — 対応済み**: 上位文書整合テーブル graph 行と背景節が「feature doc の `SourceLocation` protocol 型再利用決定を D6 で覆す変更提案」として正確に記録。「乖離の是正」表現は除去済み
2. **feature doc 影響行 — 追加済み**: `Node.Source` / `Edge.CallSite` の domain 自前型への置換行が存在し、feature doc の実型定義と対応

### 精緻化追記 (go-service-design 由来) の整合確認

- D1 追記 (層数・命名自由 / 層ファースト維持根拠): DesignDoc P1〜P4 と矛盾なし
- D5 追記 (depguard の files+deny+desc 記法): engineering.md の「CI gate 要件化時点で追加」条件と整合
- D6 追記 (port 利用側定義 / port package なし / struct 公開 / var _ の cli 集約 / ACL): D6 本体・Interface 設計節・ADR-0002 依存最小方針と一貫

### 観点別評価 (要旨)

- 上位文書整合 / 未解決論点 / 実装対象明示 / template 必須節 / EARS acceptance: すべて PASS
- prompts 自己完結性 / 正本境界: N/A (未着手 phase)

### 参考指摘 (判定に影響しない) → 対応済み

- 設計フェーズ状況「論点解決」行の最終更新日を 2026-07-24 へ揃えた
- sync 時に graph feature doc の「変換は Analyze Use Case 層で 1 回だけ」記述も更新対象であることを feature doc 影響行へ明記した

## Review 2026-07-24 (diagram phase)

Verdict: NEEDS_WORK → 対応後 PASS

### 指摘 (初回 NEEDS_WORK)

1. **層依存図と sequence の矛盾**: sequence では ACL (protocol) が analyzer を駆動するが、層依存図に `protocol → analyzer` がなく、逆に説明のない `analyzer → analyze` 辺が存在
2. **cli の手動 DI 辺の欠落**: D6 (手動 DI / `var _` の cli 集約) に必要な `cli → protocol` / `cli → analyzer` が層依存図にない
3. (軽微) sequence の「invalid record は拒否」が record 単位 skip と誤読しうる (graph feature doc は parse / schema error を fatal 全破棄と規定)
4. (軽微) phase 7 行の状態が図の記載実態と不一致

### 対応 (2026-07-24)

- 層依存図の辺を D6 と一致させた: `analyzer → analyze` 削除、`cli → protocol` (配線 + var _ 検証) / `cli → analyzer` (配線) / `protocol → analyzer` (process 起動に利用) を追加。凡例「辺は Go の import 方向」と辺の読み方の補足 (analyzer は内層を import しない呼ばれる側、cli はコンポジションルート) を明記
- sequence の parse / validate ラベルを「parse / schema error は fatal → 破棄経路へ」に修正
- phase 7 行を「進行中 / 図・配置は phase 6 で確定済み」に補記

### 再レビュー (PASS)

- 指摘 4 件すべて解消を確認。層依存図の全辺が内向き依存で矛盾なし、Mermaid 構文 3 図とも妥当
- Java 配置図は実ソースツリーと全件一致、sootup 漏れ 7 クラスも実測一致
- 参考 (非ブロッキング): 変更履歴への反映行追記 → 対応済み

## Review 2026-07-24 (track phase)

Verdict: NEEDS_WORK → 対応後 PASS

### 独立検証 (reviewer による実測突合)

変更点テーブルの grep 実測主張はすべて上位文書の実記述と一致: Design Doc の `core/internal` 言及ゼロ / feature doc 計 13 箇所 (traversal 3 + cli 3 + output 3 + java-analyzer 1 + graph 3) / context 計 4 箇所 (testing 3 + toolchain 1) / ADR-0003 の 2 箇所。D1〜D7 の反映カバレッジも網羅 (D2 は architecture 3 層改訂 + ADR 統合行に包含、D4 はプロセス判断で上位文書変更不要)。source: clarify / track の二重追記なし。

### 指摘

1. phase 3「上位文書突合」行のメタ情報が track 本文更新に未同期
2. 上位文書整合テーブルが project.md「対象ドメイン」を変更提案としているのに、変更点テーブルに変更要否の記録がない (実測では変更不要)

### 対応 (2026-07-24)

- phase 3 行を「完了 / 2026-07-24」→ 再レビュー後「レビュー済」に更新
- context への影響へ「project.md / 対象ドメイン: 変更不要 (module 名ベースで path 非参照)」行を追加 (source: track)

### 再レビュー (PASS)

- 指摘 2 件の解消を確認。sync phase へ進行可と判断

## Review 2026-07-24 (sync phase)

Verdict: PASS

### 確認内容

- **[反映済] 行の実反映**: 7 系統 (architecture.md 3 層化 + Java 内部境界 / project.md Naming Conventions / engineering.md 層依存 gate / graph feature doc の SourceLocation 改訂 + 変換所在 / java-analyzer feature doc の内部構成節 / ADR-0007 新規 / ADR-0002 追補) すべて実在・改変なしを実地確認
- **正本ハンドオフの完全性**: design 側の正本宣言と spec 側の「決定時スナップショット」降格を確認。二重正本なし。用語規約 (spec = 決定経緯) 遵守。反映先文書のメタ情報 (最終更新 / Status / 変更点行) も同期済み
- **D1〜D7 一致**: 段階実行順・JavaParser 隔離 3 段階・depguard 記法・手動 DI / `var _` 集約まで欠落なし
- **path 機械追随の子 issue 委譲**: 残存旧 path (feature doc ≈13 箇所 / context 4 箇所) が spec の記録と合致し、architecture.md に drift 注記あり

### 参考指摘 (非ブロッキング)

- フェーズ表に sync 専用行がない (変更履歴と上位文書整合で記録されており許容範囲)
- graph feature doc は新 path を先行使用しており drift 注記がない → 子 issue ① 完了までの短期 drift 窓として認識
