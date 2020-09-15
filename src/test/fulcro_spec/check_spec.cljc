(ns fulcro-spec.check-spec
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :as t]
    [fulcro-spec.check :as check :refer [checker]]
    [fulcro-spec.core :refer [specification component assertions when-mocking]]))

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
         (check/checker? x-double?) => true
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
      ((check/equals?* 456) 111)
      => {:actual 111, :expected 456}))
  (component "is?*"
    (assertions
      ((check/is?* even?) 111)
      => {:actual 111, :expected even?}))
  (component "valid?*"
    (when-mocking
      (s/explain-str _ _) => ::MOCK_EXPLAIN_STR
      (assertions
        ((check/valid?* int?) "string")
        => {:actual "string"
            :expected int?
            :message ::MOCK_EXPLAIN_STR})))
  (component "exists?*"
    (assertions
      ((check/exists?* "DID NOT EXIST") nil)
      => {:message "DID NOT EXIST"
          :actual nil
          :expected `some?}))
  (component "every?*"
    (assertions
      (map (check/every?* (check/is?* number?))
        [:kw 123])
      => [{:actual :kw :expected seqable?}
          {:actual 123 :expected seqable?}]
      ((check/every?* (check/is?* number?))
       "str")
      => [{:actual \s :expected number?}
          {:actual \t :expected number?}
          {:actual \r :expected number?}]
      ((check/every?* (check/is?* number?))
       {:key :value})
      => [{:actual [:key :value] :expected number?}]
      ((check/every?* (check/is?* map-entry?))
       {:key :value})
      => []
      (set ((check/every?* (check/is?* number?))
            #{:a :b}))
      => #{{:actual :a :expected number?}
           {:actual :b :expected number?}}
      ((check/every?*
         (check/is?* even?)
         (check/is?* pos?))
       [-42 13])
      => [{:actual -42 :expected pos?}
          {:actual 13 :expected even?}]))
  (component "in*"
    (assertions
      ((check/in* [:a]) {:x 1})
      => {:actual {:x 1}
          :expected `(check/in* [:a])
          :message "expected `{:x 1}` to contain `:a` at path []"}
      ((check/in* [:a :b :c]) {:a {:x 2}})
      => {:actual {:a {:x 2}}
          :expected `(check/in* [:a :b])
          :message "expected `{:x 2}` to contain `:b` at path [:a]"}
      ((check/in* [:a :b :c]) {:a {:b {:x 3}}})
      => {:actual {:a {:b {:x 3}}}
          :expected `(check/in* [:a :b :c])
          :message "expected `{:x 3}` to contain `:c` at path [:a :b]"}
      ((check/in* [:a] (check/is?* even?)) {:a 1})
      => [{:actual 1 :expected even?}]))
  (component "embeds?*"
    (assertions
      "checks simple map values for equality"
      ((check/embeds?* {:a :X}) {:a "not :X"})
      => [{:message "at path [:a]:"
           :expected :X
           :actual {:a "not :X"}}]
      "checks nested hashmap recursively"
      ((check/embeds?* {:a {:b :X}}) {:a {:b "not :X"}})
      => [[{:message "at path [:a :b]:"
            :expected :X
            :actual {:b "not :X"}}]]
      ((check/embeds?* {:a {:b :X}}) {:a "not a map"})
      => [[{:message "at path [:a]:"
            :expected :X
            :actual "not a map"}]]
      "can take checkers as map values"
      ((check/embeds?* {:a (check/equals?* :X)}) {:a "not x"})
      => [{:actual "not x" :expected :X}]
      "does not take functions as map values"
      (seq ((check/embeds?* {:a even?}) {:a 111}))
      =throws=> #"function found, should be created with `checker` macro")))

(specification "all* combiner checker"
  (assertions
    ((check/all* (check/is?* double?)) {:x 3})
    => [{:actual {:x 3} :expected double?}]
    ((check/all*
       (check/is?* double?)
       (check/equals?* 9.99))
     {:x 3})
    => [{:actual {:x 3} :expected double?}
        {:actual {:x 3} :expected 9.99}]
    ((check/all*
       (check/is?* double?)
       (check/all* (check/equals?* 9.99)))
     {:x 3})
    => [{:actual {:x 3} :expected double?}
        {:actual {:x 3} :expected 9.99}]))

#?(:clj
   (defn test-check-expr [checker actual & [message]]
     (let [reports (atom [])]
       (with-redefs [t/do-report (fn [m] (swap! reports conj m))]
         (eval (check/check-expr message (list '_ checker actual)))
         @reports))))

#?(:clj
   (specification "check-expr"
     (assertions
       "called by assertions macro"
       (test-check-expr `(check/is?* even?) 333)
       => [{:type :fail :message nil
            :actual 333 :expected even?}]
       "reports a :pass if there were no checker failures"
       (test-check-expr `(check/is?* even?) 2)
       => [{:type :pass :message nil}]
       "the checker must be a valid `checker?`"
       (test-check-expr even? 2)
       =throws=> #"checker should be created with `checker` macro")))
