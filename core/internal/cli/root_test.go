package cli

import (
	"bytes"
	"io"
	"strings"
	"testing"
)

func TestRootCommandRejectsUnknownInputWithExit2(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		args []string
		want string
	}{
		{name: "unknown-subcommand", args: []string{"foo"}, want: `unknown command "foo"`},
		{name: "unknown-flag", args: []string{"--bogus"}, want: "unknown flag"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			root := newRootCommand()
			root.SetOut(io.Discard)
			root.SetErr(io.Discard)
			root.SetArgs(tt.args)

			err := root.Execute()
			if got := ExitCode(err); got != 2 {
				t.Fatalf("ExitCode(%v) = %d, want 2", err, got)
			}
			if !strings.Contains(err.Error(), tt.want) {
				t.Errorf("error = %q, want %q", err, tt.want)
			}
		})
	}
}

func TestRootCommandWithoutArgumentsShowsHelp(t *testing.T) {
	t.Parallel()

	var stdout bytes.Buffer
	root := newRootCommand()
	root.SetOut(&stdout)
	root.SetErr(io.Discard)
	root.SetArgs(nil)

	if err := root.Execute(); err != nil {
		t.Fatalf("Execute() error = %v, want nil", err)
	}
	if !strings.Contains(stdout.String(), "analyze") {
		t.Errorf("help = %q, want the analyze subcommand listed", stdout.String())
	}
}
