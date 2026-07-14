package e2e

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/analyze"
	"github.com/Fukuemon/depwalk/core/internal/analyzer"
	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

// requiredEnvVar is the environment variable that, when set to "1", turns a
// missing E2E prerequisite (JDK 25, the analyzer jar) from a skip into a
// hard failure. CI's e2e job sets it so that a prerequisite silently going
// missing (e.g. JDK version detection breaking, or the jar upload/download
// step failing) fails the job loudly instead of the job going green having
// run zero assertions.
const requiredEnvVar = "DEPWALK_E2E_REQUIRED"

// skipOrFail skips the test with the given message, unless requiredEnvVar
// is set to "1", in which case it fails the test instead. This is the
// single chokepoint E2E prerequisite checks go through so CI can opt into
// "missing prerequisite is a failure" without duplicating the env check at
// every call site.
func skipOrFail(t *testing.T, format string, args ...any) {
	t.Helper()
	msg := fmt.Sprintf(format, args...)
	if e2eRequired(os.Getenv(requiredEnvVar)) {
		t.Fatal(msg)
	}
	t.Skip(msg)
}

// e2eRequired reports whether requiredEnvVar's value means "prerequisite
// gaps must fail the test rather than skip it". Split out from skipOrFail
// so the branch condition (as opposed to the t.Skip/t.Fatal side effect,
// which testing.T does not allow observing directly) can be unit tested.
func e2eRequired(envValue string) bool {
	return envValue == "1"
}

// fixtureRoot returns testdata/fixtures/java as an absolute path. Tests run
// with the package directory as the working directory, so the relative
// climb is stable regardless of how `go test` is invoked.
func fixtureRoot(t *testing.T) string {
	t.Helper()
	root, err := filepath.Abs(filepath.Join("..", "..", "testdata", "fixtures", "java"))
	if err != nil {
		t.Fatalf("resolve fixture root: %v", err)
	}
	return root
}

// findJava25 locates a JDK 25 java executable, first under the Gradle
// toolchain provisioning directory (~/.gradle/jdks, where
// analyzers/java's Gradle build auto-provisions JDK 25 even though the
// system java may be older), then on PATH. It skips the test when none is
// found so a plain `go test ./...` does not fail in environments without
// JDK 25 (e.g. the Go-only CI job).
func findJava25(t *testing.T) string {
	t.Helper()

	if home, err := os.UserHomeDir(); err == nil {
		patterns := []string{
			filepath.Join(home, ".gradle", "jdks", "*", "*", "Contents", "Home", "bin", "java"), // macOS
			filepath.Join(home, ".gradle", "jdks", "*", "*", "bin", "java"),                     // Linux
		}
		for _, pattern := range patterns {
			matches, _ := filepath.Glob(pattern)
			for _, match := range matches {
				if isJava25(match) {
					return match
				}
			}
		}
	}

	if path, err := exec.LookPath("java"); err == nil && isJava25(path) {
		return path
	}

	skipOrFail(t, "no JDK 25 java executable found (checked ~/.gradle/jdks and PATH); "+
		"run `cd analyzers/java && ./gradlew shadowJar` to provision JDK 25 via Gradle, or install JDK 25 directly")
	return ""
}

// javaVersionPattern extracts the major version number from `java -version`
// output, e.g. `openjdk version "25"` or `openjdk version "25.0.1" 2025-...`.
// Matching on the leading digits before a "." or closing quote tolerates
// vendor/format variation better than a literal `version "25` substring
// check would (which breaks on any prefix change and can't distinguish 25
// from 250).
var javaVersionPattern = regexp.MustCompile(`version "(\d+)`)

func isJava25(path string) bool {
	out, err := exec.Command(path, "-version").CombinedOutput()
	if err != nil {
		return false
	}
	return javaMajorVersion(string(out)) == 25
}

// javaMajorVersion parses the major version number out of `java -version`
// combined output, or returns 0 if no version string is found.
func javaMajorVersion(out string) int {
	match := javaVersionPattern.FindStringSubmatch(out)
	if match == nil {
		return 0
	}
	major, err := strconv.Atoi(match[1])
	if err != nil {
		return 0
	}
	return major
}

// findAnalyzerJar locates the Java Analyzer fat jar built by `cd
// analyzers/java && ./gradlew shadowJar`. It skips the test when the jar is
// missing rather than building it itself: producing the jar is an explicit
// Gradle build prerequisite and must not be an implicit side effect of a Go test.
func findAnalyzerJar(t *testing.T) string {
	t.Helper()
	path, err := filepath.Abs(filepath.Join("..", "..", "analyzers", "java", "build", "libs", "java-analyzer.jar"))
	if err != nil {
		t.Fatalf("resolve analyzer jar path: %v", err)
	}
	if _, err := os.Stat(path); err != nil {
		skipOrFail(t, "analyzers/java/build/libs/java-analyzer.jar not found; run `cd analyzers/java && ./gradlew shadowJar` first")
	}
	return path
}

// expectedCallEdge is one entry of testdata/fixtures/java/expected/call-edges.json.
type expectedCallEdge struct {
	Description string `json:"description"`
	Caller      string `json:"caller"`
	Callee      string `json:"callee"`
	Dispatch    string `json:"dispatch,omitempty"`
	ViaLambda   bool   `json:"viaLambda,omitempty"`
}

// expectedDiagnostic is one entry of testdata/fixtures/java/expected/diagnostics.json.
type expectedDiagnostic struct {
	Code        string `json:"code"`
	Description string `json:"description"`
}

func loadExpectedCallEdges(t *testing.T, expectedDir string) []expectedCallEdge {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(expectedDir, "call-edges.json"))
	if err != nil {
		t.Fatalf("read expected call-edges.json: %v", err)
	}
	var edges []expectedCallEdge
	if err := json.Unmarshal(data, &edges); err != nil {
		t.Fatalf("parse expected call-edges.json: %v", err)
	}
	return edges
}

func loadExpectedDiagnostics(t *testing.T, expectedDir string) []expectedDiagnostic {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(expectedDir, "diagnostics.json"))
	if err != nil {
		t.Fatalf("read expected diagnostics.json: %v", err)
	}
	var diagnostics []expectedDiagnostic
	if err := json.Unmarshal(data, &diagnostics); err != nil {
		t.Fatalf("parse expected diagnostics.json: %v", err)
	}
	return diagnostics
}

func findCallEdge(edges []protocol.CallEdge, caller, callee string) (protocol.CallEdge, bool) {
	for _, edge := range edges {
		if edge.CallerMethodID == caller && edge.CalleeMethodID == callee {
			return edge, true
		}
	}
	return protocol.CallEdge{}, false
}

func metadataString(metadata protocol.Metadata, key string) (string, bool) {
	if metadata == nil {
		return "", false
	}
	value, ok := metadata[key]
	if !ok {
		return "", false
	}
	s, ok := value.(string)
	return s, ok
}

func metadataBool(metadata protocol.Metadata, key string) bool {
	if metadata == nil {
		return false
	}
	value, ok := metadata[key]
	if !ok {
		return false
	}
	b, _ := value.(bool)
	return b
}

func assertDiagnosticCode(t *testing.T, diagnostics []protocol.Diagnostic, code string) {
	t.Helper()
	for _, diagnostic := range diagnostics {
		if diagnostic.Code == code {
			return
		}
	}
	t.Errorf("diagnostic code %q not found among %d diagnostics: %+v", code, len(diagnostics), diagnostics)
}

// TestJavaAnalyzerFixtureE2E runs the real analyzers/java fat jar against
// testdata/fixtures/java/project through the same request-building and
// Analyzer-launch path as `depwalk analyze` (core/internal/analyze), and
// checks the result against the fixture's known caller/callee/diagnostic
// expectations.
//
// This test goes one layer below analyze.Run (using analyzer.Runner
// directly) instead of asserting against analyze.Run's Result: Result.Graph
// does not carry callEdge.metadata (graph.Edge has no Metadata field, since
// the current Traversal Engine graph model does not need it), but this test needs to
// see callEdge.metadata.dispatch and .viaLambda. Going one layer down keeps
// the assertions at the protocol record level without reimplementing any
// analyze/protocol logic — it reuses analyze.BuildMetadata for the
// metadata composition rule and protocol.AnalysisRequest.Validate for
// request validation, exactly as analyze.Run does internally.
func TestJavaAnalyzerFixtureE2E(t *testing.T) {
	javaPath := findJava25(t)
	jarPath := findAnalyzerJar(t)

	root := fixtureRoot(t)
	projectRoot := filepath.Join(root, "project", "src", "main", "java")
	libJar := filepath.Join(root, "lib", "fixture-lib.jar")
	expectedDir := filepath.Join(root, "expected")

	metadata, err := analyze.BuildMetadata([]string{"classpath=" + libJar})
	if err != nil {
		t.Fatalf("build analyzer metadata: %v", err)
	}

	request := protocol.AnalysisRequest{
		SchemaVersion: protocol.SchemaVersion,
		RecordType:    protocol.RecordTypeAnalysisRequest,
		RequestID:     "e2e-fixture",
		WorkspaceRoot: projectRoot,
		Language:      protocol.LanguageJava,
		Metadata:      metadata,
	}
	if err := request.Validate(); err != nil {
		t.Fatalf("fixture analysisRequest is invalid: %v", err)
	}

	runner := analyzer.New(analyzer.Command{Path: javaPath, Args: []string{"-jar", jarPath}})
	result, err := runner.Run(request)
	if err != nil {
		t.Fatalf("failed to run the analyzer process: %v", err)
	}
	if result.ValidationError != nil {
		t.Fatalf("analyzer stdout violated the Analyzer Protocol: %v\nstderr:\n%s", result.ValidationError, result.Stderr)
	}
	if result.AnalyzerError != nil {
		t.Fatalf("analyzer reported a fatal error %s: %s", result.AnalyzerError.Code, result.AnalyzerError.Message)
	}
	if result.ExitCode != 0 {
		t.Fatalf("analyzer process exited with code %d; stderr:\n%s", result.ExitCode, result.Stderr)
	}

	var edges []protocol.CallEdge
	for _, record := range result.Records {
		if edge, ok := record.(protocol.CallEdge); ok {
			edges = append(edges, edge)
		}
	}

	expectedEdges := loadExpectedCallEdges(t, expectedDir)
	expectedDiagnostics := loadExpectedDiagnostics(t, expectedDir)

	t.Run("KnownCallerCalleeEdges", func(t *testing.T) {
		for _, exp := range expectedEdges {
			edge, ok := findCallEdge(edges, exp.Caller, exp.Callee)
			if !ok {
				t.Errorf("%s: missing callEdge %s -> %s", exp.Description, exp.Caller, exp.Callee)
				continue
			}
			if exp.Dispatch != "" {
				dispatch, _ := metadataString(edge.Metadata, "dispatch")
				if dispatch != exp.Dispatch {
					t.Errorf("%s: dispatch = %q, want %q", exp.Description, dispatch, exp.Dispatch)
				}
			}
			if exp.ViaLambda && !metadataBool(edge.Metadata, "viaLambda") {
				t.Errorf("%s: viaLambda metadata not set", exp.Description)
			}
		}
	})

	t.Run("InterfaceDispatch", func(t *testing.T) {
		edge, ok := findCallEdge(edges,
			"java:com.example.GreetingService#greetUser(java.lang.String)",
			"java:com.example.Greeter#greet(java.lang.String)")
		if !ok {
			t.Fatal("interface injection callEdge (GreetingService.greetUser -> Greeter.greet) not found")
		}
		dispatch, _ := metadataString(edge.Metadata, "dispatch")
		if dispatch != "interface" {
			t.Errorf("dispatch = %q, want %q", dispatch, "interface")
		}
	})

	t.Run("LambdaViaFlag", func(t *testing.T) {
		edge, ok := findCallEdge(edges,
			"java:com.example.LambdaUser#run()",
			"java:com.example.LambdaUser#helperMessage()")
		if !ok {
			t.Fatal("lambda callEdge (LambdaUser.run -> LambdaUser.helperMessage) not found")
		}
		if !metadataBool(edge.Metadata, "viaLambda") {
			t.Errorf("viaLambda metadata not set on edge %s", edge.EdgeID)
		}
	})

	t.Run("ParseErrorContinuesAnalysis", func(t *testing.T) {
		assertDiagnosticCode(t, result.Diagnostics, "JAVA_PARSE_ERROR")
		if _, ok := findCallEdge(edges,
			"java:com.example.GreetingService#greetUser(java.lang.String)",
			"java:com.example.Greeter#greet(java.lang.String)"); !ok {
			t.Error("other files' callEdges are missing even though only BrokenSyntax.java should fail to parse")
		}
	})

	t.Run("UnresolvedSymbolCoexistsWithResolvedEdges", func(t *testing.T) {
		assertDiagnosticCode(t, result.Diagnostics, "JAVA_UNRESOLVED_SYMBOL")
		if _, ok := findCallEdge(edges,
			"java:com.example.UnresolvedCaller#resolvedCall()",
			"java:com.example.EnglishGreeter#greet(java.lang.String)"); !ok {
			t.Error("resolved callEdge in the same file as the unresolved call is missing")
		}
	})

	t.Run("AllExpectedDiagnosticsPresent", func(t *testing.T) {
		for _, exp := range expectedDiagnostics {
			assertDiagnosticCode(t, result.Diagnostics, exp.Code)
		}
	})

	t.Run("PerformanceBaseline", func(t *testing.T) {
		t.Logf("analyzer stderr metrics (analyzedFiles/durationMs/unresolvedSymbols): %s", strings.TrimSpace(result.Stderr))

		rss, ok := runForMaxRSS(javaPath, jarPath, request)
		if !ok {
			t.Log("max RSS measurement unavailable on this platform (see rss_unix.go / rss_windows.go)")
			return
		}
		t.Logf("max RSS (platform-reported units, see maxRSS doc comment) = %d", rss)
	})
}

// runForMaxRSS runs the analyzer process a second time, independent of
// analyzer.Runner, purely to read the process's maximum resident set size
// from os.ProcessState.SysUsage(): analyzer.Runner does not expose
// os.ProcessState, so this is the smallest addition that gets a max-RSS
// reading without changing Core's production Runner API.
func runForMaxRSS(javaPath, jarPath string, request protocol.AnalysisRequest) (int64, bool) {
	payload, err := json.Marshal(request)
	if err != nil {
		return 0, false
	}
	payload = append(payload, '\n')

	cmd := exec.Command(javaPath, "-jar", jarPath)
	cmd.Stdin = bytes.NewReader(payload)
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	if err := cmd.Run(); err != nil {
		if _, isExit := err.(*exec.ExitError); !isExit {
			return 0, false
		}
	}
	return maxRSS(cmd.ProcessState)
}
