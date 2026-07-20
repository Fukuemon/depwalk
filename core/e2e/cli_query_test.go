package e2e

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestCLIQueryGolden(t *testing.T) {
	javaPath := findJava25(t)
	jarPath := findAnalyzerJar(t)
	fixture := multiModuleFixtureRoot(t)
	ensureMultiModuleManifest(t, fixture)
	cliPath := buildCoreCLI(t)

	tests := []struct {
		name      string
		method    string
		direction string
		format    string
		golden    string
	}{
		{
			name:      "caller-console",
			method:    "com.example.mm.repository.JpaOrderRepository#save(java.lang.String)",
			direction: "caller",
			format:    "console",
			golden:    "cli-query-caller-console.txt",
		},
		{
			name:      "caller-json",
			method:    "com.example.mm.repository.JpaOrderRepository#save(java.lang.String)",
			direction: "caller",
			format:    "json",
			golden:    "cli-query-caller.json",
		},
		{
			name:      "callee-console",
			method:    "com.example.mm.app.OrderController#placeOrder(java.lang.String)",
			direction: "callee",
			format:    "console",
			golden:    "cli-query-callee-console.txt",
		},
		{
			name:      "callee-json",
			method:    "com.example.mm.app.OrderController#placeOrder(java.lang.String)",
			direction: "callee",
			format:    "json",
			golden:    "cli-query-callee.json",
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			result := runCLI(t, cliPath, t.TempDir(), javaPath, jarPath,
				fixture,
				"--language", "java",
				"--method", tc.method,
				"--direction", tc.direction,
				"--max-depth", "1",
				"--format", tc.format,
			)
			if result.exitCode != 0 {
				t.Fatalf("CLI exit = %d, want 0; stderr:\n%s", result.exitCode, result.stderr)
			}

			want, err := os.ReadFile(filepath.Join(fixture, "expected", tc.golden))
			if err != nil {
				t.Fatalf("read golden: %v", err)
			}
			if tc.format == "json" {
				var compact bytes.Buffer
				if err := json.Compact(&compact, want); err != nil {
					t.Fatalf("compact JSON golden: %v", err)
				}
				compact.WriteByte('\n')
				want = compact.Bytes()
			}
			if !bytes.Equal([]byte(result.stdout), want) {
				t.Errorf("CLI stdout:\n%s\nwant:\n%s", result.stdout, want)
			}

			if tc.format == "json" {
				var document struct {
					DepthCutoffs []json.RawMessage `json:"depthCutoffs"`
				}
				if err := json.Unmarshal([]byte(result.stdout), &document); err != nil {
					t.Fatalf("CLI stdout is not valid JSON: %v\n%s", err, result.stdout)
				}
				if len(document.DepthCutoffs) == 0 {
					t.Fatal("JSON output has no depth cutoff, want --max-depth=1 annotation")
				}
			} else if !strings.Contains(result.stdout, "depth limit") {
				t.Fatalf("console output has no depth cutoff annotation:\n%s", result.stdout)
			}
		})
	}
}

func TestCLIQueryExitCodes(t *testing.T) {
	javaPath := findJava25(t)
	jarPath := findAnalyzerJar(t)
	fixture := multiModuleFixtureRoot(t)
	ensureMultiModuleManifest(t, fixture)
	cliPath := buildCoreCLI(t)

	t.Run("success", func(t *testing.T) {
		result := runCLI(t, cliPath, t.TempDir(), javaPath, jarPath,
			fixture,
			"--language", "java",
			"--method", "com.example.mm.app.OrderController#placeOrder(java.lang.String)",
			"--direction", "callee",
		)
		if result.exitCode != 0 {
			t.Fatalf("CLI exit = %d, want 0; stderr:\n%s", result.exitCode, result.stderr)
		}
	})

	t.Run("analyzer-fatal", func(t *testing.T) {
		workspace := t.TempDir()
		writeFatalWorkspace(t, workspace)
		result := runCLI(t, cliPath, t.TempDir(), javaPath, jarPath,
			workspace,
			"--language", "java",
			"--source-root", ".",
			"--analyzer-meta", "classpath=",
			"--analyzer-meta", "javaLanguageLevel=17",
		)
		if result.exitCode != 1 {
			t.Fatalf("CLI exit = %d, want 1; stderr:\n%s", result.exitCode, result.stderr)
		}
	})

	t.Run("invalid-flag", func(t *testing.T) {
		result := runCLI(t, cliPath, t.TempDir(), javaPath, jarPath,
			fixture,
			"--language", "java",
			"--method", "com.example.mm.app.OrderController#placeOrder(java.lang.String)",
			"--direction", "sideways",
		)
		if result.exitCode != 2 {
			t.Fatalf("CLI exit = %d, want 2; stderr:\n%s", result.exitCode, result.stderr)
		}
	})

	t.Run("selector-not-found", func(t *testing.T) {
		result := runCLI(t, cliPath, t.TempDir(), javaPath, jarPath,
			fixture,
			"--language", "java",
			"--method", "com.example.mm.app.Missing#run()",
		)
		if result.exitCode != 2 {
			t.Fatalf("CLI exit = %d, want 2; stderr:\n%s", result.exitCode, result.stderr)
		}
	})
}
