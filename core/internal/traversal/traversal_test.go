package traversal_test

import (
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/graph/graphtest"
	"github.com/Fukuemon/depwalk/core/internal/traversal"
)

func intPtr(v int) *int { return &v }

func linearGraph() *graph.Graph {
	// a -> b -> c
	return graphtest.NewBuilder().
		Edge("edge:ab", "method:a", "method:b").
		Edge("edge:bc", "method:b", "method:c").
		Build()
}

func TestTraverseRejectsInvalidDirection(t *testing.T) {
	_, err := traversal.Traverse(linearGraph(), traversal.Request{StartID: "method:a", Direction: "sideways"})
	if err == nil {
		t.Error("Traverse with invalid direction returned nil error, want validation error")
	}
}

func TestTraverseRejectsNegativeMaxDepth(t *testing.T) {
	req := traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee, MaxDepth: intPtr(-1)}
	if _, err := traversal.Traverse(linearGraph(), req); err == nil {
		t.Error("Traverse with negative maxDepth returned nil error, want validation error")
	}
}

func TestTraverseRejectsInvalidOrder(t *testing.T) {
	req := traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee, Order: "random"}
	if _, err := traversal.Traverse(linearGraph(), req); err == nil {
		t.Error("Traverse with invalid order returned nil error, want validation error")
	}
}

func TestTraverseReturnsStartNotFoundForMissingStart(t *testing.T) {
	res, err := traversal.Traverse(linearGraph(), traversal.Request{StartID: "method:missing", Direction: graph.DirectionCallee})
	if err != nil {
		t.Fatalf("Traverse returned error %v, want nil (startNotFound is not an error)", err)
	}
	if res.Status != traversal.StatusStartNotFound {
		t.Errorf("Status = %q, want %q", res.Status, traversal.StatusStartNotFound)
	}
	if len(res.Nodes) != 0 {
		t.Errorf("Nodes = %v, want empty", res.Nodes)
	}
}

func TestTraverseReturnsStartNotFoundForEmptyGraph(t *testing.T) {
	res, err := traversal.Traverse(graph.New(), traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee})
	if err != nil {
		t.Fatalf("Traverse returned error %v, want nil", err)
	}
	if res.Status != traversal.StatusStartNotFound {
		t.Errorf("Status = %q, want %q", res.Status, traversal.StatusStartNotFound)
	}
}

func TestTraverseCalleeDirectionReachesCallees(t *testing.T) {
	res, err := traversal.Traverse(linearGraph(), traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee})
	if err != nil {
		t.Fatalf("Traverse returned error: %v", err)
	}
	if res.Status != traversal.StatusOK {
		t.Fatalf("Status = %q, want %q", res.Status, traversal.StatusOK)
	}
	want := []string{"method:a", "method:b", "method:c"}
	if len(res.Nodes) != len(want) {
		t.Fatalf("Nodes = %v, want %v", res.Nodes, want)
	}
	for _, id := range want {
		if !res.Nodes[id] {
			t.Errorf("Nodes missing %q", id)
		}
	}
}

func TestTraverseCallerDirectionReachesCallers(t *testing.T) {
	res, err := traversal.Traverse(linearGraph(), traversal.Request{StartID: "method:c", Direction: graph.DirectionCaller})
	if err != nil {
		t.Fatalf("Traverse returned error: %v", err)
	}
	want := []string{"method:c", "method:b", "method:a"}
	if len(res.Nodes) != len(want) {
		t.Fatalf("Nodes = %v, want %v", res.Nodes, want)
	}
	for _, id := range want {
		if !res.Nodes[id] {
			t.Errorf("Nodes missing %q", id)
		}
	}
}

func TestTraverseDoesNotLoopOnCircularGraph(t *testing.T) {
	// a -> b -> a and self loop c -> c reachable from a via b -> c.
	g := graphtest.NewBuilder().
		Edge("edge:ab", "method:a", "method:b").
		Edge("edge:ba", "method:b", "method:a").
		Edge("edge:bc", "method:b", "method:c").
		Edge("edge:cc", "method:c", "method:c").
		Build()

	res, err := traversal.Traverse(g, traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee})
	if err != nil {
		t.Fatalf("Traverse returned error: %v", err)
	}
	if len(res.Nodes) != 3 {
		t.Errorf("Nodes = %v, want a, b, c", res.Nodes)
	}
}

func TestTraverseUnlimitedDepthWhenMaxDepthUnset(t *testing.T) {
	// Deep chain a -> b -> c -> d -> e.
	g := graphtest.NewBuilder().
		Edge("edge:ab", "method:a", "method:b").
		Edge("edge:bc", "method:b", "method:c").
		Edge("edge:cd", "method:c", "method:d").
		Edge("edge:de", "method:d", "method:e").
		Build()

	res, err := traversal.Traverse(g, traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee})
	if err != nil {
		t.Fatalf("Traverse returned error: %v", err)
	}
	if len(res.Nodes) != 5 {
		t.Errorf("Nodes = %v, want all 5 nodes without depth cutoff", res.Nodes)
	}
}

func TestTraverseLimitsNodesByMinDepth(t *testing.T) {
	g := graphtest.NewBuilder().
		Edge("edge:ab", "method:a", "method:b").
		Edge("edge:bc", "method:b", "method:c").
		Edge("edge:cd", "method:c", "method:d").
		Build()

	res, err := traversal.Traverse(g, traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee, MaxDepth: intPtr(2)})
	if err != nil {
		t.Fatalf("Traverse returned error: %v", err)
	}
	want := []string{"method:a", "method:b", "method:c"}
	if len(res.Nodes) != len(want) {
		t.Fatalf("Nodes = %v, want %v", res.Nodes, want)
	}
	for _, id := range want {
		if !res.Nodes[id] {
			t.Errorf("Nodes missing %q", id)
		}
	}
	if res.Nodes["method:d"] {
		t.Error("Nodes contains method:d beyond maxDepth 2")
	}
}

func TestTraverseMaxDepthZeroKeepsOnlyStart(t *testing.T) {
	res, err := traversal.Traverse(linearGraph(), traversal.Request{StartID: "method:a", Direction: graph.DirectionCallee, MaxDepth: intPtr(0)})
	if err != nil {
		t.Fatalf("Traverse returned error: %v", err)
	}
	if len(res.Nodes) != 1 || !res.Nodes["method:a"] {
		t.Errorf("Nodes = %v, want only start node", res.Nodes)
	}
}

func TestTraverseConvergentNodeUsesShortestPathDepth(t *testing.T) {
	// Diamond with uneven arms: o -> a -> a2 -> m (depth 3) and o -> b -> m (depth 2).
	g := graphtest.NewBuilder().
		Edge("edge:oa", "method:o", "method:a").
		Edge("edge:aa2", "method:a", "method:a2").
		Edge("edge:a2m", "method:a2", "method:m").
		Edge("edge:ob", "method:o", "method:b").
		Edge("edge:bm", "method:b", "method:m").
		Build()

	res, err := traversal.Traverse(g, traversal.Request{StartID: "method:o", Direction: graph.DirectionCallee, MaxDepth: intPtr(2)})
	if err != nil {
		t.Fatalf("Traverse returned error: %v", err)
	}
	if !res.Nodes["method:m"] {
		t.Error("Nodes missing method:m: convergent node must use shortest-path minDepth (2), not the longer arm depth (3)")
	}
}

func TestTraverseNodesIdenticalForBFSAndDFS(t *testing.T) {
	// Uneven diamond where a DFS arm reaches m at depth 3 before the
	// shorter depth-2 arm: the node set must not depend on the order.
	g := graphtest.NewBuilder().
		Edge("edge:oa", "method:o", "method:a").
		Edge("edge:aa2", "method:a", "method:a2").
		Edge("edge:a2m", "method:a2", "method:m").
		Edge("edge:ob", "method:o", "method:b").
		Edge("edge:bm", "method:b", "method:m").
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
			bfs, err := traversal.Traverse(g, traversal.Request{StartID: "method:o", Direction: graph.DirectionCallee, MaxDepth: tc.maxDepth, Order: traversal.OrderBFS})
			if err != nil {
				t.Fatalf("Traverse(bfs) returned error: %v", err)
			}
			dfs, err := traversal.Traverse(g, traversal.Request{StartID: "method:o", Direction: graph.DirectionCallee, MaxDepth: tc.maxDepth, Order: traversal.OrderDFS})
			if err != nil {
				t.Fatalf("Traverse(dfs) returned error: %v", err)
			}
			if len(bfs.Nodes) != len(dfs.Nodes) {
				t.Fatalf("node set size differs: bfs=%v dfs=%v", bfs.Nodes, dfs.Nodes)
			}
			for id := range bfs.Nodes {
				if !dfs.Nodes[id] {
					t.Errorf("dfs result missing %q present in bfs result", id)
				}
			}
		})
	}
}
