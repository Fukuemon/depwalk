---
phase: 1
seq: 1
target: java-analyzer
issue: 82
depends_on: []
---

# entry point アノテーション検出基盤と分類標識の実装

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

1. issue #82 の `status:*` が `status:implementing` でなければ付け替え、状態遷移コメントを残す (`workflow-git` の `references/issue-status.md`。既に implementing なら何もしない)
2. 最新の `develop` を取得する
3. 作業ブランチ `feature/82` が無ければ作成、あればそのまま使う
4. Draft PR が無ければ作成して push する (完了条件を description に転記)

### ステップ 1: アノテーション検出基盤 (既存 SpringAnnotations の拡張)

1. `SpringAnnotations` の既存テスト様式に合わせ、entry point 集合 (設計仕様の FQN 一覧) の検出テストを先に書く (直接付与 / 1 段 meta-annotation / 2 段は検出されないこと / javax・jakarta 両版)
2. `SpringAnnotations` に entry point アノテーション集合の判定を追加する (既存の FQN 文字列判定方式を踏襲)。利用者定義合成アノテーションの 1 段検出は、対象 annotation 宣言の annotation を 1 段だけ照合する
3. `## 検証コマンド` の analyzer-test を実行する
4. diff レビューを回し、指摘を対応してから次へ

### ステップ 2: entry point 分類の index と metadata 付与

1. unit テストを先に書く: 対象メソッドの `methodSymbol.metadata.entryPoint` に検出アノテーション FQN が重複なし・辞書順で載ること、edge が増えないこと、非対象メソッドに key が無いこと
2. 1st pass (AnalysisRunner の index 構築) で workspace メソッドの entry point 付与を収集する index を追加し (SpringDiIndex と同じ構築様式)、`MethodSymbolFactory` 経由の methodSymbol 出力時に `entryPoint` metadata を付与する
3. patterns fixture (`testdata/fixtures/java/multi-module-spring-project/patterns/`) に entry point の最小再現 (ライフサイクル / Web / 合成 1 段) を追加し、required E2E (`core/e2e/`) の既存様式で「CLI 経由で JSON の `nodes[].metadata.entryPoint` に現れる」成功期待を追加する
4. `## 検証コマンド` をすべて実行する
5. diff レビューを回し、指摘を対応してから次へ

### ステップ最終: 最終確認

1. 全テスト / lint がパスすることを確認
2. spec の `## 上位資料からの変更点` に追記が必要な差分が出ていないか確認し、あれば記録する
3. commit する (規約: `workflow-git` の commit-format、AI attribution 禁止)

## 実装コンテキスト

- spec: `specs/82-implicit-call-resolution/index.md` (解決済みの論点 D2 / D3、実装分割 P1)
- 設計正本: `design/features/java-analyzer/analysis.md` の「framework 由来の暗黙呼び出しの解決 → entry point 分類」/ `design/features/java-analyzer/protocol-mapping.md` の「暗黙呼び出しの標識」
- 参照する path:
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/spring/SpringAnnotations.java`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/spring/SpringDiIndex.java` (index 構築様式の参照)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/pipeline/AnalysisRunner.java` (index 配線)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/graph/MethodSymbolFactory.java`
  - `analyzers/java/src/test/java/com/fukuemon/depwalk/javaanalyzer/analysis/spring/SpringAnnotationsTest.java`
  - `testdata/fixtures/java/multi-module-spring-project/`
  - `core/e2e/`

## 前提条件

- 完了しているべき phase / 依存 prompt: なし
- 完了後に着手可能になる後続 prompt: `P2_01_java-analyzer_event-edges.md` / `P4_01_output_entry-point-console.md`
- 必要な repo 状態: `develop` が clean で analyzer-build が通る

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- entry point アノテーション集合の検出 (直接付与 + 利用者定義合成 1 段)
- entry point index の構築と `methodSymbol.metadata.entryPoint` の付与
- fixture / unit / required E2E の追加

### 実装しない範囲

- イベント edge の生成 (P2)、callable 追跡 (P3)、Console 表示 (P4)、型伝播救済層 (P5)
- 新規機能の作成・無関係な既存挙動の変更・既存 fixture の再作成
- Protocol schema / `symbolKind` enum の変更 (opaque metadata のみ)

## 設計仕様

- 対象集合 (既知集合として明示列挙):
  - ライフサイクル: `org.springframework.scheduling.annotation.Scheduled` / `PostConstruct` / `PreDestroy` (後者 2 つは `javax.annotation.*` と `jakarta.annotation.*` の両 FQN)
  - イベント listener (分類のみ。edge は P2): `org.springframework.context.event.EventListener` / `org.springframework.transaction.event.TransactionalEventListener`
  - Web (すべて `org.springframework.web.bind.annotation` 配下の FQN): `RequestMapping` + composed (`GetMapping` / `PostMapping` / `PutMapping` / `DeleteMapping` / `PatchMapping`) / `ExceptionHandler` / `ModelAttribute`
- meta-annotation は 1 段のみ検出する。2 段以上は検出不能であり診断も出さない (制約として実装コメントで明示)
- `methodSymbol.metadata.entryPoint`: 検出アノテーション FQN の string 配列。重複なし・辞書順
- edge は作らない。擬似 caller node を合成しない (終端根拠のみ)
- entry point の意味は「framework が直接起動し得るメソッド」であり caller edge の有無とは独立 (listener 系はイベント edge で caller を持ち得るが標識する)
- `silentOmission == 0` と outcome ledger の終端保証を変更しない

## テスト観点

- WHEN 対象アノテーション付きメソッドを解析したとき、`methodSymbol.metadata.entryPoint` に検出 FQN が載る (unit + E2E)
- 1 段合成アノテーションで検出され、2 段では検出されない
- javax / jakarta 両版で検出される
- 非対象メソッドに `entryPoint` key が現れない。edge 数が分類の有無で変化しない
- 既存テストが全て通る (非回帰)

## 検証コマンド

- `cd analyzers/java && ./gradlew shadowJar` (analyzer-build)
- `cd analyzers/java && ./gradlew test` (analyzer-test。ArchUnit 含む)
- `cd core && go build ./...` / `cd core && go vet ./...` / `cd core && go test ./...` (E2E 含む)
- `lefthook run pre-commit` (check)

## 完了条件

- [ ] ステップ 0 でブランチと Draft PR を準備し、issue を `status:implementing` へ遷移した
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] fixture / unit / required E2E が「テスト観点」を網羅している
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (無ければ「なし」を確認した)
- [ ] 未解決の仕様質問が残っていない
