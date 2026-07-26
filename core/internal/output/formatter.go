package output

import "io"

// formatter renders a [View] to a writer. Implementations are registered in
// formatters.go; the package exposes formats by name ([RegisteredFormats])
// rather than the interface, so adding one stays an in-package change.
type formatter interface {
	Format(w io.Writer, view View) error
}
