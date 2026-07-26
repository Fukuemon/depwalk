package traversal

import (
	"slices"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/graph/graphtest"
)

// branchGraph is shaped so that BFS and DFS visit orders differ:
// a -> b, a -> c, b -> d. BFS visits [a b c d], DFS visits [a b d c].
func branchGraph() *graph.Graph {
	return graphtest.NewBuilder().
		Edge("edge:ab", "method:a", "method:b").
		Edge("edge:ac", "method:a", "method:c").
		Edge("edge:bd", "method:b", "method:d").
		Build()
}

func TestVisitOrderDefaultsToBFS(t *testing.T) {
	got := visitOrder(branchGraph(), "method:a", graph.DirectionCallee, "")
	want := []string{"method:a", "method:b", "method:c", "method:d"}
	if !slices.Equal(got, want) {
		t.Errorf("visitOrder(default) = %v, want BFS order %v", got, want)
	}
}

func TestVisitOrderBFS(t *testing.T) {
	got := visitOrder(branchGraph(), "method:a", graph.DirectionCallee, OrderBFS)
	want := []string{"method:a", "method:b", "method:c", "method:d"}
	if !slices.Equal(got, want) {
		t.Errorf("visitOrder(bfs) = %v, want %v", got, want)
	}
}

func TestVisitOrderDFS(t *testing.T) {
	got := visitOrder(branchGraph(), "method:a", graph.DirectionCallee, OrderDFS)
	want := []string{"method:a", "method:b", "method:d", "method:c"}
	if !slices.Equal(got, want) {
		t.Errorf("visitOrder(dfs) = %v, want %v", got, want)
	}
}

func TestVisitOrderDoesNotRevisitOnCycle(t *testing.T) {
	g := graphtest.NewBuilder().
		Edge("edge:ab", "method:a", "method:b").
		Edge("edge:ba", "method:b", "method:a").
		Build()

	got := visitOrder(g, "method:a", graph.DirectionCallee, OrderBFS)
	want := []string{"method:a", "method:b"}
	if !slices.Equal(got, want) {
		t.Errorf("visitOrder on cycle = %v, want %v (no revisit)", got, want)
	}
}

func TestMinDepthsComputesShortestDistances(t *testing.T) {
	// o -> a -> a2 -> m and o -> b -> m: minDepth(m) must be 2.
	g := graphtest.NewBuilder().
		Edge("edge:oa", "method:o", "method:a").
		Edge("edge:aa2", "method:a", "method:a2").
		Edge("edge:a2m", "method:a2", "method:m").
		Edge("edge:ob", "method:o", "method:b").
		Edge("edge:bm", "method:b", "method:m").
		Build()

	got := minDepths(g, "method:o", graph.DirectionCallee)
	want := map[string]int{
		"method:o":  0,
		"method:a":  1,
		"method:b":  1,
		"method:a2": 2,
		"method:m":  2,
	}
	if len(got) != len(want) {
		t.Fatalf("minDepths = %v, want %v", got, want)
	}
	for id, d := range want {
		if got[id] != d {
			t.Errorf("minDepths[%q] = %d, want %d", id, got[id], d)
		}
	}
}
