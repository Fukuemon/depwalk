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

// write は in から共通の view を組み立て、formatter へ渡す。
//
// テストはこの seam を使い、stub の formatter で view 構築とエラー伝播を検証する。
// 書き換え可能な formatter registry を用意せずに済ませるためである。
func write(w io.Writer, formatter formatter, in Input) error {
	return formatter.Format(w, buildView(in))
}
