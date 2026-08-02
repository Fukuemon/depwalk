package output

import (
	"fmt"
	"io"
	"strings"
)

// Write は format に登録された formatter で in を描画する。
// 未対応の format は writer に触れる前に失敗させる (書きかけを残さないため)。
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
func write(w io.Writer, formatter formatter, in Input) error {
	return formatter.Format(w, buildView(in))
}
