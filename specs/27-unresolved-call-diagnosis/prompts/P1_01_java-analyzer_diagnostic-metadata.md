# P1_01: 未解決 call の診断 metadata 4項目を実装する

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

1. 作業ブランチ `feature-27` が checkout 済みであることを確認する (存在済み。新規作成しない)
2. Draft PR が未作成なら、完了条件を description に転記して Draft PR を作成し push する

### ステップ 1: 診断 metadata の記録機構

1. Java unit test を先に書く (TDD): 解決失敗した call site の diagnostic に `resolutionPhase` / `exceptionClass` / receiver 式種別 / receiver 型取得成否 が入ること、救済成功時は Protocol へ出力されないこと
2. `analyzers/java/` の CallGraphBuilder 系 (method call / method reference / explicit constructor invocation / object creation の処理経路) で、resolve 失敗時に sanitize 済み 4 項目を内部 diagnostic に記録する
3. `exceptionClass` は例外クラスの単純名または FQCN のみとし、message / stacktrace / path を含めない
4. `## 検証コマンド` を実行する
5. diff レビュー (`spec-review` または repo の標準レビュー手段) を回し、指摘を対応する

### ステップ 2: `error.details.metadata` への出力

1. Java unit test を先に書く: `JAVA_INCOMPLETE_ANALYSIS` の `error.details[].metadata` に既存 reason / callKind / target / candidate と並んで 4 項目が出ること、`silentOmission == 0`・total / reasonCounts 整合が保たれること
2. primary diagnostic として終端した call site のみ、記録済み 4 項目を `error.details.metadata` へ出力する (救済成功時は破棄してよい。保持する場合も出力しない)
3. `## 検証コマンド` を実行する
4. diff レビューを回し、指摘を対応する

### ステップ最終: 最終確認

1. 全テスト / lint がパスすることを確認
2. spec の `## 上位資料からの変更点` に必要な追記がないかを phase: track に渡す
3. WIP commit を残す (protected branch へ直接コミットしない)

## 実装コンテキスト

- spec: `specs/27-unresolved-call-diagnosis/index.md`
- 正本 (契約): `design/features/java-analyzer/DesignDoc_java-analyzer.md` の `diagnostic / error code 体系` (診断 metadata 4項目の段落) と `Parse・resolution・call 完全性`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `analyzers/java/src/main/java/` (CallGraphBuilder と diagnostic 生成経路)
  - `analyzers/java/src/test/java/` (対応する unit test)

## 前提条件

- 完了しているべき phase / 依存 prompt: なし (最初の prompt)
- 完了後に着手可能になる後続 prompt: `P2_01_java-analyzer_unresolved-fixture.md`、`P2_02_java-analyzer_remeasure-classification.md`
- 必要な repo 状態: `feature-27` branch、`cd analyzers/java && ./gradlew test` が成功する状態

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する
- 特に `resolutionPhase` の段階区分の粒度が既存実装の解決経路と噛み合わない場合は、案を提示して停止する

## タスク境界

### 実装する範囲

- 解決失敗時の診断 4 項目 (`resolutionPhase` / `exceptionClass` / receiver 式種別 / receiver 型取得成否) の内部記録
- primary diagnostic 終端時のみの `error.details.metadata` への出力
- 上記の Java unit test

### 実装しない範囲

- 救済 fallback の追加・変更 (→ P4_01 / P4_02 / P4_03)
- fixture 追加 (→ P2_01)
- Protocol / Model schema の変更 (metadata は opaque key-value のまま)
- Core (Go) 側の変更 (既存の汎用 passthrough で対応)

## 設計仕様

feature doc `diagnostic / error code 体系` より (正本):

> `JAVA_INCOMPLETE_ANALYSIS` の `error.details.metadata` には、既存の reason / callKind / target / candidate に加えて、sanitize 済みの診断 4 項目 — `resolutionPhase` (失敗した解決段階) / `exceptionClass` (resolver 例外のクラス名のみ、message は含めない) / receiver 式種別 / receiver 型取得成否 — を含める。診断 metadata は解決失敗時点で内部記録し、その call site が primary diagnostic として終端した場合のみ Protocol へ出力する (救済成功時は出力しない)。metadata は opaque な key-value であり Protocol schema は変更しない。sanitize 制約 (source 本文・絶対 path・classpath entry・credential・raw exception message の禁止) を維持する。

spec EARS (抜粋):

- WHEN 開発者が実環境 Gradle multi-project に対して `depwalk analyze` を実行し `JAVA_INCOMPLETE_ANALYSIS` を受け取ったとき、THEN システムは `error.details` から要因クラスを判別できる情報を提供する
- THE SYSTEM SHALL 要因分類の結果を Protocol / Graph 出力契約を変更せずに得られるようにする

## テスト観点

- 4 項目が解決失敗 call の `error.details.metadata` に含まれる (callKind 別: method-call / method-reference / object-creation / explicit-constructor-invocation)
- 救済成功した call には 4 項目が Protocol 出力されない
- `exceptionClass` に message・path・source 断片が混入しない (sanitize 制約)
- 既存の total / reasonCounts / `silentOmission == 0` 整合が壊れない

## 検証コマンド

- `cd analyzers/java && ./gradlew test`
- `cd analyzers/java && ./gradlew shadowJar`
- `cd core && go test ./...` (回帰確認)

## 完了条件

- [ ] ステップ 0 でブランチ確認と Draft PR を準備した
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] 診断 4 項目が primary diagnostic 終端時のみ出力されることをテストで確認した
- [ ] sanitize 制約 (raw exception message 等の禁止) をテストで確認した
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った
- [ ] 未解決の仕様質問が残っていない
