package execx

import (
	"bytes"
	"context"
	"os/exec"
)

// Result holds the output of a command execution.
type Result struct {
	Stdout []byte
	Stderr []byte
}

// Run executes a command and returns its output.
func Run(ctx context.Context, name string, args ...string) (Result, error) {
	cmd := exec.CommandContext(ctx, name, args...)
	var out bytes.Buffer
	var errb bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = &errb
	err := cmd.Run()
	return Result{Stdout: out.Bytes(), Stderr: errb.Bytes()}, err
}

