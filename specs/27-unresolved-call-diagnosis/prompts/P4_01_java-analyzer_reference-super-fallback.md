# P4_01: method reference / explicit super の救済 fallback を実装する

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
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

1. 作業ブランチ `feature-27` が checkout 済みで、P2_01 (fixture) と P3_01 (判定) が完了していることを確認する

### ステップ 1: method reference の救済 (④)

1. Java unit test を先に書く: `mre.resolve()` 失敗時に bytecode 救済 → external-target 分類の順で試み、どちらも成立しない場合のみ diagnostic 化すること
2. method reference の resolve 失敗経路へ、method call と同等の bytecode 救済 (project bytecode member index) と external-target 分類を追加する。型名 scope の static call へ instance member を救済しない既存境界 (PR #26) を維持する
3. `## 検証コマンド` を実行し、diff レビューを回して指摘を対応する

### ステップ 2: explicit constructor invocation の救済 (⑤)

1. Java unit test を先に書く: 明示 `super(...)` / `this(...)` の resolve 失敗時に bytecode 救済 (親 / 自クラスの constructor を index 検索) を試みること
2. explicit constructor invocation の失敗経路へ bytecode 救済を追加する
3. `## 検証コマンド` を実行し、diff レビューを回して指摘を対応する

### ステップ 3: fixture 期待値の成功側への更新

1. P2_01 の fixture のうち ④⑤ ケースの期待値を「未解決」から「edge / 除外へ分類」へ更新し、E2E を通す
2. diff レビューを回し、指摘を対応する

### ステップ最終: 最終確認

1. 全テスト / E2E がパスすることを確認
2. spec の `## 上位資料からの変更点` に必要な追記がないかを phase: track に渡す
3. WIP commit を残す

## 実装コンテキスト

- spec: `specs/27-unresolved-call-diagnosis/index.md`
- 正本 (契約): `design/features/java-analyzer/DesignDoc_java-analyzer.md` の `Parse・resolution・call 完全性` (「救済・除外分類の適用範囲拡大 (spec #27)」の段落) と `solver 層の bytecode member 合成` (PR #26 の選択境界)
- `specs/27-unresolved-call-diagnosis/report.md` の対応方針 (P3_01)
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `analyzers/java/src/main/java/` (CallGraphBuilder の processMethodReference / processExplicitConstructorInvocation 経路)
  - `analyzers/java/src/test/java/`
  - `testdata/fixtures/java/multi-module-spring-project/` / `core/e2e/` (期待値更新)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P2_01_java-analyzer_unresolved-fixture.md`、`P3_01_java-analyzer_disposition.md`
- 完了後に着手可能になる後続 prompt: `P5_01_java-analyzer_remeasure-sync.md` (P4 系全完了後)
- 必要な repo 状態: fixture の ④⑤ ケースが「未解決」期待値で E2E に組み込まれている

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- method reference の receiver 型が取れないケースの扱いが P4_03 (⑥) と重なる場合は、境界を整理してユーザーに確認する

## タスク境界

### 実装する範囲

- ④ method reference の resolve 失敗時 bytecode 救済 + external-target 分類
- ⑤ explicit constructor invocation の resolve 失敗時 bytecode 救済
- 対応する unit test と fixture 期待値の更新 (④⑤ 分のみ)

### 実装しない範囲

- ⑥ receiver 型不明時の external-target 判定 (→ P4_03)
- ⑧ Lombok cross-module 救済 (→ P4_02)
- ①②③⑦ 型推論回避策 (→ P4_04)
- outcome ledger の終端種別・帰属意味論の変更

## 設計仕様

feature doc `Parse・resolution・call 完全性` より (正本):

> bytecode 救済 (project bytecode member index) と `external-target` 除外分類は、method call だけでなく method reference と explicit constructor invocation (`super(...)` / `this(...)`) の resolve 失敗にも適用し、救済・分類を試みてから diagnostic 化する。outcome ledger の 3 終端と帰属意味論は変更しない。

spec EARS (抜粋):

- WHEN method reference / explicit `super(...)` の source 解決が失敗したとき、THEN システムは method call と同等の bytecode 救済と external-target 分類を試みてから diagnostic 化する

## テスト観点

- ④⑤ の fixture ケースが救済後に edge / 除外へ分類され、`JAVA_INCOMPLETE_ANALYSIS` から消える
- scope 内 call を誤って external 分類しない (既存 fixture の期待 graph が変化しない)
- 型名 scope static call の非救済境界 (PR #26) が維持される
- 救済成功時に診断 metadata が Protocol へ出ない (P1_01 の契約維持)

## 検証コマンド

- `cd analyzers/java && ./gradlew test && ./gradlew shadowJar`
- `(cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -run TestGradleMultiProjectCLI -count=1)`
- `cd core && go test ./...`

## 完了条件

- [ ] ステップ 0 で前提 prompt の完了を確認した
- [ ] ④ method reference の救済を unit test 先行で実装した
- [ ] ⑤ explicit constructor invocation の救済を unit test 先行で実装した
- [ ] fixture の ④⑤ 期待値を成功側へ更新し E2E がパスする
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った
- [ ] 未解決の仕様質問が残っていない
