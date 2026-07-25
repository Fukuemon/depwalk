package output

import "slices"

var formatters = map[Format]Formatter{}

func registerFormatter(format Format, formatter Formatter) {
	formatters[format] = formatter
}

func registeredFormats() []string {
	formats := make([]string, 0, len(formatters))
	for format := range formatters {
		formats = append(formats, string(format))
	}
	slices.Sort(formats)
	return formats
}

// RegisteredFormats returns the registered output format names in sorted
// order. The returned slice is independent from the formatter registry.
func RegisteredFormats() []string {
	return registeredFormats()
}
