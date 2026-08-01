---
type: feature-design
title: "Java Analyzer: Source root discovery"
description: Gradle build model からの source root / classpath 取得と、その安全境界
status: 完了
keywords:
  [discovery, Gradle, Tooling API, source root, classpath, composite build]
governs:
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/discovery
verified_commit: 906d77a
---

# Java Analyzer: Source root discovery

Java Analyzer が **解析対象のソースと classpath をどう決めるか**の正本。

利用者が `--source-root` を明示しない場合、Gradle の build model へ問い合わせて source root・compile classpath・classes output を取得する。この経路は対象プロジェクトの build logic を評価するため、network や credential provider に触れうる。その安全境界も本 doc が定める。

判断の正本は [ADR-0006](../../../adr/0006-adopt-gradle-tooling-api-discovery.md)。親 doc は [DesignDoc_java-analyzer.md](DesignDoc_java-analyzer.md)。用語 (classpath / classes directory / source root) は親 doc の「前提」節を参照する。

## この doc が答えること

- 利用者が `--source-root` を書かなかったとき、解析対象のソースをどう見つけるか
- 型解決に必要な classpath をどこから得るか
- Gradle を呼ぶことで生じる副作用 (build logic の評価) をどう扱うか

## Source root discovery と解析 context

`analysisRequest.sourceRoots` の有無で経路を排他的に選ぶ。

| 経路           | 入力                                                                               | discovery / context                                                                                                             |
| -------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| 明示 override  | `sourceRoots` 1 件以上 + `classpath` + `javaLanguageLevel`、必要なら `javaPreview` | Gradle runtime を完全 bypass し、全 root と global classpath から単一 synthetic `SourceSetAnalysisContext` を構築する           |
| 自動 discovery | `sourceRoots` 未指定                                                               | Gradle Tooling API で build model を取得し、各 Gradle project の `main` source set ごとに `SourceSetAnalysisContext` を構築する |

自動 discovery は filesystem convention や root module の include 記述を独自解析しない。Gradle Tooling API `9.6.1` と、一時 init script から注入する bundled custom model provider を用いる。provider が返すのは次だけである。project identifier、`main` source roots、compile classpath、classes output、project dependencies、実効 source language level、preview 有無。task 実行や source 生成は行わず、`test` と名前付き source set は明示 override で指定された場合を除き対象外とする。一時 provider / init script は workspace 外へ置く。

provider は Gradle `7.6.5` API に対して build し Java 8 classfile とする。対象 Gradle は `7.6.5 <= version < 9.7.0`。Tooling API client と Analyzer build wrapper は `9.6.1` で、wrapper がない build には bundled の `9.6.1` を使う。Analyzer runtime は JDK 25 とする。Gradle daemon JVMは対象Gradleの互換条件に従って選び、project compile toolchainとsource language levelとは別軸にする。source language levelはcompile taskの`release`を優先し、なければ実効`sourceCompatibility`を用いる。`targetCompatibility`、Analyzer JVM、daemon JVM、project toolchainからparser levelを推測しない。固定CI anchorと安定failure reasonの詳細正本は [toolchain context](../../../context/toolchain.md#gradle-discovery-compatibility-matrix) とする。

root は `/` separator の workspace 相対 path へ正規化する。明示root、またはworkspace内projectのsource setとして採用したroot / fileのrealpathがworkspace外へ出る場合はfatalとする。Tooling API が workspace 外の external composite / included build として識別した build の project は、root validation より先に解析 scope から除外する。除外は `JAVA_SOURCE_ROOT_EXCLUDED` warning へ件数を集約して報告する。root build の project 階層に含まれない composite / included build (workspace 内を含む) は v1 の model 対象外である。黙示の脱落を残さないため、provider が報告する build root ごとに 1 件の `JAVA_SOURCE_ROOT_EXCLUDED` warning と、`--source-root` による明示 override の案内を出す。modelが返す解決済みartifactは外部依存として利用できる。directory symlinkは再帰追跡しない。完全重複は先勝ちで除去し、一方が他方を包含するrootはrequest ambiguityとして拒否する。明示rootの欠落・非directory・読取不能はfatal、自動discoveryで存在しないrootは生成前sourceとみなし除外する。最終的なsource fileは絶対realpathで重複排除する。`include` / `exclude`と全locationは常に`workspaceRoot`座標で評価し、module / root IDはgraphに持ち込まない。

各自動 context は model の project dependency で到達可能な context と自身の classpath だけを solver に接続する。明示経路は synthetic context の global classpath を用いる。source index を location の正本とし、solver origin と dependency reachability が一致するときだけ別 context の source へ対応付ける。

## Gradle runtime と安全境界

自動 discovery は利用者が信頼する Gradle build logic を利用者権限で評価する。repository 認証、credential provider、network、Gradle cache、daemon JVM 選択は Gradle に委譲され、任意の build logic の副作用を depwalk が sandbox するとは保証しない。明示 `sourceRoots` はこの runtime を完全に bypass する安全経路である。

CLI help はこの副作用境界と明示overrideを常時説明する。自動discoveryを開始する各runでは、build評価前にAnalyzer stderrへ安全通知の安定した定型文を出す。定型文が伝える内容 (build評価・repository / credential・network / cacheの委譲) と非漏洩境界の正本は [context/infrastructure.md](../../../context/infrastructure.md) であり、本docでは再掲しない。discovery開始・終了、使用Gradle version、project / root件数、安定failure categoryもstderrへ出すが、Gradle由来の自由文は出力しない。

Gradle の stdout / stderr は Protocol / CLI 出力へ転送せず破棄する。例外は raw message、URL query、credential、絶対 path をそのまま返さず、分類済み code と sanitize 済み message / detail に変換する。非漏洩保証は depwalk が生成・転送する Protocol、CLI、log、test artifact に限定し、Gradle 自身や利用者 build logic の出力・副作用までは含めない。
