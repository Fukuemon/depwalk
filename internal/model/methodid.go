package model

import "fmt"

// MethodID is a stable identifier for a resolved Java method declaration.
//
// Format: <declaringTypeFQN>#<name>(<paramFQN1>,<paramFQN2>,...)
// Example: com.example.FooService#doThing(java.lang.String,int)
type MethodID string

func (id MethodID) String() string { return string(id) }

// NewMethodID creates a MethodID from its components.
func NewMethodID(declaringTypeFQN, name string, paramFQNs []string) MethodID {
	sig := name + "("
	for i, p := range paramFQNs {
		if i > 0 {
			sig += ","
		}
		sig += p
	}
	sig += ")"
	return MethodID(fmt.Sprintf("%s#%s", declaringTypeFQN, sig))
}

// Unresolved represents a method that could not be resolved.
const Unresolved MethodID = "(unresolved)"

