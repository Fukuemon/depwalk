# Feature 設計: Analyzer Protocol / SPI

> 最終更新: 2026-06-15 / Status: 完了

Analyzer SPI、JSONL Communication Protocol、Model schema の durable な feature 設計正本。本 doc は Protocol / SPI / Model の正本であり、決定経緯と issue 単位の作業記録は [spec #8](../../../specs/8-analyzer-protocol/) を参照する。

## メタ

| 項目           | 値 |
| -------------- | -- |
| 関連 PRD 要求  | 統合モードのため [DesignDoc の Why / What](../../DesignDoc.md#why--what) |
| 関連 DesignDoc | [Communication Protocol](../../DesignDoc.md#communication-protocol)、[モジュール責務](../../DesignDoc.md#モジュール責務)、[設計原則](../../DesignDoc.md#設計原則-design-principles) |
| 関連 context   | [architecture](../../../context/architecture.md)、[testing](../../../context/testing.md)、[toolchain](../../../context/toolchain.md)、[infrastructure](../../../context/infrastructure.md) |
| 関連 ADR       | [ADR-0001](../../../adr/0001-analyzer-protocol-jsonl-spi.md) |
| 関連 spec      | [specs/8-analyzer-protocol](../../../specs/8-analyzer-protocol/) |
| 対象モジュール | `analyzer-protocol` |

## 背景・要件解釈

depwalk は Core を言語非依存に保ち、言語ごとの差異を独立プロセスの Analyzer に閉じ込める。Analyzer Protocol / SPI は Core と Analyzer の唯一の結合点であり、`MethodSymbol` / `CallEdge` / `SourceLocation`、`diagnostic` / `error`、および process contract を定義する。

本 feature は Design Doc の成功条件 S5「新しい言語の Analyzer を追加するとき Core を変更せずに済む」を満たすため、Analyzer 実装者が準拠すべき共通契約を提供する。

## スコープ

### やること

- Core から Analyzer へ渡す `analysisRequest` record を定義する。
- Analyzer から Core へ返す `methodSymbol` / `callEdge` / `diagnostic` / `error` record を定義する。
- `SourceLocation` value object、`methodId` / `edgeId` の安定性、`metadata` の扱いを定義する。
- Analyzer process の起動、stdin / stdout / stderr、exit code の契約を定義する。
- `schemaVersion`、未知 field、breaking change の互換性方針を定義する。
- Protocol contract test の正本観点を定義する。

### やらないこと

- Java 固有の AST 解析、型解決、DI 解決の方式は定義しない。
- Graph Engine、Traversal Engine、Output Engine の内部構造は定義しない。
- Console / DOT / Mermaid の出力表現は定義しない。
- Core 実装言語、package manager、test framework は定義しない。
- Reflection、AspectJ Runtime、実行時 Proxy の動的解析は扱わない。

## 設計

### データ構造 / コンテンツモデル

Protocol は STDIN / STDOUT 上の JSONL とし、1 行を 1 record として扱う。全 record は `schemaVersion` と `recordType` を必須 field に持つ。Phase1 の `schemaVersion` は `"1"` とする。

#### Core -> Analyzer

`analysisRequest` は Core が Analyzer process 起動後に stdin へ 1 件だけ送信する解析要求である。送信後、Core は stdin を close する。

| field | 必須/任意 | 説明 |
| ----- | --------- | ---- |
| `schemaVersion` | 必須 | Protocol version。Phase1 は `"1"` |
| `recordType` | 必須 | `analysisRequest` |
| `requestId` | 必須 | 解析要求を識別する ID |
| `workspaceRoot` | 必須 | 解析対象 repository root |
| `language` | 必須 | 対象言語。Phase1 は `java` |
| `include` | 任意 | `workspaceRoot` からの相対 path glob 配列 |
| `exclude` | 任意 | `workspaceRoot` からの相対除外 path glob 配列 |
| `entrypoints` | 任意 | 起点 method selector 配列 |
| `analysisMode` | 任意 | `fullGraph` または `reachableFromEntrypoints`。未指定時は `fullGraph` |
| `metadata` | 任意 | 言語固有または Analyzer 固有の hint。Core の共通処理は依存しない |

`include` / `exclude` は `workspaceRoot` からの相対 path glob とする。path separator は `/` に正規化し、絶対 path、空文字、`..` を含む path は schema 不準拠として扱う。対応する glob は `*`、`?`、`**` とする。

`entrypoints` の各要素は method selector object とし、`qualifiedName` を必須、`signature` を任意にする。`entrypoints` が未指定または空配列の場合、Analyzer は scope 全体の call graph 生成要求として扱う。

#### Analyzer -> Core

| record | Core 対応 | 出現条件 / 説明 |
| ------ | --------- | --------------- |
| `methodSymbol` | 必須 | graph node として扱う method / constructor / function が検出された場合 |
| `callEdge` | 必須 | 解決済み caller / callee の呼び出し関係が検出された場合 |
| `diagnostic` | 必須 | 未解決 symbol、部分解析、警告、非致命的エラーがある場合 |
| `error` | 必須 | 致命的エラー時。出力後、Analyzer は非ゼロ終了する |

`Core 対応 = 必須` は Core parser / validator がその record type を実装するという意味であり、すべての解析結果にその record が 1 件以上出ることを意味しない。

#### `methodSymbol`

| field | 必須/任意 | 説明 |
| ----- | --------- | ---- |
| `schemaVersion` | 必須 | Protocol version |
| `recordType` | 必須 | `methodSymbol` |
| `methodId` | 必須 | Analyzer が決定的に生成する stable ID |
| `language` | 必須 | 対象言語。Phase1 は `java` |
| `symbolKind` | 必須 | `method` / `constructor` / `function` / `initializer` |
| `qualifiedName` | 必須 | 表示・debug 用の完全修飾名 |
| `signature` | 必須 | overload を区別できる正規化済み signature |
| `sourceLocation` | 任意 | 定義位置。位置を持てない symbol では省略できる |
| `metadata` | 任意 | 言語固有情報。Core の graph 構築は依存しない |

`methodId` は、同一 Analyzer 実装 version、同一 `analysisRequest`、同一 source content、同一 `qualifiedName` / `signature` に対して決定的に再生成できる ID とする。Analyzer version をまたぐ永続 ID は要求しない。

#### `callEdge`

| field | 必須/任意 | 説明 |
| ----- | --------- | ---- |
| `schemaVersion` | 必須 | Protocol version |
| `recordType` | 必須 | `callEdge` |
| `edgeId` | 必須 | Analyzer が決定的に生成する stable ID |
| `callerMethodId` | 必須 | 呼び出し元の `methodSymbol.methodId` |
| `calleeMethodId` | 必須 | 呼び出し先の `methodSymbol.methodId` |
| `callSite` | 任意 | 呼び出し式の source 位置 |
| `metadata` | 任意 | dispatch 種別、解析 confidence、言語固有 call kind など |

valid な `callEdge` は、`callerMethodId` と `calleeMethodId` が解決済み `methodSymbol` を参照する。未解決 symbol は `diagnostic` として表現する。

#### `SourceLocation`

`SourceLocation` は独立 JSONL record ではなく、`methodSymbol.sourceLocation` または `callEdge.callSite` に埋め込む value object とする。

| field | 必須/任意 | 説明 |
| ----- | --------- | ---- |
| `path` | 必須 | `workspaceRoot` からの相対 path |
| `startLine` | 必須 | 1-based の開始行 |
| `startColumn` | 任意 | 1-based の開始 column |
| `endLine` | 任意 | 1-based の終了行 |
| `endColumn` | 任意 | 1-based の終了 column |

#### `diagnostic` / `error`

`diagnostic` は継続可能な問題や部分解析情報を表す。Core は利用者へ伝播するが、`diagnostic` だけを理由に解析全体を fatal failure として扱わない。

`error` は Analyzer が解析を継続できない致命的な問題を表す。Analyzer が `error` を出力した場合、Analyzer process は非ゼロ exit code で終了する。

| record | 必須 field | 任意 field |
| ------ | ---------- | ---------- |
| `diagnostic` | `schemaVersion`, `recordType`, `severity`, `code`, `message` | `sourceLocation`, `relatedMethodId`, `metadata` |
| `error` | `schemaVersion`, `recordType`, `code`, `message` | `sourceLocation`, `metadata` |

`diagnostic.severity` は `info` / `warning` / `partialFailure` とする。不正 JSONL、schema 不準拠、未対応 `schemaVersion` は Analyzer が表現する `error` ではなく、Core 側 validation error として扱う。

### 画面・デザイン

非該当。depwalk は CLI ツールであり、本 feature は Web UI / IDE Plugin を提供しない。

### コンポーネント構成 (C4 L3)

```mermaid
flowchart TD
    CLI["CLI"] --> Core["Core"]
    Core --> SPI["Analyzer SPI"]
    SPI --> Request["analysisRequest writer"]
    SPI --> Validator["JSONL parser / validator"]
    SPI --> Process["Analyzer process"]
    Process --> Stdout["stdout JSONL records"]
    Process --> Stderr["stderr diagnostics"]
    Validator --> Model["MethodSymbol / CallEdge"]
    Validator --> Diagnostics["diagnostic / error"]
    Model --> Graph["Graph Engine"]
```

Core は Analyzer process を起動し、Protocol parser / validator を通じて Model と diagnostics だけを受領する。Analyzer の内部ライブラリや言語ランタイムには依存しない。

### フロー / シーケンス

```mermaid
flowchart TD
    A["Core が analysisRequest を生成"] --> B{"request schema は有効か"}
    B -- "No" --> C["schema error を返す"]
    B -- "Yes" --> D["Analyzer process を起動"]
    D --> E["stdin に analysisRequest を送信して close"]
    E --> F["Analyzer が read-only 解析"]
    F --> G["stdout に JSONL record を逐次出力"]
    G --> H{"parse / schema validation"}
    H -- "methodSymbol / callEdge" --> I["Graph Engine へ渡す"]
    H -- "diagnostic" --> J["利用者へ伝播し解析は継続可能"]
    H -- "error" --> K["fatal failure"]
    H -- "invalid" --> L["Core validation error"]
```

### Versioning / compatibility

`schemaVersion` は record 種別ごとの個別 version ではなく、protocol 全体の version を表す。Core は対応済み major version の record だけを受け付ける。未対応 major version の record を受け取った場合、Core は schema version mismatch として解析を失敗させる。

| 変更種別 | 互換性 | 扱い |
| -------- | ------ | ---- |
| 任意 field の追加 | 互換 | Core は未知 field を無視し、既知 field だけで処理を継続する |
| `metadata` 内の追加 | 互換 | Core の graph 構築は `metadata` に依存しない |
| 必須 field の追加 | 非互換 | major version bump の対象 |
| 必須 field の削除 | 非互換 | major version bump の対象 |
| field 型の変更 | 非互換 | major version bump の対象 |
| field 意味論の変更 | 非互換 | major version bump の対象 |
| record type の削除 | 非互換 | major version bump の対象 |

Handshake / capability negotiation は Phase1 では採用しない。

## 主要シナリオ / フロー

- Core が CLI 入力を `analysisRequest` に正規化し、Analyzer process に 1 件だけ送信する。
- Analyzer が `methodSymbol` / `callEdge` を stdout JSONL として逐次出力し、Core が逐次 parse / validate する。
- Analyzer が未解決 symbol や部分解析を `diagnostic` として出力し、Core が利用者へ観測可能に伝播する。
- Analyzer が継続不能な問題を `error` として出力し、非ゼロ exit code で終了する。
- Core が未知 field を無視し、対応済み major version の既知 field だけで処理を継続する。

## テスト観点

横断規約は [context/testing.md](../../../context/testing.md) を正本とする。本 feature の contract test は少なくとも以下を検証する。

- valid `analysisRequest` record を Analyzer が受け取れること。
- `include` / `exclude` が相対 path glob として扱われ、絶対 path、空文字、`..` を含む path が拒否されること。
- `entrypoints` の method selector が `qualifiedName` 必須、`signature` 任意として検証されること。
- Core が `analysisRequest` 送信後に stdin を close すること。
- Analyzer stdout の JSONL record が逐次 parse / validate されること。
- Analyzer stderr が protocol record として parse されないこと。
- exit code `0` を成功、非ゼロを fatal failure として扱うこと。
- `methodSymbol` / `callEdge` が 0 件の正常解析を success として扱えること。
- `methodId` / `edgeId` が同一条件で決定的に再生成されること。
- 対応済み major version の未知 field を Core が無視できること。
- 未対応 major version の record を Core が schema version mismatch として拒否できること。
- valid `diagnostic` を Core が利用者へ伝播し、`diagnostic` だけを理由に fatal failure としないこと。
- valid `error` を Core が fatal failure として扱うこと。
- 未解決 symbol が `diagnostic` として表現され、未解決 callee を参照する `callEdge` が valid edge として扱われないこと。

## 上位資料からの変更点

| 対象資料  | 変更種別 (継承 / 追記 / 変更提案) | 内容 |
| --------- | --------------------------------- | ---- |
| PRD       | 継承 | 統合 Design Doc の S5 / P1-P4 を具体化する。 |
| DesignDoc | 追記 | Analyzer Protocol / SPI feature の正本を本 doc に移す。 |
| context   | 追記 | protocol contract test の横断観点を `context/testing.md` に反映する。 |
| ADR       | 追記 | JSONL over STDIN/STDOUT、process SPI、versioning 方針を ADR-0001 に記録する。 |
