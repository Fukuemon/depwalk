// Package treesitter implements the Parser interface using tree-sitter for fast AST traversal.
package treesitter

import (
	"context"
	"fmt"
	"os"
	"strings"

	sitter "github.com/smacker/go-tree-sitter"
	"github.com/smacker/go-tree-sitter/java"

	"github.com/Fukuemon/depwalk/internal/model"
)

// Parser implements pipeline.Parser using tree-sitter.
type Parser struct {
	lang *sitter.Language
}

// NewParser creates a new tree-sitter based parser.
func NewParser() *Parser {
	return &Parser{
		lang: java.GetLanguage(),
	}
}

// FindEnclosingMethod finds the enclosing method_declaration for a given position.
func (p *Parser) FindEnclosingMethod(ctx context.Context, file string, line, col int) (model.DeclRange, error) {
	source, err := os.ReadFile(file)
	if err != nil {
		return model.DeclRange{}, fmt.Errorf("failed to read file %s: %w", file, err)
	}

	tree, err := p.parse(source)
	if err != nil {
		return model.DeclRange{}, err
	}
	defer tree.Close()

	// tree-sitter uses 0-based line numbers
	row := uint32(line - 1)

	// If col is 0, try to find a method that starts on this line
	if col == 0 {
		if decl := p.findMethodOnLine(tree.RootNode(), row); decl != nil {
			return model.DeclRange{
				File:      file,
				StartByte: decl.StartByte(),
				EndByte:   decl.EndByte(),
			}, nil
		}
	}

	point := sitter.Point{Row: row, Column: uint32(col)}

	// Find the node at the given position
	node := tree.RootNode().NamedDescendantForPointRange(point, point)
	if node == nil {
		return model.DeclRange{}, fmt.Errorf("no node found at %s:%d:%d", file, line, col)
	}

	// Walk up to find method_declaration or constructor_declaration
	for node != nil {
		nodeType := node.Type()
		if nodeType == "method_declaration" || nodeType == "constructor_declaration" {
			return model.DeclRange{
				File:      file,
				StartByte: node.StartByte(),
				EndByte:   node.EndByte(),
			}, nil
		}
		node = node.Parent()
	}

	return model.DeclRange{}, fmt.Errorf("no enclosing method found at %s:%d:%d", file, line, col)
}

// FindMethodCandidatesByName lists method_declaration candidates by simple name in a file.
func (p *Parser) FindMethodCandidatesByName(ctx context.Context, file string, methodName string) ([]model.DeclRange, error) {
	source, err := os.ReadFile(file)
	if err != nil {
		return nil, fmt.Errorf("failed to read file %s: %w", file, err)
	}

	tree, err := p.parse(source)
	if err != nil {
		return nil, err
	}
	defer tree.Close()

	var candidates []model.DeclRange

	// Query for method_declaration and constructor_declaration with matching name
	queryStr := `
		(method_declaration
			name: (identifier) @name)
		(constructor_declaration
			name: (identifier) @name)
	`

	query, err := sitter.NewQuery([]byte(queryStr), p.lang)
	if err != nil {
		return nil, fmt.Errorf("failed to create query: %w", err)
	}
	defer query.Close()

	cursor := sitter.NewQueryCursor()
	defer cursor.Close()

	cursor.Exec(query, tree.RootNode())

	for {
		match, ok := cursor.NextMatch()
		if !ok {
			break
		}

		for _, capture := range match.Captures {
			name := capture.Node.Content(source)
			if name == methodName {
				// Get the parent (method_declaration or constructor_declaration)
				parent := capture.Node.Parent()
				if parent != nil {
					candidates = append(candidates, model.DeclRange{
						File:      file,
						StartByte: parent.StartByte(),
						EndByte:   parent.EndByte(),
					})
				}
			}
		}
	}

	return candidates, nil
}

// ListCallsInMethod lists call nodes within a method declaration range.
func (p *Parser) ListCallsInMethod(ctx context.Context, decl model.DeclRange) ([]model.CallSite, error) {
	source, err := os.ReadFile(decl.File)
	if err != nil {
		return nil, fmt.Errorf("failed to read file %s: %w", decl.File, err)
	}

	tree, err := p.parse(source)
	if err != nil {
		return nil, err
	}
	defer tree.Close()

	// Find the method node by byte range
	methodNode := p.findNodeByRange(tree.RootNode(), decl.StartByte, decl.EndByte)
	if methodNode == nil {
		return nil, fmt.Errorf("method node not found at %s[%d:%d]", decl.File, decl.StartByte, decl.EndByte)
	}

	var calls []model.CallSite

	// Query for method_invocation and object_creation_expression
	queryStr := `
		(method_invocation
			name: (identifier) @method_name
			arguments: (argument_list) @args) @call
		(object_creation_expression
			type: (_) @type_name
			arguments: (argument_list) @args) @constructor
	`

	query, err := sitter.NewQuery([]byte(queryStr), p.lang)
	if err != nil {
		return nil, fmt.Errorf("failed to create query: %w", err)
	}
	defer query.Close()

	cursor := sitter.NewQueryCursor()
	defer cursor.Close()

	cursor.Exec(query, methodNode)

	for {
		match, ok := cursor.NextMatch()
		if !ok {
			break
		}

		var callNode *sitter.Node
		var nameNode *sitter.Node
		var argsNode *sitter.Node

		for _, capture := range match.Captures {
			captureName := query.CaptureNameForId(capture.Index)
			switch captureName {
			case "call", "constructor":
				callNode = capture.Node
			case "method_name", "type_name":
				nameNode = capture.Node
			case "args":
				argsNode = capture.Node
			}
		}

		if callNode == nil || nameNode == nil {
			continue
		}

		// Count arguments
		argsCount := 0
		if argsNode != nil {
			for i := 0; i < int(argsNode.NamedChildCount()); i++ {
				argsCount++
			}
		}

		// Extract receiver text for method_invocation
		receiverText := ""
		if callNode.Type() == "method_invocation" {
			// Check if there's a receiver (object before the dot)
			for i := 0; i < int(callNode.ChildCount()); i++ {
				child := callNode.Child(int(i))
				if child != nil && child.Type() == "." {
					// The node before the dot is the receiver
					if i > 0 {
						prevChild := callNode.Child(int(i - 1))
						if prevChild != nil {
							receiverText = prevChild.Content(source)
						}
					}
					break
				}
			}
		}

		calleeName := nameNode.Content(source)
		// For constructor calls, extract simple class name
		if callNode.Type() == "object_creation_expression" {
			parts := strings.Split(calleeName, ".")
			calleeName = parts[len(parts)-1]
		}

		calls = append(calls, model.CallSite{
			File:                     decl.File,
			StartByte:                callNode.StartByte(),
			EndByte:                  callNode.EndByte(),
			EnclosingMethodDeclRange: decl,
			CalleeName:               calleeName,
			ArgsCount:                argsCount,
			ReceiverText:             receiverText,
		})
	}

	return calls, nil
}

// parse parses the source code and returns the syntax tree.
func (p *Parser) parse(source []byte) (*sitter.Tree, error) {
	parser := sitter.NewParser()
	parser.SetLanguage(p.lang)

	tree, err := parser.ParseCtx(context.Background(), nil, source)
	if err != nil {
		return nil, fmt.Errorf("failed to parse: %w", err)
	}

	return tree, nil
}

// findNodeByRange finds a node with the exact byte range.
func (p *Parser) findNodeByRange(root *sitter.Node, startByte, endByte uint32) *sitter.Node {
	if root.StartByte() == startByte && root.EndByte() == endByte {
		return root
	}

	for i := 0; i < int(root.ChildCount()); i++ {
		child := root.Child(int(i))
		if child == nil {
			continue
		}

		// Check if the target range is within this child
		if child.StartByte() <= startByte && child.EndByte() >= endByte {
			found := p.findNodeByRange(child, startByte, endByte)
			if found != nil {
				return found
			}
		}
	}

	return nil
}

// findMethodOnLine finds a method_declaration or constructor_declaration that starts on the given line.
func (p *Parser) findMethodOnLine(root *sitter.Node, row uint32) *sitter.Node {
	nodeType := root.Type()
	if nodeType == "method_declaration" || nodeType == "constructor_declaration" {
		if root.StartPoint().Row == row {
			return root
		}
	}

	for i := 0; i < int(root.ChildCount()); i++ {
		child := root.Child(int(i))
		if child == nil {
			continue
		}

		// Only search in children that might contain the target line
		if child.StartPoint().Row <= row && child.EndPoint().Row >= row {
			found := p.findMethodOnLine(child, row)
			if found != nil {
				return found
			}
		}
	}

	return nil
}
