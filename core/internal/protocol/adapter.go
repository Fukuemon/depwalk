package protocol

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"

	"github.com/Fukuemon/depwalk/core/internal/analyze"
	"github.com/Fukuemon/depwalk/core/internal/analyzer"
	"github.com/Fukuemon/depwalk/core/internal/graph"
)

// Adapter is the Adapter half of the ACL (spec #32 D6): it implements the
// analyze.AnalysisSource port by composing the wire analysisRequest,
// running the Analyzer process through [Runner], and translating wire
// records into domain values with the Translator (translate.go). The
// wiring of Adapter into the analyze use case happens in cli (manual DI).
type Adapter struct {
	command analyzer.Command
}

// NewAdapter returns an [Adapter] that launches the Analyzer with command.
func NewAdapter(command analyzer.Command) *Adapter {
	return &Adapter{command: command}
}

// RunAnalysis implements the analyze.AnalysisSource port.
func (a *Adapter) RunAnalysis(
	request analyze.AnalysisRequest,
	onNode func(graph.Node),
	onEdge func(graph.Edge),
) (analyze.AnalysisOutcome, error) {
	requestID, err := newRequestID()
	if err != nil {
		return analyze.AnalysisOutcome{}, err
	}

	wireRequest := AnalysisRequest{
		SchemaVersion: SchemaVersion,
		RecordType:    RecordTypeAnalysisRequest,
		RequestID:     requestID,
		WorkspaceRoot: request.WorkspaceRoot,
		Language:      Language(request.Language),
		AnalysisMode:  AnalysisModeFullGraph,
		Metadata:      request.Metadata,
	}
	if len(request.SourceRoots) > 0 {
		wireRequest.SourceRoots = request.SourceRoots
	}
	if len(request.Include) > 0 {
		wireRequest.Include = request.Include
	}
	if len(request.Exclude) > 0 {
		wireRequest.Exclude = request.Exclude
	}
	if err := wireRequest.Validate(); err != nil {
		return analyze.AnalysisOutcome{}, &analyze.InputError{Err: fmt.Errorf("invalid analysis request: %w", err)}
	}

	runResult, err := NewRunner(a.command).Run(wireRequest, func(record Record) {
		switch typed := record.(type) {
		case MethodSymbol:
			if onNode != nil {
				onNode(NodeFromMethodSymbol(typed))
			}
		case CallEdge:
			if onEdge != nil {
				onEdge(EdgeFromCallEdge(typed))
			}
		}
	})
	if err != nil {
		return analyze.AnalysisOutcome{}, err
	}
	return analyze.AnalysisOutcome{
		Diagnostics:     diagnosticsToDomain(runResult.Diagnostics),
		Failure:         failureToDomain(runResult.AnalyzerError),
		ValidationError: runResult.ValidationError,
		ExitCode:        runResult.ExitCode,
	}, nil
}

func diagnosticsToDomain(records []Diagnostic) []analyze.Diagnostic {
	if records == nil {
		return nil
	}
	diagnostics := make([]analyze.Diagnostic, 0, len(records))
	for _, record := range records {
		diagnostics = append(diagnostics, analyze.Diagnostic{
			Severity:        string(record.Severity),
			Code:            record.Code,
			Message:         record.Message,
			Source:          copySourceLocation(record.Source),
			RelatedMethodID: record.RelatedMethodID,
			Metadata:        copyMetadataObject(record.Metadata),
		})
	}
	return diagnostics
}

func failureToDomain(record *AnalyzerError) *analyze.AnalyzerFailure {
	if record == nil {
		return nil
	}
	failure := &analyze.AnalyzerFailure{
		Code:     record.Code,
		Message:  record.Message,
		Source:   copySourceLocation(record.Source),
		Metadata: copyMetadataObject(record.Metadata),
	}
	for _, detail := range record.Details {
		failure.Details = append(failure.Details, analyze.FailureDetail{
			Code:     detail.Code,
			Message:  detail.Message,
			Source:   copySourceLocation(detail.Source),
			Metadata: copyMetadataObject(detail.Metadata),
		})
	}
	return failure
}

func newRequestID() (string, error) {
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return "", fmt.Errorf("generate request id: %w", err)
	}
	return hex.EncodeToString(buf), nil
}
