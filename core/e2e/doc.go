// Package e2e は実 analyzers/java fat jar を core/internal/analyze の use case 経由で
// testdata/fixtures/java の fixture project に対して動かし、既知の caller / callee の
// 期待値と突き合わせる (testdata/fixtures/java/README.md)。
//
// analyzers/java の JUnit テストは解析ロジックをプロセス内で、
// core/internal/analyzer の Go テストは Analyzer Protocol を fake analyzer に対して
// 検証する。本 package は **Core と実 Java Analyzer プロセスが端から端まで実際に
// 噛み合うことを検証する唯一の場所**である。
//
// 前提と skip 規則: JDK 25 の java 実行ファイルと、build 済みの
// analyzers/java/build/libs/java-analyzer.jar (`cd analyzers/java && ./gradlew
// shadowJar` で作る) が要る。どちらも実行時に探す (java_fixture_test.go の
// findJava25 / findAnalyzerJar)。欠けていれば t.Skip する。素の
// `go test ./...` (Java Analyzer を build しない Go の CI job など) を壊さないため。
package e2e
