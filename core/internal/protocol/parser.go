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
	if err := rejectUnknownNestedFields(exact); err != nil {
		return err
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

func rejectUnknownNestedFields(raw map[string]json.RawMessage) error {
	if value, ok := raw["sourceLocation"]; ok {
		if err := rejectUnknownObjectFields("sourceLocation", value, acceptedSourceLocationJSONFields()); err != nil {
			return err
		}
	}
	if value, ok := raw["callSite"]; ok {
		if err := rejectUnknownObjectFields("callSite", value, acceptedSourceLocationJSONFields()); err != nil {
			return err
		}
	}
	if value, ok := raw["entrypoints"]; ok {
		if err := rejectUnknownArrayObjectFields("entrypoints", value, acceptedMethodSelectorJSONFields()); err != nil {
			return err
		}
	}
	if value, ok := raw["details"]; ok {
		if err := rejectUnknownFailureDetailFields("details", value); err != nil {
			return err
		}
	}
	return nil
}

func rejectUnknownFailureDetailFields(field string, raw json.RawMessage) error {
	if bytes.Equal(bytes.TrimSpace(raw), []byte("null")) {
		return nil
	}
	var details []json.RawMessage
	if err := json.Unmarshal(raw, &details); err != nil {
		return invalid(field, err.Error())
	}
	for i, detail := range details {
		element := fmt.Sprintf("%s[%d]", field, i)
		if err := rejectUnknownObjectFields(element, detail, acceptedFailureDetailJSONFields()); err != nil {
			return err
		}
		if bytes.Equal(bytes.TrimSpace(detail), []byte("null")) {
			continue
		}
		var object map[string]json.RawMessage
		if err := json.Unmarshal(detail, &object); err != nil {
			return invalid(element, err.Error())
		}
		if value, ok := object["sourceLocation"]; ok {
			if err := rejectUnknownObjectFields(element+".sourceLocation", value, acceptedSourceLocationJSONFields()); err != nil {
				return err
			}
		}
	}
	return nil
}

func rejectUnknownObjectFields(field string, raw json.RawMessage, fields map[string]struct{}) error {
	if bytes.Equal(bytes.TrimSpace(raw), []byte("null")) {
		return nil
	}
	var object map[string]json.RawMessage
	if err := json.Unmarshal(raw, &object); err != nil {
		return invalid(field, err.Error())
	}
	for key := range object {
		if _, ok := fields[key]; !ok {
			return invalid(field, fmt.Sprintf("unknown field %q", key))
		}
	}
	return nil
}

func rejectUnknownArrayObjectFields(field string, raw json.RawMessage, fields map[string]struct{}) error {
	if bytes.Equal(bytes.TrimSpace(raw), []byte("null")) {
		return nil
	}
	var objects []json.RawMessage
	if err := json.Unmarshal(raw, &objects); err != nil {
		return invalid(field, err.Error())
	}
	for i, object := range objects {
		if err := rejectUnknownObjectFields(fmt.Sprintf("%s[%d]", field, i), object, fields); err != nil {
			return err
		}
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
		"sourceRoots",
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
		"details",
	)
}

func acceptedFailureDetailJSONFields() map[string]struct{} {
	return fieldSet(
		"code",
		"message",
		"sourceLocation",
		"metadata",
	)
}

func acceptedMethodSelectorJSONFields() map[string]struct{} {
	return fieldSet(
		"qualifiedName",
		"signature",
	)
}

func acceptedSourceLocationJSONFields() map[string]struct{} {
	return fieldSet(
		"path",
		"startLine",
		"startColumn",
		"endLine",
		"endColumn",
	)
}

func fieldSet(fields ...string) map[string]struct{} {
	set := make(map[string]struct{}, len(fields))
	for _, field := range fields {
		set[field] = struct{}{}
	}
	return set
}
