# Java Analyzer E2E fixture

`project/` is a small Java/Spring-style source tree used by the Go E2E test
(`core/e2e`) to run the real `analyzers/java` fat jar through
`depwalk analyze` and check its output against known caller/callee sets.
This fixture is designed as an analyzer test target, not a working
application — do not add real business logic here.

## Structure (4 covered scenarios)

| Scenario                                              | File(s)                                                                   |
| ----------------------------------------------------- | ------------------------------------------------------------------------- |
| Interface injection (`dispatch: interface`)           | `project/.../Greeter.java`, `EnglishGreeter.java`, `GreetingService.java` |
| Inheritance, override vs. no override                 | `project/.../Animal.java`, `Dog.java`, `Cat.java`, `AnimalCaller.java`    |
| Jar-derived method lifted to a scope-internal subtype | `project/.../WidgetUsingLib.java` + `lib/fixture-lib.jar`                 |
| Lambda call (`viaLambda: true`)                       | `project/.../LambdaUser.java`                                             |

Parse errors and unresolved in-scope calls are no longer covered as
"continue with a partial graph" scenarios: since spec #24 (D15 / D20), an
unparseable file fails the whole request with `JAVA_PARSE_ERROR` and an
unresolved in-scope call fails it with `JAVA_INCOMPLETE_ANALYSIS`. Those
fatal paths are asserted by the Java Analyzer's own tests, so this fixture
holds only cleanly analyzable sources and `expected/diagnostics.json` is
an empty array.

Known caller/callee expectations live in `expected/call-edges.json` and
`expected/diagnostics.json`, loaded and checked by the Go E2E test. These
list the specific edges/diagnostics each scenario must produce; the test
checks that the required entries are present (a subset check), not full
graph equality, since declared-but-uncalled methods and other structural
detail are not part of what this fixture is asserting.

## classpath

`analysisRequest.metadata.classpath` is a required key (Analyzer Protocol
pre-flight), but its value may be an empty array. This fixture uses a
**minimal pre-built jar** (`lib/fixture-lib.jar`) rather than an empty
classpath, because the "jar 由来メソッドの引き上げ対象" scenario needs a
class the fixture project can extend without owning its source.

`lib/fixture-lib.jar` contains one class, `com.example.lib.LibBase`
(source kept at `lib/src/com/example/lib/LibBase.java` for audit /
reproducibility). To rebuild it:

```sh
cd lib
javac -d /tmp/depwalk-fixture-lib-classes src/com/example/lib/LibBase.java
jar --create --file fixture-lib.jar -C /tmp/depwalk-fixture-lib-classes .
```

The rest of the fixture project does not need any classpath entry — it only
resolves against its own scope files and the JDK's own reflection-based
type solver.

## workspaceRoot

Pass `project/src/main/java` as `workspaceRoot`/the `depwalk analyze` path
argument, **not** `project/`. `JavaParserTypeSolver` indexes a source root
directly as a package hierarchy (`<root>/com/example/Foo.java` for
`com.example.Foo`); pointing it at `project/` (one level above `src/main/
java`) leaves every scope-internal type unresolvable, which surfaces as an
`UnsolvedSymbolException` thrown out of the Analyzer process rather than a
diagnostic (confirmed by running the real jar against this fixture).

## Running against the real jar

```sh
depwalk analyze testdata/fixtures/java/project/src/main/java \
  --language java \
  --analyzer-cmd "java -jar analyzers/java/build/libs/java-analyzer.jar" \
  --analyzer-meta classpath=testdata/fixtures/java/lib/fixture-lib.jar
```

The Go E2E test (`core/e2e`) does the equivalent through
`core/internal/analyze` and `core/internal/analyzer` directly, using an
absolute path to the fixture-lib.jar and to whichever JDK 25 `java`
executable it discovers (see `core/e2e` package doc for the discovery /
skip rule).
