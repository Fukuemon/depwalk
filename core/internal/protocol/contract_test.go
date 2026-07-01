package protocol

import (
	"errors"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
)

func TestContractFixturesValidRecords(t *testing.T) {
	t.Parallel()

	files := fixtureFiles(t, "records", "valid")
	for _, file := range files {
		file := file
		t.Run(filepath.Base(file), func(t *testing.T) {
			t.Parallel()

			for _, line := range fixtureLines(t, file) {
				if _, err := ParseRecord(line); err != nil {
					t.Fatalf("ParseRecord(%s) error = %v", file, err)
				}
			}
		})
	}
}

func TestContractFixturesInvalidRecords(t *testing.T) {
	t.Parallel()

	files := fixtureFiles(t, "records", "invalid")
	for _, file := range files {
		file := file
		t.Run(filepath.Base(file), func(t *testing.T) {
			t.Parallel()

			var validationError ValidationError
			if _, err := ParseRecord(readFixture(t, file)); !errors.As(err, &validationError) {
				t.Fatalf("ParseRecord(%s) error = %v, want ValidationError", file, err)
			}
		})
	}
}

func TestContractScenarioFixturesShape(t *testing.T) {
	t.Parallel()

	dir := fixturePath("scenarios")
	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatalf("ReadDir(%s) error = %v", dir, err)
	}

	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}
		name := entry.Name()
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			scenarioDir := filepath.Join(dir, name)
			if _, err := ParseRecord(readFixture(t, filepath.Join(scenarioDir, "request.jsonl"))); err != nil {
				t.Fatalf("request fixture error = %v", err)
			}

			exitCode := strings.TrimSpace(string(readFixture(t, filepath.Join(scenarioDir, "exit-code.txt"))))
			if _, err := strconv.Atoi(exitCode); err != nil {
				t.Fatalf("exit-code fixture error = %v", err)
			}

			_ = readFixture(t, filepath.Join(scenarioDir, "stderr.txt"))

			stdout := filepath.Join(scenarioDir, "stdout.jsonl")
			for _, line := range fixtureLines(t, stdout) {
				_, err := ParseRecord(line)
				if name == "invalid-stdout" {
					var validationError ValidationError
					if !errors.As(err, &validationError) {
						t.Fatalf("stdout fixture error = %v, want ValidationError", err)
					}
					continue
				}
				if err != nil {
					t.Fatalf("stdout fixture error = %v", err)
				}
			}
		})
	}
}

func fixtureFiles(t *testing.T, parts ...string) []string {
	t.Helper()

	dir := fixturePath(parts...)
	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatalf("ReadDir(%s) error = %v", dir, err)
	}

	var files []string
	for _, entry := range entries {
		if entry.IsDir() || filepath.Ext(entry.Name()) != ".jsonl" {
			continue
		}
		files = append(files, filepath.Join(dir, entry.Name()))
	}
	if len(files) == 0 {
		t.Fatalf("no fixture files in %s", dir)
	}
	return files
}

func fixturePath(parts ...string) string {
	base := []string{"..", "..", "..", "testdata", "analyzer-protocol"}
	return filepath.Join(append(base, parts...)...)
}

func fixtureLines(t *testing.T, file string) [][]byte {
	t.Helper()

	content := readFixture(t, file)
	text := strings.TrimSuffix(string(content), "\n")
	lines := strings.Split(text, "\n")
	result := make([][]byte, 0, len(lines))
	for _, line := range lines {
		if line == "" {
			continue
		}
		result = append(result, []byte(line))
	}
	return result
}

func readFixture(t *testing.T, file string) []byte {
	t.Helper()

	content, err := os.ReadFile(file)
	if err != nil {
		t.Fatalf("ReadFile(%s) error = %v", file, err)
	}
	return content
}
