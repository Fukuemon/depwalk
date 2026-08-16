package e2e

import (
	"encoding/json"
	"testing"
)

// TestCLICallableEdgeMetadata は callable invocation edge (viaCallableInvocation)
// が CLI 経由で JSON の edges[].metadata に透過表出されることを確かめる。
func TestCLICallableEdgeMetadata(t *testing.T) {
	javaPath := findJava25(t)
	jarPath := findAnalyzerJar(t)
	cliPath := buildCoreCLI(t)

	workspace := t.TempDir()
	writeFile(t, mkdirFor(t, workspace, "com/example/Retry.java"),
		"package com.example;\n"+
			"import java.util.function.Supplier;\n"+
			"final class Retry {\n"+
			"    static String retry(Supplier<String> supplier) { return supplier.get(); }\n"+
			"}\n")
	writeFile(t, mkdirFor(t, workspace, "com/example/Caller.java"),
		"package com.example;\n"+
			"class Caller {\n"+
			"    String fromLambda() { return Retry.retry(() -> work()); }\n"+
			"    String work() { return \"w\"; }\n"+
			"}\n")

	result := runCLI(t, cliPath, t.TempDir(), javaPath, jarPath,
		workspace,
		"--language", "java",
		"--source-root", ".",
		"--analyzer-meta", "classpath=",
		"--analyzer-meta", "javaLanguageLevel=17",
		"--method", "com.example.Retry#retry(java.util.function.Supplier)",
		"--direction", "callee",
		"--format", "json",
	)
	if result.exitCode != 0 {
		t.Fatalf("CLI exit = %d, want 0; stderr:\n%s", result.exitCode, result.stderr)
	}

	var document struct {
		Edges []struct {
			CalleeMethodID string         `json:"calleeMethodId"`
			Metadata       map[string]any `json:"metadata"`
		} `json:"edges"`
	}
	if err := json.Unmarshal([]byte(result.stdout), &document); err != nil {
		t.Fatalf("CLI stdout is not valid JSON: %v\n%s", err, result.stdout)
	}
	for _, edge := range document.Edges {
		if edge.CalleeMethodID != "java:com.example.Caller#fromLambda()" {
			continue
		}
		if edge.Metadata["viaCallableInvocation"] != true {
			t.Fatalf("callable edge metadata = %v, want viaCallableInvocation true", edge.Metadata)
		}
		return
	}
	t.Fatalf("callable invocation edge not found in JSON output:\n%s", result.stdout)
}
