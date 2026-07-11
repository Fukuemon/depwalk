# 出力形式 (Console / JSON / DOT / Mermaid) spec

> issue #7 の設計 spec。
> Output Engine は Traversal Engine が返す到達集合 (到達 node / edge 集合、`cycle` 注釈、`depthLimit` cutoff) を入力に、Console / JSON / DOT / Mermaid の各形式へ変換する。Traversal result 契約の正本は [Traversal feature doc](../../design/features/traversal/DesignDoc_traversal.md)、Model schema の正本は [Analyzer Protocol / SPI feature doc](../../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md)、Core package 境界の正本は [context/architecture.md](../../context/architecture.md) / [ADR-0002](../../adr/0002-core-implementation-foundation.md) とする。
> 本 spec で確定した durable な設計成果は phase: sync で `design/features/output/DesignDoc_output.md` へハンドオフする (未作成)。

## メタ情報

- Issue: `#7`
- ステータス: `Draft`
- 作成日: 2026-07-11
- 更新日: 2026-07-11
- Branch: `feature/7`
- Owner: Fukuemon

## 設計フェーズ状況

状態は `未着手 / 進行中 / 完了 / レビュー済 / 保留` のいずれか。保留の場合は理由を備考に残す。

| #   | フェーズ                    | 状態       | 最終更新   | 備考                                                                   |
| --- | --------------------------- | ---------- | ---------- | ---------------------------------------------------------------------- |
| 1   | 起票                        | 完了       | 2026-07-11 | GitHub issue #7 と `requirements.md` を確認済み                        |
| 2   | 下書き                      | レビュー済 | 2026-07-11 | `requirements.md` から本 spec を scaffold。scaffold gate で PASS       |
| 3   | 上位文書突合                | レビュー済 | 2026-07-11 | Design Doc / context / ADR / traversal / analyzer-protocol と矛盾なし  |
| 4   | 論点整理                    | レビュー済 | 2026-07-11 | D1-D7 を初期論点として列挙 (Q3 は D2 が引き取る)                       |
| 5   | 論点解決                    | 進行中     | 2026-07-11 | D1 / D2 (= Q3) / D3 解決済み。D4-D7 が未決。D3 が traversal へ変更提案 |
| 6   | Interface / Routing 設計    | 未着手     |            | Formatter I/F は D1 / D6 の決定に依存                                  |
| 7   | Content / Data 設計         | 未着手     |            | JSON schema は D3 の決定に依存                                         |
| 8   | Performance / Security 設計 | 未着手     |            |                                                                        |
| 9   | Test / Metrics 設計         | 未着手     |            |                                                                        |
| 10  | 実装分割                    | 未着手     |            |                                                                        |
| 11  | レビュー済                  | 未着手     |            |                                                                        |

## 上位文書整合

正本 (PRD ※本プロダクトは統合モードのため未作成、Why/What は [Design Doc](../../design/DesignDoc.md) に統合 / [Design Doc](../../design/DesignDoc.md) / [feature doc](../../design/features/) / [context](../../context/) / ADR) のどの節と、どう整合させたかを記録する。

- PRD 更新要否: 不要 (本プロダクトは統合モード。Why / What は Design Doc に統合)
- Design Doc 更新要否: 要 (phase: sync で Open Question Q3 を「解決済み」へ更新し、feature 一覧の「出力形式」行に新規 feature doc を紐付ける)
- ADR 起票要否: 現時点では不要見込み (既存の Core package 境界と Protocol 判断の範囲内)。D1 が graph model の変更を伴う結論になった場合のみ再判定する

| 上位文書    | 節 / 該当箇所                                                                                                                 | 整合方針 (継承 / 補足 / 変更提案) |
| ----------- | ----------------------------------------------------------------------------------------------------------------------------- | --------------------------------- |
| PRD         | 統合モードのため `design/DesignDoc.md` の Why / What を参照                                                                   | 継承                              |
| Design Doc  | 成功条件 S3、Goal G3、モジュール責務 Output Engine、Non Goals (ビューワ非提供)、Future Work Phase1 / Phase4                   | 継承                              |
| Design Doc  | Open Questions Q3 (Console ツリー表現)                                                                                        | 補足 (D2 で解決 → sync で反映)    |
| Design Doc  | 「詳細の所在」feature 一覧の「出力形式」行 = 未作成 / 未着手                                                                  | 補足 (sync で feature doc を作成) |
| feature doc | `design/features/traversal/DesignDoc_traversal.md` の Traversal result 契約 (到達集合 / `cycle` / `depthLimit` / tree 非保持) | 継承 (下記注記)                   |
| feature doc | `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md` の `MethodSymbol` / `CallEdge` / `SourceLocation`          | 継承 (下記注記)                   |
| context     | `context/architecture.md` Package Boundary (`Output Engine` → `Graph Engine` / `Model`、`core/internal/output`)               | 継承 (下記注記)                   |
| context     | `context/testing.md` E2E 照合 (S3 = 各出力形式のパース可否)                                                                   | 補足 (D7 で観点を具体化)          |
| context     | `context/toolchain.md` Go 標準 library / Go 標準 `testing`                                                                    | 継承                              |
| context     | `context/engineering.md` Repository Quality Gate / 依存境界 gate                                                              | 継承                              |
| ADR         | `adr/0001-analyzer-protocol-jsonl-spi.md`                                                                                     | 継承                              |
| ADR         | `adr/0002-core-implementation-foundation.md`                                                                                  | 継承                              |

> Traversal feature doc は「Console tree が必要な場合も tree 構築は Output 側で行う (Traversal は tree 表現を保持しない)」と定めている。本 spec はこの分界を継承し、tree 化を Output Engine の責務として設計する (D2)。
> `context/architecture.md` は Output Engine の依存先を `Graph Engine` / `Model` と定める。一方、現行実装の `graph.Node` は `methodId` のみを保持し (`core/internal/graph/graph.go:23-25`)、`MethodSymbol` の `qualifiedName` / `signature` / `sourceLocation` を保持していない。Console / JSON が methodId 以外を表示するには symbol 情報の受け渡し経路が要る。これは上位文書との**矛盾ではなく未定義**であり (Design Doc のモジュール責務は Output → Model 依存を許容している)、D1 で解決する。D1 が graph model の拡張を選ぶ場合は Graph Engine (`core/internal/graph`) への差分が発生するため、実装対象に `core` を含めている。

## 関連資料

- `design/DesignDoc.md`: 成功条件 S3、Goal G3、モジュール責務 Output Engine、Non Goals、Future Work Phase1 / Phase4、Open Questions Q3
- `design/features/traversal/DesignDoc_traversal.md`: Traversal result 契約 (Output Engine が consumer)
- `design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md`: `MethodSymbol` / `CallEdge` / `SourceLocation` schema の正本
- `context/architecture.md`: Package Boundary、`core/internal/output` の責務
- `context/testing.md`: S3 の E2E 照合方針
- `specs/7-output/requirements.md`: 本 spec の要求定義 (S3、R1-R3、I1-I2、O1-O3、E1-E3、V1)
- 関連 issue: #7 (本 spec)、#6 (Traversal / 入力元、完了)、#8 (Analyzer Protocol / Model、完了)、CLI interface spec (未起票。format 引数 / exit code の正本)

## 背景

- depwalk の調査結果は、人が読む用途 (Console) と機械処理・可視化用途 (JSON / DOT / Mermaid) の双方で使われる。両立する出力手段が現状ない。
- Design Doc の成功条件 S3 「呼び出しグラフを Console / JSON / DOT / Mermaid で出力できる」と Goal G3 に直接対応する。
- Traversal Engine (#6) が到達集合を返せる状態になり、その consumer である Output Engine が Phase1 の最後の未設計モジュールになっている (`core/internal/output/output.go` は package 宣言のみの stub)。
- Phase1 の完成条件は Console / JSON。DOT / Mermaid は Future Work Phase4 の実装だが、後から Output Engine の構造を作り直さずに済むよう **I/F は本 feature で設計する**。

## スコープ

### やること

- Traversal result (到達 node / edge 集合、`cycle` 注釈、`depthLimit` cutoff、`status`) を各出力形式へ変換する Output API を設計する。
- Console 出力のツリー表現を確定する (深さ表示、循環参照の見せ方、depthLimit cutoff の見せ方) — Design Doc Open Question **Q3** の正本。
- JSON 出力の契約を確定する (schema、schemaVersion、後方互換方針、要素順序の決定性)。
- DOT / Mermaid 出力の I/F 方針を確定する (実装は Phase4)。
- 空グラフ / 起点不在 (`startNotFound`) / 未対応 format の各形式での見せ方を定義する。
- Output Engine のテスト観点 (unit / golden / S3 の parse 可否照合) を定義する。

### やらないこと

- グラフのビューワ / レンダラの提供 (DOT / Mermaid は構文生成まで — Design Doc Non Goals)。
- 探索ロジック (→ `traversal` / #6 で確定済み)。
- 解析・Model schema の定義 (→ `analyzer-protocol` / #8 で確定済み)。
- CLI の引数名 (`--format` 等)、exit code、エラーメッセージ表示 (→ CLI interface spec。本 spec は Output Engine が返す値までを責務とし、プロセス終了コードには関与しない)。
- DOT / Mermaid の実装 (Phase4)。本 spec は I/F 方針までを確定する。
- 出力のファイル書き出し / 出力先の決定 (Output Engine は `io.Writer` へ書く。宛先の選択は CLI の責務)。

## 要件の解釈

### 実現したいユーザー価値

- 開発者 / 保守担当が、ターミナルで呼び出し関係をすぐ読める (Console)。
- CI パイプラインが、機械可読な形式で結果を保存・後処理できる (JSON)。
- 調査結果を図としてドキュメントに貼り付けられる (DOT / Mermaid、Phase4)。

### 成功条件

- S3: 呼び出しグラフを Console / JSON / DOT / Mermaid で出力でき、各形式でパース / レンダリング可能な出力が得られる。
- 本 spec の実装対象は Output Engine (+ D1 の結論次第で Graph Engine) に限られるため、#7 の成功条件は **Output Engine が Traversal result から各形式の文字列を生成できること**に限定して検証する。CLI 引数レベルでの最終的な S3 の E2E 照合は、CLI interface spec の実装後に完成する (Traversal の #6 と同じ分界)。
- Phase1 完了時点で Console / JSON が満たされていること。DOT / Mermaid は I/F が定まっていれば Phase1 の完了条件を満たす。

### 対象ユーザー / 操作主体

- 開発者 / 保守担当 (Console)
- CI パイプライン (JSON)
- 下流ツール / ドキュメント (DOT / Mermaid)

EARS 風で振る舞いを記述する。

- WHEN 呼び出し側が Traversal result と出力形式を指定して Output Engine を呼ぶ時、システムは指定形式の出力を `io.Writer` へ書き出す。
- WHEN format が `console` の時、システムは起点メソッドを根とするツリーを、深さ・循環・深さ上限打ち切りが読み取れる形で出力する。
- WHEN format が `json` の時、システムは schemaVersion を含む機械可読な JSON を、決定的な要素順序で出力する。
- IF Traversal result の status が `startNotFound` の時、システムは各形式で「該当なし」を明示する (エラーとして扱わない)。
- IF 到達集合が起点のみで edge が空の時、システムは各形式で空グラフを表現する。
- IF 未対応の format が指定された時、システムは出力を行わずエラーを返す (対応形式を案内する。プロセス exit code は CLI の責務)。
- THE SYSTEM SHALL 同一の Traversal result に対して常に同一のバイト列を出力する (到達集合が順序非保証であっても、出力は決定的にする)。

## 設計時の論点

設計 / 実装フェーズへ持ち越す残課題を 1 件ずつ管理する。確定したものは「解決済みの論点」へ移す。

| #   | 論点                                                                                                                                                   | 決定候補                                                                                                                                                                          | 決定         |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ |
| D1  | Output が表示する symbol 情報 (`qualifiedName` / `signature` / `sourceLocation` / `callSite`) の受け渡し経路。現行 `graph.Node` は `methodId` のみ持つ | (a) `graph` に固有の値型で保持し Output は Graph から引く / (b) `graph.Node` に `protocol.MethodSymbol` (wire DTO) をそのまま埋める / (c) methodId のみ表示                       | **決定 (a)** |
| D2  | **Q3**: Console ツリー表現。到達集合 (非 tree) から tree を組む際の、深さ表示・循環参照・`depthLimit` cutoff・合流 (同一 node 複数経路) の見せ方       | 罫線ツリー + **初出のみ展開** (`(既出)` 参照印) / 循環は `(cycle)` で打ち切り / cutoff は `… (depth limit: N)` / 深さラベルなし / 子行は `callSite`、root は宣言位置              | **決定**     |
| D3  | JSON 出力スキーマと版管理。フィールド構成、`schemaVersion` の採番系 (Analyzer Protocol の `schemaVersion` と同一系統か独立か)、後方互換方針、要素順序  | フラットな graph (`nodes[]` / `edges[]` / `depthCutoffs[]`)。node に `minDepth` を持つ (**traversal 契約の拡張が必要**)。版は Protocol と独立 / additive 互換。順序は id の辞書順 | **決定**     |
| D4  | DOT / Mermaid の I/F 方針 (Phase4 実装)。Formatter をどう抽象化し、`cycle` / cutoff / 起点をどう図示するか。本 spec でどこまで決めるか                 | (a) 共通 Formatter interface のみ定め、DOT / Mermaid の構文詳細は Phase4 spec へ送る / (b) 構文まで本 spec で確定する                                                             | 未決         |
| D5  | 空グラフ / `startNotFound` / 未対応 format の扱いと、Output Engine とエラーの境界 (どこまでを戻り値のエラーとし、どこから CLI の責務か)                | `startNotFound` と空グラフは**正常系**として各形式で表現、未対応 format は Output API の呼び出し前 validation エラー。exit code は CLI spec                                       | 未決         |
| D6  | Formatter の Go interface 形状と出力先。大規模グラフでの streaming / バッファリング方針 (非機能: 実用時間で出力)                                       | `Format(w io.Writer, r traversal.Result, opts) error` 相当の interface + format ごとの実装。全件メモリ構築か逐次書き出しか                                                        | 未決         |
| D7  | テストの検証境界。golden file test を導入するか、S3 の「パース可否」照合をどの層で行うか (`context/testing.md` との整合)                               | unit: 各 formatter の出力を golden 比較 / E2E: 生成した JSON を `encoding/json` で、DOT / Mermaid を構文パースで検証。golden の置き場所は `testdata/`                             | 未決         |

## 解決済みの論点

(phase: clarify で確定したものをここに移動する)

### D1: symbol 情報は Graph が保持し、Output は Graph から引く (2026-07-11 決定)

**決定**: `graph` package に表示用の値型を持たせる。`graph.Node` は `ID` (Analyzer の `methodId`) に加えて `Symbol` (`QualifiedName` / `Signature` / `Source`) を保持し、`graph.Edge` は `CallSite` を保持する。`protocol` の wire record から graph の値型への変換は、graph 構築時 (Analyze Use Case 層) に 1 回だけ行う。Output Engine は Graph の読み取り API から symbol を引く。

**理由**:

- `methodId` は Analyzer が決定的に生成する **不透明な stable ID** であり、人間可読な名前である保証も Analyzer version をまたぐ永続性もない ([analyzer-protocol feature doc](../../design/features/analyzer-protocol/DesignDoc_analyzer-protocol.md) の `methodId` 定義)。したがって Console / JSON が `methodId` のみを出す案 (c) は成立しない。
- 依存方向が `Output Engine → Graph Engine → Model` となり、[Design Doc モジュール責務](../../design/DesignDoc.md) および [context/architecture.md](../../context/architecture.md) の Package Boundary と一致する。Graph Engine の責務 (Node 管理 / Edge 管理) の範囲内に収まる。
- symbol table を別入力で渡す案 (b) は、「table が到達 node 集合を漏れなく覆っていること」という**暗黙の不変条件**を新設し、graph と table の 2 つを同期させる必要が生じる。node 属性は node と同じ場所に置くほうが不変条件が 1 つ減る。
- wire DTO をそのまま埋める案 (b') は変換コストがゼロな一方、`schemaVersion` / `recordType` という wire 専用フィールドを Core の graph model に持ち込む。graph 固有の値型にすることでこれを避ける。

**派生する詳細**:

- `SourceLocation` は `protocol` package の型を再利用する。この型は `path` / `startLine` 等の純粋な値のみで wire 専用フィールドを持たないため、再利用しても本決定の理由 (wire 表現を core domain に入れない) に反しない。依存方向も `Graph Engine → Model` の範囲内。
- 本決定により Graph Engine (`core/internal/graph`) への差分が確定するため、実装対象の `core` は条件付きではなく確定 ◯ になる。
- edge の `callSite` を Console / JSON でどう見せるかは D2 / D3 で決める (保持することのみ本決定で確定)。

### D2 (= Design Doc Open Question Q3): Console ツリー表現 (2026-07-11 決定)

**決定**: 罫線ツリーで、**初出の node のみ部分木を展開する**。再登場した node は葉として `(既出)` を付け、展開しない。循環は `(cycle)` で打ち切り、深さ上限で切られた枝は `… (depth limit: N)` で「続きがある」ことを明示する。深さラベル (`[d2]` 等) は付けない。

Traversal result は tree ではなく集合 (到達 node 集合 + 誘導 edge 集合) であるため、**tree 化の規則を Output 側の仕様として定義する** ([traversal feature doc](../../design/features/traversal/DesignDoc_traversal.md): 「tree 構築は Output 側で行う」)。

#### tree 構築規則

1. **root** = 起点 node。
2. **子** = 誘導 edge 集合 (`Result.Edges`) を探索方向に辿った先の node。caller 方向なら子は「呼び出し元」、callee 方向なら子は「呼び出し先」。
3. **兄弟の順序** = `qualifiedName` → `signature` → `methodId` の辞書順。到達集合は順序非保証のため、この並び順の固定が出力の決定性を担保する。
4. **展開順序** = 上記順序に従った pre-order DFS。展開順が決定的なので、どの出現が「初出」かも決定的になる。
5. **初出のみ展開** = ある node の部分木を展開するのは、tree 中で最初に出現したときの 1 回のみ。2 回目以降の出現は `(既出)` を付けた葉にする。これにより出力行数は **O(到達 edge 数)** に収まり、合流 (ダイヤモンド構造) で指数的に膨らまない。
6. **`(cycle)`** = `Result.Cycles` に注釈された edge の先。閉路を構成する edge の先端は必ず既出 node になるため、`(既出)` ではなく `(cycle)` を優先して表示し、展開しない。
7. **`… (depth limit: N)`** = `Result.DepthCutoffs` に記録された edge を持つ node の子として 1 行出す。N はその node からの cutoff edge 数。cutoff 先の node は到達集合外なので名前は出さない。

#### 行の書式

- **node ラベル** = `qualifiedName` + `signature`。
- **位置情報**: 子行は **`edge.CallSite`** (= その呼び出しを行っている行) を出す。tree の各子行は edge を表すため、影響調査で「どこで呼んでいるか」に直接飛べることを優先する。root は edge を持たないため、node の宣言位置 (`Symbol.Source`) を出す。位置が欠落している場合 (`callSite` / `sourceLocation` は Protocol 上 optional) は位置表記を省略する。
- メソッド自体の宣言位置は Console では出さない (JSON には node / edge の両方を含める → D3)。

```text
UserService.findById(Long)  [UserService.java:42]
├─ UserController.getUser(Long)  [UserController.java:31]
│  └─ ApiFilter.doFilter()  [ApiFilter.java:20]
├─ AdminController.getUser(Long)  [AdminController.java:18]
│  └─ ApiFilter.doFilter()  (既出)
├─ UserBatch.execute()  [UserBatch.java:55]
│  └─ … (depth limit: 2)
└─ CacheWarmer.warm()  [CacheWarmer.java:8]
   └─ UserService.findById(Long)  (cycle)
```

**理由**:

- 呼び出しグラフでは共有メソッド (Filter / Util / Repository 等) が多数の経路から到達する。経路ごとにフル展開する案は「その経路を辿るとこうなる」が完全に見える一方、実コードのダイヤモンド構造で出力が指数的に膨らみ、Console として実用にならない。初出のみ展開なら出力サイズの上限が到達 edge 数で読める。
- `(既出)` の node は tree の別の場所に必ず完全な部分木があるため、情報は失われない (参照先を辿れば読める)。
- 深さラベルはインデントと冗長であり、行を長くする割に得られる情報が少ないため付けない。深さ情報が必要な用途 (機械処理) は JSON 側が担う。

**空グラフ / 起点不在 (`startNotFound`) の Console 表現は D5 で決める。**

### D3: JSON 出力スキーマと版管理 (2026-07-11 決定)

**決定**: JSON は **フラットな graph** (`nodes[]` / `edges[]`) として出力し、Console と同型の tree にはしない。各 node は起点からの最短距離 `minDepth` を持つ。スキーマは Analyzer Protocol とは**独立した output schema 版**を持つ。

#### スキーマ

```json
{
  "schemaVersion": "1.0",
  "status": "ok",
  "direction": "caller",
  "start": "<methodId>",
  "nodes": [
    {
      "methodId": "<methodId>",
      "qualifiedName": "com.example.UserService.findById",
      "signature": "(java.lang.Long)",
      "minDepth": 0,
      "sourceLocation": {
        "path": "src/main/java/com/example/UserService.java",
        "startLine": 42
      }
    }
  ],
  "edges": [
    {
      "edgeId": "<edgeId>",
      "callerMethodId": "<methodId>",
      "calleeMethodId": "<methodId>",
      "cycle": false,
      "callSite": {
        "path": "src/main/java/com/example/UserController.java",
        "startLine": 31
      }
    }
  ],
  "depthCutoffs": [
    {
      "edgeId": "<edgeId>",
      "callerMethodId": "<methodId>",
      "calleeMethodId": "<methodId>",
      "targetMinDepth": 3,
      "callSite": { "path": "...", "startLine": 12 }
    }
  ]
}
```

- **field 名は Analyzer Protocol の語彙を踏襲する** (`methodId` / `callerMethodId` / `sourceLocation` / `callSite`)。利用者が Protocol JSONL と同じ語彙で扱えるようにする。
- `status` は `ok` / `startNotFound` (`traversal.Status` と同値)。`direction` は `caller` / `callee`。
- `cycle` は `Result.Cycles` の注釈に対応する bool。**false でも省略せず常に出力する** (利用者が field の有無を分岐しなくて済むようにする)。
- `sourceLocation` / `callSite` は Protocol 上 optional のため、欠落時は field ごと省略する。
- **`depthCutoffs[]` の `calleeMethodId` は `nodes[]` に存在しない** (深さ上限の外側にある node のため到達集合に含まれない。`traversal.Result` は cutoff 先の symbol を保持しない)。利用者はこの参照が dangling であることを前提にする。この非対称性は Traversal result の契約に由来する。
- **要素順序**: `nodes[]` は `methodId`、`edges[]` / `depthCutoffs[]` は `edgeId` の辞書順に固定する。到達集合は順序非保証のため、この固定が出力の決定性を担保する。

#### 版管理

- **Analyzer Protocol の `schemaVersion` とは独立の採番**とする。Protocol は Analyzer ↔ Core の wire 契約、本 schema は Core ↔ 利用者 (CI / 後処理) の契約であり、変更理由が独立しているため。
- **後方互換方針**: field の追加は後方互換 (additive) とし minor を上げる。field の削除 / 意味変更 / 型変更は破壊的変更とし major を上げる。利用者は未知の field を無視できることを前提にする。

**理由**:

- Traversal result は tree ではなく **graph** (合流・循環を含む) である。フラットな graph はこの構造をそのまま表現でき、合流 node の重複も、循環の打ち切り表現も要らない。tree 化は Console の表示都合であり、機械処理向けの JSON に持ち込む必要がない。
- tree 形式にすると、Console と同じ `(既出)` / `(cycle)` の打ち切りが機械処理側に漏れ出し、利用者が「この node は本当に葉なのか、参照印なのか」を判定する羽目になる。
- `minDepth` を含めることで、「直接の呼び出し元だけ抽出 (`minDepth == 1`)」のような典型的な後処理が JSON 単体で完結する。

**上位文書への影響 (変更提案)**: `minDepth` を出力するには `traversal.Result` が node ごとの最短距離を公開する必要がある。現行の `Result.Nodes` は `map[string]bool` で深さを公開していない (深さは内部計算のみ)。これは #6 で確定した Traversal result 契約の拡張であり、[traversal feature doc](../../design/features/traversal/DesignDoc_traversal.md) への **変更提案** として phase: sync で反映する (`## 上位資料からの変更点 > feature doc への影響`)。追加は additive (既存の到達集合・`cycle`・`depthLimit` の意味論を変えない) であり、#6 の決定を上書きしない。

## 未確定事項

(決定できない項目を理由とともに残す。1 件でも残っていれば下流 phase は止める)

- **D3 が Traversal result 契約への変更提案を含む** (`minDepth` の公開)。spec 単独で閉じないため、phase: sync で [traversal feature doc](../../design/features/traversal/DesignDoc_traversal.md) へ反映するまで、この変更提案は未反映のまま残る。
- D4-D7 が未決 (D1 / D2 / D3 は 2026-07-11 に解決済み)。**決定者: Fukuemon / 期限: Phase1 設計時 (本 spec の phase: clarify)**。phase: clarify で 1 件ずつ確定する (D2 = Design Doc Open Question Q3 の管理を引き継ぐ。`requirements.md` の Q3 と決定者 / 期限を揃える)。
- CLI interface spec が未起票のため、`--format` の引数名・exit code・エラー出力先 (stdout / stderr) は本 spec では確定できない。本 spec は Output Engine の戻り値までを責務境界とし、CLI 側の契約は当該 spec に委ねる (D5 で境界を明文化する)。

## 実装対象

正規 target は `context/project.md` の対象ドメイン一覧を正本とする。

| モジュール          | 実装有無 | 主な責務                                                                                                                                            |
| ------------------- | :------: | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `output`            |    ◯     | Console / JSON / DOT / Mermaid formatter (`core/internal/output`)。本 spec の主対象                                                                 |
| `core`              |    ◯     | `core/internal/graph` に symbol 値型を追加 (`Node.Symbol` / `Edge.CallSite`) と、graph 構築時の wire → 値型変換 (D1 で確定)                         |
| `traversal`         |    ◯     | **D3 の変更提案**: `Result` が node ごとの `minDepth` を公開するよう拡張 (additive)。到達集合 / `cycle` / `depthLimit` の意味論は #6 のまま変えない |
| `analyzer-protocol` |    -     | Model schema の正本。#8 で確定済み。本 spec では再定義しない                                                                                        |
| `java-analyzer`     |    -     | 非該当                                                                                                                                              |

## 機能仕様

### User Flow

1. 呼び出し側 (Analyze Use Case) が Traversal Engine から `traversal.Result` を得る。
2. 呼び出し側が出力形式と出力先 (`io.Writer`) を指定して Output Engine を呼ぶ。
3. Output Engine は format に対応する Formatter を選び、result を当該形式へ変換して書き出す。
4. Console なら人間可読なツリー、JSON なら機械可読なレコード、DOT / Mermaid ならグラフ構文が得られる。
5. 起点不在 / 空グラフの場合も、エラーではなく「該当なし」を表す出力を返す。

### Reuse Policy

- Formatter は `core/internal/output` に閉じる。format ごとのヘルパを他 package へ先回りで昇格しない。
- 出力に必要な graph / symbol の読み取りは Graph Engine / Model の公開 API 経由で行い、内部構造に依存しない (`context/architecture.md` Package Boundary)。
- Traversal の内部関数 (SCC 判定・minDepth 計算等) を Output から再実装しない。必要な情報は `traversal.Result` の契約から取る。

### Performance

- 大規模グラフでも実用時間で出力できること (`requirements.md` 非機能)。具体的な budget と streaming 要否は D6 で決める。
- 出力の決定性 (要素順序の固定) のためのソートコストは、グラフ規模に対して支配的にならない範囲に収める。

### Routing / URL State

- 非該当 (CLI ツール)。

### Content / Assets

- 非該当 (静的 asset を持たない)。

### UI Reuse

- 非該当 (Web UI / IDE Plugin は Non Goals)。

### Testing

- 横断規約は [context/testing.md](../../context/testing.md)。詳細な検証境界は D7 で確定する。
- unit: 各 formatter が Traversal result (循環あり / 合流あり / cutoff あり / 空 / `startNotFound`) から期待どおりの出力を生成すること。
- E2E: 生成物が各形式としてパース可能であること (S3 の測定方法)。
- Console (D2 で確定した規則の検証観点):
  - 合流 (ダイヤモンド) graph で、共有 node の部分木が展開されるのは 1 回だけで、2 回目以降が `(既出)` の葉になること (出力行数が到達 edge 数に対して線形に収まること)。
  - 循環 (self-loop / 相互再帰) を含む graph で無限展開せず、閉路を構成する edge の先が `(cycle)` で打ち切られること。
  - `depthLimit` cutoff を持つ node の下に `… (depth limit: N)` が出て、N が当該 node からの cutoff edge 数に一致すること。
  - 兄弟の並び順が `qualifiedName` → `signature` → `methodId` の辞書順で固定され、同一 Result から常に同一のバイト列が得られること (到達集合の map 順序に依存しないこと)。
  - 子行に `callSite`、root に宣言位置が出ること。位置が欠落している場合に位置表記を省略しても破綻しないこと。
- JSON (D3 で確定した契約の検証観点):
  - 出力が `encoding/json` でパースでき、`schemaVersion` / `status` / `direction` / `start` / `nodes[]` / `edges[]` / `depthCutoffs[]` を持つこと (S3 の測定方法)。
  - `nodes[]` は `methodId`、`edges[]` / `depthCutoffs[]` は `edgeId` の辞書順で、同一 Result から常に同一のバイト列が得られること。
  - node の `minDepth` が起点 0 からの最短距離に一致すること (合流 graph で最短経路側の値を採ること)。
  - `cycle` が false の場合も field が出力されること。`sourceLocation` / `callSite` が欠落する場合に field ごと省略されること。
  - `depthCutoffs[]` の `calleeMethodId` が `nodes[]` に存在しない (dangling) ことを、契約として明示的に検証すること。

## Interface 設計

### UI / API / Event Interface

- Output Engine の入力は **Graph と Traversal result の 2 つ**に確定 (D1)。symbol は Graph の読み取り API から引くため、symbol table を第 3 引数として渡さない。
- Formatter の interface 形状 (シグネチャ / 出力先 / options) は D6 で確定する。format の受け付けと未対応 format の扱いは D5 で確定する。
- **Console 出力**: 罫線ツリー / 初出のみ展開 / `(既出)` / `(cycle)` / `… (depth limit: N)` / 子行は `callSite`、root は宣言位置 (D2 で確定。tree 構築規則と書式の詳細は `## 解決済みの論点 > D2`)。
- Console の tree 化は Output Engine 内に閉じる。Traversal は tree を保持しないため、tree 構築を Traversal 側へ押し戻さない。

### Props / Request / Response

- Graph が保持する表示用の値型 (D1 で確定):

  ```go
  // core/internal/graph
  type Node struct {
      ID     string // Analyzer の methodId (不透明な stable ID)
      Symbol Symbol
  }

  type Symbol struct {
      QualifiedName string
      Signature     string
      Source        *protocol.SourceLocation
  }

  type Edge struct {
      ID       string
      CallerID string
      CalleeID string
      CallSite *protocol.SourceLocation
  }
  ```

- `protocol.MethodSymbol` / `protocol.CallEdge` (wire record) から上記値型への変換は graph 構築時に 1 回だけ行い、wire 専用フィールド (`schemaVersion` / `recordType`) は Core の graph model に持ち込まない。

## Content / Data 設計

### 保存・管理するデータ

- Output Engine は状態を持たない (`State Boundary`: 中間状態は Core プロセス内、永続ストアなし)。表示に要する属性は Graph が保持する (D1)。
- JSON 出力はフラットな graph (`nodes[]` / `edges[]` / `depthCutoffs[]`)。node は `minDepth` を持つ。field 名は Analyzer Protocol の語彙を踏襲し、要素順序は id の辞書順に固定する (D3 で確定。schema 全体は `## 解決済みの論点 > D3`)。
- `schemaVersion` は Analyzer Protocol とは独立の採番。field 追加は additive (minor)、削除 / 意味変更は破壊的 (major) (D3)。

### コンテンツ配置 / package / route

- `core/internal/output` (`context/architecture.md` の package 表に既定)。

## Performance / Security 設計

### Performance

- (D6 決定後に確定)

### Security / Privacy

- 出力先は標準出力 / ファイルのみ。外部送信は行わない (`requirements.md` 非機能)。
- 出力にはソースコード上の識別子・パスが含まれる。秘匿情報の追加収集は行わない。

## Error / Fallback 設計

### エラーケース

| #   | ケース                               | ユーザーへの見せ方                                                                 | リカバリ                             |
| --- | ------------------------------------ | ---------------------------------------------------------------------------------- | ------------------------------------ |
| E1  | 循環参照を含むグラフ                 | Console は閉路の先を `(cycle)` で打ち切り表示、JSON は edge に `cycle` を表現 (D3) | 正常系。無限展開させない (D2 で確定) |
| E2  | 空グラフ / 起点のみ (edge が空)      | 各形式で空グラフを明示                                                             | 正常系 (D5)                          |
| E3  | 起点不在 (`status = startNotFound`)  | 各形式で「該当なし」を明示                                                         | 正常系。エラーにしない (D5)          |
| E4  | 未対応 format 指定                   | 対応形式を案内するエラーを返す                                                     | 出力前に拒否 (V1 / D5)               |
| E5  | 出力先への書き込み失敗 (`io.Writer`) | 呼び出し側へエラーを返す                                                           | CLI 側で報告 (D5 で境界を明文化)     |

### Fallback

- Output Engine はフォールバック出力を行わない。生成できない場合はエラーを返し、表示は呼び出し側 (CLI) に委ねる。

## テスト / 評価方針

### テスト観点

- (D7 決定後に具体化。現時点の候補は「Testing」節を参照)

### 計測指標

- S3: 各形式でパース / レンダリング可能な出力が得られること。

## フロー / シーケンス

(phase: diagram で生成)

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

(phase: tasks で確定)

| Phase | 対象 | 概要 | 依存 |
| ----- | ---- | ---- | ---- |
| P1    |      |      |      |

### prompts 生成方針

- (phase: tasks で確定)

## 上位資料からの変更点

本 spec で PRD / Design Doc / feature doc / context / 既存 ADR から変更・追加した内容を、反映先別に記録する。phase: track / sync で更新する。

### PRD への影響

| 対象節 | 変更内容                          | 理由                            |
| ------ | --------------------------------- | ------------------------------- |
| (なし) | 統合モードのため PRD は存在しない | Why / What は Design Doc に統合 |

### Design Doc への影響

| 対象節                         | 変更内容                                                                                      | 理由                                  |
| ------------------------------ | --------------------------------------------------------------------------------------------- | ------------------------------------- |
| Open Questions Q3              | (予定) 「解決済み」へ更新し、feature doc を正本として参照させる <!-- source: clarify (D2) --> | Q3 は本 feature が正本。D2 で決定済み |
| 詳細の所在 / Feature 設計 一覧 | (予定) 「出力形式 (Console/JSON/DOT/Mermaid)」行に新規 feature doc を紐付け、状態を更新       | 現在「未作成 / 未着手」               |

### feature doc への影響

| 対象 doc / 節                                      | 変更内容                                                                                                                                                                | 理由                                                                                                                                               |
| -------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `design/features/output/DesignDoc_output.md`       | (予定) 新規作成。Output API / Console tree 表現 / JSON schema / DOT・Mermaid I/F の durable な正本 <!-- source: clarify (D1-D3) -->                                     | phase: sync の正本ハンドオフ先                                                                                                                     |
| `design/features/traversal/DesignDoc_traversal.md` | **変更提案**: Traversal result が node ごとの最短距離 (`minDepth`) を公開する。到達 node 集合を「ID 集合」から「ID → minDepth」へ拡張する <!-- source: clarify (D3) --> | D3 で JSON に `minDepth` を含めると決定したため。additive な拡張で、到達集合 / `cycle` / `depthLimit` の意味論は変えない (#6 の決定を上書きしない) |

### context への影響

| 対象 doc / 節                              | 変更内容                                                                                                                                                              | 理由                                                  |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| `context/architecture.md` Package Boundary | (予定) Graph Engine が node / edge の表示用属性 (`Symbol` / `CallSite`) を保持し、wire record → 値型の変換を graph 構築時に行う旨を補足 <!-- source: clarify (D1) --> | D1 で確定。依存方向 (Graph Engine → Model) 自体は不変 |
| `context/testing.md`                       | (D7 次第) golden file test を導入する場合、test 方針へ追記                                                                                                            | D7 の決定に従属                                       |

### ADR の新規 / 更新

| ADR ID | 変更内容                | 理由                                                                                                                                                                                     |
| ------ | ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| (なし) | 現時点では新規 ADR 不要 | D1 は既存の Core package 境界 (ADR-0002) / Protocol 判断 (ADR-0001) の範囲内に収まり、依存方向を変えないため ADR 不要と判定。D3 が長期判断 (出力 schema の版管理方針) を伴う場合は再判定 |

## レビュー

`spec-review` (fresh-context evaluator) の最新結果。完全な記録は `review.md` を参照。

| 日付       | 結果 (PASS / NEEDS_WORK) | 指摘要点                                                                                                                   | 対応                                                                            |
| ---------- | ------------------------ | -------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| 2026-07-11 | PASS (phase: scaffold)   | 全観点 PASS。非ブロッキング提案 3 件: ①Console の EARS 述語は D2 確定時に具体化 ②未確定事項に決定者 / 期限 ③D1(b) の供給元 | ② は本 spec に反映済み。① / ③ は phase: clarify で D2 / D1 を解くときに対応する |

## 変更履歴

| 日付       | 変更者   | 変更内容                                                                                                        |
| ---------- | -------- | --------------------------------------------------------------------------------------------------------------- |
| 2026-07-11 | Fukuemon | phase: scaffold で初版作成 (上位文書突合 + D1-D7 列挙)                                                          |
| 2026-07-11 | Fukuemon | scaffold gate の spec-review で PASS。未確定事項に決定者 / 期限を追記                                           |
| 2026-07-11 | Fukuemon | phase: clarify で D1 (symbol 情報の受け渡し経路) を決定                                                         |
| 2026-07-11 | Fukuemon | phase: clarify で D2 (= Design Doc Q3。Console ツリー表現) を決定                                               |
| 2026-07-11 | Fukuemon | phase: clarify で D3 (JSON schema / 版管理) を決定。traversal feature doc への変更提案 (`minDepth` 公開) が発生 |

## 備考

- 追加 appendix (API / database / authorization / screen-spec / testid) はいずれも本 spec のスコープに該当しないため取り込まない (CLI ツールであり、HTTP endpoint / 永続層 / ロール / 画面を持たない)。
