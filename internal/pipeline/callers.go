package pipeline

import (
	"context"
	"fmt"

	"github.com/Fukuemon/depwalk/internal/infra/output"
	"github.com/Fukuemon/depwalk/internal/model"
)

// CallersPipeline explores incoming calls to a target method.
type CallersPipeline struct {
	deps     Dependencies
	cfg      Config
	renderer *output.Renderer
}

// NewCallersPipeline creates a new callers exploration pipeline.
func NewCallersPipeline(deps Dependencies, cfg Config) *CallersPipeline {
	return &CallersPipeline{
		deps:     deps,
		cfg:      cfg,
		renderer: output.NewRenderer(),
	}
}

// Run executes the callers pipeline.
//
// Flow:
//  1. Parse selector → DeclRange
//  2. Resolve DeclRange → MethodID (target)
//  3. For depth iterations:
//     a. Use Index to find candidate call sites by method name
//     b. Resolve candidates to confirm they call target
//     c. Add confirmed callers to queue
//  4. Render graph
func (p *CallersPipeline) Run(ctx context.Context, selectorRaw string) (string, error) {
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

	// Stage 3: Resolve target method
	target, err := p.resolveWithCache(ctx, decl)
	if err != nil {
		return "", fmt.Errorf("failed to resolve target method: %w", err)
	}
	if target == "" || target == model.Unresolved {
		return "", &model.SelectorError{
			Kind:     model.SelectorErrorUnresolvable,
			Selector: selectorRaw,
			Message:  "could not resolve method",
		}
	}

	// Stage 4: Build index if needed
	if p.deps.Index != nil {
		if err := p.deps.Index.Build(ctx); err != nil {
			return "", fmt.Errorf("failed to build index: %w", err)
		}
	}

	// Stage 5: Traverse callers
	graph, err := p.traverse(ctx, target, sel.MethodName)
	if err != nil {
		return "", err
	}

	// Stage 6: Render output
	return p.renderer.Render(ctx, target, graph, p.cfg.Format, output.DirectionCallers)
}

func (p *CallersPipeline) findDeclaration(ctx context.Context, sel model.Selector) (model.DeclRange, error) {
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
			return model.DeclRange{}, &model.SelectorError{
				Kind:     model.SelectorErrorAmbiguous,
				Selector: sel.Raw,
				Message:  fmt.Sprintf("multiple methods named '%s' found", sel.MethodName),
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

func (p *CallersPipeline) resolveWithCache(ctx context.Context, decl model.DeclRange) (model.MethodID, error) {
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

func (p *CallersPipeline) traverse(ctx context.Context, target model.MethodID, methodName string) (*model.Graph, error) {
	if p.deps.Index == nil {
		return nil, fmt.Errorf("index not configured (required for callers)")
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

		// Find candidate call sites that might call this method
		candidates, err := p.deps.Index.LookupCallSites(ctx, item.methodName)
		if err != nil {
			continue
		}

		if len(candidates) == 0 {
			continue
		}

		// Resolve candidates to confirm they actually call the target
		resolved, err := p.deps.Resolver.ResolveCalls(ctx, candidates)
		if err != nil {
			continue
		}

		// Filter to only those that call our target
		for _, rc := range resolved {
			if rc.CalleeMethodID != item.methodID {
				continue
			}

			if rc.CallerMethodID == "" || rc.CallerMethodID == model.Unresolved {
				continue
			}

			graph.AddEdge(rc.CallerMethodID, item.methodID)

			if !visited[rc.CallerMethodID] {
				// Extract method name from caller for next iteration
				// TODO: Better method name extraction from MethodID
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

// extractMethodName extracts the simple method name from a MethodID.
// MethodID format: com.example.Foo#bar(int,String) → bar
func extractMethodName(id model.MethodID) string {
	s := string(id)
	hashIdx := -1
	parenIdx := -1
	for i, c := range s {
		if c == '#' {
			hashIdx = i
		} else if c == '(' {
			parenIdx = i
			break
		}
	}
	if hashIdx >= 0 && parenIdx > hashIdx {
		return s[hashIdx+1 : parenIdx]
	}
	return ""
}

