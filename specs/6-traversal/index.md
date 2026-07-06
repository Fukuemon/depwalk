# Traversal (Caller / Callee 探索) spec

> issue #6 の設計 spec。
> Traversal Engine は Graph Engine が保持する `MethodSymbol` / `CallEdge` 相当のグラフを入力に、caller / callee 方向の到達集合を返す。Model schema の正本は Analyzer Protocol / SPI feature doc、Core package 境界の正本は Design Doc / context / ADR-0002 とする。

## メタ情報

- Issue: `#6`
- ステータス: `Draft`
- 作成日: 2026-07-07
- 更新日: 2026-07-07
- Branch: `feature/6`
- Owner: Fukuemon

## 設計フェーズ状況

状態は `未着手 / 進行中 / 完了 / レビュー済 / 保留` のいずれか。保留の場合は理由を備考に残す。

| #   | フェーズ                    | 状態   | 最終更新   | 備考                                                      |
| --- | --------------------------- | ------ | ---------- | --------------------------------------------------------- |
| 1   | 起票                        | 完了   | 2026-06-10 | GitHub issue #6 を確認済み                                |
| 2   | 下書き                      | 完了   | 2026-07-07 | requirements から本 spec を scaffold                      |
| 3   | 上位文書突合                | 完了   | 2026-07-07 | Design Doc / context / ADR / analyzer-protocol と矛盾なし |
| 4   | 論点整理                    | 完了   | 2026-07-07 | D1-D5 を初期論点として列挙                                |
| 5   | 論点解決                    | 完了   | 2026-07-07 | D1-D5 を解決済み                                          |
| 6   | Interface / Routing 設計    | 完了   | 2026-07-07 | Traversal request / result の主要 option を決定           |
| 7   | Content / Data 設計         | 完了   | 2026-07-07 | 探索結果モデルは到達 node 集合 + edge 集合として確定      |
| 8   | Performance / Security 設計 | 進行中 | 2026-07-07 | 深さ上限未指定時は無制限。大規模 graph budget は後続計測  |
| 9   | Test / Metrics 設計         | 未着手 |            | unit / E2E fixture 観点を具体化する                       |
| 10  | 実装分割                    | 未着手 |            | Graph / traversal / test の prompts 分割を決める          |
| 11  | レビュー済                  | 未着手 |            | `spec-review` 後に更新                                    |

## 上位文書整合

正本 ([PRD](../../PRD.md) / [Design Doc](../../design/DesignDoc.md) / [feature doc](../../design/features/) / [context](../../context/) / ADR) のどの節と、どう整合させたかを記録する。

- PRD 更新要否: 不要 (本プロダクトは統合モード。Why / What は Design Doc に統合)
- Design Doc 更新要否: 未定 (Q4 解決後、循環 / 深さ上限の durable な設計だけ反映要否を判定)
- ADR 起票要否: 不要 (現時点では既存の Core package 境界と Protocol 判断の範囲内)

| 上位文書    | 節 / 該当箇所                                                                                                        | 整合方針 (継承 / 補足 / 変更提案) |
| ----------- | -------------------------------------------------------------------------------------------------------------------- | --------------------------------- |
| PRD         | 統合モードのため `design/DesignDoc.md` の Why / What を参照                                                          | 継承                              |
| Design Doc  | 成功条件 S1 / S2、Goal G1 / G2、モジュール責務 Traversal Engine、Open Question Q4                                    | 継承                              |
| feature doc | `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md` の `MethodSymbol` / `CallEdge` / `SourceLocation` | 継承                              |
| context     | `context/architecture.md` Package Boundary (`Traversal Engine` -> `Graph Engine` -> `Model`)                         | 継承                              |
| context     | `context/testing.md` E2E 照合、探索打ち切り (Q4) の unit test                                                        | 継承                              |
| context     | `context/toolchain.md` Go 標準 library / Go 標準 `testing`                                                           | 継承                              |
| context     | `context/engineering.md` Repository Quality Gate / 依存境界 gate                                                     | 継承                              |
| ADR         | `adr/0001-analyzer-protocol-jsonl-spi.md`                                                                            | 継承                              |
| ADR         | `adr/0002-core-implementation-foundation.md`                                                                         | 継承                              |

> 現時点で上位文書との矛盾は検出していない。本 spec は Design Doc の Q4 を issue #6 の作業正本として解く。

## 関連資料

- `design/DesignDoc.md`: S1 / S2、G1 / G2、Traversal Engine、Graph Engine、Q4
- `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md`: `MethodSymbol` / `CallEdge` / `SourceLocation` の正本
- `context/architecture.md`: Traversal Engine の package boundary と依存方向
- `context/testing.md`: Traversal unit test、S1 / S2 E2E 照合
- `context/toolchain.md`: Go 標準 library / `testing` 方針
- `adr/0001-analyzer-protocol-jsonl-spi.md`: Analyzer Protocol / Model 境界の判断
- `adr/0002-core-implementation-foundation.md`: Go Core package 境界
- `specs/6-traversal/requirements.md`: issue #6 の要求定義 draft
- `specs/12-analyzer-protocol-implementation/`: Protocol DTO / parser / contract fixture / analyzer runner 実装記録
- 関連 issue / ticket: [#6](https://github.com/Fukuemon/depwalk/issues/6), [#7](https://github.com/Fukuemon/depwalk/issues/7), [#9](https://github.com/Fukuemon/depwalk/issues/9), [#12](https://github.com/Fukuemon/depwalk/issues/12)

## 背景

depwalk の Phase1 は、指定メソッドの caller / callee を探索し、既知の呼び出し関係集合と一致する結果を返すことを成功条件にしている。Analyzer Protocol / SPI は #12 で Go Core 側に実装済みであり、Core は Analyzer から `methodSymbol` / `callEdge` を受け取れる境界を持った。

次に必要なのは、Graph Engine が構築した呼び出しグラフを入力として、Traversal Engine が caller / callee 方向へ到達集合を計算する設計である。本 spec は Design Doc の Open Question Q4「循環呼び出し・再帰の探索打ち切り条件」を解き、探索 API、探索結果モデル、テスト観点、実装分割を確定する。

## スコープ

### やること

- caller 方向の再帰探索を設計する。
- callee 方向の探索を設計する。
- 探索方向、起点メソッド、深さ上限、探索順序を受け取る Traversal API を設計する。
- 循環呼び出し / 再帰 / 深さ上限到達を検出し、探索結果へ観測可能に含める方針を決める。
- Graph Engine との入力境界を設計する。
- Traversal unit test と S1 / S2 E2E 照合の責務を分ける。
- 実装 prompt へ分割できる粒度まで package / test / fixture 境界を整理する。

### やらないこと

- Java ソースの解析、型解決、DI 解決は行わない。これは `java-analyzer` の責務である。
- Analyzer Protocol / SPI / Model schema は再定義しない。正本は analyzer-protocol feature doc と ADR-0001 である。
- Output Engine の Console / JSON / DOT / Mermaid 表現は決めない。Traversal は出力に必要な構造と打ち切り情報を渡せる形にする。
- CLI `depwalk analyze` の引数、exit code、エラー表示は決めない。CLI interface spec の対象とする。
- 永続ストア、キャッシュ、並列探索、分散処理は扱わない。

## 要件の解釈

### 実現したいユーザー価値

開発者は、改修対象メソッドを起点に「どこから呼ばれているか」と「何を呼んでいるか」を再帰的に確認できる。CI は同じ探索を自動実行し、既知の caller / callee 集合とのずれを検出できる。

### 成功条件

- 指定した起点メソッドから caller 方向の到達集合を列挙できる。
- 指定した起点メソッドから callee 方向の到達集合を列挙できる。
- 循環呼び出し / 再帰を検出して無限ループしない。
- 深さ上限に到達したノードを探索結果で区別できる。
- 起点メソッドがグラフに存在しない場合、panic ではなく「該当なし」を表す結果を返せる。
- `cd core && go test ./...` で Traversal unit test を実行できる。
- E2E fixture では S1 / S2 の既知集合と CLI 出力の一致を検証できる。

### 対象ユーザー / 操作主体

- Core 開発者
- depwalk CLI を local / CI で利用する開発者
- Output Engine / CLI interface の後続実装者

EARS 風で振る舞いを記述する。

- WHEN Core が caller 探索を実行する時、システムは起点メソッドへ到達する呼び出し元を探索方向に従って列挙する。
- WHEN Core が callee 探索を実行する時、システムは起点メソッドから到達する呼び出し先を探索方向に従って列挙する。
- IF グラフに循環または再帰が含まれる時、システムは訪問済みノードを管理し、無限ループせず循環を結果に標識する。
- IF 深さ上限に到達した時、システムはそれ以降の探索を打ち切り、打ち切り理由を結果に保持する。
- IF 起点メソッドがグラフに存在しない時、システムは空の探索結果と起点不在の状態を返す。
- THE SYSTEM SHALL keep Traversal Engine independent from Analyzer implementation language and Output format.

## 設計時の論点

設計 / 実装フェーズへ持ち越す残課題を 1 件ずつ管理する。確定したものは「解決済みの論点」へ移す。

| #   | 論点                                     | 決定候補                                                                                                                         | 決定                                                                                     |
| --- | ---------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| D1  | 探索順序を BFS / DFS のどちらにするか    | A: BFS を既定にする / B: DFS を既定にする / C: API で選択可能にし既定を固定する                                                  | C: API で選択可能にし、既定は BFS とする                                                 |
| D2  | Q4: 循環呼び出し・再帰の探索打ち切り条件 | A: 訪問済み node で再訪を抑止し循環を標識 / B: path 単位で循環を検出し同一 node の別 path 再訪を許す / C: 深さ上限のみで制御する | A: 訪問済み node で再訪を抑止し、再訪 edge を `cycle` cutoff として記録する              |
| D3  | 深さ上限の扱い                           | A: 未指定は無制限 / B: 未指定時に安全な既定値を置く / C: CLI interface で必須にする                                              | A: 未指定は無制限とし、指定された場合だけ depth limit cutoff を記録する                  |
| D4  | 探索結果モデルの形                       | A: 到達 node 集合 + edge 集合 / B: 起点からの traversal tree / C: graph view と tree view の両方を保持                           | A: 到達 node 集合 + edge 集合を基本結果とし、tree 表現は Output 側で必要に応じて構築する |
| D5  | 起点メソッド不在の扱い                   | A: 空結果 + status で返す / B: validation error として返す / C: Output Engine でのみ表現する                                     | A: 空結果 + `startNotFound` status で返す                                                |

## 解決済みの論点

- #8 / #12: Traversal が参照する `MethodSymbol` / `CallEdge` / `SourceLocation` の schema と Go 側 Protocol 実装は確定済み。正本は analyzer-protocol feature doc、ADR-0001、#12 実装である。
- #11: Core 実装基盤と package 境界は確定済み。Traversal 実装は `core/internal/traversal`、Graph 実装は `core/internal/graph` に置く。
- #6 D1: 探索順序は Traversal API の option として選択可能にし、未指定時の既定は BFS とする。影響範囲調査では起点から近い caller / callee を先に確認できることが重要で、深さ上限とも整合しやすいため。DFS は debug や tree 表現の都合で必要になった場合に選択できる余地を残す。
- #6 D2: 循環呼び出し / 再帰は、訪問済み node への再訪を抑止し、再訪を発生させた edge を `cycle` cutoff として記録する。#6 の目的は全経路列挙ではなく caller / callee の到達集合を安定して列挙することであり、同一 node の別 path 再展開を許すと結果量と test matrix が膨らむため。深さ上限だけで制御する案は循環を観測可能にできないため採用しない。
- #6 D3: 深さ上限は任意 option とし、未指定時は無制限に探索する。D2 で訪問済み node の再展開を抑止するため、循環による無限ループは深さ上限なしでも防げる。Phase1 の成功条件は caller / callee 到達集合の網羅列挙であり、既定値で探索を切ると取りこぼしが発生するため、必要な利用者だけ明示的に上限を指定する。
- #6 D4: 探索結果モデルは到達 node 集合 + edge 集合を基本とする。Traversal は全経路列挙や Output 固有 tree 表現を責務にしないため、起点からの traversal tree は保持しない。Console などで tree 表現が必要な場合は、#7 Output 側で Traversal 結果を入力に変換する。
- #6 D5: 起点メソッドが graph に存在しない場合は、validation error ではなく空結果 + `startNotFound` status を返す。起点不在は解析処理の破綻ではなく「該当なし」を表す正常な探索結果として扱えるため。Output Engine は status を参照して利用者向けに表示する。

## 未確定事項

| 未確定事項 | 候補 / 確認方法 | 決定者 | 期限 | 下流への影響                                 |
| ---------- | --------------- | ------ | ---- | -------------------------------------------- |
| なし       | -               | -      | -    | D1-D5 は解決済み。実装分割と review へ進める |

## 実装対象

正規 target は `context/project.md` の対象ドメイン一覧を正本とする。

| モジュール          | 実装有無 | 主な責務                                                    |
| ------------------- | :------: | ----------------------------------------------------------- |
| `core`              |    ◯     | Graph / Traversal package 境界、use case からの呼び出し口   |
| `traversal`         |    ◯     | caller / callee 探索、循環 / 深さ上限の扱い、探索結果モデル |
| `output`            |    -     | #6 では Traversal 結果の consumer として参照のみ            |
| `analyzer-protocol` |    -     | `MethodSymbol` / `CallEdge` schema の正本として参照のみ     |
| `java-analyzer`     |    -     | #6 では graph 入力を生成する上流として参照のみ              |

## 機能仕様

### User Flow

1. Core は Analyzer から受け取った `methodSymbol` / `callEdge` を Graph Engine に渡す。
2. Graph Engine は node / edge を保持し、Traversal Engine が参照できる graph view を提供する。
3. 呼び出し側は起点メソッド、探索方向、深さ上限、探索順序を指定して Traversal Engine を呼び出す。探索順序を未指定にした場合、Traversal Engine は BFS として扱う。深さ上限を未指定にした場合、Traversal Engine は到達可能な node を無制限に探索する。
4. 起点メソッドが graph に存在しない場合、Traversal Engine は空の到達集合と `startNotFound` status を返す。
5. Traversal Engine は caller または callee 方向に graph を辿り、到達 node 集合、到達 edge 集合、打ち切り情報を返す。訪問済み node に再到達した場合は、その node を再展開せず `cycle` cutoff を記録する。
6. Output Engine は Traversal 結果を受け取り、Console / JSON / DOT / Mermaid の各形式へ変換する。Console tree が必要な場合も、tree 構築は Output 側で行う。

### Reuse Policy

- Traversal Engine は `core/internal/traversal` に閉じる。
- Graph の node / edge 管理は `core/internal/graph` に閉じ、Traversal は graph が公開する読み取り API 経由で探索する。
- Analyzer 固有情報や Java 固有 metadata を Traversal の分岐条件にしない。
- Traversal は全経路列挙を責務にしない。到達 node 集合、到達 edge 集合、再訪を発生させた edge の cutoff 情報を返す。
- 起点メソッド不在は Traversal result の status として返す。Output Engine だけに判断を委ねず、Traversal から意味を渡す。
- Output format 固有の tree 表現は Traversal に持ち込まない。ただし循環 / 深さ上限 / 起点不在など、出力に必要な状態は Traversal 結果として保持する。

### Performance

- 探索は graph 全体の再構築を伴わず、Graph Engine が保持する adjacency を読む。
- 探索順序は BFS / DFS を option として受け取る。未指定時は BFS とし、起点から近い node を先に返す。
- 訪問済み node は再展開しない。再訪を発生させた edge は `cycle` cutoff として保持し、循環で無限ループしない。
- 深さ上限は任意 option として受け取る。未指定時は無制限とし、指定時だけ上限到達 node を `depthLimit` cutoff として保持する。
- 大規模 graph の runtime budget は実装後の fixture 計測で候補値を出し、CLI interface または performance spec で確定する。

### Routing / URL State

- 非該当。depwalk は CLI ツールであり、Web routing / URL state を持たない。

### Content / Assets

- 永続コンテンツや静的 asset は扱わない。
- E2E fixture は `testdata/fixtures/` に置く。具体的なサンプル Java/Spring repo は Java Analyzer / CLI interface の spec と同期して決める。

### UI Reuse

- 非該当。IDE Plugin / Web UI は Non Goals。

### Testing

- Traversal unit test は `core/internal/traversal` に置く。
- Graph fixture / builder は `core/internal/graph` の公開 API を通じて組み立てる。
- 探索結果モデルの unit test は、到達 node 集合、到達 edge 集合、cutoff 情報が期待どおりに返ることを検証する。tree 表現の生成は #7 Output の test 対象とする。
- 探索順序の unit test は、未指定時に BFS になること、BFS / DFS を明示指定した場合に traversal order が変わることを検証する。
- 循環 / 再帰の unit test は、訪問済み node を再展開しないこと、再訪 edge が `cycle` cutoff として記録されることを検証する。
- 深さ上限の unit test は、未指定時に到達可能 node を深さで打ち切らないこと、指定時に上限到達 node を `depthLimit` cutoff として記録することを検証する。
- 起点不在の unit test は、空の到達 node / edge 集合と `startNotFound` status が返ることを検証する。
- caller / callee 方向を unit test で検証する。
- S1 / S2 の既知集合との一致は `testdata/fixtures/` の E2E で検証する。

## Interface 設計

### UI / API / Event Interface

- UI: 非該当。
- Web API endpoint: 非該当。
- Go package interface: `core/internal/traversal` は graph view、起点、探索方向、探索 option を受け取り、探索結果を返す。
- Event interface: 非該当。Traversal はプロセス内同期処理として扱う。

### Props / Request / Response

初期設計では次の概念を扱う。具体的な Go 型名は実装 prompt 生成時に確定する。

| 概念              | 主な field / 値                                                                                                           | 備考                                         |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------- |
| Traversal request | 起点 method ID、方向 (`caller` / `callee`)、深さ上限 (任意、未指定時は無制限)、探索順序 (`bfs` / `dfs`、未指定時は `bfs`) | CLI 引数名は後続で決める                     |
| Traversal result  | 到達 node 集合、到達 edge 集合、status (`ok` / `startNotFound`)、打ち切り情報                                             | Output Engine が consumer。tree は保持しない |
| Cutoff            | 理由 (`cycle` / `depthLimit`)、対象 node / edge、depth                                                                    | `cycle` は訪問済み node への再訪 edge を表す |

## Content / Data 設計

### 保存・管理するデータ

- 永続データは持たない。
- Graph は Core process 内の一時データとして扱う。
- Traversal 結果は到達 node 集合 + edge 集合 + cutoff 情報として Core process 内で一時的に扱い、Output Engine へ渡した後は破棄できる。
- 起点不在時の Traversal 結果は、空の到達 node 集合、空の到達 edge 集合、`startNotFound` status を持つ。
- Traversal 実行中は訪問済み node set を保持する。訪問済み node への再訪は到達集合を増やさず、cutoff として記録する。
- 深さ上限は request option としてのみ保持し、永続設定や global default は持たない。

### コンテンツ配置 / package / route

| path                      | 用途                                                 |
| ------------------------- | ---------------------------------------------------- |
| `core/internal/graph`     | node / edge 管理、Traversal が読む graph view        |
| `core/internal/traversal` | caller / callee 探索、探索 option、探索結果          |
| `core/internal/output`    | Traversal 結果の出力先。#6 では参照のみ              |
| `testdata/fixtures`       | S1 / S2 E2E fixture。具体 fixture は後続 spec と同期 |

## Performance / Security 設計

### Performance

- Traversal は node 数 `V`、edge 数 `E` に対して `O(V + E)` の探索を基本方針にする。
- BFS / DFS のどちらを選んでも、同一 graph view に対して追加の graph 再構築は行わない。
- 訪問済み node を再展開しないため、循環 graph でも探索量は node / edge の到達範囲に比例させる。
- 深さ上限が未指定の場合は到達可能 node を深さで打ち切らない。深さ上限が指定された場合は、上限到達以降の隣接 node を展開せず `depthLimit` cutoff として記録する。
- 探索結果が大きい場合の出力抑制や pagination は #6 の対象外とし、CLI / Output の後続論点に残す。

### Security / Privacy

- 解析対象ソースは read-only とし、Traversal はファイルシステムを書き換えない。
- Traversal は外部送信を行わない。
- Analyzer metadata に含まれる言語固有情報は、Traversal の権限判断や外部 I/O に使わない。

## Error / Fallback 設計

### エラーケース

| #   | ケース                            | ユーザーへの見せ方                                                               | リカバリ                                      |
| --- | --------------------------------- | -------------------------------------------------------------------------------- | --------------------------------------------- |
| 1   | 起点メソッドが graph に存在しない | 空結果 + `startNotFound` status を返す。Output Engine は「該当なし」と表示できる | CLI / Output が候補表示を行うかは後続で決める |
| 2   | 探索方向が未対応値                | 実行前 validation error                                                          | caller / callee のいずれかを指定する          |
| 3   | 深さ上限が不正値                  | 実行前 validation error                                                          | 未指定または 0 以上の整数を指定する           |
| 4   | 循環呼び出し / 再帰               | 再訪 edge を `cycle` cutoff として探索結果に含める                               | 訪問済み node を再展開しない                  |

### Fallback

- Graph が空の場合、Traversal は空結果を返す。
- 深さ上限に到達した場合、Traversal は部分結果と cutoff 情報を返す。

## テスト / 評価方針

### テスト観点

- caller 方向で既知の呼び出し元集合を返せること。
- callee 方向で既知の呼び出し先集合を返せること。
- 探索順序未指定時に BFS で到達 node を列挙すること。
- DFS を明示指定した場合に DFS の順序で到達 node を列挙すること。
- 循環 graph で訪問済み node を再展開せず、無限ループしないこと。
- 再帰 edge を含む graph で、再訪 edge を `cycle` cutoff として保持できること。
- 同一 node へ別 path から到達した場合でも、2 回目以降の再展開を行わないこと。
- 深さ上限未指定時に、到達可能 node を深さで打ち切らないこと。
- 深さ上限指定時に、上限到達 node を `depthLimit` cutoff として保持できること。
- Traversal 結果が tree ではなく、到達 node 集合 + edge 集合として返ること。
- 起点メソッドが存在しない場合に panic せず、空結果 + `startNotFound` status を返すこと。
- Traversal が Analyzer 実装や Output format に依存しないこと。

### 計測指標

- Traversal unit test の pass / fail。
- S1 / S2 E2E fixture の expected caller / callee 集合との差分。
- 大規模 fixture での探索時間と peak memory。固定 budget は後続で決める。

## フロー / シーケンス

(`spec-diagrams` で生成。spec の主要操作を Mermaid 図に落とす)

### Flowchart (ユーザー操作起点)

```mermaid
flowchart TD
    A["起点メソッドと探索方向を受け取る"] --> B{"起点は graph に存在するか"}
    B -- "No" --> C["空結果 + 起点不在 status を返す"]
    B -- "Yes" --> D["探索順序を決定<br/>未指定時は BFS"]
    D --> E["BFS queue または DFS stack を初期化"]
    E --> F{"次の node はあるか"}
    F -- "No" --> G["到達集合と cutoff 情報を返す"]
    F -- "Yes" --> H{"深さ上限に到達したか"}
    H -- "Yes" --> I["depthLimit cutoff を記録"]
    H -- "No" --> J["探索方向に従って隣接 edge を読む"]
    J --> K{"訪問済み node か"}
    K -- "Yes" --> L["cycle cutoff を記録"]
    K -- "No" --> M["node / edge を到達集合へ追加"]
    I --> F
    L --> F
    M --> F
```

### Sequence

```mermaid
sequenceDiagram
    participant UseCase as Analyze Use Case
    participant Graph as Graph Engine
    participant Traversal as Traversal Engine
    participant Output as Output Engine

    UseCase->>Graph: methodSymbol / callEdge を登録
    UseCase->>Traversal: Traverse(graph, request)
    Traversal->>Graph: 起点 node を取得
    Graph-->>Traversal: node found / not found
    Traversal->>Graph: 方向に応じた隣接 edge を取得
    Graph-->>Traversal: caller / callee edges
    Traversal-->>UseCase: Traversal result
    UseCase->>Output: result を出力形式へ渡す
```

## 実装分割

### 実装タスク案

| Phase | 対象                                      | 概要                                                                                    | 依存  |
| ----- | ----------------------------------------- | --------------------------------------------------------------------------------------- | ----- |
| P1    | `core/internal/graph`                     | Traversal が読む node / edge graph view と test builder を実装                          | #12   |
| P2    | `core/internal/traversal`                 | caller / callee 探索、探索順序 option、既定 BFS、任意 depth limit、方向、起点不在を実装 | P1    |
| P3    | `core/internal/traversal`                 | 訪問済み node 管理、`cycle` cutoff、到達 node / edge 集合の traversal result を実装     | P2    |
| P4    | `testdata/fixtures` / `core/internal/...` | S1 / S2 fixture と E2E 照合の土台を追加                                                 | P1-P3 |

### prompts 生成方針

- `graph` と `traversal` を分ける。Traversal API が Graph Engine の内部構造へ直接依存しないようにするため。
- 未確定論点はない。`spec-review` 後に prompts を生成する。
- Output Engine の実装 prompt は #7 で生成する。

## 上位資料からの変更点

本 spec で PRD / Design Doc / feature doc / context / 既存 ADR から変更・追加した内容を、反映先別に記録する。`spec-track` / `spec-sync` で更新する。

### PRD への影響

| 対象節 | 変更内容 | 理由                                                            |
| ------ | -------- | --------------------------------------------------------------- |
| -      | なし     | 本プロダクトは統合モードで、Why / What は Design Doc に統合済み |

### Design Doc への影響

| 対象節            | 変更内容                                                                                                                                                    | 理由                                                                                    |
| ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| -                 | なし (source: spec-resolve D1)                                                                                                                              | 探索順序 option は Traversal API の詳細であり、Design Doc の landscape 変更を伴わない   |
| Open Questions Q4 | 循環 / 再帰は訪問済み node の再展開を抑止し、再訪 edge を `cycle` cutoff として記録する (source: spec-resolve D2)。`spec-sync` 時に Q4 を解決済みへ更新する | Q4 は issue #6 が正本として解くため                                                     |
| Open Questions Q4 | 深さ上限は任意 option とし、未指定時は無制限に探索する (source: spec-resolve D3)。`spec-sync` 時に Q4 の解決内容へ含める                                    | Q4 の深さ上限 / 訪問済み管理のうち深さ上限側の判断であるため                            |
| -                 | なし (source: spec-resolve D4)                                                                                                                              | 探索結果モデルは Traversal feature の詳細であり、Design Doc の landscape 変更を伴わない |
| -                 | なし (source: spec-resolve D5)                                                                                                                              | 起点不在 status は Traversal API の詳細であり、Design Doc の landscape 変更を伴わない   |

### feature doc への影響

| 対象 doc / 節                                      | 変更内容                                                                                                                                            | 理由                                             |
| -------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| `design/features/traversal/DesignDoc_traversal.md` | 探索順序は API option とし、既定を BFS にする (source: spec-resolve D1)。feature doc 作成時に反映する                                               | Traversal の durable な API 判断になるため       |
| `design/features/traversal/DesignDoc_traversal.md` | 循環 / 再帰は訪問済み node の再展開を抑止し、再訪 edge を `cycle` cutoff として記録する (source: spec-resolve D2)。feature doc 作成時に反映する     | Traversal の durable な打ち切り条件になるため    |
| `design/features/traversal/DesignDoc_traversal.md` | 深さ上限は任意 option とし、未指定時は無制限に探索する (source: spec-resolve D3)。feature doc 作成時に反映する                                      | Traversal の durable な API / 探索仕様になるため |
| `design/features/traversal/DesignDoc_traversal.md` | 探索結果モデルは到達 node 集合 + edge 集合とし、tree 表現は Output 側で必要に応じて構築する (source: spec-resolve D4)。feature doc 作成時に反映する | Traversal と Output の責務境界になるため         |
| `design/features/traversal/DesignDoc_traversal.md` | 起点不在は空結果 + `startNotFound` status として返す (source: spec-resolve D5)。feature doc 作成時に反映する                                        | Traversal result の status contract になるため   |
| `design/features/traversal/DesignDoc_traversal.md` | 未作成。spec 解決後に作成 / 同期要否を判定                                                                                                          | durable な Traversal 設計の置き場が未作成のため  |

### context への影響

| 対象 doc / 節 | 変更内容 | 理由                                                   |
| ------------- | -------- | ------------------------------------------------------ |
| -             | なし     | 現時点では既存 package / test / toolchain 境界の範囲内 |

### ADR の新規 / 更新

| ADR ID | 変更内容 | 理由                                 |
| ------ | -------- | ------------------------------------ |
| -      | なし     | 技術選定や横断アーキ判断の変更はない |

## レビュー

`spec-review` (fresh-context evaluator) の最新結果。完全な記録は `review.md` を参照。

| 日付 | 結果 (PASS / NEEDS_WORK) | 指摘要点 | 対応                     |
| ---- | ------------------------ | -------- | ------------------------ |
| -    | -                        | 未実施   | D1-D5 解決済み。次に実施 |

## 変更履歴

| 日付       | 変更者 | 変更内容                                                                             |
| ---------- | ------ | ------------------------------------------------------------------------------------ |
| 2026-07-07 | Codex  | D5 起点メソッド不在を空結果 + status として解決                                      |
| 2026-07-07 | Codex  | D4 探索結果モデルを到達 node 集合 + edge 集合として解決                              |
| 2026-07-07 | Codex  | D3 深さ上限を任意 option、未指定時は無制限として解決                                 |
| 2026-07-07 | Codex  | D2 循環 / 再帰の打ち切り条件を訪問済み node の再展開抑止と `cycle` cutoff として解決 |
| 2026-07-07 | Codex  | D1 探索順序を API option、既定 BFS として解決                                        |
| 2026-07-07 | Codex  | requirements から初期 spec を作成                                                    |

## 備考

- 追加 appendix は現時点では不要。API endpoint、永続データ層、認可、画面、E2E UI を持たないため。
