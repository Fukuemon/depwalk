package output

import "slices"

// formatters returns the formatter for every [Format] the package renders.
//
// init で package 変数に持たせず、呼ばれるたびに組み立てる。状態の持ち主が
// 明示され、テストが共有 registry を書き換えて戻す必要もなくなる。
//
// 形式を足すときは実装とここの 1 行だけでよい。[RegisteredFormats] と CLI の
// --format 検証が自動で拾う。
func formatters() map[Format]formatter {
	return map[Format]formatter{
		FormatConsole: consoleFormatter{},
		FormatJSON:    jsonFormatter{},
	}
}

// RegisteredFormats は登録済みの出力形式名を辞書順で返す。
// 返す slice は formatter の集合とは独立している。
func RegisteredFormats() []string {
	registered := formatters()
	formats := make([]string, 0, len(registered))
	for format := range registered {
		formats = append(formats, string(format))
	}
	slices.Sort(formats)
	return formats
}
