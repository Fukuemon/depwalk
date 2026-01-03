// Package index implements the Index interface for reverse lookup of callers.
package index

import (
	"context"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"sync"

	sitter "github.com/smacker/go-tree-sitter"
	"github.com/smacker/go-tree-sitter/java"

	"github.com/Fukuemon/depwalk/internal/model"
)

// CallNameIndex provides reverse lookup from method name to call sites.
// It scans all Java files in the project and indexes method invocations.
type CallNameIndex struct {
	sourceRoots  []string
	includeTests bool
	lang         *sitter.Language

	mu    sync.RWMutex
	index map[string][]model.CallSite // methodName -> []CallSite
	built bool
}

// NewCallNameIndex creates a new call name index.
func NewCallNameIndex(sourceRoots []string, includeTests bool) *CallNameIndex {
	return &CallNameIndex{
		sourceRoots:  sourceRoots,
		includeTests: includeTests,
		lang:         java.GetLanguage(),
		index:        make(map[string][]model.CallSite),
	}
}

// Build builds the index by scanning all source files.
func (idx *CallNameIndex) Build(ctx context.Context) error {
	idx.mu.Lock()
	defer idx.mu.Unlock()

	// Reset index
	idx.index = make(map[string][]model.CallSite)

	// Scan all source roots
	for _, root := range idx.sourceRoots {
		if err := idx.scanDirectory(ctx, root); err != nil {
			return err
		}
	}

	idx.built = true
	return nil
}

// LookupCallSites returns call sites that may call a method with the given name.
func (idx *CallNameIndex) LookupCallSites(ctx context.Context, calleeName string) ([]model.CallSite, error) {
	idx.mu.RLock()
	defer idx.mu.RUnlock()

	if !idx.built {
		return nil, nil
	}

	return idx.index[calleeName], nil
}

// scanDirectory recursively scans a directory for Java files.
func (idx *CallNameIndex) scanDirectory(ctx context.Context, root string) error {
	return filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return nil // Skip errors
		}

		// Check context cancellation
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		// Skip directories
		if d.IsDir() {
			name := d.Name()
			// Skip common non-source directories
			if name == "build" || name == "target" || name == ".gradle" || name == "out" || name == ".git" {
				return filepath.SkipDir
			}
			return nil
		}

		// Only process .java files
		if !strings.HasSuffix(path, ".java") {
			return nil
		}

		// Index the file
		return idx.indexFile(ctx, path)
	})
}

// indexFile indexes all method calls in a single Java file.
func (idx *CallNameIndex) indexFile(ctx context.Context, path string) error {
	source, err := os.ReadFile(path)
	if err != nil {
		return nil // Skip unreadable files
	}

	parser := sitter.NewParser()
	parser.SetLanguage(idx.lang)

	tree, err := parser.ParseCtx(ctx, nil, source)
	if err != nil {
		return nil // Skip unparseable files
	}
	defer tree.Close()

	// Find all method declarations to get enclosing method ranges
	methodRanges := idx.findMethodRanges(tree.RootNode())

	// Query for method invocations and constructor calls
	queryStr := `
		(method_invocation
			name: (identifier) @method_name
			arguments: (argument_list) @args) @call
		(object_creation_expression
			type: (_) @type_name
			arguments: (argument_list) @args) @constructor
	`

	query, err := sitter.NewQuery([]byte(queryStr), idx.lang)
	if err != nil {
		return nil
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
			argsCount = int(argsNode.NamedChildCount())
		}

		// Get callee name
		calleeName := nameNode.Content(source)
		if callNode.Type() == "object_creation_expression" {
			// For constructor calls, extract simple class name
			parts := strings.Split(calleeName, ".")
			calleeName = parts[len(parts)-1]
		}

		// Find enclosing method
		enclosingDecl := idx.findEnclosingMethod(callNode, methodRanges)
		enclosingDecl.File = path // Set the file path

		// Extract receiver text for method calls
		receiverText := ""
		if callNode.Type() == "method_invocation" {
			receiverText = idx.extractReceiver(callNode, source)
		}

		callSite := model.CallSite{
			File:                     path,
			StartByte:                callNode.StartByte(),
			EndByte:                  callNode.EndByte(),
			EnclosingMethodDeclRange: enclosingDecl,
			CalleeName:               calleeName,
			ArgsCount:                argsCount,
			ReceiverText:             receiverText,
		}

		idx.index[calleeName] = append(idx.index[calleeName], callSite)
	}

	return nil
}

// findMethodRanges finds all method/constructor declarations in the tree.
func (idx *CallNameIndex) findMethodRanges(root *sitter.Node) []model.DeclRange {
	var ranges []model.DeclRange
	idx.collectMethodRanges(root, &ranges)
	return ranges
}

func (idx *CallNameIndex) collectMethodRanges(node *sitter.Node, ranges *[]model.DeclRange) {
	nodeType := node.Type()
	if nodeType == "method_declaration" || nodeType == "constructor_declaration" {
		*ranges = append(*ranges, model.DeclRange{
			File:      "", // Will be set by caller
			StartByte: node.StartByte(),
			EndByte:   node.EndByte(),
		})
	}

	for i := 0; i < int(node.ChildCount()); i++ {
		child := node.Child(int(i))
		if child != nil {
			idx.collectMethodRanges(child, ranges)
		}
	}
}

// findEnclosingMethod finds the method declaration that contains the given node.
func (idx *CallNameIndex) findEnclosingMethod(node *sitter.Node, methodRanges []model.DeclRange) model.DeclRange {
	nodeStart := node.StartByte()
	nodeEnd := node.EndByte()

	// Find the smallest method range that contains this node
	var best model.DeclRange
	bestSize := uint32(0xFFFFFFFF)

	for _, r := range methodRanges {
		if r.StartByte <= nodeStart && r.EndByte >= nodeEnd {
			size := r.EndByte - r.StartByte
			if size < bestSize {
				best = r
				bestSize = size
			}
		}
	}

	return best
}

// extractReceiver extracts the receiver text from a method invocation.
func (idx *CallNameIndex) extractReceiver(callNode *sitter.Node, source []byte) string {
	for i := 0; i < int(callNode.ChildCount()); i++ {
		child := callNode.Child(int(i))
		if child != nil && child.Type() == "." {
			if i > 0 {
				prevChild := callNode.Child(int(i - 1))
				if prevChild != nil {
					return prevChild.Content(source)
				}
			}
			break
		}
	}
	return ""
}
