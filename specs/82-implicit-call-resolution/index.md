# framework 由来の暗黙呼び出し解決

## メタ情報

- Issue: `#82`
- ステータス: `Draft`
- 作成日: 2026-08-11
- 更新日: 2026-08-12
- Branch: `feature/82`
- Owner: Fukuemon

## 設計フェーズ状況

状態は `未着手 / 進行中 / 完了 / レビュー済 / 保留` のいずれか。保留の場合は理由を備考に残す。

| #   | フェーズ                    | 状態       | 最終更新   | 備考                                         |
| --- | --------------------------- | ---------- | ---------- | -------------------------------------------- |
| 1   | 起票                        | 完了       | 2026-08-11 | issue #82 / requirements.md 起票済み         |
| 2   | 下書き                      | 完了       | 2026-08-11 | 実装突合: 対象 (既存 java-analyzer への増分) |
| 3   | 上位文書突合                | 完了       | 2026-08-11 | 矛盾 (変更提案) なし                         |
| 4   | 論点整理                    | 完了       | 2026-08-11 | D1〜D8 を洗い出し                            |
| 5   | 論点解決                    | レビュー済 | 2026-08-12 | D1〜D8 全件確定。clarify gate レビュー PASS  |
| 6   | Interface / Routing 設計    | 未着手     |            |                                              |
| 7   | Content / Data 設計         | 未着手     |            |                                              |
| 8   | Performance / Security 設計 | 未着手     |            |                                              |
| 9   | Test / Metrics 設計         | 未着手     |            |                                              |
| 10  | 実装分割                    | 未着手     |            |                                              |
| 11  | レビュー済                  | 未着手     |            |                                              |

## 上位文書整合

- PRD 更新要否: 不要 (統合モード。DesignDoc の Why/What は Future Work の消化で、スコープ変更なし)
- Design Doc 更新要否: 要 (完了後に Future Work「解析精度の強化」の記述を更新。sync / closeout で扱う)
- ADR 起票要否: 不要 (D2 で opaque metadata 表現に確定し Protocol 契約変更が発生しないため。sync 時に ADR 化基準で再判定する)

| 上位文書    | 節 / 該当箇所                                                        | 整合方針 (継承 / 補足 / 変更提案)                                                                                        |
| ----------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| Design Doc  | Non Goals (Runtime Trace / Reflection 非対象) / Future Work          | 継承 (静的解析の範囲内で edge を増やす。実行時計測はしない)                                                              |
| feature doc | java-analyzer: 救済規則・outcome ledger・完全性 gate (`analysis.md`) | 補足 (新分類・新 edge も outcome ledger の終端保証と `silentOmission == 0` に従う)                                       |
| feature doc | java-analyzer: `protocol-mapping.md`「lambda は独立 node にしない」  | 継承 (callable invocation 解決でも symbolKind enum / node 化方針は変えない。edge の張り先は D5 で確定)                   |
| feature doc | analyzer-protocol: `callEdge.metadata` / `diagnostic.code` は opaque | 補足 (entry point 分類・イベント edge の標識は opaque metadata / `JAVA_` code 新設で表現する。D2 で確定済み)             |
| feature doc | output: JSON の `nodes[].metadata` / `edges[].metadata` 透過表出     | 変更提案 (JSON は既存透過で表出可能。Console への entry point 表示を D6 で決定済みのため、sync で output doc を改訂する) |
| context     | architecture: Java Analyzer 内部境界 (SootUp / JavaParser 隔離)      | 継承 (ArchUnit gate を維持。新機構も既存 layer 内に置く)                                                                 |
| context     | engineering: 依存方向 gate / testing: 検証境界                       | 継承                                                                                                                     |
| ADR-0004    | Runtime Trace 保留 / 根拠なき推測の禁止 / 観測可能性の方針           | 継承 (ソース根拠のある edge のみ追加。条件評価はしない)                                                                  |
| ADR-0005    | SootUp + Spring DI 解決 (candidate edge / resolution / provenance)   | 補足 (イベント edge の候補列挙・曖昧扱いは DI 候補 edge の既存規則に揃える)                                              |
| ADR-0007    | レイヤードアーキテクチャ                                             | 継承                                                                                                                     |

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
- Console tree への entry point 標識の表示 (output / Go。意味解釈は entry point key に限定)

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

なし (D1〜D8 の全件を解決済み。「解決済みの論点」を参照)

## 解決済みの論点

- **D2: entry point / イベント edge は opaque metadata + `JAVA_` diagnostic code 新設で表現し、Protocol schema は変更しない** (決定 2026-08-11)
  - 表現: entry point は `methodSymbol.metadata` の標識、イベント edge は `callEdge.metadata` (`provenance` の値追加)。診断は `JavaDiagnosticCode` enum への code 追加
  - 根拠: metadata / diagnostic code は opaque 契約のため Core parser / validator 無変更で成立する。JSON 出力は既存の `nodes[].metadata` / `edges[].metadata` 透過で即座に表出される。ADR 不要
  - トレードオフ / 却下した代替案: schema 拡張 (新 field / record) は言語非依存の一級表現になるが、Protocol 契約変更 (要 ADR)・Core 改修・他言語 Analyzer への契約負担が生じ、複数言語要求がない現時点では過剰設計。Console で意味解釈が必要になった場合の契約整理は D6 で扱う
- **D3: 対象アノテーションは javax / jakarta 両版対応とし、meta-annotation は 1 段まで検出する** (決定 2026-08-12)
  - 集合: ライフサイクル `@Scheduled` / `@PostConstruct` / `@PreDestroy` (後者 2 つは `javax.annotation` / `jakarta.annotation` 両 FQN)、イベント `@EventListener` / `@TransactionalEventListener`、Web `@RequestMapping` + Spring 提供 composed (`@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` / `@PatchMapping`) を既知集合として明示列挙
  - 検出深さ: 利用者定義の合成アノテーションは 1 段だけ辿る。2 段以上の入れ子は検出不能であり、制約として文書化する (検出できないものは診断も出せない)
  - トレードオフ / 却下した代替案: 直接付与のみは自作 composed が普通に使われる Web 層で取りこぼす。再帰解決は Spring の意味論に忠実だが実装・検証コストに対して 2 段以上の実例が稀
- **D7: イベント edge は broadcast 意味論を反映した専用規則で表現する** (決定 2026-08-12)
  - 規則: caller = `publishEvent()` call site の囲みメソッド、callee = 引数の静的型とその型階層に合致する listener。`provenance` に `spring-event` を追加
  - 確度: 無条件 listener への edge は複数あっても各々確定 (`resolution: unique`)。条件付き listener のみ `conditional: true` + `conditionTypes` (既存規則流用) で `ambiguous`
  - 根拠: Spring DI は実行時に 1 つだけ配線されるため複数候補 = 曖昧が正しいが、イベントは合致 listener が全て実行される broadcast 意味論であり、複数 listener への edge を「曖昧」とすると利用者が確度を誤読する
  - requirements R3 の精密化: 「一意に絞れない場合は曖昧候補」は条件付き listener に限って適用する (requirements.md に追記済み)
  - 制約: generics を使ったイベント型の突合は raw type 一致で近似し、制約として文書化する
- **D5: callable invocation edge は method reference → 参照先メソッド、lambda → 囲みメソッド (標識付き) へ張る** (決定 2026-08-12)
  - 規則: invocation site の囲みメソッドを caller とし、method reference は参照先メソッドへ、lambda は定義側の囲みメソッドへ edge を張る。通常呼び出しと区別する標識 metadata (`viaCallableInvocation: true` 等、既存 `viaLambda` / `viaMethodReference` とは独立) を付ける
  - 根拠: lambda 本体は独立 node ではなく囲みメソッド node の一部 (protocol-mapping の決定を維持)。囲みメソッドへの edge は「invoker は M 内で定義されたコードを実行する」を表し、影響調査の到達性 (lambda 本体の変更が invoker に伝播する) として正確
  - トレードオフ / 却下した代替案: lambda 側 edge は囲みメソッド全体への近似で粒度が粗い (標識で明示し文書化)。method reference のみ対応は V3 が部分達成に留まる。診断のみは V3 未達で requirements 改訂が必要になるため却下
- **D1: callable の静的追跡範囲は「同一メソッド内 + workspace メソッドへの引数渡し 1 段」とする** (決定 2026-08-12)
  - 規則: (1) 同一メソッド内の local 変数経由の invocation、(2) workspace メソッドの functional interface parameter へ call site から渡された callable と、そのメソッド内の parameter への invocation の突合。複数 call site から異なる callable が渡る場合は各 edge を call site 根拠付きで全列挙する
  - 根拠: template method パターン (`retry(() -> doWork())`) が実コードで支配的。#27 実測の残余も「chain / lambda の起点が scope 内」の形状に収束しており、1 段写像で汎用 dataflow 解析なしに閉じる
  - 対象外: field 経由・多段の受け渡し・Bean 境界越えは diagnostic に残し、効果実測後に拡張を判断する。invocation site が外部ライブラリ内にあるケース (`stream.map(...)` 等) は原理的に対象外 (workspace 内に invocation site が存在しない)
  - トレードオフ / 却下した代替案: 同一メソッド内のみは実コードで稀なパターンしか拾えない。field・多段対応は dataflow 解析の複雑度と誤 edge リスクが跳ね、R1 (根拠なき推測の禁止) と緊張する
- **D6: entry point 標識は JSON 透過に加えて Console にも表示する** (決定 2026-08-12)
  - 規則: JSON は既存の `nodes[].metadata` 透過で表出する (実装不要)。Console は出力層で entry point 標識 (metadata key) を意味解釈し、tree の該当 node 行に根拠 (検出アノテーション) を表示する
  - 影響: 「Output は metadata を意味解釈しない」(protocol-mapping で決定済み) の改訂と output feature doc の更新が必要 = 上位文書への変更提案。sync phase で back-propagate する。実装対象に `output` (Go) が加わる
  - 根拠: Console は主要 UI であり、V1 (entry point と未解決の区別) の体験を Console で完結させる
  - トレードオフ / 却下した代替案: JSON 完結案は本 issue が java-analyzer に閉じて小さいが、Console 利用者が entry point 標識を直接見られない。意味解釈の対象は entry point 標識の key に限定し、他 metadata (dispatch / viaLambda 等) の Console 表示は Future Work「CLI の使い勝手」に残す (スコープ拡大を防ぐ)
- **D8: テストは既存方式を踏襲し、ロジック = Java unit / 系統ごとの最小再現 = patterns fixture + required E2E / Console = Go unit で担保する** (決定 2026-08-12)
  - 配分: entry point 検出・イベント突合・callable 追跡・diagnostic の分岐は Java unit (#30 の複数 context 基盤)。系統ごとの最小再現 fixture が CLI 経由で edge / 標識になる成功期待は patterns fixture + required E2E (既存方式)。Console の entry point 表示は Go 側 output の unit test。`silentOmission == 0` / outcome ledger 終端の非回帰は既存の完全性 gate テストの対象拡大
  - 根拠: 既存 `context/testing.md` / #30 の検証境界の整理とそのまま揃い、保守の認知負荷が最小
  - トレードオフ / 却下した代替案: Java unit のみは CLI までの保証がなく受け入れ確認が手動になる。E2E 中心は原因特定が遅く、meta-annotation 版差などの分岐網羅でテスト時間が膨らむ
- **D4: 実装は 4 分割とし、P1 (検出基盤 + entry point) を起点に P2 (イベント) / P4 (Console) が続き、P3 (callable) は並列とする** (決定 2026-08-12)
  - 分割: P1 アノテーション検出基盤 + entry point 分類 (java-analyzer) / P2 イベント突合 + edge 生成 (java-analyzer、P1 の検出基盤に依存) / P3 callable 追跡 (java-analyzer、独立) / P4 Console の entry point 表示 (output、P1 の metadata key に依存)
  - 根拠: PR が系統単位で独立して revert でき、各 prompt に fixture + unit + E2E を同梱できて D8 の検証境界と一致する。P3 は P1 と並列実装できる
  - トレードオフ / 却下した代替案: 基盤先行の 5 分割は P0 単独で利用者価値がなく過剰設計を誘発する。2 分割は java-analyzer 側 PR が肥大しレビューと revert が困難

## 未確定事項

なし (D1〜D8 の全件を解決済み)

## 実装対象

正規 target は `context/project.yml` の対象ドメイン一覧を正本とする。

| モジュール          | 実装有無 | 主な責務                                                                           |
| ------------------- | :------: | ---------------------------------------------------------------------------------- |
| `core`              |    -     | 変更なし見込み (metadata は opaque passthrough 済み。D2 の決定で変わり得る)        |
| `traversal`         |    -     | 変更なし見込み (metadata を解釈しない既存契約を維持)                               |
| `output`            |    ◯     | Console tree への entry point 標識の表示 (D6。意味解釈は entry point key に限定)   |
| `analyzer-protocol` |    -     | 変更なし (D2 で opaque metadata 表現に確定。schema / Core parser 無変更)           |
| `java-analyzer`     |    ◯     | entry point 分類 / イベント突合 index / callable 追跡 / diagnostic code 追加の実装 |

## 機能仕様

### User Flow

1. 利用者が既存の `depwalk analyze ... --method <selector> --direction caller|callee` を実行する (新規操作なし)
2. Java Analyzer が解析時に entry point 分類・イベント edge・callable invocation edge を追加で出力する
3. 利用者は出力 (Console / JSON) で、entry point の終端根拠・イベント経由の伝播・callable 経由の伝播を確認する
4. 解決できなかったケースは diagnostic として理由コード付きで確認する

### Reuse Policy

- 新機構 (アノテーション index / イベント突合 / callable 追跡) は java-analyzer の既存 layer 構造 (ADR-0007) 内に置き、SpringDiIndex 等の既存 index 基盤の設計に揃える
- Core / traversal への変更はしない。output は D6 で確定した entry point 標識の表示のみ変更する (他 metadata の Console 表示は Future Work へ)

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

- ロジック (entry point 検出 / イベント突合 / callable 追跡 / diagnostic) は Java unit、系統ごとの最小再現は patterns fixture + required E2E、Console 表示は Go 側 output unit で担保する (D8 で確定。`context/testing.md` と #30 の基盤に従う)

## Interface 設計

### UI / API / Event Interface

- CLI / Protocol の外形は変更しない (既存 `depwalk analyze` の引数・JSONL record 種のまま)
- Console: tree の該当 node 行に entry point 標識 (検出アノテーション名) を表示する。表示書式の詳細は P4 prompt で確定し、output feature doc へ sync する (D6)
- JSON: `nodes[].metadata` / `edges[].metadata` の既存透過で新標識を表出する (実装不要)

### Props / Request / Response

以下の metadata key / diagnostic code は仮称。最終名は prompts phase で確定し、protocol-mapping.md へ sync する。

- `methodSymbol.metadata.entryPoint` (string 配列): 検出した entry point アノテーションの FQN。D3 の既知集合 + 1 段 meta-annotation 由来
- `callEdge.metadata.provenance` へ値 `spring-event` を追加 (イベント edge。D7)
- `callEdge.metadata.viaCallableInvocation: true` (callable invocation edge。既存 `viaLambda` / `viaMethodReference` とは独立。D5)
- diagnostic code 追加 (`JavaDiagnosticCode` enum): イベント型未解決 / callable 追跡不能の 2 系統 (severity はいずれも `info` または `warning`、prompts phase で確定)

## Content / Data 設計

### 保存・管理するデータ

いずれも解析実行中の in-memory index であり、永続化しない (ADR-0002 の「永続ストアを持たない」を維持)。

- アノテーション index: workspace 内メソッドの entry point アノテーション付与 (D3 の既知集合 + 1 段 meta-annotation) を 1st pass で収集。SpringDiIndex と同じ構築様式
- イベント index: `@EventListener` / `@TransactionalEventListener` メソッドを引数型 (raw type) で引ける表。publishEvent call site の引数静的型 + 型階層と突合する (D7)
- callable 突合表: workspace メソッドの functional interface parameter への invocation site と、call site から渡された lambda / method reference の 1 段写像 (D1)

### コンテンツ配置 / package / route

- java-analyzer 内の配置は ADR-0007 の layer 構造に従う (詳細は実装分割時)

## Performance / Security 設計

### Performance

- 新設 index (アノテーション / イベント / callable 突合表) は 1st pass で 1 回だけ構築し、call site ごとの走査を索引参照に置き換える (SpringDiIndex と同じ構築様式)。構築コストは workspace のメソッド数・call site 数に線形
- 受け入れ計測は実プロジェクト実測 (#27 と同手法) で行い、解析時間の顕著な悪化がないことを確認する。閾値の数値化はせず、実測値の前後比較で判断する

### Security / Privacy

- diagnostic / metadata の sanitize 制約 (source 本文・絶対 path・classpath entry・credential・raw exception message の禁止) を新設 code でも維持する

## Error / Fallback 設計

### エラーケース

| #   | ケース                                         | ユーザーへの見せ方                                    | リカバリ                         |
| --- | ---------------------------------------------- | ----------------------------------------------------- | -------------------------------- |
| 1   | publishEvent の引数型が解決できない            | diagnostic (理由コード付き)                           | 既存の未解決診断と同じ運用       |
| 2   | listener が条件付きで一意に絞れない            | 曖昧候補として全列挙 + 条件付き事実を metadata に記録 | 既存の条件付き Bean の扱いと同じ |
| 3   | callable が D1 の追跡範囲外 (field 経由・多段) | diagnostic (理由コード付き)                           | 効果実測後に範囲拡張を判断       |
| 4   | 2 段以上の meta-annotation 入れ子              | 検出不能 (検出できないものは診断も出せない)           | 制約として文書化する (D3)        |

### Fallback

- 新機構の解決失敗は request を fatal にしない (diagnostic 終端)。新設 diagnostic は完全性 gate の primary outcome に加えず、advisory として扱う — 本 issue は既存保証の上に edge を追加するものであり、追加分の未解決で既存の成功挙動を fatal 側へ変えない。`silentOmission == 0` は維持する

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

D4 で確定した 4 分割。各 prompt に fixture + unit + E2E を同梱する (D8)。

| Phase | 対象            | 概要                                                                             | 依存               |
| ----- | --------------- | -------------------------------------------------------------------------------- | ------------------ |
| P1    | `java-analyzer` | アノテーション検出基盤 (D3 の既知集合 + 1 段 meta-annotation) + entry point 分類 | なし               |
| P2    | `java-analyzer` | イベント index + publish→listener edge 生成 (D7 の専用規則)                      | P1 (検出基盤)      |
| P3    | `java-analyzer` | callable 追跡 (D1 の 1 段写像) + invocation edge (D5 の意味論)                   | なし (P1 と並列可) |
| P4    | `output`        | Console tree への entry point 標識表示 (D6)                                      | P1 (metadata key)  |

### prompts 生成方針

- P1 → {P2, P4} の依存、P3 は独立。P1 完了後は P2 / P3 / P4 を並列実装できる
- 各 prompt は java-analyzer / output のドメイン境界で閉じ、モジュールをまたがない

## 上位資料からの変更点

本 spec で PRD / Design Doc / feature doc / context / 既存 ADR から変更・追加した内容を、反映先別に記録する。track / sync phase で更新する。

### Design Doc への影響

| 対象節                     | 変更内容                                                                     | 理由                |
| -------------------------- | ---------------------------------------------------------------------------- | ------------------- |
| Future Work (Rollout Plan) | 「解析精度の強化」の項を実装完了後に消化済みとして更新する (source: clarify) | Rollout Plan の消化 |

### feature doc への影響

| 対象 doc / 節                                                                                 | 変更内容                                                                                                                                 | 理由                                                        |
| --------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| java-analyzer `protocol-mapping.md` (metadata 契約 / diagnostic code 体系)                    | entry point 標識・イベント edge の metadata key と新設 `JAVA_` code を追記 (source: clarify D2)                                          | opaque metadata 表現の決定を正本へ反映する                  |
| java-analyzer `protocol-mapping.md` / analyzer-protocol feature doc (metadata の Core 内保持) | **変更提案**: 「Output は metadata を意味解釈しない」を「entry point 標識 key に限り Console が意味解釈する」へ改訂 (source: clarify D6) | Console での entry point 表示の決定と既存契約が矛盾するため |
| output `DesignDoc_output.md` (Console ツリー表現 / 行の書式)                                  | **変更提案**: entry point 標識の表示規則を追加 (source: clarify D6)                                                                      | Console の行書式が変わるため正本の更新が必要                |

### context への影響

なし — 新機構は java-analyzer / output の既存境界内に収まり、architecture / engineering / testing の規約変更を伴わない (D6 の Console 変更も output モジュール内で閉じる)。

### ADR の新規 / 更新

なし — D2 で Protocol schema 変更が発生しないと確定したため新規 ADR は不要。ADR-0002 (永続ストアなし) / ADR-0004 (Runtime Trace 保留) / ADR-0007 (layer 構造) はいずれも継承であり更新しない。sync 時に ADR 化基準で再判定する。

## レビュー

`spec-review` (fresh-context evaluator) の最新結果。完全な記録は `review.md` を参照。

| 日付       | 結果 (PASS / NEEDS_WORK) | 指摘要点                                                                       | 対応   |
| ---------- | ------------------------ | ------------------------------------------------------------------------------ | ------ |
| 2026-08-12 | NEEDS_WORK               | メタ同期漏れ 2 件・空表行 2 件・プレースホルダ残骸 1 件 (設計判断の変更は不要) | 対応済 |
| 2026-08-12 | PASS                     | 前回指摘 5 件の解消を確認。sync 時に D2 の ADR 化基準再判定を実施すること      | —      |

## 変更履歴

| 日付       | 変更者   | 変更内容                                   |
| ---------- | -------- | ------------------------------------------ |
| 2026-08-11 | Fukuemon | scaffold: index.md 初版を起草              |
| 2026-08-12 | Fukuemon | clarify: D1〜D8 を確定し全セクションへ展開 |

## 備考

- appendix (api / database / authorization / screen-spec / testid) は該当スコープがないため取り込まない
- 増分判定: 既存 java-analyzer 実装への増分。clarify 冒頭の「実装との突合ゲート」の対象
