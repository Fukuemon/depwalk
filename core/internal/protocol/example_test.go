package protocol_test

import (
	"fmt"

	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

func ExampleParseRecord() {
	line := []byte(`{"schemaVersion":"1","recordType":"diagnostic","severity":"warning","code":"UNRESOLVED_SYMBOL","message":"Could not resolve optional dependency"}`)

	record, err := protocol.ParseRecord(line)
	if err != nil {
		fmt.Println(err)
		return
	}

	diagnostic := record.(protocol.Diagnostic)
	fmt.Println(diagnostic.Code)
	fmt.Println(diagnostic.Severity)

	// Output:
	// UNRESOLVED_SYMBOL
	// warning
}

func ExampleAnalysisRequest_Mode() {
	request := protocol.AnalysisRequest{}

	fmt.Println(request.Mode())

	// Output:
	// fullGraph
}
