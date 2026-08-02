package protocol

// SchemaVersion is the Phase 1 Analyzer Protocol version.
const SchemaVersion = "1"

// RecordType identifies the kind of protocol record.
type RecordType string

const (
	// RecordTypeAnalysisRequest is the Core-to-Analyzer analysis request.
	RecordTypeAnalysisRequest RecordType = "analysisRequest"
	// RecordTypeMethodSymbol is an Analyzer-to-Core method symbol record.
	RecordTypeMethodSymbol RecordType = "methodSymbol"
	// RecordTypeCallEdge is an Analyzer-to-Core call edge record.
	RecordTypeCallEdge RecordType = "callEdge"
	// RecordTypeDiagnostic is an Analyzer-to-Core non-fatal diagnostic record.
	RecordTypeDiagnostic RecordType = "diagnostic"
	// RecordTypeError is an Analyzer-to-Core fatal error record.
	RecordTypeError RecordType = "error"
)

// Language は Analyzer が扱うソース言語を識別する。
type Language string

const (
	// LanguageJava is the Phase 1 Analyzer language.
	LanguageJava Language = "java"
)

// AnalysisMode controls the requested call graph scope.
type AnalysisMode string

const (
	// AnalysisModeFullGraph requests a graph for the whole analysis scope.
	AnalysisModeFullGraph AnalysisMode = "fullGraph"
	// AnalysisModeReachableFromEntrypoints requests a graph reachable from entrypoints.
	AnalysisModeReachableFromEntrypoints AnalysisMode = "reachableFromEntrypoints"
)

// SymbolKind identifies the kind of callable symbol.
type SymbolKind string

const (
	// SymbolKindMethod identifies a method.
	SymbolKindMethod SymbolKind = "method"
	// SymbolKindConstructor identifies a constructor.
	SymbolKindConstructor SymbolKind = "constructor"
	// SymbolKindFunction identifies a function.
	SymbolKindFunction SymbolKind = "function"
	// SymbolKindInitializer identifies an initializer.
	SymbolKindInitializer SymbolKind = "initializer"
)

// Severity は diagnostic record の深刻度を識別する。
type Severity string

const (
	// SeverityInfo is informational.
	SeverityInfo Severity = "info"
	// SeverityWarning is a warning.
	SeverityWarning Severity = "warning"
	// SeverityPartialFailure indicates a non-fatal partial analysis failure.
	SeverityPartialFailure Severity = "partialFailure"
)

// Metadata は言語固有・Analyzer 固有のヒントを運ぶ。Core は解釈しない。
type Metadata map[string]any

// Record は [ParseRecord] が返す Analyzer Protocol の record。
type Record interface {
	Validate() error
	record()
}

// AnalysisRequest is the Core-to-Analyzer request record.
type AnalysisRequest struct {
	SchemaVersion string           `json:"schemaVersion"`
	RecordType    RecordType       `json:"recordType"`
	RequestID     string           `json:"requestId"`
	WorkspaceRoot string           `json:"workspaceRoot"`
	SourceRoots   []string         `json:"sourceRoots,omitempty"`
	Language      Language         `json:"language"`
	Include       []string         `json:"include,omitempty"`
	Exclude       []string         `json:"exclude,omitempty"`
	Entrypoints   []MethodSelector `json:"entrypoints,omitempty"`
	AnalysisMode  AnalysisMode     `json:"analysisMode,omitempty"`
	Metadata      Metadata         `json:"metadata,omitempty"`
}

func (AnalysisRequest) record() {}

// Mode は実効の [AnalysisMode] を返す。
func (r AnalysisRequest) Mode() AnalysisMode {
	if r.AnalysisMode == "" {
		return AnalysisModeFullGraph
	}
	return r.AnalysisMode
}

// MethodSelector identifies an entrypoint method.
type MethodSelector struct {
	QualifiedName string `json:"qualifiedName"`
	Signature     string `json:"signature,omitempty"`
}

// MethodSymbol is an Analyzer-to-Core graph node record.
type MethodSymbol struct {
	SchemaVersion string          `json:"schemaVersion"`
	RecordType    RecordType      `json:"recordType"`
	MethodID      string          `json:"methodId"`
	Language      Language        `json:"language"`
	SymbolKind    SymbolKind      `json:"symbolKind"`
	QualifiedName string          `json:"qualifiedName"`
	Signature     string          `json:"signature"`
	Source        *SourceLocation `json:"sourceLocation,omitempty"`
	Metadata      Metadata        `json:"metadata,omitempty"`
}

func (MethodSymbol) record() {}

// CallEdge is an Analyzer-to-Core graph edge record.
type CallEdge struct {
	SchemaVersion  string          `json:"schemaVersion"`
	RecordType     RecordType      `json:"recordType"`
	EdgeID         string          `json:"edgeId"`
	CallerMethodID string          `json:"callerMethodId"`
	CalleeMethodID string          `json:"calleeMethodId"`
	CallSite       *SourceLocation `json:"callSite,omitempty"`
	Metadata       Metadata        `json:"metadata,omitempty"`
}

func (CallEdge) record() {}

// SourceLocation identifies a source range relative to workspaceRoot.
type SourceLocation struct {
	Path        string `json:"path"`
	StartLine   int    `json:"startLine"`
	StartColumn *int   `json:"startColumn,omitempty"`
	EndLine     *int   `json:"endLine,omitempty"`
	EndColumn   *int   `json:"endColumn,omitempty"`
}

// Diagnostic は Analyzer から Core への、致命的でない診断 record。
type Diagnostic struct {
	SchemaVersion   string          `json:"schemaVersion"`
	RecordType      RecordType      `json:"recordType"`
	Severity        Severity        `json:"severity"`
	Code            string          `json:"code"`
	Message         string          `json:"message"`
	Source          *SourceLocation `json:"sourceLocation,omitempty"`
	RelatedMethodID string          `json:"relatedMethodId,omitempty"`
	Metadata        Metadata        `json:"metadata,omitempty"`
}

func (Diagnostic) record() {}

// AnalyzerError is an Analyzer-to-Core fatal error record.
type AnalyzerError struct {
	SchemaVersion string          `json:"schemaVersion"`
	RecordType    RecordType      `json:"recordType"`
	Code          string          `json:"code"`
	Message       string          `json:"message"`
	Source        *SourceLocation `json:"sourceLocation,omitempty"`
	Metadata      Metadata        `json:"metadata,omitempty"`
	Details       []FailureDetail `json:"details,omitempty"`
}

// FailureDetail is one language-agnostic structured detail of a fatal error.
// Core consumers validate only the common fields and treat metadata as opaque.
type FailureDetail struct {
	Code     string          `json:"code"`
	Message  string          `json:"message"`
	Source   *SourceLocation `json:"sourceLocation,omitempty"`
	Metadata Metadata        `json:"metadata,omitempty"`
}

func (AnalyzerError) record() {}
