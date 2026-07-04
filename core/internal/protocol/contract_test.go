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
			t.Run("request is a valid analysisRequest", func(t *testing.T) {
				t.Parallel()

				validateScenarioRequestFixture(t, scenarioDir)
			})
			t.Run("exit-code is an integer", func(t *testing.T) {
				t.Parallel()

				validateScenarioExitCodeFixture(t, scenarioDir)
			})
			t.Run("stderr fixture is readable", func(t *testing.T) {
				t.Parallel()

				_ = readFixture(t, filepath.Join(scenarioDir, "stderr.txt"))
			})
			t.Run("stdout records match scenario validity", func(t *testing.T) {
				t.Parallel()

				validateScenarioStdoutFixture(t, scenarioDir, name == "invalid-stdout")
			})
		})
	}
}

func validateScenarioRequestFixture(t *testing.T, scenarioDir string) {
	t.Helper()

	record, err := ParseRecord(readFixture(t, filepath.Join(scenarioDir, "request.jsonl")))
	if err != nil {
		t.Fatalf("request fixture error = %v", err)
	}
	if _, ok := record.(AnalysisRequest); !ok {
		t.Fatalf("request fixture type = %T, want AnalysisRequest", record)
	}
}

func validateScenarioExitCodeFixture(t *testing.T, scenarioDir string) {
	t.Helper()

	exitCode := strings.TrimSpace(string(readFixture(t, filepath.Join(scenarioDir, "exit-code.txt"))))
	if _, err := strconv.Atoi(exitCode); err != nil {
		t.Fatalf("exit-code fixture error = %v", err)
	}
}

func validateScenarioStdoutFixture(t *testing.T, scenarioDir string, wantValidationError bool) {
	t.Helper()

	for _, line := range fixtureLines(t, filepath.Join(scenarioDir, "stdout.jsonl")) {
		_, err := ParseRecord(line)
		if wantValidationError {
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
