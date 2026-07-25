// Package output formats traversal results for users and downstream tools.
package output

import (
	"fmt"
	"io"
	"strings"
)

// Write renders in using the formatter registered for format.
// Unsupported formats fail before the writer is touched.
func Write(w io.Writer, format Format, in Input) error {
	formatter, ok := formatters[format]
	if !ok {
		return fmt.Errorf("output: unsupported format %q; supported formats: %s", format, strings.Join(registeredFormats(), ", "))
	}
	return formatter.Format(w, buildView(in))
}
