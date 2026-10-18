package analyze

import (
	"strings"
	"testing"
)

func TestOutcomeErrRendersHeapGuidance(t *testing.T) {
	withHint := Outcome{ExitCode: 1, HeapExhausted: true}.Err()
	if withHint == nil {
		t.Fatal("Err() = nil, want an error for a non-zero exit")
	}
	for _, want := range []string{"OutOfMemoryError", "-Xmx", "--analyzer-cmd"} {
		if !strings.Contains(withHint.Error(), want) {
			t.Errorf("Err() = %q, want it to mention %q", withHint.Error(), want)
		}
	}

	withoutHint := Outcome{ExitCode: 1}.Err()
	if withoutHint == nil || withoutHint.Error() != "analyzer process exited with code 1" {
		t.Errorf("Err() without hint = %v, want the plain exit message", withoutHint)
	}

	if err := (Outcome{ExitCode: 0, HeapExhausted: true}).Err(); err != nil {
		t.Errorf("Err() on normal exit = %v, want nil (hint must not fabricate a failure)", err)
	}
}
