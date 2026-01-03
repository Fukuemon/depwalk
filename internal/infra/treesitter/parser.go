// Package treesitter implements the Parser interface using tree-sitter for fast AST traversal.
package treesitter

import (
	"context"

	"github.com/Fukuemon/depwalk/internal/model"
)

// Parser implements ports.Parser using tree-sitter.
type Parser struct {
	// TODO: Add tree-sitter configuration (language grammar, etc.)
}

// NewParser creates a new tree-sitter based parser.
func NewParser() *Parser {
	return &Parser{}
}

// FindEnclosingMethod finds the enclosing method_declaration for a given position.
func (p *Parser) FindEnclosingMethod(ctx context.Context, file string, line, col int) (model.DeclRange, error) {
	// TODO: implement using tree-sitter Java grammar
	// 1. Parse the file
	// 2. Find node at position (line, col)
	// 3. Walk up to find method_declaration
	return model.DeclRange{}, nil
}

// FindMethodCandidatesByName lists method_declaration candidates by simple name in a file.
func (p *Parser) FindMethodCandidatesByName(ctx context.Context, file string, methodName string) ([]model.DeclRange, error) {
	// TODO: implement using tree-sitter query
	// Query: (method_declaration name: (identifier) @name (#eq? @name "methodName"))
	return nil, nil
}

// ListCallsInMethod lists call nodes within a method declaration range.
func (p *Parser) ListCallsInMethod(ctx context.Context, decl model.DeclRange) ([]model.CallSite, error) {
	// TODO: implement using tree-sitter query
	// Query: (method_invocation) and (object_creation_expression)
	return nil, nil
}

