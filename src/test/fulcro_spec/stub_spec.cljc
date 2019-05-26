(ns fulcro-spec.stub-spec
  (:require
    [nubank.workspaces.core :refer [deftest]]
    [fulcro-spec.stub :as s
     #?@(:cljs [:include-macros true])]
    [fulcro-spec.core #?(:clj :refer :cljs :refer-macros)
     [behavior provided assertions]]
    #?(:clj [clojure.test :refer [is]])
    #?(:cljs [cljs.test :refer-macros [is]]))
  #?(:clj
     (:import clojure.lang.ExceptionInfo)))

(defn make-simple-script []
  (s/make-script "something"
    [(s/make-step 'stub 1 [])]))

(deftest increment-script-call-count
  (behavior "finds and increments the correct step"
    (let [script (make-simple-script)]

      (s/increment-script-call-count script 0)

      (is (= 1 (get-in @script [:steps 0 :times]))))))

(deftest step-complete
  (let [script (make-simple-script)]
    (behavior "is false when call count is less than expected count"
      (is (not (s/step-complete script 0))))
    (s/increment-script-call-count script 0)
    (behavior "is true when call count reaches expected count"
      (is (s/step-complete script 0)))))

(defn make-call-script [to-call & {:keys [literals N]
                                   :or {N 1, literals []}}]
  (s/scripted-stub
    (s/make-script "something"
      [(s/make-step to-call N literals)])))

(deftest scripted-stub
  (behavior "calls the stub function"
    (let [detector (atom false)
          sstub (make-call-script (fn [] (reset! detector true)))]
      (sstub), (is (= true @detector))))

  (behavior "verifies the stub fn is called with the correct literals"
    (let [sstub (make-call-script
                   (fn [n x] [(inc n) x])
                   :literals [41 ::s/any]
                   :N :many)]
      (assertions
        (sstub 41 :foo) => [42 :foo]
        (sstub 2 :whatever) =throws=> #"called with wrong arguments"
        (try (sstub 2 :evil)
          (catch ExceptionInfo e (ex-data e)))
        => {:args [2 :evil]
            :expected-literals [41 ::s/any]})))

  (behavior "returns whatever the stub function returns"
    (let [sstub (make-call-script (fn [] 42))]
      (assertions (sstub) => 42)))

  (behavior "throws an exception if the function is invoked more than programmed"
    (let [sstub (make-call-script (fn [] 42))]
      (sstub) ; first call
      (assertions
        (try (sstub 1 2 3) (catch ExceptionInfo e (ex-data e)))
        => {:max-calls 1
            :args '(1 2 3)})))

  (behavior "throws whatever exception the function throws"
    (let [sstub (make-call-script (fn [] (throw (ex-info "BUMMER" {}))))]
      (assertions
        (sstub) =throws=> ExceptionInfo)))

  (behavior "only moves to the next script step if the call count for the current step reaches the programmed amount"
    (let [a-count (atom 0)
          b-count (atom 0)
          script (s/make-script "something"
                   [(s/make-step (fn [] (swap! a-count inc)) 2 [])
                    (s/make-step (fn [] (swap! b-count inc)) 1 nil)])
          sstub (s/scripted-stub script)]
      (assertions
        (repeatedly 3 (fn [] (sstub) [@a-count @b-count]))
        => [[1 0] [2 0] [2 1]])))

  (behavior "records the call argument history"
    (let [script (s/make-script "something"
                   [(s/make-step (fn [& args] args) 2 nil)
                    (s/make-step (fn [& args] args) 1 nil)])
          sstub (s/scripted-stub script)]
      (sstub 1 2) (sstub 3 4), (sstub :a :b)
      (assertions
        (:history @script) => [[1 2] [3 4] [:a :b]]
        (map :history (:steps @script)) => [[[1 2] [3 4]] [[:a :b]]]))))

(deftest validate-target-function-counts
  (behavior "returns nil if a target function has been called enough times"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 5 :times 5}]})]]
      (assertions
        (s/validate-target-function-counts script-atoms)
        =fn=> some?)))
  (behavior "throws an exception when a target function has not been called enough times"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 0 :times 5}]})]]
      (assertions
        (s/validate-target-function-counts script-atoms)
        =throws=> ExceptionInfo)))

  (behavior "returns nil if a target function has been called enough times with :many specified"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times :many}]})]]
      (assertions
        (s/validate-target-function-counts script-atoms)
        =fn=> some?)))

  (behavior "throws an exception if a function has not been called at all with :many was specified"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 0 :times :many}]})]]
      (assertions
        (s/validate-target-function-counts script-atoms)
        =throws=> ExceptionInfo)))

  (behavior "returns nil all the function have been called the specified number of times"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times 1}]})
                        (atom {:function "fun2" :steps [{:ncalled 1 :times 1}]})]]
      (assertions
        (s/validate-target-function-counts script-atoms)
        =fn=> some?)))

  (behavior "throws an exception if the second function has not been called at all with :many was specified"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times 1}]})
                        (atom {:function "fun2" :steps [{:ncalled 0 :times 1}]})]]
      (assertions
        (s/validate-target-function-counts script-atoms)
        =throws=> ExceptionInfo)))

  (behavior "stubs record history, will show the script when it fails to validate"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times 1}]})
                        (atom {:function "fun2" :steps [{:ncalled 1 :times 2}]})]]
      (assertions
        (s/validate-target-function-counts script-atoms)
        =throws=> ExceptionInfo))))
