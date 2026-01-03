// Package gradle implements ClasspathProvider by invoking Gradle to extract classpath.
package gradle

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/Fukuemon/depwalk/pkg/execx"
)

// ClasspathProvider extracts classpath from Gradle projects.
type ClasspathProvider struct {
	// TODO: Add configuration options (module name, etc.)
}

// NewClasspathProvider creates a new Gradle classpath provider.
func NewClasspathProvider() *ClasspathProvider {
	return &ClasspathProvider{}
}

// GetClasspath extracts the runtime classpath from a Gradle project.
func (p *ClasspathProvider) GetClasspath(ctx context.Context, projectRoot string) (string, error) {
	// Check for gradlew
	gradlew := filepath.Join(projectRoot, "gradlew")
	if _, err := os.Stat(gradlew); os.IsNotExist(err) {
		return "", fmt.Errorf("gradlew not found in %s", projectRoot)
	}

	// Generate init script for classpath extraction
	initScript := `
allprojects {
    task depwalkClasspath {
        doLast {
            println "DEPWALK_CLASSPATH_START"
            println sourceSets.main.runtimeClasspath.asPath
            println "DEPWALK_CLASSPATH_END"
        }
    }
}
`
	tmpFile, err := os.CreateTemp("", "depwalk-init-*.gradle")
	if err != nil {
		return "", fmt.Errorf("failed to create init script: %w", err)
	}
	defer os.Remove(tmpFile.Name())

	if _, err := tmpFile.WriteString(initScript); err != nil {
		return "", fmt.Errorf("failed to write init script: %w", err)
	}
	tmpFile.Close()

	// Run gradlew with init script
	result, err := execx.Run(ctx, gradlew, "-q", "--init-script", tmpFile.Name(), "depwalkClasspath")
	if err != nil {
		return "", fmt.Errorf("gradle failed: %w\nstderr: %s", err, string(result.Stderr))
	}

	// Parse output
	output := string(result.Stdout)
	startIdx := strings.Index(output, "DEPWALK_CLASSPATH_START")
	endIdx := strings.Index(output, "DEPWALK_CLASSPATH_END")
	if startIdx == -1 || endIdx == -1 || endIdx <= startIdx {
		return "", fmt.Errorf("failed to parse classpath from gradle output")
	}

	classpath := strings.TrimSpace(output[startIdx+len("DEPWALK_CLASSPATH_START") : endIdx])
	return classpath, nil
}

