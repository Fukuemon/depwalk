package protocol

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"strings"

	"github.com/Fukuemon/depwalk/core/internal/analyze"
	"github.com/Fukuemon/depwalk/core/internal/analyzer"
	"github.com/Fukuemon/depwalk/core/internal/graph"
)

// Adapter は ACL の Adapter 側。wire の analysisRequest を組み立て、[Runner] で
// Analyzer process を動かし、Translator (translate.go) で wire record を domain 値へ
// 変換することで analyze.Source port を実装する。
//
// analyze の use case への配線は cli が手で行う。
type Adapter struct {
	command analyzer.Command
}

func NewAdapter(command analyzer.Command) *Adapter {
	return &Adapter{command: command}
}

func (a *Adapter) Run(
	request analyze.Request,
	onNode func(graph.Node),
	onEdge func(graph.Edge),
) (analyze.Outcome, error) {
	requestID, err := newRequestID()
	if err != nil {
		return analyze.Outcome{}, err
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
	// [Runner.Run] 側でも要求を検証する (record レベルの E2E などが直接使うため)。
	// Adapter が先に検証するのは、port 経由の不正な要求を process 起動前に入力
	// エラー (exit code 2) として分類するためである。後段に任せると型のない
	// 実行時失敗として表に出る。
	if err := wireRequest.Validate(); err != nil {
		return analyze.Outcome{}, &analyze.InputError{Err: fmt.Errorf("invalid analysis request: %w", err)}
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
		if heapExhausted(runResult) {
			err = fmt.Errorf("%w (the analyzer ran out of heap: java.lang.OutOfMemoryError on stderr)", err)
		}
		return analyze.Outcome{}, err
	}
	return analyze.Outcome{
		Diagnostics:     diagnosticsToDomain(runResult.Diagnostics),
		Failure:         failureToDomain(runResult.AnalyzerError),
		ValidationError: runResult.ValidationError,
		ExitCode:        runResult.ExitCode,
		HeapExhausted:   heapExhausted(runResult),
	}, nil
}

// heapExhausted は異常終了時の stderr から OutOfMemoryError の痕跡を検知する。
// stderr を protocol record として parse しない契約は維持したまま、valid error
// record なしの異常終了時に限り診断ヒントとして照合する (analyzer-protocol
// feature doc「異常終了時の stderr の扱い」)。解析結果の解釈には一切使わない。
func heapExhausted(result RunResult) bool {
	return result.ExitCode != 0 &&
		result.AnalyzerError == nil &&
		strings.Contains(result.Stderr, "java.lang.OutOfMemoryError")
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
			Location:        copySourceLocation(record.Source),
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
		Location: copySourceLocation(record.Source),
		Metadata: copyMetadataObject(record.Metadata),
	}
	for _, detail := range record.Details {
		failure.Details = append(failure.Details, analyze.FailureDetail{
			Code:     detail.Code,
			Message:  detail.Message,
			Location: copySourceLocation(detail.Source),
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
