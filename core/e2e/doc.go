// Package e2e runs the real analyzers/java fat jar through the
// core/internal/analyze use case against the fixture project under
// testdata/fixtures/java, and checks the result against the fixture's known
// caller/callee expectations (see testdata/fixtures/java/README.md).
//
// Unlike the JUnit tests in analyzers/java (which exercise the analysis
// logic in-process) and the Go process-contract tests in
// core/internal/analyzer (which exercise the Analyzer Protocol against a
// fake analyzer), this package is the only place that verifies Core and the
// real Java Analyzer process actually agree with each other end to end.
//
// Requirements and skip rule: these tests need a JDK 25 java executable and
// a built analyzers/java/build/libs/java-analyzer.jar (produced by `cd
// analyzers/java && ./gradlew shadowJar`). Both are discovered at runtime
// (see findJava25 and findAnalyzerJar in java_fixture_test.go); when either
// is missing, the tests call t.Skip so a plain `go test ./...` (e.g. the Go
// CI job, which does not build the Java Analyzer) is not broken.
package e2e
