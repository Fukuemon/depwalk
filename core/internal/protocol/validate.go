package protocol

import (
	"fmt"
	"path/filepath"
	"strings"
)

// ValidationError は protocol schema の検証失敗を表す。
type ValidationError struct {
	Field  string
	Reason string
}

func (e ValidationError) Error() string {
	if e.Field == "" {
		return e.Reason
	}
	return fmt.Sprintf("%s: %s", e.Field, e.Reason)
}

// Validate は [AnalysisRequest] record を検証する。
func (r AnalysisRequest) Validate() error {
	if err := validateRecordHeader(r.SchemaVersion, r.RecordType, RecordTypeAnalysisRequest); err != nil {
		return err
	}
	if err := require("requestId", r.RequestID); err != nil {
		return err
	}
	if err := require("workspaceRoot", r.WorkspaceRoot); err != nil {
		return err
	}
	if err := rejectExplicitEmptyArray("sourceRoots", r.SourceRoots); err != nil {
		return err
	}
	for i, root := range r.SourceRoots {
		if err := validateRelativePath(fmt.Sprintf("sourceRoots[%d]", i), root); err != nil {
			return err
		}
	}
	if r.Language != LanguageJava {
		return invalid("language", "must be java")
	}
	if r.Mode() != AnalysisModeFullGraph && r.Mode() != AnalysisModeReachableFromEntrypoints {
		return invalid("analysisMode", "must be fullGraph or reachableFromEntrypoints")
	}
	for i, pattern := range r.Include {
		if err := validateRelativePath(fmt.Sprintf("include[%d]", i), pattern); err != nil {
			return err
		}
	}
	for i, pattern := range r.Exclude {
		if err := validateRelativePath(fmt.Sprintf("exclude[%d]", i), pattern); err != nil {
			return err
		}
	}
	for i, entrypoint := range r.Entrypoints {
		if err := entrypoint.validate(fmt.Sprintf("entrypoints[%d]", i)); err != nil {
			return err
		}
	}
	return nil
}

func (s MethodSelector) validate(field string) error {
	if s.QualifiedName == "" {
		return invalid(field+".qualifiedName", "is required")
	}
	return nil
}

// Validate は [MethodSymbol] record を検証する。
func (r MethodSymbol) Validate() error {
	if err := validateRecordHeader(r.SchemaVersion, r.RecordType, RecordTypeMethodSymbol); err != nil {
		return err
	}
	if err := require("methodId", r.MethodID); err != nil {
		return err
	}
	if r.Language != LanguageJava {
		return invalid("language", "must be java")
	}
	switch r.SymbolKind {
	case SymbolKindMethod, SymbolKindConstructor, SymbolKindFunction, SymbolKindInitializer:
	default:
		return invalid("symbolKind", "must be method, constructor, function, or initializer")
	}
	if err := require("qualifiedName", r.QualifiedName); err != nil {
		return err
	}
	if err := require("signature", r.Signature); err != nil {
		return err
	}
	if r.Source != nil {
		if err := r.Source.validate("sourceLocation"); err != nil {
			return err
		}
	}
	return nil
}

// Validate は [CallEdge] record を検証する。
func (r CallEdge) Validate() error {
	if err := validateRecordHeader(r.SchemaVersion, r.RecordType, RecordTypeCallEdge); err != nil {
		return err
	}
	if err := require("edgeId", r.EdgeID); err != nil {
		return err
	}
	if err := require("callerMethodId", r.CallerMethodID); err != nil {
		return err
	}
	if err := require("calleeMethodId", r.CalleeMethodID); err != nil {
		return err
	}
	if r.CallSite != nil {
		if err := r.CallSite.validate("callSite"); err != nil {
			return err
		}
	}
	return nil
}

// Validate は [Diagnostic] record を検証する。
func (r Diagnostic) Validate() error {
	if err := validateRecordHeader(r.SchemaVersion, r.RecordType, RecordTypeDiagnostic); err != nil {
		return err
	}
	switch r.Severity {
	case SeverityInfo, SeverityWarning, SeverityPartialFailure:
	default:
		return invalid("severity", "must be info, warning, or partialFailure")
	}
	if err := require("code", r.Code); err != nil {
		return err
	}
	if err := require("message", r.Message); err != nil {
		return err
	}
	if r.Source != nil {
		if err := r.Source.validate("sourceLocation"); err != nil {
			return err
		}
	}
	return nil
}

// Validate は [AnalyzerError] record を検証する。
func (r AnalyzerError) Validate() error {
	if err := validateRecordHeader(r.SchemaVersion, r.RecordType, RecordTypeError); err != nil {
		return err
	}
	if err := require("code", r.Code); err != nil {
		return err
	}
	if err := require("message", r.Message); err != nil {
		return err
	}
	if r.Source != nil {
		if err := r.Source.validate("sourceLocation"); err != nil {
			return err
		}
	}
	if err := rejectExplicitEmptyArray("details", r.Details); err != nil {
		return err
	}
	for i, detail := range r.Details {
		if err := detail.validate(fmt.Sprintf("details[%d]", i)); err != nil {
			return err
		}
	}
	return nil
}

func (d FailureDetail) validate(field string) error {
	if err := require(field+".code", d.Code); err != nil {
		return err
	}
	if err := require(field+".message", d.Message); err != nil {
		return err
	}
	if d.Source != nil {
		if err := d.Source.validate(field + ".sourceLocation"); err != nil {
			return err
		}
	}
	return nil
}

func (l SourceLocation) validate(field string) error {
	if err := validateRelativePath(field+".path", l.Path); err != nil {
		return err
	}
	if l.StartLine < 1 {
		return invalid(field+".startLine", "must be 1 or greater")
	}
	if l.StartColumn != nil && *l.StartColumn < 1 {
		return invalid(field+".startColumn", "must be 1 or greater")
	}
	if l.EndLine != nil && *l.EndLine < 1 {
		return invalid(field+".endLine", "must be 1 or greater")
	}
	if l.EndColumn != nil && *l.EndColumn < 1 {
		return invalid(field+".endColumn", "must be 1 or greater")
	}
	return nil
}

func validateRecordHeader(schemaVersion string, recordType, want RecordType) error {
	if schemaVersion != SchemaVersion {
		return invalid("schemaVersion", "must be 1")
	}
	if recordType != want {
		return invalid("recordType", fmt.Sprintf("must be %s", want))
	}
	return nil
}

// rejectExplicitEmptyArray は「存在するが空」の optional 配列を拒否し、
// 省略 (nil) は許す。空配列は「対象なし」を意図した指定と解釈できず曖昧なため。
func rejectExplicitEmptyArray[T any](field string, values []T) error {
	if values != nil && len(values) == 0 {
		return invalid(field, "must not be an explicit empty array")
	}
	return nil
}

func require(field, value string) error {
	if value == "" {
		return invalid(field, "is required")
	}
	return nil
}

func validateRelativePath(field, value string) error {
	if value == "" {
		return invalid(field, "is required")
	}
	path := strings.ReplaceAll(value, "\\", "/")
	if strings.HasPrefix(path, "/") || filepath.IsAbs(value) || isWindowsAbsolutePath(value) {
		return invalid(field, "must be relative")
	}
	if strings.Contains(value, "\\") {
		return invalid(field, "must use / path separators")
	}
	for _, part := range strings.Split(path, "/") {
		if part == ".." {
			return invalid(field, "must not contain ..")
		}
	}
	return nil
}

func isWindowsAbsolutePath(value string) bool {
	path := strings.ReplaceAll(value, "\\", "/")
	if strings.HasPrefix(path, "//") {
		return true
	}
	if len(path) < 2 || path[1] != ':' {
		return false
	}
	letter := path[0]
	if !((letter >= 'A' && letter <= 'Z') || (letter >= 'a' && letter <= 'z')) {
		return false
	}
	return len(path) == 2 || path[2] == '/'
}

func invalid(field, reason string) ValidationError {
	return ValidationError{Field: field, Reason: reason}
}
