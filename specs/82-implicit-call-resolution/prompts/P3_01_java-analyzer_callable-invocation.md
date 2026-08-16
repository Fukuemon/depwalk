---
phase: 3
seq: 1
target: java-analyzer
issue: 82
depends_on: []
---

# callable 値渡しの invocation edge の実装

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
2. `feature/82` を最新化して使う (無ければ `develop` から作成)
3. Draft PR が無ければ作成して push する

### ステップ 1: 同一メソッド内の callable 突合

1. unit テストを先に書く: local 変数に代入した lambda / method reference が同一メソッド内の invocation (`run()` / `apply()` 等) と突合され edge になること
2. `CallGraphBuilder` の AST walk で、(a) lambda / method reference の代入・受け渡し、(b) functional interface 型の receiver への invocation を収集し、同一メソッド内の 1 段写像で突合して edge を生成する
3. `## 検証コマンド` の analyzer-test を実行する
4. diff レビューを回し、指摘を対応してから次へ

### ステップ 2: 引数渡し 1 段の突合と診断

1. unit テストを先に書く (テスト観点を網羅)
2. workspace メソッドの functional interface parameter への invocation site と、call site から渡された callable の 1 段写像を突合表として構築し、edge を生成する。複数 call site から異なる callable が渡る場合は各 edge を call site 根拠付きで全列挙する
3. 追跡範囲外 (field 経由・多段) は `JAVA_CALLABLE_UNRESOLVED` (info) を `JavaDiagnosticCode` へ追加して診断化する。advisory であり完全性 gate の primary outcome に加えない
4. patterns fixture に最小再現 (同一メソッド内 / 引数渡し 1 段 / 複数 call site / method reference / field 経由 = 範囲外) を追加し、required E2E の成功期待を追加する
5. `## 検証コマンド` をすべて実行する
6. diff レビューを回し、指摘を対応してから次へ

### ステップ最終: 最終確認

1. 全テスト / lint がパスすることを確認
2. spec の `## 上位資料からの変更点` に追記が必要な差分が出ていないか確認し、あれば記録する
3. commit する (規約: `workflow-git` の commit-format、AI attribution 禁止)

## 実装コンテキスト

- spec: `specs/82-implicit-call-resolution/index.md` (解決済みの論点 D1 / D5、実装分割 P3)
- 設計正本: `design/features/java-analyzer/analysis.md` の「callable invocation」/ `design/features/java-analyzer/protocol-mapping.md` の「暗黙呼び出しの標識」
- 参照する path:
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/graph/CallGraphBuilder.java` (lambda / method reference の既存処理: `processMethodReference` / `WalkContext` / `CallEdgeMetadata`)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/graph/CallEdgeMetadata.java` (標識 metadata の追加先)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/pipeline/AnalysisRunner.java` (突合表の配線)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/JavaDiagnosticCode.java`
  - `analyzers/java/src/test/java/com/fukuemon/depwalk/javaanalyzer/analysis/MethodReferenceTest.java` (既存テスト様式)
  - `testdata/fixtures/java/multi-module-spring-project/` / `core/e2e/`

## 前提条件

- 完了しているべき phase / 依存 prompt: なし (P1 と並列可。変更ファイルが `CallGraphBuilder` で重なるため、同時進行時は先行分を rebase してから着手する)
- 完了後に着手可能になる後続 prompt: なし
- 必要な repo 状態: `feature/82` が analyzer-build を通る

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- 同一メソッド内 + 引数渡し 1 段の callable 突合と invocation edge 生成
- `viaCallableInvocation` 標識と `JAVA_CALLABLE_UNRESOLVED` 診断
- fixture / unit / required E2E

### 実装しない範囲

- field 経由・多段の受け渡し・Bean 境界越えの追跡 (診断化のみ)
- invocation site が外部ライブラリ内にあるケース (`stream.map(...)` 等。原理的に対象外)
- lambda の独立 node 化・`symbolKind` enum の変更 (既存決定を維持)
- entry point 分類 (P1) / イベント edge (P2) / 型伝播救済層 (P5)

## 設計仕様

- edge の張り先: method reference → 参照先メソッド (既存「帰属型の決定規則」を適用)、lambda → 定義側の囲みメソッド。意味は「invoker はそのメソッド内で定義されたコードを実行する」
- caller = invocation site の囲みメソッド
- 標識: `callEdge.metadata.viaCallableInvocation: true` (既存 `viaLambda` / `viaMethodReference` とは独立の flag)
- 追跡範囲: (1) 同一メソッド内の local 変数経由 (2) workspace メソッドの functional interface parameter への引数渡し 1 段。複数 call site は各 edge を全列挙
- 解決不能 (範囲外) は `JAVA_CALLABLE_UNRESOLVED` (info。設計上の制約による対象外を表す) で診断化する
- `silentOmission == 0` と outcome ledger の終端保証を変更しない

## テスト観点

- WHEN 同一メソッド内で lambda を local に代入して invoke したとき、invocation を持つメソッド → lambda の囲みメソッドへ `viaCallableInvocation` 付き edge が生成される
- WHEN `retry(() -> doWork())` 形の引数渡し 1 段で、`retry` 内の parameter invocation と突合され edge が生成される
- WHEN method reference が渡されたとき、edge の callee が参照先メソッドになる
- 複数 call site から異なる callable が渡ると、各 edge が call site 根拠付きで全列挙される
- IF field 経由で渡された callable の invocation の場合、edge は生成されず `JAVA_CALLABLE_UNRESOLVED` (info) が記録される
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
