---
phase: 6
seq: 1
target: java-analyzer
issue: 82
depends_on: []
---

# Gradle daemon JVM の明示指定 (gradleJavaHome) の実装

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

### ステップ 1: metadata 解釈と daemon JVM 指定

1. unit テストを先に書く: `metadata.gradleJavaHome` (要素 1) の解釈、複数要素 / 空値 / 存在しない path の扱い (validation)、指定時に Tooling API connector へ Java home が渡ること
2. request metadata の解釈を追加し (`allowIncompleteAnalysis` 等の既存 metadata 解釈の様式を踏襲)、`GradleToolingClient` の接続で daemon JVM として使用する (Tooling API の Java home 指定)。未指定時の挙動は従来どおり変更しない
3. 存在しない path・要素数違反は既存の metadata validation 規則に合わせて `JAVA_INVALID_REQUEST` として拒否する
4. cross-version matrix test (`gradleCompatibilityTest`) の既存様式に、`gradleJavaHome` 指定で非互換 daemon を回避できる検証を追加する (追加が matrix 全体の再実行を強いる場合は対象 anchor を絞ってよい)
5. `## 検証コマンド` をすべて実行する
6. diff レビューを回し、指摘を対応してから次へ

### ステップ 2: 運用文書の同期

1. `context/toolchain.md`「実環境解析の運用指針」の daemon JVM 項から「未実装の間は…」の暫定手順を削除し、`--analyzer-meta gradleJavaHome=<path>` を正式手順にする
2. `design/features/java-analyzer/discovery.md` / `protocol-mapping.md` の該当節から「未実装、実装は #82 で進行中」注記を削除する

### ステップ最終: 最終確認

1. 全テスト / lint がパスすることを確認
2. spec の `## 上位資料からの変更点` に追記が必要な差分が出ていないか確認し、あれば記録する
3. commit する (規約: `workflow-git` の commit-format、AI attribution 禁止)

## 実装コンテキスト

- spec: `specs/82-implicit-call-resolution/index.md` (解決済みの論点 D11、実装分割 P6)
- 設計正本: `design/features/java-analyzer/discovery.md` (daemon JVM 指定の規則) / `design/features/java-analyzer/protocol-mapping.md` の metadata 契約表 (`gradleJavaHome` 行)
- 参照する path:
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/discovery/GradleToolingClient.java` (connector / daemon JVM)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/discovery/` (version-check / DiscoveryFailure)
  - `analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/io/RequestReader.java` (metadata 解釈の既存様式)
  - `analyzers/java/build.gradle` (`gradleCompatibilityTest` 配線)
  - `context/toolchain.md` / `design/features/java-analyzer/discovery.md`

## 前提条件

- 完了しているべき phase / 依存 prompt: なし (他 prompt と並列可。変更ファイルは discovery / io 配下で P1〜P5 と重ならない)
- 完了後に着手可能になる後続 prompt: なし
- 必要な repo 状態: `feature/82` が analyzer-build を通る

## 不明点ハンドリング

- 矛盾 / 欠落 / 未定義を見つけたら作業を止める
- 推測で実装を進めない。Tooling API の Java home 指定 API が予期した形で存在しない場合は停止して確認する
- 質問するときは: 止まっている作業単位 / 判断が必要な論点 / 選択肢 を整理する

## タスク境界

### 実装する範囲

- `metadata.gradleJavaHome` の解釈・validation・Tooling API への引き渡し
- cross-version matrix への検証追加、運用文書の正式手順化 (未実装注記の削除)

### 実装しない範囲

- 互換 JDK の自動探索フォールバック (ADR-0012 で却下済み)
- 未指定時の daemon JVM 選択の変更 (従来どおり Gradle に委任)
- version-check の互換範囲そのものの変更

## 設計仕様

- `metadata.gradleJavaHome`: string 配列・要素 1 の path。`--analyzer-meta gradleJavaHome=<path>` で渡る (metadata 合成規則は既存契約どおり)
- 指定時、自動 discovery の Gradle daemon JVM として使用する。明示 override のみで暗黙の自動選択は導入しない
- 要素数違反・存在しない path・実行不能な Java home は `JAVA_INVALID_REQUEST` で拒否する (専用 code は設けない)
- 明示 `sourceRoots` 経路 (discovery bypass) では本 metadata は使用しない (無視される未知 key と同様の扱いにせず、bypass 時は解釈不要であることをコメントで明示)

## テスト観点

- WHEN `gradleJavaHome` に互換 JDK を指定したとき、daemon JVM 非互換だった build model 取得が成功する (matrix 検証)
- IF path が存在しない / 要素数が 1 でない場合、`JAVA_INVALID_REQUEST` で拒否される
- 未指定時の挙動が従来と変わらない (非回帰)

## 検証コマンド

- `cd analyzers/java && ./gradlew shadowJar` / `cd analyzers/java && ./gradlew test`
- `cd core && go build ./...` / `cd core && go vet ./...` / `cd core && go test ./...`
- `lefthook run pre-commit`

## 完了条件

- [ ] ステップ 0 でブランチと Draft PR を準備した (status 遷移済みを確認)
- [ ] 全ステップを順序通りに実行した
- [ ] 各ステップで diff レビューを実施し、指摘を対応した
- [ ] `## 検証コマンド` がすべてパスする
- [ ] matrix 検証と validation テストが「テスト観点」を網羅している
- [ ] 運用文書の暫定手順を正式手順へ置き換え、未実装注記を削除した
- [ ] spec の `## 上位資料からの変更点` に必要な追記を行った (無ければ「なし」を確認した)
- [ ] 未解決の仕様質問が残っていない
