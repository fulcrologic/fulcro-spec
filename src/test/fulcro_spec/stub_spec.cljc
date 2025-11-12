(ns fulcro-spec.stub-spec
  (:require
   [clojure.test :as t :refer [is deftest]]
   [fulcro-spec.stub :as stub]
   [fulcro-spec.core :refer [behavior assertions]])
  #?(:clj
     (:import clojure.lang.ExceptionInfo)))

(defn make-simple-script []
  (stub/make-script "something"
                    [(stub/make-step 'stub 1 [] [])]))

(deftest increment-script-call-count
  (behavior "finds and increments the correct step"
            (let [script (make-simple-script)]

              (stub/increment-script-call-count script 0)

              (is (= 1 (get-in @script [:steps 0 :times]))))))

(deftest step-complete
  (let [script (make-simple-script)]
    (behavior "is false when call count is less than expected count"
              (is (not (stub/step-complete script 0))))
    (stub/increment-script-call-count script 0)
    (behavior "is true when call count reaches expected count"
              (is (stub/step-complete script 0)))))

(defn make-call-script [to-call & {:keys [literals N]
                                   :or {N 1, literals []}}]
  (stub/scripted-stub
   (stub/make-script "something"
                     [(stub/make-step to-call N literals [])])))

(deftest scripted-stub
  (behavior "calls the stub function"
            (let [detector (atom false)
                  sstub (make-call-script (fn [] (reset! detector true)))]
              (sstub), (is (= true @detector))))

  (behavior "verifies the stub fn is called with the correct literals"
            (let [sstub (make-call-script
                         (fn [n x] [(inc n) x])
                         :literals [41 ::stub/any]
                         :N :many)]
              (assertions
               (sstub 41 :w/e) => [42 :w/e]
               (sstub 2 :w/e) =throws=> #"called with wrong arguments"
               (try (sstub 2 :w/e)
                    (catch ExceptionInfo e (ex-data e)))
               => {:args [2 :w/e]
                   :expected-literals [41 ::stub/any]}))
            (let [sstub (make-call-script
                         (fn [x n] [x (inc n)])
                         :literals [::stub/any 2]
                         :N :many)]
              (assertions
               (sstub :w/e 2) => [:w/e 3]
               (sstub :w/e 8)
               =throws=> #"called with wrong arguments"
               (try (sstub :w/e 8)
                    (catch ExceptionInfo e (ex-data e)))
               => {:args [:w/e 8]
                   :expected-literals [::stub/any 2]})))

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
                  script (stub/make-script "something"
                                           [(stub/make-step (fn [] (swap! a-count inc)) 2 [] [])
                                            (stub/make-step (fn [] (swap! b-count inc)) 1 nil [])])
                  sstub (stub/scripted-stub script)]
              (assertions
               (repeatedly 3 (fn [] (sstub) [@a-count @b-count]))
               => [[1 0] [2 0] [2 1]])))

  (behavior "records the call argument history"
            (let [script (stub/make-script "something"
                                           [(stub/make-step (fn [& args] args) 2 nil [])
                                            (stub/make-step (fn [& args] args) 1 nil [])])
                  sstub (stub/scripted-stub script)]
              (sstub 1 2) (sstub 3 4), (sstub :a :b)
              (assertions
               (:history @script) => [[1 2] [3 4] [:a :b]]
               (map :history (:steps @script)) => [[[1 2] [3 4]] [[:a :b]]]))))

(defn test-validate-counts [script-atoms]
  (let [error (atom nil)]
    (with-redefs [t/do-report (fn [m] (reset! error m))]
      (let [return (stub/validate-target-function-counts script-atoms)]
        (or @error return)))))

(deftest validate-target-function-counts
  (behavior "returns nil if a target function has been called enough times"
            (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 5 :times 5}]})]]
              (assertions
               (test-validate-counts script-atoms)
               =fn=> some?)))
  (behavior "reports an error when a target function has not been called enough times"
            (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 0 :times 5}]})]]
              (assertions
               (test-validate-counts script-atoms)
               => {:type :fail,
                   :message "fun1 was not called the expected number of times.",
                   :actual 0,
                   :expected 5})))

  (behavior "returns nil if a target function has been called enough times with :many specified"
            (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times :many}]})]]
              (assertions
               (test-validate-counts script-atoms)
               =fn=> some?)))

  (behavior "throws an exception if a function has not been called at all with :many was specified"
            (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 0 :times :many}]})]]
              (assertions
               (test-validate-counts script-atoms)
               => {:type :fail,
                   :message "fun1 was not called the expected number of times.",
                   :actual 0,
                   :expected :many})))

  (behavior "returns nil all the function have been called the specified number of times"
            (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times 1}]})
                                (atom {:function "fun2" :steps [{:ncalled 1 :times 1}]})]]
              (assertions
               (test-validate-counts script-atoms)
               =fn=> some?)))

  (behavior "throws an exception if the second function has not been called at all with :many was specified"
            (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times 1}]})
                                (atom {:function "fun2" :steps [{:ncalled 0 :times 1}]})]]
              (assertions
               (test-validate-counts script-atoms)
               => {:type :fail,
                   :message "fun2 was not called the expected number of times.",
                   :actual 0,
                   :expected 1})))

  (behavior "stubs record history, will show the script when it fails to validate"
            (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times 1}]})
                                (atom {:function "fun2" :steps [{:ncalled 1 :times 2}]})]]
              (assertions
               (test-validate-counts script-atoms)
               => {:type :fail,
                   :message "fun2 was not called the expected number of times.",
                   :actual 1,
                   :expected 2}))))

(deftest zip-arglist-test
  (assertions
   (stub/zip-arglist
    '["a"] '[1])
   => '{a 1}
   "elides extra args"
   (stub/zip-arglist
    '["a"] '[1 2])
   => '{a 1}
   "elides param if no respective arg"
   (stub/zip-arglist
    '["a" "b"] '[1])
   => '{a 1}
   "ignores ::stub/ignored"
   (stub/zip-arglist
    '[::stub/ignored "b"] '[1 2])
   => '{b 2}
   (stub/zip-arglist
    '["a" ::stub/ignored] '[1 2])
   => '{a 1}
   "bundles all arguments after an `&`"
   (stub/zip-arglist
    '[::stub/&_ "a"] '[1 2])
   => '{a [1 2]}
   (stub/zip-arglist
    '["a" ::stub/&_ "b"] '[1 2])
   => '{a 1 b [2]}
   (stub/zip-arglist
    '[::stub/&_ ::stub/ignored] '[1 2])
   => '{}
   (stub/zip-arglist
    '[::stub/&_ ::stub/literal] '[1 2])
   => '{}))
