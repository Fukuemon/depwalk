# Issue #21 Java Analyzer Interface Dispatch / Spring DI 解決 要求定義

## 要求フェーズ状況

| #   | フェーズ           | 状態   | 最終更新   | 備考                                           |
| --- | ------------------ | ------ | ---------- | ---------------------------------------------- |
| 1   | 受付               | 完了   | 2026-07-11 | Issue #9 の後続要求                            |
| 2   | 下書き             | 完了   | 2026-07-11 | 起票前レビュー待ち                             |
| 3   | スコープ/成功条件  | 完了   | 2026-07-11 | 起票内容として承認済み                         |
| 4   | 業務仕様           | 未着手 |            | spec-lifecycle で具体化                        |
| 5   | バリデーション方針 | 未着手 |            | spec-lifecycle で具体化                        |
| 6   | 権限要件           | 完了   | 2026-07-11 | CLI機能のため非該当                            |
| 7   | 監査/非機能        | 未着手 |            | 性能・解析精度を設計時に確定                   |
| 8   | 未決事項解消       | 完了   | 2026-07-12 | spec clarify で Q1〜Q4 解消 (spec D1〜D6 参照) |
| 9   | 最終レビュー       | 未着手 |            |                                                |
| 10  | 公開/同期          | 完了   | 2026-07-11 | GitHub Issue #21へ同期                         |

## チケット情報

- 起点: Issue #9 のPhase 2 / Phase 3 (旧呼称) を [ADR-0005](../../adr/0005-adopt-sootup-and-spring-di-resolution.md) により単一feature (#21) に統合
- チケットID: #21
- トラッカー: GitHub
- URL: https://github.com/Fukuemon/depwalk/issues/21
- 関連Issue: https://github.com/Fukuemon/depwalk/issues/9

## 背景・目的

Issue #9 のPhase 1では、JavaParserベースの静的解析によりinterfaceまたは基底型の宣言メソッドまでをcall edgeとして出力する。Spring BootプロジェクトではDIとvirtual dispatchによって実際の呼び出し先が実装クラスになるため、宣言メソッドだけでは変更影響を十分に追跡できない。

本要求は、Interface Dispatch / Override解決、SootUpによるbytecode・依存jar解析、Spring Bean / DI解決を一つのJava Analyzer拡張として実装し、静的に特定できる実装候補へのcall edgeを生成可能にする。

## 想定ユーザー/ステークホルダー

- Java/Spring Bootコードの変更影響を調査する開発者
- 解析結果をCIやレビューで利用するチーム
- Java AnalyzerとAnalyzer Protocolを保守する開発者

## 提供価値（成功条件）

- interfaceまたは基底型経由の呼び出しについて、静的型階層から実装候補を列挙できる。
- SpringのDI情報から注入されるBean候補を絞り込み、実装メソッドへのcall edgeを生成できる。
- sourceと依存jarの型情報を統合し、JavaParserだけでは不足するdispatch情報をSootUpで補完できる。
- 解決が一意でない場合も候補とdiagnosticを保持し、根拠なく一つの実装へ確定しない。

## スコープ

### やること

- Interface Dispatch、継承、override、interface default methodの解決
- SootUpによるbytecodeおよび依存jarの型階層・dispatch情報の補完
- Spring stereotypeと`@Bean`によるBean候補の収集
- constructor、field、setter injectionの解決
- `@Qualifier`、`@Primary`および一意候補によるBean選択
- JavaParser、SootUp、Spring解析結果の統合とcall edge重複排除
- 既存Analyzer Protocolのmetadata / diagnosticを用いた解決根拠と曖昧性の表現
- Spring Boot fixtureを用いたunit / integration / E2Eテスト

### やらないこと

- Reflection、AspectJ Runtime、実行時Proxyの動的追跡
- SpELや任意文字列から動的に選択されるクラス・メソッドの完全解決
- 実行時profile、外部設定、条件評価を含むSpring ApplicationContextの完全再現
- Analyzer Protocolの破壊的変更
- KotlinなどJava以外の言語解析

動的呼び出しの完全追跡を初期スコープに含めない理由と再検討条件は [ADR-0004](../../adr/0004-defer-runtime-call-tracing.md)、SootUpとSpring DI解決を採用して一つのfeatureとして扱う理由は [ADR-0005](../../adr/0005-adopt-sootup-and-spring-di-resolution.md) に従う。

## 業務ルール

| #   | ルール                                                                             | 理由                         | 備考                 |
| --- | ---------------------------------------------------------------------------------- | ---------------------------- | -------------------- |
| R1  | 静的に一意な実装が決まる場合だけ、その実装を確定結果として扱う                     | false positiveを隠さないため |                      |
| R2  | 複数候補が残る場合は候補を保持し、曖昧性をdiagnosticまたはmetadataで観測可能にする | 解析を停止せず根拠を残すため | 表現方法は設計で確定 |
| R3  | Spring固有の解析はJava Analyzer内に閉じ、CoreへSpring依存を導入しない              | Coreの言語非依存性を守るため | S5準拠               |
| R4  | sourceとbytecode由来の同一symbol / edgeは正規化後に重複排除する                    | 二重計上を防ぐため           | methodId規則を継承   |

## 受け入れ基準

- WHEN interfaceまたは基底型を通じてメソッドが呼ばれたとき、Java Analyzerは静的型階層から到達可能な実装候補へのcall edgeを出力する。
- WHEN Spring Beanがconstructor、fieldまたはsetterで注入されるとき、Java AnalyzerはBean定義と選択規則に従って実装候補へのcall edgeを出力する。
- WHERE `@Qualifier`または`@Primary`で候補が一意になる場合、Java Analyzerは選択されたBeanの実装メソッドを解決結果として出力する。
- IF 複数の実装候補が残る場合、Java Analyzerは解析を失敗させず、候補と曖昧性をdiagnosticまたはmetadataに出力する。
- IF sourceから必要な型階層を取得できず依存jarに情報がある場合、Java AnalyzerはSootUpで補完してdispatchを解決する。
- IF Reflection、実行時Proxyまたは実行時条件がなければ確定できない場合、Java Analyzerは推測で一意に確定せず未解決理由を出力する。
- WHEN Spring Boot E2E fixtureを解析したとき、既知のcaller / callee集合と一致する。検証はgraph上の既知caller / callee集合との照合を基本とし、CLI出力レベルの照合はCLI interface spec (#22) 完了後に完成する (#22 完了を前提条件とする)。

## 例外シナリオ

| #   | シナリオ                         | ユーザーへの見せ方               | 代替手段                                |
| --- | -------------------------------- | -------------------------------- | --------------------------------------- |
| E1  | Bean候補が0件                    | 未解決diagnosticを出力し解析継続 | 宣言型のedgeを保持                      |
| E2  | Bean候補が複数件で絞り込めない   | 候補一覧と曖昧性を出力           | 複数候補edge + 宣言型edge保持 (spec D2) |
| E3  | bytecodeをSootUpが読めない       | 対象と原因をdiagnosticへ出力     | JavaParser結果のみで解析継続            |
| E4  | 条件付きBeanを静的に確定できない | 条件未確定として候補を保持       | 実行環境を推測しない                    |

## 監査/非機能要件

- 解析結果から、JavaParser / SootUp / Spring DIのどの根拠でedgeが生成されたかを観測可能にする。
- Issue #9で取得する性能baselineと比較し、追加解析の時間・最大RSSを計測する (合否基準は定めず計測・記録まで。SLOは#22で確定 — spec D5)。
- CoreのGo jobはJVM / Spring依存を持たない状態を維持する。
- 観測レイヤーの責務境界 (spec D6): Analyzer JSONL の metadata / diagnostic までを#21の責務とし、CLI出力 (Console / JSON) へのedge単位metadata表出は#22 (CLI interface spec) へ引き継ぐ。

## 未決事項（論点）

| #   | 論点                                                     | 決定者   | 期限          | 状態                                                                   | メモ                             |
| --- | -------------------------------------------------------- | -------- | ------------- | ---------------------------------------------------------------------- | -------------------------------- |
| Q1  | SootUpを型階層補完だけに使うか、call graph生成まで使うか | Fukuemon | clarify phase | 決定 (spec D1 参照: 型階層補完のみ)                                    | Design Doc Q2を継承              |
| Q2  | 複数dispatch候補を複数edgeで表すかmetadataで表すか       | Fukuemon | clarify phase | 決定 (spec D2 参照: call site 単位の複数候補 edge)                     | Traversalへの影響を確認          |
| Q3  | Spring条件評価をどこまで静的解決するか                   | Fukuemon | clarify phase | 決定 (spec D3 参照: 条件評価せず検出・記録のみ)                        | profile / property / conditional |
| Q4  | Spring Data等の実行時生成実装をどの抽象度で表すか        | Fukuemon | clarify phase | 決定 (spec D4 参照: 宣言メソッド edge + runtime-provided マーカー区別) | 実行時Proxy自体は非対象          |

## 関連資料

- `design/DesignDoc.md` の成功条件S4、Java Analyzer責務、後続feature (#21) の段階導入境界 ([ADR-0005](../../adr/0005-adopt-sootup-and-spring-di-resolution.md))
- `design/features/java-analyzer/DesignDoc_java-analyzer.md` の段階導入と既知の制約
- `specs/9-java-analyzer/` のPhase 1設計・実装記録
- `adr/0004-defer-runtime-call-tracing.md` の静的解析とRuntime Traceの境界
- `adr/0005-adopt-sootup-and-spring-di-resolution.md` の採用技術とIssue分割判断

## 変更履歴

| 日付       | 変更者 | 変更内容                                                                                                                                                        |
| ---------- | ------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-07-11 | Codex  | Issue起票用draftを作成                                                                                                                                          |
| 2026-07-11 | Codex  | 動的解析の非採用とSootUp / Spring DI採用のADRを関連付け                                                                                                         |
| 2026-07-11 | Codex  | GitHub Issue #21を起票しメタ情報を同期                                                                                                                          |
| 2026-07-12 | Claude | ADR-0005追随 (起点 / 上位文書参照の旧Phase呼称を統合feature表現へ更新)、E2E受け入れ基準の検証レベル (graph照合基本 / CLI出力照合は#22完了後) を明確化           |
| 2026-07-12 | Claude | clarify: Q1をspec index.mdのD1決定 (型階層補完のみ) を参照する形で決定済みへ更新                                                                                |
| 2026-07-12 | Claude | clarify: Q2をspec index.mdのD2決定 (call site単位の複数候補edge) を参照する形で決定済みへ更新。E2の代替手段も同決定に合わせて確定                               |
| 2026-07-12 | Claude | clarify: D6決定 (観測はJSONL (metadata/diagnostic) までを#21の責務とし、CLI出力表出は#22へ引き継ぎ) を監査/非機能要件へ反映                                     |
| 2026-07-12 | Claude | clarify: Q3をspec index.mdのD3決定 (条件評価せず検出・記録のみ) を参照する形で決定済みへ更新                                                                    |
| 2026-07-12 | Claude | clarify: Q4をspec index.mdのD4決定 (宣言メソッドedge + runtime-providedマーカー区別) を参照する形で決定済みへ更新                                               |
| 2026-07-12 | Claude | clarify: spec D5決定 (性能増分は数値基準を定めず計測・記録を受け入れ基準に、SLOは#22で確定) を監査/非機能要件へ反映。Q1〜Q4解消により未決事項解消フェーズを完了 |
