# P2_01: 未解決 call パターンの最小再現 fixture を追加する

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

1. 作業ブランチ `feature-27` が checkout 済みで、P1_01 の変更が含まれることを確認する
2. Draft PR の description に本 prompt の完了条件を追記する

### ステップ 1: fixture ソースの追加

1. `testdata/fixtures/java/multi-module-spring-project/` の既存 module 構成の中に、次の 5 パターンの最小再現ソースを追加する (1 パターン 1 クラスを目安に、一般名のドメイン非依存コードで書く):
   - ①lambda / generic を含む fluent chain の途中で receiver 型が失われるケース (builder 風 API + lambda 引数 + chain 後続 call)
   - ⑦`final var x = genericMap.get(key)` 形式の `var` + generic メソッド戻り値からの後続 call
   - ④他クラスの method reference (`Type::method`) が解決に失敗し得る形 (generic 文脈)
   - ⑤明示 `super(...)` 呼び出しを持つ subclass constructor
   - ⑧module A の Lombok 相当の生成 member (fixture では明示コンストラクタを持たない Lombok 注釈クラス) を module B から呼ぶ cross-module ケース
2. fixture の build (`writeDepwalkClasspath` 等の既存 task) が成功することを確認する
3. diff レビューを回し、指摘を対応する

### ステップ 2: E2E 期待値の更新と診断 metadata 検証

1. E2E の期待 graph / diagnostic 集合へ追加ケース分の期待値を足す。この時点で救済未修正のため、④⑤⑧等が `JAVA_INCOMPLETE_ANALYSIS` へ入るならその状態を「現状の期待値」として明示し、P4 系 prompt が修正後に期待値を成功側へ更新できるようテストへ注記を残す
2. `JAVA_INCOMPLETE_ANALYSIS` の details に P1_01 の診断 4 項目が入ることを E2E で検証する
3. `## 検証コマンド` を実行する
4. diff レビューを回し、指摘を対応する

### ステップ最終: 最終確認

1. 全テスト / E2E がパスすることを確認
2. spec の `## 上位資料からの変更点` に必要な追記がないかを phase: track に渡す
3. WIP commit を残す

## 実装コンテキスト

- spec: `specs/27-unresolved-call-diagnosis/index.md`
- 正本 (fixture 方針): `design/features/java-analyzer/DesignDoc_java-analyzer.md` の `テスト観点` (「未解決 call パターン fixture (#27)」の項)
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照する path:
  - `testdata/fixtures/java/multi-module-spring-project/`
  - `core/e2e/` (期待値定義)

## 前提条件

- 完了しているべき phase / 依存 prompt: `P1_01_java-analyzer_diagnostic-metadata.md`
- 完了後に着手可能になる後続 prompt: `P4_01` / `P4_02` / `P4_03` / `P4_04` (修正の回帰検証に本 fixture を使う)
- 必要な repo 状態: JDK 25、fixture build が可能な環境

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 追加ケースが既存 fixture の期待 graph を大きく壊す場合は、影響一覧を提示して停止する

## タスク境界

### 実装する範囲

- 上記 5 パターンの最小再現 fixture ソース
- E2E 期待値の更新 (現状の未解決状態を期待値として明示)
- 診断 metadata 4 項目の E2E 期待値検証

### 実装しない範囲

- 救済 fallback の修正 (→ P4 系。本 prompt では「未解決のまま」が正)
- 実プロジェクトでの再計測 (→ P2_02)
- fixture 以外の testdata 新設 (新規 fixture project を作らない)

## 設計仕様

feature doc `テスト観点` より (正本):

> `testdata/fixtures/java/multi-module-spring-project/` へ、実環境実測の上位未解決パターン (lambda / generic を含む fluent chain、`var` + generic メソッド戻り値、method reference、explicit `super(...)`、cross-module の Lombok 生成 member 呼び出し) の最小再現ケースを追加する。救済修正後の回帰検証と、`JAVA_INCOMPLETE_ANALYSIS` 時の診断 metadata 4 項目 (sanitize 制約含む) の期待値検証に使う。

spec 解決済み論点 D4 (抜粋):

> 実測対象コードへ依存しない一般化した形で表現し、既存 E2E 基盤を再利用して修正後の回帰検証にもそのまま使う。既存 fixture の期待 graph / diagnostic 集合への影響は追加ケース分の期待値更新で吸収する。

## テスト観点

- 5 パターンそれぞれが意図した reason / callKind で観測される (分類の再現性)
- 診断 metadata 4 項目が details に入り、sanitize 制約が守られている
- 既存 fixture の期待 graph / call-site outcome 集計が追加分以外で変化しない

## 検証コマンド

- `cd analyzers/java && ./gradlew test && ./gradlew shadowJar`
- `./analyzers/java/gradlew --no-daemon -p testdata/fixtures/java/multi-module-spring-project clean writeDepwalkClasspath` (fixture build task 名は fixture 既存定義に従う)
- `(cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -run TestGradleMultiProjectCLI -count=1)`

## 完了条件

- [ ] ステップ 0 でブランチと Draft PR description を確認した
- [ ] 5 パターンの最小再現ソースを追加した
- [ ] E2E 期待値を更新し、修正前の未解決状態を明示した
- [ ] 診断 metadata 4 項目の期待値検証を追加した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った
- [ ] 未解決の仕様質問が残っていない
