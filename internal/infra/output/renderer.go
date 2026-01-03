// Package output implements the Renderer interface for various output formats.
package output

import (
	"context"
	"fmt"
	"strings"

	"github.com/Fukuemon/depwalk/internal/model"
)

// Format specifies the output format.
type Format string

const (
	FormatTree    Format = "tree"
	FormatMermaid Format = "mermaid"
)

// Direction specifies the traversal direction.
type Direction string

const (
	DirectionCallees Direction = "callees"
	DirectionCallers Direction = "callers"
)

// Renderer renders a call graph to various formats.
type Renderer struct{}

// NewRenderer creates a new renderer.
func NewRenderer() *Renderer {
	return &Renderer{}
}

// Render renders the graph in the specified format.
func (r *Renderer) Render(ctx context.Context, root model.MethodID, g *model.Graph, format Format, direction Direction) (string, error) {
	switch format {
	case FormatTree:
		return r.renderTree(root, g, direction), nil
	case FormatMermaid:
		return r.renderMermaid(root, g, direction), nil
	default:
		return "", fmt.Errorf("unsupported format: %s", format)
	}
}

func (r *Renderer) renderTree(root model.MethodID, g *model.Graph, direction Direction) string {
	var sb strings.Builder
	sb.WriteString(fmt.Sprintf("%s: %s\n", direction, root))

	visited := make(map[model.MethodID]bool)
	r.renderTreeNode(&sb, root, g, direction, "", true, visited)
	return sb.String()
}

func (r *Renderer) renderTreeNode(sb *strings.Builder, node model.MethodID, g *model.Graph, direction Direction, prefix string, isLast bool, visited map[model.MethodID]bool) {
	if visited[node] {
		return
	}
	visited[node] = true

	var children []model.MethodID
	if direction == DirectionCallees {
		children = g.Successors(node)
	} else {
		children = g.Predecessors(node)
	}

	for i, child := range children {
		isChildLast := i == len(children)-1
		connector := "├─ "
		if isChildLast {
			connector = "└─ "
		}
		sb.WriteString(fmt.Sprintf("%s%s%s\n", prefix, connector, child))

		newPrefix := prefix
		if isChildLast {
			newPrefix += "   "
		} else {
			newPrefix += "│  "
		}
		r.renderTreeNode(sb, child, g, direction, newPrefix, isChildLast, visited)
	}
}

func (r *Renderer) renderMermaid(root model.MethodID, g *model.Graph, direction Direction) string {
	var sb strings.Builder

	// Use TD for callees (top-down), BT for callers (bottom-up)
	graphDir := "TD"
	if direction == DirectionCallers {
		graphDir = "BT"
	}
	sb.WriteString(fmt.Sprintf("graph %s\n", graphDir))

	// Add edges
	for from, edges := range g.Edges {
		for to := range edges {
			// Escape special characters in method IDs
			fromEsc := escapeMermaidID(string(from))
			toEsc := escapeMermaidID(string(to))
			sb.WriteString(fmt.Sprintf("    %s --> %s\n", fromEsc, toEsc))
		}
	}

	return sb.String()
}

func escapeMermaidID(id string) string {
	// Replace special characters that break Mermaid syntax
	id = strings.ReplaceAll(id, "#", "_")
	id = strings.ReplaceAll(id, "(", "[")
	id = strings.ReplaceAll(id, ")", "]")
	id = strings.ReplaceAll(id, ",", "_")
	id = strings.ReplaceAll(id, ".", "_")
	return id
}

