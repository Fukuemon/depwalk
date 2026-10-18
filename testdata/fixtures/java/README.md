# Java Analyzer の E2E の fixture

`core/e2e` の Go の E2E は、ここにある fixture に対して、実際の `analyzers/java` の fat jar を動かし、出力を期待値と比べる。どの fixture も Analyzer の解析の対象として作ったもので、動くアプリケーションではない。業務のロジックを足さない。

| fixture                        | 使うテスト                                                                                          | 確かめること                                                                                                       |
| ------------------------------ | --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `project/`                     | `java_fixture_test.go`                                                                              | interface、継承、jar の型の引き上げ、lambda の 4 つの場面の caller と callee                                       |
| `spring-project/`              | `java_spring_fixture_test.go`                                                                       | Spring の DI と framework の暗黙の呼び出しを、実際の framework の jar を classpath にして解く                      |
| `multi-module-spring-project/` | `gradle_multiproject_cli_test.go`、`cli_query_test.go`、`hard_to_resolve_call_patterns_cli_test.go` | Gradle の multi-project の自動 discovery と明示の source root、CLI の出力の golden、未解決だった呼び出しの型の救済 |

## project/

場面ごとのファイルは次のとおり。

| 場面                                           | ファイル                                                                  |
| ---------------------------------------------- | ------------------------------------------------------------------------- |
| interface の注入（`dispatch: interface`）      | `project/.../Greeter.java`、`EnglishGreeter.java`、`GreetingService.java` |
| 継承。override するものとしないもの            | `project/.../Animal.java`、`Dog.java`、`Cat.java`、`AnimalCaller.java`    |
| jar の型のメソッドを、範囲の中の子へ引き上げる | `project/.../WidgetUsingLib.java` と `lib/fixture-lib.jar`                |
| lambda の中の呼び出し（`viaLambda: true`）     | `project/.../LambdaUser.java`                                             |

期待値は `expected/call-edges.json` と `expected/diagnostics.json` にある。テストは、期待値の edge と diagnostic が出力に含まれるかだけを確かめ、graph の全体が一致するかは確かめない。呼ばれないメソッドの宣言は、この fixture が確かめる対象ではないからである。

`expected/diagnostics.json` には、`JAVA_SOOTUP_UNAVAILABLE` の warning が 1 件ある。明示の source root で解析すると、classpath に classes の directory がないので、Analyzer は source だけで解析を続け、この warning を出す。テストは、Core がこの diagnostic を成功の結果へ渡すことを確かめる。

parse できないファイルと、範囲の中の解決できない呼び出しは、この fixture に置かない。どちらも request の全体を失敗にする（`JAVA_PARSE_ERROR`、`JAVA_INCOMPLETE_ANALYSIS`）。この 2 つの失敗は、Java Analyzer の unit テストが確かめる。

### classpath

`lib/fixture-lib.jar` は、`com.example.lib.LibBase` の 1 つのクラスだけを持つ jar である。jar の型のメソッドを引き上げる場面には、fixture が source を持たずに継承できるクラスが要るので、空の classpath ではなくこの jar を使う。source は `lib/src/com/example/lib/LibBase.java` にある。作り直すときは次を実行する。

```sh
cd testdata/fixtures/java/lib
javac -d /tmp/depwalk-fixture-lib-classes src/com/example/lib/LibBase.java
jar --create --file fixture-lib.jar -C /tmp/depwalk-fixture-lib-classes .
```

### workspace root

`depwalk analyze` の path の引数には、`project/` ではなく `project/src/main/java` を渡す。source root `.` は、package の階層がそのまま並ぶ directory を指す必要がある。`project/` を渡すと、範囲の中の型が 1 つも解決できない。

### 実際の jar で動かす

リポジトリのルートで、[ルートの README の「使い方」](../../../README.md#使い方)の手順で jar と CLI を作ってから、次を実行する。

```sh
./dist/depwalk analyze testdata/fixtures/java/project/src/main/java \
  --language java \
  --analyzer-cmd "java -jar analyzers/java/build/libs/java-analyzer.jar" \
  --source-root . \
  --analyzer-meta classpath=testdata/fixtures/java/lib/fixture-lib.jar \
  --analyzer-meta javaLanguageLevel=25
```

`core/e2e` は、同じ request を `core/internal/analyze` と `core/internal/analyzer` から直接に組み立てる。fixture-lib.jar と JDK 25 の `java` には、絶対パスを使う。`java` の探し方と、見つからないときに skip する規則は、`core/e2e` の package の doc comment にある。

## spring-project/

実際の Spring の jar を classpath に入れて、DI と framework の暗黙の呼び出しを解く。classpath は、fixture の Gradle の task `writeDepwalkClasspath` が `build/depwalk-classpath.txt` に書く。CI は E2E の前にこの task を実行する。手元では次を実行する。

```sh
./analyzers/java/gradlew -p testdata/fixtures/java/spring-project writeDepwalkClasspath
```

`build/depwalk-classpath.txt` がないと、テストは skip する。環境変数 `DEPWALK_E2E_REQUIRED=1` を設定すると、skip せずに失敗する。

## multi-module-spring-project/

root と 3 つの subproject（`:app`、`:service`、`:repository`）を持つ Gradle の build である。root は Java の source を持たない。`:service` は project の directory を変え、`:repository` は main の source の directory を変える。期待値は `expected/` にあり、`graph.json` は graph の全体、`cli-query-*` は CLI の出力の golden である。

classes の出力と classpath の manifest は、テストが `writeDepwalkClasspath` で作る。manifest がすでにあれば、作り直さない。

`patterns/` は、root の `settings.gradle` に含めない別の build である。かつて解決できなかった 5 つの呼び出しの型を、最小の形で再現する。5 つは fluent chain、method reference、明示の `super` の呼び出し、generics 付きの `var`、module をまたぐ Lombok の member である。`hard_to_resolve_call_patterns_cli_test.go` が自動 discovery で解析し、5 つがすべて bytecode-only の member への edge として救済されることを確かめる。classes の出力がないときは、テストが `writeDepwalkClasspath` で作る。
