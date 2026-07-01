package protocol

import (
	"encoding/json"
	"errors"
	"testing"
)

func TestAnalysisRequestValidate(t *testing.T) {
	t.Parallel()

	req := validAnalysisRequest()
	if err := req.Validate(); err != nil {
		t.Fatalf("Validate() error = %v", err)
	}

	req.AnalysisMode = ""
	if got := req.Mode(); got != AnalysisModeFullGraph {
		t.Fatalf("Mode() = %q, want %q", got, AnalysisModeFullGraph)
	}

	if _, err := json.Marshal(req); err != nil {
		t.Fatalf("Marshal() error = %v", err)
	}
}

func TestAnalysisRequestValidateRejectsInvalidFields(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		req  AnalysisRequest
	}{
		{name: "missing request id", req: withAnalysisRequest(func(r *AnalysisRequest) { r.RequestID = "" })},
		{name: "unsupported version", req: withAnalysisRequest(func(r *AnalysisRequest) { r.SchemaVersion = "2" })},
		{name: "invalid record type", req: withAnalysisRequest(func(r *AnalysisRequest) { r.RecordType = RecordTypeMethodSymbol })},
		{name: "invalid language", req: withAnalysisRequest(func(r *AnalysisRequest) { r.Language = "go" })},
		{name: "invalid analysis mode", req: withAnalysisRequest(func(r *AnalysisRequest) { r.AnalysisMode = "partial" })},
		{name: "absolute include path", req: withAnalysisRequest(func(r *AnalysisRequest) { r.Include = []string{"/src/**/*.java"} })},
		{name: "empty exclude path", req: withAnalysisRequest(func(r *AnalysisRequest) { r.Exclude = []string{""} })},
		{name: "parent path segment", req: withAnalysisRequest(func(r *AnalysisRequest) { r.Include = []string{"../src/**/*.java"} })},
		{name: "missing entrypoint name", req: withAnalysisRequest(func(r *AnalysisRequest) { r.Entrypoints = []MethodSelector{{}} })},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			if err := tt.req.Validate(); err == nil {
				t.Fatal("Validate() error = nil, want error")
			}
		})
	}
}

func TestModelRecordsValidate(t *testing.T) {
	t.Parallel()

	startColumn := 2
	endLine := 4
	source := &SourceLocation{
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
				return MethodSymbol{
					SchemaVersion: SchemaVersion,
					RecordType:    RecordTypeMethodSymbol,
					MethodID:      "method:example.App.main",
					Language:      LanguageJava,
					SymbolKind:    SymbolKindMethod,
					QualifiedName: "example.App.main",
					Signature:     "main(java.lang.String[]):void",
					Source:        source,
				}.Validate()
			},
		},
		{
			name: "call edge",
			validate: func() error {
				return CallEdge{
					SchemaVersion:  SchemaVersion,
					RecordType:     RecordTypeCallEdge,
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
				return Diagnostic{
					SchemaVersion:   SchemaVersion,
					RecordType:      RecordTypeDiagnostic,
					Severity:        SeverityWarning,
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
				return AnalyzerError{
					SchemaVersion: SchemaVersion,
					RecordType:    RecordTypeError,
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

func TestModelRecordsValidateRejectsInvalidFields(t *testing.T) {
	t.Parallel()

	zero := 0
	tests := []struct {
		name     string
		validate func() error
	}{
		{
			name: "method symbol missing signature",
			validate: func() error {
				r := validMethodSymbol()
				r.Signature = ""
				return r.Validate()
			},
		},
		{
			name: "method symbol invalid kind",
			validate: func() error {
				r := validMethodSymbol()
				r.SymbolKind = "class"
				return r.Validate()
			},
		},
		{
			name: "call edge missing callee",
			validate: func() error {
				r := validCallEdge()
				r.CalleeMethodID = ""
				return r.Validate()
			},
		},
		{
			name: "diagnostic invalid severity",
			validate: func() error {
				r := validDiagnostic()
				r.Severity = "fatal"
				return r.Validate()
			},
		},
		{
			name: "error missing code",
			validate: func() error {
				r := validAnalyzerError()
				r.Code = ""
				return r.Validate()
			},
		},
		{
			name: "invalid source path",
			validate: func() error {
				r := validMethodSymbol()
				r.Source = &SourceLocation{Path: "/tmp/App.java", StartLine: 1}
				return r.Validate()
			},
		},
		{
			name: "invalid source line",
			validate: func() error {
				r := validMethodSymbol()
				r.Source = &SourceLocation{Path: "src/App.java", StartLine: 0}
				return r.Validate()
			},
		},
		{
			name: "invalid source column",
			validate: func() error {
				r := validMethodSymbol()
				r.Source = &SourceLocation{Path: "src/App.java", StartLine: 1, StartColumn: &zero}
				return r.Validate()
			},
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			var validationError ValidationError
			if err := tt.validate(); !errors.As(err, &validationError) {
				t.Fatalf("Validate() error = %v, want ValidationError", err)
			}
		})
	}
}

func validAnalysisRequest() AnalysisRequest {
	return AnalysisRequest{
		SchemaVersion: SchemaVersion,
		RecordType:    RecordTypeAnalysisRequest,
		RequestID:     "request-1",
		WorkspaceRoot: "/workspace/project",
		Language:      LanguageJava,
		Include:       []string{"src/**/*.java"},
		Exclude:       []string{"build/**"},
		Entrypoints: []MethodSelector{
			{QualifiedName: "example.App.main", Signature: "main(java.lang.String[]):void"},
		},
		AnalysisMode: AnalysisModeReachableFromEntrypoints,
		Metadata:     Metadata{"analyzer": "java"},
	}
}

func withAnalysisRequest(update func(*AnalysisRequest)) AnalysisRequest {
	req := validAnalysisRequest()
	update(&req)
	return req
}

func validMethodSymbol() MethodSymbol {
	return MethodSymbol{
		SchemaVersion: SchemaVersion,
		RecordType:    RecordTypeMethodSymbol,
		MethodID:      "method:example.App.main",
		Language:      LanguageJava,
		SymbolKind:    SymbolKindMethod,
		QualifiedName: "example.App.main",
		Signature:     "main(java.lang.String[]):void",
		Source:        &SourceLocation{Path: "src/App.java", StartLine: 1},
	}
}

func validCallEdge() CallEdge {
	return CallEdge{
		SchemaVersion:  SchemaVersion,
		RecordType:     RecordTypeCallEdge,
		EdgeID:         "edge:1",
		CallerMethodID: "method:caller",
		CalleeMethodID: "method:callee",
	}
}

func validDiagnostic() Diagnostic {
	return Diagnostic{
		SchemaVersion: SchemaVersion,
		RecordType:    RecordTypeDiagnostic,
		Severity:      SeverityInfo,
		Code:          "PARTIAL_ANALYSIS",
		Message:       "analysis was partial",
	}
}

func validAnalyzerError() AnalyzerError {
	return AnalyzerError{
		SchemaVersion: SchemaVersion,
		RecordType:    RecordTypeError,
		Code:          "ANALYZER_FAILED",
		Message:       "analyzer failed",
	}
}
