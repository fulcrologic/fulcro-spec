(ns fulcro-spec.mock-error-messages-spec
  "Tests that verify and demonstrate the quality of error messages when
   mocking functions with guardrails specs fails.

   These tests document the expected behavior of error messages for:
   1. Missing/unresolved schema references
   2. Data type mismatches (wrong argument types)
   3. Return value mismatches

   The error messages should be clear and actionable, telling the user:
   - Which function failed
   - Whether it was an input or output problem
   - Which specific argument or return value was wrong
   - What was expected vs what was received"
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [com.fulcrologic.guardrails.core :refer [>defn =>]]
    [fulcro-spec.core :refer [=> =check=> assertions behavior specification
                              when-mocking! provided!]]
    [fulcro-spec.check :as check]))

;; Test functions with valid specs
(>defn process-single-int
  "A function that takes an int and returns an int"
  [n]
  [int? => int?]
  (* n 2))

(>defn process-pair
  "A function that takes an int and string and returns a string"
  [id name]
  [int? string? => string?]
  (str id "-" name))

(>defn process-triple
  "A function with three typed arguments"
  [a b c]
  [int? string? boolean? => map?]
  {:a a :b b :c c})

;; Function with missing/unresolved schema reference
(>defn fn-with-missing-schema
  "A function referencing a schema that doesn't exist"
  [x]
  [:missing/schema-reference => any?]
  x)

;; Helper to capture test failures
(defn- capture-failures
  "Run body and capture any test failure messages"
  [f]
  (let [failures (atom [])]
    (binding [clojure.test/report
              (fn [m]
                (when (= :fail (:type m))
                  (swap! failures conj m)))]
      (f))
    @failures))

;; ============================================================
;; CASE 1: Missing/Unresolved Schema Reference
;; ============================================================

(deftest missing-schema-error-message-test
  (testing "When a function references a schema that doesn't exist"
    (let [failures (capture-failures
                     (fn []
                       (when-mocking!
                         (fn-with-missing-schema x) => 42
                         (fn-with-missing-schema "test"))))]
      (is (= 1 (count failures))
        "Should report exactly one failure")
      (when (seq failures)
        (let [msg (:message (first failures))]
          (is (str/includes? msg "fn-with-missing-schema")
            "Error should mention the function name")
          (is (or (str/includes? msg "schema")
                (str/includes? msg "Unable to resolve spec"))
            "Error should indicate the schema problem")
          (is (str/includes? msg ":missing/schema-reference")
            "Error should include the missing schema name"))))))

;; ============================================================
;; CASE 2: Data Mismatch - Wrong Argument Types
;; ============================================================

(deftest wrong-argument-type-error-message-test
  (testing "When mock is called with wrong argument type (single arg)"
    (let [failures (capture-failures
                     (fn []
                       (when-mocking!
                         (process-single-int n) => 100
                         ;; Calling with string instead of int
                         (process-single-int "not-an-int"))))]
      (is (= 1 (count failures))
        "Should report exactly one failure")
      (when (seq failures)
        (let [msg (:message (first failures))]
          (is (str/includes? msg "process-single-int")
            "Error should mention the function name")
          (is (or (str/includes? msg "input")
                (str/includes? msg "argument")
                (str/includes? msg "args"))
            "Error should indicate it's an input/argument problem")
          (is (or (str/includes? msg "int?")
                (str/includes? msg "integer"))
            "Error should mention the expected type")))))

  (testing "When mock is called with wrong argument types (multiple args)"
    (let [failures (capture-failures
                     (fn []
                       (when-mocking!
                         (process-pair id name) => "result"
                         ;; Arguments swapped - string where int expected, int where string expected
                         (process-pair "wrong" 123))))]
      (is (= 1 (count failures))
        "Should report exactly one failure")
      (when (seq failures)
        (let [msg (:message (first failures))]
          (is (str/includes? msg "process-pair")
            "Error should mention the function name")
          (is (or (str/includes? msg "input")
                (str/includes? msg "argument")
                (str/includes? msg "args"))
            "Error should indicate it's an input/argument problem"))))))

;; ============================================================
;; CASE 3: Data Mismatch - Wrong Return Type
;; ============================================================

(deftest wrong-return-type-error-message-test
  (testing "When mock returns wrong type (int expected, string returned)"
    (let [failures (capture-failures
                     (fn []
                       (when-mocking!
                         (process-single-int n) => "not-an-int"  ;; Returns string instead of int
                         (process-single-int 5))))]
      (is (= 1 (count failures))
        "Should report exactly one failure")
      (when (seq failures)
        (let [msg (:message (first failures))]
          (is (str/includes? msg "process-single-int")
            "Error should mention the function name")
          (is (or (str/includes? msg "output")
                (str/includes? msg "return")
                (str/includes? msg "ret"))
            "Error should indicate it's an output/return problem")))))

  (testing "When mock returns wrong type (map expected, string returned)"
    (let [failures (capture-failures
                     (fn []
                       (when-mocking!
                         (process-triple a b c) => "not-a-map"  ;; Returns string instead of map
                         (process-triple 1 "two" true))))]
      (is (= 1 (count failures))
        "Should report exactly one failure")
      (when (seq failures)
        (let [msg (:message (first failures))]
          (is (str/includes? msg "process-triple")
            "Error should mention the function name"))))))

;; ============================================================
;; Combined test to show current vs expected behavior
;; ============================================================

(deftest error-message-quality-test
  (testing "Error messages should be clear and actionable"
    (let [all-failures (atom [])]
      ;; Collect failures from all scenarios
      (swap! all-failures into
        (capture-failures
          (fn []
            (when-mocking!
              (fn-with-missing-schema x) => 42
              (fn-with-missing-schema "test")))))

      (swap! all-failures into
        (capture-failures
          (fn []
            (when-mocking!
              (process-single-int n) => "wrong-type"
              (process-single-int 5)))))

      (swap! all-failures into
        (capture-failures
          (fn []
            (when-mocking!
              (process-pair id name) => "result"
              (process-pair "wrong" 123)))))

      ;; Verify we got failures (the mocking correctly detected issues)
      (is (= 3 (count @all-failures))
        "Should have detected all three error scenarios")

      ;; Print the actual messages for review
      (doseq [[idx failure] (map-indexed vector @all-failures)]
        (println (str "\n--- Failure " (inc idx) " ---"))
        (println "Message:" (:message failure))
        (println "Actual:" (:actual failure))))))
