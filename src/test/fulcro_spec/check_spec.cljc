(ns fulcro-spec.check-spec
  (:require
    #?(:clj [clojure.test :as t])
    [clojure.spec.alpha :as s]
    [fulcro-spec.check :as _ :refer [checker]]
    [fulcro-spec.impl.check :as _impl]
    [fulcro-spec.core :refer [specification behavior component assertions when-mocking]]))

(def x-double?
  (checker [actual]
    (when-not (double? (get-in actual [:x]))
      {:actual actual
       :expected `double?
       :message "x was not a double"})))

#?(:clj
   (specification "checker macro"
     (component "creates a function"
       (assertions
         "that is a `checker?`"
         (_/checker? x-double?) => true
         "that takes only one argument"
         (x-double? :x :y) =throws=> clojure.lang.ArityException
         (x-double? {:x 1 :y 2})
         => {:actual {:x 1 :y 2}
             :expected `double?
             :message "x was not a double"}
         (try (eval `(checker [a b] 13))
           (catch Throwable t
             (ex-message (ex-cause t))))
         =fn=> (partial re-find #"checker arglist should only have one argument")))))

(specification "default checkers"
  (component "equals?*"
    (assertions
      ((_/equals?* 456) 111)
      => {:actual 111, :expected 456}))
  (component "is?*"
    (assertions
      ((_/is?* even?) 111)
      => {:actual 111, :expected even?}))
  (component "valid?*"
    (when-mocking
      (s/explain-str _ _) => ::MOCK_EXPLAIN_STR
      (assertions
        ((_/valid?* int?) "string")
        => {:actual "string"
            :expected int?
            :message ::MOCK_EXPLAIN_STR})))
  (component "re-find?*"
    (assertions
      ((_/re-find?* #"-123-") "foo-123-bar")
      => nil
      ((_/re-find?* #"-123-") :not-a-string)
      => [{:actual :not-a-string
           :expected string?}]
      ((_/re-find?* #"test regex") "foo-123-bar")
      => [#?(:clj  {:message "Failed to find `test regex` in 'foo-123-bar'"
                    :actual "foo-123-bar"
                    :expected `(re-pattern "test regex")}
             :cljs {:message "Failed to find `/test regex/` in 'foo-123-bar'"
                    :actual "foo-123-bar"
                    :expected `(re-pattern "/test regex/")})]))
  (component "seq-matches?*"
    (assertions
      "compares expected with actual in a sequential manner"
      ((_/seq-matches?* [0 1 2]) (range 5))
      => nil
      ((_/seq-matches?* [1 1]) (range 5))
      => [{:message "at index `0` failed to match:"
           :actual 0 :expected 1}]
      "accepts checkers as values in collection"
      ((_/seq-matches?*
         [(_/is?* odd?) (_/equals?* 42)])
       [22 33])
      => [{:actual 22 :expected odd?}
          {:actual 33 :expected 42}]
      "will pad actual collection with ::not-found"
      ((_/seq-matches?*
         [(_/equals?* 0) (_/equals?* 1)]) [0])
      => [{:actual ::_/not-found :expected 1}]
      "refuses non-checker functions"
      ((_/seq-matches?* [odd?]) [1])
      =throws=> #"function found, should be created with `checker`"
      "only takes `sequential?` collections"
      (_/seq-matches?* #{:a})
      =throws=> #"Invalid argument.*failed predicate.*sequential"
      ((_/seq-matches?* [:a]) #{:b})
      => [{:actual #{:b} :expected sequential?}]))
  (component "of-length?*"
    (assertions
      "takes a single int for an exact length"
      ((_/of-length?* 1) [:a])
      => nil
      ((_/of-length?* 2) [:a])
      => [{:actual [:a]
           :expected '(of-length?* :equal-to 2)
           :message "Expected collection count to be 2 was 1"}]
      "can take two ints for (inclusive) min and max length"
      ((_/of-length?* 1 2) [:a])
      => nil
      ((_/of-length?* 1 2) [:a :b :c])
      => [{:actual [:a :b :c]
           :expected '(of-length?* :between :min 1 :max 2)
           :message "Expected collection count 3 to be between [1,2]"}]))
  (component "seq-matches-exactly?*"
    (assertions
      ((_/seq-matches-exactly?* [:a]) [:a])
      => nil
      ((_/seq-matches-exactly?* [:a]) [:a :b])
      => [{:actual [:a :b]
           :expected '(of-length?* :equal-to 1)
           :message "Expected collection count to be 1 was 2"}]
      ((_/seq-matches-exactly?* [:a]) [])
      => [{:actual []
           :expected '(of-length?* :equal-to 1)
           :message "Expected collection count to be 1 was 0"}]))
  (component "exists?*"
    (assertions
      ((_/exists?* "DID NOT EXIST") nil)
      => {:message "DID NOT EXIST"
          :actual nil
          :expected `some?}))
  (component "every?*"
    (assertions
      ((_/every?* (_/is?* number?)) :kw)
      => [{:actual :kw :expected seqable?}]
      ((_/every?* (_/is?* number?))
       "str")
      => [{:actual \s :expected number?}
          {:actual \t :expected number?}
          {:actual \r :expected number?}]
      ((_/every?* (_/is?* number?))
       {:key :value})
      => [{:actual [:key :value] :expected number?}]
      ((_/every?* (_/is?* map-entry?))
       {:key :value})
      => nil
      (set ((_/every?* (_/is?* number?))
            #{:a :b}))
      => #{{:actual :a :expected number?}
           {:actual :b :expected number?}}
      ((_/every?*
         (_/is?* even?)
         (_/is?* pos?))
       [-42 13])
      => [{:actual -42 :expected pos?}
          {:actual 13 :expected even?}]
      "short circuits checkers using and*"
      ((_/every?*
         (_/is?* number?)
         (_/is?* pos?))
       [1 "A"])
      => [{:actual "A"
           :expected number?}]))
  (component "subset?*"
    (assertions
      ((_/subset?* #{:a :b}) #{:a})
      => nil
      ((_/subset?* #{:a}) #{:a :b})
      => [{:actual {:extra-values #{:b}}
           :expected '(subset?* #{:a})
           :message "Found extra values in set"}]
      "must take a set"
      (_/subset?* 123) =throws=> #"Invalid argument.*to.*subset"))
  (component "in*"
    (assertions
      "checks that the value contains the path"
      ((_/in* [:a] (_/is?* some?)) {:x 1})
      => {:actual {:x 1}
          :expected `(_/in* [:a])
          :message "expected `{:x 1}` to contain `:a` at path []"}
      ((_/in* [:a :b :c] (_/is?* some?)) {:a {:x 2}})
      => {:actual {:a {:x 2}}
          :expected `(_/in* [:a :b])
          :message "expected `{:x 2}` to contain `:b` at path [:a]"}
      ((_/in* [:a :b :c] (_/is?* some?)) {:a {:b {:x 3}}})
      => {:actual {:a {:b {:x 3}}}
          :expected `(_/in* [:a :b :c])
          :message "expected `{:x 3}` to contain `:c` at path [:a :b]"}
      "the path is in the failure"
      ((_/in* [:a] (_/is?* even?)) {:a 1})
      => [{:actual 1 :expected even? ::_/path [:a]}]
      "paths for nested in* are correct"
      ((_/in* [:a] (_/in* [:b] (_/equals?* "B"))) {:a {:b "not b"}})
      => [{:actual "not b"
           :expected "B"
           ::_/path [:a :b]}] ))
  (component "embeds?*"
    (assertions
      "checks simple map values for equality"
      ((_/embeds?* {:a :X}) {:a "not :X"})
      => [{:expected :X
           :actual "not :X"
           ::_/path [:a]}]
      "checks nested hashmap recursively"
      ((_/embeds?* {:a {:b :X}}) {:a {:b "not :X"}})
      => [{:expected :X
           :actual "not :X"
           ::_/path [:a :b]}]
      ((_/embeds?* {:a {:b :X}}) {:a "not a map"})
      => [{:expected {:b :X}
           :actual "not a map"
           ::_/path [:a]}]
      "checking for nil values that dont exist"
      ((_/embeds?* {:a nil}) {})
      => [{:actual ::_/not-found
           :expected nil
           ::_/path [:a]}]
      "can take checkers as map values"
      ((_/embeds?* {:a (_/equals?* :X)}) {:a "not x"})
      => [{:actual "not x"
           :expected :X
           ::_/path [:a]}]
      "does not take functions as map values"
      (seq ((_/embeds?* {:a even?}) {:a 111}))
      =throws=> #"Expected a checker.*found a function"
      "can check that key value pair was not found by checking for equality with ::not-found"
      ((_/embeds?* {:a ::_/not-found}) {})
      => nil
      ((_/embeds?* {:a ::_/not-found}) {:a "FAIL"})
      => [{:actual "FAIL"
           :expected ::_/not-found
           ::_/path [:a]}]
      "paths for nested embeds are correct"
      ((_/embeds?* {:a (_/embeds?* {:b "B"})}) {:a {:b "not b"}})
      => [{:actual "not b"
           :expected "B"
           ::_/path [:a :b]}])))

(specification "fmap*"
  (assertions
    ((_/fmap* inc (_/is?* even?)) 0)
    => {:actual 1 :expected even?}))

(specification "all* checker combiner"
  (assertions
    ((_/all* (_/is?* double?)) {:x 3})
    => [{:actual {:x 3} :expected double?}]
    ((_/all*
       (_/is?* double?)
       (_/equals?* 9.99))
     {:x 3})
    => [{:actual {:x 3} :expected double?}
        {:actual {:x 3} :expected 9.99}]
    ((_/all*
       (_/is?* double?)
       (_/all* (_/equals?* 9.99)))
     {:x 3})
    => [{:actual {:x 3} :expected double?}
        {:actual {:x 3} :expected 9.99}]
    "refuses to take non-checker arguments"
    (_/all* even?)
    =throws=> #"Invalid checker"))

(specification "and* checker combiner"
  (assertions
    ((_/and* (_/is?* number?) (_/is?* pos?)) 123)
    => nil
    ((_/and* (_/is?* number?) (_/is?* pos?)) -123)
    => [{:actual -123 :expected pos?}]
    ((_/and* (_/is?* number?) (_/is?* pos?)) "123")
    => [{:actual "123" :expected number?}]
    ((_/and* (_/all* (_/is?* pos?) (_/is?* even?))) -123)
    => [{:actual -123 :expected pos?}
        {:actual -123 :expected even?}]
    "refuses to take non-checker arguments"
    (_/and* number?)
    =throws=> #"Invalid checker"))

(specification "throwable*"
  (assertions
    (throw (ex-info "test" {}))
    =throws=> (_/throwable* (_/is?* some?))))

(defn errors-match?* [& cs]
  (_/ex-data*
    (checker [data]
      ((apply _/all* cs) (::errors data)))))

(specification "ex-data*"
  (assertions
    (throw (ex-info "blah" {::errors 555}))
    =throws=> (_/ex-data*
                (_/embeds?* {::errors 555}))
    (throw (ex-info "blah" {::errors 555}))
    =throws=> (errors-match?*
                (_/equals?* 555))))

(specification "prepend-message"
  (assertions
    (_impl/prepend-message "TST:MSG" {})
    => {:message "TST:MSG"}
    (_impl/prepend-message "TST:MSG" {:message "message"})
    => {:message "TST:MSG\nmessage"}))

(specification "append-message"
  (assertions
    (_impl/append-message "TST:MSG" nil)
    => {:message "TST:MSG"}
    (_impl/append-message "TST:MSG" {})
    => {:message "TST:MSG"}
    (_impl/append-message "TST:MSG" {})
    => {:message "TST:MSG"}
    (_impl/append-message "TST:MSG" {:message "message"})
    => {:message "message\nTST:MSG"}))

#?(:clj
   (defn test-check-expr [checker actual & [message]]
     (let [reports (atom [])]
       (with-redefs [t/do-report (fn [m] (swap! reports conj m))]
         (eval (_impl/check-expr false message (list '_ checker actual)))
         @reports))))

#?(:clj
   (specification "check-expr"
     (assertions
       "called by assertions macro"
       (test-check-expr `(_/is?* even?) 333)
       => [{:type :fail :message nil ::_/actual 333
            :actual 333 :expected even?}]
       "reports a :pass if there were no checker failures"
       (test-check-expr `(_/is?* even?) 2)
       => [{:type :pass :message nil}]
       "the checker must be a valid `checker?`"
       (test-check-expr even? 2)
       =throws=> #"Invalid checker")))
