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

// SplitCommand splits a resolved command string into argv without invoking
// a shell (ADR-0003 rejects shell injection risk). It supports single and
// double quoting and backslash escapes, matching common shell-word
// splitting semantics for the subset depwalk needs.
func SplitCommand(command string) ([]string, error) {
	var (
		words      []string
		current    strings.Builder
		hasCurrent bool
		quote      rune
		inEscape   bool
	)

	flush := func() {
		if hasCurrent {
			words = append(words, current.String())
			current.Reset()
			hasCurrent = false
		}
	}

	for _, r := range command {
		switch {
		case inEscape:
			current.WriteRune(r)
			hasCurrent = true
			inEscape = false
		case quote != 0:
			if r == quote {
				quote = 0
				continue
			}
			current.WriteRune(r)
			hasCurrent = true
		case r == '\\':
			inEscape = true
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

	if inEscape {
		return nil, errors.New("analyzer command has a trailing unescaped backslash")
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
