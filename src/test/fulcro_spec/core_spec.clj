(ns fulcro-spec.core-spec
  (:require
    [clojure.test :refer [deftest]]
    [clojure.walk :as walk]
    [fulcro-spec.assertions :as ae]
    [fulcro-spec.core
     :refer [=> =fn=> assertions specification behavior]
     :as core]))

(deftest var-name-from-string
  (assertions
    "converts the rest to dashes"
    (core/var-name-from-string "foo\\\"@^()[]{};',/  ∂¨∫øƒ∑Ó‡ﬁ€⁄ª•¶§¡˙√ß::")
    => '__foo-------------------------------------__))

;; =============================================================================
;; Specification Macro Expansion with :covers Tests
;; =============================================================================

(defn- tree-contains?
  "Returns true if the expanded form tree contains a call to the given symbol."
  [form sym]
  (let [found (atom false)]
    (walk/postwalk
      (fn [x]
        (when (and (symbol? x) (= (name x) (name sym)))
          (reset! found true))
        x)
      form)
    @found))

(specification "specification macro expansion with map :covers"
  (behavior "emits already-checked? call for map covers (CLJ)"
    (let [expanded (macroexpand-1
                     '(fulcro-spec.core/specification
                        {:covers {my.ns/foo "abc123"}}
                        "test with map covers"
                        (assertions 1 => 1)))]
      (assertions
        "expanded form contains already-checked? call"
        (tree-contains? expanded 'already-checked?) => true

        "expanded form contains register-coverage! call"
        (tree-contains? expanded 'register-coverage!) => true))))

(specification "specification macro expansion with vector :covers (legacy)"
  (behavior "does NOT emit already-checked? for legacy vector covers"
    (let [expanded (macroexpand-1
                     '(fulcro-spec.core/specification
                        {:covers [`my.ns/foo]}
                        "test with vector covers"
                        (assertions 1 => 1)))]
      (assertions
        "expanded form does NOT contain already-checked?"
        (tree-contains? expanded 'already-checked?) => false

        "expanded form still contains register-coverage!"
        (tree-contains? expanded 'register-coverage!) => true))))

(specification "specification macro expansion without :covers"
  (behavior "emits no skip-check or registration code"
    (let [expanded (macroexpand-1
                     '(fulcro-spec.core/specification
                        "plain test"
                        (assertions 1 => 1)))]
      (assertions
        "expanded form does NOT contain already-checked?"
        (tree-contains? expanded 'already-checked?) => false

        "expanded form does NOT contain register-coverage!"
        (tree-contains? expanded 'register-coverage!) => false))))

;; =============================================================================
;; parse-assertions Unit Tests
;; =============================================================================

(specification "parse-assertions"
  (behavior "parses a single triple with no behavior label"
    (let [result (ae/parse-assertions '(1 => 2))]
      (assertions
        "returns one block"
        (count result) => 1

        "block has no behavior"
        (:behavior (first result)) => nil

        "block has one triple"
        (count (:triples (first result))) => 1

        "triple has correct actual, arrow, expected"
        (get-in result [0 :triples 0 :actual]) => 1
        (get-in result [0 :triples 0 :arrow]) => '=>
        (get-in result [0 :triples 0 :expected]) => 2)))

  (behavior "parses a behavior label followed by a triple"
    (let [result (ae/parse-assertions '("my behavior" 1 => 2))]
      (assertions
        "returns one block with behavior"
        (count result) => 1
        (:behavior (first result)) => "my behavior"

        "block has one triple"
        (count (:triples (first result))) => 1)))

  (behavior "parses multiple blocks"
    (let [result (ae/parse-assertions '("block1" 1 => 1 "block2" 2 => 2))]
      (assertions
        "returns two blocks"
        (count result) => 2

        "first block has correct behavior"
        (:behavior (first result)) => "block1"

        "second block has correct behavior"
        (:behavior (second result)) => "block2")))

  (behavior "handles string as actual value (not a behavior label)"
    (let [result (ae/parse-assertions '("hello" => "hello"))]
      (assertions
        "returns one block with no behavior"
        (count result) => 1
        (:behavior (first result)) => nil

        "triple has string as actual"
        (get-in result [0 :triples 0 :actual]) => "hello")))

  (behavior "handles multiple triples in one block"
    (let [result (ae/parse-assertions '(1 => 1 2 => 2 3 => 3))]
      (assertions
        "returns one block"
        (count result) => 1

        "block has three triples"
        (count (:triples (first result))) => 3)))

  (behavior "handles different arrow types"
    (let [result (ae/parse-assertions (list 1 '=> 1 '(+ 1 1) '=fn=> 'even?))]
      (assertions
        "returns one block with two triples"
        (count (:triples (first result))) => 2

        "second triple has =fn=> arrow"
        (get-in result [0 :triples 1 :arrow]) => '=fn=>)))

  (behavior "handles empty forms"
    (let [result (ae/parse-assertions '())]
      (assertions
        "returns empty vector"
        result => [])))

  (behavior "discards trailing behavior label with no triples"
    (let [result (ae/parse-assertions '(1 => 1 "orphan label"))]
      (assertions
        "returns one block (the orphan label is discarded)"
        (count result) => 1)))

  (behavior "throws on incomplete triple"
    (assertions
      "throws with descriptive message for missing expected"
      (try
        (ae/parse-assertions '(1 =>))
        :no-exception
        (catch clojure.lang.ExceptionInfo e
          (let [msg (ex-message e)]
            {:threw? true :has-message? (boolean (re-find #"Incomplete" msg))})))
      => {:threw? true :has-message? true}

      "throws with descriptive message for lone actual"
      (try
        (ae/parse-assertions '(1))
        :no-exception
        (catch clojure.lang.ExceptionInfo e
          (let [msg (ex-message e)]
            {:threw? true :has-message? (boolean (re-find #"Incomplete" msg))})))
      => {:threw? true :has-message? true}))

  (behavior "throws on invalid arrow symbol"
    (assertions
      "rejects non-arrow symbol in arrow position"
      (try
        (ae/parse-assertions '(1 + 2))
        :no-exception
        (catch clojure.lang.ExceptionInfo e
          (let [msg (ex-message e)]
            {:threw? true :has-message? (boolean (re-find #"Invalid assertion arrow" msg))})))
      => {:threw? true :has-message? true})))
