package protocol

import (
	"encoding/json"
	"errors"
	"reflect"
	"strings"
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
		{name: "unsupported schema version", line: []byte(`{"schemaVersion":"2","recordType":"diagnostic","severity":"info","code":"A","message":"message"}`)},
		{name: "unknown record type", line: []byte(`{"schemaVersion":"1","recordType":"unknown"}`)},
		{name: "type mismatch", line: []byte(`{"schemaVersion":"1","recordType":"diagnostic","severity":"info","code":1,"message":"message"}`)},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			assertParseRecordValidationError(t, tt.line)
		})
	}
}

func TestParseRecordRejectsCaseMismatchedNestedFields(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		line []byte
	}{
		{
			name: "sourceLocation field name must be exact",
			line: []byte(`{"schemaVersion":"1","recordType":"methodSymbol","methodId":"m","language":"java","symbolKind":"method","qualifiedName":"q","signature":"s","sourceLocation":{"Path":"src/App.java","startLine":1}}`),
		},
		{
			name: "entrypoint field name must be exact",
			line: []byte(`{"schemaVersion":"1","recordType":"analysisRequest","requestId":"request-1","workspaceRoot":"/workspace","language":"java","entrypoints":[{"qualifiedname":"example.App.main"}]}`),
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			assertParseRecordValidationError(t, tt.line)
		})
	}
}

func TestParseRecordRejectsNonNormalizedProtocolPaths(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		line []byte
	}{
		{
			name: "Windows drive absolute path with slash separators",
			line: []byte(`{"schemaVersion":"1","recordType":"methodSymbol","methodId":"m","language":"java","symbolKind":"method","qualifiedName":"q","signature":"s","sourceLocation":{"path":"C:/repo/src/App.java","startLine":1}}`),
		},
		{
			name: "Windows drive absolute path with backslash separators",
			line: []byte(`{"schemaVersion":"1","recordType":"methodSymbol","methodId":"m","language":"java","symbolKind":"method","qualifiedName":"q","signature":"s","sourceLocation":{"path":"C:\\repo\\src\\App.java","startLine":1}}`),
		},
		{
			name: "relative path with backslash separators",
			line: []byte(`{"schemaVersion":"1","recordType":"methodSymbol","methodId":"m","language":"java","symbolKind":"method","qualifiedName":"q","signature":"s","sourceLocation":{"path":"src\\App.java","startLine":1}}`),
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			assertParseRecordValidationError(t, tt.line)
		})
	}
}

func TestParseRecordPreservesSourceRootOrder(t *testing.T) {
	t.Parallel()

	line := `{"schemaVersion":"1","recordType":"analysisRequest","requestId":"request-1","workspaceRoot":"/workspace","language":"java","sourceRoots":["module-b/src/main/java","module-a/src/main/java","."]}`
	record, err := ParseRecord([]byte(line))
	if err != nil {
		t.Fatalf("ParseRecord() error = %v", err)
	}
	request, ok := record.(AnalysisRequest)
	if !ok {
		t.Fatalf("ParseRecord() type = %T, want AnalysisRequest", record)
	}
	want := []string{"module-b/src/main/java", "module-a/src/main/java", "."}
	if !reflect.DeepEqual(request.SourceRoots, want) {
		t.Fatalf("SourceRoots = %v, want %v", request.SourceRoots, want)
	}
}

// JSON の明示 null は省略と同義に扱う (nil slice へ decode され自動 discovery
// へ委譲する)。空配列 [] だけを invalid とする境界を意図的な契約として固定する。
func TestParseRecordTreatsNullSourceRootsAsOmitted(t *testing.T) {
	t.Parallel()

	line := `{"schemaVersion":"1","recordType":"analysisRequest","requestId":"request-1","workspaceRoot":"/workspace","language":"java","sourceRoots":null}`
	record, err := ParseRecord([]byte(line))
	if err != nil {
		t.Fatalf("ParseRecord() error = %v, want null to behave as omitted", err)
	}
	request, ok := record.(AnalysisRequest)
	if !ok {
		t.Fatalf("ParseRecord() type = %T, want AnalysisRequest", record)
	}
	if request.SourceRoots != nil {
		t.Fatalf("SourceRoots = %v, want nil (auto discovery route)", request.SourceRoots)
	}
}

func TestParseRecordRejectsInvalidSourceRootsAndDetails(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		line []byte
	}{
		{
			name: "explicit empty sourceRoots",
			line: []byte(`{"schemaVersion":"1","recordType":"analysisRequest","requestId":"request-1","workspaceRoot":"/workspace","language":"java","sourceRoots":[]}`),
		},
		{
			name: "explicit empty details",
			line: []byte(`{"schemaVersion":"1","recordType":"error","code":"JAVA_INCOMPLETE_ANALYSIS","message":"failed","details":[]}`),
		},
		{
			name: "detail unknown field",
			line: []byte(`{"schemaVersion":"1","recordType":"error","code":"JAVA_INCOMPLETE_ANALYSIS","message":"failed","details":[{"code":"C","message":"m","extra":true}]}`),
		},
		{
			name: "detail source location field name must be exact",
			line: []byte(`{"schemaVersion":"1","recordType":"error","code":"JAVA_INCOMPLETE_ANALYSIS","message":"failed","details":[{"code":"C","message":"m","sourceLocation":{"Path":"src/App.java","startLine":1}}]}`),
		},
		{
			name: "detail missing message",
			line: []byte(`{"schemaVersion":"1","recordType":"error","code":"JAVA_INCOMPLETE_ANALYSIS","message":"failed","details":[{"code":"C"}]}`),
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			assertParseRecordValidationError(t, tt.line)
		})
	}
}

func TestParseRecordRoundTripsOpaqueMetadata(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		line string
	}{
		{
			name: "bytecode-only method symbol with owner metadata",
			line: `{"schemaVersion":"1","recordType":"methodSymbol","methodId":"m","language":"java","symbolKind":"method","qualifiedName":"com.example.Owner.builder","signature":"builder():com.example.Owner$Builder","metadata":{"declarationOrigin":"projectClasses","ownerSourceLocation":{"path":"module-a/src/main/java/com/example/Owner.java","startLine":3},"sourceAnchor":null,"tags":["generated",1,true],"issueId":9007199254740993}}`,
		},
		{
			name: "error details with nested metadata in input order",
			line: `{"schemaVersion":"1","recordType":"error","code":"JAVA_INCOMPLETE_ANALYSIS","message":"unresolved calls remain","details":[{"code":"JAVA_UNRESOLVED_SYMBOL","message":"first","sourceLocation":{"path":"module-a/src/App.java","startLine":10},"metadata":{"callKind":"virtual","candidates":[{"qualifiedName":"com.example.A"},null],"reason":"ambiguous"}},{"code":"JAVA_UNRESOLVED_SYMBOL","message":"second"}]}`,
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			record, err := ParseRecord([]byte(tt.line))
			if err != nil {
				t.Fatalf("ParseRecord() error = %v", err)
			}
			marshaled, err := json.Marshal(record)
			if err != nil {
				t.Fatalf("Marshal() error = %v", err)
			}

			var want, got map[string]any
			if err := json.Unmarshal([]byte(tt.line), &want); err != nil {
				t.Fatalf("Unmarshal(input) error = %v", err)
			}
			if err := json.Unmarshal(marshaled, &got); err != nil {
				t.Fatalf("Unmarshal(output) error = %v", err)
			}
			if !reflect.DeepEqual(got, want) {
				t.Fatalf("round trip = %v, want %v", got, want)
			}
			// map-level comparison is blind to float64 rounding, so pin the
			// exact decimal text of an integer beyond float64 precision.
			if strings.Contains(tt.line, "9007199254740993") && !strings.Contains(string(marshaled), "9007199254740993") {
				t.Fatalf("round trip lost integer precision: %s", marshaled)
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

func assertParseRecordValidationError(t *testing.T, line []byte) {
	t.Helper()

	var validationError ValidationError
	if _, err := ParseRecord(line); !errors.As(err, &validationError) {
		t.Fatalf("ParseRecord() error = %v, want ValidationError", err)
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
