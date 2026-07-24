# Go Style Guide — Responsibility Separation by package, struct, interface, func

## Abstract/Summary

This document defines design rules for separating responsibilities in Go using `package`, `struct`, `interface`, and `func`.

The primary goal is to reduce cognitive load by improving _referencability_ (how easily a reader can understand where to look and what to use), while keeping responsibilities thin and tests simple.

This guide intentionally avoids architectural layers, framework assumptions, and abstract categorizations. All rules are derived from practical concerns:

- Can a reader quickly understand _what to use_?
- Is state easy to reason about?
- Are changes localized and tests simple?
- Can both humans and AI agents reach the same design decision?

## Principles

### Base principles

- [Design Principles: Separation of Concerns](../sourcecodes/design-principles-separation-of-concerns.md)
- [Design Principles: Separation of Concerns in Iteration](../sourcecodes/design-principles-for-sliced-iterations.md)

### Referencability equals separation of concerns

Separation of concerns is evaluated by how easily a reader can:

- Identify the correct entrypoint
- Infer responsibility from names
- Understand where state and dependencies live

If code is difficult to reference or requires exploration to determine usage, concerns are insufficiently separated.

### Keep state easy to reason about

Design structures so that:

- State is explicit
- Ownership of state is clear
- Execution does not rely on hidden or ambient variables

The goal is **state that is easy to see and reason about, resulting in low cognitive load**.

### Keep responsibilities thin to simplify testing

When responsibilities are thin:

- Tests become linear and deterministic
- External dependencies are isolated
- Behavior changes affect fewer tests

If testing becomes complex or brittle, responsibility boundaries should be reconsidered.

### Do not split packages or interfaces prematurely

`package` and `interface` are tools for separation, not defaults.

They should be introduced **only when required** to preserve referencability, responsibility clarity, or test simplicity.

## Decision Rules

### Public surface minimization

**Rule**

- Each package should expose **one public entrypoint function**.

**Decision**

- If one public function is sufficient, keep the package closed.
- If more are required, evaluate whether the exception conditions are met.

**Allowed (Exception)**

Multiple public functions are allowed **only if**:

- They serve the same concern
- They provide different usage purposes
- Their coexistence is necessary

Example:

- `mp3tag.Read`
- `mp3tag.Write`

**Necessity definition**

- For `internal/` packages: the functions are actually used internally
- For non-`internal/` packages: there is a clear intention to provide them as a library API

### Package function vs struct method

**Rule**

Choose between package-level functions and struct methods based on the _size of the concern_.

**Package function is appropriate when**:

- Execution is single-shot and stateless
- No configuration, connection, cache, or environment dependency is required
- Dependencies can be passed as lightweight abstractions (`io.Reader`, `io.Writer`, etc.)
- No package-level variables are required

**Typical examples**

- Parsing
- Encoding / decoding
- Format transformation
- Simple I/O-bound utilities

**Struct method is required when**:

- Configuration, connection, cache, or environment state is involved
- State must persist across calls
- Behavior depends on execution context
- External systems are involved (DB, API, filesystem, etc.)

In such cases:

- Define a struct as the state owner
- Store all dependencies and configuration as private fields
- Expose behavior through methods

This avoids leaking state through package-level variables.

### Package splitting rules

**Rule**

Split packages only when referencability degrades.

**Signals for splitting**

- Public surface is growing
- Names become verbose or repetitive
- Tests require excessive setup due to mixed concerns
- Internal responsibilities diverge

**Prohibited split criteria**

- Generic architectural categories (`model`, `usecase`, `repository`, etc.)

Packages should be named after _what they do_, not _what they are_.

### Interface introduction rules

**Rule**

Do not define interfaces until there is a concrete need.

**Valid reasons to introduce an interface**

- External dependency isolation
- Explicit substitution point
- Test replacement necessity

**Guidelines**

- Start with concrete implementations
- Introduce interfaces at the boundary
- Keep interfaces small (often one verb)

**Minimal-first, compose-as-needed**

When an interface is necessary in production code, define it as narrowly as possible, then compose capabilities only when the caller truly needs them.

- Prefer minimal capability interfaces (similar to `io.Reader`)
- When additional capabilities are required, use compositional interfaces (similar to `io.ReadSeeker`)

This improves referencability: a reader can infer required behavior from the smallest possible contract.

**Decision**

- If the consumer only needs a single capability, accept the minimal interface
- If the consumer needs multiple independent capabilities, accept a composed interface
- If capabilities are optional (only required in some paths), split the API or move the optional capability behind a separate function/method rather than inflating the default contract

**Notes**

- Avoid “convenience” interfaces that bundle unrelated methods because they are harder to mock, harder to implement, and tend to grow
- Prefer defining interfaces at the point of use (consumer side) unless the package is intentionally providing a public library contract

### Naming rules

**Rule**

Avoid semantic duplication between package names and identifiers.

**Examples**

- ❌ `graphql.NewGraphqlHandler`

- ✅ `graphql.NewHandler`

- ❌ `chat.NewChatUsecase`

- ✅ `chatusecase.New`

- ❌ `i2s.HardwareWriter`

- ✅ `i2s.Writer`

Names should convey role, not repeat context.

### Package-level variables

**Rule**

Do not define package-level variables.

**Rationale**

- They obscure state ownership
- They complicate tests
- They introduce hidden coupling

**Permitted exceptions**

- Constants
- Immutable error values
- Effectively-constant values (e.g. compiled regex)

All configuration and dependencies must be injected via constructors and stored in struct private fields.

## Patterns

### Thin Entrypoint Pattern

- One public function
- Returns a struct or performs a single operation
- Internals remain private

### Stateful Struct Pattern

- Struct owns configuration and dependencies
- No global state
- Methods express behavior

### Lightweight Package Function Pattern

- Stateless
- Minimal dependencies
- Suitable for functional-style testing

### Boundary Interface Pattern

- Interface placed at dependency boundary
- Implementation hidden
- Enables substitution without abstraction leakage

## Examples

### Lightweight package function

- `mp3tag.Read(r io.Reader)`
- `mp3tag.Write(w io.Writer, tag Tag)`

### Stateful repository

- `userrepository.RepositoryImpl`
- Holds config, connection, cache
- Methods: `Find`, `Search`

## Review Checklist

- Does the package expose only one public entrypoint?
- Are multiple public functions justified by Decision Rules?
- Is state visible and easy to reason about?
- Are all dependencies injected, not global?
- Are names free from semantic duplication?
- Is responsibility thin enough to simplify tests?
