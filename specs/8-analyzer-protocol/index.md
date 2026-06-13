# Analyzer Protocol / SPI feature spec

> Analyzer SPI、JSONL Communication Protocol、Model schema の作業 spec。
> durable な契約は `spec-sync` 実行後に feature doc / ADR へハンドオフする。

## メタ情報

- Issue: `#8`
- ステータス: `Draft`
- 作成日: 2026-06-13
- 更新日: 2026-06-13
- Branch: `feature/8`
- Owner: Fukuemon

## 設計フェーズ状況

状態は `未着手 / 進行中 / 完了 / レビュー済 / 保留` のいずれか。保留の場合は理由を備考に残す。

| #   | フェーズ                    | 状態   | 最終更新   | 備考                                     |
| --- | --------------------------- | ------ | ---------- | ---------------------------------------- |
| 1   | 起票                        | 完了   | 2026-06-13 | GitHub issue #8 を確認済み               |
| 2   | 下書き                      | 進行中 | 2026-06-13 | 本 spec を scaffold                      |
| 3   | 上位文書突合                | 完了   | 2026-06-13 | Design Doc / context / ADR と矛盾なし    |
| 4   | 論点整理                    | 完了   | 2026-06-13 | D1-D5 を初期論点として列挙               |
| 5   | 論点解決                    | 未着手 |            | `spec-resolve` で D1 から順に解決する    |
| 6   | Interface / Routing 設計    | 未着手 |            | Analyzer SPI / process interface を扱う  |
| 7   | Content / Data 設計         | 未着手 |            | Model schema / JSONL record を扱う       |
| 8   | Performance / Security 設計 | 未着手 |            | streaming / read-only / no external send |
| 9   | Test / Metrics 設計         | 未着手 |            | protocol contract test を扱う            |
| 10  | 実装分割                    | 未着手 |            | prompts 生成前に分割する                 |
| 11  | レビュー済                  | 未着手 |            | `spec-review` 未実施                     |

## 上位文書整合

正本 ([PRD](../../PRD.md) / [Design Doc](../../design/DesignDoc.md) / [feature doc](../../design/features/) / [context](../../context/) / ADR) のどの節と、どう整合させたかを記録する。

- PRD 更新要否: 不要 (本プロダクトは統合モード。Why / What は Design Doc に統合)
- Design Doc 更新要否: 不要 (draft 時点では既存方針の詳細化のみ)
- ADR 起票要否: 要 (Q1 / SPI / versioning 確定後に `spec-sync` で判断を昇格)

| 上位文書    | 節 / 該当箇所                                                                | 整合方針 (継承 / 補足 / 変更提案) |
| ----------- | ---------------------------------------------------------------------------- | --------------------------------- |
| PRD         | 統合モードのため `design/DesignDoc.md` の Why / What を参照                  | 継承                              |
| Design Doc  | Communication Protocol / モジュール責務 / 設計原則 P1-P4 / Open Questions Q1 | 補足                              |
| feature doc | `design/features/` は未作成。確定後に analyzer-protocol feature doc へ反映   | 補足                              |
| context     | `context/architecture.md` Package Boundary / Runtime Boundary                | 補足                              |
| context     | `context/testing.md` Protocol contract / test runtime contract               | 補足                              |
| context     | `context/toolchain.md` Analyzer との通信は JSONL over STDIN/STDOUT に固定    | 継承                              |
| context     | `context/infrastructure.md` CLI / CI 実行、外部送信なし、JSONL 観測可能      | 継承                              |
| ADR         | 既存 ADR なし。確定した Protocol / SPI 判断は ADR 候補                       | 補足                              |

> 現時点で上位文書との矛盾は検出していない。下流 phase で durable な契約が確定したら `spec-sync` で feature doc / ADR へ反映する。

## 関連資料

- `design/DesignDoc.md`: Communication Protocol、モジュール責務、設計原則 P1-P4、Open Questions Q1、Future Work Phase1 / Phase5
- `context/project.md`: 対象ドメイン `analyzer-protocol`、Issue Tracker、Source of Truth、Branch pattern
- `context/architecture.md`: Core -> Analyzer は Protocol 境界のみ、Core は Analyzer 内部を知らない
- `context/testing.md`: analyzer-protocol に Protocol contract test を置く
- `context/toolchain.md`: JSONL over STDIN/STDOUT 固定、Core 実装言語は未定
- `context/infrastructure.md`: CLI / CI 実行、外部インフラなし、外部送信なし
- `specs/8-analyzer-protocol/requirements.md`: 要求定義
- 関連 issue / ticket: [#8](https://github.com/Fukuemon/depwalk/issues/8)

## 背景

depwalk は Core を言語非依存に保ち、言語ごとの差異を独立プロセスの Analyzer に閉じ込める。Core と Analyzer の結合点は Analyzer SPI、STDIN / STDOUT 上の JSONL、`MethodSymbol` / `CallEdge` / `SourceLocation` である。

この spec は、全 Analyzer が実装する共通契約を issue #8 の作業正本として定義する。Phase1 では Java Analyzer がこの契約を最初に実装し、将来の Kotlin / TypeScript / Vue / Go Analyzer 追加時にも Core を変更しない状態を目指す。

## スコープ

### やること

- Model schema (`MethodSymbol` / `CallEdge` / `SourceLocation`) の JSONL record 形式を定義する
- Core から Analyzer へ渡す解析要求 record を定義する
- Analyzer から Core へ返す応答 record とエラー record を定義する
- Analyzer SPI の起動境界、終了条件、stderr / exit code の扱いを定義する
- schema versioning、未知フィールド、後方互換 / 前方互換の方針を定義する
- Protocol contract test の最小観点を定義する

### やらないこと

- Java 固有の AST 解析、型解決、DI 解決の方式を定義しない
- Graph Engine、Traversal Engine、Output Engine の内部データ構造を定義しない
- Console / DOT / Mermaid の出力表現を定義しない
- Core 実装言語や package manager を確定しない
- Reflection、AspectJ Runtime、実行時 Proxy の動的解析を扱わない

## 要件の解釈

### 実現したいユーザー価値

Core 開発者は、Analyzer の実装言語や内部ライブラリを知らずに、呼び出し関係を `MethodSymbol` / `CallEdge` として受け取れる。Analyzer 実装者は、準拠すべき JSONL record と SPI 境界を一意に参照できる。

### 成功条件

- Q1 の JSONL schema が確定している
- Analyzer SPI の起動、入出力、終了、エラー伝播の契約が確定している
- schema versioning と未知フィールドの扱いが確定している
- Protocol contract test により、Analyzer 実装が schema 準拠か判定できる
- 新しい Analyzer を追加しても Core の内部実装に差分が発生しない方針が説明できる

### 対象ユーザー / 操作主体

- Core 開発者
- Analyzer 実装者
- depwalk CLI を CI / ローカルで実行する開発者

EARS 風で振る舞いを記述する。

- WHEN Core が解析対象と解析範囲を Analyzer に渡す時、Analyzer SPI は schema version を含む解析要求 record を JSONL で送信する。
- WHEN Analyzer がメソッド定義を検出した時、Analyzer は `MethodSymbol` record を JSONL の 1 行として出力する。
- WHEN Analyzer が呼び出し関係を検出した時、Analyzer は caller と callee を参照する `CallEdge` record を JSONL の 1 行として出力する。
- IF Analyzer が schema に準拠しない JSONL を出力した時、Core は不準拠行を報告して解析を失敗として扱う。
- IF Analyzer が未知フィールドを含む JSONL record を出力した時、Core は既知フィールドを採用し、未知フィールドを無視する。
- IF Analyzer プロセスが非ゼロ終了した時、Core は exit code と stderr の要約を利用者に伝播する。
- THE SYSTEM SHALL keep Core independent from Analyzer implementation language and runtime.

## 設計時の論点

設計 / 実装フェーズへ持ち越す残課題を 1 件ずつ管理する。確定したものは「解決済みの論点」へ移す。

| #   | 論点                                                                  | 決定候補                                                                                                                        | 決定 |
| --- | --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- | ---- |
| D1  | `MethodSymbol` / `CallEdge` / `SourceLocation` の必須フィールドは何か | `schemaVersion`, `recordType`, stable id, language, signature, source location を最小核にする / Java 固有情報を拡張領域へ逃がす | 未決 |
| D2  | Core -> Analyzer の解析要求 record はどの粒度を持つか                 | repository root、include / exclude、target language、entry method selector、analysis mode を持つ                                | 未決 |
| D3  | Analyzer SPI のプロセス契約はどこまで定義するか                       | stdin/stdout JSONL、stderr diagnostics、exit code、timeout、capability handshake を定義する                                     | 未決 |
| D4  | versioning と互換性の単位をどうするか                                 | protocol version と schema version を同一にする / record 単位に version を持たせる                                              | 未決 |
| D5  | 不正 record、部分解析、未解決 symbol をどう表現するか                 | error / diagnostic record を定義し、致命的エラーと継続可能な警告を分ける                                                        | 未決 |

## 解決済みの論点

(`spec-resolve` で確定したものをここに移動する)

- なし

## 未確定事項

- D1-D5 が未決のため、下流 phase (diagram / tasks / prompts) には進まない。
- Core 実装言語、package manager、test framework は未確定。Protocol 契約は特定実装言語に依存しない形で定義する。

## 実装対象

正規 target は `context/project.md` の対象ドメイン一覧を正本とする。

| モジュール          | 実装有無 | 主な責務                                                                |
| ------------------- | :------: | ----------------------------------------------------------------------- |
| `traversal`         |    -     | 本 spec では Model の consumer として参照のみ                           |
| `output`            |    -     | 本 spec では JSON 出力 schema と混同しない                              |
| `analyzer-protocol` |    ◯     | Analyzer SPI、JSONL Communication Protocol、Model schema、contract test |
| `java-analyzer`     |    -     | 本 spec で定義した契約を実装する後続 feature                            |

## 機能仕様

### User Flow

1. Core は CLI から受け取った解析対象と解析範囲を Analyzer SPI に渡す。
2. Analyzer SPI は対象 Analyzer を独立プロセスとして起動し、解析要求 record を JSONL で stdin に送る。
3. Analyzer は対象ソースを read-only で解析し、`MethodSymbol` / `CallEdge` / diagnostic record を stdout に JSONL で出力する。
4. Core は stdout の各行を schema 検証し、Graph Engine が扱える Model として受領する。
5. Core は Analyzer の終了コードと stderr を確認し、成功 / 失敗 / 部分解析の結果を確定する。

### Reuse Policy

- Analyzer 固有の処理は各 Analyzer 実装に閉じ込める。
- Core が再利用してよいのは Analyzer SPI、Protocol parser / validator、Model schema のみとする。
- Java 固有フィールドは共通 Model の必須フィールドに昇格しない。必要な場合は optional metadata または language-specific extension として扱う。

### Performance

- JSONL は 1 行 1 record の streaming とし、Core は全出力を一括読み込みしない。
- 大規模コードベースを想定し、Analyzer は `MethodSymbol` / `CallEdge` を逐次出力できる契約にする。
- 具体的な runtime budget は Core 実装言語と Analyzer 実装後に測定値で確定する。

### Routing / URL State

- 非該当。depwalk は CLI ツールであり、Web routing / URL state を持たない。

### Content / Assets

- 非該当。静的 asset やコンテンツ配信は扱わない。

### UI Reuse

- 非該当。IDE Plugin / Web UI は Non Goals。

### Testing

- analyzer-protocol に Protocol contract test を置く。
- contract test は JSONL record の必須フィールド、未知フィールド、schema version、不正 JSONL、Analyzer error record を検証する。
- Java Analyzer はこの contract test に準拠する実装として検証する。

## Interface 設計

### UI / API / Event Interface

- UI: 非該当。
- Web API endpoint: 非該当。
- Process interface: Core は Analyzer を独立プロセスとして起動し、stdin / stdout / stderr / exit code を SPI 境界として扱う。
- Event interface: JSONL の各行を record event として扱う。

### Props / Request / Response

#### Core -> Analyzer request

| 項目            | 必須/任意 | 説明                                                 |
| --------------- | --------- | ---------------------------------------------------- |
| `schemaVersion` | 必須      | JSONL record の schema version                       |
| `recordType`    | 必須      | `analysisRequest`                                    |
| `requestId`     | 必須      | 解析要求を識別する ID                                |
| `workspaceRoot` | 必須      | 解析対象 repository root                             |
| `language`      | 必須      | 対象言語。Phase1 は `java`                           |
| `include`       | 任意      | 解析対象 path pattern                                |
| `exclude`       | 任意      | 除外 path pattern                                    |
| `entrypoint`    | 任意      | 起点 method selector。未指定時は全体 call graph 生成 |

#### Analyzer -> Core response

| record           | 必須/任意 | 説明                                                           |
| ---------------- | --------- | -------------------------------------------------------------- |
| `MethodSymbol`   | 必須      | メソッド同定情報。D1 で必須フィールドを確定する                |
| `CallEdge`       | 必須      | caller -> callee の呼び出し関係。D1 で参照形式を確定する       |
| `SourceLocation` | 任意      | source file / range。MethodSymbol または CallEdge から参照する |
| `Diagnostic`     | 任意      | 未解決 symbol、部分解析、警告、非致命的エラー                  |
| `Error`          | 任意      | 致命的エラー。出力後、Analyzer は非ゼロ終了する                |

## Content / Data 設計

### 保存・管理するデータ

- 永続データは持たない。
- Core は Analyzer から受け取った `MethodSymbol` / `CallEdge` / `SourceLocation` をプロセス内の Graph Engine へ渡す。
- JSONL schema と contract test fixture は repository 内の実装対象として管理する。

### コンテンツ配置 / package / route

- package 配置は Core 実装言語確定後に決める。
- spec 上の分割単位は `analyzer-protocol` とする。
- route は非該当。

## Performance / Security 設計

### Performance

- JSONL は streaming 前提とし、Analyzer は解析結果を逐次出力できる。
- Core は stdout の各行を逐次 parse / validate する。
- Analyzer の timeout、最大 stderr サイズ、最大 record サイズは D3 または後続論点で確定する。

### Security / Privacy

- Analyzer は対象ソースを read-only で読む。
- Core / Analyzer は解析対象ソースや解析結果を外部送信しない。
- secret / token は不要。
- Analyzer 実行は利用者が指定したローカルまたは CI 環境内で完結する。

## Error / Fallback 設計

### エラーケース

| #   | ケース                                      | ユーザーへの見せ方                                         | リカバリ                                       |
| --- | ------------------------------------------- | ---------------------------------------------------------- | ---------------------------------------------- |
| 1   | Analyzer が不正 JSONL を出力する            | 不正行の record type / 行番号 / parse error を報告して失敗 | Analyzer 実装を contract test で修正する       |
| 2   | Analyzer が schema 不準拠 record を出力する | 不足フィールドまたは型不一致を報告して失敗                 | schema version と必須フィールドを修正する      |
| 3   | Analyzer が非ゼロ終了する                   | exit code と stderr 要約を表示して失敗                     | Analyzer stderr をもとに原因を修正する         |
| 4   | Analyzer が未知フィールドを出力する         | 既知フィールドを採用し、未知フィールドは無視する           | schema version policy に従い後続で採用判断する |
| 5   | 型解決不能などの部分解析が発生する          | diagnostic record として警告を残し、継続可否を判定する     | Analyzer 側の解析精度を改善する                |

### Fallback

- schema 準拠の既知フィールドが揃っている場合、Core は未知フィールドを無視して処理を継続する。
- 致命的な schema 不準拠、不正 JSONL、Analyzer 非ゼロ終了は失敗として扱う。
- 部分解析は diagnostic record として表現し、Core が利用者に観測可能な形で伝播する。

## テスト / 評価方針

### テスト観点

- valid `analysisRequest` record を Analyzer が受け取れること
- valid `MethodSymbol` / `CallEdge` / `SourceLocation` record を Core が parse / validate できること
- 未知フィールドを含む record で既知フィールドを採用できること
- schema 不準拠 record を拒否できること
- 不正 JSONL を parse error として報告できること
- Analyzer の非ゼロ終了と stderr を Core が伝播できること
- Java Analyzer が protocol contract test を通過できること

### 計測指標

- contract test pass rate
- schema validation error count
- Analyzer process exit status
- JSONL records processed per second (実装後に計測)
- peak memory while streaming parse (実装後に計測)

## フロー / シーケンス

(`spec-diagrams` で生成。spec の主要操作を Mermaid 図に落とす)

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

| Phase | 対象                | 概要                                                          | 依存   |
| ----- | ------------------- | ------------------------------------------------------------- | ------ |
| P1    | `analyzer-protocol` | schema の record 種別と必須フィールドを定義する               | D1     |
| P2    | `analyzer-protocol` | Core -> Analyzer request と process SPI を定義する            | D2, D3 |
| P3    | `analyzer-protocol` | versioning / unknown field / error policy を定義する          | D4, D5 |
| P4    | `analyzer-protocol` | Protocol contract test の fixture と期待結果を定義する        | P1-P3  |
| P5    | `java-analyzer`     | Java Analyzer が protocol を実装するための handoff を整理する | P1-P4  |

### prompts 生成方針

- prompts は `analyzer-protocol` を中心に生成する。
- `java-analyzer` 用 prompt は contract 確定後に別 spec または後続 prompt へ分ける。
- `traversal` / `output` は consumer として Model を参照するため、本 spec の prompts では実装対象にしない。

## 上位資料からの変更点

本 spec で PRD / Design Doc / feature doc / context / 既存 ADR から変更・追加した内容を、反映先別に記録する。`spec-track` / `spec-sync` で更新する。

### PRD への影響

| 対象節 | 変更内容                                                   | 理由                                   |
| ------ | ---------------------------------------------------------- | -------------------------------------- |
| なし   | 現時点では Design Doc 統合モードの Why / What を変更しない | 既存の S5 / P1-P4 の詳細化で足りるため |

### Design Doc への影響

| 対象節                 | 変更内容                                                              | 理由                                                                      |
| ---------------------- | --------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| Communication Protocol | Q1 解決後、具体 schema / SPI 方針への正本リンクを追加する可能性がある | Design Doc には landscape だけを残し、詳細は feature doc / ADR に移すため |

### feature doc への影響

| 対象 doc / 節                                                      | 変更内容                                             | 理由                                          |
| ------------------------------------------------------------------ | ---------------------------------------------------- | --------------------------------------------- |
| `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md` | Q1 / SPI / versioning 確定後に新規作成または反映する | durable な契約は feature doc を正本にするため |

### context への影響

| 対象 doc / 節             | 変更内容                                         | 理由                                       |
| ------------------------- | ------------------------------------------------ | ------------------------------------------ |
| `context/architecture.md` | 現時点では変更なし                               | 既存の Protocol 境界方針と整合しているため |
| `context/testing.md`      | contract test の詳細確定後に追記する可能性がある | 横断テスト規約として残す場合があるため     |

### ADR の新規 / 更新

| ADR ID | 変更内容                                                                                               | 理由                                   |
| ------ | ------------------------------------------------------------------------------------------------------ | -------------------------------------- |
| 未採番 | Analyzer 通信を JSONL over STDIN/STDOUT とする判断、SPI 境界、versioning 方針を ADR 化する可能性がある | 長期参照価値のあるアーキ判断になるため |

## レビュー

`spec-review` (fresh-context evaluator) の最新結果。完全な記録は `review.md` を参照。

| 日付 | 結果 (PASS / NEEDS_WORK) | 指摘要点 | 対応 |
| ---- | ------------------------ | -------- | ---- |
|      |                          |          |      |

## 変更履歴

| 日付       | 変更者 | 変更内容               |
| ---------- | ------ | ---------------------- |
| 2026-06-13 | Codex  | 初版 draft spec を追加 |

## 備考

- Appendix は追加しない。Web API endpoint、永続データ層、ロール / 権限、画面、E2E 対象 UI が本 spec の直接スコープにないため。
