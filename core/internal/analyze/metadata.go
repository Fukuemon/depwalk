package analyze

import (
	"fmt"
	"strings"
)

// BuildMetadata composes analysisRequest.metadata from repeated
// --analyzer-meta key=value flags (ADR-0003 metadata passthrough).
//
// Core does not interpret key or value; it only applies the composition
// rule shared with the Analyzer:
//   - every value is appended to a JSON array under its key, in the order
//     the flags were given, even when the key appears once;
//   - an empty value (key=) registers the key with an empty array;
//   - the split happens on the first "=", so a value may itself contain "=";
//   - an entry without "=" is rejected as a validation error.
//
// It is exported so that record-level E2E tests can compose metadata with
// the same rule the use case applies, instead of reimplementing ADR-0003's
// composition in the test.
func BuildMetadata(pairs []string) (map[string]any, error) {
	if len(pairs) == 0 {
		return nil, nil
	}

	metadata := map[string]any{}
	for _, pair := range pairs {
		key, value, ok := strings.Cut(pair, "=")
		if !ok {
			return nil, fmt.Errorf("--analyzer-meta %q must be in key=value form", pair)
		}
		if key == "" {
			return nil, fmt.Errorf("--analyzer-meta %q must have a non-empty key", pair)
		}

		values, _ := metadata[key].([]string)
		if value != "" {
			values = append(values, value)
		}
		if values == nil {
			values = []string{}
		}
		metadata[key] = values
	}
	return metadata, nil
}
