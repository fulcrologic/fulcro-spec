# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Use your clojure repl skill to run ANY clojure code or tests in this project.

## Overview

Fulcro-spec is a Clojure/ClojureScript testing library that augments `clojure.test` with enhanced features including:
- Midje-style mocking with `provided` and `when-mocking` macros
- Left-to-right assertions with custom arrows (`=>`, `=fn=>`, `=throws=>`, `=check=>`)
- Timeline testing for async/callback code
- Enhanced test reporting and diff output
- Support for both CLJ and CLJS via cross-platform `.cljc` files

## Project Structure

- `src/main/` - Library source code
  - `fulcro_spec/core.clj[s]` - Main API macros: `specification`, `behavior`, `assertions`, `provided`, `when-mocking`
  - `fulcro_spec/assertions.cljc` - Assertion macro implementation and arrow operators
  - `fulcro_spec/provided.clj` - Mocking macro implementation
  - `fulcro_spec/stub.cljc` - Mocking/stubbing runtime support
  - `fulcro_spec/check.cljc` - Checker functions for enhanced assertions
  - `fulcro_spec/reporters/` - Test reporters (terminal, repl, console)
  - `fulcro_spec/impl/` - Implementation details
- `src/test/` - Test files (both `.clj`, `.cljs`, and `.cljc`)
- `target/` - Build artifacts (ignore)

## Common Commands

## Running Tests

```clojure
(in-ns (.getName *ns*))
(require 'fulcro-spec.reporters.repl)
;; Run all tests in current namespace
(fulcro-spec.reporters.repl/run-tests)
;; Run only tests with :focus metadata
(fulcro-spec.reporters.repl/run-tests #(:focus (meta %)))
```

## Architecture Notes

### Macro-Heavy Implementation

This library is heavily macro-based to transform test DSL into clojure.test code. Key macros:

- `specification` - Wraps `deftest`, adds reporting hooks
- `behavior`/`component` - Wraps `testing`, adds outline nesting
- `assertions` - Parses arrow-based assertions using clojure.spec
- `provided`/`when-mocking` - Rewrites function calls with scripted stubs via `with-redefs`

### Cross-Platform Support

- `.cljc` files contain most logic
- `.clj` files have CLJ-specific macros
- `.cljs` files have CLJS-specific implementations
- Use `fulcro-spec.impl.macros/if-cljs` for conditional compilation in macros
- Macros check `&env` to determine CLJ vs CLJS context

### Mocking System

The mocking system (`provided.clj` + `stub.cljc`) works by:

1. Parsing mock triples: `(fn-call args) =Nx=> result`
2. Creating a script (sequence of stubs) for each mocked function
3. Using `with-redefs` to replace functions with scripted stubs
4. Validating call counts and argument patterns after test execution
5. Supporting clojure.spec validation of args/ret when fdef exists

Key concepts:
- Arrow counts: `=>` (1+), `=2x=>` (exactly 2), etc.
- Argument binding: Symbols in mock arglist are bound for RHS expression
- Timeline support: `async` and `tick` macros for callback testing
- Spy support: `(real-return)` calls original function

### Assertions and Checkers

The `assertions` macro uses clojure.spec to parse blocks:
```clojure
(assertions
  "optional description"
  actual => expected
  actual =fn=> predicate-fn
  actual =check=> (checker expected)
  actual =throws=> ExceptionType)
```

Checkers (`fulcro_spec/check.cljc`) are composable validation functions that return failure maps or nil/empty seq for success.

### Test Metadata and Selectors

`specification` supports metadata for test filtering:
```clojure
;; Metadata map before name
(specification {:integration true} "DB test" ...)
;; Selector keywords after name (converted to metadata)
(specification "Debug this" :focus ...)
;; Both combined
(specification {:slow true} "Integration" :focus ...)
```

Test runners can filter by metadata (e.g., Kaocha's `:skip-meta [:integration]`).

### Coverage Analysis and Transitive Proof System

The library includes a system for verifying transitive test coverage (CLJ only):

**Key namespaces:**
- `fulcro-spec.coverage` - Registry tracking which tests cover which functions
- `fulcro-spec.signature` - Computes signatures for staleness detection
- `fulcro-spec.proof` - High-level API for coverage verification

**Coverage declaration:**
Tests declare which functions they cover via metadata:
```clojure
;; With signature (enables staleness detection)
(specification {:covers {`my-fn "abc123"}} "my-fn test" ...)

;; Legacy format (no staleness detection)
(specification {:covers [`my-fn]} "my-fn test" ...)
```

**Configuration via `.fulcro-spec.edn`:**
Create a file in your project root:
```clojure
;; .fulcro-spec.edn
{:scope-ns-prefixes #{"myapp"}}
```

This file is auto-loaded when coverage features are used. Alternatively, configure programmatically:
```clojure
(proof/configure! {:scope-ns-prefixes #{"myapp"}})
```

**Getting signatures:**
```clojure
(require '[fulcro-spec.signature :as sig])
(require '[fulcro-spec.proof :as proof])

;; With .fulcro-spec.edn configured, just pass the function symbol:
(sig/signature 'myapp.core/my-function)
(proof/signature 'myapp.core/my-function)
;; => "a1b2c3" (leaf) or "a1b2c3,def456" (non-leaf)

;; Or with explicit scope:
(sig/signature 'myapp.core/my-function #{"myapp"})

;; Get advice for resealing all covered functions
(proof/reseal-advice)
```

**Signature format:**
- `"xxxxxx"` - leaf functions (no in-scope callees)
- `"xxxxxx,yyyyyy"` - non-leaf functions (6-char hash of function + 6-char hash of callees)

**Verifying coverage:**
```clojure
(proof/fully-tested? 'myapp.orders/process-order)
(proof/why-not-tested? 'myapp.orders/process-order)
(proof/coverage-stats)
```

**Staleness detection:**
- `sealed?` - Has a signature recorded
- `fresh?` - Sealed AND signature matches current (works for both leaf and non-leaf)
- `stale?` - Sealed but signature differs (works for both leaf and non-leaf)
- `stale-functions` - All stale functions in scope
- `reseal-advice` - Get new signatures for stale functions

**Auto-skip tests (performance optimization):**
When enabled, tests with matching signatures are automatically skipped:
```bash
# Full test suite with auto-skip + caching (fast)
clj -J-Dfulcro-spec.auto-skip -J-Dfulcro-spec.sigcache -M:test
```
- `-Dfulcro-spec.auto-skip` - Enable skipping already-checked tests
- `-Dfulcro-spec.sigcache` - Cache signatures for JVM lifetime (faster)

When a test is skipped, it outputs: `"skipped. Unchanged since last run"`

**Enforcement in mocking:**
- `when-mocking!!` / `provided!!` (double-bang) enforce transitive coverage
- Requires guardrails (`>defn`) for call graph analysis
- CLJ only (call graph analysis not available in CLJS)

## Important Configuration Files

- `deps.edn` - Dependencies and aliases (`:clj-tests`, `:test`)
- `tests.edn` - Kaocha configuration for CLJ tests
- `shadow-cljs.edn` - Shadow-cljs builds (`:ci-tests` for Karma, `:test` for browser)
- `karma.conf.js` - Karma test runner config
- `pom.xml` - Maven config for releases

## Development Notes

### Working with Specs

The library uses clojure.spec extensively to validate macro inputs:
- `fulcro_spec/spec.cljc` - Helper functions and basic specs
- `fulcro_spec/assertions.cljc` - `::assertions` spec
- `fulcro_spec/provided.clj` - `::mocks` spec

Use `fulcro-spec.spec/conform!` which throws on invalid input with explanation.

### Reporter Integration

Custom reporters can be created by implementing test event handlers. See `fulcro_spec/reporters/terminal.clj` for an example. The library emits custom report events:
- `:type :specification` - Outer test block
- `:type :behavior` - Nested behavior/component block
- `:type :provided` - Mocking block

### Breaking Changes (v3.0+)

- No browser-based runners (use Workspaces or Karma)
- `=throws=>` takes only type or regex (not maps/lists)
- Recommend Kaocha for CLJ, Shadow-cljs + Karma for CLJS

## Testing This Library

When adding features or fixing bugs:
1. Add tests in `src/test/fulcro_spec/*_spec.clj[c]`
2. Use the library's own macros to test itself
3. Test both CLJ and CLJS when applicable
4. Run full suite with `make tests` before committing
5. Ensure cross-platform `.cljc` files work in both environments
