package output

import (
	"encoding/json"
	"io"

	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/protocol"
	"github.com/Fukuemon/depwalk/core/internal/traversal"
)

const jsonSchemaVersion = "1.0"

type jsonFormatter struct{}

func init() {
	registerFormatter(FormatJSON, jsonFormatter{})
}

func (jsonFormatter) Format(w io.Writer, view View) error {
	return json.NewEncoder(w).Encode(newJSONDocument(view))
}

type jsonDocument struct {
	SchemaVersion string           `json:"schemaVersion"`
	Status        traversal.Status `json:"status"`
	Direction     graph.Direction  `json:"direction"`
	Start         string           `json:"start"`
	Nodes         []jsonNode       `json:"nodes"`
	Edges         []jsonEdge       `json:"edges"`
	DepthCutoffs  []jsonCutoff     `json:"depthCutoffs"`
}

type jsonNode struct {
	MethodID      string                   `json:"methodId"`
	QualifiedName string                   `json:"qualifiedName"`
	Signature     string                   `json:"signature"`
	MinDepth      int                      `json:"minDepth"`
	Source        *protocol.SourceLocation `json:"sourceLocation,omitempty"`
	Metadata      map[string]any           `json:"metadata,omitempty"`
}

type jsonEdge struct {
	EdgeID         string                   `json:"edgeId"`
	CallerMethodID string                   `json:"callerMethodId"`
	CalleeMethodID string                   `json:"calleeMethodId"`
	Cycle          bool                     `json:"cycle"`
	CallSite       *protocol.SourceLocation `json:"callSite,omitempty"`
	Metadata       map[string]any           `json:"metadata,omitempty"`
}

type jsonCutoff struct {
	EdgeID         string                   `json:"edgeId"`
	CallerMethodID string                   `json:"callerMethodId"`
	CalleeMethodID string                   `json:"calleeMethodId"`
	TargetMethodID string                   `json:"targetMethodId"`
	TargetMinDepth int                      `json:"targetMinDepth"`
	CallSite       *protocol.SourceLocation `json:"callSite,omitempty"`
}

func newJSONDocument(view View) jsonDocument {
	document := jsonDocument{
		SchemaVersion: jsonSchemaVersion,
		Status:        view.Status,
		Direction:     view.Direction,
		Start:         view.Start.ID,
		Nodes:         make([]jsonNode, 0, len(view.Nodes)),
		Edges:         make([]jsonEdge, 0, len(view.Edges)),
		DepthCutoffs:  make([]jsonCutoff, 0, len(view.Cutoffs)),
	}
	for _, node := range view.Nodes {
		document.Nodes = append(document.Nodes, jsonNode{
			MethodID: node.ID, QualifiedName: node.QualifiedName, Signature: node.Signature,
			MinDepth: node.MinDepth, Source: node.Source, Metadata: node.Metadata,
		})
	}
	for _, edge := range view.Edges {
		document.Edges = append(document.Edges, jsonEdge{
			EdgeID: edge.ID, CallerMethodID: edge.CallerID, CalleeMethodID: edge.CalleeID,
			Cycle: edge.Cycle, CallSite: edge.CallSite, Metadata: edge.Metadata,
		})
	}
	for _, cutoff := range view.Cutoffs {
		document.DepthCutoffs = append(document.DepthCutoffs, jsonCutoff{
			EdgeID: cutoff.EdgeID, CallerMethodID: cutoff.CallerID, CalleeMethodID: cutoff.CalleeID,
			TargetMethodID: cutoff.TargetMethodID, TargetMinDepth: cutoff.TargetMinDepth, CallSite: cutoff.CallSite,
		})
	}
	return document
}
