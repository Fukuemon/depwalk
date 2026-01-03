package domain

// CallSite represents a call expression discovered by the Go-side parser (tree-sitter).
// It is a *candidate* until the Java helper resolves it to a stable MethodID.
type CallSite struct {
	File      string
	StartByte uint32
	EndByte   uint32

	// EnclosingMethodDeclRange is the byte-range of the caller's method declaration (tentative).
	EnclosingMethodDeclRange DeclRange

	CalleeName   string
	ArgsCount    int
	ReceiverText string // optional (best-effort)
}

type ResolvedCall struct {
	CallSite

	// CalleeMethodID is the resolved target method.
	CalleeMethodID MethodID

	// CallerMethodID is the resolved enclosing method (stable).
	CallerMethodID MethodID
}



