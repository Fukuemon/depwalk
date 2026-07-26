package protocol_test

import (
	"encoding/json"
	"errors"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

func TestAnalysisRequestValidate(t *testing.T) {
	t.Parallel()

	req := validAnalysisRequest()
	if err := req.Validate(); err != nil {
		t.Fatalf("Validate() error = %v", err)
	}

	req.AnalysisMode = ""
	if got := req.Mode(); got != protocol.AnalysisModeFullGraph {
		t.Fatalf("Mode() = %q, want %q", got, protocol.AnalysisModeFullGraph)
	}

	if _, err := json.Marshal(req); err != nil {
		t.Fatalf("Marshal() error = %v", err)
	}
}

func TestAnalysisRequestValidateRejectsInvalidHeader(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		req  protocol.AnalysisRequest
	}{
		{name: "missing request id", req: withAnalysisRequest(func(r *protocol.AnalysisRequest) { r.RequestID = "" })},
		{name: "unsupported version", req: withAnalysisRequest(func(r *protocol.AnalysisRequest) { r.SchemaVersion = "2" })},
		{name: "invalid record type", req: withAnalysisRequest(func(r *protocol.AnalysisRequest) { r.RecordType = protocol.RecordTypeMethodSymbol })},
		{name: "invalid language", req: withAnalysisRequest(func(r *protocol.AnalysisRequest) { r.Language = "go" })},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			assertValidateError(t, tt.req.Validate)
		})
	}
}

func TestAnalysisRequestValidateRejectsInvalidAnalysisMode(t *testing.T) {
	t.Parallel()

	req := withAnalysisRequest(func(r *protocol.AnalysisRequest) {
		r.AnalysisMode = "partial"
	})

	assertValidateError(t, req.Validate)
}

func TestAnalysisRequestValidateRejectsNonRelativeScopePaths(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		req  protocol.AnalysisRequest
	}{
		{name: "absolute include path", req: withAnalysisRequest(func(r *protocol.AnalysisRequest) { r.Include = []string{"/src/**/*.java"} })},
		{name: "empty exclude path", req: withAnalysisRequest(func(r *protocol.AnalysisRequest) { r.Exclude = []string{""} })},
		{name: "parent path segment", req: withAnalysisRequest(func(r *protocol.AnalysisRequest) { r.Include = []string{"../src/**/*.java"} })},
		{name: "Windows drive include path", req: withAnalysisRequest(func(r *protocol.AnalysisRequest) { r.Include = []string{"C:/repo/src/**/*.java"} })},
		{name: "backslash include path", req: withAnalysisRequest(func(r *protocol.AnalysisRequest) { r.Include = []string{`src\**\*.java`} })},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			assertValidateError(t, tt.req.Validate)
		})
	}
}

func TestAnalysisRequestValidateAcceptsSourceRoots(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name  string
		roots []string
	}{
		{name: "omitted", roots: nil},
		{name: "single root", roots: []string{"src/main/java"}},
		{name: "multiple roots", roots: []string{"module-a/src/main/java", "module-b/src/main/java"}},
		{name: "workspace itself", roots: []string{"."}},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			req := withAnalysisRequest(func(r *protocol.AnalysisRequest) { r.SourceRoots = tt.roots })
			if err := req.Validate(); err != nil {
				t.Fatalf("Validate() error = %v", err)
			}
		})
	}
}

func TestAnalysisRequestValidateRejectsInvalidSourceRoots(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name  string
		roots []string
	}{
		{name: "explicit empty array", roots: []string{}},
		{name: "absolute path", roots: []string{"/workspace/src"}},
		{name: "empty element", roots: []string{""}},
		{name: "backslash separators", roots: []string{`module-a\src\main\java`}},
		{name: "parent segment", roots: []string{"../other/src"}},
		{name: "Windows drive path", roots: []string{"C:/repo/src"}},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			req := withAnalysisRequest(func(r *protocol.AnalysisRequest) { r.SourceRoots = tt.roots })
			assertValidateError(t, req.Validate)
		})
	}
}

func TestAnalyzerErrorValidateAcceptsFailureDetails(t *testing.T) {
	t.Parallel()

	err := validAnalyzerError()
	err.Details = []protocol.FailureDetail{
		{
			Code:    "JAVA_UNRESOLVED_SYMBOL",
			Message: "could not resolve call target",
			Source:  &protocol.SourceLocation{Path: "module-a/src/App.java", StartLine: 10},
			Metadata: protocol.Metadata{
				"callKind":   "virtual",
				"candidates": []any{"com.example.A", "com.example.B"},
				"unknownKey": nil,
			},
		},
		{Code: "JAVA_UNRESOLVED_SYMBOL", Message: "another unresolved call"},
	}
	if got := err.Validate(); got != nil {
		t.Fatalf("Validate() error = %v", got)
	}
}

func TestAnalyzerErrorValidateRejectsInvalidFailureDetails(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		details []protocol.FailureDetail
	}{
		{name: "explicit empty details", details: []protocol.FailureDetail{}},
		{name: "missing code", details: []protocol.FailureDetail{{Message: "message"}}},
		{name: "missing message", details: []protocol.FailureDetail{{Code: "CODE"}}},
		{
			name: "invalid source location",
			details: []protocol.FailureDetail{
				{Code: "CODE", Message: "message", Source: &protocol.SourceLocation{Path: "/abs/App.java", StartLine: 1}},
			},
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			err := validAnalyzerError()
			err.Details = tt.details
			assertValidateError(t, err.Validate)
		})
	}
}

func TestMethodSymbolValidateAcceptsBytecodeOnlySymbol(t *testing.T) {
	t.Parallel()

	r := validMethodSymbol()
	r.Source = nil
	r.Metadata = protocol.Metadata{
		"declarationOrigin": "projectClasses",
		"ownerSourceLocation": map[string]any{
			"path":      "module-a/src/main/java/com/example/Owner.java",
			"startLine": float64(3),
		},
	}
	if err := r.Validate(); err != nil {
		t.Fatalf("Validate() error = %v", err)
	}
}

func TestAnalysisRequestValidateRejectsEntrypointsWithoutQualifiedName(t *testing.T) {
	t.Parallel()

	req := withAnalysisRequest(func(r *protocol.AnalysisRequest) {
		r.Entrypoints = []protocol.MethodSelector{{}}
	})

	assertValidateError(t, req.Validate)
}

func TestModelRecordsValidateRejectsInvalidRecordFields(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		validate func() error
	}{
		{
			name: "methodSymbol signature is required",
			validate: func() error {
				r := validMethodSymbol()
				r.Signature = ""
				return r.Validate()
			},
		},
		{
			name: "methodSymbol kind must be callable",
			validate: func() error {
				r := validMethodSymbol()
				r.SymbolKind = "class"
				return r.Validate()
			},
		},
		{
			name: "callEdge calleeMethodId is required",
			validate: func() error {
				r := validCallEdge()
				r.CalleeMethodID = ""
				return r.Validate()
			},
		},
		{
			name: "diagnostic severity must be non-fatal",
			validate: func() error {
				r := validDiagnostic()
				r.Severity = "fatal"
				return r.Validate()
			},
		},
		{
			name: "error code is required",
			validate: func() error {
				r := validAnalyzerError()
				r.Code = ""
				return r.Validate()
			},
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			assertValidateError(t, tt.validate)
		})
	}
}

func TestModelRecordsValidateRejectsInvalidSourceLocations(t *testing.T) {
	t.Parallel()

	zero := 0
	tests := []struct {
		name     string
		validate func() error
	}{
		{
			name: "source path must be workspace-relative",
			validate: func() error {
				r := validMethodSymbol()
				r.Source = &protocol.SourceLocation{Path: "/tmp/App.java", StartLine: 1}
				return r.Validate()
			},
		},
		{
			name: "source path must use slash separators",
			validate: func() error {
				r := validMethodSymbol()
				r.Source = &protocol.SourceLocation{Path: `src\App.java`, StartLine: 1}
				return r.Validate()
			},
		},
		{
			name: "source startLine must be positive",
			validate: func() error {
				r := validMethodSymbol()
				r.Source = &protocol.SourceLocation{Path: "src/App.java", StartLine: 0}
				return r.Validate()
			},
		},
		{
			name: "source startColumn must be positive",
			validate: func() error {
				r := validMethodSymbol()
				r.Source = &protocol.SourceLocation{Path: "src/App.java", StartLine: 1, StartColumn: &zero}
				return r.Validate()
			},
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			assertValidateError(t, tt.validate)
		})
	}
}

func TestModelRecordsValidate(t *testing.T) {
	t.Parallel()

	startColumn := 2
	endLine := 4
	source := &protocol.SourceLocation{
		Path:        "src/main/java/example/App.java",
		StartLine:   3,
		StartColumn: &startColumn,
		EndLine:     &endLine,
	}

	records := []struct {
		name     string
		validate func() error
	}{
		{
			name: "method symbol",
			validate: func() error {
				return protocol.MethodSymbol{
					SchemaVersion: protocol.SchemaVersion,
					RecordType:    protocol.RecordTypeMethodSymbol,
					MethodID:      "method:example.App.main",
					Language:      protocol.LanguageJava,
					SymbolKind:    protocol.SymbolKindMethod,
					QualifiedName: "example.App.main",
					Signature:     "main(java.lang.String[]):void",
					Source:        source,
				}.Validate()
			},
		},
		{
			name: "call edge",
			validate: func() error {
				return protocol.CallEdge{
					SchemaVersion:  protocol.SchemaVersion,
					RecordType:     protocol.RecordTypeCallEdge,
					EdgeID:         "edge:1",
					CallerMethodID: "method:caller",
					CalleeMethodID: "method:callee",
					CallSite:       source,
				}.Validate()
			},
		},
		{
			name: "diagnostic",
			validate: func() error {
				return protocol.Diagnostic{
					SchemaVersion:   protocol.SchemaVersion,
					RecordType:      protocol.RecordTypeDiagnostic,
					Severity:        protocol.SeverityWarning,
					Code:            "UNRESOLVED_SYMBOL",
					Message:         "symbol could not be resolved",
					Source:          source,
					RelatedMethodID: "method:caller",
				}.Validate()
			},
		},
		{
			name: "error",
			validate: func() error {
				return protocol.AnalyzerError{
					SchemaVersion: protocol.SchemaVersion,
					RecordType:    protocol.RecordTypeError,
					Code:          "ANALYZER_FAILED",
					Message:       "analyzer failed",
					Source:        source,
				}.Validate()
			},
		},
	}

	for _, record := range records {
		record := record
		t.Run(record.name, func(t *testing.T) {
			t.Parallel()

			if err := record.validate(); err != nil {
				t.Fatalf("Validate() error = %v", err)
			}
		})
	}
}

func assertValidateError(t *testing.T, validate func() error) {
	t.Helper()

	var validationError protocol.ValidationError
	if err := validate(); !errors.As(err, &validationError) {
		t.Fatalf("Validate() error = %v, want ValidationError", err)
	}
}

func validAnalysisRequest() protocol.AnalysisRequest {
	return protocol.AnalysisRequest{
		SchemaVersion: protocol.SchemaVersion,
		RecordType:    protocol.RecordTypeAnalysisRequest,
		RequestID:     "request-1",
		WorkspaceRoot: "/workspace/project",
		Language:      protocol.LanguageJava,
		Include:       []string{"src/**/*.java"},
		Exclude:       []string{"build/**"},
		Entrypoints: []protocol.MethodSelector{
			{QualifiedName: "example.App.main", Signature: "main(java.lang.String[]):void"},
		},
		AnalysisMode: protocol.AnalysisModeReachableFromEntrypoints,
		Metadata:     protocol.Metadata{"analyzer": "java"},
	}
}

func withAnalysisRequest(update func(*protocol.AnalysisRequest)) protocol.AnalysisRequest {
	req := validAnalysisRequest()
	update(&req)
	return req
}

func validMethodSymbol() protocol.MethodSymbol {
	return protocol.MethodSymbol{
		SchemaVersion: protocol.SchemaVersion,
		RecordType:    protocol.RecordTypeMethodSymbol,
		MethodID:      "method:example.App.main",
		Language:      protocol.LanguageJava,
		SymbolKind:    protocol.SymbolKindMethod,
		QualifiedName: "example.App.main",
		Signature:     "main(java.lang.String[]):void",
		Source:        &protocol.SourceLocation{Path: "src/App.java", StartLine: 1},
	}
}

func validCallEdge() protocol.CallEdge {
	return protocol.CallEdge{
		SchemaVersion:  protocol.SchemaVersion,
		RecordType:     protocol.RecordTypeCallEdge,
		EdgeID:         "edge:1",
		CallerMethodID: "method:caller",
		CalleeMethodID: "method:callee",
	}
}

func validDiagnostic() protocol.Diagnostic {
	return protocol.Diagnostic{
		SchemaVersion: protocol.SchemaVersion,
		RecordType:    protocol.RecordTypeDiagnostic,
		Severity:      protocol.SeverityInfo,
		Code:          "PARTIAL_ANALYSIS",
		Message:       "analysis was partial",
	}
}

func validAnalyzerError() protocol.AnalyzerError {
	return protocol.AnalyzerError{
		SchemaVersion: protocol.SchemaVersion,
		RecordType:    protocol.RecordTypeError,
		Code:          "ANALYZER_FAILED",
		Message:       "analyzer failed",
	}
}
