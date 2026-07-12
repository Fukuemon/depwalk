package e2e

import "testing"

// TestE2ERequired verifies the branch condition skipOrFail uses to decide
// between t.Skip and t.Fatal. skipOrFail's own t.Skip/t.Fatal call cannot be
// observed directly from within a test (testing.T does not expose which one
// ran), so the decision logic is tested here in isolation instead.
func TestE2ERequired(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		envValue string
		want     bool
	}{
		{name: "unset env var means skip", envValue: "", want: false},
		{name: "1 means fail", envValue: "1", want: true},
		{name: "true is not 1, still means skip", envValue: "true", want: false},
		{name: "0 means skip", envValue: "0", want: false},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			if got := e2eRequired(tt.envValue); got != tt.want {
				t.Errorf("e2eRequired(%q) = %v, want %v", tt.envValue, got, tt.want)
			}
		})
	}
}

// TestJavaMajorVersion verifies the regex-based java -version parser
// against real-world `java -version` output variants, since the previous
// literal `version "25` substring check was brittle against formatting
// changes across JDK vendors and versions.
func TestJavaMajorVersion(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		out  string
		want int
	}{
		{
			name: "temurin 25 GA",
			out: "openjdk version \"25\" 2025-09-16\n" +
				"OpenJDK Runtime Environment Temurin-25+36 (build 25+36)\n" +
				"OpenJDK 64-Bit Server VM Temurin-25+36 (build 25+36, mixed mode)\n",
			want: 25,
		},
		{
			name: "25 with patch version",
			out:  "openjdk version \"25.0.1\" 2025-10-21\n",
			want: 25,
		},
		{
			name: "jdk 21 is not 25",
			out:  "openjdk version \"21.0.4\" 2024-07-16\n",
			want: 21,
		},
		{
			name: "unparseable output",
			out:  "command not found\n",
			want: 0,
		},
		{
			name: "empty output",
			out:  "",
			want: 0,
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			if got := javaMajorVersion(tt.out); got != tt.want {
				t.Errorf("javaMajorVersion(%q) = %d, want %d", tt.out, got, tt.want)
			}
		})
	}
}
