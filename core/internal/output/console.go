package output

import (
	"fmt"
	"io"
	"slices"

	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/traversal"
)

type consoleFormatter struct{}

func (consoleFormatter) Format(w io.Writer, view View) error {
	if view.Status == traversal.StatusStartNotFound {
		_, err := fmt.Fprintf(w, "該当なし: 起点メソッドが解析結果に存在しません (%s)\n", view.Start.ID)
		return err
	}

	tree := newConsoleTree(view)
	if _, err := fmt.Fprintln(w, formatNode(view.Start, view.Start.Source)); err != nil {
		return err
	}
	tree.expanded[view.Start.ID] = true
	tree.ancestors[view.Start.ID] = true
	defer delete(tree.ancestors, view.Start.ID)

	children := tree.children[view.Start.ID]
	cutoffs := tree.cutoffs[view.Start.ID]
	if len(children) == 0 && cutoffs == 0 {
		message := "(呼び出し先なし)"
		if view.Direction == graph.DirectionCaller {
			message = "(呼び出し元なし)"
		}
		_, err := fmt.Fprintf(w, "└─ %s\n", message)
		return err
	}
	return tree.writeChildren(w, view.Start.ID, "")
}

type consoleTree struct {
	nodes     map[string]NodeView
	children  map[string][]consoleChild
	cutoffs   map[string]int
	expanded  map[string]bool
	ancestors map[string]bool
}

type consoleChild struct {
	node     NodeView
	edgeID   string
	callSite *graph.SourceLocation
}

func newConsoleTree(view View) *consoleTree {
	tree := &consoleTree{
		nodes: make(map[string]NodeView, len(view.Nodes)), children: map[string][]consoleChild{},
		cutoffs: map[string]int{}, expanded: map[string]bool{}, ancestors: map[string]bool{},
	}
	for _, node := range view.Nodes {
		tree.nodes[node.ID] = node
	}
	for _, edge := range view.Edges {
		parentID, childID := edge.CallerID, edge.CalleeID
		if view.Direction == graph.DirectionCaller {
			parentID, childID = edge.CalleeID, edge.CallerID
		}
		tree.children[parentID] = append(tree.children[parentID], consoleChild{
			node: tree.nodes[childID], edgeID: edge.ID, callSite: edge.CallSite,
		})
	}
	for _, cutoff := range view.Cutoffs {
		parentID := cutoff.CallerID
		if view.Direction == graph.DirectionCaller {
			parentID = cutoff.CalleeID
		}
		tree.cutoffs[parentID]++
	}
	for parentID := range tree.children {
		slices.SortFunc(tree.children[parentID], compareConsoleChild)
	}
	return tree
}

func compareConsoleChild(a, b consoleChild) int {
	for _, values := range [][2]string{
		{a.node.QualifiedName, b.node.QualifiedName},
		{a.node.Signature, b.node.Signature},
		{a.node.ID, b.node.ID},
		{a.edgeID, b.edgeID},
	} {
		if result := compareID(values[0], values[1]); result != 0 {
			return result
		}
	}
	return 0
}

func (tree *consoleTree) writeChildren(w io.Writer, parentID, prefix string) error {
	children := tree.children[parentID]
	cutoffCount := tree.cutoffs[parentID]
	for i, child := range children {
		isLast := i == len(children)-1 && cutoffCount == 0
		connector, childPrefix := "├─ ", prefix+"│  "
		if isLast {
			connector, childPrefix = "└─ ", prefix+"   "
		}

		marker := ""
		switch {
		case tree.ancestors[child.node.ID]:
			marker = "cycle"
		case tree.expanded[child.node.ID]:
			marker = "既出"
		}
		if _, err := fmt.Fprintf(w, "%s%s%s%s\n", prefix, connector, formatNode(child.node, child.callSite), formatMarker(marker)); err != nil {
			return err
		}
		if marker != "" {
			continue
		}

		tree.expanded[child.node.ID] = true
		tree.ancestors[child.node.ID] = true
		if err := tree.writeChildren(w, child.node.ID, childPrefix); err != nil {
			return err
		}
		delete(tree.ancestors, child.node.ID)
	}
	if cutoffCount > 0 {
		_, err := fmt.Fprintf(w, "%s└─ … (depth limit: %d edges cut)\n", prefix, cutoffCount)
		return err
	}
	return nil
}

func formatNode(node NodeView, location *graph.SourceLocation) string {
	label := node.Signature
	if label == "" {
		label = node.QualifiedName
	}
	if label == "" {
		label = node.ID
	}
	if location == nil {
		return label
	}
	return fmt.Sprintf("%s  [%s:%d]", label, location.Path, location.StartLine)
}

func formatMarker(marker string) string {
	if marker == "" {
		return ""
	}
	return "  (" + marker + ")"
}
