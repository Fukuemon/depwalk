package traversal_test

import (
	"reflect"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/graph/graphtest"
	"github.com/Fukuemon/depwalk/core/internal/traversal"
)

func mustTraverse(t *testing.T, g *graph.Graph, req traversal.Request) traversal.Result {
	t.Helper()
	res, err := traversal.Traverse(g, req)
	if err != nil {
		t.Fatalf("Traverse returned error: %v", err)
	}
	return res
}

func edgeIDs(res traversal.Result) map[string]bool {
	ids := map[string]bool{}
	for id := range res.Edges {
		ids[id] = true
	}
	return ids
}

func TestResultEdgesFormInducedSubgraph(t *testing.T) {
	// a -> b -> c: every edge between reached nodes is included.
	res := mustTraverse(t, linearGraph(), traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee})

	want := []string{"edge:ab", "edge:bc"}
	if len(res.Edges) != len(want) {
		t.Fatalf("Edges = %v, want %v", edgeIDs(res), want)
	}
	for _, id := range want {
		if _, ok := res.Edges[id]; !ok {
			t.Errorf("Edges missing %q", id)
		}
	}
}

func TestResultPublishesReachedNodeDepths(t *testing.T) {
	res := mustTraverse(t, linearGraph(), traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee})

	want := map[string]int{"method:a": 0, "method:b": 1, "method:c": 2}
	if len(res.Depths) != len(want) {
		t.Fatalf("Depths = %v, want %v", res.Depths, want)
	}
	for id, depth := range want {
		if res.Depths[id] != depth {
			t.Errorf("Depths[%q] = %d, want %d", id, res.Depths[id], depth)
		}
	}
}

func TestResultDepthsExcludeNodesBeyondMaxDepth(t *testing.T) {
	res := mustTraverse(t, linearGraph(), traversal.Request{
		StartID: "method:a", Direction: graph.DirectionCallee, MaxDepth: intPtr(1),
	})

	for id, depth := range res.Depths {
		if depth > 1 {
			t.Errorf("Depths[%q] = %d, want <= 1", id, depth)
		}
	}
	if _, ok := res.Depths["method:c"]; ok {
		t.Error("Depths contains method:c beyond maxDepth 1")
	}
}

func TestResultDepthsUseShortestPathAtConvergence(t *testing.T) {
	// o -> a -> a2 -> m and o -> b -> m: the second route is shorter.
	g := graphtest.NewBuilder().
		Edge("edge:oa", "method:o", "method:a").
		Edge("edge:aa2", "method:a", "method:a2").
		Edge("edge:a2m", "method:a2", "method:m").
		Edge("edge:ob", "method:o", "method:b").
		Edge("edge:bm", "method:b", "method:m").
		Build()

	res := mustTraverse(t, g, traversal.Request{StartID: "method:o", Direction: graph.DirectionCallee})

	if got := res.Depths["method:m"]; got != 2 {
		t.Errorf("Depths[method:m] = %d, want shortest depth 2", got)
	}
}

func TestResultDiamondKeepsAllConvergentEdges(t *testing.T) {
	// o -> a -> m and o -> b -> m: all four edges are call relations and
	// must survive regardless of which arm a walk expands first.
	g := graphtest.NewBuilder().
		Edge("edge:oa", "method:o", "method:a").
		Edge("edge:ob", "method:o", "method:b").
		Edge("edge:am", "method:a", "method:m").
		Edge("edge:bm", "method:b", "method:m").
		Build()

	res := mustTraverse(t, g, traversal.Request{StartID: "method:o", Direction: graph.DirectionCallee})

	if len(res.Edges) != 4 {
		t.Fatalf("Edges = %v, want all 4 diamond edges", edgeIDs(res))
	}
	if len(res.Cycles) != 0 {
		t.Errorf("Cycles = %v, want none: diamond convergence is not a cycle", res.Cycles)
	}
}

func TestResultSelfLoopAnnotatedAsCycle(t *testing.T) {
	g := graphtest.NewBuilder().
		Edge("edge:ab", "method:a", "method:b").
		Edge("edge:bb", "method:b", "method:b").
		Build()

	res := mustTraverse(t, g, traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee})

	if !res.Cycles["edge:bb"] {
		t.Error("self-loop edge:bb not annotated as cycle")
	}
	if _, ok := res.Edges["edge:bb"]; !ok {
		t.Error("cycle edge:bb excluded from Edges, want included (call relations are kept)")
	}
	if res.Cycles["edge:ab"] {
		t.Error("edge:ab annotated as cycle, want plain edge")
	}
}

func TestResultMutualRecursionAnnotatedAsCycle(t *testing.T) {
	// o -> a <-> b (mutual recursion), b -> c (escape edge).
	g := graphtest.NewBuilder().
		Edge("edge:oa", "method:o", "method:a").
		Edge("edge:ab", "method:a", "method:b").
		Edge("edge:ba", "method:b", "method:a").
		Edge("edge:bc", "method:b", "method:c").
		Build()

	res := mustTraverse(t, g, traversal.Request{StartID: "method:o", Direction: graph.DirectionCallee})

	if !res.Cycles["edge:ab"] || !res.Cycles["edge:ba"] {
		t.Errorf("Cycles = %v, want edge:ab and edge:ba (same SCC)", res.Cycles)
	}
	if res.Cycles["edge:oa"] || res.Cycles["edge:bc"] {
		t.Errorf("Cycles = %v: edges entering/leaving the SCC must not be annotated", res.Cycles)
	}
	for _, id := range []string{"edge:oa", "edge:ab", "edge:ba", "edge:bc"} {
		if _, ok := res.Edges[id]; !ok {
			t.Errorf("Edges missing %q", id)
		}
	}
}

func TestResultDepthCutoffRecordsBeyondLimitEdges(t *testing.T) {
	// a -> b -> c -> d with maxDepth 2: edge c->d leads to minDepth 3.
	g := graphtest.NewBuilder().
		Edge("edge:ab", "method:a", "method:b").
		Edge("edge:bc", "method:b", "method:c").
		Edge("edge:cd", "method:c", "method:d").
		Build()

	res := mustTraverse(t, g, traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee, MaxDepth: intPtr(2)})

	if _, ok := res.Edges["edge:cd"]; ok {
		t.Error("edge:cd included in Edges, want excluded (beyond depth limit)")
	}
	cut, ok := res.DepthCutoffs["edge:cd"]
	if !ok {
		t.Fatalf("DepthCutoffs = %v, want edge:cd", res.DepthCutoffs)
	}
	if cut.TargetMinDepth != 3 {
		t.Errorf("DepthCutoffs[edge:cd].TargetMinDepth = %d, want 3", cut.TargetMinDepth)
	}
}

func TestResultMaxDepthZeroCutsAllAdjacentEdges(t *testing.T) {
	g := graphtest.NewBuilder().
		Edge("edge:ab", "method:a", "method:b").
		Edge("edge:ac", "method:a", "method:c").
		Build()

	res := mustTraverse(t, g, traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee, MaxDepth: intPtr(0)})

	if len(res.Nodes) != 1 || !res.Nodes["method:a"] {
		t.Errorf("Nodes = %v, want only start", res.Nodes)
	}
	if len(res.Edges) != 0 {
		t.Errorf("Edges = %v, want empty", edgeIDs(res))
	}
	if len(res.DepthCutoffs) != 2 {
		t.Errorf("DepthCutoffs = %v, want both adjacent edges", res.DepthCutoffs)
	}
}

func TestResultMaxDepthZeroKeepsStartSelfLoopAsCycleEdge(t *testing.T) {
	// A self-loop on the start node has both endpoints reached even at
	// maxDepth 0, so it stays in the induced edge set with a cycle
	// annotation instead of becoming a depth cutoff.
	g := graphtest.NewBuilder().
		Edge("edge:aa", "method:a", "method:a").
		Edge("edge:ab", "method:a", "method:b").
		Build()

	res := mustTraverse(t, g, traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee, MaxDepth: intPtr(0)})

	if _, ok := res.Edges["edge:aa"]; !ok {
		t.Error("self-loop edge:aa excluded from Edges, want included (both endpoints reached)")
	}
	if !res.Cycles["edge:aa"] {
		t.Error("self-loop edge:aa not annotated as cycle")
	}
	if _, ok := res.DepthCutoffs["edge:ab"]; !ok {
		t.Error("edge:ab not recorded as depthLimit cutoff")
	}
}

func TestResultNoDepthCutoffsWhenUnlimited(t *testing.T) {
	res := mustTraverse(t, linearGraph(), traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee})

	if len(res.DepthCutoffs) != 0 {
		t.Errorf("DepthCutoffs = %v, want empty when maxDepth unset", res.DepthCutoffs)
	}
}

func TestResultCallerDirectionBuildsInducedSubgraph(t *testing.T) {
	// a -> c, b -> c: caller traversal from c reaches both callers.
	g := graphtest.NewBuilder().
		Edge("edge:ac", "method:a", "method:c").
		Edge("edge:bc", "method:b", "method:c").
		Build()

	res := mustTraverse(t, g, traversal.Request{StartID: "method:c", Direction: graph.DirectionCaller})

	if len(res.Edges) != 2 {
		t.Fatalf("Edges = %v, want both caller edges", edgeIDs(res))
	}
}

func TestResultStartNotFoundHasEmptyCollections(t *testing.T) {
	res := mustTraverse(t, linearGraph(), traversal.Request{StartID: "method:missing", Direction: graph.DirectionCallee})

	if res.Status != traversal.StatusStartNotFound {
		t.Fatalf("Status = %q, want %q", res.Status, traversal.StatusStartNotFound)
	}
	if len(res.Nodes) != 0 || len(res.Depths) != 0 || len(res.Edges) != 0 || len(res.Cycles) != 0 || len(res.DepthCutoffs) != 0 {
		t.Errorf("Result = %+v, want all collections empty", res)
	}
}

func TestResultIdenticalForBFSAndDFS(t *testing.T) {
	// Uneven diamond + cycle + depth limit: the full result contract
	// (nodes, edges, cycles, cutoffs) must not depend on the visit order.
	g := graphtest.NewBuilder().
		Edge("edge:oa", "method:o", "method:a").
		Edge("edge:aa2", "method:a", "method:a2").
		Edge("edge:a2m", "method:a2", "method:m").
		Edge("edge:ob", "method:o", "method:b").
		Edge("edge:bm", "method:b", "method:m").
		Edge("edge:mo", "method:m", "method:o").
		Edge("edge:md", "method:m", "method:d").
		Build()

	cases := []struct {
		name     string
		maxDepth *int
	}{
		{name: "unlimited depth", maxDepth: nil},
		{name: "maxDepth 2", maxDepth: intPtr(2)},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			bfs := mustTraverse(t, g, traversal.Request{StartID: "method:o", Direction: graph.DirectionCallee, MaxDepth: tc.maxDepth, Order: traversal.OrderBFS})
			dfs := mustTraverse(t, g, traversal.Request{StartID: "method:o", Direction: graph.DirectionCallee, MaxDepth: tc.maxDepth, Order: traversal.OrderDFS})

			if len(bfs.Nodes) != len(dfs.Nodes) {
				t.Fatalf("node sets differ: bfs=%v dfs=%v", bfs.Nodes, dfs.Nodes)
			}
			for id := range bfs.Nodes {
				if !dfs.Nodes[id] {
					t.Errorf("dfs Nodes missing %q", id)
				}
				if dfs.Depths[id] != bfs.Depths[id] {
					t.Errorf("dfs Depths[%q] = %d, want %d", id, dfs.Depths[id], bfs.Depths[id])
				}
			}
			if len(bfs.Edges) != len(dfs.Edges) {
				t.Fatalf("edge sets differ: bfs=%v dfs=%v", edgeIDs(bfs), edgeIDs(dfs))
			}
			for id := range bfs.Edges {
				if _, ok := dfs.Edges[id]; !ok {
					t.Errorf("dfs Edges missing %q", id)
				}
			}
			if len(bfs.Cycles) != len(dfs.Cycles) {
				t.Fatalf("cycle sets differ: bfs=%v dfs=%v", bfs.Cycles, dfs.Cycles)
			}
			for id := range bfs.Cycles {
				if !dfs.Cycles[id] {
					t.Errorf("dfs Cycles missing %q", id)
				}
			}
			if len(bfs.DepthCutoffs) != len(dfs.DepthCutoffs) {
				t.Fatalf("cutoff sets differ: bfs=%v dfs=%v", bfs.DepthCutoffs, dfs.DepthCutoffs)
			}
			for id, cut := range bfs.DepthCutoffs {
				if !reflect.DeepEqual(dfs.DepthCutoffs[id], cut) {
					t.Errorf("dfs DepthCutoffs[%q] = %+v, want %+v", id, dfs.DepthCutoffs[id], cut)
				}
			}
		})
	}
}
