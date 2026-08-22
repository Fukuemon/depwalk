---
type: feature-design
title: "Java Analyzer: Source root discovery"
description: Gradle build model からの source root / classpath 取得と、その安全境界
status: 完了
keywords:
  [discovery, Gradle, Tooling API, source root, classpath, composite build]
governs:
  - analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/discovery
verified_commit: 4cae142
---

# Java Analyzer: Source root discovery

Java Analyzer が **解析対象のソースと classpath をどう決めるか**を定める。

利用者が `--source-root` を明示しない場合、Gradle の build model へ問い合わせて source root / compile classpath / classes output を取得する。この経路は対象プロジェクトの build logic を評価するため、network や credential provider に触れうる。その安全境界も本 doc が定める。

用語 (classpath / classes directory / source root) は、親 doc の「前提: この doc を読むのに必要な語」節が定義する。

- [DesignDoc_java-analyzer.md](DesignDoc_java-analyzer.md) — Java Analyzer の骨格と、本 doc で使う語の定義
- [ADR-0006](../../../adr/0006-adopt-gradle-tooling-api-discovery.md) — Gradle Tooling API による source root 自動 discovery を採用した決定

## この doc が答えること

- 利用者が `--source-root` を書かなかったとき、解析対象のソースをどう見つけるか
- 型解決に必要な classpath をどこから得るか
- Gradle を呼ぶことで生じる副作用 (build logic の評価) をどう扱うか

問い合わせには [Gradle Tooling API](https://docs.gradle.org/current/userguide/tooling_api.html) を使う。外部プログラムから Gradle の build を評価させ、project 階層 / 依存関係 / source directory を取得できる仕組みである。**build を評価する**という点が安全境界の話につながる。

## Source root discovery と解析 context

`analysisRequest.sourceRoots` の有無で経路を排他的に選ぶ。

| 経路           | 入力                                                                               | discovery / context                                                                                                             |
| -------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| 明示 override  | `sourceRoots` 1 件以上 + `classpath` + `javaLanguageLevel`、必要なら `javaPreview` | Gradle runtime を完全 bypass し、全 root と global classpath から単一 synthetic `SourceSetAnalysisContext` を構築する           |
| 自動 discovery | `sourceRoots` 未指定                                                               | Gradle Tooling API で build model を取得し、各 Gradle project の `main` source set ごとに `SourceSetAnalysisContext` を構築する |

自動 discovery は filesystem convention や root module の include 記述を独自解析しない。Gradle Tooling API `9.7.1` と、一時 init script から注入する bundled custom model provider を用いる。provider が返すのは次だけである。project identifier、`main` source roots、compile classpath、classes output、project dependencies、実効 source language level、preview 有無。

task 実行と source 生成は行わない。`test` と名前付き source set は、明示 override で指定された場合を除き対象外とする。一時 provider と init script は workspace 外へ置く。

provider は Gradle `7.6.5` API に対して build し、Java 8 classfile とする。対象 Gradle は `7.6.5 <= version < 9.8.0` である。Tooling API client と Analyzer build wrapper は `9.7.1` とし、wrapper がない build には bundled の `9.7.1` を使う。Analyzer runtime は JDK 25 とする。

Gradle daemon JVM は対象 Gradle の互換条件に従って選び、project compile toolchain や source language level とは別軸で扱う。daemon JVM が対象 Gradle の互換範囲外になる場合 (例: Analyzer JVM が daemon に引き継がれるとき) は、request `metadata.gradleJavaHome` で daemon JVM を明示指定できる。

- 値は要素 1 の path とし、CLI からは `--analyzer-meta gradleJavaHome=<path>` で渡す。
- 互換 JDK の暗黙の自動探索は行わず、明示 override だけを受け付ける。
  - [ADR-0009](../../../adr/0009-implicit-call-resolution-and-type-propagation-rescue.md) の 決定 — daemon JVM を明示 override だけで指定すると定めた決定
- 指定 path が要素 1 の実在する java home (`bin/java`、Windows は `bin/java.exe` が実行可能な directory) でなければ `JAVA_INVALID_REQUEST` で拒否する。
- 明示 `sourceRoots` 経路では解釈しない。

source language level は compile task の `release` を優先し、なければ実効 `sourceCompatibility` を用いる。`targetCompatibility`、Analyzer JVM、daemon JVM、project toolchain から parser level を推測しない。

- [context/toolchain.md](../../../context/toolchain.md#gradle-discovery-compatibility-matrix) の Gradle discovery compatibility matrix — 固定 CI anchor と安定 failure reason を定める

root は `/` separator の workspace 相対 path へ正規化する。正規化と検証の規則は次のとおりである。

- 明示 root、または workspace 内 project の source set として採用した root / file の realpath が workspace の外へ出る場合は fatal とする。
- Tooling API が workspace 外の external composite / included build として識別した build の project は、root validation より先に解析 scope から除外する。除外は `JAVA_SOURCE_ROOT_EXCLUDED` warning へ件数を集約して報告する。
- root build の project 階層に含まれない composite / included build は、workspace 内のものも含めて v1 の model 対象外である。黙示の脱落を残さないため、provider が報告する build root ごとに 1 件の `JAVA_SOURCE_ROOT_EXCLUDED` warning と、`--source-root` による明示 override の案内を出す。
- model が返す解決済み artifact は外部依存として利用できる。
- directory symlink は再帰追跡しない。
- 完全重複は先勝ちで除去する。一方が他方を包含する root は request ambiguity として拒否する。
- 明示 root の欠落、非 directory、読取不能は fatal とする。自動 discovery で存在しない root は、生成前の source とみなして除外する。
- 最終的な source file は絶対 realpath で重複排除する。
- `include` / `exclude` と全 location は常に `workspaceRoot` 座標で評価し、module / root ID は graph に持ち込まない。

各自動 context は model の project dependency で到達可能な context と自身の classpath だけを solver に接続する。明示経路は synthetic context の global classpath を用いる。location は source index から取り、solver origin と dependency reachability が一致するときだけ別 context の source へ対応付ける。

## Gradle runtime と安全境界

自動 discovery は利用者が信頼する Gradle build logic を利用者権限で評価する。repository 認証、credential provider、network、Gradle cache、daemon JVM 選択は Gradle に任せ、任意の build logic の副作用を depwalk が sandbox するとは保証しない。明示 `sourceRoots` はこの runtime を完全に bypass する安全経路である。

CLI help は、この副作用境界と明示 override を常時説明する。自動 discovery を開始する各 run では、build 評価の前に Analyzer stderr へ安全通知の定型文を出す。定型文は run をまたいで安定した文面とする。

- [context/infrastructure.md](../../../context/infrastructure.md) — 定型文が伝える内容 (build 評価、repository / credential、network / cache の委任) と非漏洩境界を定める

discovery の開始と終了、使用 Gradle version、project / root 件数、安定 failure category も stderr へ出す。Gradle 由来の自由文は出力しない。

Gradle の stdout / stderr は Protocol / CLI 出力へ転送せず破棄する。例外は raw message、URL query、credential、絶対 path をそのまま返さず、分類済み code と sanitize 済みの message / detail に変換する。非漏洩の保証範囲は、depwalk が生成して転送する Protocol、CLI、log、test artifact に限る。Gradle 自身や利用者 build logic の出力と副作用は含めない。

## 関連ドキュメント

- [DesignDoc_java-analyzer.md](DesignDoc_java-analyzer.md): Java Analyzer の骨格と起動契約
- [context/toolchain.md](../../../context/toolchain.md): Gradle discovery の互換 matrix
- [context/infrastructure.md](../../../context/infrastructure.md): Gradle daemon の実行境界と安全性の前提
- [ADR-0006](../../../adr/0006-adopt-gradle-tooling-api-discovery.md): Gradle Tooling API による source root 自動 discovery を採用した決定
- [ADR-0009](../../../adr/0009-implicit-call-resolution-and-type-propagation-rescue.md): 暗黙呼び出し解決と型伝播救済、daemon JVM の明示 override の決定
