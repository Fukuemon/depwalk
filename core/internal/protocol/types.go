package protocol

// SchemaVersion は現行の Analyzer Protocol の版。
const SchemaVersion = "1"

// RecordType は protocol record の種別を識別する。
type RecordType string

const (
	// RecordTypeAnalysisRequest は Core から Analyzer への解析要求。
	RecordTypeAnalysisRequest RecordType = "analysisRequest"
	// RecordTypeMethodSymbol は Analyzer から Core への method symbol record。
	RecordTypeMethodSymbol RecordType = "methodSymbol"
	// RecordTypeCallEdge は Analyzer から Core への call edge record。
	RecordTypeCallEdge RecordType = "callEdge"
	// RecordTypeDiagnostic は Analyzer から Core への、致命的でない診断 record。
	RecordTypeDiagnostic RecordType = "diagnostic"
	// RecordTypeError は Analyzer から Core への致命的な error record。
	RecordTypeError RecordType = "error"
)

// Language は Analyzer が扱うソース言語を識別する。
type Language string

const (
	// LanguageJava は現行で唯一対応する言語。
	LanguageJava Language = "java"
)

// AnalysisMode は要求する呼び出しグラフの範囲を指定する。
type AnalysisMode string

const (
	// AnalysisModeFullGraph は解析 scope 全体の graph を要求する。
	AnalysisModeFullGraph AnalysisMode = "fullGraph"
	// AnalysisModeReachableFromEntrypoints は entrypoints から到達可能な graph を要求する。
	AnalysisModeReachableFromEntrypoints AnalysisMode = "reachableFromEntrypoints"
)

// SymbolKind は呼び出せる symbol の種別を識別する。
type SymbolKind string

const (
	SymbolKindMethod      SymbolKind = "method"
	SymbolKindConstructor SymbolKind = "constructor"
	SymbolKindFunction    SymbolKind = "function"
	SymbolKindInitializer SymbolKind = "initializer"
)

// Severity は diagnostic record の深刻度を識別する。
type Severity string

const (
	SeverityInfo    Severity = "info"
	SeverityWarning Severity = "warning"
	// SeverityPartialFailure は致命的でない部分的な解析失敗を表す。
	SeverityPartialFailure Severity = "partialFailure"
)

// Metadata は言語固有・Analyzer 固有のヒントを運ぶ。Core は解釈しない。
type Metadata map[string]any

// Record は [ParseRecord] が返す Analyzer Protocol の record。
type Record interface {
	Validate() error
	record()
}

// AnalysisRequest は Core から Analyzer への要求 record。
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

// MethodSelector は entrypoint のメソッドを指定する。
type MethodSelector struct {
	QualifiedName string `json:"qualifiedName"`
	Signature     string `json:"signature,omitempty"`
}

// MethodSymbol は Analyzer から Core への graph node record。
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

// CallEdge は Analyzer から Core への graph edge record。
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

// SourceLocation は workspaceRoot からの相対でソース範囲を表す。
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

// AnalyzerError は Analyzer から Core への致命的な error record。
type AnalyzerError struct {
	SchemaVersion string          `json:"schemaVersion"`
	RecordType    RecordType      `json:"recordType"`
	Code          string          `json:"code"`
	Message       string          `json:"message"`
	Source        *SourceLocation `json:"sourceLocation,omitempty"`
	Metadata      Metadata        `json:"metadata,omitempty"`
	Details       []FailureDetail `json:"details,omitempty"`
}

// FailureDetail は致命的な失敗の、言語に依存しない構造化された明細 1 件。
// Core は共通 field だけを検証し、metadata は opaque として扱う。
type FailureDetail struct {
	Code     string          `json:"code"`
	Message  string          `json:"message"`
	Source   *SourceLocation `json:"sourceLocation,omitempty"`
	Metadata Metadata        `json:"metadata,omitempty"`
}

func (AnalyzerError) record() {}
