package e2e

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

// TestCLIEntryPointMetadata は entry point 分類の標識が CLI 経由で JSON の
// nodes[].metadata.entryPoint に透過表出されることを確かめる。fixture は Spring を
// classpath に置かない source-only の一時 workspace で、annotation の FQN は
// import 経由で解決される。
func TestCLIEntryPointMetadata(t *testing.T) {
	javaPath := findJava25(t)
	jarPath := findAnalyzerJar(t)
	cliPath := buildCoreCLI(t)

	workspace := t.TempDir()
	dir := filepath.Join(workspace, "com", "example")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	writeFile(t, filepath.Join(dir, "Batch.java"),
		"package com.example;\n"+
			"import org.springframework.scheduling.annotation.Scheduled;\n"+
			"public class Batch {\n"+
			"    @Scheduled(cron = \"0 0 * * * *\")\n"+
			"    public void nightly() { helper(); }\n"+
			"    void helper() {}\n"+
			"}\n")

	result := runCLI(t, cliPath, t.TempDir(), javaPath, jarPath,
		workspace,
		"--language", "java",
		"--source-root", ".",
		"--analyzer-meta", "classpath=",
		"--analyzer-meta", "javaLanguageLevel=17",
		"--method", "com.example.Batch#nightly()",
		"--direction", "callee",
		"--format", "json",
	)
	if result.exitCode != 0 {
		t.Fatalf("CLI exit = %d, want 0; stderr:\n%s", result.exitCode, result.stderr)
	}

	var document struct {
		Nodes []struct {
			MethodID string         `json:"methodId"`
			Metadata map[string]any `json:"metadata"`
		} `json:"nodes"`
	}
	if err := json.Unmarshal([]byte(result.stdout), &document); err != nil {
		t.Fatalf("CLI stdout is not valid JSON: %v\n%s", err, result.stdout)
	}

	const wantMethod = "java:com.example.Batch#nightly()"
	const wantAnnotation = "org.springframework.scheduling.annotation.Scheduled"
	found := false
	for _, node := range document.Nodes {
		switch node.MethodID {
		case wantMethod:
			found = true
			entryPoint, ok := node.Metadata["entryPoint"].([]any)
			if !ok || len(entryPoint) != 1 || entryPoint[0] != wantAnnotation {
				t.Fatalf("nodes[%s].metadata.entryPoint = %v, want [%s]", wantMethod, node.Metadata, wantAnnotation)
			}
		default:
			if _, ok := node.Metadata["entryPoint"]; ok {
				t.Fatalf("unexpected entryPoint marker on %s: %v", node.MethodID, node.Metadata)
			}
		}
	}
	if !found {
		t.Fatalf("node %s not found in JSON output:\n%s", wantMethod, result.stdout)
	}
}
