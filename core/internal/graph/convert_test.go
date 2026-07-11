package graph

import (
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
	if got != want {
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
	if got != want {
		t.Errorf("EdgeFromCallEdge() = %#v, want %#v", got, want)
	}
}

func TestEdgeFromCallEdgeAllowsNilCallSite(t *testing.T) {
	got := EdgeFromCallEdge(protocol.CallEdge{EdgeID: "edge:ab"})
	if got.CallSite != nil {
		t.Errorf("EdgeFromCallEdge().CallSite = %#v, want nil", got.CallSite)
	}
}
