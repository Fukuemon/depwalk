# Core / Java Analyzer アーキテクチャ再編 要求定義

## 要求フェーズ状況

`/requirements-full` 用。状態は `未着手 / 進行中 / 完了 / レビュー済 / 保留` のいずれか。
保留の場合は理由(ユーザー判断待ち等)を備考に記載する。

| #   | フェーズ           | 状態   | 最終更新   | 備考                                       |
| --- | ------------------ | ------ | ---------- | ------------------------------------------ |
| 1   | 受付               | 完了   | 2026-07-23 | spec-requirement で対話整理                |
| 2   | 下書き             | 完了   | 2026-07-23 | 本ドラフト                                 |
| 3   | スコープ/成功条件  | 完了   | 2026-07-23 | 深さ・対象範囲・自動検査をユーザー確認済み |
| 4   | 業務仕様           | 完了   | 2026-07-23 | 業務ルール節参照                           |
| 5   | バリデーション方針 | 完了   | 2026-07-23 | 機械検査 (import 規約) が該当              |
| 6   | 権限要件           | 完了   | 2026-07-23 | 対象外 (ローカル CLI / リファクタ)         |
| 7   | 監査/非機能        | 完了   | 2026-07-23 | 非機能節参照                               |
| 8   | 未決事項解消       | 進行中 | 2026-07-23 | 未決事項テーブル参照 (設計フェーズで解消)  |
| 9   | 最終レビュー       | 完了   | 2026-07-23 | ユーザー承認済み (epic として起票判断)     |
| 10  | 公開/同期          | 完了   | 2026-07-23 | #32 起票、spec-dir を 32- へリネーム済み   |

## チケット情報

- 起点: 自由文 (ユーザー要求)
- チケットID: #32
- トラッカー: GitHub (`Fukuemon/depwalk`)
- URL: https://github.com/Fukuemon/depwalk/issues/32

## 背景・目的

depwalk は Core (Go) と Analyzer (言語別) をプロセス + Protocol で分離しており、この外側の境界は明確に機能している。一方で **それぞれの内部** は feature 単位の package 分割に留まり、次の問題がある。

### Core 側の実測課題 (2026-07-23 時点の import 実測)

- `core/internal/protocol` がハブ化しており、`graph` / `output` / `cli` / `analyze` / `analyzer` の 5 package が直接依存している。wire 表現 (JSONL DTO) がドメイン側へ漏れており、[context/architecture.md](../../context/architecture.md) の「wire DTO / wire 専用フィールドを graph model に保持しない」「Model は他に依存しない」という規約と実装が乖離している (`graph -> protocol` の import が実在)。
- `core/internal/cli` が use case (`analyze`) だけでなく `graph` / `output` / `protocol` にも直接依存しており、エントリポイントが内層を迂回参照している。
- Domain (graph / traversal) / UseCase (analyze) / Infrastructure (protocol / analyzer / output / cli) に相当する層の区別がディレクトリ構造からも import 規約からも読み取れず、新規参加者 (人間 / AI エージェント) が依存方向を誤りやすい。

### Java Analyzer 側の課題

- `analysis` 配下に attribution / augment / completeness / context / graph / normalize / scope / sootup / spring の 9 sub-package が並列しており、解析パイプラインの段階 (どれが入口でどの順に流れるか)、SootUp など外部ライブラリへの依存境界がディレクトリ構造から読み取れない。

これを Domain / UseCase / Infrastructure の層が明示されたディレクトリ構造へ再編し、依存方向を機械検査で固定し、正本ドキュメントを実態と一致させることが目的。

## 想定ユーザー/ステークホルダー

- **リポジトリ開発者 (Fukuemon)**: 機能追加時に「どの層に置くか / 何に依存してよいか」を迷わず判断したい
- **AI エージェント (spec-\* workflow)**: context/architecture.md を読んで正しい配置・依存で実装したい
- **将来のコントリビュータ / 第 2 言語 Analyzer 実装者**: 参照実装 (Java Analyzer) の構造から Analyzer の作り方を読み取りたい

## 提供価値(成功条件)

- 依存方向が機械検査 (lint) で強制され、実際の依存関係 (package 間のエッジ) が生成された依存図と配線コード (composition root) から判別できる (2026-07-25 改訂: 当初の「ディレクトリ構造を見るだけで層が判別できる」は、層 3 分類の粗い順序しか示せず知りたい解像度に届かないため、生成図による判別に置き換え。決定経緯は spec D8)
- 層をまたぐ禁止 import が CI / pre-commit で機械的に検出され、regression が防止される
- context/architecture.md・DesignDoc の記述と実装の乖離 (graph -> protocol 等) がゼロになる
- 既存の外部挙動 (CLI インターフェース / JSONL Protocol / 出力形式) は一切変わらない

## スコープ

### やること

- **Core (Go) のディレクトリ再編**: `core/internal` 配下を層別構造 (例: domain / usecase / infra 相当) へ物理的に移動・改名する。層の命名と配置は設計フェーズで確定する
- **依存方向の是正**: wire 表現 (protocol DTO) のドメイン漏れを変換層で断ち切る (`graph` / `output` から `protocol` への依存除去を含む)。エントリポイント (`cli`) の内層迂回参照を整理する
- **Java Analyzer のパッケージ再編**: `javaanalyzer` 配下を解析パイプラインの段階と依存境界 (SootUp 等の外部ライブラリ隔離) が読み取れる構造へ再編する
- **依存方向の自動検査**: Go 側は lint (depguard 等) で層をまたぐ禁止 import を検査し、既存 quality gate (lefthook / CI) に組み込む。Java 側の検査手段は設計フェーズで選定する
- **ドキュメント同期**: context/architecture.md の Package Boundary、design/DesignDoc.md・feature doc の該当節、context/project.md の Naming Conventions / 対象ドメイン記述を再編後の実態に合わせて更新する

### やらないこと

- CLI インターフェース (フラグ / 引数 / 出力形式)、JSONL Protocol schema、exit code 等の **外部挙動の変更**
- 新機能の追加、既存ロジックのアルゴリズム変更
- Core / Analyzer 間のプロセス境界・Protocol 境界の変更 (この境界は現状維持)
- analyzers/java の Gradle build 構成 (shadowJar / compatibility matrix) の変更 (package 移動に伴う機械的追随を除く)

## 業務ルール

| #   | ルール                                                                                      | 理由                                                     | 備考                                    |
| --- | ------------------------------------------------------------------------------------------- | -------------------------------------------------------- | --------------------------------------- |
| 1   | 依存方向は内向き単方向 (Infra → UseCase → Domain)。Domain は他層に依存しない                | クリーンアーキテクチャの基本原則。DesignDoc P2/P3 と整合 | 層の具体名は設計フェーズで確定          |
| 2   | wire 表現 (Protocol DTO) は境界の変換層でドメインモデルへ写像し、内層に持ち込まない         | architecture.md の既存規約を実装レベルで担保             | 現状の `graph -> protocol` 依存の是正   |
| 3   | 外部ライブラリ (Cobra / SootUp / Gradle Tooling API) への依存は Infrastructure 層に隔離する | 将来のライブラリ差し替え・テスト容易性                   |                                         |
| 4   | 再編は外部挙動を変えない (E2E / golden test が無変更で PASS すること)                       | リファクタリングの安全性担保                             | テスト自体の package 移動に伴う修正は可 |
| 5   | 層をまたぐ禁止 import は機械検査 (lint) で検出し、quality gate に組み込む                   | 人力レビュー頼みでは regression する                     | engineering.md の quality gate 節へ追記 |

## 入出力要件

リファクタリングのため、プロダクトとしての入出力は変更しない。本 issue の成果物としての入出力を記す。

### 入力

| #   | 項目                                      | 必須/任意 | 形式       | 制約/備考                    |
| --- | ----------------------------------------- | --------- | ---------- | ---------------------------- |
| 1   | 現行 core / analyzers/java のソースツリー | 必須      | Go / Java  |                              |
| 2   | 既存テストスイート (unit / E2E / golden)  | 必須      | go test 等 | 再編前後の挙動不変の検証基準 |
| 3   | 正本ドキュメント (architecture.md 等)     | 必須      | Markdown   | 再編後の同期対象             |

### 出力

| #   | 出力                                           | 条件                   | 備考                                     |
| --- | ---------------------------------------------- | ---------------------- | ---------------------------------------- |
| 1   | 層別に再編されたディレクトリ構造 (Go / Java)   | 必須                   | 具体構造は設計フェーズで確定             |
| 2   | 依存方向 lint 設定と quality gate への組み込み | 必須                   | Go: depguard 等。Java: 設計フェーズ選定  |
| 3   | 更新された正本ドキュメント群                   | 必須                   | architecture.md / DesignDoc / project.md |
| 4   | 層構造の判断根拠を記録した ADR                 | 設計判断が非自明な場合 |                                          |

## 例外シナリオ

| #   | シナリオ                                                 | ユーザーへの見せ方                          | 代替手段                                             |
| --- | -------------------------------------------------------- | ------------------------------------------- | ---------------------------------------------------- |
| 1   | 再編後にテスト (unit / E2E) が FAIL する                 | 該当 commit を merge しない (CI で検出)     | 挙動差分を特定し是正するまで PR を進めない           |
| 2   | protocol 依存の除去で変換コストや重複モデルが過大になる  | 設計フェーズで trade-off を提示し判断を仰ぐ | 該当箇所のみ規約に例外を設け ADR に記録              |
| 3   | Java 側の依存検査に適切なツールがない / 導入コストが過大 | 設計フェーズで報告                          | Java 側の機械検査を別 issue へ分離し、規約文書で運用 |

## バリデーション方針（業務観点）

| #   | 対象                 | ルール                                       | エラー時の扱い          |
| --- | -------------------- | -------------------------------------------- | ----------------------- |
| 1   | Go package の import | 層をまたぐ禁止 import を lint で検査         | CI / pre-commit で FAIL |
| 2   | 外部挙動             | 既存 E2E / golden test が無変更で PASS       | CI で FAIL              |
| 3   | ドキュメント整合     | architecture.md の境界記述と実 import の一致 | レビューで確認          |

## 権限要件

ローカル CLI のリファクタリングであり、権限要件は対象外。

| #   | 機能   | ロール | 許可/条件 | 根拠 |
| --- | ------ | ------ | --------- | ---- |
| -   | 対象外 | -      | -         | -    |

## 監査/非機能要件

### 監査・運用

- 再編の判断根拠 (層の命名 / 配置 / 例外) は ADR に記録し、変更経緯を追跡可能にする
- spec の変更履歴・設計フェーズ状況を都度更新する (Spec Workflow Contract)

### 非機能(性能/可用性/セキュリティ/保守)

- **性能**: wire → ドメインの変換層追加による解析時間の目立った劣化がないこと (現行 E2E の実行時間から大きく逸脱しない)
- **保守**: 新規 package 追加時に配置先の層が一意に決まる程度に、層の責務定義が明文化されていること
- **互換**: Go / Java の toolchain バージョン、Gradle compatibility matrix の対象範囲は変更しない

## 受け入れ基準 (EARS)

1. WHEN 開発者が `core/internal` および `analyzers/java` のディレクトリツリーを閲覧したとき、THE SYSTEM SHALL Domain / UseCase / Infrastructure に相当する層の区別と依存方向がディレクトリ構造 (および各層の README または architecture.md) から判別できる状態を提供する。
2. THE SYSTEM SHALL Core の Domain 相当層 (graph / traversal 相当) から wire 表現 (`protocol` 相当 package) への import を持たない。
3. IF 層をまたぐ禁止 import が追加された場合、THEN THE SYSTEM SHALL lint (quality gate) で検出し CI / pre-commit を FAIL させる。
4. WHEN 再編後に既存のテストスイート (Go unit / Java unit / E2E / golden) を実行したとき、THE SYSTEM SHALL テスト本体のロジック変更なし (package 移動に伴う機械的修正のみ) で全件 PASS する。
5. THE SYSTEM SHALL context/architecture.md の Package Boundary 記述・context/project.md の Naming Conventions と、実装の package 構造 / import 関係を一致させる。

## 未決事項（論点）

| #   | 論点                                                                       | 決定者   | 期限           | 状態 | メモ                                                |
| --- | -------------------------------------------------------------------------- | -------- | -------------- | ---- | --------------------------------------------------- |
| 1   | 層のディレクトリ命名 (domain/usecase/infra か Go 慣習寄りの命名か)         | Fukuemon | 設計フェーズ内 | 未決 | Go コミュニティは機械的な層名を避ける傾向。ADR 候補 |
| 2   | output の位置づけ (presenter として独立層にするか Infra に含めるか)        | Fukuemon | 設計フェーズ内 | 未決 | output は traversal result の consumer              |
| 3   | Java 側の依存検査ツール選定 (ArchUnit 等)                                  | Fukuemon | 設計フェーズ内 | 未決 | 導入コスト過大なら別 issue 分離 (例外シナリオ #3)   |
| 4   | 段階分割 (Core 再編 / Java 再編 / lint 導入を 1 PR にするか複数に分けるか) | Fukuemon | 設計フェーズ内 | 未決 | epic + 子 issue 分割の可能性あり                    |

## 設計着手条件チェック

- [x] 業務ルールが確定している
- [x] 入出力要件が確定している
- [x] 例外シナリオが確定している
- [x] バリデーション方針が確定している
- [x] 権限要件が確定している（対象外と判断）
- [x] 監査/非機能要件が確定している
- [ ] 未決事項がゼロ、または担当者・期限付きで管理されている (4 件を設計フェーズで解消する前提で管理中)

## 関連資料

- [design/DesignDoc.md](../../design/DesignDoc.md) — 設計原則 P1〜P4、モジュール責務 (本要求は P2/P3 の内部徹底であり矛盾しない)
- [context/architecture.md](../../context/architecture.md) — Package Boundary (本要求で実態と同期する対象)
- [context/project.md](../../context/project.md) — Naming Conventions / 対象ドメイン (同期対象)
- [adr/0002-core-implementation-foundation.md](../../adr/0002-core-implementation-foundation.md) — Core 実装基盤 (package 構成の変更で改訂または追補 ADR が必要)
- 関連 feature doc: graph / output / analyzer-protocol / java-analyzer (`design/features/`)

## 既存資料からの変更点

| 対象                    | 変更内容                                        | 理由                 |
| ----------------------- | ----------------------------------------------- | -------------------- |
| context/architecture.md | Package Boundary 表を層別構造へ改訂             | 実装再編との同期     |
| context/project.md      | Naming Conventions の Core package 一覧を改訂   | 同上                 |
| adr/0002                | package 構成の記述が古くなるため追補 ADR で改訂 | 意思決定の追跡可能性 |
| design/features/*       | graph / output 等の package 参照箇所を更新      | 同上                 |

## 変更履歴

| 日付       | 変更者                    | 変更内容                   |
| ---------- | ------------------------- | -------------------------- |
| 2026-07-23 | Claude (spec-requirement) | 初版ドラフト作成           |
| 2026-07-23 | Claude (spec-requirement) | #32 起票、チケット情報同期 |

## 備考

### 非対象（開発設計で扱う）

- 層の具体的なディレクトリ名・package 配置図
- 変換層 (protocol DTO → ドメインモデル) の API 詳細
- depguard / ArchUnit 等の具体的な lint ルール定義
