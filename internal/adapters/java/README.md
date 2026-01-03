# internal/adapters/java

Java language driver implementation lives here.

- tree-sitter based fast parsing (candidates)
- Java helper process (JavaParser + SymbolSolver) for strict resolution
- Gradle classpath retrieval
- helper jar embedding/extraction

Other languages can add sibling directories under `internal/adapters/` (e.g. `go/`, `ts/`).
