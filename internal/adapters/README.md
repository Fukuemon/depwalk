# internal/adapters

This directory hosts implementations of `internal/ports` that talk to external systems:
- tree-sitter (fast parsing)
- Java helper process (strict resolution)
- Gradle (classpath retrieval)
- cache persistence
- output renderers

Adapters may depend on `internal/domain`, `internal/ports`, and optionally `pkg/*`.



