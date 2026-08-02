package output

import "io"

// formatter は [View] を writer へ描画する。実装は formatters.go で登録する。
//
// package が公開するのは interface ではなく形式名 ([RegisteredFormats]) である。
// 形式の追加を package 内の変更で完結させるため。
type formatter interface {
	Format(w io.Writer, view View) error
}
