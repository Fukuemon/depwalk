package model

// DeclRange identifies a method declaration node (or any resolvable node) by file + byte range.
// File should be repository-relative (or project-root-relative) and stable across runs.
type DeclRange struct {
	File      string
	StartByte uint32
	EndByte   uint32
}

// IsZero returns true if the DeclRange is uninitialized.
func (r DeclRange) IsZero() bool {
	return r.File == "" && r.StartByte == 0 && r.EndByte == 0
}

