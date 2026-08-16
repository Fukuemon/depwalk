package e2e

import (
	"bytes"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

// TestCLIOOMHint は analyzer が valid error record なしで異常終了し stderr に
// java.lang.OutOfMemoryError を残したとき、CLI が対処 (-Xmx) 付きエラーを表示し
// exit 1 になることを確かめる。fake analyzer は stderr へ OOM を書いて死ぬだけの
// script で、stdout には何も出さない (error record なしの経路)。
func TestCLIOOMHint(t *testing.T) {
	// fake analyzer が POSIX shell script (#!/bin/sh) であり、Windows では
	// 実行できないため skip する。
	if runtime.GOOS == "windows" {
		t.Skip("fake analyzer は POSIX shell に依存する")
	}
	cliPath := buildCoreCLI(t)

	script := filepath.Join(t.TempDir(), "oom-analyzer.sh")
	if err := os.WriteFile(script,
		[]byte("#!/bin/sh\ncat > /dev/null\n"+
			"echo 'Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space' >&2\n"+
			"exit 1\n"), 0o755); err != nil {
		t.Fatalf("write fake analyzer: %v", err)
	}

	workspace := t.TempDir()
	writeFile(t, mkdirFor(t, workspace, "com/example/A.java"),
		"package com.example;\nclass A { void a() {} }\n")

	cmd := exec.Command(cliPath, "analyze", workspace,
		"--language", "java",
		"--source-root", ".",
		"--analyzer-meta", "classpath=",
		"--analyzer-meta", "javaLanguageLevel=17",
		"--analyzer-cmd", script,
	)
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	err := cmd.Run()
	exitErr, ok := err.(*exec.ExitError)
	if !ok || exitErr.ExitCode() != 1 {
		t.Fatalf("CLI err = %v, want exit 1; stderr:\n%s", err, stderr.String())
	}
	for _, want := range []string{"OutOfMemoryError", "-Xmx"} {
		if !strings.Contains(stderr.String(), want) {
			t.Fatalf("stderr lacks %q:\n%s", want, stderr.String())
		}
	}
}
