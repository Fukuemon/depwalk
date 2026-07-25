package protocol

import "fmt"

func ExampleParseRecord() {
	line := []byte(`{"schemaVersion":"1","recordType":"diagnostic","severity":"warning","code":"UNRESOLVED_SYMBOL","message":"Could not resolve optional dependency"}`)

	record, err := ParseRecord(line)
	if err != nil {
		fmt.Println(err)
		return
	}

	diagnostic := record.(Diagnostic)
	fmt.Println(diagnostic.Code)
	fmt.Println(diagnostic.Severity)

	// Output:
	// UNRESOLVED_SYMBOL
	// warning
}

func ExampleAnalysisRequest_Mode() {
	request := AnalysisRequest{}

	fmt.Println(request.Mode())

	// Output:
	// fullGraph
}
