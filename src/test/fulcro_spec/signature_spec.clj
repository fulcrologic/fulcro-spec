(ns fulcro-spec.signature-spec
  "Comprehensive tests for signature computation and content normalization.
   Ported from com.fulcrologic.test-filter.content-test."
  (:require
    [clojure.string :as str]
    [com.fulcrologic.guardrails.core :refer [=> >defn]]
    [fulcro-spec.core :refer [assertions behavior specification]]
    [fulcro-spec.proof :as proof]
    ;; Import guardrails last so its => is used for >defn specs
    [fulcro-spec.signature :as sig]))

;; Access private functions for thorough testing
(def ^:private normalize-content @#'sig/normalize-content)
(def ^:private hash-content @#'sig/hash-content)
(def ^:private sha256 @#'sig/sha256)

;; =============================================================================
;; Test Data
;; =============================================================================

(def sample-source
  "(defn calculate
  \"Calculates something.\"
  [x y]
  (+ x y))")

(def sample-source-no-docstring
  "(defn calculate
  [x y]
  (+ x y))")

;; =============================================================================
;; normalize-content Tests
;; =============================================================================

(specification "normalize-content"
  (behavior "strips docstrings from def forms"
    (let [result (normalize-content sample-source)]

      (assertions
        "returns a string"
        (string? result) => true

        "does not contain docstring"
        (str/includes? result "Calculates") => false

        "contains function logic"
        (str/includes? result "defn") => true
        (str/includes? result "calculate") => true
        (str/includes? result "+ x y") => true)))

  (behavior "produces same output regardless of docstring position"
    (let [doc-after-name "(defn calculate \"Doc\" [x y] (+ x y))"
          doc-after-args "(defn calculate [x y] \"Doc\" (+ x y))"
          no-doc         "(defn calculate [x y] (+ x y))"
          hash1          (hash-content doc-after-name)
          hash2          (hash-content doc-after-args)
          hash3          (hash-content no-doc)]

      (assertions
        "all three produce same hash"
        hash1 => hash2
        hash2 => hash3)))

  (behavior "handles docstrings with escaped quotes"
    (let [with-escaped "(defn foo \"This is a \\\"docstring\\\" with quotes\" [x] (+ x 1))"
          without-doc  "(defn foo [x] (+ x 1))"
          result       (normalize-content with-escaped)]

      (assertions
        "removes docstring with escaped quotes"
        (str/includes? result "docstring") => false
        (str/includes? result "(defn foo [x] (+ x 1))") => true

        "produces same hash as version without docstring"
        (hash-content with-escaped) => (hash-content without-doc))))

  (behavior "handles multiline docstrings"
    (let [multiline   "(defn bar\n  \"This is a docstring\n  that spans multiple\n  lines with details\"\n  [x y]\n  (* x y))"
          without-doc "(defn bar [x y] (* x y))"
          result      (normalize-content multiline)]

      (assertions
        "removes multiline docstring"
        (str/includes? result "docstring") => false
        (str/includes? result "spans") => false
        (str/includes? result "(defn bar [x y] (* x y))") => true

        "produces same hash as version without docstring"
        (hash-content multiline) => (hash-content without-doc))))

  (behavior "handles multiline docstrings with escaped quotes"
    (let [complex     "(defn baz\n  \"Multi-line with \\\"escaped\\\" quotes\n  on multiple lines\"\n  [a b c]\n  (+ a b c))"
          without-doc "(defn baz [a b c] (+ a b c))"
          result      (normalize-content complex)]

      (assertions
        "removes complex docstring"
        (str/includes? result "Multi-line") => false
        (str/includes? result "escaped") => false
        (str/includes? result "(defn baz [a b c] (+ a b c))") => true

        "produces same hash as version without docstring"
        (hash-content complex) => (hash-content without-doc))))

  (behavior "preserves string literals that are not docstrings"
    (let [with-string "(defn greet [name] (str \"Hello, \" name))"
          result      (normalize-content with-string)]

      (assertions
        "keeps string literals in function body"
        (str/includes? result "\"Hello, \"") => true
        (str/includes? result "name") => true)))

  (behavior "preserves source text exactly (no reader expansion)"
    (let [code-with-syntax-quote "(p `get-entity-min-issue-date (foo))"
          normalized             (normalize-content code-with-syntax-quote)]

      (assertions
        "does not expand syntax-quote"
        (str/includes? normalized "`get-entity-min-issue-date") => true
        (str/includes? normalized "user/get-entity-min-issue-date") => false)))

  (behavior "produces deterministic hashes for syntax-quoted symbols"
    (let [code  "(p `get-entity-min-issue-date (foo))"
          hash1 (hash-content code)
          hash2 (hash-content code)]

      (assertions
        "hash is stable across multiple calls"
        hash1 => hash2)))

  (behavior "produces deterministic hashes for anonymous functions"
    (let [code  "(def underscore #(str/replace % #\"-\" \"_\"))"
          hash1 (hash-content code)
          hash2 (hash-content code)]

      (assertions
        "hash is stable across multiple calls"
        hash1 => hash2)))

  (behavior "handles nil input"
    (let [result (normalize-content nil)]

      (assertions
        "returns nil"
        result => nil)))

  (behavior "falls back to original on processing errors"
    (let [invalid "(defn broken [x"
          result  (normalize-content invalid)]

      (assertions
        "returns original text when processing fails"
        result => invalid)))

  (behavior "handles >defn (guardrails) syntax"
    (let [guardrails-fn "(>defn calculate \"Doc for guardrails\" [x y] [int? int? => int?] (+ x y))"
          without-doc   "(>defn calculate [x y] [int? int? => int?] (+ x y))"
          result        (normalize-content guardrails-fn)]

      (assertions
        "removes docstring from >defn form"
        (str/includes? result "Doc for guardrails") => false
        (str/includes? result ">defn calculate") => true
        (str/includes? result "[int? int? => int?]") => true

        "produces same hash as version without docstring"
        (hash-content guardrails-fn) => (hash-content without-doc)))))

;; =============================================================================
;; sha256 Tests
;; =============================================================================

(specification "sha256"
  (behavior "generates SHA256 hash of string"
    (let [result (sha256 "hello world")]

      (assertions
        "returns a hex string"
        (string? result) => true

        "is 64 characters (256 bits in hex)"
        (count result) => 64

        "contains only hex characters"
        (re-matches #"[0-9a-f]+" result) => result)))

  (behavior "produces consistent hashes"
    (let [hash1 (sha256 "test")
          hash2 (sha256 "test")]

      (assertions
        "same input produces same hash"
        hash1 => hash2)))

  (behavior "produces different hashes for different inputs"
    (let [hash1 (sha256 "test1")
          hash2 (sha256 "test2")]

      (assertions
        "different inputs produce different hashes"
        (not= hash1 hash2) => true)))

  (behavior "handles nil input"
    (let [result (sha256 nil)]

      (assertions
        "returns nil"
        result => nil))))

;; =============================================================================
;; hash-content Tests
;; =============================================================================

(specification "hash-content"
  (behavior "combines normalization and hashing"
    (let [with-doc    "(defn calculate \"Doc\" [x y] (+ x y))"
          without-doc "(defn calculate [x y] (+ x y))"
          hash1       (hash-content with-doc)
          hash2       (hash-content without-doc)]

      (assertions
        "returns hex string"
        (string? hash1) => true

        "docstring removal produces same hash when formatting is identical"
        hash1 => hash2)))

  (behavior "ignores whitespace differences (Clojure is whitespace-agnostic)"
    (let [compact   "(defn foo [x] (* x 2))"
          spaced    "(defn foo [x]  (*  x  2))"
          multiline "(defn foo\n  [x]\n  (* x 2))"
          tabs      "(defn\tfoo\t[x]\t(*\tx\t2))"
          hash1     (hash-content compact)
          hash2     (hash-content spaced)
          hash3     (hash-content multiline)
          hash4     (hash-content tabs)]

      (assertions
        "all whitespace variations produce same hash"
        hash1 => hash2
        hash2 => hash3
        hash3 => hash4)))

  (behavior "detects actual logic changes"
    (let [original "(defn foo [x] (* x 2))"
          changed  "(defn foo [x] (* x 3))"
          hash1    (hash-content original)
          hash2    (hash-content changed)]

      (assertions
        "logic changes produce different hashes"
        (not= hash1 hash2) => true)))

  (behavior "handles nil input"
    (let [result (hash-content nil)]

      (assertions
        "returns nil"
        result => nil))))

;; =============================================================================
;; Public signature API Tests
;; =============================================================================

;; Define some test functions for signature testing
(defn simple-fn
  "A simple function for testing."
  [x]
  (* x 2))

(defn no-doc-fn [x] (+ x 1))

(defn multiline-fn
  "Multiline docstring
   that spans lines."
  [a b c]
  (let [sum (+ a b)]
    (* sum c)))

(specification "signature (public API)"
  (behavior "returns 6-character signature for valid functions"
    (let [sig (sig/signature `simple-fn)]

      (assertions
        "returns a string"
        (string? sig) => true

        "is exactly 6 characters"
        (count sig) => 6

        "contains only hex characters"
        (re-matches #"[0-9a-f]+" sig) => sig)))

  (behavior "produces consistent signatures"
    (let [sig1 (sig/signature `simple-fn)
          sig2 (sig/signature `simple-fn)]

      (assertions
        "same function produces same signature"
        sig1 => sig2)))

  (behavior "produces different signatures for different functions"
    (let [sig1 (sig/signature `simple-fn)
          sig2 (sig/signature `no-doc-fn)]

      (assertions
        "different functions produce different signatures"
        (not= sig1 sig2) => true)))

  (behavior "returns nil for unresolvable symbols"
    (let [sig (sig/signature 'nonexistent.ns/fake-fn)]

      (assertions
        "returns nil for non-existent function"
        sig => nil)))

  (behavior "docstring changes don't affect signature"
    ;; We can't easily test this with actual functions since we can't redefine
    ;; them mid-test, but we can verify the underlying hash-content behavior
    (let [with-doc    "(defn test-fn \"Doc v1\" [x] (* x 2))"
          changed-doc "(defn test-fn \"Doc v2 - totally different\" [x] (* x 2))"
          hash1       (hash-content with-doc)
          hash2       (hash-content changed-doc)]

      (assertions
        "same implementation with different docs produces same hash"
        hash1 => hash2))))

;; =============================================================================
;; Edge Cases and Regression Tests
;; =============================================================================

(specification "edge cases"
  (behavior "handles deeply nested structures"
    (let [nested "(defn complex [x]
                    (let [a (fn [y]
                              (let [b (fn [z]
                                        {:key \"value\"})]
                                (b y)))]
                      (a x)))"
          result (normalize-content nested)]

      (assertions
        "preserves nested string literals"
        (str/includes? result "\"value\"") => true
        "preserves keywords in nested maps"
        (str/includes? result ":key") => true)))

  (behavior "handles multiple def forms in source"
    (let [multi  "(defn first-fn \"Doc1\" [x] x)\n(defn second-fn \"Doc2\" [y] y)"
          result (normalize-content multi)]

      (assertions
        "removes docstrings from both forms"
        (str/includes? result "Doc1") => false
        (str/includes? result "Doc2") => false

        "preserves both function definitions"
        (str/includes? result "first-fn") => true
        (str/includes? result "second-fn") => true)))

  (behavior "handles defn- (private functions)"
    (let [private-fn "(defn- private-helper \"Private doc\" [x] (inc x))"
          without    "(defn- private-helper [x] (inc x))"
          hash1      (hash-content private-fn)
          hash2      (hash-content without)]

      (assertions
        "removes docstring from defn-"
        hash1 => hash2)))

  (behavior "handles def (not just defn)"
    (let [def-val "(def my-constant \"A constant value\" 42)"
          result  (normalize-content def-val)]

      (assertions
        "removes docstring from def"
        (str/includes? result "A constant value") => false
        (str/includes? result "my-constant") => true
        (str/includes? result "42") => true)))

  (behavior "handles empty function bodies"
    (let [empty-fn "(defn noop \"Does nothing\" [])"
          result   (normalize-content empty-fn)]

      (assertions
        "removes docstring from empty function"
        (str/includes? result "Does nothing") => false
        (str/includes? result "defn noop") => true)))

  ;; NOTE: Docstring removal doesn't work when metadata precedes the function name.
  ;; This is a known limitation of both test-filter and fulcro-spec.
  ;; The parser looks for the name immediately after `defn` but metadata comes first.
  (behavior "handles functions with metadata (known limitation)"
    (let [with-meta "(defn ^:private ^:deprecated old-fn \"Deprecated\" [x] x)"
          result    (normalize-content with-meta)]

      (assertions
        "preserves metadata markers"
        (str/includes? result "^:private") => true
        (str/includes? result "^:deprecated") => true

        ;; Known limitation: docstring is NOT removed when metadata precedes name
        "docstring is preserved (limitation - not removed when metadata present)"
        (str/includes? result "Deprecated") => true))))

;; =============================================================================
;; Test Functions for Transitive Signature Testing
;; =============================================================================

(>defn trans-leaf-a
  "A leaf function with no callees."
  [x]
  [int? => int?]
  (* x 2))

(>defn trans-leaf-b
  "Another leaf function."
  [x]
  [int? => int?]
  (+ x 10))

(>defn trans-mid
  "A mid-level function that calls leaf functions."
  [x]
  [int? => int?]
  (+ (trans-leaf-a x) (trans-leaf-b x)))

(>defn trans-top
  "A top-level function that calls mid."
  [x]
  [int? => int?]
  (trans-mid (inc x)))

(def test-scope #{"fulcro-spec.signature-spec"})

;; =============================================================================
;; Transitive Signature Tests
;; =============================================================================

(specification "signature (with scope - unified API)"
  (behavior "leaf functions return single-field format (no comma)"
    (let [s (sig/signature `trans-leaf-a test-scope)]
      (assertions
        "returns a string"
        (string? s) => true

        "is exactly 6 characters"
        (count s) => 6

        "does NOT contain comma separator"
        (str/includes? s ",") => false)))

  (behavior "non-leaf functions return two-field format with comma"
    (let [s (sig/signature `trans-mid test-scope)]
      (assertions
        "returns a string"
        (string? s) => true

        "contains comma separator"
        (str/includes? s ",") => true

        "has exactly one comma"
        (count (filter #(= % \,) s)) => 1)))

  (behavior "non-leaf functions have proper callee signature"
    (let [s (sig/signature `trans-mid test-scope)
          [self-sig callee-sig] (str/split s #",")]
      (assertions
        "self signature is 6 chars"
        (count self-sig) => 6

        "callee signature is 6 chars"
        (count callee-sig) => 6

        "callee signature is NOT 000000 (has real callees)"
        (not= callee-sig "000000") => true)))

  (behavior "is deterministic"
    (let [sig1 (sig/signature `trans-top test-scope)
          sig2 (sig/signature `trans-top test-scope)]
      (assertions
        "same function produces same signature"
        sig1 => sig2)))

  (behavior "returns nil for unresolvable functions"
    (let [s (sig/signature 'nonexistent.ns/fake-fn test-scope)]
      (assertions
        "returns nil for non-existent function"
        s => nil))))

;; =============================================================================
;; leaf? Function Tests
;; =============================================================================

(specification "leaf?"
  (behavior "returns true for functions with no in-scope callees"
    (assertions
      "trans-leaf-a has no callees"
      (sig/leaf? `trans-leaf-a test-scope) => true

      "trans-leaf-b has no callees"
      (sig/leaf? `trans-leaf-b test-scope) => true))

  (behavior "returns false for functions with in-scope callees"
    (assertions
      "trans-mid calls trans-leaf-a and trans-leaf-b"
      (sig/leaf? `trans-mid test-scope) => false

      "trans-top calls trans-mid (which has callees)"
      (sig/leaf? `trans-top test-scope) => false)))

;; =============================================================================
;; Property Check Tests
;; =============================================================================

(specification "auto-skip-enabled?"
  (behavior "returns false when property not set"
    (assertions
      "is false by default (property not set in test environment)"
      (sig/auto-skip-enabled?) => false)))

(specification "sigcache-enabled?"
  (behavior "returns false when property not set"
    (assertions
      "is false by default (property not set in test environment)"
      (sig/sigcache-enabled?) => false)))

;; =============================================================================
;; already-checked? Tests
;; =============================================================================

(specification "already-checked?"
  (behavior "returns false when auto-skip is disabled"
    (let [covers {`trans-leaf-a (sig/signature `trans-leaf-a test-scope)}]
      (assertions
        "returns false even with matching signatures (auto-skip not enabled)"
        (sig/already-checked? covers test-scope) => false)))

  (behavior "returns false when scope is empty"
    (let [covers {`trans-leaf-a "abc123,000000"}]
      (assertions
        "returns false with empty scope"
        (sig/already-checked? covers #{}) => false
        (sig/already-checked? covers nil) => false)))

  (behavior "short-circuits on property check (zero overhead)"
    ;; This test verifies the design: property check happens first
    ;; When disabled, no expensive signature computation should occur
    (let [covers {`trans-leaf-a "wrong-signature"}]
      (assertions
        "returns false immediately without computing signatures"
        (sig/already-checked? covers test-scope) => false))))

;; =============================================================================
;; Integration with proof namespace
;; =============================================================================

(specification "proof/fresh? and stale? with unified API"
  (behavior "returns false when not sealed"
    (proof/configure! {:scope-ns-prefixes test-scope})
    (assertions
      "unsealed function is not fresh"
      (proof/fresh? `trans-leaf-a) => false

      "unsealed function is not stale"
      (proof/stale? `trans-leaf-a) => false)))

;; =============================================================================
;; sigs-match? Legacy Compatibility Tests
;; =============================================================================

(def ^:private sigs-match? @#'sig/sigs-match?)

(specification "sigs-match? (legacy compatibility)"
  (behavior "returns true for exact match"
    (assertions
      "identical single-field signatures match"
      (sigs-match? "abc123" "abc123") => true

      "identical two-field signatures match"
      (sigs-match? "abc123,def456" "abc123,def456") => true))

  (behavior "returns true for legacy leaf format vs current leaf format"
    (assertions
      "legacy 'xxx,000000' matches current 'xxx' (leaf migration)"
      (sigs-match? "abc123,000000" "abc123") => true))

  (behavior "returns false for different signatures"
    (assertions
      "different single-field signatures don't match"
      (sigs-match? "abc123" "xyz789") => false

      "different two-field signatures don't match"
      (sigs-match? "abc123,def456" "abc123,zzz999") => false

      "non-legacy comma format does not match single-field"
      (sigs-match? "abc123,def456" "abc123") => false)))

;; =============================================================================
;; Namespace-Aliased Keyword Tests (regression for get-source *ns* binding)
;; =============================================================================

;; These functions use namespace-aliased keywords (::alias/key) in their source.
;; Before the fix, clojure.repl/source-fn would fail with "Invalid token: ::str/..."
;; because `read` didn't have the alias context. The fix binds *ns* to the
;; function's defining namespace before reading, giving the reader access to aliases.

(defn fn-with-aliased-keyword
  "A function that uses ::alias/keyword syntax in its body."
  [m]
  (get m ::str/aliased-key))

(defn fn-with-multiple-aliases
  "A function that uses multiple namespace-aliased keywords."
  [m]
  (assoc m ::str/key-a 1 ::sig/key-b 2))

(specification "get-source handles namespace-aliased keywords"
  (behavior "computes signature for functions using ::alias/key"
    (let [s (sig/signature `fn-with-aliased-keyword)]
      (assertions
        "returns a valid 6-char hex signature"
        (string? s) => true
        (count s) => 6
        (re-matches #"[0-9a-f]+" s) => s)))

  (behavior "is deterministic for aliased keyword functions"
    (assertions
      "same function produces same signature across calls"
      (sig/signature `fn-with-aliased-keyword) => (sig/signature `fn-with-aliased-keyword)))

  (behavior "handles multiple different namespace aliases in one function"
    (let [s (sig/signature `fn-with-multiple-aliases)]
      (assertions
        "returns a valid 6-char hex signature"
        (string? s) => true
        (count s) => 6
        (re-matches #"[0-9a-f]+" s) => s))))
