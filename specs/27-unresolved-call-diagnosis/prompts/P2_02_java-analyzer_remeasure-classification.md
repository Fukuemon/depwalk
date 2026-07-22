# P2_02: 実プロジェクト再計測と要因クラス分類レポートを作成する

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- レポート・spec・commit・issue には集計値 (件数・分類・割合) のみを記録する
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

1. 作業ブランチ `feature-27` が checkout 済みで、P1_01 (診断 metadata) の変更が含まれることを確認する
2. Analyzer jar を rebuild する (`cd analyzers/java && ./gradlew shadowJar`)

### ステップ 1: 再計測

1. Resilience4j (issue #27 コメントに記録された既存計測と同一 commit) と、ユーザーが指定する追加検証プロジェクトの 2 件に対し、`depwalk analyze` を実行して `JAVA_INCOMPLETE_ANALYSIS` の details (診断 metadata 4 項目付き) を取得する。追加検証プロジェクトの場所と実行手順 (JDK / Gradle 設定を含む) は実行時にユーザーへ確認する
2. stderr 出力を一時ファイル (repo 外の scratch 領域) に保存する。repo 内へ生データを置かない
3. 計測条件 (対象 commit、build 有無、実行環境) を記録する

### ステップ 2: 8 分類への機械集計

1. 診断 metadata (`resolutionPhase` / `exceptionClass` / receiver 式種別 / receiver 型取得成否) と reason / callKind / target を使い、spec D1 の 8 分類 (+ 未分類) へ機械的に振り分ける集計スクリプトを scratch 領域で作成・実行する (repo へは committed script を残さない)
2. プロジェクト別 × 要因クラス別の件数分布と、各クラスの代表例 (Resilience4j のみ file / 行を記載可、追加検証プロジェクトは集計値のみ) をまとめる
3. 「未分類」が残る場合は、その diagnostic 特徴を記録し、分類可能にする追加観測が要るかを判断材料として残す

### ステップ 3: 要因分類レポートの作成

1. `specs/27-unresolved-call-diagnosis/report.md` として要因分類レポートを書く: 計測条件 / 分類方法 / プロジェクト別件数分布 / 要因クラス別の代表例と特徴 / 未分類の残余 / P3_01 (対応方針判定) への引き継ぎ事項
2. diff レビューを回し、指摘を対応する

### ステップ最終: 最終確認

1. レポートと spec の整合 (件数・分類名) を確認
2. spec の `## 上位資料からの変更点` に必要な追記がないかを phase: track に渡す
3. WIP commit を残す

## 実装コンテキスト

- spec: `specs/27-unresolved-call-diagnosis/index.md` (D1 の 8 分類定義は `## 解決済みの論点`)
- issue #27 のコメント (Resilience4j 一次調査: 350 件の内訳・module 分布・代表例)
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `specs/27-unresolved-call-diagnosis/report.md` (新規作成)
  - `analyzers/java/build/libs/java-analyzer.jar` (再計測に使用)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P1_01_java-analyzer_diagnostic-metadata.md`
- 完了後に着手可能になる後続 prompt: `P3_01_java-analyzer_disposition.md`
- 必要な repo 状態: Analyzer jar が P1_01 込みで build 済み。実測対象 2 プロジェクトへ到達可能な実行環境 (ユーザー確認)

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 実測対象プロジェクトの場所・実行環境 (JDK version / Gradle 互換設定) が不明な場合は必ずユーザーに確認する
- 未分類が総数の 1 割を超える場合は、分類基準の追加をユーザーへ提案して停止する

## タスク境界

### 実装する範囲

- 実プロジェクト 2 件の再計測 (P1_01 の診断 metadata 付き)
- 8 分類への機械集計と `report.md` の作成

### 実装しない範囲

- 救済 fallback の修正 (→ P4 系)
- 対応方針の判定 (→ P3_01。本 prompt は分類と材料整理まで)
- 集計スクリプトの repo への追加 (scratch 領域で完結)
- 実測対象プロジェクト自体の変更

## 設計仕様

spec 解決済み論点 D1 (8 分類、抜粋):

> ①fluent chain 型推論失敗 ②generic 型変数 / lambda parameter 解決失敗 ③JDK fluent API 後続 call 解決失敗 ④method reference fallback 不足 ⑤explicit `super(...)` fallback 不足 ⑥receiver 型不明で external-target 分類へ到達不能 ⑦`var` + generic メソッド戻り値の型推論失敗 ⑧Lombok 生成 member の cross-module bytecode 救済欠落。分類が困難な残余は「未分類 (要診断 metadata)」として D2 の追加観測で解消する。

spec 成功条件 (抜粋):

> 残存未解決 call (Resilience4j 350件 + 追加検証プロジェクト 14,248件) が要因クラスへ分類され、件数分布と代表例が記録されている。

## テスト観点

- 分類の妥当性: 代表例が該当 reason / target / 診断 metadata と一致している
- 集計の完全性: プロジェクト別合計が `error.details` の total と一致する

## 検証コマンド

- `cd analyzers/java && ./gradlew shadowJar` (再計測前の jar 更新)
- `timeout 60 bash hooks/spec/validate_document.sh specs/27-unresolved-call-diagnosis/report.md` (レポートが hook 検査対象の場合)
- `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 でブランチと jar を確認した
- [ ] 2 プロジェクトの再計測を完了し、計測条件を記録した
- [ ] 8 分類 (+ 未分類) への機械集計を完了した
- [ ] `report.md` を作成した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った
- [ ] 未解決の仕様質問が残っていない
