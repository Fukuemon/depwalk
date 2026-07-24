# Go Style Guide - Go Doc Comments

## Purpose

A skill for creating and maintaining high-quality documentation in Go projects that follows official GoDoc conventions.

## Scope

- Documentation for packages, types, functions, constants, and variables
- Decision-making for doc.go file usage
- Adherence to official guidelines
- Avoiding common mistakes

## Core Principles

### 1. Follow Official Guidelines

**Reference:** <https://go.dev/doc/comment>

The official Go documentation comment guide covers:

- Package documentation
- Command documentation (for main packages)
- Type documentation
- Function documentation
- Constant documentation
- Variable documentation
- Common syntax
- Common mistakes and pitfalls

### 2. Deciding When to Use doc.go

**When package godoc does not exist:**

- Multiple files in package → Create doc.go for package documentation
- Single public function in package → Write package godoc in that function's file

**When package godoc already exists:**

- Prioritize the existing file unless instructed otherwise

### 3. Doc Links

**Reference:** <https://go.dev/doc/comment#doclinks>

godoc コメント内で定義や実装を参照する際に Doc Links 記法を使用する。

- `[Name]` — 同パッケージ内の識別子へのリンク
- `[pkg.Name]` — 別パッケージの識別子へのリンク（インポートパスの末尾要素を使用）
- `[pkg/path.Name]` — フルインポートパスで指定する場合

**用途:**

- 関連する型・関数・定数・変数を相互参照する
- 実装の詳細を別の識別子に委譲する場合に参照先を明示する
- パッケージ間の依存関係をコメントで表現する

```go
// NewReader returns a new [Reader] that reads from r.
// See [io.Reader] for the interface definition.
func NewReader(r io.Reader) *Reader { ... }
```

### 4. Formatting

- Use `gofmt` for formatting
- Maintain consistent style including comments

## Practical Examples

### Example of Well-Documented Package

**net/http package:**
<https://cs.opensource.google/go/go/+/master:src/net/http/doc.go>

This file demonstrates:

- Clear explanation of package purpose and usage
- Key usage patterns with code examples
- References to related types and functions
- Security considerations and caveats

## Common Mistakes

Refer to the "Common mistakes and pitfalls" section in the official guide: <https://go.dev/doc/comment>

## Agent Expectations

When creating Go documentation:

1. Reference official guidelines (<https://go.dev/doc/comment>)
2. Follow doc.go usage decision criteria
3. Respect existing godoc when present
4. Use well-documented packages like net/http as reference
5. Format with gofmt
6. Document all exported identifiers
7. Use Doc Links (`[Name]`, `[pkg.Name]`) to cross-reference related identifiers

## References

- **Official Guide:** <https://go.dev/doc/comment>
- **Doc Links:** <https://go.dev/doc/comment#doclinks>
- **Reference Implementation:** <https://cs.opensource.google/go/go/+/master:src/net/http/doc.go>
- **Related Skill:** effective-go
