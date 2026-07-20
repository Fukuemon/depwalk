package output

import (
	"slices"

	"github.com/Fukuemon/depwalk/core/internal/graph"
)

func buildView(in Input) View {
	view := View{
		Status:    in.Result.Status,
		Direction: in.Request.Direction,
		Start:     nodeView(in.Graph, in.Request.StartID, in.Result.Depths[in.Request.StartID]),
		Nodes:     make([]NodeView, 0, len(in.Result.Nodes)),
		Edges:     make([]EdgeView, 0, len(in.Result.Edges)),
		Cutoffs:   make([]CutoffView, 0, len(in.Result.DepthCutoffs)),
	}

	for id := range in.Result.Nodes {
		view.Nodes = append(view.Nodes, nodeView(in.Graph, id, in.Result.Depths[id]))
	}
	for _, edge := range in.Result.Edges {
		view.Edges = append(view.Edges, EdgeView{
			ID: edge.ID, CallerID: edge.CallerID, CalleeID: edge.CalleeID,
			Cycle: in.Result.Cycles[edge.ID], CallSite: edge.CallSite, Metadata: edge.Metadata,
		})
	}
	for _, cutoff := range in.Result.DepthCutoffs {
		targetID := cutoff.Edge.CalleeID
		if in.Request.Direction == graph.DirectionCaller {
			targetID = cutoff.Edge.CallerID
		}
		view.Cutoffs = append(view.Cutoffs, CutoffView{
			EdgeID: cutoff.Edge.ID, CallerID: cutoff.Edge.CallerID, CalleeID: cutoff.Edge.CalleeID,
			TargetMethodID: targetID, TargetMinDepth: cutoff.TargetMinDepth, CallSite: cutoff.Edge.CallSite,
		})
	}

	slices.SortFunc(view.Nodes, func(a, b NodeView) int { return compareID(a.ID, b.ID) })
	slices.SortFunc(view.Edges, func(a, b EdgeView) int { return compareID(a.ID, b.ID) })
	slices.SortFunc(view.Cutoffs, func(a, b CutoffView) int { return compareID(a.EdgeID, b.EdgeID) })
	return view
}

func nodeView(g *graph.Graph, id string, minDepth int) NodeView {
	view := NodeView{ID: id, MinDepth: minDepth}
	if g == nil {
		return view
	}
	node, ok := g.Node(id)
	if !ok {
		return view
	}
	view.QualifiedName = node.Symbol.QualifiedName
	view.Signature = node.Symbol.Signature
	view.Source = node.Symbol.Source
	view.Metadata = node.Symbol.Metadata
	return view
}

func compareID(a, b string) int {
	switch {
	case a < b:
		return -1
	case a > b:
		return 1
	default:
		return 0
	}
}
