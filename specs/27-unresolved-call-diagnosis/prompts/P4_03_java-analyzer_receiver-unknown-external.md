# P4_03: receiver 型不明時の external-target 判定を実装する

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- 判定規則は P3_01 でユーザー承認された規則のみを実装する。承認されていない緩和 (scope 内 call を external へ流す等) を加えない
- **完了条件のタスク化**: 作業開始前に「完了条件」セクションの各項目を todo として登録し、各ステップ完了時に状態を更新すること。タスク化せずに作業を開始することは禁止

### 実装アンチパターンの回避 (必守)

- スコープ厳守: spec / 本 prompt に明記された機能のみ実装する。未要求の機能追加・
  先回りの抽象化・無関係なリファクタ・暗黙の互換維持をしない。
- 既存規約への整合: 命名・エラー処理・ログ・テスト・API 連携方式は、対象コードベースの
  既存パターンに合わせる。新方式を持ち込む場合は理由を述べて確認を取る。
- 観測可能な契約の保持: UI 文言・イベント名・戻り値・エラーメッセージ・ログ形式・API を
  要求なく変更しない。変更が必要なら理由と影響を明記する。
- 推測の排除: 要件・業務ルール・API 仕様が不明なら停止して確認する。それらしいが
  誤った実装 (存在しない API 呼び出し / 非互換な引数) を避け、import と API の実在を確認する。
- fallback の最小化: `??` / `||` / 既定引数 / 多段 fallback / 暗黙のエラー握り潰しは
  「任意データ」に限定する。必須データの欠落は隠さず明示的に失敗させる。
- 過剰実装の排除: 単純な条件分岐を strategy / handler map に置換しない。
  要求も計測もない caching / memoization を入れない。
- dead code を残さない: 到達不能コード・未使用の変数 / 関数 / import / export・
  変更後に不要化した型定義を削除する。
- 判断の記録: 非自明な設計判断は理由 (or spec / ADR へのリンク) を残す。

## 作業ステップ (この順序で実行する)

### ステップ 0: ブランチ準備

ブランチ命名と base branch は `AGENTS.md` の `Spec Workflow Contract` および `workflow-git` に従う。

1. 作業ブランチ `feature-27` が checkout 済みで、P2_01 (fixture) と P3_01 (判定規則の承認) が完了していることを確認する
2. `report.md` に ⑥ の判定根拠規則 (承認済み) があることを確認する。無ければ停止する

### ステップ 1: 判定規則の実装

1. Java unit test を先に書く: 承認済み規則の positive (external へ分類してよい) / negative (scope 内の可能性が残るため diagnostic に留める) 両ケース
2. receiver 型が取得できない call の分類経路へ、承認済み判定規則を実装する。規則が成立しない場合は従来どおり primary diagnostic に残す (保守側へ倒す)
3. `## 検証コマンド` を実行し、diff レビューを回して指摘を対応する

### ステップ 2: fixture / E2E の検証

1. fixture のうち ⑥ に該当するケース (fluent chain 等で receiver 型が失われた scope 外 call) の期待値を `external-target` 除外へ更新し、scope 内 call が誤って除外されないことを既存期待 graph の不変で確認する
2. `## 検証コマンド` を実行し、diff レビューを回して指摘を対応する

### ステップ最終: 最終確認

1. 全テスト / E2E がパスすることを確認
2. 実装した判定規則を feature doc `Parse・resolution・call 完全性` の該当段落へ追記する (「判定根拠の詳細規則は spec #27 の実装で確定し本節へ追記する」の履行)。追記後、spec の `## 上位資料からの変更点` へ反映済み注記を足す
3. WIP commit を残す

## 実装コンテキスト

- spec: `specs/27-unresolved-call-diagnosis/index.md`
- 正本 (契約): `design/features/java-analyzer/DesignDoc_java-analyzer.md` の `Parse・resolution・call 完全性` (「救済・除外分類の適用範囲拡大 (spec #27)」の段落) と `帰属型の決定規則` (external-target / lift-excluded-package の既存意味論)
- `specs/27-unresolved-call-diagnosis/report.md` の ⑥ 判定根拠規則 (P3_01 承認済み)
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `analyzers/java/src/main/java/` (external-target 分類経路)
  - `analyzers/java/src/test/java/`
  - `testdata/fixtures/java/multi-module-spring-project/` / `core/e2e/` (期待値更新)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P2_01_java-analyzer_unresolved-fixture.md`、`P3_01_java-analyzer_disposition.md` (⑥ 規則の承認が必須)
- 完了後に着手可能になる後続 prompt: `P5_01_java-analyzer_remeasure-sync.md` (P4 系全完了後)
- 必要な repo 状態: fixture が E2E に組み込まれている

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 承認済み規則で判定できない境界ケースが実装中に見つかったら、規則の追加要否をユーザーへ確認する (勝手に規則を広げない)

## タスク境界

### 実装する範囲

- ⑥ receiver 型不明時の external-target 判定 (承認済み規則のみ)
- 対応する unit test と fixture 期待値の更新 (⑥ 分のみ)
- feature doc への判定規則の追記 (履行済み宣言の解消)

### 実装しない範囲

- ④⑤ fallback (→ P4_01)、⑧ 救済 (→ P4_02)、①②③⑦ 回避策 (→ P4_04)
- external-target / lift-excluded-package の既存意味論の変更
- 完全性 gate の緩和 (未解決を成功へ倒さない)

## 設計仕様

feature doc `Parse・resolution・call 完全性` より (正本):

> receiver 型が取得できない call でも、静的に scope 外と判定できる根拠がある場合は primary diagnostic ではなく `external-target` 除外へ分類する (判定根拠の詳細規則は spec #27 の実装で確定し本節へ追記する)。outcome ledger の 3 終端と帰属意味論は変更しない。

spec EARS (抜粋):

- IF receiver 型が取得できず、かつ call が scope 外と判定できる根拠があるとき、THEN システムは primary diagnostic ではなく `external-target` 除外へ分類する

spec Error / Fallback 設計 (抜粋):

> 救済 fallback 追加により scope 内 call を誤って external 分類する → 既存 fixture の期待 graph / call-site outcome 集計との差分で検出 → fixture 回帰検証で修正

## テスト観点

- 承認済み規則の positive / negative 両ケースが unit test で担保されている
- scope 内 call が誤って external 分類されない (既存期待 graph 不変)
- 規則不成立時は diagnostic に残る (完全性 gate の保守性維持)

## 検証コマンド

- `cd analyzers/java && ./gradlew test && ./gradlew shadowJar`
- `(cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -run TestGradleMultiProjectCLI -count=1)`
- `cd core && go test ./...`

## 完了条件

- [ ] ステップ 0 で承認済み規則の存在を確認した
- [ ] 判定規則を unit test 先行で実装した (positive / negative 両方)
- [ ] fixture の ⑥ 期待値を更新し、誤 external 分類がないことを確認した
- [ ] feature doc へ判定規則を追記し、spec の変更点へ反映済み注記を足した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] 未解決の仕様質問が残っていない
