package pipeline

import (
	"context"
	"fmt"

	"github.com/Fukuemon/depwalk/internal/infra/output"
	"github.com/Fukuemon/depwalk/internal/model"
)

// BothPipeline explores both callees and callers from a starting method.
type BothPipeline struct {
	deps     Dependencies
	cfg      Config
	renderer *output.Renderer
}

// NewBothPipeline creates a new pipeline that explores both directions.
func NewBothPipeline(deps Dependencies, cfg Config) *BothPipeline {
	return &BothPipeline{
		deps:     deps,
		cfg:      cfg,
		renderer: output.NewRenderer(),
	}
}

// Run executes the both pipeline.
//
// Flow:
//  1. Parse selector → DeclRange
//  2. Resolve DeclRange → MethodID (root)
//  3. Run callees traversal
//  4. Build index for callers (if not done)
//  5. Run callers traversal
//  6. Render combined graph
func (p *BothPipeline) Run(ctx context.Context, selectorRaw string) (string, error) {
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
	calleesGraph, err := p.traverseCallees(ctx, root, decl)
	if err != nil {
		return "", fmt.Errorf("failed to traverse callees: %w", err)
	}

	// Stage 5: Build index for callers if available
	if p.deps.Index != nil {
		if err := p.deps.Index.Build(ctx); err != nil {
			return "", fmt.Errorf("failed to build index: %w", err)
		}
	}

	// Stage 6: Traverse callers
	callersGraph, err := p.traverseCallers(ctx, root, sel.MethodName)
	if err != nil {
		return "", fmt.Errorf("failed to traverse callers: %w", err)
	}

	// Stage 7: Render combined output
	return p.renderer.RenderBoth(ctx, root, calleesGraph, callersGraph, p.cfg.Format)
}

func (p *BothPipeline) findDeclaration(ctx context.Context, sel model.Selector) (model.DeclRange, error) {
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

func (p *BothPipeline) resolveWithCache(ctx context.Context, decl model.DeclRange) (model.MethodID, error) {
	if p.deps.Cache != nil && !p.cfg.NoCache {
		if id, ok, err := p.deps.Cache.GetResolvedDecl(ctx, decl); err == nil && ok {
			return id, nil
		}
	}

	if p.deps.Resolver == nil {
		return "", fmt.Errorf("resolver not configured")
	}

	id, err := p.deps.Resolver.ResolveDecl(ctx, decl)
	if err != nil {
		return "", err
	}

	if p.deps.Cache != nil && !p.cfg.NoCache {
		_ = p.deps.Cache.PutResolvedDecl(ctx, decl, id)
	}

	return id, nil
}

func (p *BothPipeline) traverseCallees(ctx context.Context, root model.MethodID, rootDecl model.DeclRange) (*model.Graph, error) {
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

		if p.cfg.MaxNodes > 0 && len(graph.Nodes) >= p.cfg.MaxNodes {
			break
		}

		calls, err := p.deps.Parser.ListCallsInMethod(ctx, item.decl)
		if err != nil {
			continue
		}

		if len(calls) == 0 {
			continue
		}

		resolved, err := p.deps.Resolver.ResolveCalls(ctx, calls)
		if err != nil {
			continue
		}

		for _, rc := range resolved {
			if rc.CalleeMethodID == "" || rc.CalleeMethodID == model.Unresolved {
				continue
			}

			graph.AddEdge(item.methodID, rc.CalleeMethodID)

			if !visited[rc.CalleeMethodID] {
				queue = append(queue, queueItem{
					methodID: rc.CalleeMethodID,
					decl:     model.DeclRange{},
					depth:    item.depth + 1,
				})
			}
		}
	}

	return graph, nil
}

func (p *BothPipeline) traverseCallers(ctx context.Context, target model.MethodID, methodName string) (*model.Graph, error) {
	if p.deps.Index == nil {
		// Return empty graph if index not available
		return model.NewGraph(), nil
	}

	graph := model.NewGraph()
	graph.AddNode(target)

	type queueItem struct {
		methodID   model.MethodID
		methodName string
		depth      int
	}

	visited := make(map[model.MethodID]bool)
	queue := []queueItem{{methodID: target, methodName: methodName, depth: 0}}

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

		if p.cfg.MaxNodes > 0 && len(graph.Nodes) >= p.cfg.MaxNodes {
			break
		}

		candidates, err := p.deps.Index.LookupCallSites(ctx, item.methodName)
		if err != nil {
			continue
		}

		if len(candidates) == 0 {
			continue
		}

		resolved, err := p.deps.Resolver.ResolveCalls(ctx, candidates)
		if err != nil {
			continue
		}

		for _, rc := range resolved {
			if rc.CalleeMethodID != item.methodID {
				continue
			}

			if rc.CallerMethodID == "" || rc.CallerMethodID == model.Unresolved {
				continue
			}

			graph.AddEdge(rc.CallerMethodID, item.methodID)

			if !visited[rc.CallerMethodID] {
				queue = append(queue, queueItem{
					methodID:   rc.CallerMethodID,
					methodName: extractMethodName(rc.CallerMethodID),
					depth:      item.depth + 1,
				})
			}
		}
	}

	return graph, nil
}

