---
type: context
title: 業務の知識
description: Core、Analyzer、Protocol、Model、caller と callee、record、diagnostic と fatal、staging Graph、自動 discovery などの用語。状態と不変条件は概念ごとに domain/ に置く
status: draft
---

# 業務の知識

## Glossary

Java の静的解析に固有の語（classpath、bytecode member、scope、救済など）は、[Java Analyzer の Design Doc の用語](../analyzers/java/DesignDoc.md#java-analyzer-の文書で使う語) にある。

- Core: 呼び出しグラフの構築、探索、出力を担う、言語によらない部分。Go で実装し、`core/` にある。解析の処理は持たない。
- Analyzer: 言語ごとの解析を行う独立したプロセス。Core とは JSONL の Protocol だけでつながる。`analyzers/<language>/` にある。
- Java Analyzer: Java と Spring を解析する Analyzer。`analyzers/java/` にある。
- Analyzer SPI: Core が Analyzer をプラグインとして扱う境界。Core は graph model と diagnostic を Protocol を通して受け取る。
- Protocol: Core と Analyzer の間の、STDIN と STDOUT の上の JSONL の通信の形式。
- Model: Analyzer の出力の共通のデータモデル。`MethodSymbol`、`CallEdge`、`SourceLocation` からなる。
- Graph Engine: node と edge を管理し、呼び出しグラフを作る Core のモジュール。`core/internal/graph` にある。
- Traversal Engine: caller と callee を探索する Core のモジュール。`core/internal/traversal` にある。
- Output Engine: 結果を Console と JSON に出す Core のモジュール。`core/internal/output` にある。
- caller: あるメソッドを呼び出しているメソッド。
- callee: あるメソッドが呼び出しているメソッド。
- workspace: 解析の起点のディレクトリ。利用者が `depwalk analyze <path>` で渡す。
- record: Analyzer が stdout に 1 行ずつ出す JSONL の 1 件。`methodSymbol`、`callEdge`、`diagnostic`、`error` の 4 種類がある。
- diagnostic: 解析を続けられる問題の報告。出しても結果は返る。
- fatal: 解析を続けられない問題。Analyzer は `error` の record を出して非ゼロで終了し、それまでに出した record はすべて捨てられる。
- staging Graph: 1 つの request の間だけ Core のプロセスの中に持つ、公開する前の Graph。成功したときだけ公開する。
- ACL（腐敗防止層）: wire の record を domain の値へ写す層。`core/internal/protocol` が担う。
- source root: 解析の対象のソースを置いたディレクトリ。`src/main/java` などが当たる。
- 自動 discovery: source root を指定しないときに、Java Analyzer が Gradle の build model から source root と classpath を取得すること。
- 明示の override: `--source-root`（request の `sourceRoots`）で source root を指定し、Gradle を起動せずに解析すること。

## Concepts

状態や不変条件を持つ概念を `domain/` に分けるかは未定。
