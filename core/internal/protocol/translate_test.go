package protocol_test

import (
	"reflect"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

func TestNodeFromMethodSymbol(t *testing.T) {
	startColumn := 4
	record := protocol.MethodSymbol{
		SchemaVersion: "1.0",
		RecordType:    protocol.RecordTypeMethodSymbol,
		MethodID:      "method:a",
		QualifiedName: "example.Service.Run",
		Signature:     "()",
		Source:        &protocol.SourceLocation{Path: "service.go", StartLine: 12, StartColumn: &startColumn},
	}

	got := protocol.NodeFromMethodSymbol(record)
	wantColumn := 4
	want := graph.Node{
		ID: "method:a",
		Symbol: graph.Symbol{
			QualifiedName: "example.Service.Run",
			Signature:     "()",
			Source:        &graph.SourceLocation{Path: "service.go", StartLine: 12, StartColumn: &wantColumn},
		},
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("NodeFromMethodSymbol() = %#v, want %#v", got, want)
	}
}

func TestNodeFromMethodSymbolDeepCopiesSourceLocation(t *testing.T) {
	startColumn := 4
	record := protocol.MethodSymbol{
		MethodID: "method:a",
		Source:   &protocol.SourceLocation{Path: "service.go", StartLine: 12, StartColumn: &startColumn},
	}

	got := protocol.NodeFromMethodSymbol(record)

	// protocol の DTO を書き換えても、graph が所有する値は変わらないこと。
	record.Source.Path = "mutated.go"
	*record.Source.StartColumn = 99
	wantColumn := 4
	want := &graph.SourceLocation{Path: "service.go", StartLine: 12, StartColumn: &wantColumn}
	if !reflect.DeepEqual(got.Symbol.Source, want) {
		t.Fatalf("Source after DTO mutation = %#v, want unchanged %#v", got.Symbol.Source, want)
	}
}

func TestNodeFromMethodSymbolAllowsNilSource(t *testing.T) {
	got := protocol.NodeFromMethodSymbol(protocol.MethodSymbol{MethodID: "method:a"})
	if got.Symbol.Source != nil {
		t.Errorf("NodeFromMethodSymbol().Symbol.Source = %#v, want nil", got.Symbol.Source)
	}
}

func TestEdgeFromCallEdge(t *testing.T) {
	record := protocol.CallEdge{
		SchemaVersion:  "1.0",
		RecordType:     protocol.RecordTypeCallEdge,
		EdgeID:         "edge:ab",
		CallerMethodID: "method:a",
		CalleeMethodID: "method:b",
		CallSite:       &protocol.SourceLocation{Path: "caller.go", StartLine: 24},
	}

	got := protocol.EdgeFromCallEdge(record)
	want := graph.Edge{
		ID: "edge:ab", CallerID: "method:a", CalleeID: "method:b",
		CallSite: &graph.SourceLocation{Path: "caller.go", StartLine: 24},
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("EdgeFromCallEdge() = %#v, want %#v", got, want)
	}
}

func TestEdgeFromCallEdgeDeepCopiesCallSite(t *testing.T) {
	startColumn := 7
	record := protocol.CallEdge{
		EdgeID:   "edge:ab",
		CallSite: &protocol.SourceLocation{Path: "caller.go", StartLine: 24, StartColumn: &startColumn},
	}

	got := protocol.EdgeFromCallEdge(record)

	// protocol の DTO を書き換えても、graph が所有する値は変わらないこと。
	record.CallSite.Path = "mutated.go"
	*record.CallSite.StartColumn = 99
	wantColumn := 7
	want := &graph.SourceLocation{Path: "caller.go", StartLine: 24, StartColumn: &wantColumn}
	if !reflect.DeepEqual(got.CallSite, want) {
		t.Fatalf("CallSite after DTO mutation = %#v, want unchanged %#v", got.CallSite, want)
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

	got := protocol.NodeFromMethodSymbol(record)

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

	// protocol の DTO の入れ子の値を書き換えても、graph が所有する複製は
	// 変わらないこと。
	record.Metadata["ownerSourceLocation"].(map[string]any)["path"] = "mutated"
	record.Metadata["tags"].([]any)[1].(map[string]any)["nested"] = false
	if !reflect.DeepEqual(got.Symbol.Metadata, want) {
		t.Fatalf("Metadata after DTO mutation = %#v, want unchanged %#v", got.Symbol.Metadata, want)
	}
}

func TestNodeFromMethodSymbolDistinguishesOmittedAndEmptyMetadata(t *testing.T) {
	omitted := protocol.NodeFromMethodSymbol(protocol.MethodSymbol{MethodID: "method:a"})
	if omitted.Symbol.Metadata != nil {
		t.Fatalf("omitted metadata = %#v, want nil", omitted.Symbol.Metadata)
	}

	empty := protocol.NodeFromMethodSymbol(protocol.MethodSymbol{MethodID: "method:a", Metadata: protocol.Metadata{}})
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

	got := protocol.EdgeFromCallEdge(record)

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

	// protocol の DTO の入れ子の値を書き換えても、graph が所有する複製は
	// 変わらないこと。
	record.Metadata["provenance"].(map[string]any)["source"] = "mutated"
	record.Metadata["candidates"].([]any)[1].(map[string]any)["selected"] = false
	if !reflect.DeepEqual(got.Metadata, want) {
		t.Fatalf("Metadata after DTO mutation = %#v, want unchanged %#v", got.Metadata, want)
	}
}

func TestEdgeFromCallEdgeDistinguishesOmittedAndEmptyMetadata(t *testing.T) {
	omitted := protocol.EdgeFromCallEdge(protocol.CallEdge{EdgeID: "edge:ab"})
	if omitted.Metadata != nil {
		t.Fatalf("omitted metadata = %#v, want nil", omitted.Metadata)
	}

	empty := protocol.EdgeFromCallEdge(protocol.CallEdge{EdgeID: "edge:ab", Metadata: protocol.Metadata{}})
	if empty.Metadata == nil || len(empty.Metadata) != 0 {
		t.Fatalf("empty metadata = %#v, want non-nil empty map", empty.Metadata)
	}
}

func TestEdgeFromCallEdgeAllowsNilCallSite(t *testing.T) {
	got := protocol.EdgeFromCallEdge(protocol.CallEdge{EdgeID: "edge:ab"})
	if got.CallSite != nil {
		t.Errorf("EdgeFromCallEdge().CallSite = %#v, want nil", got.CallSite)
	}
}
