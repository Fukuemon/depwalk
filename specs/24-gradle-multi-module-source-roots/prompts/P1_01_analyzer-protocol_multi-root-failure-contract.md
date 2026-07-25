# 複数 source root と構造化 failure detail の Protocol 契約

## 絶対ルール

- spec と上位正本に明記された範囲だけを対象にする。
- 不明点は推測で埋めず、停止してユーザーに確認する。
- 参照 path を外れて広く探索しない。Grep / Glob / 既存実装の探索は禁止する。
- 別 app / package を追加探索せず、この prompt 内の情報だけで判断する。
- 各作業ステップに含まれる検証と diff レビューをスキップしない。
- `schemaVersion` は `1` のまま維持し、旧挙動を残す互換分岐を追加しない。
- Java 固有の Gradle discovery、型解決、Graph 構築、CLI 表示は実装しない。
- **完了条件のタスク化**: 作業開始前に「完了条件」セクションの各項目を todo として登録し、各ステップ完了時に状態を更新すること。タスク化せずに作業を開始することは禁止。

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

Issue #24 の prompt 群は同一 `feature/24` branch と同一 PR で直列または依存表どおりに実行する。

1. 現在の branch が `feature/24` であることを確認する。
2. `git status --short` で意図しない差分がないことを確認する。
3. 既存 Draft PR があれば流用し、なければ repository の標準 base branch 向けに作成する。
4. 本 prompt の完了条件を PR description に追記する。
5. branch、差分、PR 状態を記録してから次へ進む。

### ステップ 1: `analysisRequest.sourceRoots` を追加する

1. TDD で Go Protocol contract testを追加する。
2. `core/internal/protocol.AnalysisRequest`へoptionalな`sourceRoots`を追加する。省略は有効、1件以上の配列も有効、明示された空配列はinvalidとする。
3. 各要素は`workspaceRoot`相対の`/`区切りとする。`.`を許可し、絶対path、空文字、backslash、`..` segmentを拒否する。
4. 順序は入力順を保持する。重複・包含・symlink・存在検査はJava Analyzerのpre-flight責務であり、Protocol validationへ入れない。
5. Java受信DTOの`AnalysisRequest`にも同じoptional fieldを追加し、unknown field許容と既存fieldの意味を維持する。
6. `testdata/analyzer-protocol/records/`のvalid / invalid fixtureとGo / Javaのparse testを更新する。
7. 検証コマンドを実行し、diffレビューでschemaVersionと責務境界を確認する。

### ステップ 2: 共通`FailureDetail`を追加する

1. TDDで、複数detail、順序保持、未知metadata、invalid detailを扱うcontract testを追加する。
2. `error` recordへoptionalな`details`配列を追加する。存在する場合は1件以上とする。
3. 各`FailureDetail`は必須`code` / `message`、任意`sourceLocation` / opaque `metadata`を持つ。
4. `sourceLocation`は既存のworkspace相対path validationを再利用する。
5. `metadata`の未知keyとnested JSON valueを意味解釈せず保持する。配列順は入力順を維持する。
6. Goの`AnalyzerError`とJavaの`ErrorRecord` / mapperを同じwire構造へ更新する。
7. top-level errorの`code` / `message` / `sourceLocation` / `metadata`の既存意味を変更しない。
8. 検証コマンドを実行し、Java固有codeやmetadata keyでProtocolが分岐していないことをdiffレビューする。

### ステップ 3: symbol metadata と fixture のround-tripを固定する

1. `methodSymbol.metadata`が既存どおりoptionalなopaque objectであることをcontract testへ明記する。
2. `declarationOrigin`、`sourceAnchor`、nested `ownerSourceLocation`をfixture値として使用してよいが、Protocol validationはkeyの意味を解釈しない。
3. bytecode-only symbolでは`sourceLocation`省略とmetadata併存をvalid fixtureで固定する。
4. metadataのnested object、array、null、unknown keyがparse / serializeで欠落しないことを検証する。
5. `CallSiteId`とcall-site ledgerは内部実装であり、Protocol fieldやdebug recordとして追加しない。
6. 検証コマンドと`git diff --check`を実行し、diffレビューでwire契約だけに変更が閉じていることを確認する。

### ステップ最終: 最終確認

1. `## 検証コマンド`をすべて実行する。
2. schemaVersion 1、optional field追加、空配列invalid、unknown metadata保持を再確認する。
3. CoreのGraph / CLIとJava AnalyzerのGradle・解析処理へ変更していないことを確認する。
4. durable設計との不整合を発見した場合は実装せず、spec-lifecycleのtrackへ戻す。
5. diffレビューを行い、指摘対応後に本promptを完了する。

## 実装コンテキスト

- spec: `specs/24-gradle-multi-module-source-roots/index.md`
- review: `specs/24-gradle-multi-module-source-roots/review.md`
- Protocol正本: `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md`
- 関連ADR: `adr/0001-analyzer-protocol-jsonl-spi.md`
- repo root: `$(ghq root)/github.com/Fukuemon/depwalk`
- 参照・変更するpath:
  - `core/internal/protocol/types.go`
  - `core/internal/protocol/validate.go`
  - `core/internal/protocol/parser.go`
  - `core/internal/protocol/*_test.go`
  - `testdata/analyzer-protocol/records/`
  - `testdata/analyzer-protocol/scenarios/`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/protocol/AnalysisRequest.java`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/protocol/ErrorRecord.java`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/protocol/MethodSymbol.java`
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/io/ProtocolObjectMapper.java`
  - `analyzers/java/src/test/java/com/fukuemon/depwalk/javaanalyzer/io/`
- 参照のみのpath:
  - `design/features/graph/DesignDoc_graph.md`
  - `design/features/java-analyzer/DesignDoc_java-analyzer.md`

## 前提条件

- 完了しているべきphase / 依存prompt: なし。
- 完了後に着手可能になる後続prompt:
  - `P2_01_core_request-staging-failure.md`
  - `P2_02_java-analyzer_gradle-model-provider.md`
- 必要なrepo状態: 直前のsync phase commitが取り込まれ、GoとJavaの既存testが実行可能であること。

## 不明点ハンドリング

- 矛盾、欠落、未定義を見つけたら作業を止める。
- 推測でschema、validation、互換挙動を追加しない。
- JacksonまたはGo JSONの実在APIは対象pathと公式APIで確認してよいが、別packageの設計探索はしない。
- 質問時は、止まっている作業単位、判断が必要な論点、選択肢、Protocol互換性への影響を整理する。

## タスク境界

### 実装する範囲

- optional `analysisRequest.sourceRoots`のGo / Java DTO、validation、fixture。
- optional `error.details`と共通`FailureDetail`のGo / Java DTO、validation、fixture。
- `methodSymbol.metadata`のopaque round-tripとbytecode-only symbol fixture。
- schemaVersion 1のcontract test。

### 実装しない範囲

- `--source-root` CLI flagとrequest組み立て。
- staging Graph、Graph Symbol metadata、failure CLI renderer。
- Gradle Tooling API、model provider、root discovery。
- Javaのsource context、parse、call解決、bytecode member、completeness gate。
- Traversal / Output schemaの変更。

## 設計仕様

- `workspaceRoot`は必須で、request全体のpath座標系である。
- `sourceRoots`はoptionalな明示overrideである。省略時はAnalyzer discovery、1件以上指定時は明示値だけを使う。空配列はinvalidである。
- source rootはworkspace相対、`/`区切りで、`.`を許可する。絶対path、空文字、backslash、`..` segmentはinvalidである。
- Protocol validationはpath表現だけを検証する。存在、real path、重複、包含、workspace escapeはJava Analyzer pre-flightが検証する。
- `error.details`はoptionalで、存在時は1件以上である。各detailは必須`code` / `message`、任意`sourceLocation` / opaque `metadata`を持つ。
- Core consumerはdetailの共通fieldだけを検証し、Analyzer固有codeとmetadata keyを解釈しない。
- `methodSymbol.metadata`はoptionalなopaque JSON objectである。bytecode-only memberは`sourceLocation`を省略し、owner anchorをmetadataへ保持できる。
- optional field追加としてschemaVersion 1を維持する。公開済みv1 consumerは存在しないため、現行内部fixtureを確定schemaへ移行し旧挙動分岐を残さない。

## テスト観点

- `sourceRoots`省略、1件、複数件を受理し、順序を保持する。
- 明示空配列、絶対path、空文字、backslash、`..` segmentを拒否し、`.`を受理する。
- `FailureDetail`の必須field欠落、空details、invalid source locationを拒否する。
- unknown detail code / metadata、nested object / array / nullを順序と構造を保ってround-tripする。
- bytecode-only `methodSymbol`は`sourceLocation`なし、owner metadataありでvalidになる。
- `CallSiteId`やJava固有ledgerをwire schemaへ追加していない。
- 既存recordとscenario fixtureが退行しない。

## 検証コマンド

- `cd core && go test ./internal/protocol/...`
- `cd core && go test ./...`
- `cd core && go vet ./...`
- `cd core && test -z "$(gofmt -l .)"`
- `cd analyzers/java && ./gradlew test`
- `git diff --check`

## 完了条件

- [ ] ステップ0で`feature/24`、差分、Draft PR状態を確認した。
- [ ] `sourceRoots`の省略・1件・複数件・空配列invalid契約をGo / Java DTOとfixtureへ実装した。
- [ ] workspace相対path validationが`.`を許可し、絶対path・空文字・backslash・`..`を拒否する。
- [ ] optional `error.details`と共通`FailureDetail`をGo / Java DTOへ実装した。
- [ ] detailの共通fieldとopaque metadataをvalidation / round-trip testで固定した。
- [ ] bytecode-only symbolの`sourceLocation`省略とowner metadata fixtureを追加した。
- [ ] schemaVersion 1を維持し、旧挙動の互換分岐を追加していない。
- [ ] Java固有code / metadata keyをProtocol validationで解釈していない。
- [ ] Core Graph / CLI、Gradle discovery、Java解析処理、Traversal / Outputを変更していない。
- [ ] 全作業ステップを順序どおり実行した。
- [ ] 各ステップでdiffレビューを実施し、指摘を対応した。
- [ ] `## 検証コマンド`がすべてパスした。
- [ ] 未解決の仕様質問が残っていない。
