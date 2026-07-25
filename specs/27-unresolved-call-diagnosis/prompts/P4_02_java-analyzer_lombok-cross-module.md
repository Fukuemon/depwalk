# P4_02: cross-module の生成 member 救済欠陥を修正する

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

### ステップ 1: 欠陥の特定

1. P2_01 の ⑧ fixture ケース (module A の Lombok 注釈クラスの生成 constructor / getter を module B から呼ぶ) を使い、cross-module 呼び出しで bytecode 救済 / solver 層合成が働かない原因を、`## 実装コンテキスト` の path 内で特定する (context 間の index 参照範囲、owner class の origin 検証、solver 接続のいずれで漏れるか)
2. 特定した原因と修正方針を 1 段落で記録する (report.md への追記または PR description)。原因が「設計意図と実装の乖離」でなく設計自体の変更を要する場合は、feature doc の変更提案を整理して停止する

### ステップ 2: 修正

1. Java unit test を先に書く: 依存 context の classes output にある生成 member が、呼び出し元 context からの解決で救済されること
2. 設計意図 (member 候補は自 context + classpath 上の依存 project output から採用) どおりに動くよう修正する。origin 検証 (external artifact 非救済) と source 優先帰属は変更しない
3. `## 検証コマンド` を実行し、diff レビューを回して指摘を対応する

### ステップ 3: fixture 期待値の成功側への更新

1. ⑧ ケースの期待値を「未解決」から「edge (bytecode-only member 契約: 定義位置省略 + owner metadata)」へ更新し、E2E を通す
2. diff レビューを回し、指摘を対応する

### ステップ最終: 最終確認

1. 全テスト / E2E がパスすることを確認
2. spec の `## 上位資料からの変更点` に必要な追記がないかを phase: track に渡す
3. WIP commit を残す

## 実装コンテキスト

- spec: `specs/27-unresolved-call-diagnosis/index.md`
- 正本 (契約): `design/features/java-analyzer/DesignDoc_java-analyzer.md` の `solver 層の bytecode member 合成` (「cross-module 救済の欠陥修正 (spec #27)」の段落と PR #26 の選択境界) と `Source root discovery と解析 context` (context 間 solver 接続)
- `specs/27-unresolved-call-diagnosis/report.md` の対応方針 (P3_01)
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `analyzers/java/src/main/java/` (bytecode member index / solver 層合成 / 解析 context の solver 接続)
  - `analyzers/java/src/test/java/`
  - `testdata/fixtures/java/multi-module-spring-project/` / `core/e2e/` (期待値更新)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P2_01_java-analyzer_unresolved-fixture.md`、`P3_01_java-analyzer_disposition.md`
- 完了後に着手可能になる後続 prompt: `P5_01_java-analyzer_remeasure-sync.md` (P4 系全完了後)
- 必要な repo 状態: fixture の ⑧ ケースが「未解決」期待値で E2E に組み込まれている

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 原因が設計変更を要する場合 (feature doc の contract 自体の修正が必要) は、変更提案を整理してユーザーへ停止確認する

## タスク境界

### 実装する範囲

- ⑧ cross-module 生成 member 救済の欠陥特定と修正
- 対応する unit test と fixture 期待値の更新 (⑧ 分のみ)

### 実装しない範囲

- ④⑤ の fallback (→ P4_01)、⑥ の判定 (→ P4_03)、①②③⑦ 回避策 (→ P4_04)
- origin 検証・source 優先帰属・型名 scope static call 境界の変更
- Gradle model / discovery 側の変更

## 設計仕様

feature doc `solver 層の bytecode member 合成` より (正本):

> member 候補は owner class の classfile が project 所有 classes output (自 context + classpath 上の依存 project output) に存在する場合だけ採用し、external artifact だけに存在する同名 class の member を project bytecode として救済しない (D16 の origin 検証)。
> cross-module 救済の欠陥修正 (spec #27): 実環境実測で、依存 context の source 型が持つ生成 member (Lombok constructor / getter 等) の cross-module 呼び出しが救済されず未解決になる欠陥を確認した。上記の設計意図 (依存 project output を含む採用境界) は変更せず、実装を設計どおり機能させる修正を spec #27 で行う。

spec EARS (抜粋):

- WHEN Lombok 生成 member を持つ scope 内型が他 module から呼ばれ source 解決に失敗したとき、THEN システムは所属 context の bytecode member 索引で救済する

## テスト観点

- ⑧ fixture ケースが救済後に edge (bytecode-only member 契約) へ分類される
- 同一 module 内の既存救済 (spec #21/#24 での検証範囲) が退行しない
- external artifact のみ底本の member を誤救済しない (origin 検証維持)

## 検証コマンド

- `cd analyzers/java && ./gradlew test && ./gradlew shadowJar`
- `(cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -run TestGradleMultiProjectCLI -count=1)`
- `cd core && go test ./...`

## 完了条件

- [ ] ステップ 0 で前提 prompt の完了を確認した
- [ ] 欠陥の原因を特定し、修正方針を記録した
- [ ] 修正を unit test 先行で実装した
- [ ] fixture の ⑧ 期待値を成功側へ更新し E2E がパスする
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った
- [ ] 未解決の仕様質問が残っていない
