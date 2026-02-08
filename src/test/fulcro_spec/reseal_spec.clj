(ns fulcro-spec.reseal-spec
  "Integration tests for the reseal! workflow in fulcro-spec.signature.
   Tests verify that reseal! correctly reads, updates, and writes back
   :covers signatures in specification forms."
  (:require
    [com.fulcrologic.guardrails.core :refer [=> >defn]]
    [fulcro-spec.core :refer [assertions behavior specification]]
    [fulcro-spec.signature :as sig]))

;; =============================================================================
;; Test Functions (need real guardrailed fns for signature computation)
;; =============================================================================

(>defn reseal-target-fn
  "A simple function whose signature we can compute."
  [x]
  [int? => int?]
  (+ x 42))

(>defn reseal-target-fn-b
  "Another function for multi-function covers maps."
  [x]
  [int? => int?]
  (* x 3))

(def test-scope #{"fulcro-spec.reseal-spec"})

;; =============================================================================
;; Helpers
;; =============================================================================

(defn- make-temp-file
  "Creates a temp file with the given content, returns the file path."
  [content]
  (let [f (java.io.File/createTempFile "reseal-test" ".clj")]
    (.deleteOnExit f)
    (spit f content)
    (.getAbsolutePath f)))

(defn- current-sig
  "Gets the current signature for a function in our test scope."
  [fn-sym]
  (sig/signature fn-sym test-scope))

;; =============================================================================
;; PART 1: Successful Reseal
;; =============================================================================

(specification "reseal! updates stale signatures in temp file"
  (let [real-sig   (current-sig `reseal-target-fn)
        stale-sig  "aaaaaa"
        file-content (str "(ns fulcro-spec.reseal-spec\n"
                       "  (:require [fulcro-spec.core :refer [specification assertions]]))\n\n"
                       "(specification {:covers {fulcro-spec.reseal-spec/reseal-target-fn \"" stale-sig "\"}}\n"
                       "  \"my reseal test\"\n"
                       "  (assertions 1 => 1))\n")
        file-path  (make-temp-file file-content)
        result     (sig/reseal! file-path 4 {:scope-ns-prefixes test-scope})]

    (behavior "returns a result map with updated signatures"
      (assertions
        "result has no errors"
        (:errors result) => nil

        "result has the file path"
        (:file result) => file-path

        "result has the spec name"
        (:spec-name result) => "my reseal test"

        "updated map contains the function"
        (contains? (:updated result) `reseal-target-fn) => true

        "updated map has old and new signatures"
        (get-in result [:updated `reseal-target-fn :old]) => stale-sig
        (get-in result [:updated `reseal-target-fn :new]) => real-sig))

    (behavior "actually writes updated content to the file"
      (let [new-content (slurp file-path)]
        (assertions
          "file no longer contains stale signature"
          (.contains new-content stale-sig) => false

          "file contains the real signature"
          (.contains new-content real-sig) => true)))))

;; =============================================================================
;; PART 2: Already-Fresh Signatures (No Changes Needed)
;; =============================================================================

(specification "reseal! reports unchanged when signatures are fresh"
  (let [real-sig     (current-sig `reseal-target-fn)
        file-content (str "(ns fulcro-spec.reseal-spec\n"
                       "  (:require [fulcro-spec.core :refer [specification assertions]]))\n\n"
                       "(specification {:covers {fulcro-spec.reseal-spec/reseal-target-fn \"" real-sig "\"}}\n"
                       "  \"already fresh\"\n"
                       "  (assertions 1 => 1))\n")
        file-path    (make-temp-file file-content)
        result       (sig/reseal! file-path 4 {:scope-ns-prefixes test-scope})]

    (behavior "reports no updates and function is unchanged"
      (assertions
        "result has no errors"
        (:errors result) => nil

        "updated map is empty"
        (:updated result) => {}

        "unchanged set contains the function"
        (contains? (:unchanged result) `reseal-target-fn) => true))))

;; =============================================================================
;; PART 3: Multiple Functions in Covers Map
;; =============================================================================

(specification "reseal! handles multiple functions in covers map"
  (let [real-sig-a   (current-sig `reseal-target-fn)
        real-sig-b   (current-sig `reseal-target-fn-b)
        file-content (str "(ns fulcro-spec.reseal-spec\n"
                       "  (:require [fulcro-spec.core :refer [specification assertions]]))\n\n"
                       "(specification {:covers {fulcro-spec.reseal-spec/reseal-target-fn \"stale1\"\n"
                       "                         fulcro-spec.reseal-spec/reseal-target-fn-b \"stale2\"}}\n"
                       "  \"multi-fn test\"\n"
                       "  (assertions 1 => 1))\n")
        file-path    (make-temp-file file-content)
        result       (sig/reseal! file-path 4 {:scope-ns-prefixes test-scope})]

    (behavior "updates both functions"
      (assertions
        "result has no errors"
        (:errors result) => nil

        "both functions are in the updated map"
        (contains? (:updated result) `reseal-target-fn) => true
        (contains? (:updated result) `reseal-target-fn-b) => true

        "new signatures are correct"
        (get-in result [:updated `reseal-target-fn :new]) => real-sig-a
        (get-in result [:updated `reseal-target-fn-b :new]) => real-sig-b))))

;; =============================================================================
;; PART 4: Error Case - Specification Without :covers Map
;; =============================================================================

(specification "reseal! returns error when specification has no :covers map"
  (let [file-content (str "(ns fulcro-spec.reseal-spec\n"
                       "  (:require [fulcro-spec.core :refer [specification assertions]]))\n\n"
                       "(specification \"no covers\"\n"
                       "  (assertions 1 => 1))\n")
        file-path    (make-temp-file file-content)
        result       (sig/reseal! file-path 4 {:scope-ns-prefixes test-scope})]

    (behavior "returns error about missing :covers"
      (assertions
        "result has errors"
        (some? (:errors result)) => true

        "error message mentions :covers"
        (.contains (str (get-in result [:errors :message])) "covers") => true))))

;; =============================================================================
;; PART 5: Error Case - Non-existent File
;; =============================================================================

(specification "reseal! returns error for non-existent file"
  (let [result (sig/reseal! "/tmp/nonexistent-reseal-test-file.clj" 1 {:scope-ns-prefixes test-scope})]

    (behavior "returns error in result"
      (assertions
        "result has errors"
        (some? (:errors result)) => true))))

;; =============================================================================
;; PART 6: Error Case - No Specification at Target Line
;; =============================================================================

(specification "reseal! returns error when no specification exists at target line"
  (let [file-content (str "(ns fulcro-spec.reseal-spec\n"
                       "  (:require [fulcro-spec.core :refer [specification assertions]]))\n\n"
                       "(def some-var 42)\n")
        file-path    (make-temp-file file-content)
        result       (sig/reseal! file-path 4 {:scope-ns-prefixes test-scope})]

    (behavior "returns error about missing specification"
      (assertions
        "result has errors"
        (some? (:errors result)) => true

        "error message mentions specification"
        (.contains (str (get-in result [:errors :message])) "specification") => true))))
