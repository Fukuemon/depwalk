// Package pipeline provides the core pipeline abstraction for depwalk.
//
// The pipeline processes data through stages:
//
//	Selector → Parse → Resolve → Traverse → Render
//
// Each stage is independent and testable. The pipeline orchestrates
// the flow and handles errors.
package pipeline

import (
	"context"

	"github.com/Fukuemon/depwalk/internal/model"
)

// Stage is a generic interface for pipeline stages.
// Each stage transforms input of type In to output of type Out.
type Stage[In, Out any] interface {
	Process(ctx context.Context, in In) (Out, error)
}

// --- Stage-specific interfaces ---

// Parser extracts candidate declarations and call sites (fast, best-effort).
// Implementations use tree-sitter for quick AST traversal.
type Parser interface {
	// FindEnclosingMethod finds the enclosing method_declaration for a given position.
	FindEnclosingMethod(ctx context.Context, file string, line, col int) (model.DeclRange, error)

	// FindMethodCandidatesByName lists method_declaration candidates by simple name in a file.
	FindMethodCandidatesByName(ctx context.Context, file string, methodName string) ([]model.DeclRange, error)

	// ListCallsInMethod lists call nodes within a method declaration range.
	ListCallsInMethod(ctx context.Context, decl model.DeclRange) ([]model.CallSite, error)
}

// Resolver resolves declaration/call ranges to stable MethodIDs (strict).
// Implementations call the Java helper (JavaParser + SymbolSolver).
type Resolver interface {
	// ResolveDecl resolves a declaration range to a stable MethodID.
	ResolveDecl(ctx context.Context, decl model.DeclRange) (model.MethodID, error)

	// ResolveCalls resolves multiple call sites to their target MethodIDs.
	ResolveCalls(ctx context.Context, calls []model.CallSite) ([]model.ResolvedCall, error)
}

// Index provides reverse lookup for callers via callee simple name.
// Implementations can be cached/persisted.
type Index interface {
	// Build builds the index by scanning source files.
	Build(ctx context.Context) error

	// LookupCallSites returns call sites that may call a method with the given name.
	LookupCallSites(ctx context.Context, calleeName string) ([]model.CallSite, error)
}

// Cache is an optional persistence layer for resolution results.
type Cache interface {
	GetResolvedDecl(ctx context.Context, key model.DeclRange) (model.MethodID, bool, error)
	PutResolvedDecl(ctx context.Context, key model.DeclRange, val model.MethodID) error
}

// ClasspathProvider extracts classpath from build systems.
type ClasspathProvider interface {
	GetClasspath(ctx context.Context, projectRoot string) (string, error)
}

