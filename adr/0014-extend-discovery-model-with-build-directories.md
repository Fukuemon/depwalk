# ADR-0014: discovery の custom model provider が返す値に、project の directory と included build の root を足す

## Status

承認

## Context

ADR-0006 の決定 2 は、custom tooling model provider が返す値を次の 7 つに限った。

- project の identifier
- `main` の source root
- compile classpath
- classes output
- project の依存
- 実効の source language level
- preview の有無

同じ ADR の決定 3 は、root build の project 階層だけを解析し、除外した build と project を warning で報告すると決めた。  
決定 3 は、採用した source root と file の realpath が workspace の外へ出るときは fatal にするとも決めた。  
ADR-0006: [source root を指定しない request では、Gradle Tooling API で build の実効 model から解析 context を discovery する](0006-adopt-gradle-tooling-api-discovery.md)

7 つの値だけでは、決定 3 の除外を判定できず、除外を報告できない。

- provider は root build の `getAllprojects()` だけを辿るため、included build の project は model に入らない。
  - 7 つの値には、included build があることを示すものがない。
  - included build を報告せずに除外することになり、決定 3 に反する。
- root build の project でも、project の directory が workspace の外にあることがある。
  - その project の source root を採ると、source root の realpath が workspace の外へ出て、request の全体が fatal になる。
  - 7 つの値からは、workspace の外の project と、誤って構成された source root を区別できない。

実装の provider と Analyzer は、7 つの値に加えて、次の値を受け渡している。

- project の directory と included build の root の directory
- root build の root の directory
- 解析の対象に含めなかった source set の名前と件数

出典: [DepwalkModelBuilder.java の buildAll と buildProjectModel](https://github.com/Fukuemon/depwalk/blob/8e5f43e8bb2fb2ea267bd5eeb27d691cfc255333/analyzers/java/model-provider/src/main/java/com/fukuemon/depwalk/gradleprovider/DepwalkModelBuilder.java#L68-L120)  
出典: [DepwalkGradleModel.java](https://github.com/Fukuemon/depwalk/blob/8e5f43e8bb2fb2ea267bd5eeb27d691cfc255333/analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/discovery/model/DepwalkGradleModel.java#L13-L23)

## Decision

ADR-0006 の決定 2 の provider が返す値を、7 つの値に次の 5 つを足した範囲に広げる。

1. project ごとの project の directory
2. build の全体の included build の root の directory
3. root build の root の directory
4. 解析の対象に含めなかった source set の名前
5. 解析の対象に含めなかった source set の件数

ADR-0006 の決定 3 は、root build の project 階層を解析すると決めた。  
この ADR は、その範囲から、project の directory が workspace の外にある root build の project を外す。  
ADR-0006 の決定 2 と決定 3 は、この 2 点だけを変える。ほかの決定は変えない。

Analyzer は、project の directory を、workspace の外の project の除外に使う。

- Analyzer は、project の directory の realpath が workspace の root の realpath の下にない project を、source root の検証の前に scope から外す。
  - realpath を解決できない project も外す。
  - 外した project の件数は、run あたり 1 件の `JAVA_SOURCE_ROOT_EXCLUDED` の warning にまとめる。
  - project ごとの値は属する build を示さないため、warning を build ごとには分けない。
  - 出典: [AnalysisContextFactory.java の discoveredContexts](https://github.com/Fukuemon/depwalk/blob/8e5f43e8bb2fb2ea267bd5eeb27d691cfc255333/analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/context/AnalysisContextFactory.java#L122-L176)
  - 出典: [AnalysisContextFactory.java の realPathWithin](https://github.com/Fukuemon/depwalk/blob/8e5f43e8bb2fb2ea267bd5eeb27d691cfc255333/analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/context/AnalysisContextFactory.java#L432-L438)
- project の directory を返さない model は、`JAVA_GRADLE_MODEL_ERROR` の `provider-incompatible` で fatal にする。
  - 出典: [GradleModelDiscovery.java の validateModel](https://github.com/Fukuemon/depwalk/blob/8e5f43e8bb2fb2ea267bd5eeb27d691cfc255333/analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/discovery/GradleModelDiscovery.java#L134-L154)

Analyzer は、included build の root の directory を、included build の除外の warning に使う。

- included build 1 件につき 1 件の `JAVA_SOURCE_ROOT_EXCLUDED` の warning を出す。
- warning の message は、root の directory の名前と、`--source-root` で source を明示して解析する案内を持つ。
  - 出典: [AnalysisContextFactory.java の discoveredContexts](https://github.com/Fukuemon/depwalk/blob/8e5f43e8bb2fb2ea267bd5eeb27d691cfc255333/analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/analysis/context/AnalysisContextFactory.java#L178-L185)

Analyzer は、root build の root の directory を、model が欠けていないかの確認に使う。

- root build の root の directory を返さない model は、`JAVA_GRADLE_MODEL_ERROR` の `provider-incompatible` で fatal にする。
  - 出典: [GradleModelDiscovery.java の validateModel](https://github.com/Fukuemon/depwalk/blob/8e5f43e8bb2fb2ea267bd5eeb27d691cfc255333/analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/discovery/GradleModelDiscovery.java#L134-L154)

Analyzer は、解析の対象に含めなかった source set の件数を、discovery の終わりに stderr へ出す行に使う。

- 件数は、`excludedSourceSets` の値として出す。
  - 出典: [GradleModelDiscovery.java の discover](https://github.com/Fukuemon/depwalk/blob/8e5f43e8bb2fb2ea267bd5eeb27d691cfc255333/analyzers/java/src/main/java/com/fukuemon/depwalk/javaanalyzer/discovery/GradleModelDiscovery.java#L79-L85)
- source set の名前は、要約の表示のために返す。Analyzer は、いまは名前を使わない。

project の directory と included build の root の directory を足す理由は、ADR-0006 の決定 3 の「除外を報告せずに除外しない」を、model の値から直接満たせるためである。  
workspace の外の project を source root の検証より前に外すため、その project が request の全体を fatal にすることもない。

## Considered Options

| 選択肢                                                                                 | 強み                                                                                                      | 弱み                                                                                                                            |
| -------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| **採用:** project の directory と included build の root の directory を足す           | 除外の判定と warning を model の値から直接行える。workspace の外の project で request が fatal にならない | provider と Analyzer の間の model の契約が 7 つの値より広がり、互換を保つ範囲が広がる                                           |
| 7 つの値に限ったまま、source root の realpath から workspace の外の project を推測する | provider の契約が変わらない                                                                               | workspace の外の project と、誤って構成された source root を区別できない。included build を知る手段がなく、報告せずに除外する   |
| included build の project も model に入れて解析する                                    | included build の source も自動で解析できる                                                               | ADR-0006 の決定 3 の範囲（root build の project 階層だけ）を変える。build をまたぐ project の依存の扱いを新たに決める必要がある |

## Consequences

### Positive

- included build と workspace の外の project の除外が、`JAVA_SOURCE_ROOT_EXCLUDED` の warning で観測できる。
- workspace の外の project があっても、request の全体が fatal にならず、root build の残りの project を解析できる。

### Negative

- provider と Analyzer の間の model の契約が 5 つ増え、対応する Gradle のバージョンの全体で互換を保つ必要がある。
- workspace の外の project の warning は件数だけを持ち、どの build のどの project を外したかを示さない。
