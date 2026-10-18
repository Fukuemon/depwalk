---
type: feature-design
title: CLI Interface
description: analyze コマンドの flag の体系、method selector の書式、CLI と use case と ACL の責務の配置、終了コード、出力先、CLI のプロセスの E2E の観点
status: stable
governs:
  - core/internal/cli
  - core/cmd/depwalk
verified_commit: d44c349300a64279c33deec4daf393184b19e42c
---

# CLI Interface

## Overview

CLI は、traversal、output、解析のパイプラインを、`analyze` コマンドの 1 本へ結ぶ層である。  
呼び出し元の列挙、呼び出し先の列挙、Console と JSON での出力を、利用者から見える形にする。  
これらの成功条件は [PRD の Success Metrics](../../../PRD.md#success-metrics) にあり、全体の構成は [Design Doc](../../../design/DesignDoc.md#components) にある。

## Scope

持つものは次のとおり。

- コマンドの構造と flag の体系
- method selector の書式
- CLI の層、analyze の use case、ACL の責務の配置
- 終了コードの体系と出力先の規約
- CLI のプロセスの E2E で確かめる観点

持たないものは次のとおり。

- `sourceRoots`、`include`、`exclude` の意味
  - [Analyzer Protocol の Design Doc](../protocol/DesignDoc.md#analysisrequest) が持つ。
- 探索の意味
  - [Traversal の Design Doc](../traversal/DesignDoc.md) が持つ。
- 出力の形式ごとの表示の規則
  - [Output の Design Doc](../output/DesignDoc.md) が持つ。

## Design

### コマンドと flag

探索のクエリは、`analyze` コマンドの flag で指定する。  
ADR-0018: [探索のクエリを、サブコマンドを足さずに analyze コマンドの flag で指定する](../../../adr/0018-query-flags-on-analyze-command.md)

```text
depwalk analyze [path] --language <lang> [--analyzer-cmd <cmd>] [--analyzer-meta k=v ...]
                [--source-root <rel> ...] [--include <glob> ...] [--exclude <glob> ...]
                [--method <selector>] [--direction caller|callee] [--max-depth <n>] [--format console|json]
```

| flag          | 型                           | 既定値                                    | 説明                                                                                                                    |
| ------------- | ---------------------------- | ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `--method`    | string                       | なし                                      | method selector。省くと、件数の要約と diagnostics だけを出す                                                            |
| `--direction` | string（`caller`、`callee`） | `caller`                                  | 探索の方向（`graph.Direction` に対応）。不正な値は、許す値の一覧を添えてエラーにする                                    |
| `--max-depth` | int（非負）                  | 無制限                                    | 深さの上限（`traversal.Request.MaxDepth`）。0 は起点だけ。負の値はエラー                                                |
| `--format`    | string                       | `console`                                 | 出力の形式。値の範囲は output の registry に登録した formatter だけ（`output.RegisteredFormats()`）。ハードコードしない |
| `--include`   | string の配列（繰り返せる）  | なし（未指定のときは request に載せない） | workspace からの相対の path glob。指定した順のまま `analysisRequest.include` へ渡す                                     |
| `--exclude`   | string の配列（繰り返せる）  | なし（未指定のときは request に載せない） | workspace からの相対の path glob。指定した順のまま `analysisRequest.exclude` へ渡す                                     |

flag は 2 つの群に分かれ、互いに独立している。

- 解析の対象を指定する群: `--analyzer-cmd`、`--language`、`--analyzer-meta`、`--source-root`
  - ADR-0003: [Analyzer の起動コマンドを、Core が解釈しない言語非依存の文字列として解決する](../../../adr/0003-analyzer-command-resolution.md)
- 探索を指定する群: `--method`、`--direction`、`--max-depth`、`--format`

CLI は glob と path の意味を解釈せず、値をそのまま渡すだけである。

新しい出力の形式は、formatter の実装と registry への登録だけで、CLI に自動で現れる。

### method selector の書式

1 つの引数にまとめた書式 `<型の binary name>#<メソッド名>[(<引数型リスト>)]` とする。

- 括弧をつけると、signature を完全に指定する。例は `com.example.UserService#findById(java.lang.Long)` である。
- 括弧を省くと、メソッドの名前だけを指定する。例は `com.example.UserService#findById` である。
- method selector は graph の node の `Signature` だけを走査して一致を探し、Core は methodId の文字列の形式に依存しない（言語によらない）。
- signature を省いて同じ名前のメソッドが複数一致したときは、候補の完全な signature の一覧を stderr に出してエラーで終わる。自動では選ばない。一致が 1 件ならそれを使う。
- signature を省いたときは、node の正規化した `Signature` から引数の部分を除いた `<型の binary name>#<メソッド名>` と完全一致させる。Core は nested class の `$` を、`QualifiedName` のための `.` へ変換しない。

### 責務の配置

- CLI の層（`core/internal/cli`）は、flag の定義、入力の検証、エラーの表示（stderr）、終了コードの判別を担う。
  - 加えて合成ルートとして、Analyzer の起動コマンドを解決し、ACL の adapter を port へ手で DI し、探索の結果の `output.Write` を呼ぶ。
  - package の依存の規則は [context/codebase.md](../../../context/codebase.md#dependency-rules) にある。
- analyze の use case（`core/internal/analyze`）は、解析の要求の組み立て、port を通した実行、`traversal.Traverse` の呼び出しを担う。
  - graph を作った後に、method selector に一致する node を探す。
  - 一致が曖昧なときは、候補の signature の一覧を含む入力エラーで CLI の層へ返す。
  - 一致しないときは、selector を含む入力エラーで CLI の層へ返す。候補の一覧は付けない。
- ACL（`core/internal/protocol`）は、wire の表現を組み立てる。
  - `analysisRequest` の schemaVersion と requestId、常に `fullGraph` の AnalysisMode、空の `Entrypoints` は、ここで決める。
- 探索の方向に関わらず常に `fullGraph` で解析し、方向による挙動の分岐を持たない。

### Analyzer の起動コマンドの解決と分割

CLI は、`--analyzer-cmd`（flag）、`DEPWALK_ANALYZER_CMD`（環境変数）の順で、Analyzer の起動コマンドの文字列を解決する。  
どちらも指定がない場合は、Analyzer を起動する前に入力の誤りとして拒否する。  
Core は `java`、jar、JVM の存在を知らず、言語に固有の分岐と path の解決の規約を持ち込まない。

合成ルートは、解決した起動コマンドの文字列を、shell を介さずに次の字句の規則で語に分割する。  
shell を介さないのは、shell injection を避けるためである。  
実装と contract test は `resolveAnalyzerCommand` と `splitAnalyzerCommand` にある。  
ADR-0003: [Analyzer の起動コマンドを、Core が解釈しない言語非依存の文字列として解決する](../../../adr/0003-analyzer-command-resolution.md)

- 空白（space、tab、newline）を語の区切りとする。
- single quote と double quote は、語の結合に使える。quote の中の空白は区切りにならない。
- quote の中では、backslash を含むすべての文字をリテラルとして扱う。対応する閉じ quote だけが特別である。
- quote の外の backslash は、直後の 1 文字が特殊文字（space、tab、newline、single quote、double quote、backslash）のときだけ escape として働く。
  - それ以外の文字が続くときと、文字列の末尾にあるときは、リテラルの backslash として扱う。
  - このため、`C:\jdk\bin\java.exe` のような quote の要らない Windows の絶対パスを、そのまま分割できる。
- 終わっていない quote は、入力の検証のエラーとして実行の前に拒否する。
- 変数の展開、glob の展開、コマンドの置換はしない。

### `--analyzer-meta` の合成

analyze の use case（`analyze.BuildMetadata`）は、`--analyzer-meta key=value` の指定を、指定の順に `analysisRequest.metadata` の JSON へ組み立てる。  
Core は key と value の意味を解釈しない。  
Java に固有の key は [Java Analyzer の Protocol への写像の文書](../../../analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/protocol/DesignDoc.md#metadata-契約) にある。  
ADR-0003: [Analyzer の起動コマンドを、Core が解釈しない言語非依存の文字列として解決する](../../../adr/0003-analyzer-command-resolution.md)

- 値は常に string の JSON の配列に積む。
  - 1 回だけ指定した場合も、要素 1 の配列になる（`--analyzer-meta classpath=/a.jar` → `{"classpath": ["/a.jar"]}`）。
- 同じ key の繰り返しは、指定の順に配列へ足す。
- 空の値（`key=`）は、その key がまだ登録されていないときだけ、空の配列として登録する（`--analyzer-meta classpath=` → `{"classpath": []}`）。
  - 既に登録された値は消さない。
  - `classpath=/a.jar` → `classpath=` は `["/a.jar"]` になり、`classpath=` → `classpath=/a.jar` も `["/a.jar"]` になる。
- 最初の `=` で key と value に分ける。value の側は `=` を含んでよい。
- `=` を含まない指定と、key が空の指定（`=value`）は、入力エラーとして Analyzer を起動する前に拒否する。
- `--analyzer-meta` を 1 回も指定しないときは、`analysisRequest` に `metadata` を載せない。

### 終了コード

| 終了コード | 区分         | 当たるもの                                                                                                                                                                                                                                                                                                                                                                |
| ---------- | ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 0          | 探索の成功   | 結果が空（到達 node なし）のときと、depthLimit cutoff の注釈がつくときも成功とする。結果は stdout へ出す                                                                                                                                                                                                                                                                  |
| 1          | 実行時エラー | Analyzer の起動の失敗、Protocol の違反、Analyzer の fatal（`renderAnalyzerFailure` で構造化して表示）、出力の書き込みの失敗                                                                                                                                                                                                                                               |
| 2          | 入力エラー   | 未知のサブコマンドと flag、多すぎる位置引数、不正な flag の値（`--direction`、`--format`、`--max-depth` の範囲外）、method selector の overload の曖昧と不一致（startNotFound に相当）、`--source-root`、`--include`、`--exclude` の不正な path と glob、Analyzer の起動コマンドの欠落と空の値と閉じていない quote、`key=value` の形でないか key が空の `--analyzer-meta` |

- Cobra の既定（RunE のエラーは常に終了コード 1）に任せず、CLI の層がエラーの種別を判別して 0、1、2 を返す。
- エラーのメッセージ、候補の一覧、diagnostics は stderr へ出し、探索の結果だけを stdout へ出す。JSON を機械で parse できる状態を守るためである。
- Analyzer が正しい `error` の record を出さずに異常終了し、stderr に `java.lang.OutOfMemoryError` の pattern があるときは、対処を含むエラーを表示する。
  - 終了コードは 1 にする。
  - 対処の内容は「analyzer の heap 不足。`--analyzer-cmd`（または `DEPWALK_ANALYZER_CMD`）の java の起動に `-Xmx` を足すか増やす」とする。
  - 検知の規則は [Analyzer Protocol の Design Doc の異常終了のときの stderr](../protocol/DesignDoc.md#異常終了のときの-stderr) が定める。

## Interface

### 置くファイル

- `core/cmd/depwalk/main.go`: `main` の関数。`cli` だけに依存する。
- `core/internal/cli/root.go`: root のコマンドと、終了コードの判別（`ExitCode`）
- `core/internal/cli/analyze.go`: `analyze` コマンドと flag の定義、入力の検証、package の配線、Analyzer の失敗の表示（`renderAnalyzerFailure`）
- `core/internal/cli/analyzerlaunch.go`: Analyzer の起動コマンドの解決
- `core/internal/cli/errors.go`: flag の入力エラーの型（`inputError`）

## Verification

テストの共通の規約は [context/testing.md](../../../context/testing.md#test-types-and-guarantees) にある。  
この機能に固有の観点は次のとおり。

- `os/exec` で build した depwalk のバイナリを実際のプロセスとして起動し、stdout、stderr、終了コードを確かめる。
  - harness の `buildCoreCLI` と `runCLI` を使い回す。
- console と json は、どちらも golden file との完全一致で比べ、json は加えて Unmarshal できることを確かめる。
  - 出力の形式の成功条件を、CLI の水準で確かめる。
- グラフの水準の E2E（`protocol.Client` を直接呼び、record の単位で比べる層）と合わせて、2 層で確かめる。
  - 層ごとに保証する範囲は [context/testing.md](../../../context/testing.md#test-types-and-guarantees) にある。
- method selector は、完全な signature、signature を省いた一意の一致、overload の曖昧さを確かめる。
  - 加えて、nested class の binary name（`Outer$Inner#method`）の回帰を確かめる。

Analyzer の起動の契約は、fake analyzer を使い、JVM なしで確かめる。

- `--analyzer-cmd` と `DEPWALK_ANALYZER_CMD` の解決の順と、どちらもない場合の実行の前の拒否
- shell を介さない分割で exec されること
- `--analyzer-meta` の合成の規則。不正な指定は終了コード 2 になること。
- stdin の close、逐次の parse、stderr を parse しないこと、終了コードの扱いは、Protocol の contract テストの観点を再利用する。
