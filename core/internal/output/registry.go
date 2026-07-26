package output

import "slices"

// formatters returns the formatter for every [Format] the package renders.
//
// The set is built on each call rather than held in a package variable
// populated from init: state ownership stays explicit, and tests never have
// to mutate (and restore) a shared registry. Adding a format means adding
// its implementation plus one entry here; [RegisteredFormats] and the CLI's
// --format validation pick it up automatically.
func formatters() map[Format]Formatter {
	return map[Format]Formatter{
		FormatConsole: consoleFormatter{},
		FormatJSON:    jsonFormatter{},
	}
}

// RegisteredFormats returns the registered output format names in sorted
// order. The returned slice is independent from the formatter set.
func RegisteredFormats() []string {
	registered := formatters()
	formats := make([]string, 0, len(registered))
	for format := range registered {
		formats = append(formats, string(format))
	}
	slices.Sort(formats)
	return formats
}
