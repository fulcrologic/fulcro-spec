(ns untangled-spec.stub-spec
  (:require
    [untangled-spec.stub :as s
     #?@(:cljs [:include-macros true])]
    [untangled-spec.core #?(:clj :refer :cljs :refer-macros)
     [specification behavior provided assertions]]
    #?(:clj [clojure.test :refer [is]])
    #?(:cljs [cljs.test :refer-macros [is]]))
  #?(:clj
     (:import clojure.lang.ExceptionInfo)))

(defn make-simple-script []
  (s/make-script "something"
    [(s/make-step 'stub 1 [])]))

(specification "increment-script-call-count"
  (behavior "finds and increments the correct step"
    (let [script (make-simple-script)]

      (s/increment-script-call-count script 0)

      (is (= 1 (get-in @script [:steps 0 :times]))))))

(specification "step-complete"
  (let [script (make-simple-script)]
    (behavior "is false when call count is less than expected count"
      (is (not (s/step-complete script 0))))
    (s/increment-script-call-count script 0)
    (behavior "is true when call count reaches expected count"
      (is (s/step-complete script 0)))))

(defn make-call-script [to-call & {:keys [literals N]
                                   :or {N 1, literals []}}]
  (s/make-script "something"
    [(s/make-step to-call N literals)]))

(defn throws! [n] (inc n))

(specification "scripted-stub"
  (behavior "calls the stub function"
    (let [detector (atom false)
          script (make-call-script (fn [] (reset! detector true)))
          sstub (s/scripted-stub script)]
      (sstub), (is (= true @detector))))

  (behavior "verifies the stub fn is called with the correct literals"
    (let [script (make-call-script
                   (fn [n x] [(inc n) x])
                   :literals [41 ::s/any]
                   :N :many)
          sstub (s/scripted-stub script)]
      (assertions
        (sstub 41 :foo) => [42 :foo]
        (sstub 2 :whatever) =throws=> (ExceptionInfo #"called with wrong arguments")
        (try (sstub 2 :evil)
          (catch ExceptionInfo e (ex-data e)))
        => {:args [2 :evil]
            :expected-literals [41 ::s/any]})))

  (behavior "returns whatever the stub function returns"
    (let [script (make-call-script (fn [] 42))
          sstub (s/scripted-stub script)]
      (is (= 42 (sstub)))))

  (behavior "throws an exception if the function is invoked more than programmed with verify-error set to true"
    (let [script (make-call-script (fn [] 42))
          sstub (s/scripted-stub script)]
      (sstub) ; first call
      (try (sstub) (catch ExceptionInfo e (is (= true (-> (ex-data e) :untangled-spec.stub/verify-error)))))))

  (behavior "throws whatever exception the function throws"
    (let [script (make-call-script (fn [] (throw (ex-info "BUMMER" {}))))
          sstub (s/scripted-stub script)]
      (assertions
        (sstub) =throws=> (ExceptionInfo))))

  (behavior "only moves to the next script step if the call count for the current step reaches the programmed amount"
    (let [a-count (atom 0)
          b-count (atom 0)
          script (s/make-script "something"
                   [(s/make-step (fn [] (swap! a-count inc)) 2 [])
                    (s/make-step (fn [] (swap! b-count inc)) 1 nil)])
          sstub (s/scripted-stub script)]
      (assertions
        (repeatedly 3 (fn [] (sstub) [@a-count @b-count]))
        => [[1 0] [2 0] [2 1]]))))

(specification "validate-target-function-counts"
  (behavior "returns nil if a target function has been called enough times"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 5 :times 5}]})]]
      (is (nil? (s/validate-target-function-counts script-atoms)))))
  (behavior "throws an exception when a target function has not been called enough times"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 0 :times 5}]})]]
      (assertions
        (s/validate-target-function-counts script-atoms)
        =throws=> (ExceptionInfo))))

  (behavior "returns nil if a target function has been called enough times with :many specified"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times :many}]})]]
      (is (nil? (s/validate-target-function-counts script-atoms)))))

  (behavior "throws an exception if a function has not been called at all with :many was specified"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 0 :times :many}]})]]
      (assertions
        (s/validate-target-function-counts script-atoms)
        =throws=> (ExceptionInfo))))

  (behavior "returns nil all the function have been called the specified number of times"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times 1}]})
                        (atom {:function "fun2" :steps [{:ncalled 1 :times 1}]})]]
      (is (nil? (s/validate-target-function-counts script-atoms)))))

  (behavior "throws an exception if the second function has not been called at all with :many was specified"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times 1}]})
                        (atom {:function "fun2" :steps [{:ncalled 0 :times 1}]})]]
      (assertions
        (s/validate-target-function-counts script-atoms)
        =throws=> (ExceptionInfo)))))
