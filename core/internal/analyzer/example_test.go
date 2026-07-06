package analyzer

import "fmt"

func ExampleNew() {
	runner := New(Command{
		Path: "java",
		Args: []string{"-jar", "analyzer.jar"},
		Dir:  "/workspace/project",
	})

	fmt.Printf("%T\n", runner)

	// Output:
	// analyzer.Runner
}
