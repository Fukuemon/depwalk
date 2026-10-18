# ADR-0016: Java Analyzer の実行の順を、解析の段は AnalysisRunner に、context の組み立てまでは Main に持たせる

## Status

承認

## Context

ADR-0007 の決定 4 は、Java Analyzer の `analysis` の下を段階ごとの package で作り、実行の順は `analysis/pipeline` だけが知ると決めた。  
同じ決定は、`javaanalyzer` の直下の `protocol`、`io`、`preflight`、`discovery` をそのままにし、Gradle Tooling API を `discovery` へ完全に隔離するとも決めた。  
ADR-0007: [Core と Java Analyzer の依存境界を ACL と機械のチェックで固定し、Core はフラットな package 構成のまま保つ](0007-layered-architecture-refactor.md)

実装では、実行の順を `Main` と `AnalysisRunner` の 2 つが持つ。

- `Main` は、次の順に処理を呼ぶ。
  - request の読み取り（`RequestReader`）
  - 入力の pre-flight（`PreflightValidator`）
  - workspace の root の realpath の解決と、明示の `sourceRoots` か自動 discovery かの選択
  - 自動 discovery のときは、language の metadata の拒否と、`GradleModelDiscovery` による discovery
  - `AnalysisContextFactory` による解析の context の組み立て
  - `AnalysisRunner.run` の呼び出し
  - 出典: [Main.java の run と buildContexts](https://github.com/Fukuemon/depwalk/blob/8e5f43e8bb2fb2ea267bd5eeb27d691cfc255333/analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/Main.java#L62-L164)
- `Main` は、pre-flight と context の組み立ての失敗を、`JAVA_` の error record と終了コード 1 へ写す。
  - discovery の失敗（`DiscoveryFailure`）は、`reason` と `phase` の metadata を持つ `JAVA_GRADLE_MODEL_ERROR` へ写す。
- `AnalysisRunner.run` は、組み立て済みの context を受け取り、scope の列挙から完全性の gate までの解析の段を順に呼ぶ。
  - 出典: [AnalysisRunner.java の run](https://github.com/Fukuemon/depwalk/blob/8e5f43e8bb2fb2ea267bd5eeb27d691cfc255333/analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/pipeline/AnalysisRunner.java#L119-L310)
- `analysis` の下の package が import する `discovery` の型は、`discovery.model` の `DepwalkGradleModel` と `DepwalkProjectModel` だけである。
  - `GradleModelDiscovery` と `DiscoveryFailure` を import するのは `Main` だけである。

ArchUnit のテストが確かめるのは外部のライブラリの 3 段の隔離だけで、実行の順の持ち主は確かめない。  
出典: [ArchitectureTest.java](https://github.com/Fukuemon/depwalk/blob/8e5f43e8bb2fb2ea267bd5eeb27d691cfc255333/analyzers/java/src/test/java/com/fukuemon/depwalk/javaanalyzer/ArchitectureTest.java#L32-L66)

## Decision

ADR-0007 の決定 4 の「実行の順は `analysis/pipeline` だけが知る」を、次の 2 つに改める。  
ADR-0007 のほかの決定（package の構成、外部のライブラリの 3 段の隔離、ArchUnit によるチェック）は変えない。

1. `analysis/pipeline` の `AnalysisRunner` は、解析の段の実行の順を持つ。
   - 解析の段は、組み立て済みの context を受け取った後の、scope の列挙から完全性の gate までを指す。
2. `Main` は、request の読み取りから、`AnalysisContextFactory` による解析の context の組み立てまでの順を持つ。
   - `Main` は、その後に `AnalysisRunner.run` を呼ぶ。
   - `Main` は、各段の失敗を error record と終了コードへ写す。

理由は 2 つある。

- request の読み取り、pre-flight、discovery は、ADR-0007 の決定 4 が `javaanalyzer` の直下に置いた入出力と起動の package の処理である。
  - `analysis` の下の段ではないため、`analysis/pipeline` の段の順に入れない。
- `AnalysisRunner` が discovery を呼ぶと、`analysis` の下が `discovery.model` の外の `discovery` の型に依存する。
  - `Main` が discovery を呼べば、`analysis` の下が依存する `discovery` の型を、model の interface だけに保てる。

## Considered Options

| 選択肢                                                                                   | 強み                                                                                                                        | 弱み                                                                                                                                                                       |
| ---------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **採用:** 解析の段の順は `AnalysisRunner` が、context の組み立てまでの順は `Main` が持つ | 起動の package の処理を `analysis` の下に入れない。`analysis` の下が依存する `discovery` の型を model の interface に保てる | 実行の順を知るには、`Main` と `AnalysisRunner` の 2 か所を読む必要がある                                                                                                   |
| 実装を ADR-0007 に合わせ、request の読み取りから `AnalysisRunner` に持たせる             | 実行の順を 1 か所で読める                                                                                                   | `analysis/pipeline` が `io`、`preflight`、`discovery` の処理を呼び、`analysis` の下が起動の package に依存する。discovery の失敗を error record へ写す処理も移す必要がある |
| context の組み立てまでを担う段を、`analysis/pipeline` の中の別の class に持たせる        | 実行の順を `analysis/pipeline` の package に集められる                                                                      | `analysis` の下が `discovery` の型に依存する点は変わらない。`Main` との分担を新たに決める必要がある                                                                        |

## Consequences

### Positive

- `analysis` の下の package は、`discovery` の型のうち、model の interface だけに依存する。
- 起動の失敗（pre-flight、discovery）を error record へ写す処理を、`Main` の 1 か所で読める。

### Negative

- 実行の全体の順を知るには、`Main` と `AnalysisRunner` の 2 か所を読む必要がある。
- ArchUnit のテストは、実行の順の持ち主を確かめない。
  - `Main` が解析の段を直接呼ぶ変更は、レビューで止める。
