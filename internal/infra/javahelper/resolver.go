// Package javahelper implements the Resolver interface by managing a long-lived Java helper process.
package javahelper

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"sync"

	"github.com/Fukuemon/depwalk/internal/model"
)

// Resolver implements pipeline.Resolver using JavaParser + SymbolSolver via a helper process.
type Resolver struct {
	classpath   string
	sourceRoots []string
	jarPath     string

	mu     sync.Mutex
	cmd    *exec.Cmd
	stdin  io.WriteCloser
	stdout *bufio.Reader
}

// NewResolver creates a new Java helper resolver.
func NewResolver(classpath string) *Resolver {
	return &Resolver{
		classpath: classpath,
	}
}

// SetSourceRoots sets the source roots for the resolver.
func (r *Resolver) SetSourceRoots(roots []string) {
	r.sourceRoots = roots
}

// SetJarPath sets the path to the depwalk-helper jar.
func (r *Resolver) SetJarPath(jarPath string) {
	r.jarPath = jarPath
}

// Start starts the Java helper process.
func (r *Resolver) Start(ctx context.Context) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	if r.cmd != nil {
		return nil // Already running
	}

	jarPath := r.jarPath
	if jarPath == "" {
		// Try to find the jar in common locations
		candidates := []string{
			"java/depwalk-helper/build/libs/depwalk-helper-0.1.0-all.jar",
			"build/libs/depwalk-helper-0.1.0-all.jar",
		}
		for _, c := range candidates {
			if _, err := os.Stat(c); err == nil {
				jarPath = c
				break
			}
		}
		if jarPath == "" {
			return fmt.Errorf("depwalk-helper jar not found")
		}
	}

	// Build command arguments
	args := []string{"-jar", jarPath, r.classpath}
	args = append(args, r.sourceRoots...)

	r.cmd = exec.CommandContext(ctx, "java", args...)
	r.cmd.Stderr = os.Stderr

	stdin, err := r.cmd.StdinPipe()
	if err != nil {
		return fmt.Errorf("failed to get stdin pipe: %w", err)
	}
	r.stdin = stdin

	stdout, err := r.cmd.StdoutPipe()
	if err != nil {
		return fmt.Errorf("failed to get stdout pipe: %w", err)
	}
	r.stdout = bufio.NewReader(stdout)

	if err := r.cmd.Start(); err != nil {
		return fmt.Errorf("failed to start Java helper: %w", err)
	}

	return nil
}

// Stop stops the Java helper process.
func (r *Resolver) Stop() error {
	r.mu.Lock()
	defer r.mu.Unlock()

	if r.cmd == nil {
		return nil
	}

	// Send shutdown command
	shutdownReq := map[string]string{"op": "shutdown"}
	data, _ := json.Marshal(shutdownReq)
	_, _ = r.stdin.Write(append(data, '\n'))

	if err := r.cmd.Wait(); err != nil {
		// Process may have already exited, which is fine
		_ = r.cmd.Process.Kill()
	}

	r.cmd = nil
	r.stdin = nil
	r.stdout = nil

	return nil
}

// ResolveDecl resolves a declaration range to a stable MethodID.
func (r *Resolver) ResolveDecl(ctx context.Context, decl model.DeclRange) (model.MethodID, error) {
	r.mu.Lock()
	defer r.mu.Unlock()

	if r.cmd == nil {
		return "", fmt.Errorf("Java helper not started")
	}

	// Convert to absolute path
	absPath, err := filepath.Abs(decl.File)
	if err != nil {
		absPath = decl.File
	}

	req := map[string]interface{}{
		"op":        "resolveDecl",
		"file":      absPath,
		"startByte": decl.StartByte,
		"endByte":   decl.EndByte,
	}

	resp, err := r.sendRequest(req)
	if err != nil {
		return "", err
	}

	if !resp.OK {
		return model.Unresolved, nil
	}

	return model.MethodID(resp.MethodID), nil
}

// ResolveCalls resolves multiple call sites to their target MethodIDs.
func (r *Resolver) ResolveCalls(ctx context.Context, calls []model.CallSite) ([]model.ResolvedCall, error) {
	r.mu.Lock()
	defer r.mu.Unlock()

	if r.cmd == nil {
		return nil, fmt.Errorf("Java helper not started")
	}

	// Build calls array
	callsData := make([]map[string]interface{}, len(calls))
	for i, c := range calls {
		absPath, err := filepath.Abs(c.File)
		if err != nil {
			absPath = c.File
		}

		enclosingAbsPath, err := filepath.Abs(c.EnclosingMethodDeclRange.File)
		if err != nil {
			enclosingAbsPath = c.EnclosingMethodDeclRange.File
		}

		callsData[i] = map[string]interface{}{
			"file":      absPath,
			"startByte": c.StartByte,
			"endByte":   c.EndByte,
			"enclosingMethodDeclRange": map[string]interface{}{
				"file":      enclosingAbsPath,
				"startByte": c.EnclosingMethodDeclRange.StartByte,
				"endByte":   c.EnclosingMethodDeclRange.EndByte,
			},
		}
	}

	req := map[string]interface{}{
		"op":    "resolveCalls",
		"calls": callsData,
	}

	resp, err := r.sendRequest(req)
	if err != nil {
		return nil, err
	}

	if !resp.OK {
		return nil, fmt.Errorf("resolveCalls failed: %s", resp.Error)
	}

	results := make([]model.ResolvedCall, len(resp.Results))
	for i, res := range resp.Results {
		results[i] = model.ResolvedCall{
			CallSite:       calls[i],
			CalleeMethodID: model.MethodID(res.CalleeMethodID),
			CallerMethodID: model.MethodID(res.CallerMethodID),
		}
	}

	return results, nil
}

type response struct {
	OK       bool            `json:"ok"`
	Error    string          `json:"error,omitempty"`
	MethodID string          `json:"methodId,omitempty"`
	Results  []resolveResult `json:"results,omitempty"`
}

type resolveResult struct {
	File           string `json:"file"`
	StartByte      uint32 `json:"startByte"`
	EndByte        uint32 `json:"endByte"`
	CalleeMethodID string `json:"calleeMethodId"`
	CallerMethodID string `json:"callerMethodId"`
}

func (r *Resolver) sendRequest(req map[string]interface{}) (*response, error) {
	data, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	if _, err := r.stdin.Write(append(data, '\n')); err != nil {
		return nil, fmt.Errorf("failed to write request: %w", err)
	}

	line, err := r.stdout.ReadString('\n')
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	var resp response
	if err := json.Unmarshal([]byte(line), &resp); err != nil {
		return nil, fmt.Errorf("failed to unmarshal response: %w", err)
	}

	return &resp, nil
}
