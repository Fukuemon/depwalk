package protocol

import "github.com/Fukuemon/depwalk/core/internal/graph"

// 本ファイルは ACL の Translator 側。wire DTO を graph が所有する domain 値へ
// 写す。
//
// 入れ子のデータは deep copy する。あとから protocol の DTO を書き換えても
// graph が変わらないようにするためである。wire 専用の field
// (schemaVersion / recordType) はここで落とし、domain model へは渡さない。
// 渡すと domain が wire の版に結合する。

// NodeFromMethodSymbol は Analyzer Protocol の method symbol を [graph.Node] へ
// 変換する。source location と opaque な metadata は graph 所有の値へ deep copy する。
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

// EdgeFromCallEdge は Analyzer Protocol の call edge を [graph.Edge] へ変換する。
// call site と opaque な metadata は node の変換と同じく deep copy する。
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

// copyMetadataValue は opaque な JSON 値 1 つを deep copy する。
// スカラー (string / bool / nil、数値表現の json.Number / float64) は不変なので
// そのまま返す。
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
