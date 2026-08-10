# framework 由来の暗黙呼び出し解決

## メタ情報

- Issue: `#82`
- ステータス: `Draft`
- 作成日: 2026-08-11
- 更新日: 2026-08-11
- Branch: `feature/82`
- Owner: Fukuemon

## 設計フェーズ状況

状態は `未着手 / 進行中 / 完了 / レビュー済 / 保留` のいずれか。保留の場合は理由を備考に残す。

| #   | フェーズ                    | 状態   | 最終更新   | 備考                                         |
| --- | --------------------------- | ------ | ---------- | -------------------------------------------- |
| 1   | 起票                        | 完了   | 2026-08-11 | issue #82 / requirements.md 起票済み         |
| 2   | 下書き                      | 完了   | 2026-08-11 | 実装突合: 対象 (既存 java-analyzer への増分) |
| 3   | 上位文書突合                | 完了   | 2026-08-11 | 矛盾 (変更提案) なし                         |
| 4   | 論点整理                    | 完了   | 2026-08-11 | D1〜D8 を洗い出し                            |
| 5   | 論点解決                    | 未着手 |            |                                              |
| 6   | Interface / Routing 設計    | 未着手 |            |                                              |
| 7   | Content / Data 設計         | 未着手 |            |                                              |
| 8   | Performance / Security 設計 | 未着手 |            |                                              |
| 9   | Test / Metrics 設計         | 未着手 |            |                                              |
| 10  | 実装分割                    | 未着手 |            |                                              |
| 11  | レビュー済                  | 未着手 |            |                                              |

## 上位文書整合

- PRD 更新要否: 不要 (統合モード。DesignDoc の Why/What は Future Work の消化で、スコープ変更なし)
- Design Doc 更新要否: 要 (完了後に Future Work「解析精度の強化」の記述を更新。sync / closeout で扱う)
- ADR 起票要否: 未確定 (D2: Protocol 表現が opaque metadata で閉じるなら不要の見込み)

| 上位文書    | 節 / 該当箇所                                                        | 整合方針 (継承 / 補足 / 変更提案)                                                                                 |
| ----------- | -------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| Design Doc  | Non Goals (Runtime Trace / Reflection 非対象) / Future Work          | 継承 (静的解析の範囲内で edge を増やす。実行時計測はしない)                                                       |
| feature doc | java-analyzer: 救済規則・outcome ledger・完全性 gate (`analysis.md`) | 補足 (新分類・新 edge も outcome ledger の終端保証と `silentOmission == 0` に従う)                                |
| feature doc | java-analyzer: `protocol-mapping.md`「lambda は独立 node にしない」  | 継承 (callable invocation 解決でも symbolKind enum / node 化方針は変えない。edge の張り先は D5 で確定)            |
| feature doc | analyzer-protocol: `callEdge.metadata` / `diagnostic.code` は opaque | 補足 (entry point 分類・イベント edge の標識は opaque metadata / `JAVA_` code 新設で表現できる見込み。D2 で確定)  |
| feature doc | output: JSON の `nodes[].metadata` / `edges[].metadata` 透過表出     | 補足 (JSON は既存透過で表出可能。Console での entry point 表現は D6 で確定し、必要なら sync で output doc へ反映) |
| context     | architecture: Java Analyzer 内部境界 (SootUp / JavaParser 隔離)      | 継承 (ArchUnit gate を維持。新機構も既存 layer 内に置く)                                                          |
| context     | engineering: 依存方向 gate / testing: 検証境界                       | 継承                                                                                                              |
| ADR-0004    | Runtime Trace 保留 / 根拠なき推測の禁止 / 観測可能性の方針           | 継承 (ソース根拠のある edge のみ追加。条件評価はしない)                                                           |
| ADR-0005    | SootUp + Spring DI 解決 (candidate edge / resolution / provenance)   | 補足 (イベント edge の候補列挙・曖昧扱いは DI 候補 edge の既存規則に揃える)                                       |
| ADR-0007    | レイヤードアーキテクチャ                                             | 継承                                                                                                              |

## 関連資料

- `design/DesignDoc.md`: Why/What (統合 PRD) / Future Work「解析精度の強化」
- 関連 issue / ticket: [#82](https://github.com/Fukuemon/depwalk/issues/82) (本 spec) / [#27](https://github.com/Fukuemon/depwalk/issues/27) (実測手法の前例) / [#21](https://github.com/Fukuemon/depwalk/issues/21) (Interface Dispatch / Spring DI 解決)
- 要求文書: [requirements.md](requirements.md) (受け入れ基準 EARS 6 件の正本)

## 背景

- framework が実行時に起動する暗黙呼び出し (アノテーション駆動 entry point / イベント publish→listener / 値渡しされた callable の invocation) が edge にならず、変更影響調査の探索が途切れる
- DesignDoc 成功条件 S1/S2 (caller / callee の網羅列挙) の精度を実プロジェクト水準へ引き上げる。Future Work で最優先とされた「解析精度の強化」の実装単位
- 影響範囲は Java Analyzer (解析・分類の実装) を主とし、Protocol / Output は表現の確定 (D2 / D6) に応じて従属的に変わる

## スコープ

### やること

- アノテーション駆動 entry point の分類: `@Scheduled` / `@PostConstruct` / `@PreDestroy` / Web handler (`@RequestMapping` / `@GetMapping` 等の合成アノテーション含む) を entry point としてマークし、caller 探索の終端根拠として出力する (edge は作らない)
- イベント edge の解決: `ApplicationEventPublisher#publishEvent()` の引数型 (型階層含む) と `@EventListener` / `@TransactionalEventListener` の listener メソッドを突合し、候補 edge を生成する
- callable 値渡しの invocation 解決: functional interface の invocation site から、静的に追跡可能な範囲で渡された lambda / method reference 実体への edge を生成する
- 各系統の解決不能ケースの diagnostic 分類 (`JAVA_` code 体系への追加) を定める

### やらないこと

- Mapper 系マーカーの拡張 (`@FeignClient` / XML ベース MyBatis)
- Runtime Trace / Reflection / AspectJ Runtime / 実行時 Proxy 解析 (ADR-0004 の保留を維持)
- 条件アノテーション (`@Profile` 等) の条件評価 (既存方針どおり記録のみ)
- `@Async` の非同期境界の表現変更 (呼び出し edge は既存解決で生成済み)
- Protocol の `symbolKind` enum 変更・lambda の独立 node 化 (protocol-mapping の既存決定を維持)

## 要件の解釈

### 実現したいユーザー価値

- 変更影響調査で「呼び出し元なし」の理由 (探索し尽くした / framework が呼ぶ / 解析が届かない) を判別できる
- イベント経由・callable 経由の影響伝播を探索で追える

### 成功条件

- requirements.md の V1〜V4 (entry point の根拠付き分類 / イベント edge / callable invocation edge / silent omission ゼロ維持)

### 対象ユーザー / 操作主体

- 変更影響調査を行う開発者 (CLI 利用者)。操作主体は既存 `depwalk analyze` の実行者で、新たな操作は増やさない

EARS 風の振る舞い記述は [requirements.md](requirements.md) の「受け入れ基準 (EARS)」6 件を正本とする (二重管理しない)。

## 設計時の論点

設計 / 実装フェーズへ持ち越す残課題を 1 件ずつ管理する。確定したものは「解決済みの論点」へ移す。

| #   | 論点                                                                                                                            | 決定候補                                                                                                                          | 決定 |
| --- | ------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- | ---- |
| D1  | callable 値渡しの静的追跡範囲をどこまで広げるか                                                                                 | (a) 同一メソッド内のみ / (b) 同一クラス内 (field 代入含む) / (c) Bean 境界越え                                                    | 未決 |
| D2  | entry point / イベント edge の Protocol 表現。opaque metadata + `JAVA_` diagnostic code 新設で閉じるか、schema 拡張 (要 ADR) か | opaque metadata で閉じる見込み (methodSymbol.metadata に entry point 標識 / callEdge.metadata にイベント標識)                     | 未決 |
| D3  | 対象アノテーション集合の確定 (jakarta / javax の版差、meta-annotation の検出深さ)                                               | Spring 標準 + jakarta/javax 両対応 / 合成アノテーションは 1 段 or 再帰                                                            | 未決 |
| D4  | 実装分割 (entry point / イベント / callable を prompts でどう分けるか)                                                          | 3 系統を独立 prompt に分割 (相互依存が薄い)                                                                                       | 未決 |
| D5  | lambda invocation edge の張り先の意味論。「lambda は独立 node にしない」制約下で invocation site → 何に edge を張るか           | (a) lambda の囲みメソッドへ張り標識 metadata で区別 / (b) edge を張らず診断のみ / (c) method reference のみ対象にし lambda は外す | 未決 |
| D6  | entry point 分類の Output 表現 (JSON は metadata 透過で足りるか、Console tree での見せ方、output feature doc への反映要否)      | JSON: 既存 `nodes[].metadata` 透過 / Console: 表現追加の要否を判断                                                                | 未決 |
| D7  | イベント edge の表現規則 (caller は publishEvent call site の囲みメソッドか、resolution / provenance / conditional の値体系)    | DI candidate edge の metadata 規則 (`resolution` / `provenance` / `conditional`) に揃え、provenance へ `spring-event` 等を追加    | 未決 |
| D8  | テストの検証境界 (Java unit / fixture / required E2E のどこで何を担保するか)                                                    | 分類・突合ロジックは Java unit、E2E は patterns fixture 方式 (#30 の基盤) を踏襲                                                  | 未決 |

## 解決済みの論点

(clarify phase で確定したものを「設計時の論点」から移す)

## 未確定事項

- D1〜D8 (上表)。1 件でも残っていれば下流 phase は止める

## 実装対象

正規 target は `context/project.yml` の対象ドメイン一覧を正本とする。

| モジュール          | 実装有無 | 主な責務                                                                           |
| ------------------- | :------: | ---------------------------------------------------------------------------------- |
| `core`              |    -     | 変更なし見込み (metadata は opaque passthrough 済み。D2 の決定で変わり得る)        |
| `traversal`         |    -     | 変更なし見込み (metadata を解釈しない既存契約を維持)                               |
| `output`            |  未確定  | D6 の決定次第 (Console での entry point 表現を追加する場合のみ)                    |
| `analyzer-protocol` |  未確定  | D2 の決定次第 (opaque metadata で閉じれば変更なし)                                 |
| `java-analyzer`     |    ◯     | entry point 分類 / イベント突合 index / callable 追跡 / diagnostic code 追加の実装 |

## 機能仕様

### User Flow

1. 利用者が既存の `depwalk analyze ... --method <selector> --direction caller|callee` を実行する (新規操作なし)
2. Java Analyzer が解析時に entry point 分類・イベント edge・callable invocation edge を追加で出力する
3. 利用者は出力 (Console / JSON) で、entry point の終端根拠・イベント経由の伝播・callable 経由の伝播を確認する
4. 解決できなかったケースは diagnostic として理由コード付きで確認する

### Reuse Policy

- 新機構 (アノテーション index / イベント突合 / callable 追跡) は java-analyzer の既存 layer 構造 (ADR-0007) 内に置き、SpringDiIndex 等の既存 index 基盤の設計に揃える
- Core / traversal / output への先回りした変更はしない (D2 / D6 が「必要」と確定した範囲のみ)

### Performance

- イベント突合・callable 追跡は index ベースで行い、call site ごとの全件走査を避ける (`context/architecture.md` の方針に従う)
- 解析時間の顕著な悪化を伴わないことを実測 (既存の実測手法 #27) で確認する

### Routing / URL State

- なし (CLI ツールのため該当しない)

### Content / Assets

- なし (CLI ツールのため該当しない)

### UI Reuse

- なし (CLI ツールのため該当しない)

### Testing

- 分類・突合・追跡ロジックの検証境界は D8 で確定する (Java unit / patterns fixture / required E2E の責務分担。`context/testing.md` と #30 の unit test 基盤に従う)

## Interface 設計

### UI / API / Event Interface

- (clarify で D2 / D6 / D7 確定後に記述)

### Props / Request / Response

- (clarify で D2 確定後、Protocol record への具体的な metadata key / diagnostic code を記述)

## Content / Data 設計

### 保存・管理するデータ

- (clarify で D1 / D7 確定後、アノテーション index / イベント index / callable 追跡のデータ構造を記述)

### コンテンツ配置 / package / route

- java-analyzer 内の配置は ADR-0007 の layer 構造に従う (詳細は実装分割時)

## Performance / Security 設計

### Performance

- (diagram / track phase までに index 構築コストと突合コストの見積もりを記述)

### Security / Privacy

- diagnostic / metadata の sanitize 制約 (source 本文・絶対 path・classpath entry・credential・raw exception message の禁止) を新設 code でも維持する

## Error / Fallback 設計

### エラーケース

| #   | ケース                                   | ユーザーへの見せ方                                    | リカバリ                         |
| --- | ---------------------------------------- | ----------------------------------------------------- | -------------------------------- |
| 1   | publishEvent の引数型が解決できない      | diagnostic (理由コード付き)                           | 既存の未解決診断と同じ運用       |
| 2   | listener が条件付きで一意に絞れない      | 曖昧候補として全列挙 + 条件付き事実を metadata に記録 | 既存の条件付き Bean の扱いと同じ |
| 3   | callable が静的に追跡できない            | diagnostic (理由コード付き)                           | 追跡範囲の境界は D1 で確定       |
| 4   | meta-annotation 経由の付与を検出できない | diagnostic                                            | 検出深さは D3 で確定             |

### Fallback

- 新機構の解決失敗は request を fatal にしない (diagnostic 終端)。完全性 gate との関係は clarify で確定する

## テスト / 評価方針

### テスト観点

- requirements.md の EARS 6 件を検証可能な粒度に落とす (検証境界は D8)
- `silentOmission == 0` と outcome ledger 終端保証の非回帰

### 計測指標

- 新分類・新 edge の解決件数 / 診断件数 (metadata 集計)
- 実プロジェクト実測 (#27 と同手法) での未解決率の変化

## フロー / シーケンス

(diagram phase で生成)

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

(track / prompts phase で確定。D4 の決定に従う)

| Phase | 対象 | 概要 | 依存 |
| ----- | ---- | ---- | ---- |
| P1    |      |      |      |

### prompts 生成方針

- entry point 分類 / イベント edge / callable 追跡は解析機構が独立しており、並列実装候補 (D4 で確定)

## 上位資料からの変更点

本 spec で PRD / Design Doc / feature doc / context / 既存 ADR から変更・追加した内容を、反映先別に記録する。track / sync phase で更新する。

### Design Doc への影響

| 対象節 | 変更内容 | 理由 |
| ------ | -------- | ---- |
|        |          |      |

### feature doc への影響

| 対象 doc / 節 | 変更内容 | 理由 |
| ------------- | -------- | ---- |
|               |          |      |

### context への影響

| 対象 doc / 節 | 変更内容 | 理由 |
| ------------- | -------- | ---- |
|               |          |      |

### ADR の新規 / 更新

| ADR ID | 変更内容 | 理由 |
| ------ | -------- | ---- |
|        |          |      |

## レビュー

`spec-review` (fresh-context evaluator) の最新結果。完全な記録は `review.md` を参照。

| 日付 | 結果 (PASS / NEEDS_WORK) | 指摘要点 | 対応 |
| ---- | ------------------------ | -------- | ---- |
|      |                          |          |      |

## 変更履歴

| 日付       | 変更者   | 変更内容                      |
| ---------- | -------- | ----------------------------- |
| 2026-08-11 | Fukuemon | scaffold: index.md 初版を起草 |

## 備考

- appendix (api / database / authorization / screen-spec / testid) は該当スコープがないため取り込まない
- 増分判定: 既存 java-analyzer 実装への増分。clarify 冒頭の「実装との突合ゲート」の対象
