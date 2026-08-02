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

// requiredEnvVar は "1" のとき、E2E の前提 (JDK 25、analyzer jar) の欠落を skip
// ではなく失敗に変える環境変数。
//
// CI の e2e job がこれを立てる。前提が黙って欠ける (JDK 検出が壊れる、jar の
// upload / download が失敗する等) と、**アサーションを 1 つも実行しないまま job が
// 緑になる**ためである。
const requiredEnvVar = "DEPWALK_E2E_REQUIRED"

// skipOrFail はメッセージを添えてテストを skip する。ただし requiredEnvVar が "1"
// なら失敗させる。
//
// E2E の前提検査はすべてここを通す。各呼び出し箇所で環境変数を見なくても、CI が
// 「前提の欠落は失敗」を選べるようにするためである。
func skipOrFail(t *testing.T, format string, args ...any) {
	t.Helper()
	msg := fmt.Sprintf(format, args...)
	if e2eRequired(os.Getenv(requiredEnvVar)) {
		t.Fatal(msg)
	}
	t.Skip(msg)
}

// e2eRequired は requiredEnvVar の値が「前提の欠落を skip でなく失敗にする」を
// 意味するかを返す。
//
// skipOrFail から分けてある。t.Skip / t.Fatal の副作用は testing.T から直接観測
// できないため、分岐条件だけを unit test で検証できるようにした。
func e2eRequired(envValue string) bool {
	return envValue == "1"
}

// fixtureRoot は testdata/fixtures/java を絶対パスで返す。テストは package の
// ディレクトリを作業ディレクトリとして走るため、相対の遡りは `go test` の
// 呼び方によらず安定する。
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
// JDK 25 が無い環境 (Go だけの CI job など)。
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
// "." または閉じ引用符の手前の数字に一致させる。`version "25` の部分文字列
// 一致より、ベンダーや書式の違いに強い。部分文字列だと接頭辞が変わるだけで
// 壊れ、25 と 250 も区別できない。
var javaVersionPattern = regexp.MustCompile(`version "(\d+)`)

func isJava25(path string) bool {
	out, err := exec.Command(path, "-version").CombinedOutput()
	if err != nil {
		return false
	}
	return javaMajorVersion(string(out)) == 25
}

// javaMajorVersion は `java -version` の出力からメジャーバージョンを取り出す。
// バージョン文字列が見つからなければ 0 を返す。
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
// Gradle build の前提であり、Go のテストの暗黙の副作用にしてはならない。
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

// expectedCallEdge は testdata/fixtures/java/expected/call-edges.json の 1 件。
type expectedCallEdge struct {
	Description string `json:"description"`
	Caller      string `json:"caller"`
	Callee      string `json:"callee"`
	Dispatch    string `json:"dispatch,omitempty"`
	ViaLambda   bool   `json:"viaLambda,omitempty"`
}

// expectedDiagnostic は testdata/fixtures/java/expected/diagnostics.json の 1 件。
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

// TestJavaAnalyzerFixtureE2E は実 analyzers/java fat jar を
// testdata/fixtures/java/project に対して動かし、既知の caller / callee /
// diagnostic の期待値と突き合わせる。要求の組み立てと Analyzer 起動の経路は
// `depwalk analyze` (core/internal/analyze) と同じものを通る。
//
// analyze の Result ではなく、1 層下の protocol.Runner を直接叩いている。
// Result.Graph は callEdge.metadata を運ばないためである (現行の Traversal が
// 必要としないので graph.Edge に Metadata field が無い)。本テストは
// callEdge.metadata の dispatch と viaLambda を見る必要がある。
//
// 1 層下げても analyze / protocol のロジックは書き直さない。metadata の組み立ては
// analyze.BuildMetadata、要求の検証は protocol.AnalysisRequest.Validate を、
// ACL adapter (protocol.Adapter) と同じように再利用する。
func TestJavaAnalyzerFixtureE2E(t *testing.T) {
	javaPath := findJava25(t)
	jarPath := findAnalyzerJar(t)

	root := fixtureRoot(t)
	projectRoot := filepath.Join(root, "project", "src", "main", "java")
	libJar := filepath.Join(root, "lib", "fixture-lib.jar")
	expectedDir := filepath.Join(root, "expected")

	metadata, err := analyze.BuildMetadata([]string{"classpath=" + libJar, "javaLanguageLevel=25"})
	if err != nil {
		t.Fatalf("build analyzer metadata: %v", err)
	}

	request := protocol.AnalysisRequest{
		SchemaVersion: protocol.SchemaVersion,
		RecordType:    protocol.RecordTypeAnalysisRequest,
		RequestID:     "e2e-fixture",
		WorkspaceRoot: projectRoot,
		SourceRoots:   []string{"."},
		Language:      protocol.LanguageJava,
		Metadata:      metadata,
	}
	if err := request.Validate(); err != nil {
		t.Fatalf("fixture analysisRequest is invalid: %v", err)
	}

	runner := protocol.NewRunner(analyzer.Command{Path: javaPath, Args: []string{"-jar", jarPath}})
	var records []protocol.Record
	result, err := runner.Run(request, func(record protocol.Record) { records = append(records, record) })
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
	for _, record := range records {
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

// runForMaxRSS は analyzer.Runner とは別に analyzer プロセスをもう一度動かし、
// os.ProcessState.SysUsage() から最大 RSS を読む。
//
// analyzer.Runner は os.ProcessState を公開しない。本番の Runner API を変えずに
// 最大 RSS を得るための最小の追加としてこの形にした。
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
