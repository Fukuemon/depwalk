package e2e

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

// TestCLIEventEdgeMetadata は publish→listener の event edge が CLI 経由で
// JSON の edges[].metadata (provenance=spring-event) に透過表出されることを
// 確かめる。fixture は publisher interface の stub を含む source-only の
// 一時 workspace。
func TestCLIEventEdgeMetadata(t *testing.T) {
	javaPath := findJava25(t)
	jarPath := findAnalyzerJar(t)
	cliPath := buildCoreCLI(t)

	workspace := t.TempDir()
	writeFile(t, mkdirFor(t, workspace, "org/springframework/context/ApplicationEventPublisher.java"),
		"package org.springframework.context;\n"+
			"public interface ApplicationEventPublisher { void publishEvent(Object event); }\n")
	writeFile(t, mkdirFor(t, workspace, "org/springframework/context/event/EventListener.java"),
		"package org.springframework.context.event;\npublic @interface EventListener { }\n")
	writeFile(t, mkdirFor(t, workspace, "com/example/OrderEvent.java"),
		"package com.example;\npublic class OrderEvent { }\n")
	writeFile(t, mkdirFor(t, workspace, "com/example/Publisher.java"),
		"package com.example;\n"+
			"import org.springframework.context.ApplicationEventPublisher;\n"+
			"public class Publisher {\n"+
			"    private final ApplicationEventPublisher publisher;\n"+
			"    Publisher(ApplicationEventPublisher publisher) { this.publisher = publisher; }\n"+
			"    void publishOrder() { publisher.publishEvent(new OrderEvent()); }\n"+
			"}\n")
	writeFile(t, mkdirFor(t, workspace, "com/example/Listener.java"),
		"package com.example;\n"+
			"import org.springframework.context.event.EventListener;\n"+
			"public class Listener {\n"+
			"    @EventListener\n"+
			"    void onOrder(OrderEvent event) { }\n"+
			"}\n")

	result := runCLI(t, cliPath, t.TempDir(), javaPath, jarPath,
		workspace,
		"--language", "java",
		"--source-root", ".",
		"--analyzer-meta", "classpath=",
		"--analyzer-meta", "javaLanguageLevel=17",
		"--method", "com.example.Publisher#publishOrder()",
		"--direction", "callee",
		"--format", "json",
	)
	if result.exitCode != 0 {
		t.Fatalf("CLI exit = %d, want 0; stderr:\n%s", result.exitCode, result.stderr)
	}

	var document struct {
		Edges []struct {
			CallerMethodID string         `json:"callerMethodId"`
			CalleeMethodID string         `json:"calleeMethodId"`
			Metadata       map[string]any `json:"metadata"`
		} `json:"edges"`
	}
	if err := json.Unmarshal([]byte(result.stdout), &document); err != nil {
		t.Fatalf("CLI stdout is not valid JSON: %v\n%s", err, result.stdout)
	}
	for _, edge := range document.Edges {
		if edge.CalleeMethodID != "java:com.example.Listener#onOrder(com.example.OrderEvent)" {
			continue
		}
		if edge.CallerMethodID != "java:com.example.Publisher#publishOrder()" {
			t.Fatalf("event edge caller = %s, want publishOrder", edge.CallerMethodID)
		}
		provenance, _ := edge.Metadata["provenance"].([]any)
		if len(provenance) != 1 || provenance[0] != "spring-event" {
			t.Fatalf("event edge metadata = %v, want provenance [spring-event]", edge.Metadata)
		}
		if edge.Metadata["resolution"] != "unique" {
			t.Fatalf("event edge resolution = %v, want unique", edge.Metadata["resolution"])
		}
		return
	}
	t.Fatalf("event edge to the listener not found in JSON output:\n%s", result.stdout)
}

func mkdirFor(t *testing.T, workspace, relative string) string {
	t.Helper()
	path := filepath.Join(workspace, filepath.FromSlash(relative))
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatalf("mkdir for %s: %v", relative, err)
	}
	return path
}
