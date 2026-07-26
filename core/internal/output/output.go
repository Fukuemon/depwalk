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
	formatter, ok := formatters()[format]
	if !ok {
		return fmt.Errorf("output: unsupported format %q; supported formats: %s", format, strings.Join(RegisteredFormats(), ", "))
	}
	return write(w, formatter, in)
}

// write builds the shared view from in and hands it to formatter. It is the
// seam tests use to exercise the view construction and error propagation
// with a stub formatter, without a mutable formatter registry to patch.
func write(w io.Writer, formatter Formatter, in Input) error {
	return formatter.Format(w, buildView(in))
}
