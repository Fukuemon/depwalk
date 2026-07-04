package protocol

import (
	"errors"
	"testing"
)

func TestParseRecord(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		line string
		want Record
	}{
		{
			name: "analysis request",
			line: `{"schemaVersion":"1","recordType":"analysisRequest","requestId":"request-1","workspaceRoot":"/workspace","language":"java","include":["src/**/*.java"],"entrypoints":[{"qualifiedName":"example.App.main"}],"unknown":"ignored"}`,
			want: AnalysisRequest{},
		},
		{
			name: "method symbol",
			line: `{"schemaVersion":"1","recordType":"methodSymbol","methodId":"method:main","language":"java","symbolKind":"method","qualifiedName":"example.App.main","signature":"main():void","sourceLocation":{"path":"src/App.java","startLine":1}}`,
			want: MethodSymbol{},
		},
		{
			name: "call edge",
			line: `{"schemaVersion":"1","recordType":"callEdge","edgeId":"edge:1","callerMethodId":"method:caller","calleeMethodId":"method:callee"}`,
			want: CallEdge{},
		},
		{
			name: "diagnostic",
			line: `{"schemaVersion":"1","recordType":"diagnostic","severity":"warning","code":"UNRESOLVED","message":"unresolved symbol"}`,
			want: Diagnostic{},
		},
		{
			name: "error",
			line: `{"schemaVersion":"1","recordType":"error","code":"ANALYZER_FAILED","message":"failed"}`,
			want: AnalyzerError{},
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			got, err := ParseRecord([]byte(tt.line))
			if err != nil {
				t.Fatalf("ParseRecord() error = %v", err)
			}
			if gotType, wantType := recordTypeName(got), recordTypeName(tt.want); gotType != wantType {
				t.Fatalf("ParseRecord() type = %s, want %s", gotType, wantType)
			}
		})
	}
}

func TestParseRecordRejectsInvalidJSONL(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		line []byte
	}{
		{name: "empty line", line: []byte("")},
		{name: "invalid JSON", line: []byte(`{"schemaVersion":"1"`)},
		{name: "multiple JSON values", line: []byte(`{"schemaVersion":"1"} {"recordType":"analysisRequest"}`)},
		{name: "duplicate key", line: []byte(`{"schemaVersion":"1","recordType":"diagnostic","severity":"info","code":"A","message":"one","message":"two"}`)},
		{name: "nested duplicate key", line: []byte(`{"schemaVersion":"1","recordType":"methodSymbol","methodId":"m","language":"java","symbolKind":"method","qualifiedName":"q","signature":"s","metadata":{"hint":1,"hint":2}}`)},
		{name: "invalid UTF-8", line: []byte{'{', '"', 's', 'c', 'h', 'e', 'm', 'a', 'V', 'e', 'r', 's', 'i', 'o', 'n', '"', ':', '"', 0xff, '"', '}'}},
		{name: "case variant field name", line: []byte(`{"schemaversion":"1","recordType":"diagnostic","severity":"info","code":"A","message":"message"}`)},
		{name: "case variant source location field name", line: []byte(`{"schemaVersion":"1","recordType":"methodSymbol","methodId":"m","language":"java","symbolKind":"method","qualifiedName":"q","signature":"s","sourceLocation":{"Path":"src/App.java","startLine":1}}`)},
		{name: "case variant entrypoint field name", line: []byte(`{"schemaVersion":"1","recordType":"analysisRequest","requestId":"request-1","workspaceRoot":"/workspace","language":"java","entrypoints":[{"qualifiedname":"example.App.main"}]}`)},
		{name: "unsupported schema version", line: []byte(`{"schemaVersion":"2","recordType":"diagnostic","severity":"info","code":"A","message":"message"}`)},
		{name: "unknown record type", line: []byte(`{"schemaVersion":"1","recordType":"unknown"}`)},
		{name: "type mismatch", line: []byte(`{"schemaVersion":"1","recordType":"diagnostic","severity":"info","code":1,"message":"message"}`)},
		{name: "windows drive source path", line: []byte(`{"schemaVersion":"1","recordType":"methodSymbol","methodId":"m","language":"java","symbolKind":"method","qualifiedName":"q","signature":"s","sourceLocation":{"path":"C:/repo/src/App.java","startLine":1}}`)},
		{name: "windows drive source path with backslash", line: []byte(`{"schemaVersion":"1","recordType":"methodSymbol","methodId":"m","language":"java","symbolKind":"method","qualifiedName":"q","signature":"s","sourceLocation":{"path":"C:\\repo\\src\\App.java","startLine":1}}`)},
		{name: "backslash source path", line: []byte(`{"schemaVersion":"1","recordType":"methodSymbol","methodId":"m","language":"java","symbolKind":"method","qualifiedName":"q","signature":"s","sourceLocation":{"path":"src\\App.java","startLine":1}}`)},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			var validationError ValidationError
			if _, err := ParseRecord(tt.line); !errors.As(err, &validationError) {
				t.Fatalf("ParseRecord() error = %v, want ValidationError", err)
			}
		})
	}
}

func TestParseRecordAcceptsTrailingNewline(t *testing.T) {
	t.Parallel()

	line := []byte("{\"schemaVersion\":\"1\",\"recordType\":\"diagnostic\",\"severity\":\"info\",\"code\":\"A\",\"message\":\"message\"}\n")
	if _, err := ParseRecord(line); err != nil {
		t.Fatalf("ParseRecord() error = %v", err)
	}
}

func recordTypeName(record Record) string {
	switch record.(type) {
	case AnalysisRequest:
		return "AnalysisRequest"
	case MethodSymbol:
		return "MethodSymbol"
	case CallEdge:
		return "CallEdge"
	case Diagnostic:
		return "Diagnostic"
	case AnalyzerError:
		return "AnalyzerError"
	default:
		return "unknown"
	}
}
