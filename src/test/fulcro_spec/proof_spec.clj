(ns fulcro-spec.proof-spec
  "Comprehensive tests for the transitive test coverage proof system.

   Test Design:
   - Each distinct behavior has dedicated tests
   - Tests are designed to fail if implementation breaks
   - Mutation testing approach: break impl, verify test fails, fix impl"
  (:require
    [com.fulcrologic.guardrails.core :refer [=> >defn]]
    [com.fulcrologic.guardrails.impl.externs :as gr.externs]
    [fulcro-spec.core :refer [assertions behavior specification]]
    [fulcro-spec.coverage :as coverage]
    [fulcro-spec.proof :as proof]
    [fulcro-spec.signature :as sig]))

;; =============================================================================
;; Test Function Hierarchy
;; =============================================================================
;;
;; Structure:
;;                    high-level
;;                    /        \
;;            mid-covered    mid-partial
;;              /    \           |
;;          leaf-a  leaf-b   leaf-uncovered
;;
;; Also:
;;   diamond-top -> diamond-left -> diamond-bottom
;;              \-> diamond-right -/
;;
;;   self-caller (calls itself)
;;   circular-a <-> circular-b (mutual recursion)

;; Layer 1: Leaf functions (no dependencies)
(>defn leaf-a [x] [int? => int?] (inc x))
(>defn leaf-b [x] [int? => int?] (dec x))
(>defn leaf-uncovered [x] [int? => int?] (* x 2))

;; Layer 2: Mid-level functions
(>defn mid-covered [x] [int? => int?] (leaf-a (leaf-b x)))
(>defn mid-partial [x] [int? => int?] (leaf-a (leaf-uncovered x)))

;; Layer 3: High-level function
(>defn high-level [x] [int? => int?] (mid-covered (mid-partial x)))

;; Diamond pattern
(>defn diamond-bottom [x] [int? => int?] (inc x))
(>defn diamond-left [x] [int? => int?] (diamond-bottom x))
(>defn diamond-right [x] [int? => int?] (diamond-bottom x))
(>defn diamond-top [x] [int? => int?] (+ (diamond-left x) (diamond-right x)))

;; Self-referential (factorial-like)
(>defn self-caller [x] [int? => int?]
  (if (<= x 1) 1 (* x (self-caller (dec x)))))

;; Mutual recursion
(declare circular-b)
(>defn circular-a [x] [int? => int?]
  (if (<= x 0) 0 (circular-b (dec x))))
(>defn circular-b [x] [int? => int?]
  (if (<= x 0) 0 (circular-a (dec x))))

;; Standalone function (no deps, for isolation testing)
(>defn standalone [x] [int? => int?] x)

;; =============================================================================
;; Tests that declare coverage (these populate the coverage registry)
;; =============================================================================

(specification {:covers [`leaf-a]} "leaf-a coverage"
  (assertions (leaf-a 1) => 2))

(specification {:covers [`leaf-b]} "leaf-b coverage"
  (assertions (leaf-b 1) => 0))

(specification {:covers [`mid-covered]} "mid-covered coverage"
  (assertions (mid-covered 5) => 5))

(specification {:covers [`diamond-bottom]} "diamond-bottom coverage"
  (assertions (diamond-bottom 1) => 2))

(specification {:covers [`diamond-left]} "diamond-left coverage"
  (assertions (diamond-left 1) => 2))

(specification {:covers [`diamond-right]} "diamond-right coverage"
  (assertions (diamond-right 1) => 2))

(specification {:covers [`diamond-top]} "diamond-top coverage"
  (assertions (diamond-top 1) => 4))

(specification {:covers [`self-caller]} "self-caller coverage"
  (assertions (self-caller 5) => 120))

(specification {:covers [`circular-a `circular-b]} "circular functions coverage"
  (assertions
    (circular-a 4) => 0
    (circular-b 4) => 0))

(specification {:covers [`standalone]} "standalone coverage"
  (assertions (standalone 42) => 42))

;; NOTE: leaf-uncovered, mid-partial, high-level are intentionally NOT covered

;; =============================================================================
;; PART 1: Coverage Registry Tests
;; =============================================================================

(def test-scope #{"fulcro-spec.proof-spec"})

(specification "coverage/register-coverage! and covered?"
  (behavior "registers test->function coverage"
    ;; These were registered by the specification macros above
    (assertions
      "leaf-a is covered by its test"
      (coverage/covered? `leaf-a) => true

      "leaf-uncovered has no coverage"
      (coverage/covered? `leaf-uncovered) => false

      "covered-by returns the test symbols"
      (set? (coverage/covered-by `leaf-a)) => true
      (contains? (coverage/covered-by `leaf-a)
        'fulcro-spec.proof-spec/__leaf-a-coverage__) => true

      "covered-by returns nil for uncovered"
      (coverage/covered-by `leaf-uncovered) => nil)))

(specification "coverage/all-covered-functions"
  (behavior "returns all functions with declared coverage"
    (let [all-covered (set (coverage/all-covered-functions))]
      (assertions
        "includes covered leaf functions"
        (contains? all-covered `leaf-a) => true
        (contains? all-covered `leaf-b) => true

        "excludes uncovered functions"
        (contains? all-covered `leaf-uncovered) => false
        (contains? all-covered `mid-partial) => false))))

;; =============================================================================
;; PART 2: Call Graph Analysis Tests
;; =============================================================================

(specification "gr.externs/direct-calls"
  (behavior "returns immediate dependencies within scope"
    (assertions
      "leaf functions have no direct calls in scope"
      (gr.externs/direct-calls `leaf-a test-scope) => #{}

      "mid-covered calls leaf-a and leaf-b"
      (contains? (gr.externs/direct-calls `mid-covered test-scope) `leaf-a) => true
      (contains? (gr.externs/direct-calls `mid-covered test-scope) `leaf-b) => true

      "mid-partial calls leaf-a and leaf-uncovered"
      (contains? (gr.externs/direct-calls `mid-partial test-scope) `leaf-a) => true
      (contains? (gr.externs/direct-calls `mid-partial test-scope) `leaf-uncovered) => true)))

(specification "gr.externs/transitive-calls"
  (behavior "returns all dependencies transitively"
    (assertions
      "leaf function returns just itself"
      (gr.externs/transitive-calls `leaf-a test-scope) => #{`leaf-a}))

  (behavior "mid-covered includes itself and both leaves"
    (let [deps (gr.externs/transitive-calls `mid-covered test-scope)]
      (assertions
        (contains? deps `mid-covered) => true
        (contains? deps `leaf-a) => true
        (contains? deps `leaf-b) => true)))

  (behavior "high-level includes entire chain"
    (let [deps (gr.externs/transitive-calls `high-level test-scope)]
      (assertions
        (contains? deps `high-level) => true
        (contains? deps `mid-covered) => true
        (contains? deps `mid-partial) => true
        (contains? deps `leaf-a) => true
        (contains? deps `leaf-b) => true
        (contains? deps `leaf-uncovered) => true))))

(specification "gr.externs/transitive-calls with diamond pattern"
  (behavior "handles diamond dependencies correctly (no duplicates)"
    (let [deps (gr.externs/transitive-calls `diamond-top test-scope)]
      (assertions
        "includes all nodes"
        (contains? deps `diamond-top) => true
        (contains? deps `diamond-left) => true
        (contains? deps `diamond-right) => true
        (contains? deps `diamond-bottom) => true

        "exactly 4 functions (no duplicates)"
        (count deps) => 4))))

(specification "gr.externs/transitive-calls with circular dependencies"
  (behavior "handles mutual recursion without infinite loop"
    (let [deps-a (gr.externs/transitive-calls `circular-a test-scope)
          deps-b (gr.externs/transitive-calls `circular-b test-scope)]
      (assertions
        "circular-a includes both"
        (contains? deps-a `circular-a) => true
        (contains? deps-a `circular-b) => true

        "circular-b includes both"
        (contains? deps-b `circular-a) => true
        (contains? deps-b `circular-b) => true

        "no infinite loop - finite set"
        (count deps-a) => 2
        (count deps-b) => 2))))

(specification "gr.externs/transitive-calls with self-reference"
  (behavior "handles self-calling functions"
    (let [deps (gr.externs/transitive-calls `self-caller test-scope)]
      (assertions
        "includes the function itself"
        (contains? deps `self-caller) => true
        "only one element (itself)"
        (count deps) => 1))))

;; =============================================================================
;; PART 3: Proof Verification Tests
;; =============================================================================

(specification "proof/fully-tested? - complete chains"
  (behavior "returns true when function and all deps are covered"
    (assertions
      "leaf-a (no deps, directly covered)"
      (proof/fully-tested? `leaf-a {:scope-ns-prefixes test-scope}) => true

      "mid-covered (deps leaf-a, leaf-b all covered)"
      (proof/fully-tested? `mid-covered {:scope-ns-prefixes test-scope}) => true

      "standalone (no deps, directly covered)"
      (proof/fully-tested? `standalone {:scope-ns-prefixes test-scope}) => true

      "diamond-top (all diamond nodes covered)"
      (proof/fully-tested? `diamond-top {:scope-ns-prefixes test-scope}) => true

      "self-caller (covered)"
      (proof/fully-tested? `self-caller {:scope-ns-prefixes test-scope}) => true

      "circular-a (both circular fns covered)"
      (proof/fully-tested? `circular-a {:scope-ns-prefixes test-scope}) => true)))

(specification "proof/fully-tested? - incomplete chains"
  (behavior "returns false when any dep lacks coverage"
    (assertions
      "leaf-uncovered (not covered)"
      (proof/fully-tested? `leaf-uncovered {:scope-ns-prefixes test-scope}) => false

      "mid-partial (calls uncovered leaf-uncovered)"
      (proof/fully-tested? `mid-partial {:scope-ns-prefixes test-scope}) => false

      "high-level (chain includes uncovered functions)"
      (proof/fully-tested? `high-level {:scope-ns-prefixes test-scope}) => false)))

(specification "proof/why-not-tested? - identifying gaps"
  (behavior "returns nil for fully tested functions"
    (assertions
      (proof/why-not-tested? `leaf-a {:scope-ns-prefixes test-scope}) => nil
      (proof/why-not-tested? `mid-covered {:scope-ns-prefixes test-scope}) => nil
      (proof/why-not-tested? `diamond-top {:scope-ns-prefixes test-scope}) => nil))

  (behavior "returns exactly the uncovered functions"
    (assertions
      "leaf-uncovered - just itself"
      (:uncovered (proof/why-not-tested? `leaf-uncovered {:scope-ns-prefixes test-scope}))
      => #{`leaf-uncovered}

      "mid-partial - itself and leaf-uncovered"
      (:uncovered (proof/why-not-tested? `mid-partial {:scope-ns-prefixes test-scope}))
      => #{`leaf-uncovered `mid-partial}

      "high-level - all 3 uncovered in chain"
      (:uncovered (proof/why-not-tested? `high-level {:scope-ns-prefixes test-scope}))
      => #{`leaf-uncovered `mid-partial `high-level})))

(specification "proof/verify-transitive-coverage - full report"
  (behavior "returns complete report structure"
    (let [report (proof/verify-transitive-coverage `mid-covered {:scope-ns-prefixes test-scope})]
      (assertions
        "includes function"
        (:function report) => `mid-covered

        "includes scope"
        (:scope-ns-prefixes report) => test-scope

        "tracks all transitive deps"
        (contains? (:transitive-deps report) `mid-covered) => true
        (contains? (:transitive-deps report) `leaf-a) => true
        (contains? (:transitive-deps report) `leaf-b) => true

        "covered set matches"
        (= (:covered report) (:transitive-deps report)) => true

        "uncovered is empty"
        (:uncovered report) => #{}

        "proof is complete"
        (:proof-complete? report) => true)))

  (behavior "reports incomplete chains correctly"
    (let [report (proof/verify-transitive-coverage `high-level {:scope-ns-prefixes test-scope})]
      (assertions
        (:proof-complete? report) => false
        (contains? (:uncovered report) `leaf-uncovered) => true
        (contains? (:uncovered report) `mid-partial) => true
        (contains? (:uncovered report) `high-level) => true
        (contains? (:covered report) `leaf-a) => true
        (contains? (:covered report) `leaf-b) => true
        (contains? (:covered report) `mid-covered) => true))))

;; =============================================================================
;; PART 4: Assert and Enforcement Tests
;; =============================================================================

(specification "proof/assert-transitive-coverage!"
  (behavior "does not throw for complete chains"
    (assertions
      "no exception for covered function"
      (proof/assert-transitive-coverage! `leaf-a {:scope-ns-prefixes test-scope}) => nil
      (proof/assert-transitive-coverage! `mid-covered {:scope-ns-prefixes test-scope}) => nil))

  (behavior "throws for incomplete chains with explicit opts"
    (assertions
      "throws exception with details"
      (try
        (proof/assert-transitive-coverage! `leaf-uncovered {:scope-ns-prefixes test-scope})
        :no-exception
        (catch clojure.lang.ExceptionInfo e
          (let [data (ex-data e)]
            {:threw?         true
             :function       (:function data)
             :has-uncovered? (contains? (:uncovered data) `leaf-uncovered)})))
      => {:threw?         true
          :function       `leaf-uncovered
          :has-uncovered? true})))

;; =============================================================================
;; PART 5: Global Configuration Tests
;; =============================================================================

(specification "proof/configure! - global scope"
  (behavior "sets global scope for all queries"
    (proof/configure! {:scope-ns-prefixes test-scope :enforce? false})
    (assertions
      "fully-tested? uses global scope"
      (proof/fully-tested? `leaf-a) => true
      (proof/fully-tested? `leaf-uncovered) => false

      "why-not-tested? uses global scope"
      (proof/why-not-tested? `leaf-a) => nil
      (map? (proof/why-not-tested? `leaf-uncovered)) => true
      (set? (:uncovered (proof/why-not-tested? `leaf-uncovered))) => true

      "get-config returns current config"
      (:scope-ns-prefixes (proof/get-config)) => test-scope)))

(specification "proof/configure! - enforce flag"
  (behavior "when enforce? is false, assert doesn't throw without explicit opts"
    (proof/configure! {:scope-ns-prefixes test-scope :enforce? false})
    (assertions
      "no exception even for uncovered"
      (proof/assert-transitive-coverage! `leaf-uncovered) => nil))

  (behavior "when enforce? is true, assert throws for uncovered"
    (proof/configure! {:scope-ns-prefixes test-scope :enforce? true})
    (assertions
      "throws for uncovered function"
      (try
        (proof/assert-transitive-coverage! `leaf-uncovered)
        :no-exception
        (catch clojure.lang.ExceptionInfo e
          :threw))
      => :threw

      "still doesn't throw for covered"
      (proof/assert-transitive-coverage! `leaf-a) => nil))

  ;; Reset
  (proof/configure! {:scope-ns-prefixes #{} :enforce? false}))

(specification "proof/configure! - explicit opts override global"
  (behavior "explicit opts take precedence over global config"
    (proof/configure! {:scope-ns-prefixes #{"wrong-scope"} :enforce? false})
    (assertions
      "global scope doesn't find our functions"
      ;; With wrong scope, function appears uncovered (no deps found, but fn itself uncovered)
      (proof/fully-tested? `leaf-uncovered) => false

      "explicit opts override to correct scope"
      (proof/fully-tested? `leaf-a {:scope-ns-prefixes test-scope}) => true
      (proof/fully-tested? `leaf-uncovered {:scope-ns-prefixes test-scope}) => false))

  ;; Reset
  (proof/configure! {:scope-ns-prefixes #{} :enforce? false}))

;; =============================================================================
;; PART 6: Statistics and Queries
;; =============================================================================

(specification "proof/coverage-stats"
  (behavior "returns accurate statistics"
    (let [stats (proof/coverage-stats test-scope)]
      (assertions
        "total is positive"
        (pos? (:total stats)) => true

        "covered count is positive"
        (pos? (:covered stats)) => true

        "uncovered count is positive (we have uncovered fns)"
        (pos? (:uncovered stats)) => true

        "percentages are reasonable"
        (< 0 (:coverage-pct stats) 100) => true

        "math is correct"
        (+ (:covered stats) (:uncovered stats)) => (:total stats)

        "coverage-pct formula is correct (covered/total * 100)"
        (:coverage-pct stats) => (* 100.0 (/ (:covered stats) (:total stats)))))))

(specification "proof/uncovered-in-scope"
  (behavior "finds all uncovered functions"
    (let [uncovered (proof/uncovered-in-scope test-scope)]
      (assertions
        "includes uncovered functions"
        (contains? uncovered `leaf-uncovered) => true
        (contains? uncovered `mid-partial) => true
        (contains? uncovered `high-level) => true

        "excludes covered functions"
        (contains? uncovered `leaf-a) => false
        (contains? uncovered `leaf-b) => false
        (contains? uncovered `mid-covered) => false
        (contains? uncovered `diamond-top) => false))))

;; =============================================================================
;; PART 7: Edge Cases
;; =============================================================================

(specification "edge case: empty scope"
  (behavior "handles empty scope gracefully"
    (let [report (proof/verify-transitive-coverage `leaf-a {:scope-ns-prefixes #{}})]
      (assertions
        "returns a report"
        (map? report) => true

        "proof-complete? is false when no scope configured"
        (:proof-complete? report) => false

        "has error key indicating no scope"
        (:error report) => :no-scope-configured

        "indicates no scope configured"
        (boolean (or (:note report) (empty? (:transitive-deps report)))) => true))))

(specification "edge case: function not in registry"
  (behavior "handles non-guardrailed functions"
    ;; clojure.core/inc is not in the guardrails registry
    (let [report (proof/verify-transitive-coverage 'clojure.core/inc {:scope-ns-prefixes #{"clojure"}})]
      (assertions
        "doesn't crash"
        (map? report) => true))))

(specification "edge case: scope that matches nothing"
  (behavior "handles scope with no matching functions"
    (let [stats (proof/coverage-stats #{"nonexistent.namespace"})]
      (assertions
        "total is zero"
        (:total stats) => 0

        "coverage is 100% (vacuously)"
        (:coverage-pct stats) => 100.0))))

;; =============================================================================
;; PART 8: Multiple Coverage Declarations
;; =============================================================================

;; Add another test that also covers leaf-a
(specification {:covers [`leaf-a]} "leaf-a additional coverage"
  (assertions (leaf-a 100) => 101))

(specification "multiple tests covering same function"
  (behavior "function can have multiple covering tests"
    (let [covering-tests (coverage/covered-by `leaf-a)]
      (assertions
        "has multiple covering tests"
        (> (count covering-tests) 1) => true

        "still reports as covered"
        (coverage/covered? `leaf-a) => true))))

;; =============================================================================
;; PART 9: Staleness Detection Tests
;; =============================================================================

;; Get the actual signature of standalone for use in tests
;; (standalone is a leaf function, so signature will be single-field)
(def standalone-sig (proof/signature `standalone test-scope))

;; Register coverage with correct signature (fresh)
(specification {:covers {`standalone standalone-sig}} "standalone with correct signature"
  (assertions (standalone 42) => 42))

;; A function we'll register with wrong signature for stale testing
(>defn stale-test-fn [x] [int? => int?] (+ x 100))

;; Register with deliberately wrong signature
(specification {:covers {`stale-test-fn "wrong!"}} "stale-test-fn with wrong signature"
  (assertions (stale-test-fn 1) => 101))

;; A non-leaf function specifically for testing single-field rejection
(>defn non-leaf-for-edge-case [x] [int? => int?] (leaf-a x))

;; For edge-case testing: manually specify a SINGLE-FIELD signature for a non-leaf
;; (this is invalid - non-leaf functions should have two-field signatures)
(def non-leaf-single-sig "abc123")

;; Register with SINGLE-FIELD signature (invalid for non-leaf function)
(specification {:covers {`non-leaf-for-edge-case non-leaf-single-sig}} "non-leaf with single-field signature (edge case)"
  (assertions (non-leaf-for-edge-case 5) => 6))

(specification "proof/sealed?"
  (behavior "returns true for functions with sealed signatures"
    (assertions
      "standalone has sealed signature"
      (proof/sealed? `standalone) => true

      "stale-test-fn has sealed signature"
      (proof/sealed? `stale-test-fn) => true))

  (behavior "returns false for functions without sealed signatures"
    (assertions
      "leaf-a has no sealed signature (legacy :covers format)"
      (proof/sealed? `leaf-a) => false

      "leaf-uncovered has no coverage at all"
      (proof/sealed? `leaf-uncovered) => false)))

(specification "proof/fresh?"
  ;; Configure scope for these tests (needed for leaf? detection)
  (proof/configure! {:scope-ns-prefixes test-scope :enforce? false})

  (behavior "returns true only when sealed and signature matches"
    (assertions
      "standalone is fresh (correct signature)"
      (proof/fresh? `standalone) => true))

  (behavior "returns false when sealed but signature mismatches"
    (assertions
      "stale-test-fn is not fresh (wrong signature)"
      (proof/fresh? `stale-test-fn) => false))

  (behavior "returns false when not sealed"
    (assertions
      "leaf-a is not fresh (not sealed)"
      (proof/fresh? `leaf-a) => false

      "leaf-uncovered is not fresh (not covered)"
      (proof/fresh? `leaf-uncovered) => false)))

(specification "proof/stale?"
  ;; Configure scope for these tests (needed for leaf? detection)
  (proof/configure! {:scope-ns-prefixes test-scope :enforce? false})

  (behavior "returns true when sealed and signature mismatches"
    (assertions
      "stale-test-fn is stale (wrong signature)"
      (proof/stale? `stale-test-fn) => true))

  (behavior "returns false when sealed and signature matches"
    (assertions
      "standalone is not stale (correct signature)"
      (proof/stale? `standalone) => false))

  (behavior "returns false when not sealed"
    (assertions
      "leaf-a is not stale (not sealed)"
      (proof/stale? `leaf-a) => false

      "leaf-uncovered is not stale (not covered)"
      (proof/stale? `leaf-uncovered) => false))

  (behavior "returns true for non-leaf function with single-field signature"
    ;; This is the critical edge case: single-field signatures are ONLY valid for leaf functions
    ;; A non-leaf function with a single-field signature should ALWAYS be stale
    (assertions
      "non-leaf-for-edge-case has single-field signature (invalid for non-leaf)"
      (proof/stale? `non-leaf-for-edge-case) => true

      "non-leaf-for-edge-case is NOT fresh (single-field invalid for non-leaf)"
      (proof/fresh? `non-leaf-for-edge-case) => false)))

(specification "proof/stale-functions"
  (behavior "returns set of stale functions in scope"
    (let [stale-fns (proof/stale-functions test-scope)]
      (assertions
        "includes stale-test-fn"
        (contains? stale-fns `stale-test-fn) => true

        "excludes fresh functions"
        (contains? stale-fns `standalone) => false

        "excludes unsealed functions"
        (contains? stale-fns `leaf-a) => false))))

(specification "proof/stale-coverage"
  (behavior "returns map with details for stale functions"
    (let [stale-info (proof/stale-coverage test-scope)]
      (assertions
        "has entry for stale-test-fn"
        (contains? stale-info `stale-test-fn) => true

        "includes sealed signature"
        (get-in stale-info [`stale-test-fn :sealed-sig]) => "wrong!"

        "includes current signature (different from sealed)"
        (not= (get-in stale-info [`stale-test-fn :current-sig]) "wrong!") => true

        "includes tested-by info"
        (set? (get-in stale-info [`stale-test-fn :tested-by])) => true))))

(specification "verify-transitive-coverage with stale functions"
  (behavior "marks stale functions in report"
    (let [report (proof/verify-transitive-coverage `stale-test-fn {:scope-ns-prefixes test-scope})]
      (assertions
        "stale-test-fn appears in :stale set"
        (contains? (:stale report) `stale-test-fn) => true

        "proof is not complete due to staleness"
        (:proof-complete? report) => false)))

  (behavior "fresh functions don't appear in stale set"
    (let [report (proof/verify-transitive-coverage `standalone {:scope-ns-prefixes test-scope})]
      (assertions
        "standalone not in :stale set"
        (contains? (:stale report) `standalone) => false

        "proof is complete"
        (:proof-complete? report) => true))))

(specification "coverage-stats includes stale counts"
  (behavior "returns stale and fresh counts"
    (let [stats (proof/coverage-stats test-scope)]
      (assertions
        "has :stale count"
        (number? (:stale stats)) => true
        (>= (:stale stats) 1) => true                       ;; At least stale-test-fn

        "has :fresh count"
        (number? (:fresh stats)) => true

        "stale + fresh = covered"
        (+ (:stale stats) (:fresh stats)) => (:covered stats)))))

;; =============================================================================
;; PART 10: Two-Field Signature Tests
;; =============================================================================

;; Non-leaf function for testing CORRECT two-field signature
(>defn non-leaf-fresh-twofield [x] [int? => int?] (+ (leaf-a x) (leaf-b x)))

;; Get the CORRECT signature (will be two-field since it has callees)
(def non-leaf-fresh-trans-sig
  (sig/signature `non-leaf-fresh-twofield test-scope))

;; Register with CORRECT two-field signature
(specification {:covers {`non-leaf-fresh-twofield non-leaf-fresh-trans-sig}} "non-leaf with correct two-field signature"
  (assertions (non-leaf-fresh-twofield 5) => 10))

;; Non-leaf function for testing INCORRECT two-field signature (stale)
(>defn non-leaf-stale-twofield [x] [int? => int?] (* (leaf-a x) (leaf-b x)))

;; Register with WRONG two-field signature (wrong callee hash)
(specification {:covers {`non-leaf-stale-twofield "abcdef,wrong1"}} "non-leaf with wrong two-field signature"
  (assertions (non-leaf-stale-twofield 5) => 24))

;; Leaf function for testing two-field signature on a leaf (should be rejected)
(>defn leaf-with-twofield [x] [int? => int?] (+ x 42))

;; Register leaf with two-field format (invalid - leaves should use single-field)
(specification {:covers {`leaf-with-twofield "abc123,000000"}} "leaf with two-field signature (edge case)"
  (assertions (leaf-with-twofield 5) => 47))

(specification "proof/fresh? with two-field signatures"
  (proof/configure! {:scope-ns-prefixes test-scope :enforce? false})

  (behavior "returns true for non-leaf with correct two-field signature"
    (assertions
      "non-leaf-fresh-twofield has matching two-field signature"
      (proof/fresh? `non-leaf-fresh-twofield) => true))

  (behavior "returns false for non-leaf with incorrect two-field signature"
    (assertions
      "non-leaf-stale-twofield has wrong two-field signature"
      (proof/fresh? `non-leaf-stale-twofield) => false))

  (behavior "returns false for leaf with two-field signature (format mismatch)"
    ;; A leaf function sealed with two-field format won't match because
    ;; transitive-signature returns single-field for leaves
    (assertions
      "leaf-with-twofield has two-field but leaf expects single-field"
      (proof/fresh? `leaf-with-twofield) => false)))

(specification "proof/stale? with two-field signatures"
  (proof/configure! {:scope-ns-prefixes test-scope :enforce? false})

  (behavior "returns false for non-leaf with correct two-field signature"
    (assertions
      "non-leaf-fresh-twofield is not stale (signature matches)"
      (proof/stale? `non-leaf-fresh-twofield) => false))

  (behavior "returns true for non-leaf with incorrect two-field signature"
    (assertions
      "non-leaf-stale-twofield is stale (signature differs)"
      (proof/stale? `non-leaf-stale-twofield) => true))

  (behavior "returns true for leaf with two-field signature"
    ;; A leaf function with two-field signature is stale because the format is wrong
    (assertions
      "leaf-with-twofield is stale (two-field invalid for leaf)"
      (proof/stale? `leaf-with-twofield) => true)))

;; Note: transitive-fresh? and transitive-stale? functions have been removed in favor
;; of the unified fresh? and stale? functions that work with both leaf and non-leaf
;; functions automatically. The tests above for proof/fresh? and proof/stale? cover
;; both single-field (leaf) and two-field (non-leaf) signature scenarios.

;; =============================================================================
;; PART 11: Baseline Export/Import Tests
;; =============================================================================

(specification "proof/export-baseline"
  (behavior "returns baseline map with correct structure"
    (let [baseline (proof/export-baseline test-scope)]
      (assertions
        "has :generated-at timestamp"
        (instance? java.util.Date (:generated-at baseline)) => true

        "has :scope-ns-prefixes"
        (:scope-ns-prefixes baseline) => test-scope

        "has :signatures map"
        (map? (:signatures baseline)) => true

        "signatures map contains guardrailed functions from our scope"
        (contains? (:signatures baseline) `leaf-a) => true
        (contains? (:signatures baseline) `standalone) => true)))

  (behavior "returns error when scope is empty"
    (let [baseline (proof/export-baseline #{})]
      (assertions
        "has :error key"
        (:error baseline) => :no-scope-configured

        "has explanatory :note"
        (string? (:note baseline)) => true

        "does not have :signatures"
        (contains? baseline :signatures) => false))))

(specification "proof/compare-to-baseline"
  (behavior "detects unchanged functions on immediate round-trip"
    (let [baseline   (proof/export-baseline test-scope)
          comparison (proof/compare-to-baseline baseline)]
      (assertions
        "all functions are unchanged"
        (empty? (:changed comparison)) => true

        "no added functions"
        (empty? (:added comparison)) => true

        "no removed functions"
        (empty? (:removed comparison)) => true

        "unchanged set contains our functions"
        (contains? (:unchanged comparison) `leaf-a) => true
        (contains? (:unchanged comparison) `standalone) => true)))

  (behavior "detects changed signatures when baseline has wrong signatures"
    (let [baseline   {:scope-ns-prefixes test-scope
                      :signatures        {`leaf-a "wrong1" `leaf-b "wrong2"}}
          comparison (proof/compare-to-baseline baseline)]
      (assertions
        "both functions appear as changed"
        (contains? (:changed comparison) `leaf-a) => true
        (contains? (:changed comparison) `leaf-b) => true)))

  (behavior "detects removed functions not in current scope"
    (let [baseline   {:scope-ns-prefixes test-scope
                      :signatures        {`leaf-a (sig/signature `leaf-a test-scope)
                                          'fake.ns/gone "abc123"}}
          comparison (proof/compare-to-baseline baseline)]
      (assertions
        "removed set contains the fake function"
        (contains? (:removed comparison) 'fake.ns/gone) => true)))

  (behavior "detects added functions not in baseline"
    (let [;; Create baseline with only leaf-a
          baseline   {:scope-ns-prefixes test-scope
                      :signatures        {`leaf-a (sig/signature `leaf-a test-scope)}}
          comparison (proof/compare-to-baseline baseline)]
      (assertions
        "added set contains functions not in baseline"
        ;; leaf-b and others should appear as added since they're in scope but not in baseline
        (contains? (:added comparison) `leaf-b) => true)))

  (behavior "returns error when no scope configured"
    (let [baseline   {:signatures {}}
          comparison (proof/compare-to-baseline baseline)]
      (assertions
        "has :error key"
        (:error comparison) => :no-scope-configured))))

;; =============================================================================
;; PART 12: Coverage Registry Inverse Query Tests
;; =============================================================================

(specification "coverage/functions-covered-by"
  (behavior "returns functions that a specific test covers"
    (let [test-sym 'fulcro-spec.proof-spec/__leaf-a-coverage__
          covered  (set (coverage/functions-covered-by test-sym))]
      (assertions
        "includes leaf-a (which is covered by that test)"
        (contains? covered `leaf-a) => true

        "does not include leaf-b (covered by a different test)"
        (contains? covered `leaf-b) => false)))

  (behavior "returns empty vector for unknown test"
    (assertions
      (coverage/functions-covered-by 'nonexistent/test) => [])))

(specification "coverage/all-sealed-functions"
  (behavior "returns only functions with sealed signatures (not legacy format)"
    (let [sealed (set (coverage/all-sealed-functions))]
      (assertions
        "includes standalone (which has a signature in :covers)"
        (contains? sealed `standalone) => true

        "includes stale-test-fn (which has a signature in :covers)"
        (contains? sealed `stale-test-fn) => true

        "excludes leaf-a (legacy :covers format without signature)"
        (contains? sealed `leaf-a) => false))))

(specification "coverage/coverage-summary"
  (behavior "returns correct summary structure"
    (let [summary (coverage/coverage-summary)]
      (assertions
        "has :total-functions count"
        (pos? (:total-functions summary)) => true

        "has :sealed-functions count"
        (number? (:sealed-functions summary)) => true

        "sealed <= total"
        (<= (:sealed-functions summary) (:total-functions summary)) => true

        "has :coverage-map (the full registry)"
        (map? (:coverage-map summary)) => true

        "coverage-map contains known functions"
        (contains? (:coverage-map summary) `leaf-a) => true))))
