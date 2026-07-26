package analyzer_test

import (
	"fmt"

	"github.com/Fukuemon/depwalk/core/internal/analyzer"
)

func ExampleNew() {
	runner := analyzer.New(analyzer.Command{
		Path: "java",
		Args: []string{"-jar", "analyzer.jar"},
		Dir:  "/workspace/project",
	})

	fmt.Printf("%T\n", runner)

	// Output:
	// analyzer.Runner
}
