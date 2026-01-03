package app

import (
	"strconv"
	"strings"

	"github.com/Fukuemon/depwalk/internal/domain"
)

type SelectorType string

const (
	SelectorTypeFileLine SelectorType = "file_line"
	SelectorTypeFileHash SelectorType = "file_hash"
)

// Selector is a parsed selector string (still needs parser+resolver to turn into MethodID).
type Selector struct {
	Raw  string
	Type SelectorType

	File string

	// for file:line[:col]
	Line int
	Col  int

	// for file#method
	MethodName string
}

func ParseSelector(raw string) (Selector, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return Selector{}, &domain.SelectorError{
			Kind:     domain.SelectorErrorInvalid,
			Selector: raw,
			Message:  "empty selector",
		}
	}

	if i := strings.LastIndex(raw, "#"); i >= 0 {
		file := strings.TrimSpace(raw[:i])
		method := strings.TrimSpace(raw[i+1:])
		if file == "" || method == "" {
			return Selector{}, &domain.SelectorError{
				Kind:     domain.SelectorErrorInvalid,
				Selector: raw,
				Message:  "invalid file#method selector",
			}
		}
		return Selector{
			Raw:        raw,
			Type:       SelectorTypeFileHash,
			File:       file,
			MethodName: method,
		}, nil
	}

	// file:line[:col]
	parts := strings.Split(raw, ":")
	if len(parts) < 2 || len(parts) > 3 {
		return Selector{}, &domain.SelectorError{
			Kind:     domain.SelectorErrorInvalid,
			Selector: raw,
			Message:  "selector must be file:line[:col] or file#method",
		}
	}

	file := strings.TrimSpace(parts[0])
	if file == "" {
		return Selector{}, &domain.SelectorError{
			Kind:     domain.SelectorErrorInvalid,
			Selector: raw,
			Message:  "file path is empty",
		}
	}
	line, err := strconv.Atoi(strings.TrimSpace(parts[1]))
	if err != nil || line <= 0 {
		return Selector{}, &domain.SelectorError{
			Kind:     domain.SelectorErrorInvalid,
			Selector: raw,
			Message:  "line must be a positive integer",
		}
	}
	col := 0
	if len(parts) == 3 {
		col, err = strconv.Atoi(strings.TrimSpace(parts[2]))
		if err != nil || col < 0 {
			return Selector{}, &domain.SelectorError{
				Kind:     domain.SelectorErrorInvalid,
				Selector: raw,
				Message:  "col must be a non-negative integer",
			}
		}
	}

	return Selector{
		Raw:  raw,
		Type: SelectorTypeFileLine,
		File: file,
		Line: line,
		Col:  col,
	}, nil
}
