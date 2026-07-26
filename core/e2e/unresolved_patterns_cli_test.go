package e2e

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

// TestUnresolvedCallPatternsCLI analyses the hard-to-resolve call pattern
// fixture (testdata/fixtures/java/multi-module-spring-project/patterns/) with
// the real Core CLI and the real Analyzer jar through auto discovery.
//
// Five patterns used to be reported as unresolved: fluent chains, method
// references, explicit super calls, var with generics, and cross-module Lombok
// members. All of them are now rescued, so this test guards the success
// expectation that every pattern becomes an edge under the bytecode-only
// member contract.
//
// patterns/ is a standalone build that the root fixture's settings.gradle does
// not include, so it does not affect TestGradleMultiProjectCLI's expectations.
func TestUnresolvedCallPatternsCLI(t *testing.T) {
	javaPath := findJava25(t)
	jarPath := findAnalyzerJar(t)
	fixture := patternsFixtureRoot(t)
	ensurePatternsClasses(t, fixture)
	cliPath := buildCoreCLI(t)

	capture := t.TempDir()
	result := runCLI(t, cliPath, capture, javaPath, jarPath, fixture, "--language", "java")

	if result.exitCode != 0 {
		t.Fatalf("CLI exit = %d, want 0 once every pattern is rescued; stderr:\n%s", result.exitCode, result.stderr)
	}

	records := capturedRecords(t, capture)
	edges := map[string][]protocol.CallEdge{}
	for _, record := range records {
		switch typed := record.(type) {
		case protocol.AnalyzerError:
			t.Fatalf("unexpected analyzer error record: %+v", typed)
		case protocol.Diagnostic:
			if typed.Code == "JAVA_UNRESOLVED_SYMBOL" {
				t.Errorf("no unresolved diagnostics expected after the rescues: %+v", typed)
			}
		case protocol.CallEdge:
			edges[typed.CalleeMethodID] = append(edges[typed.CalleeMethodID], typed)
		}
	}

	// Rescue outcome: cross-module generated members become edges under the
	// bytecode-only contract (calleeOrigin=project-bytecode-member).
	itemCtor := "java:com.example.pat.lib.Item#<init>(java.lang.String,int)"
	itemGetName := "java:com.example.pat.lib.Item#getName()"
	baseTaskCtor := "java:com.example.pat.lib.BaseTask#<init>(java.lang.String)"

	// Four callers reach getName (cross-module constructor, var with generics,
	// the head of a fluent chain, and a method reference); the constructor has
	// two callers.
	if got := len(edges[itemCtor]); got < 2 {
		t.Errorf("Item ctor edges = %d, want >= 2 (cross-module, fluent chain head): %v", got, edges[itemCtor])
	}
	if got := len(edges[itemGetName]); got < 3 {
		t.Errorf("Item getName edges = %d, want >= 3 (cross-module, var, method reference): %v", got, edges[itemGetName])
	}
	if got := len(edges[baseTaskCtor]); got != 1 {
		t.Errorf("BaseTask ctor edges = %d, want 1 (explicit super): %v", got, edges[baseTaskCtor])
	}
	for _, callee := range []string{itemCtor, itemGetName, baseTaskCtor} {
		for _, edge := range edges[callee] {
			if edge.Metadata["calleeOrigin"] != "project-bytecode-member" {
				t.Errorf("edge to %s must follow the bytecode-only member contract: %+v", callee, edge)
			}
		}
	}

	// A rescued method reference edge carries the viaMethodReference marker.
	foundReference := false
	for _, edge := range edges[itemGetName] {
		if edge.Metadata["viaMethodReference"] == true {
			foundReference = true
		}
	}
	if !foundReference {
		t.Errorf("rescued method reference edge must carry viaMethodReference: %v", edges[itemGetName])
	}

	// Completeness gate: every call site is classified and none stay unresolved.
	stderrText := capturedText(t, capture, "stderr.txt")
	if !strings.Contains(stderrText, "silentOmission=0") {
		t.Errorf("ledger summary must report silentOmission=0:\n%s", stderrText)
	}
}

func patternsFixtureRoot(t *testing.T) string {
	t.Helper()
	return filepath.Join(multiModuleFixtureRoot(t), "patterns")
}

// ensurePatternsClasses builds the patterns fixture classes output (Lombok
// generated members) so bytecode rescue has real candidates; a build failure
// fails the test.
func ensurePatternsClasses(t *testing.T, fixture string) {
	t.Helper()
	marker := filepath.Join(fixture, "lib", "build", "classes", "java", "main",
		"com", "example", "pat", "lib", "Item.class")
	if _, err := os.Stat(marker); err == nil {
		return
	}
	gradlew, err := filepath.Abs(filepath.Join("..", "..", "analyzers", "java", "gradlew"))
	if err != nil {
		t.Fatalf("resolve gradlew: %v", err)
	}
	cmd := exec.Command(gradlew, "-p", fixture, "--console=plain", "-q", "writeDepwalkClasspath")
	if output, err := cmd.CombinedOutput(); err != nil {
		t.Fatalf("build patterns fixture: %v\n%s", err, output)
	}
}
