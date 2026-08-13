package protocol

import "testing"

// heapExhausted は「valid error record なしの異常終了 + stderr に OutOfMemoryError」
// のときだけ真になる (analyzer-protocol feature doc「異常終了時の stderr の扱い」)。
func TestHeapExhausted(t *testing.T) {
	oomStderr := "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space\n\tat X.y(X.java:1)"
	tests := []struct {
		name   string
		result RunResult
		want   bool
	}{
		{name: "oom-abnormal-exit", result: RunResult{ExitCode: 1, Stderr: oomStderr}, want: true},
		{name: "normal-exit", result: RunResult{ExitCode: 0, Stderr: oomStderr}, want: false},
		{name: "abnormal-exit-without-oom", result: RunResult{ExitCode: 1, Stderr: "boom"}, want: false},
		{
			// valid error record があるときは Analyzer 自身の構造化された失敗が優先で、
			// stderr ヒントは照合しない。
			name:   "structured-analyzer-error",
			result: RunResult{ExitCode: 1, Stderr: oomStderr, AnalyzerError: &AnalyzerError{}},
			want:   false,
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := heapExhausted(tc.result); got != tc.want {
				t.Errorf("heapExhausted() = %v, want %v", got, tc.want)
			}
		})
	}
}
