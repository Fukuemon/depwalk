# P4_04: 型推論限界の回避策を実装する (P3_01 で修正と判定した分のみ)

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- 実装対象は P3_01 で「修正」と判定された ①②③⑦ のクラスのみ。scope 外記録と判定されたクラスへ回避策を実装しない
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
2. `report.md` の対応方針から「修正」と判定された ①②③⑦ のクラスと、その承認済み回避策の一覧を確認する。「修正」判定が 0 件ならこの prompt は空振りで完了とし、その旨を記録して終了する

### ステップ 1: 回避策の実装 (判定されたクラスごとに繰り返す)

1. Java unit test を先に書く: 対象パターン (fixture の該当ケース) が回避策で解決されること
2. 承認済みの回避策を実装する。JavaParser の内部 API への非公式依存や、根拠のない型の推測 (false edge の温床) を持ち込まない。解決できない場合は正しく diagnostic に残す
3. `## 検証コマンド` を実行し、diff レビューを回して指摘を対応する

### ステップ 2: fixture 期待値の更新

1. 修正したクラスに対応する fixture ケースの期待値を成功側へ更新し、E2E を通す
2. scope 外記録と判定されたクラスのケースは「未解決のまま」を期待値として維持されていることを確認する
3. diff レビューを回し、指摘を対応する

### ステップ最終: 最終確認

1. 全テスト / E2E がパスすることを確認
2. spec の `## 上位資料からの変更点` に必要な追記がないかを phase: track に渡す
3. WIP commit を残す

## 実装コンテキスト

- spec: `specs/27-unresolved-call-diagnosis/index.md`
- `specs/27-unresolved-call-diagnosis/report.md` の対応方針 (P3_01。実装対象と回避策の正)
- 正本 (契約): `design/features/java-analyzer/DesignDoc_java-analyzer.md` の `型解決` / `Parse・resolution・call 完全性` / `solver 層の bytecode member 合成` (erasure degrade の既存挙動)
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `analyzers/java/src/main/java/` (型解決・chain 解決経路)
  - `analyzers/java/src/test/java/`
  - `testdata/fixtures/java/multi-module-spring-project/` / `core/e2e/` (期待値更新)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P2_01_java-analyzer_unresolved-fixture.md`、`P3_01_java-analyzer_disposition.md` (①②③⑦ の修正判定と回避策の承認が必須)
- 完了後に着手可能になる後続 prompt: `P5_01_java-analyzer_remeasure-sync.md` (P4 系全完了後)
- 必要な repo 状態: fixture が E2E に組み込まれている

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 承認済み回避策が実装中に false edge リスクを持つと判明したら、リスクと代替案を整理してユーザーへ停止確認する

## タスク境界

### 実装する範囲

- P3_01 で「修正」と判定された ①②③⑦ クラスの回避策
- 対応する unit test と fixture 期待値の更新 (該当分のみ)

### 実装しない範囲

- 「v1 scope 外記録」と判定されたクラスへの実装
- JavaParser 本体の fork / パッチ、upstream への依存 version 変更 (別判断。必要なら停止)
- ④⑤⑥⑧ (→ P4_01 / P4_02 / P4_03)
- scope 外記録の文書化 (→ P5_01)

## 設計仕様

spec 解決済み論点 D3(c) (抜粋):

> 上流 (JavaParser) の型推論限界 (①②③⑦) → 回避策の実装コストと件数規模で修正 / v1 scope 外記録を個別判断し、scope 外とする場合は ADR-0004 の再検討条件との整合を明記して記録する。

対象クラスの定義 (spec D1 より):

> ①fluent chain 型推論失敗 ②generic 型変数 / lambda parameter 解決失敗 ③JDK fluent API 後続 call 解決失敗 ⑦`var` + generic メソッド戻り値の型推論失敗

## テスト観点

- 修正対象クラスの fixture ケースが解決され、`JAVA_INCOMPLETE_ANALYSIS` から消える
- 根拠のない型推測による false edge が入っていない (回避策は解決根拠を持つ場合のみ edge を張る)
- scope 外判定クラスのケースは未解決のまま (期待値の維持)

## 検証コマンド

- `cd analyzers/java && ./gradlew test && ./gradlew shadowJar`
- `(cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -run TestGradleMultiProjectCLI -count=1)`
- `cd core && go test ./...`

## 完了条件

- [ ] ステップ 0 で「修正」判定クラスと承認済み回避策を確認した (0 件なら空振り完了を記録)
- [ ] 判定された各クラスの回避策を unit test 先行で実装した
- [ ] fixture 期待値を判定どおりに更新し E2E がパスする
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った
- [ ] 未解決の仕様質問が残っていない
