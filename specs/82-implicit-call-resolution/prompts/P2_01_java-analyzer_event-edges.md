---
phase: 2
seq: 1
target: java-analyzer
issue: 82
depends_on: [P1_01_java-analyzer_entry-point-classification.md]
---

# イベント publish → listener edge の実装

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止)
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する
- 各作業ステップに含まれる検証 / レビュー手順をスキップしない
- **完了条件のタスク化**: 作業開始前に「完了条件」セクションの各項目を todo として登録し、各ステップ完了時に状態を更新すること。タスク化せずに作業を開始することは禁止

### 実装アンチパターンの回避 (必守)

- スコープ厳守: spec / 本 prompt に明記された機能のみ実装する。未要求の機能追加・先回りの抽象化・無関係なリファクタ・暗黙の互換維持をしない。
- 既存規約への整合: 命名・エラー処理・ログ・テスト・API 連携方式は、対象コードベースの既存パターンに合わせる。新方式を持ち込む場合は理由を述べて確認を取る。
- 観測可能な契約の保持: UI 文言・イベント名・戻り値・エラーメッセージ・ログ形式・API を要求なく変更しない。変更が必要なら理由と影響を明記する。
- 推測の排除: 要件・業務ルール・API 仕様が不明なら停止して確認する。それらしいが誤った実装 (存在しない API 呼び出し / 非互換な引数) を避け、import と API の実在を確認する。
- fallback の最小化: `??` / `||` / 既定引数 / 多段 fallback / 暗黙のエラー握り潰しは「任意データ」に限定する。必須データの欠落は隠さず明示的に失敗させる。
- 過剰実装の排除: 単純な条件分岐を strategy / handler map に置換しない。要求も計測もない caching / memoization を入れない。
- dead code を残さない: 到達不能コード・未使用の変数 / 関数 / import / export・変更後に不要化した型定義を削除する。
- 判断の記録: 非自明な設計判断は理由 (or spec / ADR へのリンク) を残す。コード内コメントは英語で書き、spec / issue を引用せず ADR 等へのリンクのみ許可。

## 作業ステップ (この順序で実行する)

### ステップ 0: ブランチ準備と着手記録

ブランチ命名と base branch は `context/project.yml` の `naming.branch` (`feature/<issue-id>`) / `naming.base_branch` (`develop`) に従う。

1. issue #82 の `status:*` が `status:implementing` でなければ付け替え、状態遷移コメントを残す (既に implementing なら何もしない)
2. `feature/82` を最新化して使う (P1 が merge / 積層済みであること)
3. Draft PR が無ければ作成して push する

### ステップ 1: イベント index (listener 側) の構築

1. unit テストを先に書く: `@EventListener` / `@TransactionalEventListener` メソッドが引数型 (raw type) で引けること、型階層 (supertype のイベント型を受ける listener) の合致
2. P1 のアノテーション検出を利用し、listener メソッドをイベント型で引ける index を 1st pass に追加する (SpringDiIndex と同じ構築様式)
3. `## 検証コマンド` の analyzer-test を実行する
4. diff レビューを回し、指摘を対応してから次へ

### ステップ 2: publishEvent call site の突合と edge 生成

1. unit テストを先に書く (テスト観点を網羅)
2. `CallGraphBuilder` の method call 処理で `ApplicationEventPublisher#publishEvent()` の call site を検出し、引数の静的型 + 型階層で listener index と突合して candidate edge を生成する。既存の `emitDispatchCandidateEdges` からは candidate edge の生成様式 (`edgeId` 重複統合・metadata の書式) のみを踏襲する。同関数は Spring DI の確度規則 (複数候補 = ambiguous) を内包するため直接再利用せず、resolution / provenance は D7 の broadcast 専用ロジックを新設する
3. 解決不能時は `JAVA_EVENT_UNRESOLVED` (warning) を `JavaDiagnosticCode` へ追加して診断化する。診断は advisory であり完全性 gate の primary outcome に加えない
4. patterns fixture に最小再現 (単一 listener / 複数 listener / 条件付き listener / 型階層合致 / 引数型未解決) を追加し、required E2E の成功期待を追加する
5. `## 検証コマンド` をすべて実行する
6. diff レビューを回し、指摘を対応してから次へ

### ステップ最終: 最終確認

1. 全テスト / lint がパスすることを確認
2. spec の `## 上位資料からの変更点` に追記が必要な差分が出ていないか確認し、あれば記録する
3. commit する (規約: `workflow-git` の commit-format、AI attribution 禁止)

## 実装コンテキスト

- spec: `specs/82-implicit-call-resolution/index.md` (解決済みの論点 D7、実装分割 P2)
- 設計正本: `design/features/java-analyzer/analysis.md` の「イベント edge」/ `design/features/java-analyzer/protocol-mapping.md` の「暗黙呼び出しの標識」「diagnostic / error code 体系」
- 参照する path:
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/graph/CallGraphBuilder.java` (publishEvent 検出と edge 生成の組み込み先)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/spring/SpringDiIndex.java` (index 様式) / `SpringDiagnosticEmitter.java` (診断 emit 様式)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/pipeline/AnalysisRunner.java` (index 配線)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/JavaDiagnosticCode.java` (code 追加先)
  - `testdata/fixtures/java/multi-module-spring-project/` / `core/e2e/`

## 前提条件

- 完了しているべき phase / 依存 prompt: `P1_01_java-analyzer_entry-point-classification.md` (アノテーション検出基盤)
- 完了後に着手可能になる後続 prompt: なし
- 必要な repo 状態: P1 の変更が `feature/82` に取り込み済み

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- イベント index (listener 側) の構築
- `publishEvent()` call site の検出・突合・candidate edge 生成
- `JAVA_EVENT_UNRESOLVED` 診断の追加、fixture / unit / required E2E

### 実装しない範囲

- entry point 分類 (P1)、callable 追跡 (P3)、Console 表示 (P4)、型伝播救済層 (P5)
- generics イベント型の厳密突合 (raw type 一致で近似。制約として維持)
- 条件アノテーションの条件評価 (既存方針: 記録のみ)

## 設計仕様

- 対象 call site: receiver の静的型が `org.springframework.context.ApplicationEventPublisher` またはその subtype (`ApplicationContext` 等) である `publishEvent` 呼び出し
- caller = call site の囲みメソッド、callee = 引数の静的型とその型階層に合致する listener メソッド
- broadcast 意味論: 無条件 listener への edge は複数でも各々確定 (`resolution: unique`)。条件付き listener のみ既存規則 (`conditional: true` + `conditionTypes`) で `ambiguous`
- `callEdge.metadata.provenance` に `spring-event` を積む (既存 `sootup` / `spring-di` と同列。重複統合時は和集合)
- イベント型の突合は raw type 一致で近似する
- 解決不能は `JAVA_EVENT_UNRESOLVED` (warning) で診断化し、silent omission にしない。完全性 gate の primary outcome には加えない (advisory)

## テスト観点

- WHEN `publishEvent()` の引数型が解決でき対応 listener が存在するとき、publish 地点から listener への edge が生成される (unit + E2E)
- WHEN 複数 listener が合致するとき、全 listener へ各々 `resolution: unique` の edge が生成される (曖昧と偽らない)
- 条件付き listener は `conditional: true` + `ambiguous` になる
- IF 引数型が解決できない場合、`JAVA_EVENT_UNRESOLVED` が記録され request は fatal にならない
- `silentOmission == 0` の非回帰

## 検証コマンド

- `cd analyzers/java && ./gradlew shadowJar` / `cd analyzers/java && ./gradlew test`
- `cd core && go build ./...` / `cd core && go vet ./...` / `cd core && go test ./...`
- `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 でブランチと Draft PR を準備した (status 遷移済みを確認)
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] fixture / unit / required E2E が「テスト観点」を網羅している
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (無ければ「なし」を確認した)
- [ ] 未解決の仕様質問が残っていない
