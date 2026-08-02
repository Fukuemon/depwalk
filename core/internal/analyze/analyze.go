package analyze

import (
	"errors"
	"fmt"
	"slices"
	"strings"

	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/traversal"
)

// Options は depwalk analyze 1 回の設定。
type Options struct {
	WorkspaceRoot string
	// SourceRoots は --source-root の値を指定順のまま持つ。Core は build system も
	// package 階層も解釈せず analysisRequest.sourceRoots へ素通しする。
	// 空なら flag 未指定で、request の field 自体を省く。
	SourceRoots []string
	// Language は解釈せず analysisRequest.language へ素通しする。
	Language string
	// AnalyzerMeta は --analyzer-meta の key=value を指定順のまま持つ。
	AnalyzerMeta []string
	// Include / Exclude は workspace 相対の glob を指定順のまま持つ。
	// 空なら request の該当 field を省く。
	Include []string
	Exclude []string
	// Method は探索の起点 node を選ぶ。空なら件数サマリだけを返す動作になり、
	// 以降の query field は無視する。
	Method    string
	Direction graph.Direction
	MaxDepth  *int
}

// Result は depwalk analyze が成功したときの結果。
type Result struct {
	// Graph は Analyzer の methodSymbol と
	// callEdge records.
	Graph         *graph.Graph
	Diagnostics   []Diagnostic
	MethodCount   int
	CallEdgeCount int
	// MethodQuery は method query の探索結果。Options.Method が空なら nil。
	MethodQuery *MethodQuery
}

// MethodQuery は method query の探索要求と結果。
// output の formatter が両方を使うため、組にして渡す。
type MethodQuery struct {
	Request traversal.Request
	Result  traversal.Result
}

// Runner は注入された [Source] に対して analyze の use case を実行する。
//
// interface ではなく struct として公開する。抽象が要る呼び出し側が自分で
// interface を定義すればよく、提供側が先回りして抽象を作ると使われない抽象が残る。
type Runner struct {
	source Source
}

func New(source Source) Runner {
	return Runner{source: source}
}

// Run は port 経由で Analyzer を実行し、その record から呼び出しグラフを構築する。
//
// error を返すのは、要求を組めない / 検証に通らない、Analyzer の stdout が
// protocol 検証に失敗する、Analyzer が致命的な失敗 ("error" record または非ゼロ
// exit) を報告した場合。いずれも呼び出し側が非ゼロ exit として伝播すべき失敗である。
func (r Runner) Run(opts Options) (Result, error) {
	if r.source == nil {
		return Result{}, errors.New("analyze: a Source is required")
	}

	metadata, err := BuildMetadata(opts.AnalyzerMeta)
	if err != nil {
		return Result{}, err
	}

	request := Request{
		WorkspaceRoot: opts.WorkspaceRoot,
		SourceRoots:   opts.SourceRoots,
		Language:      opts.Language,
		Include:       opts.Include,
		Exclude:       opts.Exclude,
		Metadata:      metadata,
	}

	// stagingGraph receives domain values one at a time as the port streams
	// them; it stays private request state until the run is confirmed
	// successful and is discarded (never published) on any fatal outcome,
	// keeping the request atomic.
	stagingGraph := graph.New()
	methodCount, callEdgeCount := 0, 0
	outcome, err := r.source.Run(request,
		func(node graph.Node) {
			stagingGraph.AddNode(node)
			methodCount++
		},
		func(edge graph.Edge) {
			stagingGraph.AddEdge(edge)
			callEdgeCount++
		},
	)
	if err != nil {
		return Result{}, err
	}
	if err := outcome.Err(); err != nil {
		return Result{}, err
	}

	result := Result{
		Graph:         stagingGraph,
		Diagnostics:   outcome.Diagnostics,
		MethodCount:   methodCount,
		CallEdgeCount: callEdgeCount,
	}
	if opts.Method == "" {
		return result, nil
	}

	start, err := selectMethod(stagingGraph, opts.Method)
	if err != nil {
		return Result{}, err
	}
	requestForTraversal := traversal.Request{
		StartID:   start.ID,
		Direction: opts.Direction,
		MaxDepth:  opts.MaxDepth,
	}
	traversalResult, err := traversal.Traverse(stagingGraph, requestForTraversal)
	if err != nil {
		return Result{}, fmt.Errorf("traverse method %q: %w", opts.Method, err)
	}
	result.MethodQuery = &MethodQuery{Request: requestForTraversal, Result: traversalResult}
	return result, nil
}

func selectMethod(g *graph.Graph, selector string) (graph.Node, error) {
	nodes := g.Nodes()
	matches := make([]graph.Node, 0, 1)
	if strings.Contains(selector, "(") {
		for _, node := range nodes {
			if node.Symbol.Signature == selector {
				matches = append(matches, node)
			}
		}
	} else {
		for _, node := range nodes {
			signatureName, _, hasArgumentList := strings.Cut(node.Symbol.Signature, "(")
			if hasArgumentList && signatureName == selector {
				matches = append(matches, node)
			}
		}
	}

	if len(matches) == 1 {
		return matches[0], nil
	}
	if len(matches) == 0 {
		return graph.Node{}, &InputError{Err: fmt.Errorf("method selector %q did not match any method", selector)}
	}
	candidates := make([]string, len(matches))
	for i, node := range matches {
		candidates[i] = node.Symbol.Signature
	}
	slices.Sort(candidates)
	return graph.Node{}, &InputError{Err: fmt.Errorf(
		"method selector %q is ambiguous; candidates: %s",
		selector,
		strings.Join(candidates, ", "),
	)}
}
