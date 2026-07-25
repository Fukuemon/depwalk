package graph

import (
	"reflect"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

func TestNodeFromMethodSymbol(t *testing.T) {
	source := &protocol.SourceLocation{Path: "service.go", StartLine: 12}
	record := protocol.MethodSymbol{
		SchemaVersion: "1.0",
		RecordType:    protocol.RecordTypeMethodSymbol,
		MethodID:      "method:a",
		QualifiedName: "example.Service.Run",
		Signature:     "()",
		Source:        source,
	}

	got := NodeFromMethodSymbol(record)
	want := Node{
		ID: "method:a",
		Symbol: Symbol{
			QualifiedName: "example.Service.Run",
			Signature:     "()",
			Source:        source,
		},
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("NodeFromMethodSymbol() = %#v, want %#v", got, want)
	}
}

func TestNodeFromMethodSymbolAllowsNilSource(t *testing.T) {
	got := NodeFromMethodSymbol(protocol.MethodSymbol{MethodID: "method:a"})
	if got.Symbol.Source != nil {
		t.Errorf("NodeFromMethodSymbol().Symbol.Source = %#v, want nil", got.Symbol.Source)
	}
}

func TestEdgeFromCallEdge(t *testing.T) {
	callSite := &protocol.SourceLocation{Path: "caller.go", StartLine: 24}
	record := protocol.CallEdge{
		SchemaVersion:  "1.0",
		RecordType:     protocol.RecordTypeCallEdge,
		EdgeID:         "edge:ab",
		CallerMethodID: "method:a",
		CalleeMethodID: "method:b",
		CallSite:       callSite,
	}

	got := EdgeFromCallEdge(record)
	want := Edge{ID: "edge:ab", CallerID: "method:a", CalleeID: "method:b", CallSite: callSite}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("EdgeFromCallEdge() = %#v, want %#v", got, want)
	}
}

func TestNodeFromMethodSymbolDeepCopiesOpaqueMetadata(t *testing.T) {
	record := protocol.MethodSymbol{
		MethodID: "method:a",
		Metadata: protocol.Metadata{
			"declarationOrigin": "projectClasses",
			"ownerSourceLocation": map[string]any{
				"path":      "module-a/src/Owner.java",
				"startLine": 3,
			},
			"tags":         []any{"generated", map[string]any{"nested": true}},
			"sourceAnchor": nil,
		},
	}

	got := NodeFromMethodSymbol(record)

	want := map[string]any{
		"declarationOrigin": "projectClasses",
		"ownerSourceLocation": map[string]any{
			"path":      "module-a/src/Owner.java",
			"startLine": 3,
		},
		"tags":         []any{"generated", map[string]any{"nested": true}},
		"sourceAnchor": nil,
	}
	if !reflect.DeepEqual(got.Symbol.Metadata, want) {
		t.Fatalf("Metadata = %#v, want %#v", got.Symbol.Metadata, want)
	}

	// Mutating the protocol DTO's nested values must not change the
	// graph-owned copy.
	record.Metadata["ownerSourceLocation"].(map[string]any)["path"] = "mutated"
	record.Metadata["tags"].([]any)[1].(map[string]any)["nested"] = false
	if !reflect.DeepEqual(got.Symbol.Metadata, want) {
		t.Fatalf("Metadata after DTO mutation = %#v, want unchanged %#v", got.Symbol.Metadata, want)
	}
}

func TestNodeFromMethodSymbolDistinguishesOmittedAndEmptyMetadata(t *testing.T) {
	omitted := NodeFromMethodSymbol(protocol.MethodSymbol{MethodID: "method:a"})
	if omitted.Symbol.Metadata != nil {
		t.Fatalf("omitted metadata = %#v, want nil", omitted.Symbol.Metadata)
	}

	empty := NodeFromMethodSymbol(protocol.MethodSymbol{MethodID: "method:a", Metadata: protocol.Metadata{}})
	if empty.Symbol.Metadata == nil || len(empty.Symbol.Metadata) != 0 {
		t.Fatalf("empty metadata = %#v, want non-nil empty map", empty.Symbol.Metadata)
	}
}

func TestEdgeFromCallEdgeDeepCopiesOpaqueMetadata(t *testing.T) {
	record := protocol.CallEdge{
		EdgeID: "edge:ab",
		Metadata: protocol.Metadata{
			"resolution": "springDi",
			"provenance": map[string]any{
				"bean":   "exampleService",
				"source": "componentScan",
			},
			"candidates": []any{"method:b", map[string]any{"selected": true}},
			"reason":     nil,
		},
	}

	got := EdgeFromCallEdge(record)

	want := map[string]any{
		"resolution": "springDi",
		"provenance": map[string]any{
			"bean":   "exampleService",
			"source": "componentScan",
		},
		"candidates": []any{"method:b", map[string]any{"selected": true}},
		"reason":     nil,
	}
	if !reflect.DeepEqual(got.Metadata, want) {
		t.Fatalf("Metadata = %#v, want %#v", got.Metadata, want)
	}

	// Mutating the protocol DTO's nested values must not change the
	// graph-owned copy.
	record.Metadata["provenance"].(map[string]any)["source"] = "mutated"
	record.Metadata["candidates"].([]any)[1].(map[string]any)["selected"] = false
	if !reflect.DeepEqual(got.Metadata, want) {
		t.Fatalf("Metadata after DTO mutation = %#v, want unchanged %#v", got.Metadata, want)
	}
}

func TestEdgeFromCallEdgeDistinguishesOmittedAndEmptyMetadata(t *testing.T) {
	omitted := EdgeFromCallEdge(protocol.CallEdge{EdgeID: "edge:ab"})
	if omitted.Metadata != nil {
		t.Fatalf("omitted metadata = %#v, want nil", omitted.Metadata)
	}

	empty := EdgeFromCallEdge(protocol.CallEdge{EdgeID: "edge:ab", Metadata: protocol.Metadata{}})
	if empty.Metadata == nil || len(empty.Metadata) != 0 {
		t.Fatalf("empty metadata = %#v, want non-nil empty map", empty.Metadata)
	}
}

func TestEdgeFromCallEdgeAllowsNilCallSite(t *testing.T) {
	got := EdgeFromCallEdge(protocol.CallEdge{EdgeID: "edge:ab"})
	if got.CallSite != nil {
		t.Errorf("EdgeFromCallEdge().CallSite = %#v, want nil", got.CallSite)
	}
}
