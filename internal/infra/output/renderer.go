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

	// Track which nodes we've defined to avoid duplicates
	definedNodes := make(map[string]bool)

	// Add edges with node definitions
	for from, edges := range g.Edges {
		fromID := mermaidNodeID(string(from))
		fromLabel := mermaidNodeLabel(string(from))

		// Define 'from' node if not yet defined
		if !definedNodes[fromID] {
			sb.WriteString(fmt.Sprintf("    %s[\"%s\"]\n", fromID, fromLabel))
			definedNodes[fromID] = true
		}

		for to := range edges {
			toID := mermaidNodeID(string(to))
			toLabel := mermaidNodeLabel(string(to))

			// Define 'to' node if not yet defined
			if !definedNodes[toID] {
				sb.WriteString(fmt.Sprintf("    %s[\"%s\"]\n", toID, toLabel))
				definedNodes[toID] = true
			}

			sb.WriteString(fmt.Sprintf("    %s --> %s\n", fromID, toID))
		}
	}

	return sb.String()
}

// mermaidNodeID creates a valid Mermaid node ID from a MethodID.
func mermaidNodeID(id string) string {
	// Replace special characters that break Mermaid node IDs
	id = strings.ReplaceAll(id, "#", "_")
	id = strings.ReplaceAll(id, "(", "_")
	id = strings.ReplaceAll(id, ")", "_")
	id = strings.ReplaceAll(id, ",", "_")
	id = strings.ReplaceAll(id, ".", "_")
	id = strings.ReplaceAll(id, " ", "_")
	return id
}

// mermaidNodeLabel creates a readable label for display.
// Format: ClassName.methodName(params)
func mermaidNodeLabel(id string) string {
	// Parse: com.example.FooService#doThing(java.lang.String,int)
	hashIdx := strings.LastIndex(id, "#")
	if hashIdx == -1 {
		return id
	}

	fqn := id[:hashIdx]
	methodSig := id[hashIdx+1:]

	// Extract simple class name from FQN
	dotIdx := strings.LastIndex(fqn, ".")
	className := fqn
	if dotIdx >= 0 {
		className = fqn[dotIdx+1:]
	}

	// Simplify parameter types
	parenIdx := strings.Index(methodSig, "(")
	if parenIdx == -1 {
		return className + "." + methodSig
	}

	methodName := methodSig[:parenIdx]
	paramsPart := methodSig[parenIdx+1 : len(methodSig)-1] // Remove ( and )

	if paramsPart == "" {
		return className + "." + methodName + "()"
	}

	// Simplify each parameter type
	params := strings.Split(paramsPart, ",")
	simpleParams := make([]string, len(params))
	for i, p := range params {
		dotIdx := strings.LastIndex(p, ".")
		if dotIdx >= 0 {
			simpleParams[i] = p[dotIdx+1:]
		} else {
			simpleParams[i] = p
		}
	}

	return className + "." + methodName + "(" + strings.Join(simpleParams, ", ") + ")"
}
