# Core / Java Analyzer アーキテクチャ再編

> spec 本体。要求の正本は [requirements.md](requirements.md)、上位文書は [design/DesignDoc.md](../../design/DesignDoc.md) / [context/architecture.md](../../context/architecture.md) を参照する。

## メタ情報

- Issue: `#32`
- ステータス: `Draft`
- 作成日: 2026-07-23
- 更新日: 2026-07-23
- Branch: `feature/32`
- Owner: Fukuemon

## 設計フェーズ状況

状態は `未着手 / 進行中 / 完了 / レビュー済 / 保留` のいずれか。保留の場合は理由を備考に残す。

| #   | フェーズ                    | 状態       | 最終更新   | 備考                                            |
| --- | --------------------------- | ---------- | ---------- | ----------------------------------------------- |
| 1   | 起票                        | 完了       | 2026-07-23 | #32 (requirements.md で要求整理済み)            |
| 2   | 下書き                      | レビュー済 | 2026-07-23 | 本 scaffold。spec-review PASS                   |
| 3   | 上位文書突合                | レビュー済 | 2026-07-23 | 変更提案は本 issue の成果物。sync phase で反映  |
| 4   | 論点整理                    | レビュー済 | 2026-07-23 | requirements の未決 4 件 + scaffold で追加 3 件 |
| 5   | 論点解決                    | 進行中     | 2026-07-23 | D1〜D3 / D5〜D7 解決済み。残り D4               |
| 6   | Interface / Routing 設計    | 未着手     |            | 層別ディレクトリ構造・package 配置図の確定      |
| 7   | Content / Data 設計         | 未着手     |            | 変換層 (wire DTO → domain model) の API 詳細    |
| 8   | Performance / Security 設計 | 未着手     |            | 変換層追加による性能影響の確認方針              |
| 9   | Test / Metrics 設計         | 未着手     |            | 挙動不変の検証方針 (既存テスト無変更 PASS)      |
| 10  | 実装分割                    | 未着手     |            | 段階分割 (D4) の決定に依存                      |
| 11  | レビュー済                  | 未着手     |            |                                                 |

## 上位文書整合

正本 ([Design Doc](../../design/DesignDoc.md) / [feature doc](../../design/features/) / [context](../../context/) / ADR) のどの節と、どう整合させたかを記録する。

- PRD 更新要否: 不要 (統合モード。DesignDoc の Why/What / 成功条件 S1〜S5 は変更しない)
- Design Doc 更新要否: 要 (モジュール責務・図の package 参照が再編後に古くなる場合のみ)
- ADR 起票要否: 要 (層構造の判断根拠。ADR-0002 の package 構成記述への追補)

| 上位文書                | 節 / 該当箇所                                         | 整合方針 (継承 / 補足 / 変更提案)                                        |
| ----------------------- | ----------------------------------------------------- | ------------------------------------------------------------------------ |
| Design Doc (統合 PRD)   | Why/What / 成功条件 / Non Goals                       | 継承 (外部挙動・スコープを変えない)                                      |
| Design Doc              | モジュール責務 / 設計原則 P1〜P4                      | 継承 (P2/P3 の内部徹底。責務分割自体は変えない)                          |
| Design Doc              | モジュール責務表・図中の package 対応                 | 変更提案 (再編後の package 名参照の更新が必要なら sync で反映)           |
| feature doc (graph)     | staging Graph / wire DTO 非保持の規約                 | 継承 + 変更提案 (`graph -> protocol` 依存の是正で実装を規約に一致させる) |
| feature doc (output 等) | package 参照箇所                                      | 変更提案 (移動後の path 追随。sync で反映)                               |
| context/architecture.md | Package Boundary (package 表 / 依存方向)              | 変更提案 (層別構造へ改訂。本 issue の成果物)                             |
| context/project.md      | Naming Conventions (Core package 一覧) / 対象ドメイン | 変更提案 (再編後の package 一覧へ改訂)                                   |
| context/engineering.md  | quality gate                                          | 変更提案 (依存方向 lint の組み込みを追記)                                |
| ADR-0002                | 初期 directory / package 構成                         | 変更提案 (追補 ADR で改訂。0002 自体は履歴として保持)                    |
| ADR-0001 / ADR-0006     | Protocol 境界 / Gradle discovery                      | 継承 (プロセス境界・Protocol は現状維持)                                 |

> 変更提案は本 issue の目的そのもの (ドキュメント同期が成果物) であり、変更内容は論点解決 (層命名・配置) に依存する。したがって scaffold 時点で sync へ分岐せず、論点解決・設計確定後の sync phase で back-propagate する。

## 関連資料

- 要求定義: [requirements.md](requirements.md)
- Issue: https://github.com/Fukuemon/depwalk/issues/32
- [design/DesignDoc.md](../../design/DesignDoc.md) — 設計原則 P1〜P4、モジュール責務
- [context/architecture.md](../../context/architecture.md) — Package Boundary (改訂対象)
- [context/project.md](../../context/project.md) — Naming Conventions / 対象ドメイン (改訂対象)
- [context/engineering.md](../../context/engineering.md) — quality gate (lint 組み込み先)
- [adr/0002-core-implementation-foundation.md](../../adr/0002-core-implementation-foundation.md) — 現行 package 構成の正本 (追補対象)
- 関連 feature doc: [graph](../../design/features/graph/DesignDoc_graph.md) / [output](../../design/features/output/DesignDoc_output.md) / [analyzer-protocol](../../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md) / [java-analyzer](../../design/features/java-analyzer/DesignDoc_java-analyzer.md)

## 背景

- Core (Go) は `core/internal` 配下が feature 単位の 7 package 並列で、Domain / UseCase / Infrastructure の層区別と依存方向がディレクトリ構造から読み取れない。
- import 実測 (2026-07-23) で `core/internal/protocol` がハブ化しており、`graph` / `output` / `cli` / `analyze` / `analyzer` の 5 package が直接依存。wire 表現 (JSONL DTO) がドメイン側へ漏れ、architecture.md の「wire DTO を graph model に保持しない」「Model は他に依存しない」と実装が乖離している (`graph -> protocol` の import が実在)。
- `core/internal/cli` が use case (`analyze`) だけでなく `graph` / `output` / `protocol` にも直接依存し、エントリポイントが内層を迂回参照している。
- Java Analyzer は `analysis` 配下に 9 sub-package が並列し、解析パイプラインの段階と SootUp 等外部ライブラリへの依存境界が構造から読み取れない。
- これを層が明示された構造へ再編し、依存方向を機械検査 (lint) で固定し、正本ドキュメントを実態と一致させる。外側の Core / Analyzer プロセス境界 (P1〜P4) は変更しない。

## スコープ

### やること

- Core (Go) `core/internal` 配下の層別ディレクトリ再編 (層の命名・配置は設計で確定)
- 依存方向の是正: wire 表現のドメイン漏れを変換層で断ち切る (`graph` / `output` から `protocol` への依存除去を含む)。`cli` の内層迂回参照の整理
- Java Analyzer `javaanalyzer` 配下を、解析パイプラインの段階と依存境界 (SootUp 等の隔離) が読み取れる構造へ再編
- 依存方向の自動検査: Go は lint (depguard 等) を既存 quality gate (lefthook / CI) に組み込む。Java 側の検査手段は設計で選定
- ドキュメント同期: architecture.md / DesignDoc / feature doc / project.md / engineering.md / 追補 ADR

### やらないこと

- CLI インターフェース (フラグ / 引数 / 出力形式)、JSONL Protocol schema、exit code 等の外部挙動の変更
- 新機能の追加、既存ロジックのアルゴリズム変更
- Core / Analyzer 間のプロセス境界・Protocol 境界の変更
- analyzers/java の Gradle build 構成 (shadowJar / compatibility matrix) の変更 (package 移動に伴う機械的追随を除く)

## 要件の解釈

### 実現したいユーザー価値

- 機能追加時に「どの層に置くか / 何に依存してよいか」をディレクトリ構造と lint から迷わず判断できる
- AI エージェント (spec-* workflow) が architecture.md を読んで正しい配置・依存で実装できる
- 第 2 言語 Analyzer 実装者が参照実装 (Java Analyzer) の構造から Analyzer の作り方を読み取れる

### 成功条件

- ディレクトリ構造 (および層 README / architecture.md) だけで層と依存方向が判別できる
- 層をまたぐ禁止 import が CI / pre-commit で機械検出される
- architecture.md / DesignDoc の記述と実装の乖離 (graph -> protocol 等) がゼロ
- 既存の外部挙動 (CLI / JSONL Protocol / 出力形式) が一切変わらない

### 対象ユーザー / 操作主体

- リポジトリ開発者 (Fukuemon) / AI エージェント / 将来のコントリビュータ

EARS 風で振る舞いを記述する (正本は [requirements.md の受け入れ基準](requirements.md#受け入れ基準-ears))。

- WHEN 開発者が `core/internal` / `analyzers/java` のツリーを閲覧したとき、システムは層の区別と依存方向を構造から判別できる状態を提供する
- THE SYSTEM SHALL Core の Domain 相当層から wire 表現 (`protocol` 相当 package) への import を持たない
- IF 層をまたぐ禁止 import が追加された場合、システムは lint で検出し CI / pre-commit を FAIL させる
- WHEN 再編後に既存テストスイートを実行したとき、システムはテスト本体のロジック変更なし (package 移動の機械的修正のみ) で全件 PASS する
- THE SYSTEM SHALL architecture.md / project.md の記述と実装の package 構造・import 関係を一致させる

## 設計時の論点

設計 / 実装フェーズへ持ち越す残課題を 1 件ずつ管理する。確定したものは「解決済みの論点」へ移す。
D1〜D4 は [requirements.md の未決事項](requirements.md#未決事項論点) から引き継ぎ。D5〜D7 は scaffold で追加。

| #   | 論点                                                                                     | 決定候補                                  | 決定 |
| --- | ---------------------------------------------------------------------------------------- | ----------------------------------------- | ---- |
| D4  | 段階分割 (Core 再編 / Java 再編 / lint 導入を 1 PR にするか、epic + 子 issue に分けるか) | A) 1 spec 内で PR 分割 B) epic + 子 issue | 未決 |

## 解決済みの論点

(clarify で確定したものをここに移動する)

- **D1: Core の層ディレクトリ命名 → B) Go 慣習寄りの層名 `domain` / `app` / `platform` を採用** (2026-07-23, Fukuemon)
  - `core/internal/` 直下を `domain/` (graph / traversal)、`app/` (analyze)、`platform/` (protocol / analyzer / cli、output は D2 で確定) の 3 層ディレクトリでグルーピングする。package 名 (import 末尾) は従来の責務名を維持する
  - `usecase` / `infra` という機械的層名は避け、Go コミュニティで通用する語彙 (`app` = アプリケーションサービス層、`platform` = 技術基盤層) を使う。層名とクリーンアーキテクチャ用語の対応は architecture.md と各層 README で明文化する
  - 依存方向は `platform` → `app` → `domain` の内向き単方向 (業務ルール 1)。lint (D5) はディレクトリ prefix 単位でルール化する
- **D2: output の位置づけ → A) `platform/output` (presenter 層は設けない)** (2026-07-23, Fukuemon)
  - output は「外界への書き出し形式」という技術詳細として `platform` に含める。依存先は `domain` のみ (現状の `output -> protocol` import は本 issue で除去)
  - package 1 つのために 4 層目 (presenter) を作らない (先回りした共通化の回避)。将来 formatter が肥大化した場合に分離を再検討する
- **D6: wire 変換層の配置 → A) platform 側に変換、app に port interface** (2026-07-23, Fukuemon)
  - `domain/graph` が自前の `Symbol` / `SourceLocation` 相当型を持ち、protocol import をゼロにする (受け入れ基準 2)。wire 型との重複定義は境界隔離のコストとして許容する
  - `app/analyze` は domain 型を返す port interface を定義し、`platform/protocol` (adapter) が wire → domain 変換を担って port を実装する。変換関数の現在地 (`graph/convert.go` の `NodeFromMethodSymbol` 等) は platform 側へ移す
  - 依存方向は `platform` → `app` → `domain` の内向き単方向を例外なしで成立させる (app から protocol への import も除去)
  - 配線は `cli` でのコンストラクタ注入による**手動 DI** とし、`google/wire` 等の DI ライブラリ・コード生成は導入しない (独自の変換層のみ。ADR-0002 の依存最小方針と整合)
- **D5: Go 側の依存方向 lint → A) golangci-lint + depguard** (2026-07-23, Fukuemon)
  - golangci-lint を dev ツールとして導入し、depguard で層別 (ディレクトリ prefix 単位) の import 禁止ルールを宣言する: `domain` は `app` / `platform` を deny、`app` は `platform` を deny
  - 既存 quality gate (lefthook pre-commit / CI) に組み込む。バージョンは固定して再現性を保つ
  - ルール定義の詳細 (`.golangci.yml` の具体構成) は実装 phase で確定
- **D7: Java Analyzer の層構造 → A) パイプライン段階 + 外部ライブラリ隔離** (2026-07-23, Fukuemon)
  - `javaanalyzer` 直下 (`protocol` / `io` / `preflight` / `discovery`) は現状維持。`discovery` は引き続き Gradle Tooling API の隔離境界とする
  - `analysis` 配下を「実行順の段階別 package」+「外部ライブラリ adapter package」で再編する。段階の実行順は `pipeline` (Runner) だけが知る
  - SootUp 型の漏れ (現状 `graph` / `augment` / `completeness` / `spring` / `AnalysisRunner` に散在) を adapter package (`sootup` 等) の境界内に封じ、JavaParser / SymbolSolver も同様に扱う
  - Core (3 層) と命名思想が非対称になることは許容し、意図 (Analyzer は変換パイプラインである) を README / architecture.md で説明する
  - クラス単位の最終配置図は diagram phase で確定する
- **D3: Java 側の依存検査 → A) ArchUnit を採用** (2026-07-23, Fukuemon)
  - ArchUnit を test 依存として追加し、「adapter package 以外から `sootup.*` / `com.github.javaparser.*` / `org.gradle.tooling.*` を import 禁止」等のルールを JUnit テストとして記述する
  - 既存の `./gradlew test` (quality gate 組み込み済み) で実行されるため、新しい gate 配線は不要。Go 側 (depguard) と対の機械検査が揃う (業務ルール 5)
  - 具体的なルールセットは diagram phase の配置図確定後に定義する。例外シナリオ 3 (別 issue 分離) は不採用

## 未確定事項

(決定できない項目を理由とともに残す。1 件でも残っていれば下流 phase は止める)

- D4 (上表)。clarify (論点解決 phase) で確定する。決定者は Fukuemon、期限は clarify phase 内 ([requirements.md の未決事項](requirements.md#未決事項論点) と同一管理)

## 実装対象

正規 target は [context/project.md](../../context/project.md) の対象ドメイン一覧を正本とする。

| モジュール          | 実装有無 | 主な責務                                                                     |
| ------------------- | :------: | ---------------------------------------------------------------------------- |
| `core`              |    ◯     | `core/internal` の層別再編、cli 迂回参照の整理、Go lint 組み込み             |
| `traversal`         |    ◯     | Domain 相当層への配置替え (ロジック変更なし)                                 |
| `output`            |    ◯     | `platform/output` へ配置 (D2 確定)、`protocol` 依存の除去                    |
| `analyzer-protocol` |    ◯     | wire → domain 変換を platform 側に集約 (D6 確定)。Protocol schema 自体は不変 |
| `java-analyzer`     |    ◯     | `javaanalyzer` 配下のパッケージ再編、ArchUnit による依存検査 (D3 確定)       |

## 機能仕様

リファクタリングのため、ユーザー向け機能仕様は変更しない。本 spec では「層構造と依存規約」を仕様として扱う。

### User Flow

外部挙動の変更なし (`depwalk analyze` の操作フローは現状維持)。

### Reuse Policy

- 層の抽象は Core / Java Analyzer それぞれの内部で閉じる。Core と Analyzer 間で code を共有しない (architecture.md の既存規約を継承)
- 先回りした共通化 (未使用の抽象層・interface) を導入しない。層分けは既存責務の再配置に留める

### Performance

- wire → domain の変換層追加による解析時間の目立った劣化がないこと (現行 E2E の実行時間から大きく逸脱しない)

### Routing / URL State

該当なし (CLI ツール)。

### Content / Assets

該当なし。

### UI Reuse

該当なし。

### Testing

- 既存テストスイート (Go unit / Java unit / E2E / golden) がテスト本体のロジック変更なしで全件 PASS することを挙動不変の検証基準とする
- 依存方向 lint (golangci-lint + depguard、D5 確定) を quality gate (lefthook pre-commit / CI) に組み込み、禁止 import を機械検出する
- Java 側は ArchUnit (D3 確定) を JUnit テストとして追加し、既存の `./gradlew test` で外部ライブラリ隔離・段階間依存を検査する

## Interface 設計

### UI / API / Event Interface

- (設計 phase で確定: 層別ディレクトリ構造図、各層の公開境界)

### Props / Request / Response

- 変換層 (D6 確定): `app/analyze` が domain 型を返す port interface を定義し、`platform/protocol` が wire DTO (`MethodSymbol` / `CallEdge` / `SourceLocation`) → domain 型 (`graph.Node` / `graph.Edge` / domain 版 `SourceLocation`) の写像を実装する。変換関数のシグネチャ詳細は diagram / 実装 phase で確定
- 配線は `cli` でのコンストラクタ注入による手動 DI (`google/wire` 等の DI ライブラリは導入しない)

## Content / Data 設計

### 保存・管理するデータ

- 永続データなし (既存どおり process 内 staging Graph のみ。State Boundary は変更しない)

### コンテンツ配置 / package / route

- Core (Go) の層別配置 (D1 / D2 / D6 決定):

```text
core/internal/
├── domain/         # ドメイン層 (他層に依存しない。wire 非依存)
│   ├── graph/      # graph model (自前の Symbol / SourceLocation 型)
│   └── traversal/  # caller/callee 探索
├── app/            # アプリケーションサービス層 (usecase 相当)
│   └── analyze/    # analyze orchestration + port interface 定義
└── platform/       # 技術基盤層 (infra 相当。外部ライブラリ隔離)
    ├── protocol/   # JSONL wire DTO / parse / validate + wire→domain 変換 (port 実装)
    ├── analyzer/   # Analyzer process 制御
    ├── output/     # formatter (依存は domain のみ)
    └── cli/        # Cobra command + 手動 DI 配線
```

- package 名 (import 末尾) は従来の責務名を維持する。`core/cmd/depwalk` は現状維持
- Java Analyzer の構造原理 (D7 確定): `javaanalyzer` 直下 (`protocol` / `io` / `preflight` / `discovery`) は現状維持。`analysis` 配下を「実行順の段階別 package + 外部ライブラリ adapter package (`sootup` / `javaparser` 等)」へ再編し、段階の実行順は `pipeline` (Runner) だけが知る。クラス単位の配置図は diagram phase で確定

## Performance / Security 設計

### Performance

- 変換層は既存の analyze use case が行っている protocol record → graph 変換の再配置であり、追加の走査を導入しない方針 (設計 phase で確認)

### Security / Privacy

- 該当なし (ローカル CLI のリファクタリング。Runtime / State Boundary は変更しない)

## Error / Fallback 設計

### エラーケース

| #   | ケース                                                   | ユーザーへの見せ方                         | リカバリ                                             |
| --- | -------------------------------------------------------- | ------------------------------------------ | ---------------------------------------------------- |
| 1   | 再編後にテスト (unit / E2E) が FAIL する                 | 該当 commit を merge しない (CI で検出)    | 挙動差分を特定し是正するまで PR を進めない           |
| 2   | protocol 依存の除去で変換コストや重複モデルが過大になる  | 設計 phase で trade-off を提示し判断を仰ぐ | 該当箇所のみ規約に例外を設け ADR に記録              |
| 3   | Java 側の依存検査に適切なツールがない / 導入コストが過大 | 設計 phase で報告                          | Java 側の機械検査を別 issue へ分離し、規約文書で運用 |

### Fallback

- 再編は段階的に進め、各段階で既存テストが PASS する状態を保つ (壊れた中間状態を main に入れない)

## テスト / 評価方針

### テスト観点

- 既存テスト無変更 PASS (受け入れ基準 4)
- 禁止 import の lint 検出 (受け入れ基準 3): 意図的に違反 import を書いて FAIL することを確認
- ドキュメント整合 (受け入れ基準 5): architecture.md の境界記述と実 import の一致をレビューで確認

### 計測指標

- E2E 実行時間が現行から大きく逸脱しないこと

## フロー / シーケンス

(diagram phase で生成。再編後の依存方向図・変換層のデータフローを Mermaid に落とす)

### Flowchart (ユーザー操作起点)

```mermaid
flowchart TD
```

### Sequence

```mermaid
sequenceDiagram
```

## 実装分割

### 実装タスク案

(D4 の決定後に確定)

| Phase | 対象 | 概要 | 依存 |
| ----- | ---- | ---- | ---- |
| P1    |      |      |      |

### prompts 生成方針

- 対象ドメイン (core / java-analyzer) と作業種別 (再編 / lint / doc 同期) のどこで分けるかは D4 の決定に従う
- 並列実装できる境界: Core 再編と Java 再編は独立。lint 導入は各再編の完了に依存

## 上位資料からの変更点

本 spec で Design Doc / feature doc / context / 既存 ADR から変更・追加した内容を、反映先別に記録する。track / sync phase で更新する。

### PRD への影響

| 対象節 | 変更内容 | 理由 |
| ------ | -------- | ---- |
| なし   |          |      |

### Design Doc への影響

| 対象節                      | 変更内容 | 理由 |
| --------------------------- | -------- | ---- |
| (設計確定後に track で記録) |          |      |

### feature doc への影響

| 対象 doc / 節                     | 変更内容                                                                            | 理由                      |
| --------------------------------- | ----------------------------------------------------------------------------------- | ------------------------- |
| graph / データ構造・変換          | wire → graph 変換の所在を graph package から platform 側 (adapter) へ移す記述に更新 | D6 決定 (source: clarify) |
| java-analyzer / 内部構成          | `analysis` 配下の package 参照を段階別 + adapter 構造へ更新                         | D7 決定 (source: clarify) |
| (残りは設計確定後に track で記録) |                                                                                     |                           |

### context への影響

| 対象 doc / 節                      | 変更内容                                                                                        | 理由                      |
| ---------------------------------- | ----------------------------------------------------------------------------------------------- | ------------------------- |
| architecture.md / Package Boundary | Core package 表を 3 層構造 (`domain` / `app` / `platform`) へ改訂し、層名と層責務の対応を明文化 | D1 決定 (source: clarify) |
| project.md / Naming Conventions    | Core package 一覧を `core/internal/{domain,app,platform}/...` へ改訂                            | D1 決定 (source: clarify) |
| architecture.md / Package Boundary | 変換の所在を「app/analyze が port を定義し platform/protocol が wire→domain 変換を実装」へ更新  | D6 決定 (source: clarify) |
| engineering.md / quality gate      | golangci-lint + depguard による依存方向検査を lefthook / CI の quality gate へ追記              | D5 決定 (source: clarify) |
| engineering.md / quality gate      | Java 側の依存検査 (ArchUnit、`./gradlew test` 内で実行) を quality gate の記述へ追記            | D3 決定 (source: clarify) |
| (残りは設計確定後に track で記録)  |                                                                                                 |                           |

### ADR の新規 / 更新

| ADR ID                            | 変更内容                                                                            | 理由                      |
| --------------------------------- | ----------------------------------------------------------------------------------- | ------------------------- |
| 新規 (番号は起票時採番)           | 層別ディレクトリ再編と Go 慣習寄り層名 (`domain`/`app`/`platform`) の採用判断を記録 | D1 決定 (source: clarify) |
| ADR-0002 追補                     | 初期 directory / package 構成の記述を新 ADR 参照へ追補                              | D1 決定 (source: clarify) |
| (残りは設計確定後に track で記録) |                                                                                     |                           |

## レビュー

`spec-review` (fresh-context evaluator) の最新結果。完全な記録は `review.md` を参照。

| 日付       | 結果 (PASS / NEEDS_WORK) | 指摘要点                                                      | 対応                                                    |
| ---------- | ------------------------ | ------------------------------------------------------------- | ------------------------------------------------------- |
| 2026-07-23 | PASS (scaffold)          | 軽微 2 件: D5〜D7 の決定者未明示 / requirements.md の記法崩れ | 未確定事項へ決定者・期限を補記 / requirements.md を修正 |

## 変更履歴

| 日付       | 変更者                  | 変更内容                                                 |
| ---------- | ----------------------- | -------------------------------------------------------- |
| 2026-07-23 | Claude (spec-lifecycle) | scaffold: index.md 初版作成 (論点 D1〜D7 整理)           |
| 2026-07-23 | Claude (spec-lifecycle) | spec-review PASS (scaffold)。軽微指摘 2 件を反映         |
| 2026-07-23 | Claude (spec-lifecycle) | clarify: D1 解決 (層名 domain/app/platform 採用)         |
| 2026-07-23 | Claude (spec-lifecycle) | clarify: D2 解決 (output は platform に配置)             |
| 2026-07-23 | Claude (spec-lifecycle) | clarify: D6 解決 (変換は platform、port は app、手動 DI) |
| 2026-07-23 | Claude (spec-lifecycle) | clarify: D5 解決 (golangci-lint + depguard 採用)         |
| 2026-07-23 | Claude (spec-lifecycle) | clarify: D7 解決 (パイプライン段階 + 外部 lib 隔離)      |
| 2026-07-23 | Claude (spec-lifecycle) | clarify: D3 解決 (ArchUnit 採用)                         |

## 備考

- appendix (api / database / authorization / screen-spec / testid) はいずれも該当しないため取り込まない (ローカル CLI のリファクタリングで API / 永続層 / 権限 / 画面なし)
- 「機能仕様」以下の web 前提サブセクションは該当なしと明記の上で残置 (テンプレート必須節)
