# framework 由来の暗黙呼び出し解決 要求定義

## 要求フェーズ状況

`/requirements-full` 用。状態は `未着手 / 進行中 / 完了 / レビュー済 / 保留` のいずれか。
保留の場合は理由(ユーザー判断待ち等)を備考に記載する。

| #   | フェーズ           | 状態 | 最終更新   | 備考                                                  |
| --- | ------------------ | ---- | ---------- | ----------------------------------------------------- |
| 1   | 受付               | 完了 | 2026-08-11 | Future Work「解析精度の強化」から起案                 |
| 2   | 下書き             | 完了 | 2026-08-11 |                                                       |
| 3   | スコープ/成功条件  | 完了 | 2026-08-11 | Mapper 系拡張は対象外と確定                           |
| 4   | 業務仕様           | 完了 | 2026-08-11 |                                                       |
| 5   | バリデーション方針 | 完了 | 2026-08-11 | CLI ツールのため入力検証は既存契約に従う              |
| 6   | 権限要件           | 完了 | 2026-08-11 | 該当なし (ローカル CLI)                               |
| 7   | 監査/非機能        | 完了 | 2026-08-11 |                                                       |
| 8   | 未決事項解消       | 完了 | 2026-08-13 | 4 件すべて spec の clarify (D1〜D4) で確定            |
| 9   | 最終レビュー       | 完了 | 2026-08-13 | 初版承認 2026-08-11、スコープ拡大分も選択式で承認済み |
| 10  | 公開/同期          | 完了 | 2026-08-11 | issue #82 起票済み                                    |

## チケット情報

- 起点: 自由文 (design/DesignDoc.md「Future Work — 解析精度の強化」)
- チケットID: 82
- トラッカー: github (Fukuemon/depwalk)
- URL: https://github.com/Fukuemon/depwalk/issues/82

## 背景・目的

depwalk の存在意義は「edge が 1 本欠けると答えが間違う」変更影響調査の網羅性にある。現状の Java Analyzer は明示的なメソッド呼び出し・型階層・Spring DI (constructor / field / setter injection) を解決するが、framework が実行時に起動する **暗黙の呼び出し** は edge にならない。このため次の探索が欠落する。

1. **アノテーション駆動の entry point**: `@Scheduled` / `@PostConstruct` / `@PreDestroy` / Web handler (`@RequestMapping` 系) は framework から呼ばれるため caller が存在しないが、現状は単に「呼び出し元なし」となり、「探索し尽くした」のか「解析が届いていない」のか利用者が判別できない
2. **イベント駆動の呼び出し**: `ApplicationEventPublisher#publishEvent()` と `@EventListener` / `@TransactionalEventListener` の対応が edge にならず、イベント経由の影響伝播が途切れる
3. **値として渡された callable**: lambda / method reference は「本体内の呼び出しの囲みメソッドへの帰属 (`viaLambda`)」と「method reference 式の参照先解決」までは対応済みだが、functional interface の invocation site (例: `callback.apply()`) から渡された実体への edge は解決されない

DesignDoc の Future Work で最優先とされた「解析精度の強化」の実装単位として本要求を定義する。

## 想定ユーザー/ステークホルダー

- 変更影響調査を行う開発者 (既存 PRD/DesignDoc の主ユーザーと同一)
- CI 上で影響範囲を機械的に確認するワークフロー (将来の CI 連携の前提精度を作る)

## 提供価値(成功条件)

| #   | 成功条件                                                                                                                       |
| --- | ------------------------------------------------------------------------------------------------------------------------------ |
| V1  | アノテーション駆動 entry point が caller 探索で「framework entry point」と根拠付きで分類され、未解決と区別できる               |
| V2  | イベント publish → listener の edge が生成され、イベント経由の caller / callee 探索が途切れない                                |
| V3  | functional interface 経由で起動される lambda / method reference の実体が、解決可能な範囲で edge になり、不能な場合は診断に残る |
| V4  | いずれの新分類・新 edge も silent omission を生まない (解決できないケースは必ず diagnostic / metadata として観測可能)          |
| V5  | stream / generics chain 形状の未解決が実測で減少する (実環境検証プロジェクトの再計測で未解決率 3.9% から改善。追記 2026-08-13) |
| V6  | 実環境の解析実行が阻害要因 (daemon JVM 非互換 / OOM) で raw 失敗せず、診断または文書化された手順で対処できる (追記 2026-08-13) |

## スコープ

### やること

- **アノテーション駆動 entry point の分類**: `@Scheduled` / `@PostConstruct` / `@PreDestroy` / Web handler (`@RequestMapping` / `@GetMapping` 等の合成アノテーション含む) / `@ExceptionHandler` / `@ModelAttribute` を entry point としてマークし、caller 探索の終端根拠として出力する (改訂 2026-08-13: 実環境検証プロジェクトの実測で後者 2 件の漏れを検出し追加)
- **イベント edge の解決**: `publishEvent()` の引数型 (型階層含む) と `@EventListener` / `@TransactionalEventListener` の listener メソッドを突合し edge を生成する
- **callable 値渡しの invocation 解決**: functional interface の invocation site から、静的に追跡可能な範囲で渡された lambda / method reference 本体への edge を生成する (追跡可能範囲の境界定義は設計で確定)
- **stream / generics chain の型解決強化** (追記 2026-08-13): 実環境検証プロジェクトの実測で未解決の支配形状が「stream / lambda chain 内の generics 型推論失敗」と判明したため、この形状の解決強化をスコープに加える。方式は設計で確定する (件数の定義: 未解決診断は計 2,114 件で、内訳は outcome ledger の未解決終端 2,062 件 + DI「Bean 候補なし」52 件。全 call site 52,411 の 3.9% が ledger 未解決終端)
- **codegen DAO interface の runtime-provided marker 追加** (追記 2026-08-13): annotation processing で実装が生成される DAO interface への DI 解決が「Bean 候補なし」となる形状 (実測 52 件) を、既存 `@Mapper` と同構造の marker 追加で解消する。受け入れは V4 の傘下 (解消されない場合も diagnostic として観測可能) とする
- **解析実行の運用堅牢化** (追記 2026-08-13): Gradle daemon JVM 非互換の回避手段の提供・文書化と、OutOfMemoryError の診断化 (raw stack で異常終了させない)・heap 指針の文書化をスコープに加える (実測で検出した実行阻害要因)
- 上記すべてで、解決不能ケースの diagnostic 分類 (理由コード) を定める

### やらないこと

- 実測根拠のない Mapper 系マーカーの拡張 (`@FeignClient` / XML ベース MyBatis) — 既存の `@Mapper` / Spring Data 対応で据え置き (改訂 2026-08-13: 実測で検出した codegen DAO marker の追加は「やること」へ移動)
- Runtime Trace / Reflection / AspectJ Runtime / 実行時 Proxy 解析 (ADR-0004 の保留を維持)
- 条件アノテーション (`@Profile` 等) の条件評価 (既存方針どおり記録のみ)
- `@Async` の非同期境界の表現変更 (呼び出し edge 自体は既存解決で生成されるため対象外)
- CLI の使い勝手改善 (診断の要約・フィルタ表示 / 設定ファイル / 実行時間短縮) — 実測で必要性を確認したが本 issue の対象外。Future Work「CLI の使い勝手」で扱う

## 業務ルール

| #   | ルール                                                                                                                               | 理由                                                                                                       | 備考                                      |
| --- | ------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------- | ----------------------------------------- |
| R1  | 新しい edge / 分類は必ずソース上の根拠 (アノテーション / 型 / AST) を伴う。根拠のない型推測で edge を作らない                        | 既存の解析原則 (根拠なき推測の禁止) との整合                                                               | ADR-0004 の決定と同方向                   |
| R2  | 解決できないケースは diagnostic に残し、silent omission ゼロを維持する                                                               | 精度偽装の防止。既存の完全性 gate 契約との整合                                                             | `silentOmission == 0` 不変                |
| R3  | イベント edge は合致 listener を全列挙する。無条件 listener への edge は各々確定として扱い、曖昧候補とするのは条件付き listener のみ | イベントは合致 listener が全て実行される broadcast 意味論であり、DI の「1 つだけ配線される」曖昧さと異なる | 改訂 (2026-08-12): spec D7 の決定で精密化 |
| R4  | entry point 分類は edge を作らず終端根拠のみ付与する (擬似 caller ノードを合成しない)                                                | runtime-provided マーカーの既存設計 (疑似ノード非合成) 踏襲                                                |                                           |

## 入出力要件

### 入力

| #   | 項目                    | 必須/任意 | 形式          | 制約/備考                                 |
| --- | ----------------------- | --------- | ------------- | ----------------------------------------- |
| 1   | 解析対象 workspace      | 必須      | 既存 CLI 引数 | 変更なし                                  |
| 2   | 既存 analyze オプション | 任意      | 既存 CLI 引数 | 新規フラグの要否は設計で判断 (既定は有効) |

### 出力

| #   | 出力                               | 条件                                    | 備考                                               |
| --- | ---------------------------------- | --------------------------------------- | -------------------------------------------------- |
| 1   | entry point 分類付きの探索結果     | 対象アノテーションを検出したとき        | Console / JSON 双方。Protocol への表現は設計で確定 |
| 2   | イベント edge を含む呼び出しグラフ | publish / listener の対応が解決したとき | 曖昧候補は候補列挙として出力                       |
| 3   | callable invocation edge           | 静的追跡可能な値渡しを解決したとき      |                                                    |
| 4   | 解決不能ケースの diagnostic        | 各解決が不能なとき                      | 理由コードは設計で定義                             |

## 例外シナリオ

| #   | シナリオ                                                            | ユーザーへの見せ方                                    | 代替手段                         |
| --- | ------------------------------------------------------------------- | ----------------------------------------------------- | -------------------------------- |
| 1   | publishEvent の引数型が解決できない                                 | diagnostic (理由: イベント型未解決)                   | 既存の未解決診断と同じ運用       |
| 2   | listener が条件付き (@Profile 等) で一意に絞れない                  | 曖昧候補として全列挙 + 条件付き事実を metadata に記録 | 既存の条件付き Bean の扱いと同じ |
| 3   | callable が複数メソッドを経由して渡され静的に追跡できない           | diagnostic (理由: callable 追跡不能)                  | 追跡可能範囲の境界は設計で定義   |
| 4   | 合成アノテーション (meta-annotation) 経由で対象アノテーションが付与 | 検出対象に含める (検出できない場合は diagnostic)      | 対応範囲の深さは設計で確定       |

## バリデーション方針（業務観点）

| #   | 対象     | ルール                                      | エラー時の扱い |
| --- | -------- | ------------------------------------------- | -------------- |
| 1   | CLI 入力 | 既存 analyze コマンドの検証契約から変更なし | 既存どおり     |

## 権限要件

該当なし (ローカル実行の CLI ツール。認可マトリクスなし)。

## 監査/非機能要件

### 監査・運用

- 新分類・新 edge の解決件数 / 診断件数が既存の metadata 集計で観測できること (実測評価を issue #27 と同じ手法で再実施できる状態を保つ)

### 非機能(性能/可用性/セキュリティ/保守)

- 解析時間の顕著な悪化 (目安: 既存実測プロジェクトで大幅な劣化) を伴わないこと。イベント突合・callable 追跡は index ベースで行い全件走査を避ける
- 既存の layer 構造 (ADR-0007) と ArchUnit gate を維持する (SootUp / JavaParser の隔離境界を破らない)

## 受け入れ基準 (EARS)

- WHEN 利用者が `@Scheduled` / `@PostConstruct` / `@PreDestroy` / Web handler メソッドを caller 方向で探索したとき、THE SYSTEM SHALL そのメソッドを framework entry point として根拠 (検出アノテーション) 付きで分類し、未解決 (解析が届かない) と区別して出力する。
- WHEN `publishEvent()` の引数型が静的に解決でき、対応する `@EventListener` / `@TransactionalEventListener` メソッドが workspace 内に存在するとき、THE SYSTEM SHALL publish 地点から listener メソッドへの edge を生成する。
- WHEN イベント型の型階層上に複数の listener が合致するとき、THE SYSTEM SHALL 全 listener を候補として列挙し、一意と偽らない。
- WHEN functional interface の invocation site に渡された lambda / method reference が静的追跡可能な範囲にあるとき、THE SYSTEM SHALL invocation site から実体 (lambda 本体の帰属メソッド / 参照先メソッド) への edge を生成する。
- IF 上記のいずれかが解決できない場合、THEN THE SYSTEM SHALL 理由コード付き diagnostic を記録し、silent omission にしない。
- THE SYSTEM SHALL 本機能追加後も `silentOmission == 0` と既存の outcome ledger 終端保証を維持する。
- WHEN stream / lambda chain 内の generics 型推論が既存 solver で失敗したとき、THE SYSTEM SHALL 強化された解決手段 (方式は設計で確定) で解決を試み、なお不能な場合は既存どおり diagnostic に残す。(追記 2026-08-13)
- IF 解析実行が OutOfMemoryError に到達した場合、THEN THE SYSTEM SHALL raw stack trace のまま異常終了せず、原因と対処 (heap 指針) を示す error として報告する。(追記 2026-08-13)
- WHEN Gradle daemon JVM が対象 Gradle の互換範囲外のとき、THE SYSTEM SHALL 利用者が daemon JVM を指定できる手段または文書化された回避手順を提供する。(追記 2026-08-13)

## 未決事項（論点）

| #   | 論点                                                                      | 決定者      | 期限        | 状態 | メモ                                                         |
| --- | ------------------------------------------------------------------------- | ----------- | ----------- | ---- | ------------------------------------------------------------ |
| 1   | callable 値渡しの静的追跡範囲 (同一メソッド内 / クラス内 / Bean 境界越え) | 設計 (spec) | scaffold 時 | 確定 | spec D1 で確定 (同一メソッド内 + 引数渡し 1 段)              |
| 2   | entry point / イベント edge の Protocol (JSONL) 上の表現                  | 設計 (spec) | scaffold 時 | 確定 | spec D2 で確定 (opaque metadata、schema 変更なし・ADR 不要)  |
| 3   | 対象アノテーション集合の確定 (合成アノテーションの検出深さ含む)           | 設計 (spec) | scaffold 時 | 確定 | spec D3 で確定 (両版対応 + 1 段。2026-08-13 に 2 件追加改訂) |
| 4   | 実装の分割 (1 PR か、entry point / イベント / callable の 3 段階か)       | 設計 (spec) | prompts 時  | 確定 | spec D4 で確定 (4 分割 → 拡大後 P1〜P7 へ改訂)               |

## 設計着手条件チェック

- [x] 業務ルールが確定している
- [x] 入出力要件が確定している (Protocol 表現の詳細は設計事項として明示済み)
- [x] 例外シナリオが確定している
- [x] バリデーション方針が確定している
- [x] 権限要件が確定している (該当なし)
- [x] 監査/非機能要件が確定している
- [x] 未決事項がゼロ、または担当者・期限付きで管理されている (4 件すべて spec の clarify で確定済み)

## 既存資料からの変更点

| 対象                                    | 変更内容                                                     | 理由                        |
| --------------------------------------- | ------------------------------------------------------------ | --------------------------- |
| design/DesignDoc.md (Future Work)       | 「解析精度の強化」着手に伴い、完了後に該当項目を更新         | Rollout Plan の消化         |
| design/features/java-analyzer/ 配下     | entry point 分類 / イベント edge / callable 追跡の設計を追記 | sync phase で正本ハンドオフ |
| design/features/analyzer-protocol/ 配下 | Protocol 表現の拡張があれば追記                              | 未決事項 #2 の決定に依存    |

## 変更履歴

| 日付       | 変更者   | 変更内容                                                                                                                         |
| ---------- | -------- | -------------------------------------------------------------------------------------------------------------------------------- |
| 2026-08-11 | Fukuemon | 初版起案、issue #82 起票                                                                                                         |
| 2026-08-12 | Fukuemon | R3 を broadcast 意味論に合わせて改訂 (spec D7)                                                                                   |
| 2026-08-13 | Fukuemon | 実環境検証プロジェクトの実測を受けスコープ拡大 (entry point 2 件追加 / chain 型解決強化 / 運用堅牢化)、V5・V6 と EARS 3 件を追加 |
| 2026-08-13 | Fukuemon | codegen DAO marker 追加を「やること」へ反映 (spec D12)、未解決件数の定義を注記、未決事項 4 件を確定済みへ同期                    |

## 備考

### 非対象（開発設計で扱う）

- 解析パイプライン内の実装配置 (どの stage に組み込むか)
- JSONL スキーマの具体的なフィールド定義
- fixture / E2E テストの具体構成
