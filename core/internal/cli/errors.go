package cli

import "fmt"

// inputError marks a failure caused by what the user supplied on the command
// line: an unknown flag value, a missing required flag, or a value outside the
// accepted range. [ExitCode] maps it to exit status 2.
//
// It is separate from analyze.InputError on purpose. These failures are
// rejected here, before the use case runs, so tagging them with the use case's
// error type would claim a relationship that does not exist. Both types map to
// the same exit status; [ExitCode] is where that decision lives.
type inputError struct {
	err error
}

func (e *inputError) Error() string { return e.err.Error() }

func (e *inputError) Unwrap() error { return e.err }

// invalidInput builds an [inputError] with a formatted message.
func invalidInput(format string, args ...any) error {
	return &inputError{err: fmt.Errorf(format, args...)}
}
