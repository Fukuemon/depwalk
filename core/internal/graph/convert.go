package graph

import "github.com/Fukuemon/depwalk/core/internal/protocol"

// NodeFromMethodSymbol converts an Analyzer Protocol method symbol to a [Node].
// The record's source location and opaque metadata are deep copied into
// graph-owned values so later mutation of the protocol DTO cannot change the
// graph.
func NodeFromMethodSymbol(record protocol.MethodSymbol) Node {
	return Node{
		ID: record.MethodID,
		Symbol: Symbol{
			QualifiedName: record.QualifiedName,
			Signature:     record.Signature,
			Source:        copySourceLocation(record.Source),
			Metadata:      copyMetadataObject(record.Metadata),
		},
	}
}

func copySourceLocation(location *protocol.SourceLocation) *protocol.SourceLocation {
	if location == nil {
		return nil
	}
	copied := *location
	copied.StartColumn = copyIntPointer(location.StartColumn)
	copied.EndLine = copyIntPointer(location.EndLine)
	copied.EndColumn = copyIntPointer(location.EndColumn)
	return &copied
}

func copyIntPointer(value *int) *int {
	if value == nil {
		return nil
	}
	copied := *value
	return &copied
}

func copyMetadataObject(object map[string]any) map[string]any {
	if object == nil {
		return nil
	}
	copied := make(map[string]any, len(object))
	for key, value := range object {
		copied[key] = copyMetadataValue(value)
	}
	return copied
}

// copyMetadataValue deep copies one opaque JSON value. Scalars (string,
// bool, nil, and the number representations json.Number / float64) are
// immutable and returned as is.
func copyMetadataValue(value any) any {
	switch typed := value.(type) {
	case map[string]any:
		return copyMetadataObject(typed)
	case []any:
		copied := make([]any, len(typed))
		for i, element := range typed {
			copied[i] = copyMetadataValue(element)
		}
		return copied
	default:
		return typed
	}
}

// EdgeFromCallEdge converts an Analyzer Protocol call edge to an [Edge]. The
// call site is deep copied into a graph-owned value like the node conversion.
func EdgeFromCallEdge(record protocol.CallEdge) Edge {
	return Edge{
		ID:       record.EdgeID,
		CallerID: record.CallerMethodID,
		CalleeID: record.CalleeMethodID,
		CallSite: copySourceLocation(record.CallSite),
	}
}
