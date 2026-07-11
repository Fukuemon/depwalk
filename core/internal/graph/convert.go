package graph

import "github.com/Fukuemon/depwalk/core/internal/protocol"

// NodeFromMethodSymbol converts an Analyzer Protocol method symbol to a [Node].
func NodeFromMethodSymbol(record protocol.MethodSymbol) Node {
	return Node{
		ID: record.MethodID,
		Symbol: Symbol{
			QualifiedName: record.QualifiedName,
			Signature:     record.Signature,
			Source:        record.Source,
		},
	}
}

// EdgeFromCallEdge converts an Analyzer Protocol call edge to an [Edge].
func EdgeFromCallEdge(record protocol.CallEdge) Edge {
	return Edge{
		ID:       record.EdgeID,
		CallerID: record.CallerMethodID,
		CalleeID: record.CalleeMethodID,
		CallSite: record.CallSite,
	}
}
