package analyze

import (
	"fmt"
	"strings"
)

// BuildMetadata は繰り返し指定された --analyzer-meta key=value から
// analysisRequest.metadata を組み立てる。
//
// Core は key も value も解釈しない。Analyzer と共有する組み立て規則だけを適用する。
//   - 値は、key ごとの JSON 配列へ flag の指定順に追加する。key が 1 回しか
//     現れなくても配列にする。
//   - 空の値 (key=) は、その key を空の配列で登録する。
//   - 最初の "=" で key と値に分ける。値の中に "=" を含めてよい。
//   - "=" を含まない指定と、key が空の指定は、検証エラーとして拒否する。
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
