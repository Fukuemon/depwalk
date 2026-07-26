# ADR-0006: Gradle Tooling API による Java source root 自動 discovery を採用する

## 状態

承認

## 決定日

2026-07-18

## 背景

Java project は single-root だけでなく multi-project、変更された `projectDir`、custom source directory、project ごとの classpath / language level を持ち得る。root build file の include 記述だけを辿る方法では、settings logic、plugin、composite build、動的構成で確定する実効 modelを再現できない。filesystem convention scanning も build が定義した source set と依存関係を推測するため、不完全な Graph を成功結果として返す危険がある。

一方、すべての利用者に source roots、classpath、language level の列挙を要求すると通常利用の負担が大きい。自動 discovery と、Gradle buildを評価したくない利用者向けの明示 override の両方が必要である。

## 決定

Java Analyzer は `analysisRequest.sourceRoots` 未指定時に Gradle Tooling API `9.6.1` を使い、対象 build の実効 model から解析 context を discovery する。

- workspace 外の一時 init script から bundled custom tooling model provider を注入する。
- provider は project identifier、`main` source roots、compile classpath、classes output、project dependencies、実効 source language level、preview 有無だけを返す。Gradle task や source generation は実行しない。
- `test` と名前付き source set は自動 discovery の対象外とする。
- 各 project の `main` ごとに解析 context を作り、project dependency で到達可能な context と classpath だけを型解決へ接続する。
- composite / included build のprojectはmodelの対象外 (root buildのproject階層だけを解析する)。workspace外のexternal included build projectと、providerが報告するincluded build rootは、いずれも`JAVA_SOURCE_ROOT_EXCLUDED` warningで除外を観測可能にし、黙示の脱落を残さない。解決済みartifactは外部依存として利用できる。一方、workspace内projectとして採用したsource root / fileのrealpathがworkspace外へ出る場合はfatalとする。
- modelに宣言されたsource directoryが未作成なら生成前の空rootとして除外し、既存rootの非directory・読取不能はfatalにする。project classes outputが未作成、明示経路で自project classes output自体が指定されていない場合、またはmodel由来classpathのworkspace内project依存build outputが未buildの場合は`JAVA_SOOTUP_UNAVAILABLE` warningで該当bytecodeなしのsource解析を継続する (依存contextのsource rootがsolverへ入り型解決を補完する)。ただし、利用者が明示したclasspath entryまたはmodelが解決済みworkspace外external entryの欠落・読取不能はfatalとする。
- provider は Gradle `7.6.5` API baseline に対してbuildしJava 8 classfileとする (compile 用の再配布 API artifact は `7.6.4` が最終のため `7.6.5` 相当として `7.6.4` を使用する。確定 2026-07-18)。対象Gradleは`7.6.5 <= version < 9.7.0`、wrapper不在時はbundled Tooling API `9.6.1`を使用する。固定CI anchorは`7.6.5 / daemon JDK 8`、`8.14.5 / daemon JDK 17`、`9.6.1 / daemon JDK 25`とし、詳細正本は [toolchain context](../context/toolchain.md#gradle-discovery-compatibility-matrix) とする。
- `sourceRoots` が 1 件以上指定された request は Gradle Tooling API、daemon、一時 provider を完全に bypass し、明示 classpath / language level から単一 synthetic context を構築する。

自動 discovery は trusted build 前提である。build logic は利用者権限で評価され、repository credential、network、cache、daemon JVM 選択、任意の副作用は Gradle に委譲される。depwalk は credential を受領・保存せず、Gradle stdout / stderr を Protocol / CLIへ転送しない。raw exception は sanitize する。非漏洩保証は depwalk が生成・転送する artifact に限定し、任意 build logic の sandbox は提供しない。

CLI helpはこの副作用と明示bypassを常時説明し、自動discoveryの各runではbuild評価前にAnalyzer stderrへ、build logic評価、repository / credential resolution、network、cacheを利用し得ることを安定した定型文で通知する。discoveryの開始・終了と安定categoryは観測可能にするが、Gradle由来の自由文は転送しない。

範囲外またはversion判定不能、provider非互換、daemon JVM非互換は`JAVA_GRADLE_MODEL_ERROR`の安定reason `unsupported-gradle-version` / `provider-incompatible` / `daemon-jvm-incompatible`でfatalにする。reasonの分類粒度は判定できたphaseに従う: version / daemon JVMはmodel要求前のpre-flightで、provider非互換はmodel検証で判定する。provider load中にGradle側で顕在化した失敗は原因を特定できないため`model-request-failed` / `connection-failed`として報告し、詳細をraw exceptionから推測しない。wrapper不在のbuildは同梱`9.6.1`で評価されるため、意図しないGradle versionでのbuild評価を避けたい場合はwrapperの利用または明示`sourceRoots`を推奨する。明示`sourceRoots`経路はwrapper判定を含めmatrix全体をbypassする。

## 代替案

- root module の include を独自に解析して module を辿る。
  - 却下理由: settings script、plugin、動的 projectDir、composite build と実効 source set / classpath を正確に再現できない。
- 標準 Tooling API model だけを使う。
  - 却下理由: project ごとの compile classpath、classes output、language level / preview を一つの安定した contract として取得するには不足する。
- filesystem convention (`src/main/java`) を走査する。
  - 却下理由: custom layout と build が除外した source を誤認し、project dependency / language level を復元できない。
- 明示 `sourceRoots` のみを提供する。
  - 却下理由: 正確だが multi-project の通常利用で入力負担が大きく、build model と設定の二重管理になる。

## 影響

### 良い影響

- single / multi-project と custom layout を同じ contract で扱える。
- source root、classpath、project dependency、language level を実効 build model から一貫して取得できる。
- 明示 override により Gradle を実行しない決定的な bypass を提供できる。

### 悪い影響 / トレードオフ

- 自動 discovery は Gradle daemon 起動と build 評価の時間・メモリ・副作用を伴う。
- Tooling API client、provider binary、対象 Gradle、daemon JVM の互換性 matrix を継続保守する必要がある。
- trusted でない build logic を安全に実行する sandbox は提供しない。

### 影響範囲

- 対象モジュール / package: `java-analyzer` (Tooling API / provider / discovery)、`analyzer-protocol` (optional `sourceRoots`)、`core` (言語非依存な repeatable `--source-root` flag と metadata passthrough)
- 横断 contract: `design/DesignDoc.md`、`context/architecture.md`、`context/toolchain.md`、`context/testing.md`、`context/infrastructure.md`

## 実装・運用への反映

- spec 更新要否: 要。issue #24 の durable 設計を本 ADR と Java Analyzer feature doc へハンドオフする。
- context / AI 向け設定更新要否: 要。runtime、toolchain、test、security contract へ反映する。

## 関連ドキュメント / チケット

- [design/DesignDoc.md](../design/DesignDoc.md): Java Analyzer の条件付き Gradle runtime
- [design/features/java-analyzer/DesignDoc_java-analyzer.md](../design/features/java-analyzer/DesignDoc_java-analyzer.md): discovery / analysis context / 完全性の正本
- [context/infrastructure.md](../context/infrastructure.md): trusted build、credential、network、非漏洩境界
- [issue #24](https://github.com/Fukuemon/depwalk/issues/24): 決定経緯と issue 単位の作業記録
