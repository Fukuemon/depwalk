package e2e

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

// TestUnresolvedCallPatternsCLI は解決が難しい呼び出しパターンの fixture
// (testdata/fixtures/java/multi-module-spring-project/patterns/) を、実 Core CLI と
// 実 Analyzer jar の自動 discovery で解析する。
//
// かつて未解決として報告されていた 5 パターン (fluent chain、method reference、
// 明示的な super 呼び出し、generics 付き var、cross-module の Lombok member) は
// すべて救済済みである。bytecode-only member の契約下で各パターンが edge になる、
// という成功の期待を本テストが守る。
//
// patterns/ は root fixture の settings.gradle が含めない独立 build なので、
// TestGradleMultiProjectCLI の期待値には影響しない。
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

	// 救済の結果: cross-module の生成 member が bytecode-only の契約で edge になる
	// (calleeOrigin=project-bytecode-member)。
	itemCtor := "java:com.example.pat.lib.Item#<init>(java.lang.String,int)"
	itemGetName := "java:com.example.pat.lib.Item#getName()"
	baseTaskCtor := "java:com.example.pat.lib.BaseTask#<init>(java.lang.String)"

	// getName には 4 つの caller が到達する (cross-module のコンストラクタ、
	// generics 付き var、fluent chain の先頭、method reference)。
	// コンストラクタ側の caller は 2 つ。
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

	// 救済された method reference の edge は viaMethodReference の標識を持つ。
	foundReference := false
	for _, edge := range edges[itemGetName] {
		if edge.Metadata["viaMethodReference"] == true {
			foundReference = true
		}
	}
	if !foundReference {
		t.Errorf("rescued method reference edge must carry viaMethodReference: %v", edges[itemGetName])
	}

	// 完全性 gate: すべての call site が分類され、未解決が残らない。
	stderrText := capturedText(t, capture, "stderr.txt")
	if !strings.Contains(stderrText, "silentOmission=0") {
		t.Errorf("ledger summary must report silentOmission=0:\n%s", stderrText)
	}
}

func patternsFixtureRoot(t *testing.T) string {
	t.Helper()
	return filepath.Join(multiModuleFixtureRoot(t), "patterns")
}

// ensurePatternsClasses は patterns fixture の classes output (Lombok 生成 member)
// を build する。bytecode 救済に実在の候補を与えるため。build 失敗はテストの失敗とする。
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
