package e2e

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

// TestUnresolvedCallPatternsCLI は spec #27 D4 の未解決 call パターン fixture
// (testdata/fixtures/java/multi-module-spring-project/patterns/) を実 Core CLI +
// 実 Analyzer jar の auto discovery で解析する。
//
// P2_01 時点では 5 パターン (①fluent chain / ④method reference / ⑤explicit
// super / ⑦var+generic / ⑧cross-module Lombok) が JAVA_INCOMPLETE_ANALYSIS の
// 未解決 10 件として固定されていた。P4_01 (④⑤の救済 fallback) と P4_02
// (⑧: Gradle model が依存 project を jar として classpath へ返す場合に依存
// output が external artifact 扱いになる欠陥の修正) により全件が救済され、
// 本テストは「全パターンが bytecode-only member 契約 (D21) で edge 化される」
// 成功期待の回帰ガードへ更新された。
//
// patterns/ は root fixture の settings.gradle に含まれない独立 build であり、
// TestGradleMultiProjectCLI の成功 graph 期待には影響しない。
func TestUnresolvedCallPatternsCLI(t *testing.T) {
	javaPath := findJava25(t)
	jarPath := findAnalyzerJar(t)
	fixture := patternsFixtureRoot(t)
	ensurePatternsClasses(t, fixture)
	cliPath := buildCoreCLI(t)

	capture := t.TempDir()
	result := runCLI(t, cliPath, capture, javaPath, jarPath, fixture, "--language", "java")

	if result.exitCode != 0 {
		t.Fatalf("CLI exit = %d, want 0 after the spec #27 rescues; stderr:\n%s", result.exitCode, result.stderr)
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

	// 各パターンの救済結果: cross-module の生成 member が bytecode-only 契約
	// (calleeOrigin=project-bytecode-member) で edge 化される。
	itemCtor := "java:com.example.pat.lib.Item#<init>(java.lang.String,int)"
	itemGetName := "java:com.example.pat.lib.Item#getName()"
	baseTaskCtor := "java:com.example.pat.lib.BaseTask#<init>(java.lang.String)"

	// ⑧ constructor + ⑦ var 経由 + ① chain 起点 + ④ reference で計 4 caller が
	// getName へ到達し、ctor は ⑧ と ① の 2 caller。
	if got := len(edges[itemCtor]); got < 2 {
		t.Errorf("Item ctor edges = %d, want >= 2 (⑧ cross-module / ① chain 起点): %v", got, edges[itemCtor])
	}
	if got := len(edges[itemGetName]); got < 3 {
		t.Errorf("Item getName edges = %d, want >= 3 (⑧ / ⑦ var / ④ reference): %v", got, edges[itemGetName])
	}
	if got := len(edges[baseTaskCtor]); got != 1 {
		t.Errorf("BaseTask ctor edges = %d, want 1 (⑤ explicit super): %v", got, edges[baseTaskCtor])
	}
	for _, callee := range []string{itemCtor, itemGetName, baseTaskCtor} {
		for _, edge := range edges[callee] {
			if edge.Metadata["calleeOrigin"] != "project-bytecode-member" {
				t.Errorf("edge to %s must follow the bytecode-only contract (D21): %+v", callee, edge)
			}
		}
	}

	// ④ method reference の救済 edge は viaMethodReference 標識を持つ。
	foundReference := false
	for _, edge := range edges[itemGetName] {
		if edge.Metadata["viaMethodReference"] == true {
			foundReference = true
		}
	}
	if !foundReference {
		t.Errorf("rescued method reference edge must carry viaMethodReference: %v", edges[itemGetName])
	}

	// 完全性 gate: 全 call site が分類され、未解決ゼロで成功する。
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
