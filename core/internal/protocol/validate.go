package protocol

import (
	"fmt"
	"path/filepath"
	"strings"
)

// ValidationError describes a protocol schema validation failure.
type ValidationError struct {
	Field  string
	Reason string
}

// Error returns the validation error message.
func (e ValidationError) Error() string {
	if e.Field == "" {
		return e.Reason
	}
	return fmt.Sprintf("%s: %s", e.Field, e.Reason)
}

// Validate validates an analysis request record.
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

// Validate validates a method symbol record.
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

// Validate validates a call edge record.
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

// Validate validates a diagnostic record.
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

// Validate validates an Analyzer error record.
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
	path := filepath.ToSlash(value)
	if strings.HasPrefix(path, "/") || filepath.IsAbs(value) {
		return invalid(field, "must be relative")
	}
	for _, part := range strings.Split(path, "/") {
		if part == ".." {
			return invalid(field, "must not contain ..")
		}
	}
	return nil
}

func invalid(field, reason string) ValidationError {
	return ValidationError{Field: field, Reason: reason}
}
