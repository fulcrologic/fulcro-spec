(ns untangled-spec.core-spec
  (:require
    [clojure.test :as t :refer [is]]
    [untangled-spec.contains :refer [*contains?]]
    [untangled-spec.core
     :refer [specification behavior when-mocking assertions]
     :as core]
    [untangled-spec.selectors :as sel]))

(specification "adds methods to clojure.test/assert-expr"
  (assertions
    (methods t/assert-expr)
    =fn=> (*contains? '[= exec throws?] :keys)))
(specification "var-name-from-string"
  (assertions
    "allows the following"
    (core/var-name-from-string "asdfASDF1234!#$%&*|:<>?")
    =fn=> #(not (re-find #"\-" (str %)))
    "converts the rest to dashes"
    (core/var-name-from-string "\\\"@^()[]{};',/  ∂¨∫øƒ∑Ó‡ﬁ€⁄ª•¶§¡˙√ß")
    =fn=> #(re-matches #"__\-+__" (str %))))

(defmacro test-core [code-block test-fn]
  `(let [test-var# ~code-block
         reports# (atom [])]
     (binding [t/report #(swap! reports# conj %)]
       (with-redefs [sel/selected-for? (constantly true)]
         (test-var#)))
     (alter-meta! test-var# dissoc :test)
     (~test-fn @reports#)))

(specification "uncaught errors are gracefully handled & reported"
  (let [only-errors (comp
                      (filter (comp #{:error} :type))
                      (map #(select-keys % [:type :actual :message :expected]))
                      (map #(update % :actual str)))]
    (assertions
      (test-core (specification "ERROR INTENTIONAL" :should-fail
                   (assert false))
        (partial into [] only-errors))
      => [{:type :error
           :actual "java.lang.AssertionError: Assert failed: false"
           :message "ERROR INTENTIONAL"
           :expected "IT TO NOT THROW!"}]
      (test-core (specification "EXPECTED ERROR IN BEHAVIOR" :should-fail
                   (behavior "SHOULD ERROR"
                     (assert false)))
        (partial into [] only-errors))
      => [{:type :error
           :actual "java.lang.AssertionError: Assert failed: false"
           :message "SHOULD ERROR"
           :expected "IT TO NOT THROW!"}])))
