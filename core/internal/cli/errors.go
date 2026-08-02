package cli

import "fmt"

// inputError は利用者がコマンドラインで渡した値が原因の失敗を表す。未知の flag 値、
// 必須 flag の欠落、許容範囲外の値など。[ExitCode] が exit status 2 へ写す。
//
// analyze.InputError とは意図的に分けている。これらは use case が走る前にここで
// 弾く失敗であり、use case の error 型で標識すると実際には無い関係を主張することに
// なる。両者が同じ exit status になる判断は [ExitCode] だけが持つ。
type inputError struct {
	err error
}

func (e *inputError) Error() string { return e.err.Error() }

func (e *inputError) Unwrap() error { return e.err }

func invalidInput(format string, args ...any) error {
	return &inputError{err: fmt.Errorf(format, args...)}
}
