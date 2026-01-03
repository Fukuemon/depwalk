// Package javahelper implements the Resolver interface by managing a long-lived Java helper process.
package javahelper

import (
	"context"

	"github.com/Fukuemon/depwalk/internal/model"
)

// Resolver implements ports.Resolver using JavaParser + SymbolSolver via a helper process.
type Resolver struct {
	classpath string
	// TODO: Add process handle, stdin/stdout pipes
}

// NewResolver creates a new Java helper resolver.
func NewResolver(classpath string) *Resolver {
	return &Resolver{classpath: classpath}
}

// Start starts the Java helper process.
func (r *Resolver) Start(ctx context.Context) error {
	// TODO: Start java -jar depwalk-helper.jar with classpath
	return nil
}

// Stop stops the Java helper process.
func (r *Resolver) Stop() error {
	// TODO: Graceful shutdown
	return nil
}

// ResolveDecl resolves a declaration range to a stable MethodID.
func (r *Resolver) ResolveDecl(ctx context.Context, decl model.DeclRange) (model.MethodID, error) {
	// TODO: Send resolveDecl request to Java helper
	// Request: {"op": "resolveDecl", "file": "...", "startByte": ..., "endByte": ...}
	return "", nil
}

// ResolveCalls resolves multiple call sites to their target MethodIDs.
func (r *Resolver) ResolveCalls(ctx context.Context, calls []model.CallSite) ([]model.ResolvedCall, error) {
	// TODO: Send resolveCalls batch request to Java helper
	// Request: {"op": "resolveCalls", "calls": [...]}
	return nil, nil
}

