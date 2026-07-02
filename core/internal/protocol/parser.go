package protocol

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"unicode/utf8"
)

// ParseRecord parses and validates one JSONL record line.
func ParseRecord(line []byte) (Record, error) {
	line = bytes.TrimSuffix(line, []byte("\n"))
	line = bytes.TrimSuffix(line, []byte("\r"))
	if len(bytes.TrimSpace(line)) == 0 {
		return nil, invalid("jsonl", "empty line")
	}
	if !utf8.Valid(line) {
		return nil, invalid("jsonl", "invalid UTF-8")
	}
	if err := rejectDuplicateKeys(line); err != nil {
		return nil, err
	}

	var raw map[string]json.RawMessage
	if err := json.Unmarshal(line, &raw); err != nil {
		return nil, invalid("jsonl", err.Error())
	}

	recordType, err := readRecordType(raw)
	if err != nil {
		return nil, err
	}

	var record Record
	switch recordType {
	case RecordTypeAnalysisRequest:
		var r AnalysisRequest
		if err := decodeExact(raw, acceptedAnalysisRequestJSONFields(), &r); err != nil {
			return nil, err
		}
		record = r
	case RecordTypeMethodSymbol:
		var r MethodSymbol
		if err := decodeExact(raw, acceptedMethodSymbolJSONFields(), &r); err != nil {
			return nil, err
		}
		record = r
	case RecordTypeCallEdge:
		var r CallEdge
		if err := decodeExact(raw, acceptedCallEdgeJSONFields(), &r); err != nil {
			return nil, err
		}
		record = r
	case RecordTypeDiagnostic:
		var r Diagnostic
		if err := decodeExact(raw, acceptedDiagnosticJSONFields(), &r); err != nil {
			return nil, err
		}
		record = r
	case RecordTypeError:
		var r AnalyzerError
		if err := decodeExact(raw, acceptedAnalyzerErrorJSONFields(), &r); err != nil {
			return nil, err
		}
		record = r
	default:
		return nil, invalid("recordType", fmt.Sprintf("unsupported %q", recordType))
	}

	if err := record.Validate(); err != nil {
		return nil, err
	}
	return record, nil
}

func readRecordType(raw map[string]json.RawMessage) (RecordType, error) {
	value, ok := raw["recordType"]
	if !ok {
		return "", invalid("recordType", "is required")
	}
	var recordType RecordType
	if err := json.Unmarshal(value, &recordType); err != nil {
		return "", invalid("recordType", err.Error())
	}
	return recordType, nil
}

func decodeExact(raw map[string]json.RawMessage, fields map[string]struct{}, out any) error {
	exact := make(map[string]json.RawMessage, len(fields))
	for field := range fields {
		if value, ok := raw[field]; ok {
			exact[field] = value
		}
	}
	body, err := json.Marshal(exact)
	if err != nil {
		return invalid("jsonl", err.Error())
	}
	if err := json.Unmarshal(body, out); err != nil {
		return invalid("jsonl", err.Error())
	}
	return nil
}

func rejectDuplicateKeys(line []byte) error {
	decoder := json.NewDecoder(bytes.NewReader(line))
	decoder.UseNumber()
	if err := checkValue(decoder); err != nil {
		return err
	}
	if _, err := decoder.Token(); err != io.EOF {
		if err != nil {
			return invalid("jsonl", err.Error())
		}
		return invalid("jsonl", "multiple JSON values")
	}
	return nil
}

func checkValue(decoder *json.Decoder) error {
	token, err := decoder.Token()
	if err != nil {
		return invalid("jsonl", err.Error())
	}
	delim, ok := token.(json.Delim)
	if !ok {
		return nil
	}

	switch delim {
	case '{':
		seen := map[string]struct{}{}
		for decoder.More() {
			keyToken, err := decoder.Token()
			if err != nil {
				return invalid("jsonl", err.Error())
			}
			key, ok := keyToken.(string)
			if !ok {
				return invalid("jsonl", "object key must be a string")
			}
			if _, exists := seen[key]; exists {
				return invalid("jsonl", fmt.Sprintf("duplicate key %q", key))
			}
			seen[key] = struct{}{}
			if err := checkValue(decoder); err != nil {
				return err
			}
		}
		return expectDelim(decoder, '}')
	case '[':
		for decoder.More() {
			if err := checkValue(decoder); err != nil {
				return err
			}
		}
		return expectDelim(decoder, ']')
	default:
		return invalid("jsonl", fmt.Sprintf("unexpected delimiter %q", delim))
	}
}

func expectDelim(decoder *json.Decoder, want json.Delim) error {
	token, err := decoder.Token()
	if err != nil {
		return invalid("jsonl", err.Error())
	}
	got, ok := token.(json.Delim)
	if !ok || got != want {
		return invalid("jsonl", fmt.Sprintf("expected delimiter %q", want))
	}
	return nil
}

func acceptedAnalysisRequestJSONFields() map[string]struct{} {
	return fieldSet(
		"schemaVersion",
		"recordType",
		"requestId",
		"workspaceRoot",
		"language",
		"include",
		"exclude",
		"entrypoints",
		"analysisMode",
		"metadata",
	)
}

func acceptedMethodSymbolJSONFields() map[string]struct{} {
	return fieldSet(
		"schemaVersion",
		"recordType",
		"methodId",
		"language",
		"symbolKind",
		"qualifiedName",
		"signature",
		"sourceLocation",
		"metadata",
	)
}

func acceptedCallEdgeJSONFields() map[string]struct{} {
	return fieldSet(
		"schemaVersion",
		"recordType",
		"edgeId",
		"callerMethodId",
		"calleeMethodId",
		"callSite",
		"metadata",
	)
}

func acceptedDiagnosticJSONFields() map[string]struct{} {
	return fieldSet(
		"schemaVersion",
		"recordType",
		"severity",
		"code",
		"message",
		"sourceLocation",
		"relatedMethodId",
		"metadata",
	)
}

func acceptedAnalyzerErrorJSONFields() map[string]struct{} {
	return fieldSet(
		"schemaVersion",
		"recordType",
		"code",
		"message",
		"sourceLocation",
		"metadata",
	)
}

func fieldSet(fields ...string) map[string]struct{} {
	set := make(map[string]struct{}, len(fields))
	for _, field := range fields {
		set[field] = struct{}{}
	}
	return set
}
