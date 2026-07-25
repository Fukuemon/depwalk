package protocol

import "github.com/Fukuemon/depwalk/core/internal/graph"

// This file is the Translator half of the ACL (spec #32 D6): it maps wire
// DTOs to graph-owned domain values, deep copying nested data so later
// mutation of a protocol DTO can never change the graph. Wire-only fields
// (schemaVersion / recordType) are dropped here and never reach the
// domain model.

// NodeFromMethodSymbol converts an Analyzer Protocol method symbol to a
// [graph.Node]. The record's source location and opaque metadata are deep
// copied into graph-owned values.
func NodeFromMethodSymbol(record MethodSymbol) graph.Node {
	return graph.Node{
		ID: record.MethodID,
		Symbol: graph.Symbol{
			QualifiedName: record.QualifiedName,
			Signature:     record.Signature,
			Source:        copySourceLocation(record.Source),
			Metadata:      copyMetadataObject(record.Metadata),
		},
	}
}

// EdgeFromCallEdge converts an Analyzer Protocol call edge to a
// [graph.Edge]. The call site and opaque metadata are deep copied into
// graph-owned values like the node conversion.
func EdgeFromCallEdge(record CallEdge) graph.Edge {
	return graph.Edge{
		ID:       record.EdgeID,
		CallerID: record.CallerMethodID,
		CalleeID: record.CalleeMethodID,
		CallSite: copySourceLocation(record.CallSite),
		Metadata: copyMetadataObject(record.Metadata),
	}
}

func copySourceLocation(location *SourceLocation) *graph.SourceLocation {
	if location == nil {
		return nil
	}
	return &graph.SourceLocation{
		Path:        location.Path,
		StartLine:   location.StartLine,
		StartColumn: copyIntPointer(location.StartColumn),
		EndLine:     copyIntPointer(location.EndLine),
		EndColumn:   copyIntPointer(location.EndColumn),
	}
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
