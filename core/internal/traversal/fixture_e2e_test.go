package traversal_test

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/traversal"
)

// fixtureFile is the self-describing schema of a traversal E2E fixture:
// a graph input (keys matching the Analyzer Protocol json tags) plus the
// expected reached sets per traversal case (S1 = caller, S2 = callee).
type fixtureFile struct {
	Description string        `json:"description"`
	Graph       fixtureGraph  `json:"graph"`
	Cases       []fixtureCase `json:"cases"`
}

type fixtureGraph struct {
	Nodes []string      `json:"nodes"`
	Edges []fixtureEdge `json:"edges"`
}

type fixtureEdge struct {
	EdgeID         string `json:"edgeId"`
	CallerMethodID string `json:"callerMethodId"`
	CalleeMethodID string `json:"calleeMethodId"`
}

type fixtureCase struct {
	Name      string          `json:"name"`
	Start     string          `json:"start"`
	Direction string          `json:"direction"`
	MaxDepth  *int            `json:"maxDepth"`
	Expected  fixtureExpected `json:"expected"`
}

type fixtureExpected struct {
	Status       string          `json:"status"`
	Nodes        []string        `json:"nodes"`
	Edges        []string        `json:"edges"`
	Cycles       []string        `json:"cycles"`
	DepthCutoffs []fixtureCutoff `json:"depthCutoffs"`
}

type fixtureCutoff struct {
	EdgeID         string `json:"edgeId"`
	TargetMinDepth int    `json:"targetMinDepth"`
}

func TestTraversalMatchesFixtureExpectations(t *testing.T) {
	dir := filepath.Join("..", "..", "..", "testdata", "fixtures", "traversal")
	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatalf("ReadDir(%s) error = %v", dir, err)
	}

	ran := 0
	for _, entry := range entries {
		if entry.IsDir() || filepath.Ext(entry.Name()) != ".json" {
			continue
		}
		ran++
		t.Run(entry.Name(), func(t *testing.T) {
			runFixture(t, filepath.Join(dir, entry.Name()))
		})
	}
	if ran == 0 {
		t.Fatalf("no fixture files in %s", dir)
	}
}

func runFixture(t *testing.T, path string) {
	t.Helper()

	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("ReadFile(%s) error = %v", path, err)
	}
	var fixture fixtureFile
	if err := json.Unmarshal(content, &fixture); err != nil {
		t.Fatalf("Unmarshal(%s) error = %v", path, err)
	}

	g := graph.New()
	for _, id := range fixture.Graph.Nodes {
		g.AddNode(graph.Node{ID: id})
	}
	for _, e := range fixture.Graph.Edges {
		g.AddEdge(graph.Edge{ID: e.EdgeID, CallerID: e.CallerMethodID, CalleeID: e.CalleeMethodID})
	}

	for _, tc := range fixture.Cases {
		t.Run(tc.Name, func(t *testing.T) {
			res, err := traversal.Traverse(g, traversal.Request{
				StartID:   tc.Start,
				Direction: graph.Direction(tc.Direction),
				MaxDepth:  tc.MaxDepth,
			})
			if err != nil {
				t.Fatalf("Traverse returned error: %v", err)
			}
			assertResult(t, res, tc.Expected)
		})
	}
}

func assertResult(t *testing.T, res traversal.Result, want fixtureExpected) {
	t.Helper()

	if string(res.Status) != want.Status {
		t.Errorf("Status = %q, want %q", res.Status, want.Status)
	}

	if len(res.Nodes) != len(want.Nodes) {
		t.Errorf("Nodes = %v, want %v", keys(res.Nodes), want.Nodes)
	}
	for _, id := range want.Nodes {
		if !res.Nodes[id] {
			t.Errorf("Nodes missing %q", id)
		}
	}

	if len(res.Edges) != len(want.Edges) {
		t.Errorf("Edges size = %d, want %d (%v)", len(res.Edges), len(want.Edges), want.Edges)
	}
	for _, id := range want.Edges {
		if _, ok := res.Edges[id]; !ok {
			t.Errorf("Edges missing %q", id)
		}
	}

	if len(res.Cycles) != len(want.Cycles) {
		t.Errorf("Cycles = %v, want %v", keys(res.Cycles), want.Cycles)
	}
	for _, id := range want.Cycles {
		if !res.Cycles[id] {
			t.Errorf("Cycles missing %q", id)
		}
	}

	if len(res.DepthCutoffs) != len(want.DepthCutoffs) {
		t.Errorf("DepthCutoffs size = %d, want %d (%v)", len(res.DepthCutoffs), len(want.DepthCutoffs), want.DepthCutoffs)
	}
	for _, cut := range want.DepthCutoffs {
		got, ok := res.DepthCutoffs[cut.EdgeID]
		if !ok {
			t.Errorf("DepthCutoffs missing %q", cut.EdgeID)
			continue
		}
		if got.TargetMinDepth != cut.TargetMinDepth {
			t.Errorf("DepthCutoffs[%q].TargetMinDepth = %d, want %d", cut.EdgeID, got.TargetMinDepth, cut.TargetMinDepth)
		}
	}
}

func keys(set map[string]bool) []string {
	out := make([]string, 0, len(set))
	for k := range set {
		out = append(out, k)
	}
	return out
}
