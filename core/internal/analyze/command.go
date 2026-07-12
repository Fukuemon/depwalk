package analyze

import (
	"errors"
	"fmt"
	"strings"
)

// analyzerCmdEnv is the environment variable fallback for the Analyzer
// launch command. It lives under the DEPWALK_ namespace (ADR-0003).
const analyzerCmdEnv = "DEPWALK_ANALYZER_CMD"

// ResolveCommand resolves the Analyzer launch command string.
//
// Resolution order (ADR-0003): the flag value takes precedence, then the
// DEPWALK_ANALYZER_CMD environment variable (read through getenv), and if
// neither is set, resolution fails so the caller can reject the request
// before starting an Analyzer process.
func ResolveCommand(flagValue string, getenv func(string) string) (string, error) {
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

// SplitCommand splits a resolved command string into argv without invoking
// a shell (ADR-0003 rejects shell injection risk). It supports single and
// double quoting, matching common shell-word splitting semantics for the
// subset depwalk needs.
//
// Outside quotes, a backslash escapes the next character only when that
// character is itself special (space, tab, newline, a quote, or another
// backslash); in every other case, including at the end of the string, the
// backslash is kept as a literal character. This lets unquoted Windows
// absolute paths (e.g. `C:\jdk\bin\java.exe`) pass through unchanged while
// still supporting `\ ` to escape a space and `\"` / `\'` to escape a quote.
// Inside quotes, backslashes are always literal; only the matching quote
// character closes the quoted word.
func SplitCommand(command string) ([]string, error) {
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
			// No escapable character follows (or backslash is the last
			// rune): keep it literal, e.g. for Windows paths.
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
