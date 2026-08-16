// Package e2e は Core を端から端まで動かし、CLI の出力と exit code を期待値と
// 突き合わせる。テストは fixture の作り方で 3 系統に分かれる。
//
//  1. 実 fixture: 実 analyzers/java fat jar を testdata/fixtures/java の fixture
//     project に対して動かし、既知の caller / callee の期待値や golden 出力と
//     突き合わせる (testdata/fixtures/java/README.md)。
//  2. inline workspace: t.TempDir() に source を書き出して workspace を組み立て、
//     --analyzer-meta classpath= を渡して classpath なしの解析を実 jar で走らせる。
//     framework の依存は annotation / interface の最小 stub を workspace に置いて
//     解決させる。
//  3. fake analyzer: JVM も jar も使わず、shell script の fake analyzer に対して
//     Core CLI だけを動かす。異常系 (analyzer の異常終了など) の検証に使う。
//
// analyzers/java の JUnit テストは解析ロジックをプロセス内で、
// core/internal/analyzer の Go テストは Analyzer Protocol を fake analyzer に対して
// 検証する。本 package は Core と実 Java Analyzer プロセスが端から端まで実際に
// 噛み合うことを検証する場所である。
//
// 前提と skip 規則: 1. と 2. は JDK 25 の java 実行ファイルと、build 済みの
// analyzers/java/build/libs/java-analyzer.jar (`cd analyzers/java && ./gradlew
// shadowJar` で作る) が要る。どちらも実行時に探し (java_fixture_test.go の
// findJava25 / findAnalyzerJar)、欠けていれば t.Skip する。素の
// `go test ./...` (Java Analyzer を build しない Go の CI job など) を壊さないため。
// 3. は JDK も jar も要らず、skip 条件は実行環境だけになる。
package e2e
