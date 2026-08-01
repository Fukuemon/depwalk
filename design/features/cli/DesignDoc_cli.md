---
type: feature-design
title: "CLI Interface"
description: analyze コマンドの flag 体系と、Core への配線・入力検証の契約
status: 完了
keywords: [CLI, Cobra, flag, analyze, exit code]
governs:
  - core/internal/cli
  - core/cmd/depwalk
verified_commit: 9b9d79d
---

# Feature 設計: CLI Interface (analyze コマンドの flag 体系と結合)

depwalk CLI の設計正本。定義するのは次の 7 つである。コマンド構造、flag 体系、method selector 書式、責務配置 (CLI 層と analyze use case)、exit code 体系、出力先規約、CLI プロセス E2E の検証方針。

## 背景・要件解釈

depwalk の中核機能は traversal / output / 解析パイプラインに分かれて実装されている。CLI はそれらを `analyze` コマンド 1 本へ結合し、[DesignDoc](../../DesignDoc.md) の成功条件 S1 (呼び出し元の網羅的な列挙) / S2 (呼び出し先の列挙) / S3 (Console・JSON 等での出力) を、利用者から見える形で達成させる層である。

## コマンド構造と flag 体系

探索クエリはサブコマンドを新設せず `analyze` コマンドへの flag 追加で提供する。

```text
depwalk analyze [path] --language <lang> [--analyzer-cmd <cmd>] [--analyzer-meta k=v ...]
                [--source-root <rel> ...] [--include <glob> ...] [--exclude <glob> ...]
                [--method <selector>] [--direction caller|callee] [--max-depth <n>] [--format console|json]
```

| flag          | 型                         | 既定値                               | 説明                                                                                                           |
| ------------- | -------------------------- | ------------------------------------ | -------------------------------------------------------------------------------------------------------------- |
| `--method`    | string                     | (未指定なら現行のサマリ動作)         | method selector (下記書式)。省略時は件数サマリ + diagnostics のみ (後方互換)                                   |
| `--direction` | string (`caller`/`callee`) | `caller`                             | 探索方向 (`graph.Direction` に対応)。不正値は許容値一覧を添えてエラー                                          |
| `--max-depth` | int (非負)                 | 無制限                               | 深さ上限 (`traversal.Request.MaxDepth`)。0 = 起点のみ。負値はエラー                                            |
| `--format`    | string                     | `console`                            | 出力形式。値域は output registry 登録済み formatter のみ (`output.RegisteredFormats()` 参照、ハードコード禁止) |
| `--include`   | string array (repeatable)  | なし (未指定時は request に載せない) | workspace 相対 path glob。指定順のまま `analysisRequest.include` へ透過                                        |
| `--exclude`   | string array (repeatable)  | なし (未指定時は request に載せない) | workspace 相対 path glob。指定順のまま `analysisRequest.exclude` へ透過                                        |

flag は 2 群に分かれ、互いに独立している。

- **解析対象を指定する群**: `--analyzer-cmd` / `--language` / `--analyzer-meta` / `--source-root`
- **探索を指定する群**: `--method` / `--direction` / `--max-depth` / `--format`
  Analyzer 起動契約の正本は [ADR-0003](../../../adr/0003-analyzer-command-resolution.md)。

`sourceRoots` / `include` / `exclude` の意味論の正本は [Analyzer Protocol feature doc](../analyzer-protocol/DesignDoc_analyzer-protocol.md) の `analysisRequest` 節。CLI は glob・path の意味解釈を行わず透過のみを担う。

**拡張余地**: 新出力形式は formatter 実装 + registry 登録だけで CLI に自動露出する。新しいクエリ種別 (例: パス探索) はサブコマンド新設でも flag 追加でも拡張できる (本節冒頭の方針宣言による)。

## method selector 書式

1 引数の統合書式 `<型の binary name>#<メソッド名>[(<引数型リスト>)]` とする。

- 括弧付きで signature 完全指定: `com.example.UserService#findById(java.lang.Long)`
- 括弧省略でメソッド名のみ: `com.example.UserService#findById`
- 照合は graph node の symbol 情報 (`QualifiedName` / `Signature`) の走査で行い、Core は methodId の文字列形式に依存しない (言語非依存)。
- signature 省略で同名メソッドが複数一致した場合は、候補の完全 signature 一覧を stderr に表示してエラー終了する (自動選択しない)。一致 1 件ならそれを採用する。
- signature 省略時は、node の正規化済み `Signature` から引数部分を除いた `<型の binary name>#<メソッド名>` と完全一致させる。Core は nested class の `$` を `QualifiedName` 用の `.` へ変換しない。

## 責務配置

- **CLI 層 (`core/internal/cli`)**: flag 定義・入力 validation・エラー表示 (stderr)・exit code 判別。加えてコンポジションルートとして Analyzer 起動コマンドの解決 (ADR-0003) と ACL adapter の port への手動 DI を行い、探索結果の `output.Write` を呼ぶ (いずれも #34 で use case から移動。依存規則は [architecture.md](../../../context/architecture.md) の Package Boundary)。
- **analyze use case (`core/internal/analyze`)**: 解析要求の組み立てと port 経由の実行、graph 構築後の method selector 照合・`traversal.Traverse` の orchestration。照合の曖昧・不一致は候補一覧を含む種別付きエラーで CLI 層へ返す。wire 表現 (`analysisRequest` の schemaVersion / requestId / AnalysisMode 常時 `fullGraph` / `Entrypoints` 空。) の組み立ては ACL (`core/internal/protocol`) が担う (#34)。
- 探索方向に関わらず常時 fullGraph で解析し、方向による挙動分岐を持たない。

## exit code 体系

| exit | 区分         | 該当                                                                                                                                                                                                       |
| ---- | ------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 0    | 探索成功     | 結果が空 (到達 node なし) や depthLimit cutoff 注釈付きも成功扱い。結果は stdout へ                                                                                                                        |
| 1    | 実行時エラー | Analyzer 起動失敗、protocol 違反、Analyzer fatal (構造化表示 `renderAnalyzerFailure` を維持)、出力書き込み失敗                                                                                             |
| 2    | 入力エラー   | 不正な flag 値 (`--direction`/`--format`/`--max-depth` の値域外)、method selector のオーバーロード曖昧・不一致 (startNotFound 相当)、`--source-root`/`--include`/`--exclude` の不正 path/glob (validation) |

- Cobra 既定 (RunE エラーを常に exit 1) に委ねず、CLI 層でエラー種別を判別して 0/1/2 を返す。
- エラーメッセージ・候補一覧・diagnostics は stderr、探索結果のみ stdout (JSON の機械パース性の保護)。

## テスト (CLI プロセス E2E)

- `os/exec` で build 済み depwalk バイナリを実プロセス起動し、stdout / stderr / exit code を検証する (harness の `buildCoreCLI` / `runCLI` を再利用する)。
- console / json とも golden file との完全一致で照合し、json は加えて Unmarshal 成功を検証する (成功条件 S3 の CLI レベルでの担保)。
- 既存のグラフレベル E2E (`protocol.Runner` を直接呼び出し、record 単位で照合する層) と合わせた 2 層構成 ([context/testing.md](../../../context/testing.md) の E2E 2 層構造)。
- method selector は完全 signature、signature 省略の一意一致 / overload 曖昧性に加え、nested class の binary name (`Outer$Inner#method`) を回帰検証する。
