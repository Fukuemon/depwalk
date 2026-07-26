package e2e

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"sort"
	"strings"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

// TestGradleMultiProjectCLI is the repository-standard required E2E for
// multi-project workspaces. It drives the
// real Core CLI binary against the real Java Analyzer jar through the
// test-only transparent recording proxy, and verifies the captured
// analysisRequest, the raw Analyzer JSONL / stderr / exit status, and the
// CLI stdout / stderr / exit status in the same run for the auto-discovery,
// explicit-override, and fatal paths.
//
// The --include / --exclude flags and their passthrough into the request are
// pinned by cli unit tests, and the Analyzer-side workspace-relative globs by
// the real-jar MultiModuleFixtureEquivalenceTest. This E2E focuses on the
// source root auto / explicit contract; query output is verified by
// TestCLIQueryGolden.
//
// Failure paths that need no real process (a non-zero exit without an error
// record, malformed stdout, withholding a graph with dangling references,
// keeping the fatal reason, and the language-agnostic failure renderer) are
// covered by the process contract tests in core/internal/protocol and
// core/internal/cli instead, which run as a required gate.
func TestGradleMultiProjectCLI(t *testing.T) {
	javaPath := findJava25(t)
	jarPath := findAnalyzerJar(t)
	fixture := multiModuleFixtureRoot(t)
	manifest := ensureMultiModuleManifest(t, fixture)
	cliPath := buildCoreCLI(t)
	expected := loadExpectedMultiModuleGraph(t, fixture)

	autoCapture := t.TempDir()
	t.Run("Auto", func(t *testing.T) {
		result := runCLI(t, cliPath, autoCapture, javaPath, jarPath,
			fixture, "--language", "java")
		if result.exitCode != 0 {
			t.Fatalf("CLI exit = %d, want 0; stderr:\n%s", result.exitCode, result.stderr)
		}
		if !strings.Contains(result.stdout, "analyzed ") {
			t.Fatalf("CLI stdout = %q, want the success summary", result.stdout)
		}

		request := capturedRequest(t, autoCapture)
		for _, field := range []string{"sourceRoots"} {
			if _, ok := request[field]; ok {
				t.Errorf("auto request must omit %q: %v", field, request)
			}
		}
		if metadata, ok := request["metadata"].(map[string]any); ok {
			for _, key := range []string{"classpath", "javaLanguageLevel", "javaPreview"} {
				if _, exists := metadata[key]; exists {
					t.Errorf("auto request metadata must omit %q: %v", key, metadata)
				}
			}
		}

		stderrText := capturedText(t, autoCapture, "stderr.txt")
		for _, want := range []string{
			"depwalk: notice build-model discovery",
			"projects=3",
			"sourceRoots=3",
			"silentOmission=0",
		} {
			if !strings.Contains(stderrText, want) {
				t.Errorf("analyzer stderr missing %q:\n%s", want, stderrText)
			}
		}
		assertNoGradleFreeText(t, stderrText)
		assertCapturedGraphMatches(t, autoCapture, expected)
	})

	explicitCapture := t.TempDir()
	t.Run("Explicit", func(t *testing.T) {
		args := []string{fixture, "--language", "java",
			"--source-root", "app/src/main/java",
			"--source-root", "modules/service/src/main/java",
			"--source-root", "repository/src/domain/java",
		}
		for _, entry := range manifest {
			args = append(args, "--analyzer-meta", "classpath="+entry)
		}
		args = append(args, "--analyzer-meta", "javaLanguageLevel=17")
		result := runCLI(t, cliPath, explicitCapture, javaPath, jarPath, args...)
		if result.exitCode != 0 {
			t.Fatalf("CLI exit = %d, want 0; stderr:\n%s", result.exitCode, result.stderr)
		}

		request := capturedRequest(t, explicitCapture)
		wantRoots := []any{"app/src/main/java", "modules/service/src/main/java", "repository/src/domain/java"}
		if got := request["sourceRoots"]; !reflect.DeepEqual(got, wantRoots) {
			t.Errorf("sourceRoots = %v, want order-preserving %v", got, wantRoots)
		}
		metadata, ok := request["metadata"].(map[string]any)
		if !ok {
			t.Fatalf("request metadata is not an object: %v", request["metadata"])
		}
		gotClasspath, ok := metadata["classpath"].([]any)
		if !ok {
			t.Fatalf("metadata classpath is not an array: %v", metadata["classpath"])
		}
		if len(gotClasspath) != len(manifest) {
			t.Fatalf("classpath = %d entries, want %d", len(gotClasspath), len(manifest))
		}
		for i, entry := range manifest {
			if gotClasspath[i] != entry {
				t.Errorf("classpath[%d] = %v, want %v (input order preserved)", i, gotClasspath[i], entry)
			}
		}
		if got := metadata["javaLanguageLevel"]; !reflect.DeepEqual(got, []any{"17"}) {
			t.Errorf("javaLanguageLevel = %v, want [\"17\"]", got)
		}

		stderrText := capturedText(t, explicitCapture, "stderr.txt")
		for _, forbidden := range []string{"build-model discovery", "discovery phase=start", "depwalk: notice"} {
			if strings.Contains(stderrText, forbidden) {
				t.Errorf("explicit override must not touch the Tooling API (found %q):\n%s", forbidden, stderrText)
			}
		}
		assertCapturedGraphMatches(t, explicitCapture, expected)

		// The auto and explicit runs must produce equivalent raw graphs (edgeId
		// aside). Skip when the Auto subtest left no capture: that is a
		// dependency failure, and reporting it again here would be misleading.
		if _, err := os.Stat(filepath.Join(autoCapture, "stdout.jsonl")); err != nil {
			t.Skipf("Auto subtest did not produce a capture: %v", err)
		}
		autoMethods, autoEdges := capturedGraph(t, autoCapture)
		explicitMethods, explicitEdges := capturedGraph(t, explicitCapture)
		if !reflect.DeepEqual(autoMethods, explicitMethods) {
			t.Errorf("auto and explicit method sets differ:\n%v\n%v", autoMethods, explicitMethods)
		}
		if !reflect.DeepEqual(autoEdges, explicitEdges) {
			t.Errorf("auto and explicit edge sets differ:\n%v\n%v", autoEdges, explicitEdges)
		}
	})

	t.Run("FatalAtomicity", func(t *testing.T) {
		// A workspace where the real Analyzer streams records and then returns a
		// valid error (JAVA_INCOMPLETE_ANALYSIS). The proxy synthesizes nothing.
		workspace := t.TempDir()
		writeFatalWorkspace(t, workspace)
		capture := t.TempDir()
		result := runCLI(t, cliPath, capture, javaPath, jarPath,
			workspace, "--language", "java",
			"--source-root", ".",
			"--analyzer-meta", "classpath=",
			"--analyzer-meta", "javaLanguageLevel=17")

		if result.exitCode == 0 {
			t.Fatalf("CLI exit = 0, want non-zero for a fatal analyzer error; stdout:\n%s", result.stdout)
		}
		if strings.Contains(result.stdout, "analyzed ") {
			t.Errorf("CLI must not publish the graph summary on fatal failure: %q", result.stdout)
		}

		// The real Analyzer streams records before emitting the error record.
		records := capturedRecords(t, capture)
		var sawMethod, sawError bool
		var errorRecord protocol.AnalyzerError
		for _, record := range records {
			switch typed := record.(type) {
			case protocol.MethodSymbol:
				if sawError {
					t.Error("method symbol after the error record")
				}
				sawMethod = true
			case protocol.AnalyzerError:
				sawError = true
				errorRecord = typed
			}
		}
		if !sawMethod || !sawError {
			t.Fatalf("expected streamed records before a fatal error record (method=%v error=%v)", sawMethod, sawError)
		}
		if errorRecord.Code != "JAVA_INCOMPLETE_ANALYSIS" || len(errorRecord.Details) < 2 {
			t.Fatalf("error record = %+v, want JAVA_INCOMPLETE_ANALYSIS with >=2 details", errorRecord)
		}

		// The shared renderer prints the summary first, then the details in
		// array order, each with canonical metadata JSON.
		summary := strings.Index(result.stderr, "Error: analyzer reported a fatal error: JAVA_INCOMPLETE_ANALYSIS")
		first := strings.Index(result.stderr, "detail[0] "+errorRecord.Details[0].Code)
		second := strings.Index(result.stderr, "detail[1] "+errorRecord.Details[1].Code)
		if !(summary >= 0 && summary < first && first < second) {
			t.Errorf("CLI stderr must render summary then ordered details:\n%s", result.stderr)
		}
		// The renderer never interprets Analyzer-specific keys; it prints them
		// as canonical JSON. Assert only that a metadata line exists, not which
		// keys it holds.
		if !strings.Contains(result.stderr, "  metadata {") {
			t.Errorf("CLI stderr must render detail metadata as canonical JSON:\n%s", result.stderr)
		}
	})
}

type cliResult struct {
	exitCode int
	stdout   string
	stderr   string
}

// runCLI runs the real Core CLI with --analyzer-cmd pointing at the recording
// proxy, which launches the real Analyzer jar.
func runCLI(t *testing.T, cliPath, captureDir, javaPath, jarPath string, args ...string) cliResult {
	t.Helper()
	// proxyCmd is quoted by hand, so a path containing a double quote would
	// corrupt the command. Fail explicitly rather than silently.
	for _, part := range []string{os.Args[0], captureDir, javaPath, jarPath} {
		if strings.Contains(part, `"`) {
			t.Fatalf("path containing a double quote is not supported by the proxy command quoting: %q", part)
		}
	}
	proxyCmd := fmt.Sprintf(`"%s" -test.run=TestAnalyzerRecordingProxyHelperProcess -- --proxy-capture "%s" "%s" -jar "%s"`,
		os.Args[0], captureDir, javaPath, jarPath)
	cmd := exec.Command(cliPath, append([]string{"analyze"}, append(args, "--analyzer-cmd", proxyCmd)...)...)
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	err := cmd.Run()
	exit := 0
	if exitErr, ok := err.(*exec.ExitError); ok {
		exit = exitErr.ExitCode()
	} else if err != nil {
		t.Fatalf("run CLI: %v", err)
	}
	return cliResult{exitCode: exit, stdout: stdout.String(), stderr: stderr.String()}
}

func multiModuleFixtureRoot(t *testing.T) string {
	t.Helper()
	root, err := filepath.Abs(filepath.Join("..", "..", "testdata", "fixtures", "java", "multi-module-spring-project"))
	if err != nil {
		t.Fatalf("resolve multi-module fixture root: %v", err)
	}
	return root
}

// ensureMultiModuleManifest builds the fixture (classes output + explicit
// classpath manifest) when missing; a build failure fails the test.
func ensureMultiModuleManifest(t *testing.T, fixture string) []string {
	t.Helper()
	manifestPath := filepath.Join(fixture, "build", "depwalk-classpath.txt")
	if _, err := os.Stat(manifestPath); err != nil {
		gradlew, err := filepath.Abs(filepath.Join("..", "..", "analyzers", "java", "gradlew"))
		if err != nil {
			t.Fatalf("resolve gradlew: %v", err)
		}
		cmd := exec.Command(gradlew, "-p", fixture, "--console=plain", "-q", "writeDepwalkClasspath")
		if output, err := cmd.CombinedOutput(); err != nil {
			t.Fatalf("build multi-module fixture: %v\n%s", err, output)
		}
	}
	content, err := os.ReadFile(manifestPath)
	if err != nil {
		t.Fatalf("read classpath manifest: %v", err)
	}
	var entries []string
	for _, line := range strings.Split(string(content), "\n") {
		if line = strings.TrimSpace(line); line != "" {
			entries = append(entries, line)
		}
	}
	if len(entries) == 0 {
		t.Fatal("classpath manifest is empty")
	}
	return entries
}

// buildCoreCLI builds the real depwalk CLI binary; a build failure fails the test.
func buildCoreCLI(t *testing.T) string {
	t.Helper()
	binary := filepath.Join(t.TempDir(), "depwalk")
	cmd := exec.Command("go", "build", "-o", binary, "../cmd/depwalk")
	if output, err := cmd.CombinedOutput(); err != nil {
		t.Fatalf("build Core CLI: %v\n%s", err, output)
	}
	return binary
}

type expectedMultiModuleGraph struct {
	Methods []struct {
		MethodID       string `json:"methodId"`
		SourceLocation string `json:"sourceLocation"`
	} `json:"methods"`
	Edges []struct {
		Caller     string   `json:"caller"`
		Callee     string   `json:"callee"`
		Resolution string   `json:"resolution"`
		Provenance []string `json:"provenance"`
	} `json:"edges"`
}

func loadExpectedMultiModuleGraph(t *testing.T, fixture string) expectedMultiModuleGraph {
	t.Helper()
	content, err := os.ReadFile(filepath.Join(fixture, "expected", "graph.json"))
	if err != nil {
		t.Fatalf("read expected graph: %v", err)
	}
	var expected expectedMultiModuleGraph
	if err := json.Unmarshal(content, &expected); err != nil {
		t.Fatalf("parse expected graph: %v", err)
	}
	return expected
}

func capturedText(t *testing.T, captureDir, name string) string {
	t.Helper()
	content, err := os.ReadFile(filepath.Join(captureDir, name))
	if err != nil {
		t.Fatalf("read capture %s: %v", name, err)
	}
	return string(content)
}

func capturedRequest(t *testing.T, captureDir string) map[string]any {
	t.Helper()
	var request map[string]any
	if err := json.Unmarshal([]byte(capturedText(t, captureDir, "request.jsonl")), &request); err != nil {
		t.Fatalf("parse captured request: %v", err)
	}
	return request
}

func capturedRecords(t *testing.T, captureDir string) []protocol.Record {
	t.Helper()
	var records []protocol.Record
	for _, line := range strings.Split(capturedText(t, captureDir, "stdout.jsonl"), "\n") {
		if strings.TrimSpace(line) == "" {
			continue
		}
		record, err := protocol.ParseRecord([]byte(line))
		if err != nil {
			t.Fatalf("captured stdout violates the protocol: %v\nline: %s", err, line)
		}
		records = append(records, record)
	}
	return records
}

// capturedGraph returns deterministic comparable views of the raw Analyzer
// graph (method locations by id, edge keys with metadata, edgeId excluded).
func capturedGraph(t *testing.T, captureDir string) (map[string]string, []string) {
	t.Helper()
	methods := map[string]string{}
	var edges []string
	for _, record := range capturedRecords(t, captureDir) {
		switch typed := record.(type) {
		case protocol.MethodSymbol:
			location := ""
			if typed.Source != nil {
				location = typed.Source.Path
			}
			methods[typed.MethodID] = location
		case protocol.CallEdge:
			metadata, err := json.Marshal(typed.Metadata)
			if err != nil {
				t.Fatalf("marshal edge metadata: %v", err)
			}
			edges = append(edges, typed.CallerMethodID+" -> "+typed.CalleeMethodID+" "+string(metadata))
		}
	}
	sort.Strings(edges)
	return methods, edges
}

func assertCapturedGraphMatches(t *testing.T, captureDir string, expected expectedMultiModuleGraph) {
	t.Helper()
	methods, edges := capturedGraph(t, captureDir)
	// expected/graph.json is the fixture's complete set. Matching the counts
	// turns the containment check into a set equality check, so extra methods
	// or edges are detected too.
	if len(methods) != len(expected.Methods) {
		t.Errorf("method count = %d, want exactly %d: %v", len(methods), len(expected.Methods), methods)
	}
	if len(edges) != len(expected.Edges) {
		t.Errorf("edge count = %d, want exactly %d: %v", len(edges), len(expected.Edges), edges)
	}
	for _, method := range expected.Methods {
		location, ok := methods[method.MethodID]
		if !ok {
			t.Errorf("missing expected method %s", method.MethodID)
			continue
		}
		if location != method.SourceLocation {
			t.Errorf("method %s location = %q, want %q", method.MethodID, location, method.SourceLocation)
		}
	}
	for _, edge := range expected.Edges {
		found := false
		for _, actual := range edges {
			if !strings.HasPrefix(actual, edge.Caller+" -> "+edge.Callee+" ") {
				continue
			}
			found = true
			if edge.Resolution != "" && !strings.Contains(actual, `"resolution":"`+edge.Resolution+`"`) {
				t.Errorf("edge %s -> %s missing resolution %q: %s", edge.Caller, edge.Callee, edge.Resolution, actual)
			}
			for _, provenance := range edge.Provenance {
				if !strings.Contains(actual, provenance) {
					t.Errorf("edge %s -> %s missing provenance %q: %s", edge.Caller, edge.Callee, provenance, actual)
				}
			}
			break
		}
		if !found {
			t.Errorf("missing expected edge %s -> %s in %v", edge.Caller, edge.Callee, edges)
		}
	}
}

// assertNoGradleFreeText verifies the analyzer stderr carries only
// depwalk-generated fixed lines and metrics (no Gradle free text).
func assertNoGradleFreeText(t *testing.T, stderrText string) {
	t.Helper()
	for _, line := range strings.Split(stderrText, "\n") {
		if strings.TrimSpace(line) == "" {
			continue
		}
		if strings.HasPrefix(line, "depwalk: ") ||
			strings.HasPrefix(line, "callSites=") ||
			strings.HasPrefix(line, "analyzedFiles=") {
			continue
		}
		t.Errorf("unexpected non-depwalk stderr line: %q", line)
	}
}

// writeFatalWorkspace creates a workspace whose real analysis streams graph
// records first and then fails with JAVA_INCOMPLETE_ANALYSIS (two unresolved
// in-scope calls in a second file).
func writeFatalWorkspace(t *testing.T, workspace string) {
	t.Helper()
	dir := filepath.Join(workspace, "com", "example", "fatal")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	writeFile(t, filepath.Join(dir, "AOk.java"),
		"package com.example.fatal;\npublic class AOk { void run() { helper(); } void helper() {} }\n")
	writeFile(t, filepath.Join(dir, "Bad.java"),
		"package com.example.fatal;\npublic class Bad {\n"+
			"    void one() { MissingOne.go(); }\n"+
			"    void two() { MissingTwo.go(); }\n"+
			"}\n")
}

func writeFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatalf("write %s: %v", path, err)
	}
}
