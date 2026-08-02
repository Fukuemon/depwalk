package cli

import (
	"errors"
	"fmt"
	"strings"
)

// analyzerCmdEnv is the environment variable fallback for the Analyzer
// 起動コマンドを指定する環境変数。DEPWALK_ 名前空間に置く
// (adr/0003-analyzer-command-resolution.md)。
const analyzerCmdEnv = "DEPWALK_ANALYZER_CMD"

// resolveAnalyzerCommand は Analyzer の起動コマンド文字列を解決する。
//
// 優先順位は flag 値、次に DEPWALK_ANALYZER_CMD 環境変数 (getenv 経由)
// (adr/0003-analyzer-command-resolution.md)。どちらも無ければ失敗させる。
// Analyzer process を起動する前に要求を弾けるようにするためである。
func resolveAnalyzerCommand(flagValue string, getenv func(string) string) (string, error) {
	if flagValue != "" {
		return flagValue, nil
	}
	if getenv != nil {
		if envValue := getenv(analyzerCmdEnv); envValue != "" {
			return envValue, nil
		}
	}
	return "", fmt.Errorf("analyzer command is required: set --analyzer-cmd or %s", analyzerCmdEnv)
}

// isEscapableRune reports whether r is one of the characters that a
// backslash outside quotes may escape (space, tab, newline, single quote,
// double quote, or another backslash). A backslash immediately followed by
// any other rune (or by nothing, i.e. at end of input) is kept as a
// literal backslash instead, so Windows-style paths such as
// `C:\jdk\bin\java.exe` survive splitting unmangled.
func isEscapableRune(r rune) bool {
	switch r {
	case ' ', '\t', '\n', '\'', '"', '\\':
		return true
	default:
		return false
	}
}

// splitAnalyzerCommand は解決済みのコマンド文字列を argv へ分解する。shell は
// 起動しない (adr/0003-analyzer-command-resolution.md が injection リスクを退けている)。
// 単引用符と二重引用符に対応し、depwalk が要る範囲で一般的な shell の語分割に合わせる。
//
// 引用符の外では、backslash は次の文字が特殊 (空白・tab・改行・引用符・backslash) な
// ときだけ escape として働く。それ以外は文字列末尾を含めてリテラルとして残す。
// 引用符なしの Windows 絶対パス (`C:\jdk\bin\java.exe` など) をそのまま通しつつ、
// `\ ` での空白 escape と `\"` / `\'` での引用符 escape も効かせるためである。
//
// 引用符の中では backslash は常にリテラルで、対応する引用符だけが語を閉じる。
func splitAnalyzerCommand(command string) ([]string, error) {
	runes := []rune(command)

	var (
		words      []string
		current    strings.Builder
		hasCurrent bool
		quote      rune
	)

	flush := func() {
		if hasCurrent {
			words = append(words, current.String())
			current.Reset()
			hasCurrent = false
		}
	}

	for i := 0; i < len(runes); i++ {
		r := runes[i]
		switch {
		case quote != 0:
			if r == quote {
				quote = 0
				continue
			}
			current.WriteRune(r)
			hasCurrent = true
		case r == '\\':
			if i+1 < len(runes) && isEscapableRune(runes[i+1]) {
				current.WriteRune(runes[i+1])
				hasCurrent = true
				i++
				continue
			}
			// escape 対象が続かない (または backslash が最後) ときはリテラルとして
			// 残す。Windows のパスをそのまま通すため。
			current.WriteRune(r)
			hasCurrent = true
		case r == '\'' || r == '"':
			quote = r
			hasCurrent = true
		case r == ' ' || r == '\t' || r == '\n':
			flush()
		default:
			current.WriteRune(r)
			hasCurrent = true
		}
	}

	if quote != 0 {
		return nil, fmt.Errorf("analyzer command has an unterminated %c quote", quote)
	}
	flush()

	if len(words) == 0 {
		return nil, errors.New("analyzer command must not be empty")
	}
	return words, nil
}
