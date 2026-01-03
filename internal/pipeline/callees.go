package pipeline

import (
	"context"
	"fmt"
	"os"

	"github.com/Fukuemon/depwalk/internal/infra/output"
	"github.com/Fukuemon/depwalk/internal/model"
)

// CalleesPipeline explores outgoing calls from a starting method.
type CalleesPipeline struct {
	deps     Dependencies
	cfg      Config
	renderer *output.Renderer
}

// NewCalleesPipeline creates a new callees exploration pipeline.
func NewCalleesPipeline(deps Dependencies, cfg Config) *CalleesPipeline {
	return &CalleesPipeline{
		deps:     deps,
		cfg:      cfg,
		renderer: output.NewRenderer(),
	}
}

// Run executes the callees pipeline.
//
// Flow:
//  1. Parse selector → DeclRange
//  2. Resolve DeclRange → MethodID (root)
//  3. For depth iterations:
//     a. List calls in current methods
//     b. Resolve calls → edges
//     c. Add new targets to queue
//  4. Render graph
func (p *CalleesPipeline) Run(ctx context.Context, selectorRaw string) (string, error) {
	// Stage 1: Parse selector
	sel, err := model.ParseSelector(selectorRaw)
	if err != nil {
		return "", err
	}

	// Stage 2: Find declaration range
	decl, err := p.findDeclaration(ctx, sel)
	if err != nil {
		return "", err
	}

	// Stage 3: Resolve root method
	root, err := p.resolveWithCache(ctx, decl)
	if err != nil {
		return "", fmt.Errorf("failed to resolve root method: %w", err)
	}
	if root == "" || root == model.Unresolved {
		return "", &model.SelectorError{
			Kind:     model.SelectorErrorUnresolvable,
			Selector: selectorRaw,
			Message:  "could not resolve method",
		}
	}

	// Stage 4: Traverse callees
	graph, err := p.traverse(ctx, root, decl)
	if err != nil {
		return "", err
	}

	// Stage 5: Render output
	return p.renderer.Render(ctx, root, graph, p.cfg.Format, output.DirectionCallees)
}

func (p *CalleesPipeline) findDeclaration(ctx context.Context, sel model.Selector) (model.DeclRange, error) {
	if p.deps.Parser == nil {
		return model.DeclRange{}, fmt.Errorf("parser not configured")
	}

	switch sel.Type {
	case model.SelectorTypeFileLine:
		return p.deps.Parser.FindEnclosingMethod(ctx, sel.File, sel.Line, sel.Col)

	case model.SelectorTypeFileHash:
		candidates, err := p.deps.Parser.FindMethodCandidatesByName(ctx, sel.File, sel.MethodName)
		if err != nil {
			return model.DeclRange{}, err
		}
		if len(candidates) == 0 {
			return model.DeclRange{}, &model.SelectorError{
				Kind:     model.SelectorErrorNotFound,
				Selector: sel.Raw,
				Message:  fmt.Sprintf("method '%s' not found in %s", sel.MethodName, sel.File),
			}
		}
		if len(candidates) > 1 {
			// Format candidates for display
			candidateStrs := formatCandidates(sel.File, candidates)
			return model.DeclRange{}, &model.SelectorError{
				Kind:       model.SelectorErrorAmbiguous,
				Selector:   sel.Raw,
				Message:    fmt.Sprintf("multiple methods named '%s' found in %s", sel.MethodName, sel.File),
				Candidates: candidateStrs,
			}
		}
		return candidates[0], nil

	default:
		return model.DeclRange{}, &model.SelectorError{
			Kind:     model.SelectorErrorUnsupported,
			Selector: sel.Raw,
			Message:  fmt.Sprintf("unsupported selector type: %s", sel.Type),
		}
	}
}

func (p *CalleesPipeline) resolveWithCache(ctx context.Context, decl model.DeclRange) (model.MethodID, error) {
	// Try cache first
	if p.deps.Cache != nil && !p.cfg.NoCache {
		if id, ok, err := p.deps.Cache.GetResolvedDecl(ctx, decl); err == nil && ok {
			return id, nil
		}
	}

	// Resolve via Java helper
	if p.deps.Resolver == nil {
		return "", fmt.Errorf("resolver not configured")
	}

	id, err := p.deps.Resolver.ResolveDecl(ctx, decl)
	if err != nil {
		return "", err
	}

	// Store in cache
	if p.deps.Cache != nil && !p.cfg.NoCache {
		_ = p.deps.Cache.PutResolvedDecl(ctx, decl, id)
	}

	return id, nil
}

func (p *CalleesPipeline) traverse(ctx context.Context, root model.MethodID, rootDecl model.DeclRange) (*model.Graph, error) {
	graph := model.NewGraph()
	graph.AddNode(root)

	type queueItem struct {
		methodID model.MethodID
		decl     model.DeclRange
		depth    int
	}

	visited := make(map[model.MethodID]bool)
	queue := []queueItem{{methodID: root, decl: rootDecl, depth: 0}}

	for len(queue) > 0 {
		item := queue[0]
		queue = queue[1:]

		if visited[item.methodID] {
			continue
		}
		visited[item.methodID] = true

		if item.depth >= p.cfg.Depth {
			continue
		}

		// Check max nodes limit
		if p.cfg.MaxNodes > 0 && len(graph.Nodes) >= p.cfg.MaxNodes {
			break
		}

		// List calls in this method
		calls, err := p.deps.Parser.ListCallsInMethod(ctx, item.decl)
		if err != nil {
			continue // Skip on error, but continue traversal
		}

		// Resolve calls
		if len(calls) == 0 {
			continue
		}

		resolved, err := p.deps.Resolver.ResolveCalls(ctx, calls)
		if err != nil {
			continue
		}

		// Add edges and queue new targets
		for _, rc := range resolved {
			if rc.CalleeMethodID == "" || rc.CalleeMethodID == model.Unresolved {
				continue
			}

			graph.AddEdge(item.methodID, rc.CalleeMethodID)

			if !visited[rc.CalleeMethodID] {
				// Note: We need the DeclRange of the callee to continue traversal
				// This is a simplification; in practice, we'd need to resolve callee declarations
				queue = append(queue, queueItem{
					methodID: rc.CalleeMethodID,
					decl:     model.DeclRange{}, // TODO: Resolve callee declaration
					depth:    item.depth + 1,
				})
			}
		}
	}

	return graph, nil
}

// formatCandidates formats DeclRange candidates for display.
// Returns a slice of strings like "line 12" for each candidate.
func formatCandidates(file string, candidates []model.DeclRange) []string {
	// Read file to calculate line numbers from byte offsets
	content, err := os.ReadFile(file)
	if err != nil {
		// Fallback to byte offsets if we can't read the file
		result := make([]string, len(candidates))
		for i, c := range candidates {
			result[i] = fmt.Sprintf("byte %d-%d", c.StartByte, c.EndByte)
		}
		return result
	}

	result := make([]string, len(candidates))
	for i, c := range candidates {
		line := byteOffsetToLine(content, c.StartByte)
		result[i] = fmt.Sprintf("line %d", line)
	}
	return result
}

// byteOffsetToLine converts a byte offset to a 1-based line number.
func byteOffsetToLine(content []byte, offset uint32) int {
	if offset == 0 {
		return 1
	}

	line := 1
	for i := uint32(0); i < offset && int(i) < len(content); i++ {
		if content[i] == '\n' {
			line++
		}
	}
	return line
}
