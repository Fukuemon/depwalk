package analyze

import (
	"fmt"
	"strings"
)

// BuildMetadata は繰り返し指定された --analyzer-meta key=value から
// analysisRequest.metadata を組み立てる
// (adr/0003-analyzer-command-resolution.md の metadata passthrough)。
//
// Core は key も value も解釈しない。Analyzer と共有する組み立て規則だけを適用する。
//   - every value is appended to a JSON array under its key, in the order
//     the flags were given, even when the key appears once;
//   - an empty value (key=) registers the key with an empty array;
//   - the split happens on the first "=", so a value may itself contain "=";
//   - an entry without "=" is rejected as a validation error.
//
// 公開しているのは、record レベルの E2E が use case と同じ規則で metadata を
// 組めるようにするため。テスト側で組み立てを書き直すと、規則がずれても気づけない。
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
