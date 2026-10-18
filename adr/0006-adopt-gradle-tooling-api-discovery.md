# ADR-0006: source root を指定しない request では、Gradle Tooling API で build の実効 model から解析 context を discovery する

## Status

承認

## Context

- Java の project は、single-root のほかに、multi-project の構成を持ちうる。
- project ごとに、変更した `projectDir`、custom の source directory、classpath、language level を持ちうる。
- root の build file の include の記述だけを辿っても、settings の logic、plugin、composite build、動的な構成で決まる実効の model を再現できない。
- filesystem の convention を走査する方法は、build が定義した source set と依存関係を推測するため、不完全な Graph を成功の結果として返す危険がある。
- すべての利用者に source root、classpath、language level の列挙を求めると、通常の利用の負担が大きい。
- 自動 discovery と、Gradle の build を評価したくない利用者のための明示の override の両方が要る。

## Decision

### 1. `sourceRoots` を指定しない request では、Gradle Tooling API で実効の build model から discovery する

対象は、`analysisRequest.sourceRoots` を指定しない request である。  
Java Analyzer は、Gradle Tooling API を使い、対象の build の実効の model から解析 context を discovery する。  
対応するバージョンと処理の流れの詳細は [discovery の Design Doc](../analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/discovery/DesignDoc.md) にある。

### 2. model は custom tooling model provider で、必要な項目だけを取る

- workspace の外の一時の init script から、同梱の custom tooling model provider を注入する。
- provider が返す項目は、次の 7 つに限る。
  - project の identifier
  - `main` の source root
  - compile classpath
  - classes output
  - project の依存
  - 実効の source language level
  - preview の有無
- Gradle の task と source の生成は実行しない。
- `test` と名前付きの source set は、自動 discovery の対象にしない。
- 各 project の `main` ごとに解析 context を作り、project の依存で到達できる context と classpath だけを型解決へつなぐ。

### 3. 解析するのは root build の project 階層だけにし、除外を観測できるようにする

- composite build と included build の project は model の対象にせず、root build の project 階層だけを解析する。
- 除外した build と project は warning で報告し、報告せずに除外することはしない。
- 解決済みの artifact は、外部の依存として使ってよい。
- 採用した source root と file の realpath が workspace の外へ出るときは、fatal にする。

### 4. task を実行しないことによる未生成と未 build の欠落では、解析を止めない

- model に宣言された source directory がまだないときは、生成前の空の root として除外する。
- classes output と workspace 内の project 依存の build output が未 build のときは、warning を出し、bytecode なしで source の解析を続ける。依存先の context の source root が型解決を補う。
- 利用者が明示した classpath の entry と、model が解決した workspace の外の entry の欠落と読み取り不能は、fatal にする。

### 5. 対応する Gradle のバージョンの範囲と daemon JVM の組を固定し、CI の anchor で確かめる

- provider は、対応する範囲の最も古い Gradle の API を baseline にして build する。
- wrapper がない build は、同梱の Tooling API のバージョンで評価する。
  - 意図しないバージョンの Gradle で build を評価したくないときは、wrapper を使うか、`sourceRoots` を明示するよう勧める。
- 現在のバージョンと範囲と anchor は、ADR ではなく discovery の Design Doc が持つ。

### 6. `sourceRoots` を明示した request は、Gradle を完全に通らない

`sourceRoots` を 1 件以上指定した request は、Gradle Tooling API、daemon、一時の provider を完全に bypass する。  
解析 context は、明示した classpath と language level から、1 つの synthetic context として作る。  
この request は、wrapper の判定を含めて、バージョンの matrix の全体を bypass する。

### 7. 自動 discovery は trusted build を前提にし、sandbox を提供しない

- build の logic は利用者の権限で評価する。repository の credential、network、cache、daemon JVM の選択、任意の副作用は Gradle に任せる。
- depwalk は credential を受け取らず、保存しない。
- Gradle の stdout と stderr を Protocol と CLI へ渡さず、raw の例外を sanitize する。
- 漏らさないことを保証する範囲は、depwalk が作って渡す artifact に限る。任意の build logic の sandbox は提供しない。
  - 運用の前提は [operations.md の Environments](../context/operations.md#environments) と [Secrets](../context/operations.md#secrets) にある。
- CLI の help は、この副作用と明示の bypass を常に説明する。
- 自動 discovery を実行するたびに、build を評価する前に、Analyzer の stderr へ決まった文言で通知する。
- discovery の開始と終了と、安定した失敗の分類は観測できるようにし、Gradle から来た自由な文は渡さない。

### 8. 互換の失敗は、判定できた phase に応じた安定した reason で区別する

- Gradle のバージョン、daemon JVM、provider の非互換は、どれも `JAVA_GRADLE_MODEL_ERROR` の fatal にし、安定した reason で区別する。
- provider の load の途中で Gradle の側に現れた失敗は、原因を特定できない。
  - この失敗は model の要求か接続の失敗として報告し、詳細を raw の例外から推測しない。

## Considered Options

| 選択肢                                                                                                             | 強み                                                                                                                                                                                                                             | 弱み                                                                                                                                              |
| ------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| **採用:** Gradle Tooling API と custom tooling model provider で discovery し、明示の `sourceRoots` で bypass する | single と multi-project と custom の layout を同じ contract で扱える。source root、classpath、project の依存、language level を実効の build model から一貫して取れる。明示の override で Gradle を実行しない決定的な手段を残せる | Gradle daemon の起動と build の評価の時間、メモリ、副作用を伴う。互換の matrix を保守し続ける。trusted でない build logic の sandbox を提供しない |
| root の module の include を独自に解析して module を辿る                                                           | —                                                                                                                                                                                                                                | settings の script、plugin、動的な `projectDir`、composite build と、実効の source set と classpath を正確に再現できない                          |
| 標準の Tooling API の model だけを使う                                                                             | —                                                                                                                                                                                                                                | project ごとの compile classpath、classes output、language level と preview を、1 つの安定した contract として取るには足りない                    |
| filesystem の convention（`src/main/java`）を走査する                                                              | —                                                                                                                                                                                                                                | custom の layout と、build が除外した source を誤認する。project の依存と language level を復元できない                                           |
| 明示の `sourceRoots` だけを提供する                                                                                | 正確                                                                                                                                                                                                                             | multi-project の通常の利用で入力の負担が大きい。build model と設定を二重に管理することになる                                                      |

## Consequences

### Positive

- single と multi-project と custom の layout を、同じ contract で扱える。
- source root、classpath、project の依存、language level を、実効の build model から一貫して取れる。
- 明示の override で、Gradle を実行しない決定的な bypass を提供できる。

### Negative

- 自動 discovery は、Gradle daemon の起動と build の評価の時間、メモリ、副作用を伴う。
- Tooling API の client、provider の binary、対象の Gradle、daemon JVM の互換の matrix を保守し続ける必要がある。
- trusted でない build logic を安全に実行する sandbox は提供しない。
