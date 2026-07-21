package e2e

import (
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

// TestUnresolvedCallPatternsCLI は spec #27 D4 の未解決 call パターン fixture
// (testdata/fixtures/java/multi-module-spring-project/patterns/) を実 Core CLI +
// 実 Analyzer jar の auto discovery で解析し、現行実装の未解決状態
// (JAVA_INCOMPLETE_ANALYSIS) と診断 metadata 4 項目 (spec #27 D2) を固定する。
//
// patterns/ は root fixture の settings.gradle に含まれない独立 build であり、
// TestGradleMultiProjectCLI の成功 graph 期待には影響しない。
//
// 【P4 系 prompt への注記】この期待値は「修正前の現状」を固定したもの。
// P4_01 (④⑤) / P4_02 (⑧) / P4_03 (⑥) / P4_04 (①⑦) の救済修正が入ったら、
// 該当パターンの期待を成功側 (edge / 除外) へ更新し、最終的に fixture 全体が
// exit 0 になることを目指す (①⑦ が scope 外判定なら未解決期待を残す)。
func TestUnresolvedCallPatternsCLI(t *testing.T) {
	javaPath := findJava25(t)
	jarPath := findAnalyzerJar(t)
	fixture := patternsFixtureRoot(t)
	ensurePatternsClasses(t, fixture)
	cliPath := buildCoreCLI(t)

	capture := t.TempDir()
	result := runCLI(t, cliPath, capture, javaPath, jarPath, fixture, "--language", "java")

	if result.exitCode == 0 {
		t.Fatalf("CLI exit = 0, want non-zero while rescue gaps remain; stdout:\n%s", result.stdout)
	}

	records := capturedRecords(t, capture)
	var errorRecord *protocol.AnalyzerError
	for _, record := range records {
		if typed, ok := record.(protocol.AnalyzerError); ok {
			errorRecord = &typed
		}
	}
	if errorRecord == nil {
		t.Fatal("expected a JAVA_INCOMPLETE_ANALYSIS error record")
	}
	if errorRecord.Code != "JAVA_INCOMPLETE_ANALYSIS" {
		t.Fatalf("error code = %q, want JAVA_INCOMPLETE_ANALYSIS", errorRecord.Code)
	}

	// 修正前の現状: 5 パターン由来の 10 件 (⑧ctor/getter ×2、⑤super、
	// ①chain 4 件、④reference + 波及 collect、⑦var 経由 getter)。
	// GenericChainCase (自己境界 generic builder + overload + lambda) は
	// JavaParser 3.28.2 が解決に成功するため details へ現れない。件数固定は
	// 「この形状を誤って未解決へ倒さない」ことの回帰ガードを兼ねる
	// (①の真の再現形は P2_02 の実測診断で特定し、P4_04 で fixture へ追加する)。
	// 現状の全 detail は resolver 例外を伴う失敗のため、exceptionClass は
	// phase を問わず非空のクラス名になる前提で一律検査する。
	// P4_01 で ④⑤ の bytecode 救済経路が追加されたが、cross-module の index
	// 欠陥 (⑧、P4_02 対象) が同じ土台のため件数は変わらず、phase だけが
	// bytecode-rescue へ変わった (救済試行に到達している証跡)。
	if len(errorRecord.Details) != 10 {
		t.Errorf("details = %d entries, want 10 (current pre-fix expectation): %+v",
			len(errorRecord.Details), errorRecord.Details)
	}

	type wantDetail struct {
		pathSuffix           string
		callKind             string
		reason               string
		target               string
		resolutionPhase      string
		receiverKind         string
		receiverTypeResolved bool
	}
	wants := []wantDetail{
		{"CrossModuleLombokCase.java", "object-creation", "unresolved-constructor-call", "Item", "bytecode-rescue", "none", true},
		{"CrossModuleLombokCase.java", "method-call", "unresolved-method-call", "getName", "bytecode-rescue", "NameExpr", true},
		{"ExplicitSuperCase.java", "explicit-constructor-invocation", "unresolved-constructor-call", "super", "bytecode-rescue", "super", true},
		{"FluentChainCase.java", "object-creation", "unresolved-constructor-call", "Item", "bytecode-rescue", "none", true},
		{"FluentChainCase.java", "method-call", "unresolved-method-call", "getName", "bytecode-rescue", "ObjectCreationExpr", true},
		{"FluentChainCase.java", "method-call", "unresolved-method-call", "trim", "bytecode-rescue", "MethodCallExpr", false},
		{"FluentChainCase.java", "method-call", "unresolved-method-call", "isEmpty", "bytecode-rescue", "MethodCallExpr", false},
		{"MethodReferenceCase.java", "method-call", "unresolved-method-call", "collect", "bytecode-rescue", "MethodCallExpr", false},
		{"MethodReferenceCase.java", "method-reference", "unresolved-method-reference", "getName", "bytecode-rescue", "TypeExpr", true},
		{"VarGenericCase.java", "method-call", "unresolved-method-call", "getName", "bytecode-rescue", "NameExpr", true},
	}
	bareClassName := regexp.MustCompile(`^[\w.$]+$`)
	for _, want := range wants {
		found := false
		for _, detail := range errorRecord.Details {
			if detail.Source == nil || !strings.HasSuffix(detail.Source.Path, want.pathSuffix) {
				continue
			}
			metadata := detail.Metadata
			if metadata["callKind"] != want.callKind || metadata["target"] != want.target {
				continue
			}
			found = true
			if metadata["reason"] != want.reason {
				t.Errorf("%s %s: reason = %v, want %s", want.pathSuffix, want.target, metadata["reason"], want.reason)
			}
			// spec #27 D2: sanitize 済み診断 4 項目。
			if metadata["resolutionPhase"] != want.resolutionPhase {
				t.Errorf("%s %s: resolutionPhase = %v, want %s",
					want.pathSuffix, want.target, metadata["resolutionPhase"], want.resolutionPhase)
			}
			if metadata["receiverKind"] != want.receiverKind {
				t.Errorf("%s %s: receiverKind = %v, want %s",
					want.pathSuffix, want.target, metadata["receiverKind"], want.receiverKind)
			}
			if metadata["receiverTypeResolved"] != want.receiverTypeResolved {
				t.Errorf("%s %s: receiverTypeResolved = %v, want %v",
					want.pathSuffix, want.target, metadata["receiverTypeResolved"], want.receiverTypeResolved)
			}
			exceptionClass, _ := metadata["exceptionClass"].(string)
			if !bareClassName.MatchString(exceptionClass) {
				t.Errorf("%s %s: exceptionClass = %q, want a bare class name (no message)",
					want.pathSuffix, want.target, exceptionClass)
			}
			break
		}
		if !found {
			t.Errorf("missing expected detail %s %s (%s)", want.pathSuffix, want.target, want.callKind)
		}
	}

	// sanitize 制約 (spec #24 D24 / #27 D2): 絶対 path が details に混入しない。
	for _, detail := range errorRecord.Details {
		if detail.Source != nil && filepath.IsAbs(detail.Source.Path) {
			t.Errorf("detail source path must be workspace-relative: %q", detail.Source.Path)
		}
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
