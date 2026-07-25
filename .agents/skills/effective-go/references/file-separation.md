# Go Style Guide — File Separation

## Abstract / Summary

File separation improves **referencability** and **cognitive clarity** by organizing related code into distinct files, each with a clear purpose. A reader should understand what a file contains simply from its name, and find related code nearby without excessive searching.

This guide defines five standard file patterns for Go packages. Following these patterns makes code easier to understand, maintain, and test.

See also: [Responsibility Separation](responsibility-separation.md), [Separation of Concerns](../sourcecodes/design-principles-separation-of-concerns.md)

## Core Principles

- **One responsibility per file**: Each file focuses on a single concern
- **Clear naming**: File names signal their purpose
- **Minimal hierarchy**: Related code is grouped logically, not scattered
- **Reader-first design**: Code layout reflects how people read and understand it

## File Patterns

### 1. `<<packagename>>.go` — Package Entrypoint

**Purpose:** Expose the package's public API and main entry points.

**What goes here:**

- Constructor functions (`New`, `NewXxx`)

  ```go
  func New(config Config) (*Service, error) {
    // initialization
  }
  ```

- Stateless public functions (for lightweight packages or utilities)

  ```go
  func PublicUtility(input Input) Output {
    // logic
  }
  ```

- Type definitions and constants (lightweight packages only)

  ```go
  type Config struct {
    Option string
  }

  const DefaultTimeout = 30 * time.Second
  ```

**Benefit:** A new reader opening this file immediately understands how to use the package.

---

### 2. `types.go` — Shared Types and Constants

**Purpose:** Centralize type definitions and constants used across multiple files.

**What goes here:**

- Types referenced by multiple files

  ```go
  type Status int

  const (
    StatusActive Status = iota
    StatusInactive
  )
  ```

- Types used by multiple interface implementations

  ```go
  type Record struct {
    ID   string
    Data []byte
  }
  ```

- Domain constants

**Benefit:** The package's data contracts are in one place, easy to locate and understand.

---

### 3. `<<interfacename>>.go` — Interface and Implementation

**Purpose:** Group interface definitions with their implementations and supporting types.

**What goes here:**

- Interface definition

  ```go
  type Logger interface {
    Log(msg string) error
  }
  ```

- Types used only by this interface

  ```go
  type loggerImpl struct {
    writer io.Writer
  }

  const logBufferSize = 1024
  ```

- Methods implementing the interface (optional; can be in separate files if too large)

  ```go
  func (l *loggerImpl) Log(msg string) error {
    // implementation
  }
  ```

**Naming:** File name is lowercase interface name (e.g., `logger.go` for interface `Logger`).

**Benefit:** Contract and implementation are logically close, reducing scattered searches.

---

### 4. `<<funcname>>.go` — Functions and Methods

**Purpose:** Group functions by their purpose or concern.

**What goes here:**

- Public and private functions

  ```go
  func ProcessData(input DataInput) (DataOutput, error) {
    // logic
  }

  func validateInput(input DataInput) error {
    // validation
  }
  ```

- Receiver methods (methods on a struct)

  ```go
  func (r *Repository) Fetch(id string) (*Record, error) {
    // implementation
  }
  ```

- Types and constants used only within this file

  ```go
  const internalTimeout = 5 * time.Second

  type localBuffer struct {
    data []byte
  }
  ```

**Naming:** File name reflects the function's purpose (e.g., `fetcher.go`, `validator.go`, `processor.go`).

**Benefit:** Related functionality is visible at once; scope and context are clear.

---

### 5. `<<struct>><<funcname>>.go` — Methods per Struct

**Use when:** The package exposes **multiple structs** with public methods, and each struct has **multiple methods** that warrant their own files.

**Purpose:** Prevent method files from colliding across structs by scoping the file name to the owning struct.

**What goes here:**

- Methods belonging to a specific struct, grouped by concern

  ```go
  // repositoryfetch.go
  func (r *Repository) FetchByID(id string) (*Record, error) {
      // implementation
  }

  func (r *Repository) FetchAll(ctx context.Context) ([]*Record, error) {
      // implementation
  }
  ```

  ```go
  // repositoryupdate.go
  func (r *Repository) Update(record *Record) error {
      // implementation
  }

  func (r *Repository) Delete(id string) error {
      // implementation
  }
  ```

  ```go
  // cacheget.go
  func (c *Cache) Get(key string) ([]byte, bool) {
      // implementation
  }

  func (c *Cache) GetMulti(keys []string) map[string][]byte {
      // implementation
  }
  ```

  ```go
  // cacheset.go
  func (c *Cache) Set(key string, value []byte) error {
      // implementation
  }

  func (c *Cache) Expire(key string, ttl time.Duration) error {
      // implementation
  }
  ```

**Naming:** `<<lowercasestruct>><<funcname>>.go` (e.g., `repositoryfetch.go`, `cacheset.go`). Underscores in Go file names are discouraged to stay consistent with Go community conventions. An underscore separator (e.g., `repository_fetch.go`) may be used only in codebases that do not use build tags at all, where it improves readability, provided the suffix after the underscore does not match a `GOOS` or `GOARCH` value (which the toolchain treats as a file-level build constraint).

**When to switch from `<<funcname>>.go`:** Use `<<struct>><<funcname>>.go` only when a plain name like `fetch.go` would be ambiguous because multiple structs own fetch-like methods.

**Benefit:** Readers know immediately which struct a file belongs to, and files for different structs never conflict in name.

---

## Organization Example

Single-struct or unambiguous function names — use `<<funcname>>.go`:

```text
cms/
├── cms.go           # New, lightweight public functions
├── types.go         # Shared types (Config, Status, etc.)
├── repository.go    # Repository interface and implementation
├── lister.go        # Lister interface and implementation
├── fetcher.go       # Fetch-related functions
├── validator.go     # Validation functions
└── processor.go     # Core processing logic
```

Multiple structs with multiple methods — use `<<struct>><<funcname>>.go`:

```text
store/
├── store.go               # New, lightweight public functions
├── types.go               # Shared types (Record, Config, etc.)
├── repository.go          # Repository interface definition
├── cache.go               # Cache interface definition
├── repositoryfetch.go     # Repository fetch methods
├── repositoryupdate.go    # Repository update/delete methods
├── cacheget.go            # Cache get methods
└── cacheset.go            # Cache set/expire methods
```

## Reading Flow

1. **"How do I use this package?"** → Read `cms.go` / `store.go`
2. **"What data structures exist?"** → Read `types.go`
3. **"How does Repository work?"** → Read `repository.go`
4. **"What does the validator do?"** → Read `validator.go` (cms example)
5. **"How does Repository fetch data?"** → Read `repositoryfetch.go` (store example)

## Guidelines

### ✅ Good Separation

- Interface definitions live in their own file
- Shared types are in `types.go`
- Functions are grouped by concern or purpose
- Constructors are in the package entrypoint

### ❌ Avoid Over-Separation

- Do not create a file for 1–2 lines of code
- Do not split unrelated types across files just to reduce file size
- Do not let file count become the goal

### ❌ Avoid Poor Visibility

- Do not scatter related functions across many files
- Do not hide types in files where they are hard to find
- Do not split one concern across multiple files without clear reason

## Rationale

File separation is a tool for **referencability**—enabling readers and maintainers to quickly find the code they need without exploring the entire package. When file organization reflects logical concerns and responsibilities, code becomes easier to understand, modify, and test.
