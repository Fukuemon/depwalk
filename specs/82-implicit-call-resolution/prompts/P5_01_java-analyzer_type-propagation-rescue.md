---
phase: 5
seq: 1
target: java-analyzer
issue: 82
depends_on: []
---

# 型伝播救済層と cross-module DI 候補解決の実装

## 絶対ルール

- spec に明記された範囲だけを対象にする
- 不明点は推測で埋めず、停止してユーザーに確認する
- 参照 path を外れて広く探索しない (Grep / Glob / 既存実装の探索は禁止。本 prompt のステップ 1 の「調査」は列挙済み path と実測データの読解に限る)
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

### ステップ 1: cross-module DI 候補解決の調査 (D12)

1. 再現 fixture を先に書く: 別 module (multi-module fixture の別 project) に impl クラスを持つ interface を field injection する unit test / fixture。「Bean 候補なし」になる現状を失敗期待で固定する
2. `SpringDiIndex` の bean candidate 収集と解析 context の関係を、列挙済み path の読解で調査し、context 境界を跨いだ候補が引けない原因を特定する
3. 原因と修正方針を 1 段落で記録して停止し、ユーザーへ報告して修正方針の確認を取る (修正が context 構造の変更に及ぶ場合は独断で進めない)
4. 確認後、修正を実装し fixture を成功期待へ反転する。impl が workspace のどこにも無い interface の「Bean 候補なし」診断は変更しない
5. `## 検証コマンド` の analyzer-test を実行し、diff レビューを回す

### ステップ 2: 型伝播救済層 — local 変数と chain link (D9 ①②)

1. unit テストを先に書く: (a) `var` / 明示型 local の宣言・初期化子からの receiver 型導出、(b) chain 途中の bytecode generic Signature からの型引数伝播 (`.stream().map(...).toList()` 後の要素への呼び出しが救済されること)
2. 既存の「chain の前進解決」(`BytecodeRescue` 周辺) を拡張し、solver 失敗時に上記 2 手段で receiver 型を導出して既存 bytecode 救済へ接続する
3. `## 検証コマンド` の analyzer-test を実行し、diff レビューを回す

### ステップ 3: 型伝播救済層 — lambda parameter と SAM arity (D9 ③)

1. unit テストを先に書く: (a) lambda parameter の型を functional interface の型引数 (bytecode generic Signature) から導出して救済、(b) `Type::method` 形の method reference が SAM arity の bytecode 導出 (`Function#apply` = arity 1 等) で救済されること
2. 実装する。「宣言上の名前一意を根拠にする救済はしない」保守側の原則を変更しない (arity が導出できても候補が一意でなければ diagnostic に残す)
3. patterns fixture に代表形状 (stream chain / var generic / lambda param / Lombok 生成 getter への method reference) を追加し、required E2E の成功期待を追加する
4. `## 検証コマンド` をすべて実行し、diff レビューを回す

### ステップ最終: 実測評価と最終確認

1. 全テスト / lint がパスすることを確認
2. 実環境検証プロジェクトで再計測し (計測手順は `context/toolchain.md`「実環境解析の運用指針」)、未解決率が基準値 3.9% から改善していることを確認して記録する (識別名は書かない)
3. D12 の調査結果を spec の `## 上位資料からの変更点` (feature doc への影響の D12 行) に反映し、design 側 (analysis.md の Spring DI 節) へ規則を追記する
4. commit する (規約: `workflow-git` の commit-format、AI attribution 禁止)

## 実装コンテキスト

- spec: `specs/82-implicit-call-resolution/index.md` (解決済みの論点 D9 / D12、実装分割 P5、計測指標)
- 設計正本: `design/features/java-analyzer/analysis.md` の「型伝播救済層」「呼び出し元の型が分からないとき」/ ADR-0012
- 参照する path:
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/graph/BytecodeRescue.java` (chain 前進解決・救済の既存実装)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/graph/CallGraphBuilder.java` (`inferFunctionalInterfaceArity` / 救済経路)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/completeness/` (bytecode member index / outcome ledger)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/spring/SpringDiIndex.java` (D12 調査対象)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/pipeline/AnalysisRunner.java` (context と index の配線。D12 調査対象)
  - `testdata/fixtures/java/multi-module-spring-project/` / `core/e2e/`

## 前提条件

- 完了しているべき phase / 依存 prompt: なし (P1〜P4 と並列可。変更ファイルが `CallGraphBuilder` で P3 と重なるため、同時進行時は先行分を rebase してから着手する)
- 完了後に着手可能になる後続 prompt: なし
- 必要な repo 状態: `feature/82` が analyzer-build を通る

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない。特に D12 の修正方針はステップ 1-3 でユーザー確認を必須とする
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- 型伝播救済層 (local 宣言・初期化子 / chain generic Signature / lambda parameter 型引数 / SAM arity の bytecode 導出)
- cross-module DI 候補解決の調査と (方針確認後の) 修正
- fixture / unit / required E2E、実環境での再計測

### 実装しない範囲

- 名前ベース救済の拡大 (型なしで bytecode member を引く。ADR-0012 で却下済み)
- JavaParser solver 本体への patch
- impl が workspace に存在しない interface の診断変更 (正しい診断として維持)
- entry point / イベント / callable / Console (P1〜P4)

## 設計仕様

- 型伝播救済層: solver 失敗時に receiver 式の型を段階導出して既存 bytecode 救済へ接続する。導出手段は (1) local 変数の宣言・初期化子 (2) chain link の bytecode generic Signature (型引数の伝播) (3) lambda parameter の functional interface 型引数。いずれも classfile / 確定 AST を根拠とし、推測による型付けは行わない
- SAM arity は functional interface の bytecode から導出する。arity が導出できても候補が一意でなければ救済しない (保守側の原則維持)
- 解決不能は既存どおり `JAVA_UNRESOLVED_SYMBOL` の diagnostic に残す (新 code は増やさない)
- D12: 解析 context 境界を跨いだ bean candidate 解決を修正する。impl 不在の「Bean 候補なし」は正しい診断として維持する
- `silentOmission == 0` / outcome ledger 終端保証 / ArchUnit の隔離境界 (SootUp / JavaParser) を維持する

## テスト観点

- WHEN stream chain (`.stream().map(X::getY).toList()`) の要素へ呼び出したとき、generic Signature の伝播で edge になる
- WHEN `var` local の generic 戻り値経由で呼び出したとき、宣言・初期化子の型導出で edge になる
- WHEN lambda parameter (in-scope functional interface) のメソッドを呼んだとき、型引数導出で edge になる
- WHEN Lombok 生成 getter への `Type::getter` method reference のとき、SAM arity の bytecode 導出で救済される
- IF どの導出手段でも型が決まらない場合、従来どおり diagnostic に残る (誤 edge を作らない)
- 別 module の impl を持つ interface の field injection が candidate edge になる (D12)。impl 不在は「Bean 候補なし」のまま
- `silentOmission == 0` / 既存救済パターン (patterns fixture) の非回帰
- 再現条件 → 修正後の期待: 実環境で支配的だった未解決形状 (受け手 NameExpr / MethodCallExpr の UnsolvedSymbolException) が再計測で減少する

## 検証コマンド

- `cd analyzers/java && ./gradlew shadowJar` / `cd analyzers/java && ./gradlew test`
- `cd core && go build ./...` / `cd core && go vet ./...` / `cd core && go test ./...`
- `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 でブランチと Draft PR を準備した (status 遷移済みを確認)
- [ ] D12 の原因を特定し、修正方針のユーザー確認を得てから実装した
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] fixture / unit / required E2E が「テスト観点」を網羅している
- [ ] 実環境の再計測で未解決率の改善を確認し記録した (識別名なし)
- [ ] D12 の調査結果を spec / design へ反映した
- [ ] 未解決の仕様質問が残っていない
