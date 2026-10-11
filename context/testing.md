---
type: context
title: テスト
description: テストの種類ごとの保証と置き場、JDK と fat jar が要るテスト、fake analyzer と fixture の作り方、macOS の real path、リリースの前に通す E2E
status: draft
governs:
  - core/e2e
  - testdata
  - analyzers/java/src/test
  - analyzers/java/build.gradle.kts
verified_commit: unverified
---

# テスト

## Test Types and Guarantees

テストの種類ごとに、保証することと置き場は次のとおり。  
機能ごとのテストの観点は、対象のコードの隣の機能ごとの Design Doc にある。一覧は [Design Doc の Components](../design/DesignDoc.md#components) にある。

| 種類                   | 置き場                                                                           | 保証すること                                                                                                                           |
| ---------------------- | -------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Go の unit             | `core/internal/...`                                                              | Graph、Traversal、Output のロジック。JSONL の parse と validate。循環と深さの上限による探索の打ち切り                                  |
| Go の process contract | `core/internal/analyzer` と fake analyzer                                        | Core から見た Analyzer のプロセスとの境界。JVM を使わずに確かめる                                                                      |
| Protocol contract      | `testdata/analyzer-protocol/` と Core と Analyzer の contract テスト             | `analysisRequest`、`MethodSymbol`、`CallEdge`、`SourceLocation`、`diagnostic`、`error`、versioning、プロセスの契約がスキーマに従うこと |
| Java の unit（JUnit）  | `analyzers/java/src/test`                                                        | Java Analyzer の解析の規則                                                                                                             |
| E2E の Traversal 層    | `testdata/fixtures/traversal/` の graph の入力と期待する集合の JSON              | 到達する node と edge の集合                                                                                                           |
| E2E の CLI 層          | `core/e2e/` と `testdata/fixtures/java/` のサンプルの Java/Spring のプロジェクト | 既知の caller と callee の集合と CLI の出力の一致、出力の形式ごとに parse できること                                                   |

- caller の探索、callee の探索、出力の形式の成功条件は、どれも E2E の CLI 層で期待する集合と比べて測る。
  - 成功条件と測り方は [PRD の Success Metrics](../PRD.md#success-metrics) にある。
- 出力の形式は、Output の unit と golden による比較と、E2E の CLI 層での比較の 2 段で確かめる。
- Analyzer の実装は、Core と Analyzer の間の JSONL の入出力を境界にした Protocol contract に対してテストする。
  - ADR-0001: [Analyzer Protocol を JSONL の process SPI にする](../adr/0001-analyzer-protocol-jsonl-spi.md)
- 公開する API だけを検証するテストは、black-box（`package <pkg>_test`）にする。非公開の関数や seam に触る必要があるときだけ、同じ package に置く。

`core/e2e/` のテストは、fixture の作り方で次の 3 つに分かれる。

| 系統             | fixture                                                                                           | 要るもの                                |
| ---------------- | ------------------------------------------------------------------------------------------------- | --------------------------------------- |
| 実 fixture       | `testdata/fixtures/java/` のサンプルの Java/Spring のプロジェクト                                 | Analyzer の JDK と build 済みの fat jar |
| inline workspace | `t.TempDir()` に書き出したソース。helper は `core/e2e/java_fixture_helpers_test.go` の `mkdirFor` | Analyzer の JDK と build 済みの fat jar |
| fake analyzer    | `t.TempDir()` の最小のソースと、shell script の fake analyzer                                     | 実行の環境だけ                          |

- fake analyzer を使うテストでも、実際の Core の CLI を端から端まで見るなら `core/e2e/` に置く。例は `core/e2e/oom_hint_cli_test.go` である。
- inline workspace の系統は、classpath のないソースだけの解析として動かす。
  - `--analyzer-meta classpath=` と `--analyzer-meta javaLanguageLevel=<level>` を渡す。
  - metadata がそのまま出力に出ることを見るときは、`--format json` の `nodes[].metadata` と `edges[].metadata` を比べる。
- Gradle の model の取得（Tooling API と daemon）は E2E だけで確かめる。E2E は、CLI の結合と、graph の全体の一致も確かめる。

テストのコマンドは次のとおり。

- `cd core && go test ./...`: Core の unit テストと contract テストを実行する。JVM がなくても動く状態を保つ。
- `cd analyzers/java && ./gradlew test`: Java Analyzer の unit テストと ArchUnit のテストを実行する。
- E2E の全体:

  ```sh
  (cd analyzers/java && ./gradlew shadowJar) && ./analyzers/java/gradlew --no-daemon -p testdata/fixtures/java/spring-project clean writeDepwalkClasspath && (cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -count=1)
  ```

  - fixture の `writeDepwalkClasspath` は、fixture の `classes` に依存する。

- Gradle の multi-project の E2E だけ:

  ```sh
  (cd analyzers/java && ./gradlew shadowJar) && (cd core && DEPWALK_E2E_REQUIRED=1 go test ./e2e -run TestGradleMultiProjectCLI -count=1)
  ```

- `cd analyzers/java && ./gradlew gradleCompatibilityTest`: Gradle と daemon の JVM の互換性の matrix を確かめる。`./gradlew test` には含まれない。

## Runtime Requirements

- Go の unit テストと contract テストは、fake analyzer（任意の実行ファイル）で動くため、JVM を要らない。
- 実際の Analyzer の jar を起動する E2E だけが、Analyzer の JDK と build 済みの fat jar を要る。JDK のバージョンは `mise.toml` にある。
  - CI は、JVM の要らない Go の job と、JDK と Gradle の build を使う Java と E2E の job に分ける。
- E2E は、JDK か fat jar がないと skip する。`DEPWALK_E2E_REQUIRED=1` を与えると、skip せずに失敗する。
  - CI の E2E の job はこの変数を与える。前提が欠けたまま、assertion を 1 つも実行せずに job が成功するのを防ぐ。
- POSIX の shell に依存する fake analyzer を使うテストは、JDK と jar の有無ではなく、`runtime.GOOS == "windows"` で skip する。
- `gradleCompatibilityTest` は、anchor の daemon の JDK を Gradle の toolchain（foojay resolver）が自動で用意する。
  - `javaToolchains.launcherFor` で解決した JDK を、system property でテストへ渡す。
  - arm64 の macOS には JDK 8 の Temurin がないため、Zulu が使われる。
  - anchor の JDK を解決できないときは、skip して成功にせず、失敗させる。
  - daemon の JVM は、一時的に写した fixture の `gradle.properties` に `org.gradle.java.home` を書いて固定する。この方式は、対象のすべての Gradle で働く。

macOS では、一時ディレクトリの path を real path に揃えてから比べる。

- macOS の `/tmp` と `/var/folders` は `/private` の下への symlink である。JUnit の `@TempDir`、`Files.createTempDirectory`、Go の `t.TempDir()` は、symlink の側の path を返す。
- Gradle の model と `toRealPath()` 済みの source root は `/private/...` を返す。
- 片方だけを real path にして `relativize` すると、`../../private/...` のような誤った相対 path になる。record の path の破損や glob の不一致として現れる。
- Analyzer は、`workspaceRoot` と source root の両方を real path にしてから相対にする。
- テストで一時ディレクトリを workspace にするときは、作った直後に `.toRealPath()` してから、request と期待する値の基準にする。JavaParser の CU storage の path も、real path のファイルを parse に渡して揃える。

## Mocking and Test Data

- Go の mock は、手で書いた fake と interface の stub で作る。
- `testify`、mock の generator、`github.com/google/go-cmp/cmp` は入れない。
  - `go-cmp` は、graph や Protocol の record の深い差分が読みにくくなったときに入れるかを決める。
  - mock の generator は、同じ interface の fake が複数のテストの package に重なったときに入れるかを決める。
- 複数の package のテストから使う fixture の builder の置き場は、[codebase.md](codebase.md#placement-rules) にある。

golden と fixture の置き場は次のとおり。

- golden の fixture は `testdata/` に置く。1 つの package に閉じる golden は、その package の `testdata/` に置く。例は `core/internal/output/testdata/golden/` である。
- CLI の出力と比べる golden は `testdata/fixtures/java/multi-module-spring-project/expected/` に置く。caller と callee の 2 つと、console と json の 2 つの組み合わせの 4 つと、終了コード（0、1、2）を比べる。
- Java の unit テストの fixture のソースは `analyzers/java/src/test/resources/fixtures/<ケース名>/<package の path>/*.java` に置く。
  - テストはケース名のディレクトリを workspace root（source root は `.`）にして解析するため、package の宣言とディレクトリの階層を一致させる。

fixture は、本物の framework に依存させない。

- fixture に framework の本物の jar を持ち込まない。Spring や MyBatis への依存は、annotation と interface の最小の stub のソースを fixture の中に置いて解決させる。
  - 例は `analyzers/java/src/test/resources/fixtures/event/org/springframework/...` である。
  - unit テストと CLI の E2E の両方に同じ方針を当てる。
- 複数の context（project の間の依存）が要る Java の unit テストは、実際の Gradle を起動しない。in-memory の fake の build model から、本番と同じ context の構築を通して解析の pipeline を動かす。
  - 境界の詳細は [Java Analyzer の Design Doc の Verification](../analyzers/java/DesignDoc.md#verification) にある。

Java の unit テストが `ToolProvider.getSystemJavaCompiler()` で fixture を compile するときは、`--release` を Analyzer の JDK と同じにする。

- SootUp が読める classfile の major は、同梱する ASM のバージョンで決まる。読めない major に当たると、bytecode の救済が例外を出さずに無効になる。
- 古い major を指定して避けると、SootUp が新しい major に追いつかなくなったときに検出できず、利用者の環境で救済が無効になる。Analyzer の JDK と同じ major で compile すれば、その回帰をテストが捕まえる。
- 詳しくは [tech-stack.md](tech-stack.md#upgrade-checks) にある。

## Required Tests

- リリースを判断する前に、caller の探索、callee の探索、各出力の形式の E2E を通す。
- 2 つ目以降の Analyzer を足すときは、Protocol contract テストを通す。Core を変えずに Analyzer を足せることを確かめるためである。
  - 最初の Analyzer を入れたときの、言語によらない最初の配線は対象外にする。
- Gradle の multi-project と、解析の完全性にかかわる変更では、次の観点を確かめる。
  - Protocol contract
    - 省略できる `sourceRoots`、workspace からの相対 path、`.`、空と絶対 path と `..` の拒否
    - fatal な request の原子性、共通の `FailureDetail`、省略できる symbol の位置と opaque な metadata
  - Go のプロセスと Graph
    - 正しい record を staging Graph へ 1 回の走査で写すこと、wire の DTO を持たないこと、metadata の deep copy
    - 成功したときだけの公開、fatal のときの Graph と diagnostic の破棄、正常な stream の参照の完全性
    - 汎用の CLI の失敗の表示
  - 必須の E2E
    - fixture は、app、service、repository の 3 つの project、変えた `projectDir`、独自の source のディレクトリ、project の間の呼び出しと DI を含む。
    - 実際の Core の CLI と Analyzer の jar を、テスト専用の透過の proxy でつなぐ。
    - 自動 discovery と明示の override のそれぞれで、graph、request、終了コードを期待する値と比べる。
  - 互換性と安全
    - Gradle と daemon の JVM の anchor の matrix、Gradle の stdout と stderr の隔離
    - credential と URL と絶対 path を含む negative fixture で、それらが出力に漏れないこと
  - 性能: 明示の single-root、自動の single-project、自動の multi-project について、初回と warm の 3 回の中央値、discovery の phase ごとの時間を記録する。数値の SLO は決めない。
- ほかの変更ごとに足すテストの種類は未定。
