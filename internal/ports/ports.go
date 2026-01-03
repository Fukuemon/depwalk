package ports

import (
	"context"

	"github.com/Fukuemon/depwalk/internal/domain"
)

// Parser extracts candidate declarations and call sites (fast, best-effort).
// Implementations are expected to use tree-sitter.
type Parser interface {
	// FindEnclosingMethod finds the enclosing method_declaration for a given position.
	FindEnclosingMethod(ctx context.Context, file string, line, col int) (domain.DeclRange, error)

	// FindMethodCandidatesByName lists method_declaration candidates by simple name in a file.
	FindMethodCandidatesByName(ctx context.Context, file string, methodName string) ([]domain.DeclRange, error)

	// ListCallsInMethod lists call nodes within a method declaration range.
	ListCallsInMethod(ctx context.Context, decl domain.DeclRange) ([]domain.CallSite, error)
}

// Resolver resolves declaration/call ranges to stable MethodIDs (strict).
// Implementations are expected to call the Java helper (JavaParser + SymbolSolver).
type Resolver interface {
	ResolveDecl(ctx context.Context, decl domain.DeclRange) (domain.MethodID, error)
	ResolveCalls(ctx context.Context, calls []domain.CallSite) ([]domain.ResolvedCall, error)
}

// Index provides reverse lookup for callers via callee simple name.
// Implementations can be cached/persisted.
type Index interface {
	Build(ctx context.Context) error
	LookupCallSites(ctx context.Context, calleeName string) ([]domain.CallSite, error)
}

type RenderFormat string

const (
	RenderFormatTree    RenderFormat = "tree"
	RenderFormatMermaid RenderFormat = "mermaid"
)

type Direction string

const (
	DirectionCallees Direction = "callees"
	DirectionCallers Direction = "callers"
)

// Renderer renders the result graph into human-friendly text.
type Renderer interface {
	Render(ctx context.Context, root domain.MethodID, g *domain.Graph, format RenderFormat, direction Direction) (string, error)
}

// Cache is an optional persistence layer for indexes and resolve results.
// MVP can run without it (nil cache).
type Cache interface {
	GetResolvedDecl(ctx context.Context, key domain.DeclRange) (domain.MethodID, bool, error)
	PutResolvedDecl(ctx context.Context, key domain.DeclRange, val domain.MethodID) error
}

// Classpath is a platform-appropriate classpath string (separator depends on OS).
type Classpath string

type ClasspathProvider interface {
	GetClasspath(ctx context.Context, projectRoot string) (Classpath, error)
}

