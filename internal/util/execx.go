package util

import (
	"bytes"
	"context"
	"os/exec"
)

type ExecResult struct {
	Stdout []byte
	Stderr []byte
}

func Run(ctx context.Context, name string, args ...string) (ExecResult, error) {
	cmd := exec.CommandContext(ctx, name, args...)
	var out bytes.Buffer
	var errb bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = &errb
	err := cmd.Run()
	return ExecResult{Stdout: out.Bytes(), Stderr: errb.Bytes()}, err
}

