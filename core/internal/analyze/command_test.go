package analyze

import (
	"reflect"
	"testing"
)

func TestResolveCommand(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name      string
		flagValue string
		env       map[string]string
		want      string
		wantErr   bool
	}{
		{
			name:      "flag takes precedence over environment variable",
			flagValue: "flag-cmd",
			env:       map[string]string{"DEPWALK_ANALYZER_CMD": "env-cmd"},
			want:      "flag-cmd",
		},
		{
			name: "environment variable is used when flag is empty",
			env:  map[string]string{"DEPWALK_ANALYZER_CMD": "env-cmd"},
			want: "env-cmd",
		},
		{
			name:    "neither flag nor environment variable is a validation error",
			wantErr: true,
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			getenv := func(key string) string { return tt.env[key] }
			got, err := ResolveCommand(tt.flagValue, getenv)
			if (err != nil) != tt.wantErr {
				t.Fatalf("ResolveCommand() error = %v, wantErr %v", err, tt.wantErr)
			}
			if got != tt.want {
				t.Fatalf("ResolveCommand() = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestSplitCommand(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		command string
		want    []string
		wantErr bool
	}{
		{
			name:    "simple space-separated words",
			command: "java -jar analyzers/java.jar",
			want:    []string{"java", "-jar", "analyzers/java.jar"},
		},
		{
			name:    "double-quoted word keeps embedded spaces",
			command: `java -jar "/opt/my analyzer/java.jar"`,
			want:    []string{"java", "-jar", "/opt/my analyzer/java.jar"},
		},
		{
			name:    "single-quoted word keeps embedded spaces",
			command: `java -jar '/opt/my analyzer/java.jar'`,
			want:    []string{"java", "-jar", "/opt/my analyzer/java.jar"},
		},
		{
			name:    "backslash escapes the next character",
			command: `java -jar /opt/my\ analyzer/java.jar`,
			want:    []string{"java", "-jar", "/opt/my analyzer/java.jar"},
		},
		{
			name:    "collapses repeated whitespace",
			command: "java   -jar  java.jar",
			want:    []string{"java", "-jar", "java.jar"},
		},
		{
			name:    "empty command is rejected",
			command: "   ",
			wantErr: true,
		},
		{
			name:    "unterminated quote is rejected",
			command: `java -jar "unterminated`,
			wantErr: true,
		},
		{
			name:    "trailing backslash is rejected",
			command: `java -jar java.jar\`,
			wantErr: true,
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			got, err := SplitCommand(tt.command)
			if (err != nil) != tt.wantErr {
				t.Fatalf("SplitCommand() error = %v, wantErr %v", err, tt.wantErr)
			}
			if tt.wantErr {
				return
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Fatalf("SplitCommand() = %#v, want %#v", got, tt.want)
			}
		})
	}
}
