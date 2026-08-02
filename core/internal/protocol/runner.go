package protocol

import (
	"encoding/json"
	"fmt"

	"github.com/Fukuemon/depwalk/core/internal/analyzer"
)

// Runner は解析要求 1 件につき Analyzer process を 1 つ動かし、その
// stdout as Analyzer Protocol records. Process control (spawn / stdio /
// exit code) is delegated to the analyzer package, which treats the
// payload as opaque lines; the protocol-aware half of the exchange lives
// here.
type Runner struct {
	command analyzer.Command
}

func NewRunner(command analyzer.Command) Runner {
	return Runner{command: command}
}

// RunResult は Analyzer 実行の protocol 単位の結果。
//
// methodSymbol / callEdge の record はここに溜めない。[Runner.Run] の onRecord へ
// 届いた順に渡す。
type RunResult struct {
	Diagnostics     []Diagnostic
	AnalyzerError   *AnalyzerError
	ValidationError error
	ExitCode        int
	Stderr          string
}

// Run は Analyzer process を起動し、analysisRequest の JSONL record を 1 件送り、
// and parses stdout records until the process exits. Each valid
// methodSymbol and callEdge record is passed to onRecord as it is
// received; onRecord may be nil when the caller does not consume graph
// records.
func (r Runner) Run(request AnalysisRequest, onRecord func(Record)) (RunResult, error) {
	if err := request.Validate(); err != nil {
		return RunResult{}, err
	}
	requestLine, err := json.Marshal(request)
	if err != nil {
		return RunResult{}, err
	}
	requestLine = append(requestLine, '\n')

	collector := newRecordCollector(onRecord)
	procResult, runErr := analyzer.New(r.command).Run(requestLine, collector.addLine)
	result := collector.finalize(procResult.ReadError)
	result.ExitCode = procResult.ExitCode
	result.Stderr = procResult.Stderr
	if runErr != nil {
		return result, runErr
	}
	return result, nil
}

// recordCollector は Analyzer の stdout を JSONL 1 行ずつ消費する。
//
// graph の record は溜めずに onRecord へ流す。stream 終了後の参照完全性検査に
// 要る method ID と edge の両端だけを保持する。全件を保持すると大きな graph で
// メモリが膨らむ。
type recordCollector struct {
	onRecord   func(Record)
	references *referenceChecker
	result     RunResult
}

func newRecordCollector(onRecord func(Record)) *recordCollector {
	return &recordCollector{onRecord: onRecord, references: newReferenceChecker()}
}

func (c *recordCollector) addLine(line []byte) {
	record, parseErr := ParseRecord(line)
	if parseErr != nil {
		c.setValidationError(parseErr)
		return
	}
	c.addRecord(record)
}

func (c *recordCollector) addRecord(record Record) {
	switch typed := record.(type) {
	case MethodSymbol:
		c.references.addMethodID(typed.MethodID)
		if c.onRecord != nil {
			c.onRecord(record)
		}
	case CallEdge:
		c.references.addEdge(typed.EdgeID, typed.CallerMethodID, typed.CalleeMethodID)
		if c.onRecord != nil {
			c.onRecord(record)
		}
	case Diagnostic:
		c.result.Diagnostics = append(c.result.Diagnostics, typed)
	case AnalyzerError:
		errRecord := typed
		c.result.AnalyzerError = &errRecord
	default:
		c.setValidationError(fmt.Errorf("analyzer stdout record type %T is not allowed", record))
	}
}

// finalize は stream を閉じる。stdout の読み取り失敗は検証エラーとして報告する
// (先に記録済みのものがあればそちらを優先)。参照完全性の検査は最後に走らせる。
func (c *recordCollector) finalize(readErr error) RunResult {
	if readErr != nil {
		c.setValidationError(readErr)
	}
	// fatal な stream では Core が先行 record をすべて破棄する。そのため参照の
	// 宙づりを別の検証失敗として報告しない
	// (design/features/graph/DesignDoc_graph.md の fatal 契約)。
	if c.result.AnalyzerError == nil {
		if err := c.references.validate(); err != nil {
			c.setValidationError(err)
		}
	}
	return c.result
}

func (c *recordCollector) setValidationError(err error) {
	if c.result.ValidationError == nil {
		c.result.ValidationError = err
	}
}

// referenceChecker は、stream 終了時に「全 call edge が出力済みの method symbol を
// 参照している」ことを検証するのに要る識別子だけを保持する。
type referenceChecker struct {
	methodIDs map[string]struct{}
	edges     []edgeReference
}

type edgeReference struct {
	edgeID   string
	callerID string
	calleeID string
}

func newReferenceChecker() *referenceChecker {
	return &referenceChecker{methodIDs: map[string]struct{}{}}
}

func (c *referenceChecker) addMethodID(methodID string) {
	c.methodIDs[methodID] = struct{}{}
}

func (c *referenceChecker) addEdge(edgeID, callerID, calleeID string) {
	c.edges = append(c.edges, edgeReference{edgeID: edgeID, callerID: callerID, calleeID: calleeID})
}

func (c *referenceChecker) validate() error {
	for _, edge := range c.edges {
		if _, ok := c.methodIDs[edge.callerID]; !ok {
			return fmt.Errorf("callEdge %q references unknown callerMethodId %q", edge.edgeID, edge.callerID)
		}
		if _, ok := c.methodIDs[edge.calleeID]; !ok {
			return fmt.Errorf("callEdge %q references unknown calleeMethodId %q", edge.edgeID, edge.calleeID)
		}
	}
	return nil
}
