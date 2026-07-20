# Feature 設計: CLI Interface (analyze コマンドの flag 体系と結合)

> 最終更新: 2026-07-20 / Status: 完了 (spec #22 sync で新設。実装は spec #22 の実装フェーズが担う)

depwalk CLI の durable な feature 設計正本。`depwalk analyze` の **コマンド構造 / flag 体系 / method selector 書式 / 責務配置 (CLI 層と analyze use case) / exit code 体系 / 出力先規約 / CLI プロセス E2E の検証方針** を定義する。決定経緯と issue 単位の作業記録は [spec #22](../../../specs/22-cli-interface/) (論点 D1-D12) を参照する。

## メタ

| 項目           | 値                                                                                                                                                                                     |
| -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 関連 PRD 要求  | 統合モードのため [DesignDoc の Why / What](../../DesignDoc.md#提供価値--成功条件-what)                                                                                                 |
| 関連 DesignDoc | [成功条件 S1-S3](../../DesignDoc.md#提供価値--成功条件-what)、[モジュール責務 CLI](../../DesignDoc.md#モジュール責務)                                                                  |
| 関連 context   | [architecture](../../../context/architecture.md)、[testing](../../../context/testing.md)、[project (Quick Commands)](../../../context/project.md)                                      |
| 関連 ADR       | [ADR-0002](../../../adr/0002-core-implementation-foundation.md) (Cobra)、[ADR-0003](../../../adr/0003-analyzer-command-resolution.md) (Analyzer 起動契約)                              |
| 関連 spec      | [specs/22-cli-interface](../../../specs/22-cli-interface/) (D1-D12)、[specs/24](../../../specs/24-gradle-multi-module-source-roots/) (`--source-root` / include・exclude の引き継ぎ元) |
| 対象モジュール | `core` (`core/internal/cli` / `core/internal/analyze`)                                                                                                                                 |

## 背景・要件解釈

traversal (#6) / output (#7) / 解析パイプライン (#9/#21/#24) は部品として実装済みだが、探索クエリ (このメソッドの caller / callee は?) を CLI から実行できない。本 feature は `analyze` コマンドへの flag 追加でこれらを結合し、S1 (caller 探索) / S2 (callee 探索) / S3 (機械パース可能な出力) を CLI から達成させる。

## コマンド構造と flag 体系

探索クエリはサブコマンドを新設せず `analyze` コマンドへの flag 追加で提供する (spec #22 D2)。

```text
depwalk analyze [path] --language <lang> [--analyzer-cmd <cmd>] [--analyzer-meta k=v ...]
                [--source-root <rel> ...] [--include <glob> ...] [--exclude <glob> ...]
                [--method <selector>] [--direction caller|callee] [--max-depth <n>] [--format console|json]
```

| flag          | 型                         | 既定値                               | 説明                                                                                                           | 出自    |
| ------------- | -------------------------- | ------------------------------------ | -------------------------------------------------------------------------------------------------------------- | ------- |
| `--method`    | string                     | (未指定なら現行のサマリ動作)         | method selector (下記書式)。省略時は件数サマリ + diagnostics のみ (後方互換)                                   | #22 D1  |
| `--direction` | string (`caller`/`callee`) | `caller`                             | 探索方向 (`graph.Direction` に対応)。不正値は許容値一覧を添えてエラー                                          | #22 D3  |
| `--max-depth` | int (非負)                 | 無制限                               | 深さ上限 (`traversal.Request.MaxDepth`)。0 = 起点のみ。負値はエラー                                            | #22 D4  |
| `--format`    | string                     | `console`                            | 出力形式。値域は output registry 登録済み formatter のみ (`output.RegisteredFormats()` 参照、ハードコード禁止) | #22 D5  |
| `--include`   | string array (repeatable)  | なし (未指定時は request に載せない) | workspace 相対 path glob。指定順のまま `analysisRequest.include` へ透過                                        | #22 D12 |
| `--exclude`   | string array (repeatable)  | なし (未指定時は request に載せない) | workspace 相対 path glob。指定順のまま `analysisRequest.exclude` へ透過                                        | #22 D12 |

既存 flag (`--analyzer-cmd` / `--language` / `--analyzer-meta` / `--source-root`) は変更しない。Analyzer 起動契約の正本は [ADR-0003](../../../adr/0003-analyzer-command-resolution.md)、`sourceRoots` / `include` / `exclude` の意味論の正本は [Analyzer Protocol feature doc](../analyzer-protocol/DesignDoc_analyzer-protocol.md) の `analysisRequest` 節。CLI は glob・path の意味解釈を行わず透過のみを担う。

**拡張余地**: 新出力形式は formatter 実装 + registry 登録だけで CLI に自動露出する。新しいクエリ種別 (例: パス探索) はサブコマンド新設でも flag 追加でも拡張できる (spec #22 D2 の宣言)。

## method selector 書式

1 引数の統合書式 `<型の binary name>#<メソッド名>[(<引数型リスト>)]` とする (spec #22 D1)。

- 括弧付きで signature 完全指定: `com.example.UserService#findById(java.lang.Long)`
- 括弧省略でメソッド名のみ: `com.example.UserService#findById`
- 照合は graph node の symbol 情報 (`QualifiedName` / `Signature`) の走査で行い、Core は methodId の文字列形式に依存しない (言語非依存)。
- signature 省略で同名メソッドが複数一致した場合は、候補の完全 signature 一覧を stderr に表示してエラー終了する (自動選択しない)。一致 1 件ならそれを採用する。
- signature 省略時は、node の正規化済み `Signature` から引数部分を除いた `<型の binary name>#<メソッド名>` と完全一致させる。Core は nested class の `$` を `QualifiedName` 用の `.` へ変換しない。

## 責務配置

- **CLI 層 (`core/internal/cli`)**: flag 定義・入力 validation・エラー表示 (stderr)・exit code 判別。
- **analyze use case (`core/internal/analyze`)**: `AnalysisRequest` 組み立て (AnalysisMode は常時 `fullGraph` を明示、`Entrypoints` は空のまま。spec #22 D6/D7)、graph 構築後の method selector 照合・`traversal.Traverse`・`output.Write` の orchestration。照合の曖昧・不一致は候補一覧を含む種別付きエラーで CLI 層へ返す。
- 探索方向に関わらず常時 fullGraph で解析し、方向による挙動分岐を持たない (spec #22 D6)。

## exit code 体系

| exit | 区分         | 該当                                                                                                                                                                                                       |
| ---- | ------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 0    | 探索成功     | 結果が空 (到達 node なし) や depthLimit cutoff 注釈付きも成功扱い。結果は stdout へ                                                                                                                        |
| 1    | 実行時エラー | Analyzer 起動失敗、protocol 違反、Analyzer fatal (構造化表示 `renderAnalyzerFailure` を維持)、出力書き込み失敗                                                                                             |
| 2    | 入力エラー   | 不正な flag 値 (`--direction`/`--format`/`--max-depth` の値域外)、method selector のオーバーロード曖昧・不一致 (startNotFound 相当)、`--source-root`/`--include`/`--exclude` の不正 path/glob (validation) |

- Cobra 既定 (RunE エラーを常に exit 1) に委ねず、CLI 層でエラー種別を判別して 0/1/2 を返す。
- エラーメッセージ・候補一覧・diagnostics は stderr、探索結果のみ stdout (JSON の機械パース性の保護)。

## テスト (CLI プロセス E2E)

- `os/exec` で build 済み depwalk バイナリを実プロセス起動し、stdout / stderr / exit code を検証する (spec #22 D9。harness は #24 整備の `buildCoreCLI`/`runCLI` を再利用)。
- console / json とも golden file との完全一致で照合し、json は加えて Unmarshal 成功を検証する (S3)。
- 既存のグラフレベル E2E (`analyze.Run` 直接呼び出し) と合わせた 2 層構成 ([context/testing.md](../../../context/testing.md) の E2E 2 層構造)。
- method selector は完全 signature、signature 省略の一意一致 / overload 曖昧性に加え、nested class の binary name (`Outer$Inner#method`) を回帰検証する。

## 上位資料からの変更点

| 対象資料    | 変更種別 (継承 / 追記 / 変更提案) | 内容                                                                                                                                                          |
| ----------- | --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| PRD         | 継承                              | 統合モードのため DesignDoc の Why / What を参照                                                                                                               |
| DesignDoc   | 継承                              | S1-S3 の CLI 出力レベル照合を完成させる責務。モジュール責務 CLI (引数解析、実行制御、Core 呼び出し) の範囲内                                                  |
| feature doc | 追記                              | graph / output への Metadata 透過 (spec #22 D11) はそれぞれ [graph](../graph/DesignDoc_graph.md) / [output](../output/DesignDoc_output.md) feature doc が正本 |
| context     | 追記                              | `context/project.md` Quick Commands に探索クエリの起動例、`context/testing.md` に E2E 具体引数の確定を反映                                                    |
| ADR         | 継承                              | ADR-0003 は無改訂 (規約 path 前段の導入見送り判断は spec #22 D10 に記録)                                                                                      |
