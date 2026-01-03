package domain

import "fmt"

type SelectorErrorKind string

const (
	SelectorErrorInvalid      SelectorErrorKind = "invalid"
	SelectorErrorAmbiguous    SelectorErrorKind = "ambiguous"
	SelectorErrorNotFound     SelectorErrorKind = "not_found"
	SelectorErrorUnsupported  SelectorErrorKind = "unsupported"
	SelectorErrorUnresolvable SelectorErrorKind = "unresolvable"
)

// SelectorError is returned when a selector cannot be interpreted or resolved.
type SelectorError struct {
	Kind       SelectorErrorKind
	Selector   string
	Message    string
	Candidates []string
}

func (e *SelectorError) Error() string {
	if e == nil {
		return ""
	}
	if len(e.Candidates) == 0 {
		return fmt.Sprintf("selector error (%s): %s: %s", e.Kind, e.Selector, e.Message)
	}
	return fmt.Sprintf("selector error (%s): %s: %s (candidates: %v)", e.Kind, e.Selector, e.Message, e.Candidates)
}



